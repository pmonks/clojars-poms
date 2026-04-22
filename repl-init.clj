;
; Copyright 2019 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

;
; This script is intended to be used to initialise a REPL set up for easy
; experimentation with this project. e.g.:
;
;     clojure -M -i repl-init.clj -r
;
; Note: if you use `clj` to run this script, you may see strange output
; behaviour
;

(in-ns 'user)

(require '[clojure.pprint         :as pp])
(require '[clojure.string         :as s])
(require '[clojure.java.io        :as io])
(require '[clojure.xml            :as xml])
(require '[clojure.zip            :as zip])
(require '[clojure.data.zip.xml   :as zip-xml])
(require '[version-clj.core       :as ver])
(require '[clojars-poms.cache     :as cc])
(require '[clojars-poms.index     :as ci])
(require '[clojars-poms.parse     :as cpa])
(require '[clojars-poms.poms      :as cpo])
(require '[progress.indeterminate :as pi])
(require '[progress.determinate   :as pd])
(require '[wreck.api              :as re])
(require '[rencg.api              :as ncg])

(defn prompt-for-y-or-n?
  "Prompts the user with a yes or no question, returning a boolean indicating
  their answer (yes = true)."
  [msg]
  (print msg "[y/N] ")
  (flush)
  (s/starts-with? (s/lower-case (read-line)) "y"))

(def sync? (boolean (or (not (cc/exists?))
                        (not (ci/exists?))
                        (prompt-for-y-or-n? "\nCache exists; sync?"))))

(def latest-versions-only? (not (prompt-for-y-or-n? (str (if sync? "Sync & parse" "Parse") " all versions of all POMs" (when sync? " (WARNING: answering Y will dramatically increase runtime and disk and memory usage)") "?"))))

; Sync index
(when sync?
  (when-not (cc/exists?)
    (println "ℹ️ Cache doesn't exist; creating it")
    (cc/create!))
  (print "ℹ️ Syncing Clojars POM index... ")
  (pi/animate! :opts {:frames (:clocks pi/styles)}
    (ci/sync!))
  (println))

; Count how many POMs we'll be syncing & parsing
(print "ℹ️ Counting" (if latest-versions-only? "latest" "all") "POMs in index... ")
(pi/animate! :opts {:frames (:clocks pi/styles)}
  (def pom-count (ci/pom-count latest-versions-only?)))
(println)

; Sync POMs
(if sync?
  (let [start (System/currentTimeMillis)]
    (println "ℹ️ Syncing" pom-count "POMs from Clojars" (if latest-versions-only? "(latest only)..." "(all)..."))
    (flush)
    (pd/animate! cpo/sync-count
                 :opts {:total       pom-count
                        :redraw-rate 30
                        :style       (:coloured-ascii-boxes pd/styles)}
                 (cpo/sync! latest-versions-only?))   ; This can take a while, though a JVM with virtual thread support helps...
    (let [time-taken (long (Math/ceil (/ (- (System/currentTimeMillis) start) 1000)))]
      (println (format "ℹ️ Done - %d POMs synced in %ds (%.2f/s)"
                       pom-count
                       time-taken
                       (double (/ pom-count time-taken))))))
  (println "ℹ️ Skipping Clojars POM sync"))

; Parse POMs from disk
(let [start (System/currentTimeMillis)]
  (println (str "ℹ️ Parsing " pom-count " POMs " (if latest-versions-only? "(latest only)..." "(all)...")))
  (flush)
  (def poms (pd/animate! cpa/parse-count
                         :opts {:total       pom-count
                                :redraw-rate 30
                                :style       (:coloured-ascii-boxes pd/styles)}
                         (doall (cpa/parse latest-versions-only?))))
  (let [time-taken       (long (Math/ceil (/ (- (System/currentTimeMillis) start) 1000)))
        parsed-pom-count (count poms)]
    (println (format "ℹ️ Done - %d POMs parsed (✅: %d, ❌: %d) in %ds (%.2f/s)"
                     pom-count
                     parsed-pom-count
                     (- pom-count parsed-pom-count)
                     time-taken
                     (double (/ pom-count time-taken))))))

; Handy utility fns
(defn gav-map
  "Returns the GAV (as a Map) of the given XML element. The map has some or all
  of these keys:

  * :group-id - String
  * :artifact-id - String
  * :version - String"
  [elem]
  (when elem
    (let [group-id    (zip-xml/xml1-> elem :groupId    zip-xml/text)
          artifact-id (zip-xml/xml1-> elem :artifactId zip-xml/text)
          version     (zip-xml/xml1-> elem :version    zip-xml/text)]
      (merge {}
             (when-not (s/blank? group-id)    {:group-id    group-id})
             (when-not (s/blank? artifact-id) {:artifact-id artifact-id})
             (when-not (s/blank? version)     {:version     version})))))

(defn gav
  "Returns the GAV (as a String) of the given XML element."
  [elem]
  (when-let [{group-id    :group-id
              artifact-id :artifact-id
              version     :version} (gav-map elem)]
    (str (when group-id    (str group-id "/"))
         (when artifact-id artifact-id)
         (when version     (str "@" version)))))

(defn gav->clojars-url
  "Returns the Clojars URL of the *directory* containing the POM for the given
  GAV (a String in groupId/artifactId@version format)."
  [gav]
  (when-not (s/blank? gav)
    (let [[ga v] (s/split gav #"@")
          [g  a] (s/split ga  #"/")]
      (str "https://repo.clojars.org/"
           (s/replace g "." "/")
           "/" a "/" v "/"))))

(def dependencies (apply merge (map #(let [gav  (gav-map %)
                                           deps (map gav-map (zip-xml/xml-> % :dependencies :dependency))]
                                       {gav deps})
                                    poms)))

;(def inverted-dependencies ....####TODO)

(defn find-deps-by-license
  "Find all deps where the given re 'finds' within the license name."
  [re]
  (when re
    (some-> (map gav (filter #(when-let [license-name (zip-xml/xml1-> % :licenses :license :name zip-xml/text)]
                                (ncg/re-find re license-name))
                             poms))
            seq
            set)))

(defn find-deps-by-license-name
  "Find all deps with the given license name (case insensitively)."
  [lic]
  (when-not (s/blank? lic)
    (find-deps-by-license (re/fgrp "i" #"\A" (re/esc lic) #"\z"))))

;  (when-not (s/blank? lic)
;    (some-> (map gav (filter #(= lic (zip-xml/xml1-> % :licenses :license :name zip-xml/text)) poms))
;            seq
;            set)))

(defn find-deps-containing-fragment-in-name
  "Find all deps with the given fragment in the license name (case insensitively)."
  [fragment]
  (when-not (s/blank? fragment)
    (find-deps-by-license (re/fgrp "i" (re/esc fragment)))))

;  (when-not (s/blank? fragment)
;    (some-> (map gav (filter #(when-let [name (zip-xml/xml1-> % :licenses :license :name zip-xml/text)] (s/includes? name fragment)) poms))
;            seq
;            set)))

; Print help on startup
(defn help
  []
  (println "\ndata:")
  (println "  * poms - parsed poms, as a sequence of XML zippers")
  (println "  * dependencies - map of dependencies, where each key is a GAV string and each value is a sequence of GAV maps it depends on")
  ;(println "  * inverse-dependencies - sequence of dependencies, expressed as ####")

  (println "\nfns:")
  (println "  * (gav-map xml)")
  (println "  * (gav xml)")
  (println "  * (gav->clojars-url gav-string)")
  (println "  * (find-deps-by-license re)")
  (println "  * (find-deps-by-license-name license-name-string)")
  (println "  * (find-deps-containing-fragment-in-name license-fragment-string)")

  (println "\nexample handy exps:")
  (println "  * (sort (distinct (filter (complement s/blank?) (map #(zip-xml/xml1-> % :licenses :license :name zip-xml/text) poms))))")
  (println "  * (frequencies (filter (complement s/blank?) (map #(zip-xml/xml1-> % :licenses :license :name zip-xml/text) poms)))")

  (println "\nUse (help) to see this message at any time.\n"))

(help)

(comment
; Find dependencies with a specific license name
(find-deps-by-license-name "MIT/Apache-2.0/BSD-3-Clause")

; Get license names & URLs
(def license-names (sort-by s/trim (filter #(not (s/blank? %)) (map #(zip-xml/xml1-> % :licenses :license :name zip-xml/text) poms))))
(def license-urls  (sort-by s/trim (filter #(not (s/blank? %)) (map #(zip-xml/xml1-> % :licenses :license :url  zip-xml/text) poms))))

; Unique license names & URLs
(def unique-license-names (distinct license-names))
(def unique-license-urls  (distinct license-urls))

; Write unique license names & URLs to an EDN file
(with-open [w (io/writer (io/file "clojars-licenses.edn"))]
  (pp/pprint {:names unique-license-names :urls unique-license-urls} w))
)

(comment
; Write unique license names & URLs to lice-comb unit test
(with-open [w (io/writer (io/file "lice-comb-tests.clj"))]
  (run! #(.write w (str "    (is (valid= #{\"\"} (name->expressions \"" (s/escape % {\" "\\\""}) "\")))\n")) unique-license-names)
  (.write w "\n")
  (run! #(.write w (str "    (is (valid= #{\"\"} (uri->expressions \"" (s/escape % {\" "\\\""}) "\")))\n")) unique-license-urls))
)

(comment
; How many POMs have a license name
(count license-names)

; How many unique license names
(count (distinct license-names))

; Frequencies
(sort-by second (frequencies license-names))

; 10 most common license names
(pp/pprint (take 10 (reverse (sort-by second (frequencies license-names)))))

; Distinct names
(sort (distinct license-names))

; Count how many have a license
(count (filter #(not (s/blank? %)) (map #(zip-xml/xml1-> % :licenses :license zip-xml/text) poms)))

; poms without licenses
(def poms-without-licenses (filter #(s/blank? (zip-xml/xml1-> % :licenses :license zip-xml/text)) poms))

(sort (map gav poms-without-licenses))   ; Note: large output, which rlwrap barfs on

)

