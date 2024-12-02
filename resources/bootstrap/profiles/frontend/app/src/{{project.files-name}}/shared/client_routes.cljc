;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

{{=<< >>=}}
(ns <<project.name>>.shared.client-routes
  #?(:cljs (:require [<<project.name>>.client.view.landing :as view.landing]
                     [<<project.name>>.client.view.not-found :as view.not-found])
     :clj (:require [ring.util.response :as r])))

#?(:clj
   (def index-route
     {:get {:no-doc true
            :handler (fn [_]
                       (r/resource-response "<<project.files-name>>/index.html"))}}))

(def routes
  ["/"
   ["" #?(:clj index-route :cljs view.landing/route-config)]
   ["not-found" #?(:clj index-route :cljs view.not-found/route-config)]])
