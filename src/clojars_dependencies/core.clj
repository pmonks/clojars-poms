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

(ns clojars-dependencies.core
  (:require [clojure.string       :as s]
            [clojure.java.io      :as io]
            [clojure.xml          :as xml]
            [clojure.zip          :as zip]
            [clojure.data.zip.xml :as zip-xml]
            [version-clj.core     :as ver]
            [loom.graph           :as g]))

(defn rsync
  [& args]
  (let [exit-code (-> (ProcessBuilder. ^java.util.List (cons "rsync" args))
                      (.inheritIO)
                      (.start)
                      (.waitFor))]
    (if (not= 0 exit-code)
      (throw (Exception. "rsync failed - see stderr for details")))))

(defn rsync-poms
  [source target]
  (rsync "-zarv" "--prune-empty-dirs" "--delete" "--include=*/" "--include=*.pom" "--exclude=*" source target))

(defn cljgav
  "Parses a POM XML element containing a Maven GAV, and returns it in Clojure format: [\"[groupId/]artifactId\" \"versionStr\"]."
  [root]
  (let [group-id    (zip-xml/xml1-> root      :groupId    zip-xml/text)
        artifact-id (zip-xml/xml1-> root      :artifactId zip-xml/text)
        version     (str (zip-xml/xml1-> root :version    zip-xml/text))]
    (if (not (s/blank? artifact-id))
      (if (not (s/blank? group-id))
        [(str group-id "/" artifact-id) version]
        [artifact-id version]))))

(defn parse-pom-file
  "Parses a single POM file (a java.io.File) into the structure: [projectCljGAV [dependencyCljGAVs, ...]] (see cljgav for the format of each CLJGAV)."
  [^java.io.File pom-file]
  (try
    ; We do all of this because some of the POMs in Clojars are invalid in ways that are trivial to fix (leading whitespace, BOM, invalid UTF-8 byte sequences)
    (let [xml-decoder     (doto (.newDecoder java.nio.charset.StandardCharsets/UTF_8)
                                (.onMalformedInput      java.nio.charset.CodingErrorAction/REPLACE)
                                (.onUnmappableCharacter java.nio.charset.CodingErrorAction/REPLACE))
          xml-is          (org.owasp.dependencycheck.xml.XmlInputStream.                           ; Strip leading whitespace, BOM, etc.
                            (org.apache.commons.io.input.ReaderInputStream.                        ; Convert reader back to UTF-8 encoded InputStream (*blech*)
                              (java.io.InputStreamReader. (io/input-stream pom-file) xml-decoder)  ; Replace invalid UTF-8 byte sequences into a reader
                              java.nio.charset.StandardCharsets/UTF_8))
          root            (zip/xml-zip (xml/parse xml-is))
          project-gav     (cljgav root)
          dependency-gavs (seq (remove nil? (map cljgav (zip-xml/xml-> root :dependencies :dependency))))]
      (if project-gav
        [project-gav dependency-gavs]))
    (catch Exception e
      (print (str "⚠️ Unable to parse POM " (.getName pom-file) ": " e "\n"))
      (flush))))

(defn parse-pom-files
  "Parses all POM files in the given directory, and returns a sequence of parsed POMs (see parse-pom-file for the format of each entry in the sequence)."
  [poms-directory]
  (let [pom-files (filter #(and (.endsWith (.getName ^java.io.File %) ".pom")
                                 (.canRead ^java.io.File %)
                                 (not (.isDirectory ^java.io.File %)))
                           (file-seq (io/file poms-directory)))]
    (remove nil? (pmap parse-pom-file pom-files))))  ; Just watch pmap light those CPUs up!

(defn latest-project-versions
  "Filters out all but the latest version of each project (based on how version-clj compares Maven version strings)."
  [parsed-pom-files]
  (let [grouped-projects (group-by #(first (first %)) parsed-pom-files)]
    (pmap #(last (sort-by (fn [p] (second (first p))) ver/version-compare (second %))) grouped-projects)))

(defn dependencies
  "Returns a sequence of dependency pairs, in [fromCljGAV toCljGAV] format."
  [latest-project-versions]
  (let [version-numbers-elided (pmap #(vec [(first (first %)) (map first (second %))]) latest-project-versions)]
    (for [project version-numbers-elided
          from    [(first project)]
          to      (second project)]
      [from to])))

