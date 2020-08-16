(ns cthulu-client.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as rf]
   [cthulu-client.events :as events]
   [cthulu-client.views :as views]
   [cthulu-client.config :as config]
   [wscljs.client :as websocket]
   [cljs.reader :as reader]
   ))


(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (rf/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn init []
  (rf/dispatch-sync [::events/initialize-db])
  (rf/dispatch [::events/set-websocket
                      (websocket/create
                       ;"ws://localhost:8901"
                       "ws://85.230.99.215:8901"
                       {:on-message #(rf/dispatch [::events/receive-websocket-message
                                                   (reader/read-string (.-data %))])})])
  (dev-setup)
  (mount-root))
