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

(ns clojars-dependencies.core
  (:require [clojure.string       :as s]
            [clojure.java.io      :as io]
            [clojure.xml          :as xml]
            [clojure.zip          :as zip]
            [clojure.edn          :as edn]
            [clojure.data.zip.xml :as zip-xml]
            [hato.client          :as hc]
            [version-clj.core     :as ver]))

(def clojars-repo  "https://repo.clojars.org")
(def all-poms-list "all-poms.txt")

(def metadata-ext ".meta.edn")

(defonce http-client (hc/build-http-client {:connect-timeout 10000
                                            :redirect-policy :always}))

(defn- encode-url-path
  "Encodes a URL path (but NOT a query string)."
  [url-path]
  (-> url-path
      (s/replace "%" "%25")
      (s/replace " " "%20")
      (s/replace "!" "%21")
      (s/replace "#" "%23")
      (s/replace "$" "%24")
      (s/replace "&" "%26")
      (s/replace "'" "%27")
      (s/replace "(" "%28")
      (s/replace ")" "%29")
      (s/replace "*" "%2A")
      (s/replace "+" "%2B")
      (s/replace "," "%2C")
;      (s/replace "/" "%2F")
      (s/replace ":" "%3A")
      (s/replace ";" "%3B")
      (s/replace "=" "%3D")
      (s/replace "?" "%3F")
      (s/replace "@" "%40")
      (s/replace "[" "%5B")
      (s/replace "]" "%5D")))

(defn- write-file-and-meta!
  "Writes the given response to the given file-path, including metadata."
  [target file-path {:keys [headers body]}]
  (let [filename      (str target "/" file-path)
        etag          (s/trim (get headers "etag" ""))  ; Note: for some reason etag values returned by Clojars include quotes...
        last-modified (s/trim (get headers "last-modified" ""))]
    (io/make-parents filename)          ; Ensure parent directories exist
    (spit filename body)                ; Write content
    (spit (str filename metadata-ext)   ; Write metadata
          (pr-str (merge {}
                         (when-not (s/blank? etag)          {:etag          etag})
                         (when-not (s/blank? last-modified) {:last-modified last-modified})))))
  nil)

(defn- try-n-times
  "Try f up to n times, with optional sleep in between - adapted from https://ericnormand.me/article/try-three-times"
  ([f n] (try-n-times f n 0))
  ([f n sleep-ms]
    (if (zero? (dec n))
      (f)
      (try
        (f)
        (catch Throwable _
          (when (pos? sleep-ms) (Thread/sleep sleep-ms))
          (try-n-times f (dec n)))))))

(defmacro ^:private try3sleep1
  [& body]
  `(try-n-times (fn [] ~@body) 3 1000))

(defn download-file-from-clojars!
  "Downloads a single file (identified by file-path) from Clojars, to the specific target directory. Does nothing if the given file hasn't changed (as per ETag / If-None-Match). Returns true if the file was downloaded."
  [target file-path]
  (let [metadata-file (io/file (str target "/" file-path metadata-ext))
        etag          (when (.exists metadata-file) (with-open [r (io/reader metadata-file)] (:etag (edn/read (java.io.PushbackReader. r)))))
        clojars-url   (str clojars-repo "/" (encode-url-path file-path))
        response      (hc/get clojars-url
                              {:http-client       http-client
                               :throw-exceptions? false
                               :headers           (merge {"User-Agent" "com.github.pmonks/clojars-dependencies"}
                                                         (when etag {"If-None-Match" etag}))})]
    (case (:status response)
      200   (do (write-file-and-meta! target file-path response) true)
      304   false
      410   false   ; Not sure why, but Clojars returns 410s for some of the POMs it lists in the "All POMs list"...
      :else (throw (ex-info (str "Unexpected status returned by Clojars: " (:status response)) response)))))

; Note: doesn't support deletion (though Clojars itself may not allow deletion anyway? 🤔)
(defn sync-clojars-poms!
  "Syncs all POMs from Clojars to the target directory. Returns true if there were changes, false if not."
  [target]
  ; First, get the list of all poms
  (try3sleep1 (download-file-from-clojars! target all-poms-list))

  ; Second, spin through the list, downloading all poms
  (let [all-poms-file (str target "/" all-poms-list)
        all-pom-paths (map #(s/replace-first % "./" "") (with-open [r (io/reader all-poms-file)] (doall (line-seq r))))]
    (doall (pmap #(try3sleep1 (download-file-from-clojars! target %)) all-pom-paths))
    true)
  false)

; Note: clojars dropped support for rsync in 2019 - see https://github.com/clojars/clojars-web/issues/735
(comment
(defn rsync
  [& args]
  (let [exit-code (-> (ProcessBuilder. ^java.util.List (cons "rsync" args))
                      (.inheritIO)
                      (.start)
                      (.waitFor))]
    (when-not (= 0 exit-code)
      (throw (Exception. "rsync failed - see stderr for details")))))

(defn rsync-poms
  [source target]
  (rsync "-zarv" "--prune-empty-dirs" "--delete" "--include=*/" "--include=*.pom" "--exclude=*" source target))
)

(defn cljgav
  "Parses a POM XML element containing a Maven GAV, and returns it in Leiningen format: [\"[groupId/]artifactId\" \"versionStr\"]."
  [root]
  (let [group-id    (zip-xml/xml1-> root      :groupId    zip-xml/text)
        artifact-id (zip-xml/xml1-> root      :artifactId zip-xml/text)
        version     (str (zip-xml/xml1-> root :version    zip-xml/text))]
    (when-not (s/blank? artifact-id)
      (when-not (s/blank? group-id)
        [(str group-id "/" artifact-id) version]
        [artifact-id version]))))

(defn parse-pom-file
  "Parses a single POM file (a java.io.File) into the structure: [projectCljGAV [dependencyCljGAVs, ...]] (see cljgav for the format of each CLJGAV)."
  [^java.io.File pom-file]
  (try
    ; We do all of this because some of the POMs in Clojars are invalid in ways that are trivial to fix (leading whitespace, BOM, invalid UTF-8 byte sequences)
    (let [xml-decoder     (doto (.newDecoder            java.nio.charset.StandardCharsets/UTF_8)
                                (.onMalformedInput      java.nio.charset.CodingErrorAction/REPLACE)
                                (.onUnmappableCharacter java.nio.charset.CodingErrorAction/REPLACE))
          xml-is          (org.owasp.dependencycheck.xml.XmlInputStream.                           ; Strip leading whitespace, BOM, etc.
                            (org.apache.commons.io.input.ReaderInputStream.                        ; Convert reader back to UTF-8 encoded InputStream (*blech*)
                              (java.io.InputStreamReader. (io/input-stream pom-file) xml-decoder)  ; Replace invalid UTF-8 byte sequences into a reader
                              java.nio.charset.StandardCharsets/UTF_8))
          root            (zip/xml-zip (xml/parse xml-is))
          project-gav     (cljgav root)
          dependency-gavs (seq (remove nil? (map cljgav (zip-xml/xml-> root :dependencies :dependency))))]
      (when project-gav
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
    (remove nil? (pmap parse-pom-file pom-files))))

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

