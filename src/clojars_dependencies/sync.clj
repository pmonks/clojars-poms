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

(ns clojars-dependencies.sync
  (:require [clojure.string  :as s]
            [clojure.java.io :as io]
            [clojure.edn     :as edn]
            [hato.client     :as hc]))

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

(defn- download-file-from-clojars!
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

(defn sync-index!
  "Syncs the index file."
  [target]
  (try3sleep1 (download-file-from-clojars! target all-poms-list)))

(defn pom-count
  "Returns the number of POM files in the index. Throws if the index hasn't been downloaded yet."
  [target]
  (count (line-seq (io/reader (str target "/" all-poms-list)))))

; A monitorable counter of how many POMs have been synced
(def sync-count (atom 0))

; Note: doesn't support deletion (though Clojars itself may not allow deletion anyway? 🤔)
(defn sync-clojars-poms!
  "Syncs all POMs from Clojars to the target directory. Throws if the index hasn't been downloaded yet."
  [target]
  (reset! sync-count 0)
  (let [all-poms-file (str target "/" all-poms-list)
        all-pom-paths (map #(s/replace-first % "./" "") (with-open [r (io/reader all-poms-file)] (doall (line-seq r))))]
    (doall (pmap #(do (try3sleep1 (download-file-from-clojars! target %))
                      (swap! sync-count inc))
                 all-pom-paths)))
  nil)
