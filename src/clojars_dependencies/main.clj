;
; Copyright 2019 Peter Monks
; SPDX-License-Identifier: Apache-2.0
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

(ns clojars-dependencies.main
  (:require [clojars-dependencies.core :as cd])
  (:gen-class))

(defn -main
  [& args]
  (println "-- NOT YET IMPLEMENTED --")
;  (cd/build-dependency-graph (str (System/getenv "HOME") "/Development/personal/clojars-poms")) false
  )
