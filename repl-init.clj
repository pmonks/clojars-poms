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

(require '[clojure.pprint             :as pp :refer [pprint]])
(require '[clojure.string             :as s])
(require '[clojure.java.io            :as io])
(require '[clojure.xml                :as xml])
(require '[clojure.zip                :as zip])
(require '[clojure.data.zip.xml       :as zip-xml])
(require '[version-clj.core           :as ver])
(require '[loom.graph                 :as g])
(require '[loom.alg                   :as galg])
(require '[loom.io                    :as gio])
(require '[clojars-dependencies.sync  :as cs] :reload-all)
(require '[clojars-dependencies.parse :as cp] :reload-all)
(require '[progress.indeterminate     :as pi])
(require '[progress.determinate       :as pd])

(def prevent-sync true)

(def poms-directory "./poms")
(def clojars-poms-directory "./poms/clojars")

; REPL state setup...
(if prevent-sync
  (println "ℹ️ Skipping Clojars POM sync")
  (do
    (io/make-parents clojars-poms-directory)
    (print "ℹ️ Syncing POM index from Clojars... ")
    (pi/animate! (cs/sync-index! clojars-poms-directory))
    (let [pom-count (cs/pom-count clojars-poms-directory)]
      (println "\nℹ️ Syncing" pom-count "POMs from Clojars... ")
      (cs/sync-index! clojars-poms-directory)
      (pd/animate! cs/sync-count
                   :opts {:total pom-count}
                   (cs/sync-clojars-poms! clojars-poms-directory)))   ; This may take a long time...
      (println "ℹ️ Done - POMs synced")))

(let [pom-count (cp/pom-count poms-directory)]
  (println "ℹ️ Parsing" pom-count "POMs (latest version of each artifact only)... ")
  (def parsed-poms (pd/animate! cp/parse-count
                                :opts {:total pom-count}
                                (doall (cp/parse-pom-files poms-directory))))
  (println "ℹ️ Done - POMS parsed"))

(println "\n\nParsed poms (as XML zippers) are in `parsed-poms` var\n")


; Get all license names & URLs
;(def license-names (filter #(not (s/blank? %)) (map #(zip-xml/xml1-> % :licenses :license :name zip-xml/text) parsed-poms)))
;(def license-urls  (filter #(not (s/blank? %)) (map #(zip-xml/xml1-> % :licenses :license :url  zip-xml/text) parsed-poms)))

; How many POMs have a license name
;(count license-names)

; Frequencies
;(sort-by second (frequencies license-names))

; 10 most common license names
;(pprint (take 10 (reverse (sort-by second (frequencies license-names)))))

; Distinct names
;(sort (distinct license-names))

; Save distinct names and URLs to file
;(spit (io/file "license-names.txt") (s/join "\n" (sort (distinct license-names))))
;(spit (io/file "license-urls.txt")  (s/join "\n" (sort (distinct license-urls))))




;(def latest-versions-only (cp/latest-project-versions parsed-poms))
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
