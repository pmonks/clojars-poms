;
; Copyright 2019 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns clojars-poms.sync
  (:require [clojure.string     :as s]
            [clojure.java.io    :as io]
            [clojure.edn        :as edn]
            [hato.client        :as hc]
            [embroidery.api     :as e]
            [clojars-poms.cache :as cc]))

(def ^:private clojars-repo        "https://repo.clojars.org")
(def ^:private metadata-ext        ".meta.edn")
(def ^:private http-client         (delay (hc/build-http-client {:connect-timeout 10000
                                                                 :redirect-policy :always})))
(def ^:private default-headers     {"User-Agent" "https://github.com/pmonks/clojars-poms"})
(def ^:private max-http-concurrency 500)   ; Maximum concurrency to use for HTTP requests - Clojars rejects too many concurrent requests

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
  "Writes the given response to the cache, including metadata."
  [file-path {:keys [headers body]}]
  (let [filename      (str (cc/file file-path))
        etag          (s/trim (get headers "etag" ""))  ; Note: for some reason etag values returned by Clojars include quotes...
        last-modified (s/trim (get headers "last-modified" ""))]
    (io/make-parents filename)          ; Ensure parent directories exist
    (spit filename body)                ; Write content
    (spit (str filename metadata-ext)   ; Write metadata
          (pr-str (merge {}
                         (when-not (s/blank? etag)          {:etag          etag})
                         (when-not (s/blank? last-modified) {:last-modified last-modified})))))
  nil)

(defn ^:private try-n-times
  "Try f up to n times, with optional sleep in between - adapted from
  https://ericnormand.me/article/try-three-times"
  ([f n] (try-n-times f n 0))
  ([f n ^long sleep-ms]
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

(defn- sync-file-from-clojars!
  "Syncs a single file (identified by file-path) from Clojars, to the local
  cache directory. Does nothing if the given file hasn't changed (as per ETag /
  If-None-Match). Returns true if the file was downloaded, false if the cached
  version was up to date, and throws if an error occurred."
  [file-path]
  (let [metadata-file (cc/file (str file-path metadata-ext))
        etag          (when (.exists metadata-file) (with-open [r (io/reader metadata-file)] (:etag (edn/read (java.io.PushbackReader. r)))))
        clojars-url   (str clojars-repo "/" (encode-url-path file-path))
        response      (hc/get clojars-url
                              {:http-client       @http-client
                               :throw-exceptions? false
                               :headers           (merge default-headers
                                                         (when etag {"If-None-Match" etag}))})]
    (case (:status response)
      200   (do (write-file-and-meta! file-path response) true)
      304   false
      410   false   ; Not sure why, but Clojars returns 410s (GONE) for some of the POMs it lists in the "All POMs list"...
      :else (throw (ex-info (str "Unexpected status returned by Clojars: " (:status response)) response)))))

(defn sync-file!
  "Syncs a single file (identified by file-path) from Clojars, to the local
  cache directory. Does nothing if the given file hasn't changed (as per ETag /
  If-None-Match). Throws on errors or unexpected responses."
  [file-path]
  (try3sleep1 (sync-file-from-clojars! file-path))
  nil)

(defn sync-files!
 "Syncs all of the files (identified by file-paths) from Clojars, to the local
  cache directory, optionally incrementing atom a for each one."
  ([file-paths] (sync-files! nil file-paths))
  ([^clojure.lang.Atom a file-paths]
   (when a (reset! a 0))
   (doall (e/bounded-pmap* max-http-concurrency
                           #(do
                              (try3sleep1 (sync-file! %))
                              (when a (swap! a inc)))
                           file-paths))
   nil))
