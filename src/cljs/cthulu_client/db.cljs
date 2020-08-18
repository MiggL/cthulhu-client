(ns cthulu-client.db)

(def default-db
  {:client-player-name nil
   :client-id          nil
   :name-input-value   ""
   :game-state         {:players           []
                        :revealed-cards    []
                        :player-id-in-turn nil
                        :round             0}
   :log                []
   :animate-flip-card  [] ; [card-id css-transform-value]
   :websocket          nil
   :waiting?           false})
