(ns cthulu-client.events
  (:require
   [re-frame.core :as rf]
   [cthulu-client.db :as db]
   [cthulu-client.effects]
   [cthulu-client.event-helpers :as helpers]))

(rf/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))

(rf/reg-event-db
 ::set-websocket
 (fn [db [_ websocket]]
   (assoc db :websocket websocket)))

(rf/reg-event-db
 ::set-game-state-and-log
 (fn [db [_ game-state log]]
   (-> db
       (assoc :game-state game-state)
       (assoc :log log)
       (assoc :animate-flip-card []))))

(rf/reg-event-db
 ::end-animate-card-flip
 (fn [db [_ target-player-id card-id card-entity]]
   (-> db
       (assoc :animate-flip-card [card-id "rotateY(0deg)"])
       (update-in
        [:game-state :players]
        (fn [players]
          (map (fn [player]
                 (if-not (= target-player-id (:id player))
                   player
                   (update
                    player
                    :cards
                    (fn [cards]
                      (map #(if (not= card-id (:id %)) % (assoc % :entity card-entity)) cards)))))
               players))))))

(rf/reg-event-fx
 ::start-animate-card-flip
 (fn [{:keys [db]} [_ target-player-id card-id card-entity]]
   (if (= target-player-id (:client-id db))
     {:db (assoc db :animate-flip-card [card-id "translate(0px, -32px)"])}
     {:db (assoc db :animate-flip-card [card-id "rotateY(90deg)"])
      :timeout {:event [::end-animate-card-flip target-player-id card-id card-entity]
                :time  500}})))

(rf/reg-event-fx
 ::receive-websocket-message
 (fn [{:keys [db]} [_ data]]
   (condp #(contains? %2 %1) data
     :game-state
     (let [{:keys [game-state action-log]} data
           animate? (and (get-in db [:game-state :player-id-in-turn])
                         (= (:log db) (drop-last action-log)))
           new-db (-> db
                      (assoc :waiting? false)
                      (assoc :client-id (or (:client-id data) (:client-id db))))]
       (if-not animate?
         {:db (-> new-db
                  (assoc :game-state game-state)
                  (assoc :log action-log))}
         {:db new-db
          :dispatch (let [{:keys [target-player-id
                                  revealed-card-id
                                  revealed-card-entity]} (last action-log)]
                      [::start-animate-card-flip target-player-id revealed-card-id revealed-card-entity])
          
          :timeout {:event [::set-game-state-and-log game-state action-log]
                    :time  3000}}))
     
     :client-id
     {:db (-> db
              (assoc :waiting? false)
              (assoc :client-id (:client-id data))
              (helpers/clear-game-state)
              (assoc-in [:game-state :players] (:players data)))})))

(rf/reg-event-db
 ::change-name-input
 (fn [db event]
   (assoc db :name-input-value (second event))))

(rf/reg-event-fx
 ::set-name
 (fn [{:keys [db]} [_ name]]
   {:db (assoc db :client-player-name name)
    :websocket-send {:socket  (:websocket db)
                     :message {:type "set-name" :content name}}}))

(rf/reg-event-db ; only used for debugging
 ::set-players
 (fn [db event]
   (assoc-in db [:game-state :players] (second event))))

(rf/reg-event-fx
 ::start-game
 (fn [{:keys [db]} _]
   (when-not (:waiting? db)
     {:db (assoc db :waiting? true)
      :websocket-send {:socket  (:websocket db)
                       :message {:type "start-game"}}})))

(rf/reg-event-fx
 ::clear-game
 (fn [{:keys [db]} _]
   (when-not (:waiting? db)
     {:db (assoc db :waiting? true)
      :websocket-send {:socket (:websocket db)
                       :message {:type "clear-game"}}})))

(rf/reg-event-fx
 ::reveal-card
 (fn [{:keys [db]} [_ owner-id card-id]]
   (when-not (or (:waiting? db) (seq (:animate-flip-card db)))
     {:db (assoc db :waiting? true)
      :websocket-send {:socket (:websocket db)
                       :message {:type    "reveal-card"
                                 :content {:player-id        (:client-id db)
                                           :target-player-id owner-id
                                           :card-id          card-id}}}})))
