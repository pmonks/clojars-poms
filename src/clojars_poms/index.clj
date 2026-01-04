;
; Copyright 2025 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns clojars-poms.index
  (:require [clojure.string     :as s]
            [clojure.java.io    :as io]
            [version-clj.core   :as ver]
            [clojars-poms.cache :as cc]
            [clojars-poms.sync  :as cs]))

(def ^:private               index-filename "all-poms.txt")
(def ^:private ^java.io.File index-file     (cc/file index-filename))

(defn exists?
  "Does the index exist in the local cache?"
  []
  (.exists index-file))

(defn sync!
  "Syncs the index file."
  []
  (cs/sync-file! index-filename))

(defn- all-pom-paths
  "Returns the paths (Strings) of all POMs listed in the index. Throws if the
  index hasn't been downloaded yet."
  []
  (with-open [r (io/reader index-file)]
    (doall (map #(s/replace-first % #"\A\./" "") (line-seq r)))))

(defn- latest-pom-paths
  "Returns the paths (Strings) of the latest version of each GA in the index.
  Throws if the index hasn't been downloaded yet."
  []
  (let [grouped-poms (group-by #(s/join "/" (drop-last 2 (s/split (str %) #"/"))) (all-pom-paths))]
    ; We use regular pmap here, because this workload is CPU bound so there's no benefit in using virtual threads
    (pmap #(last (sort-by (fn [f] (last (drop-last (s/split (str f) #"/"))))
                          ver/version-compare
                          (val %)))
          grouped-poms)))

(defn pom-paths
  "Returns the paths (Strings) of POMs listed in the index, either just the
  latest versions (arg is true), or all of them (arg is false). Throws if the
  index hasn't been downloaded yet."
  [latest-versions-only?]
  (if latest-versions-only?
    (latest-pom-paths)
    (all-pom-paths)))

(defn- all-pom-count
  "Returns the number of POM files listed in the index. Throws if the index
  hasn't been downloaded yet."
  []
  (count (all-pom-paths)))

(defn- latest-pom-count
  "Returns the number of POM files listed in the index. Throws if the index
  hasn't been downloaded yet."
  []
  (count (latest-pom-paths)))

(defn pom-count
  "Returns the number of POM files listed in the index, either just the latest
  versions (arg is true), or all of them (arg is false). Throws if the index
  hasn't been downloaded yet."
  [latest-versions-only?]
  (if latest-versions-only?
    (latest-pom-count)
    (all-pom-count)))
