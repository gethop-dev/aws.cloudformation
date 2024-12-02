;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

{{=<< >>=}}
(ns <<project.name>>.static.root
  (:require [integrant.core :as ig]
            [<<project.name>>.shared.client-routes :as client-routes]
            [ring.util.response :as r]))

(defmethod ig/init-key :<<project.name>>.static/root [_ _]
  client-routes/routes)
