(ns cthulu-client.subs
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub
 ::client-player-name
 (fn [db]
   (:client-player-name db)))

(rf/reg-sub
 ::client-id
 (fn [db]
   (:client-id db)))

(rf/reg-sub
 ::name-input-value
 (fn [db]
   (:name-input-value db)))

(rf/reg-sub
 ::joining-room?
 (fn [db]
   (and (:client-player-name db) (nil? (:client-id db)))))

(rf/reg-sub
 ::active-game?
 (fn [db]
   (some? (:player-id-in-turn (:game-state db)))))

(rf/reg-sub
 ::waiting?
 (fn [db]
   (:waiting? db)))

(rf/reg-sub
 ::players
 (fn [db]
   (:players (:game-state db))))

(rf/reg-sub
 ::player-id-in-turn
 (fn [db]
   (:player-id-in-turn (:game-state db))))

(rf/reg-sub
 ::my-turn?
 (fn [db]
   (= (:client-id db)
      (:player-id-in-turn (:game-state db)))))

(rf/reg-sub
 ::revealed-cards
 (fn [db]
   (:revealed-cards (:game-state db))))

(rf/reg-sub
 ::current-round
 (fn [db]
   (:round (:game-state db))))

(rf/reg-sub
 ::actions-left-for-current-round
 (fn [{game-state :game-state}]
   (let [player-count (count (:players game-state))
         round-action (:round-action game-state)]
     (inc (- player-count round-action)))))

(rf/reg-sub
 ::log
 (fn [db]
   (:log db)))

(rf/reg-sub
 ::winners
 (fn [{{players        :players
        revealed-cards :revealed-cards
        round          :round} :game-state}]
   (let [revealed-elder-signs (count (filter #(= :elder-sign (:entity %)) revealed-cards))
         revealed-cthulu? (seq (filter #(= :cthulu (:entity %)) revealed-cards))
         played-last-action? (= round 5)]
     (cond
       (= revealed-elder-signs (count players)) :investigators
       (or revealed-cthulu? played-last-action?) :cultists
       :else nil))))

(rf/reg-sub
 ::animate-flip-card
 (fn [db]
   (:animate-flip-card db)))
