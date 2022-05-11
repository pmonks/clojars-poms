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

(require '[clojure.pprint            :as pp :refer [pprint]])
(require '[clojure.string            :as s])
(require '[clojure.java.io           :as io])
(require '[clojure.xml               :as xml])
(require '[clojure.zip               :as zip])
(require '[clojure.data.zip.xml      :as zip-xml])
(require '[version-clj.core          :as ver])
(require '[loom.graph                :as g])
(require '[loom.alg                  :as galg])
(require '[loom.io                   :as gio])
(require '[clojars-dependencies.core :as cd] :reload-all)
(require '[spinner.core              :as spin])

(def prevent-sync false)

(def poms-directory "./poms")
(def clojars-poms-directory "./poms/clojars")

; REPL state setup...
(if prevent-sync
  (println "ℹ️ Skipping sync")
  (do
    (io/make-parents clojars-poms-directory)
    (print "ℹ️ Syncing POMs from Clojars... ")
    (if (spin/spin! (fn [] (cd/sync-clojars-poms! clojars-poms-directory)))   ; This may take a long time...
      (println "\nℹ️ Done - updated POMs synced")
      (println "\nℹ️ Done - no changes"))))

(print "ℹ️ Parsing POMs... ")
(def parsed-poms (cd/parse-pom-files poms-directory))   ; Should use a spinner here, but the JVM's idiotic "illegal reflection" warning seems to screw up jansi
(println "\nℹ️ Parsed" (count parsed-poms) "POMs")

(def latest-versions-only (cd/latest-project-versions parsed-poms))
(println "ℹ️ Found" (count latest-versions-only) "unique projects")

(def dependencies (cd/dependencies latest-versions-only))
(println "ℹ️ Found" (count dependencies) "unique dependencies amongst latest versions")


; Experiments go here...

(def inverted-dependencies (group-by second dependencies))   ; This isn't correct...
(def sample-library "version-clj/version-clj")
(def consumers (seq (sort (map first (get inverted-dependencies sample-library)))))

(println "ℹ️ Consumers of" (str sample-library ":\n") (if consumers (doall (map (partial println "* ") consumers)) "- none -"))
;(println "ℹ️ Top 25 most depended-upon projects:\n *" (s/join "\n * " (take 25 (map first (sort-by #(count (val %)) > inverted-dependencies)))))

; Build a Loom graph
;(def g (apply g/digraph dependencies))

;(println "ℹ️ Dependencies are a DAG?" (galg/dag? g))

;(def g (apply g/digraph edges))

;(graphio/view g)   ; Warning: this takes a *VERY* long time on a data set of this size...
