;
; Copyright 2019 Peter Monks
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;
; SPDX-License-Identifier: Apache-2.0

;
; This script is intended to be used to initialise a clj REPL set up for easy experimentation with this project.
;
; Use:
;
;     clojure -i repl-init.clj -r
;
; Note: do NOT use `clj` to run this script, or you will see strange output behaviour
;

(require '[clojure.pprint         :as pp :refer [pprint]])
(require '[clojure.string         :as s])
(require '[clojure.java.io        :as io])
(require '[clojure.xml            :as xml])
(require '[clojure.zip            :as zip])
(require '[clojure.data.zip.xml   :as zip-xml])
(require '[version-clj.core       :as ver])
(require '[loom.graph             :as g])
(require '[loom.alg               :as galg])
(require '[loom.io                :as gio])
(require '[clojars-poms.sync      :as cs] :reload-all)
(require '[clojars-poms.parse     :as cp] :reload-all)
(require '[progress.indeterminate :as pi])
(require '[progress.determinate   :as pd])

; Controls whether only the POM of the latest version of each artifact is parsed
; Turning this off will substantially increase memory usage
(def parse-latest-versions-only? true)

(def poms-directory         "./poms")
(def clojars-poms-directory (str poms-directory "/clojars"))
(def cache-exists?          (.exists (io/file clojars-poms-directory)))

(defn prompt-for-y-or-n?
  "Prompts the user with a yes or no question, returning a boolean indicating
  their answer (yes = true)."
  [msg]
  (print msg "[y/N] ")
  (flush)
  (s/starts-with? (s/lower-case (read-line)) "y"))

(def sync? (when cache-exists? (prompt-for-y-or-n? "\nCache exists; sync?")))

; Sync phase
(if (and cache-exists? (not sync?))
  (println "ℹ️ Skipping Clojars POM sync")
  (do
    (when-not cache-exists? (println "ℹ️ Cache doesn't exist; will sync all Clojars POMs"))
    (io/make-parents clojars-poms-directory)
    (print "ℹ️ Syncing Clojars POM index... ")
    (flush)
    (pi/animate! (cs/sync-index! clojars-poms-directory))
    (let [pom-count (cs/pom-count clojars-poms-directory)  ; Note: pre-synced count - after syncing it may go
          start     (System/currentTimeMillis)]
      (println "\nℹ️" (if cache-exists? "Checking" "Syncing") pom-count "Clojars POMs...")
      (flush)
      (pd/animate! cs/sync-count
                   :opts {:total pom-count}
                   (cs/sync-clojars-poms! clojars-poms-directory))   ; This takes a loooooong time...
      (let [time-taken (long (Math/ceil (/ (- (System/currentTimeMillis) start) 1000)))]
        (println (format "ℹ️ Done - %d POMs %s in %ds (%.2f/s)"
                         pom-count
                         (if cache-exists? "checked" "synced")
                         time-taken
                         (double (/ pom-count time-taken))))))))

; Parse phase
(print "ℹ️ Counting cached POM files... ")

(pi/animate!
  (def total-pom-count  (cs/pom-count clojars-poms-directory))  ; We do this a second time because it may have changed after syncing
  (def parsed-pom-count (cp/pom-count poms-directory parse-latest-versions-only?)))

(let [start (System/currentTimeMillis)]
  (println (str "\nℹ️ Parsing " parsed-pom-count " POMs " (if parse-latest-versions-only? "(latest version of each artifact only)" "(all versions of all artifacts)") "... "))
  (flush)
  (def poms (pd/animate! cp/parse-count
                         :opts {:total parsed-pom-count}
                         (doall (cp/parse-pom-files poms-directory parse-latest-versions-only?))))
  (let [time-taken (long (Math/ceil (/ (- (System/currentTimeMillis) start) 1000)))]
    (println (format "ℹ️ Done - %d POMs parsed in %ds (%.2f/s)"
                     parsed-pom-count
                     time-taken
                     (double (/ parsed-pom-count time-taken))))))

(println "\ndata:")
(println "  * poms - parsed poms, as a sequence of XML zippers")
(println "  * total-pom-count - total # of POMs in the local cache")
(println "  * parsed-pom-count - # of POMs that were parsed (may not= (count poms) due to invalid POMs)")

(println "\nfns:")
(println "  * (pom->gav pom-xml-zipper)")
(println "  * (gav->clojars-url gav-string)")
(println "  * (find-deps-by-license-name license-name-string)")
(println "  * (find-deps-containing-fragment-in-name license-fragment-string)")
(println)

; Handy utility fns
(defn pom->gav
  "Returns the GAV (as a String) of the given POM xml."
  [pom-xml]
  (when pom-xml
    (str (zip-xml/xml1-> pom-xml :groupId zip-xml/text)
         "/"
         (zip-xml/xml1-> pom-xml :artifactId zip-xml/text)
         "@"
         (zip-xml/xml1-> pom-xml :version zip-xml/text))))

(defn gav->clojars-url
  "Returns the Clojars URL of the *directory* containing the POM for the given
  GAV."
  [gav]
  (when-not (s/blank? gav)
    (let [[ga v] (s/split gav #"@")
          [g  a] (s/split ga  #"/")]
      (str "https://repo.clojars.org/"
           (s/replace g "." "/")
           "/" a "/" v "/"))))

(defn find-deps-by-license-name
  "Find all deps with the given license name"
  [lic]
  (when-not (s/blank? lic)
    (some-> (map pom->gav (filter #(= lic (zip-xml/xml1-> % :licenses :license :name zip-xml/text)) poms))
            seq
            set)))

(defn find-deps-containing-fragment-in-name
  "Find all deps with the given fragment in the license name"
  [fragment]
  (when-not (s/blank? fragment)
    (some-> (map pom->gav (filter #(when-let [name (zip-xml/xml1-> % :licenses :license :name zip-xml/text)] (s/includes? name fragment)) poms))
            seq
            set)))

; Get all license names & URLs
;(def license-names (filter #(not (s/blank? %)) (map #(zip-xml/xml1-> % :licenses :license :name zip-xml/text) poms)))
;(def license-urls  (filter #(not (s/blank? %)) (map #(zip-xml/xml1-> % :licenses :license :url  zip-xml/text) poms)))

; Distinct license names (as a set)
;(def distinct-license-names (some-> (distinct license-names) seq set))

; Find dependencies with a specific license name
;(find-deps-by-license-name "MIT/Apache-2.0/BSD-3-Clause")

; How many POMs have a license name
;(count license-names)

; How many unique license names
;(count (distinct license-names))

; Frequencies
;(sort-by second (frequencies license-names))

; 10 most common license names
;(pprint (take 10 (reverse (sort-by second (frequencies license-names)))))

; Distinct names
;(sort (distinct license-names))

; Save distinct names and URLs to file
;(spit (io/file "license-names.txt") (s/join "\n" (sort (distinct license-names))))
;(spit (io/file "license-urls.txt")  (s/join "\n" (sort (distinct license-urls))))




; Count how many have a license
;(count (filter #(not (s/blank? %)) (map #(zip-xml/xml1-> % :licenses :license zip-xml/text) poms)))

; poms without licenses
;(def poms-without-licenses (doall (filter #(s/blank? (zip-xml/xml1-> % :licenses :license zip-xml/text)) poms)))

; dump poms without licenses to a file
;(spit "poms-without-licenses.txt" (s/join "\n" (sort (map #(cp/gav->string (cp/gav %)) poms-without-licenses))))





;(def latest-versions-only (cp/latest-project-versions poms))
;(println "ℹ️ Found" (count latest-versions-only) "unique projects")

;(def dependencies (cp/dependencies latest-versions-only))
;(println "ℹ️ Found" (count dependencies) "unique dependencies amongst latest versions")


; Experiments go here...

;(def inverted-dependencies (group-by second dependencies))   ; This isn't correct...
;(def sample-library "version-clj/version-clj")
;(def consumers (seq (sort (map first (get inverted-dependencies sample-library)))))

;(println "ℹ️ Consumers of" (str sample-library ":\n") (if consumers (doall (map (partial println "* ") consumers)) "- none -"))
;(println "ℹ️ Top 25 most depended-upon projects:\n *" (s/join "\n * " (take 25 (map first (sort-by #(count (val %)) > inverted-dependencies)))))

; Build a Loom graph
;(def g (apply g/digraph dependencies))

;(println "ℹ️ Dependencies are a DAG?" (galg/dag? g))

;(def g (apply g/digraph edges))

;(graphio/view g)   ; Warning: this takes a *VERY* long time on a data set of this size...
