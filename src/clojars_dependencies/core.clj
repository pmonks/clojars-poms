(ns clojars-dependencies.core
  (:require [clojure.string       :as s]
            [clojure.java.io      :as io]
            [clojure.xml          :as xml]
            [clojure.zip          :as zip]
            [clojure.data.zip.xml :as zip-xml]
            [version-clj.core     :as ver]
            [loom.graph           :as graph]))

(defn rsync
  [& args]
  (let [exit-code (-> (ProcessBuilder. (cons "rsync" args))
                      (.inheritIO)
                      (.start)
                      (.waitFor))]
    (if (not= 0 exit-code)
      (throw (Exception. "rsync failed - see stderr for details")))))

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
