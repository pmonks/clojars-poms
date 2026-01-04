;
; Copyright 2025 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

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
