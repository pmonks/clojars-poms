;
; This script is intended to be used to initialise a clj REPL set up for easy experimentation with this project.
;
; Use:
;
;     clj -i repl-init.clj -r
;

(require '[clojure.repl              :refer :all])
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

; Change this with caution - rsync'ing from Clojars takes a while and may tax clojars.org's rsync server...
(def force-rsync false)

(def poms-directory "./poms")
(def clojars-poms-directory "./poms/clojars")

; REPL state setup...
(if (or force-rsync
        (not (.exists (io/file clojars-poms-directory))))
  (do
    (println "ℹ️ POM files not found (or force rsync enabled) - rsyncing all POMs from Clojars")
    (io/make-parents clojars-poms-directory)
    (cd/rsync-poms "clojars.org::clojars" clojars-poms-directory))   ; This takes a long time...
  (println "ℹ️ Skipping rsync - POM files already present"))

(def parsed-poms (cd/parse-pom-files poms-directory))
(println "ℹ️ Parsed" (count parsed-poms) "POMs")

(def latest-versions-only (cd/latest-project-versions parsed-poms))
(println "ℹ️ Found" (count latest-versions-only) "unique projects")

(def dependencies (cd/dependencies latest-versions-only))
(println "ℹ️ Found" (count dependencies) "unique dependencies")


; Experiments go here...

(def inverted-dependencies (group-by second dependencies))

(println "ℹ️ Consumers of version-clj/version-clj:\n *" (s/join "\n * " (sort (map first (get inverted-dependencies "version-clj/version-clj")))))
(println "ℹ️ Top 25 most depended-upon projects:\n *" (s/join "\n * " (take 25 (map first (sort-by #(count (val %)) > inverted-dependencies)))))

; Build a Loom graph
(def g (apply g/digraph dependencies))

(println "ℹ️ Dependencies are a DAG?" (galg/dag? g))

;(def g (apply g/digraph edges))

;(graphio/view g)   ; Warning: this takes a *VERY* long time on a data set of this size...
