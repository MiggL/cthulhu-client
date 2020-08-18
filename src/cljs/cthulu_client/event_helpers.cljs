(ns cthulu-client.event-helpers)

(defn clear-game-state
  [db]
  (assoc db :game-state {:players           []
                         :revealed-cards    []
                         :player-id-in-turn nil
                         :round             0}))
