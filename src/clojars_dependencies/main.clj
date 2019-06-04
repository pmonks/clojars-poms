(ns clojars-dependencies.main
  (:require [clojars-dependencies.core :as cd])
  (:gen-class))

(defn -main
  [& args]
  (println "-- NOT YET IMPLEMENTED --")
;  (cd/build-dependency-graph (str (System/getenv "HOME") "/Development/personal/clojars-poms")) false
  )
