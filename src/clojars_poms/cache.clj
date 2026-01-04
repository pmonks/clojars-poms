;
; Copyright 2025 Peter Monks
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

(ns clojars-poms.cache
  (:require [clojure.java.io :as io]))

(def ^:private ^java.io.File cache-dir (io/file "." "poms" "clojars"))

(defn exists?
  "Does the cache exist on local disk?"
  []
  (.exists cache-dir))

(defn create!
  "Creates the cache."
  []
  (io/make-parents (io/file cache-dir "dummy.txt")))

(defn file
  "Returns a File object for the cached version of the given path.  Note: does
  not check if the given file actually exists in the cache."
  ^java.io.File [path]
  (when path
    (io/file cache-dir path)))
