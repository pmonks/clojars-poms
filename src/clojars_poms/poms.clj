;
; Copyright 2025 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

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
