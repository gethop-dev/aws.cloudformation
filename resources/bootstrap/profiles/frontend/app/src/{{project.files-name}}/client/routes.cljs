;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

{{=<< >>=}}
(ns <<project.name>>.client.routes
  (:require [<<project.name>>.client.navigation :as nav]
            [<<project.name>>.client.view.not-found :as view.not-found]
            [<<project.name>>.shared.client-routes :as client-routes]
            [<<project.name>>.shared.util.malli-coercion :as util.malli-coercion]
            [re-frame.core :as rf]
            [reitit.frontend :as reitit.fr]
            [reitit.frontend.controllers :as reitit.frc]
            [reitit.frontend.easy :as reitit.fre]))

(rf/reg-event-db
 ::apply-nav-route
 (fn [db [_ new-match]]
   (let [old-match (:current-route db)
         controllers (reitit.frc/apply-controllers (:controllers old-match) new-match)]
     (assoc db :current-route (assoc new-match :controllers controllers)))))

(rf/reg-event-fx
 ::navigated
 (fn [_ [_ new-match]]
   {:fx [[:dispatch [::apply-nav-route new-match]]]}))

(defn on-navigate [new-match]
  (if new-match
    (rf/dispatch [::navigated new-match])
    (rf/dispatch [::nav/push-state (:name view.not-found/route-config)])))

(def router
  (reitit.fr/router
   client-routes/routes
   {:data {:coercion util.malli-coercion/custom-reitit-malli-coercer}}))

(rf/reg-fx
 :do-init-routes
 (fn [_]
   (reitit.fre/start!
    router
    on-navigate
    {:use-fragment false
     :on-coercion-error (fn [_ _] (js/alert "Coercion error!"))})))

(rf/reg-event-fx
 ::init-routes
 (fn [_ _]
   {:fx [[:do-init-routes]]}))
