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
(require '[loom.graph                :as graph])
(require '[loom.io                   :as graphio])
(require '[clojars-dependencies.core :as cd] :reload-all)

; Change this with caution...
(def force-rsync false)

(def poms-directory "./poms")
(def clojars-poms-directory "./poms/clojars")

(if (or force-rsync
        (not (.exists (io/file clojars-poms-directory))))
  (do
    (io/make-parents clojars-poms-directory)
    (cd/rsync "-zarv" "--prune-empty-dirs" "--delete" "--include=*/" "--include=*.pom" "--exclude=*" "clojars.org::clojars" clojars-poms-directory)))   ; This takes a long time...

(def pom-files (filter #(and (.endsWith (.getName %) ".pom")
                             (.canRead %)
                             (not (.isDirectory %)))
                       (file-seq (io/file poms-directory))))

(println "ℹ️ Found" (count pom-files) "POM files")

(def parsed-poms            (remove nil? (pmap cd/parse-pom-file pom-files)))
(def grouped-projects       (group-by #(first (first %)) parsed-poms))
(def latest-versions-only   (map #(last (sort-by (fn [p] (second (first p))) ver/version-compare (second %))) grouped-projects))
(def version-numbers-elided (map #(vec [(first (first %)) (map first (second %))]) latest-versions-only))

(println "ℹ️ Found" (count version-numbers-elided) "unique projects")

(def edges (for [project version-numbers-elided
                 from    [(first project)]
                 to      (second project)]
             [from to]))

(println "ℹ️ Found" (count edges) "unique dependencies")

(def inverted-dependencies (group-by second edges))

; Some test stuff
(println "ℹ️ Consumers of version-clj/version-clj:\n *" (s/join "\n * " (sort (map first (get inverted-dependencies "version-clj/version-clj")))))
(println "ℹ️ Top 10 most depended-upon projects:\n *" (s/join "\n * " (take 10 (map first (sort-by #(count (val %)) > inverted-dependencies)))))

; Build a Loom graph
(def g (apply graph/digraph edges))

;(graphio/view g)   ; Warning: this takes a *VERY* long time on a data set of this size...
