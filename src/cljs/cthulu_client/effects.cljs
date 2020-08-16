(ns cthulu-client.effects
  (:require
   [re-frame.core :as rf]
   [wscljs.client :as websocket]))

(rf/reg-fx
 :timeout
 (fn [{:keys [event time]}]
   (js/setTimeout
    #(rf/dispatch event)
    time)))

(rf/reg-fx
 :websocket-send
 (fn [{:keys [socket message]}]
   (println "sending:")
   (println message)
   (websocket/send socket message)))
