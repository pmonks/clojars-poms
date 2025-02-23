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

(ns clojars-poms.parse
  (:require [clojure.string        :as s]
            [clojure.java.io       :as io]
            [clojure.xml           :as xml]
            [clojure.zip           :as zip]
            [clojure.data.zip.xml  :as zip-xml]
            [clojure.tools.logging :as log]
            [version-clj.core      :as ver]
            [embroidery.api        :as e]))

(defn list-pom-files
  "Returns a lazy sequence of all the POM files in dir."
  [dir]
  (filter #(and (.endsWith (.getName ^java.io.File %) ".pom")
                           (.canRead ^java.io.File %)
                           (not (.isDirectory ^java.io.File %)))
          (file-seq (io/file dir))))

(defn latest-versions-only
  "Takes a sequence of POM files, assumed to be in a standard Maven layout (i.e.
  ..whatever../groupid/artifactId/version/artifact-version.pom), and returns a
  lazy sequence containing just the latest version's POM file for each
  groupId/artifactId."
  [pom-files]
  (let [grouped-poms (group-by #(s/join "/" (drop-last 2 (s/split (str %) #"/"))) pom-files)]
    ; We use regular pmap here, because this workload is CPU bound so there's no benefit in using virtual threads
    (pmap #(last (sort-by (fn [f] (last (drop-last (s/split (str f) #"/"))))
                          ver/version-compare
                          (val %)))
          grouped-poms)))

(defn pom-count
  "Counts the POM files in the given directory."
  ([dir] (pom-count dir true))
  ([dir latest-versions-only?]
    (let [all-pom-files (list-pom-files dir)]
      (count (if latest-versions-only?
               (latest-versions-only all-pom-files)
               all-pom-files)))))

(defn pom-file->xml-zipper
  "Parses a single POM file (a java.io.File) into an XML zipper structure (as
  per clojure.zip/xml-zip).  Assumes UTF-8 data and attempts to silently ignore
  malformed input and unmappable characters."
  [^java.io.File pom-file]
  (let ; This stuff doesn't seem to work any more...
       ;[xml-decoder     (doto (.newDecoder            java.nio.charset.StandardCharsets/UTF_8)
       ;                       (.onMalformedInput      java.nio.charset.CodingErrorAction/REPLACE)
       ;                       (.onUnmappableCharacter java.nio.charset.CodingErrorAction/REPLACE))
       ; xml-is          (org.owasp.dependencycheck.xml.XmlInputStream.                           ; Strip leading whitespace, BOM, etc.
       ;                   (org.apache.commons.io.input.ReaderInputStream.                        ; Convert reader back to UTF-8 encoded InputStream (*blech*)
       ;                     (java.io.InputStreamReader. (io/input-stream pom-file) xml-decoder)  ; Replace invalid UTF-8 byte sequences into a reader
       ;                     java.nio.charset.StandardCharsets/UTF_8))]
       [xml-is          (org.owasp.dependencycheck.xml.XmlInputStream. (io/input-stream pom-file))]
    (try
      (zip/xml-zip (xml/parse xml-is))
      (catch Exception e
        (log/warn (str "Unexpected exception while parsing " pom-file (.getMessage e)))
        nil))))

; A monitorable counter of how many POMs have been parsed
(def parse-count (atom 0))

(defn parse-pom-files
  "Returns a lazy sequence of XML zippers for all of the POM files in the given
  directory and its subdirectories, defaulting to just the latest version of
  each groupId/artifactId."
  ([dir] (parse-pom-files dir true))
  ([dir latest-versions-only?]
    (reset! parse-count 0)
    (let [pom-files (if latest-versions-only?
                      (latest-versions-only (list-pom-files dir))
                      (list-pom-files dir))]
      (filter identity (doall
                         (e/bounded-pmap* 8192  ; Cap concurrency at 8192, to try to avoid running out of file handles (limit on maxOS is normally 10240)
                                          #(let [xml-zip (pom-file->xml-zipper %)]
                                             (swap! parse-count inc)
                                             xml-zip)
                                          pom-files))))))

(defn gav
  "Parses a POM XML element containing a Maven GAV, and returns it as a map with
  keys:

  * :group-id
  * :artifact-id
  * :version"
  [elem]
  (let [group-id    (zip-xml/xml1-> elem      :groupId    zip-xml/text)
        artifact-id (zip-xml/xml1-> elem      :artifactId zip-xml/text)
        version     (str (zip-xml/xml1-> elem :version    zip-xml/text))]
    (merge {}
           (when-not (s/blank? artifact-id) {:artifact-id artifact-id})
           (when-not (s/blank? group-id)    {:group-id group-id})
           (when-not (s/blank? version)     {:version  version}))))

(defn gav->string
  "Turns a gav (returned be `gav`) into a string. Can also be used with sort-by."
  [gav]
  (str (:group-id gav)
       (when (and (:group-id gav) (:artifact-id gav)) "/")
       (:artifact-id gav)
       (when (:version gav) (str "@" (:version gav)))))
