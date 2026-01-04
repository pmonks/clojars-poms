;
; Copyright 2025 Peter Monks
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

(ns clojars-poms.poms
  (:require [clojars-poms.index :as ci]
            [clojars-poms.sync  :as cs]))

; A monitorable counter of how many POMs have been synced
(def sync-count (atom 0))

; Note: doesn't support deletion (though Clojars itself may not allow deletion anyway? 🤔)
(defn sync!
  "Syncs POMs from Clojars to the local cache directory, either just the latest
  versions (arg is true), or all of them (arg is false). Throws if the index
  hasn't been downloaded yet."
  [latest-versions-only?]
  (cs/sync-files! sync-count (ci/pom-paths latest-versions-only?)))
