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
(require '[clojars-poms.cache     :as cc])
(require '[clojars-poms.index     :as ci])
(require '[clojars-poms.parse     :as cpa])
(require '[clojars-poms.poms      :as cpo])
(require '[progress.indeterminate :as pi])
(require '[progress.determinate   :as pd])

; Controls whether only the POM of the latest version of each artifact is parsed
; Turning this off will substantially increase memory usage
(def latest-versions-only? true)

(defn prompt-for-y-or-n?
  "Prompts the user with a yes or no question, returning a boolean indicating
  their answer (yes = true)."
  [msg]
  (print msg "[y/N] ")
  (flush)
  (s/starts-with? (s/lower-case (read-line)) "y"))

(def sync? (boolean (or (not (cc/exists?))
                        (not (ci/exists?))
                        (prompt-for-y-or-n? "\nCache exists; sync?"))))

; Sync index
(when sync?
  (when-not (cc/exists?)
    (println "ℹ️ Cache doesn't exist; creating it")
    (cc/create!))
  (print "ℹ️ Syncing Clojars POM index... ")
  (pi/animate! :opts {:frames (:clocks pi/styles)}
    (ci/sync!))
  (println))

; Count how many POMs we'll be syncing & parsing
(print "ℹ️ Counting" (if latest-versions-only? "latest" "all") "POMs in index... ")
(pi/animate! :opts {:frames (:clocks pi/styles)}
  (def pom-count (ci/pom-count latest-versions-only?)))
(println)

; Sync POMs
(if sync?
  (let [start (System/currentTimeMillis)]
    (println "ℹ️ Syncing" pom-count "POMs from Clojars" (if latest-versions-only? "(latest only)..." "(all)..."))
    (flush)
    (pd/animate! cpo/sync-count
                 :opts {:total       pom-count
                        :redraw-rate 30
                        :style       (:coloured-ascii-boxes pd/styles)}
                 (cpo/sync! latest-versions-only?))   ; This can take a while, though a JVM with virtual thread support helps...
    (let [time-taken (long (Math/ceil (/ (- (System/currentTimeMillis) start) 1000)))]
      (println (format "ℹ️ Done - %d POMs synced in %ds (%.2f/s)"
                       pom-count
                       time-taken
                       (double (/ pom-count time-taken))))))
  (println "ℹ️ Skipping Clojars POM sync"))

; Parse POMs from disk
(let [start (System/currentTimeMillis)]
  (println (str "ℹ️ Parsing " pom-count " POMs " (if latest-versions-only? "(latest only)..." "(all)...")))
  (flush)
  (def poms (pd/animate! cpa/parse-count
                         :opts {:total       pom-count
                                :redraw-rate 30
                                :style       (:coloured-ascii-boxes pd/styles)}
                         (doall (cpa/parse latest-versions-only?))))
  (let [time-taken       (long (Math/ceil (/ (- (System/currentTimeMillis) start) 1000)))
        parsed-pom-count (count poms)]
    (println (format "ℹ️ Done - %d POMs parsed (✅: %d, ❌: %d) in %ds (%.2f/s)"
                     pom-count
                     parsed-pom-count
                     (- pom-count parsed-pom-count)
                     time-taken
                     (double (/ pom-count time-taken))))))

; Handy utility fns
(defn gav-map
  "Returns the GAV (as a Map) of the given XML element. The map has some or all
  of these keys:

  * :group-id - String
  * :artifact-id - String
  * :version - String"
  [elem]
  (when elem
    (let [group-id    (zip-xml/xml1-> elem :groupId    zip-xml/text)
          artifact-id (zip-xml/xml1-> elem :artifactId zip-xml/text)
          version     (zip-xml/xml1-> elem :version    zip-xml/text)]
      (merge {}
             (when-not (s/blank? group-id)    {:group-id    group-id})
             (when-not (s/blank? artifact-id) {:artifact-id artifact-id})
             (when-not (s/blank? version)     {:version     version})))))

(defn gav
  "Returns the GAV (as a String) of the given XML element."
  [elem]
  (when-let [{group-id    :group-id
              artifact-id :artifact-id
              version     :version} (gav-map elem)]
    (str (when group-id    (str group-id "/"))
         (when artifact-id artifact-id)
         (when version     (str "@" version)))))

(defn gav->clojars-url
  "Returns the Clojars URL of the *directory* containing the POM for the given
  GAV (a String in groupId/artifactId@version format)."
  [gav]
  (when-not (s/blank? gav)
    (let [[ga v] (s/split gav #"@")
          [g  a] (s/split ga  #"/")]
      (str "https://repo.clojars.org/"
           (s/replace g "." "/")
           "/" a "/" v "/"))))

(def dependencies (apply merge (map #(let [gav  (gav-map %)
                                           deps (map gav-map (zip-xml/xml-> % :dependencies :dependency))]
                                       {gav deps})
                                    poms)))

;(def inverted-dependencies ....####TODO)

(defn find-deps-by-license-name
  "Find all deps with the given license name"
  [lic]
  (when-not (s/blank? lic)
    (some-> (map gav (filter #(= lic (zip-xml/xml1-> % :licenses :license :name zip-xml/text)) poms))
            seq
            set)))

(defn find-deps-containing-fragment-in-name
  "Find all deps with the given fragment in the license name"
  [fragment]
  (when-not (s/blank? fragment)
    (some-> (map gav (filter #(when-let [name (zip-xml/xml1-> % :licenses :license :name zip-xml/text)] (s/includes? name fragment)) poms))
            seq
            set)))

; Print help on startup
(defn help
  []
  (println "\ndata:")
  (println "  * poms - parsed poms, as a sequence of XML zippers")
  (println "  * dependencies - map of dependencies, where each key is a GAV string and each value is a sequence of GAV maps it depends on")
  ;(println "  * inverse-dependencies - sequence of dependencies, expressed as ####")

  (println "\nfns:")
  (println "  * (gav-map xml)")
  (println "  * (gav xml)")
  (println "  * (gav->clojars-url gav-string)")
  (println "  * (find-deps-by-license-name license-name-string)")
  (println "  * (find-deps-containing-fragment-in-name license-fragment-string)")

  (println "\nexample handy exps:")
  (println "  * (sort (distinct (filter (complement s/blank?) (map #(zip-xml/xml1-> % :licenses :license :name zip-xml/text) poms))))")

  (println "\nUse (help) to see this message at any time.\n"))
(help)

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
