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
  (:require [clojure.java.io       :as io]
            [clojure.xml           :as xml]
            [clojure.zip           :as zip]
            [clojure.tools.logging :as log]
            [embroidery.api        :as e]
            [clojars-poms.index    :as ci]
            [clojars-poms.cache    :as cc]))

(def ^:private max-file-concurrency 8192)   ; Maximum concurrency to use for file operations to avoid running out of file handles (limit on maxOS is normally 10240)

(defn- pom-file->xml-zipper
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

(defn parse
  "Returns a lazy sequence of XML zippers for the parseable POM files, , either
  just the latest versions (arg is true), or all of them (arg is false). Throws
  if the index hasn't been downloaded yet."
  [latest-versions-only?]
  (reset! parse-count 0)
  (filter identity (doall
                     (e/bounded-pmap* max-file-concurrency
                                      #(let [pom-file (cc/file %)
                                             xml-zip (pom-file->xml-zipper pom-file)]
                                         (swap! parse-count inc)
                                         xml-zip)
                                      (ci/pom-paths latest-versions-only?)))))
