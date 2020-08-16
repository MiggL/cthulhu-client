(ns cthulu-client.events
  (:require
   [re-frame.core :as rf]
   [cthulu-client.db :as db]
   [cthulu-client.effects]))

(rf/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))

(rf/reg-event-db
 ::set-websocket
 (fn [db [_ websocket]]
   (assoc db :websocket websocket)))

(rf/reg-event-db
 ::set-game-state
 (fn [db [_ game-state]]
   (assoc db :game-state game-state)))

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
   {:db (assoc db :animate-flip-card [card-id "rotateY(90deg)"])
    :timeout {:event [::end-animate-card-flip target-player-id card-id card-entity]
              :time  500}}))

(rf/reg-event-fx
 ::receive-websocket-message
 (fn [{:keys [db]} [_ data]]
   (condp #(contains? %2 %1) data
     :game-state
     {:db       (-> db
                    (assoc :waiting? false)
                    ;(assoc :game-state (:game-state data))
                    (assoc :log (:action-log data))
                    (assoc :client-id (or (:client-id data) (:client-id db))))
      :dispatch (let [last-action (last (:action-log data))]
                  [::start-animate-card-flip
                   (:target-player-id last-action)
                   (:revealed-card-id last-action)
                   (:revealed-card-entity last-action)])
      :timeout {:event [::set-game-state (:game-state data)]
                :time  (if (and (get-in db [:game-state :player-id-in-turn])
                                (= (:log db) (drop-last (:action-log data))))
                         2500
                         0)}}
     
     :client-id
     {:db (-> db
              (assoc :client-id (:client-id data))
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
 ::reveal-card
 (fn [{:keys [db]} [_ owner-id card-id]]
   (when-not (:waiting? db)
     {:db (assoc db :waiting? true)
      :websocket-send {:socket (:websocket db)
                       :message {:type    "reveal-card"
                                 :content {:player-id        (:client-id db)
                                           :target-player-id owner-id
                                           :card-id          card-id}}}})))
