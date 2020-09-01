(ns cthulu-client.views
  (:require
   [re-frame.core :as rf]
   [cthulu-client.subs :as subs]
   [cthulu-client.events :as events]
   [clojure.string :refer [capitalize]]))

(def card-width 96)
(def card-height 128)
(def card-border-radius 6)

(def power-descriptions
  {:insanitys-grasp "If this card is unrevealed in front of you, you cannot communicate."
   :paranoia        "Control the flashlight for the rest of this round."
   :evil-presence   "Return all your unrevealed cards to the reshuffle pile."})

(def power-colors
  {:insanitys-grasp "green"
   :paranoia        "darkblue"
   :evil-presence   "red"})

(defn object-of-power?
  [entity]
  (contains? power-descriptions entity))

(defn card-view [entity clickable?]
  (let [card-div (if clickable? :div.clickable-card :div)
        image-url (str "url(\"img/"
                       (if (object-of-power? entity) "futile" (name entity))
                       ".png\")")]
    [card-div {:title (power-descriptions entity)
               :style {:border-radius       card-border-radius
                       :background-image    (when-not (= entity :unknown) image-url)
                       :background-position "center"
                       :background-size     "cover"
                       :width               card-width
                       :height              card-height
                       :display             "flex"
                       :flex-direction      "column"
                       :justify-content     "space-between"
                       :align-items         "center"
                       :color               "black"
                       :border              "1px solid black"
                       :text-align          "center"}}
     (when (object-of-power? entity)
       [:div {:style {:display "contents" :text-shadow "1px 1px 0 white,
                                                        1px -1px 0 white,
                                                        -1px 1px 0 white,
                                                        -1px -1px 0 white,
                                                        1px 0px 0 white,
                                                        -1px 0px 0 white,
                                                        0px 1px 0 white,
                                                        0px -1px white"}}
        [:h5 {:style {:margin 5 :font-weight "lighter" :color (power-colors entity)}}
         (.toUpperCase (clojure.string/replace (name entity) #"-" " "))]
        [:div {:style {:padding   "0px 4px 8px" :font-size 12}}
         (power-descriptions entity)]])]))

(defn player-card-view []
  (let [my-turn?       (rf/subscribe [::subs/my-turn?])
        animation-data (rf/subscribe [::subs/animate-flip-card])]
    (fn [card owner-id my-card?]
      (let [entity                       (:entity card)
            [flip-card-id transform-val] @animation-data
            clickable?                   (and @my-turn? (nil? flip-card-id) (not my-card?))]
        [:div {:on-click #(when clickable? (rf/dispatch [::events/reveal-card owner-id (:id card)]))
               :style {:border-radius    card-border-radius
                       :background-color "#505050"
                       :margin-left      (if my-card? 0 -32)
                       :transition       "transform .5s ease-in-out"
                       :transform        (when (= flip-card-id (:id card)) transform-val)}}
         [card-view entity clickable?]]))))

(defn hand-view []
  (let [my-id (rf/subscribe [::subs/client-id])]
    (fn [cards owner-id]
      (let [my-cards? (= owner-id @my-id)]
        [:div {:style {:display          "flex"
                       :justify-content  "center"
                       :flex-direction   "row-reverse"
                       :align-items      "center"
                       :padding-left     (if my-cards? 0 32)}}
         (for [card cards]
           ^{:key (:id card)} [player-card-view card owner-id my-cards?])]))))

(defn player-view [{name  :name
                    id    :id
                    role  :role
                    cards :cards}]
  [:div {:style {:display          "flex"
                 :flex-direction   "column"
                 :align-items      "center"}}
   [:h2 (condp = role
          :investigator {:title "Investigator"
                         :style {:color "turquoise"}}
          :cultist {:title "Cultist!"
                    :style {:color "red"}}
          nil)
    (when (= id  @(rf/subscribe [::subs/player-id-in-turn]))
      [:span {:style {:margin-left -35 :font-size 22}}  "🔦 "])
    name]
   [hand-view cards id]])

(defn game-board []
  (let [revealed-cards& (rf/subscribe [::subs/revealed-cards])
        players& (rf/subscribe [::subs/players])
        actions-left& (rf/subscribe [::subs/actions-left-for-current-round])
        waiting?& (rf/subscribe [::subs/waiting?])
        log& (rf/subscribe [::subs/log])
        winners @(rf/subscribe [::subs/winners])
        current-round @(rf/subscribe [::subs/current-round])
        client-id @(rf/subscribe [::subs/client-id])
        grouped-revealed-cards (group-by :entity @revealed-cards&)
        ;entities-of-revealed-cards (keys grouped-revealed-cards)
        player-count (count @players&)
        angle (/ (* 2 Math/PI) player-count)
        vertical-radius (max 320 (- (* 0.5 (.-innerHeight js/window)) 138))
        horizontal-radius (max 480 (min (* 2 vertical-radius) (* 0.45 (.-innerWidth js/window))))]
    [:div {:style {:position      "relative"
                   :margin-top    (if (even? player-count) 64 (+ -64 (* 8 player-count)))
                   :width         (* 2 horizontal-radius)
                   :height        (* 2 vertical-radius)
                   :border-radius "50%"}}
     [:div {:style {:width           "100%"
                    :height          "100%"
                    :display         "flex"
                    :flex-direction  "column"
                    :justify-content "center"
                    :align-items     "center"}}
      (if-not winners
        [:p "actions left: " @actions-left&]
        [:div {:style {:margin-top     -60
                       :margin-bottom  12
                       :display        "flex"
                       :flex-direction "column"
                       :align-items    "center"}}
         [:h1 {:style {:color      (condp = winners
                                     :investigators "turquoise"
                                     :cultists "red")}}
               (capitalize (name winners)) " win!"]
         [:div {:style {:align-self   "stretch"
                        :display "flex"
                        :justify-content "space-between"}}
          [:input {:type     "button"
                   :on-click #(rf/dispatch [::events/start-game])
                   :value    "same players"
                   :disabled @waiting?&
                   :style {:display "block" :z-index 10 :cursor (when-not @waiting?& "pointer")}}]
          "New game?"
          [:input {:type     "button"
                   :on-click #(rf/dispatch [::events/clear-game])
                   :value    "new players"
                   :disabled @waiting?&
                   :style {:display "block" :z-index 10 :cursor (when-not @waiting?& "pointer")}}]]])
      [:div {:style {:display "flex"}}
       (for [[entity cards-of-entity] grouped-revealed-cards]
         ^{:key entity}
         [:div {:style {:margin         1
                        :display        "flex"
                        :flex-direction "column"
                        :align-items    "center"
                        :color          "#e4e4e4"}}
          [card-view entity false]
          [:p (count cards-of-entity)]])]
      [:div {:style {:min-height 1
                     :height     1
                     :max-height 1}}
       (let [player-name-fn (zipmap (map :id @players&) (map :name @players&))]
         (for [[idx action] (map-indexed vector (take-last 10 @log&))]
           (let [revealed-entity (:revealed-card-entity action)
                 from-current-round? (= current-round (:round action))]
             ^{:key idx}
             [:div {:style {:font-size 12
                            :color (when-not from-current-round? "grey")}}
              (player-name-fn (:acting-player-id action))
              " reveals a card from "
              (player-name-fn (:target-player-id action))
              ": "
              [:span {:style {:color (when from-current-round?
                                       (condp = revealed-entity
                                         :elder-sign "turquoise"
                                         :cthulu     "red"
                                         :futile     "darkgrey"
                                         "purple"))}}
               (name revealed-entity)]])))]]
     (for [player @players&]
       (let [player-angle (+ (* angle
                                (- (:id player) client-id))
                             (/ Math/PI 2))]
         ^{:key (:id player)}
         [:div {:style {:display     "block"
                        :position    "absolute"
                        :top         "50%"
                        :left        "50%"
                        :width       (* 5 card-width)
                        :height      194
                        :margin-left (/ (* 5 card-width) -2)
                        :margin-top  -96}}
          [:div {:style {:margin-left     (* horizontal-radius (Math/cos player-angle))
                         :margin-top      (* vertical-radius (Math/sin player-angle))
                         :width           "100%"
                         :display         "flex"
                         :justify-content "center"}}
           [player-view player]]]))]))

(defn choose-name []
  (let [current-input-value (rf/subscribe [::subs/name-input-value])]
    [:form {:action    "#"
            :on-submit #(rf/dispatch [::events/set-name @current-input-value])
            :style     {:flex-grow       1
                        :display         "flex"
                        :flex-direction  "column"
                        :justify-content "center"}}
     [:label {:style {:margin 6}}
      "Choose your username"]
     [:br]
     [:input {:type      "text"
              :auto-focus true
              :value     @current-input-value
              :on-change #(rf/dispatch [::events/change-name-input (.-value (.-target %))])}]
     [:br]
     [:input {:style {:margin "8px 32px"}
              :type  "submit"
              :value "Select"}]]))

(defn waiting-room []
  (let [players @(rf/subscribe [::subs/players])
        waiting? @(rf/subscribe [::subs/waiting?])
        can-start-game? (zero? @(rf/subscribe [::subs/client-id]))]
    [:div {:style {:flex-grow       1
                   :display         "flex"
                   :flex-direction  "column"
                   :align-items     "center"
                   :justify-content "center"}}
     [:h2 "Waiting for players"]
     [:div {:style {:display "flex"}}
      (for [name (map :name players)]
        ^{:key name} [:h3 {:style {:margin 32}} name])]
     (when can-start-game?
       [:input {:type     "button"
                :value    "Start game"
                :disabled waiting?
                :on-click #(rf/dispatch [::events/start-game])}])]))

(defn main-panel []
  (let [client-player-name (rf/subscribe [::subs/client-player-name])
        joining-room? (rf/subscribe [::subs/joining-room?])
        active-game? (rf/subscribe [::subs/active-game?])]
    [:div {:style {:background-color "#202020"
                   :color            "lightgrey"
                   :margin           12
                   :min-width        1080
                   :width            "calc(100% - 24px)"
                   :border-radius    8
                   :flex-grow        1
                   :display          "flex"
                   :flex-direction   "column"
                   :align-items      "center"}}
     [:div {:style {:margin-bottom 32
                    :font-family   "cursive"
                    :font-size     26
                    :text-shadow   "0px -2px red, 0px 2px black"}}
      "DON'T MESS WITH"
      [:span {:style {:padding  16
                      :font-size 36
                      :font-family "\"Times New Roman\", Times, serif"
                      :font-weight "950"
                      :color "green"
                      :text-shadow "1px 4px black"}}
       "CTHULU"]]
     (cond
       (nil? @client-player-name)
       [choose-name]

       @joining-room?
       [:h1 {:style {:flex-grow   1
                     :display     "flex"
                     :align-items "center"}}
        "Joining room..."]

       (not @active-game?)
       [waiting-room]
       
       :else
       [game-board])]))

;(comment @re-frame.db/app-db)
(comment (rf/dispatch [::events/set-players [{:name  "Miguel"
                                              :cards [:sten :Cthulu :sten :unknown]}
                                             {:name  "Sebastian"
                                              :cards [:elder-sign :sten :sten :unknown]}
                                             {:name  "Angelina"
                                              :cards [:sten :elder-sign :unknown :sten]}
                                             {:name  "Sebastian1"
                                              :cards [:elder-sign :sten :sten :unknown]}
                                             {:name  "Angelina1"
                                              :cards [:sten :elder-sign :unknown :sten]}
                                             {:name  "Sebastian2"
                                              :cards [:elder-sign :sten :sten :unknown]}
                                             {:name  "Angelina2"
                                              :cards [:sten :elder-sign :unknown :sten]}
                                             {:name  "Sebastian3"
                                              :cards [:elder-sign :sten :sten :unknown]}
                                             {:name  "Angelina3"
                                              :cards [:sten :elder-sign :unknown :sten]}
                                             {:name  "Lina"
                                              :cards [:elder-sign :unknown :sten :sten]}]]))