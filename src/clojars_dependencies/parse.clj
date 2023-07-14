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

(ns clojars-dependencies.parse
  (:require [clojure.string        :as s]
            [clojure.java.io       :as io]
            [clojure.xml           :as xml]
            [clojure.zip           :as zip]
            [clojure.data.zip.xml  :as zip-xml]
            [clojure.tools.logging :as log]
            [version-clj.core      :as ver]))

(defn list-pom-files
  "Returns a lazy sequence of all the POM files in dir."
  [dir]
  (filter #(and (.endsWith (.getName ^java.io.File %) ".pom")
                           (.canRead ^java.io.File %)
                           (not (.isDirectory ^java.io.File %)))
          (file-seq (io/file dir))))

(defn latest-versions-only
  "Takes a sequence of POM files, assumed to be in a standard Maven layout (i.e. ..whatever../groupid/artifactId/version/artifact-version.pom), and returns a lazy sequence containing just the latest version's POM file for each groupId/artifactId."
  [pom-files]
  (let [grouped-poms (group-by #(s/join "/" (drop-last 2 (s/split (str %) #"/"))) pom-files)]
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
  "Parses a single POM file (a java.io.File) into an XML zipper structure (as per clojure.zip/xml-zip).  Assumes UTF-8 data and attempts to silently ignore malformed input and unmappable characters."
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
        (log/warn e (str "Unexpected exception while parsing " pom-file))
        nil))))

; A monitorable counter of how many POMs have been parsed
(def parse-count (atom 0))

(defn parse-pom-files
  "Returns a lazy sequence of XML zippers for all of the POM files in the given directory and its subdirectories, defaulting to just the latest version of each groupId/artifactId."
  ([dir] (parse-pom-files dir true))
  ([dir latest-versions-only?]
    (reset! parse-count 0)
    (let [pom-files (if latest-versions-only?
                      (latest-versions-only (list-pom-files dir))
                      (list-pom-files dir))]
      (filter identity (pmap #(let [xml-zip (pom-file->xml-zipper %)]
                                (swap! parse-count inc)
                                xml-zip)
                             pom-files)))))

(defn gav
  "Parses a POM XML element containing a Maven GAV, and returns it as a map with keys:

  * :group-id
  * :artifact-id
  * :version"
  [elem]
  (let [group-id    (zip-xml/xml1-> elem      :groupId    zip-xml/text)
        artifact-id (zip-xml/xml1-> elem      :artifactId zip-xml/text)
        version     (str (zip-xml/xml1-> elem :version    zip-xml/text))]
    (if-not (s/blank? artifact-id)
      (merge {:artifact-id artifact-id}
             (when-not (s/blank? group-id) {:group-id group-id})
             (when-not (s/blank? version)  {:version  version}))
      (throw (ex-info "Found a GAV without an artifactId!" {:element elem})))))











(comment "Old code narrowly focused on Leiningen style dependencies"
(defn lein-gav
  "Parses a POM XML element containing a Maven GAV, and returns it in Leiningen format: [\"[groupId/]artifactId\" \"versionStr\"]."
  [root]
  (let [{:keys [group-id artifact-id version]} (gav root)]
    (when-not (s/blank? artifact-id)
      (when-not (s/blank? group-id)
        [(str group-id "/" artifact-id) version]
        [artifact-id version]))))

(defn pom-file->dependencies
  "Parses a single POM file (a java.io.File) into a tuple of: [projectLeinGAV #{dependencyLeinGAVs, ...}] (see lein-gav for the format of each LeinGAV)."
  [^java.io.File pom-file]
  (try
    ; We do all of this because some of the POMs in Clojars are invalid in ways that are trivial to fix (leading whitespace, BOM, invalid UTF-8 byte sequences)
    (let [root            (pom-file->xml-zipper pom-file)
          project-gav     (lein-gav root)
          dependency-gavs (some-> (seq (remove nil? (map lein-gav (zip-xml/xml-> root :dependencies :dependency))))
                                  set)]
      (when project-gav
        [project-gav dependency-gavs]))
    (catch Exception e
      (print (str "⚠️ Unable to parse POM " (.getName pom-file) ": " e "\n"))
      (flush))))

(defn parse-pom-files-in-dir
  "Returns a lazy sequence of parsed POMs in the given directory and subdirectories (see parse-pom-file for the format of each entry in the sequence)."
  [poms-directory]
  (let [pom-files (filter #(and (.endsWith (.getName ^java.io.File %) ".pom")
                                (.canRead ^java.io.File %)
                                (not (.isDirectory ^java.io.File %)))
                          (file-seq (io/file poms-directory)))]
    (remove nil? (pmap pom-file->dependencies pom-files))))

(defn latest-project-versions
  "Filters out all but the latest version of each project (based on how version-clj compares Maven version strings)."
  [parsed-pom-files]
  (let [grouped-projects (group-by #(first (first %)) parsed-pom-files)]
    (pmap #(last (sort-by (fn [p] (second (first p))) ver/version-compare (second %))) grouped-projects)))

(defn dependencies
  "Returns a sequence of dependency pairs, in [fromLeinGAV toLeinGAV] format."
  [latest-project-versions]
  (let [version-numbers-elided (pmap #(vec [(first (first %)) (map first (second %))]) latest-project-versions)]
    (for [project version-numbers-elided
          from    [(first project)]
          to      (second project)]
      [from to])))
)