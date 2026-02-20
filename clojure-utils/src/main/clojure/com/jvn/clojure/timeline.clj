(ns com.jvn.clojure.timeline
  "Data-driven timeline/animation builder for JVN using Clojure.
   
   Usage:
     (require '[com.jvn.clojure.timeline :as tl])
   
     (def anim
       (tl/timeline \"entrance\" 2000
         [{:type :move :entity \"codel\" :x 640 :y 468 :dur 340 :easing :ease-in-out}
          {:type :wait :dur 340}
          {:type :fade :entity \"codel\" :alpha 0.0 :dur 500}
          {:type :scale :entity \"codel\" :scale-x 2.0 :scale-y 2.0 :dur 200}
          {:type :rotate :entity \"codel\" :angle 45.0 :dur 300 :easing :ease-out}]))
   
     ;; Register for VNS interop:
     (tl/register! anim)"
  (:import [com.jvn.core.animation TimelineData TimelineData$Track TimelineData$Keyframe
            TimelineData$Property Easing Easing$Type TimelineRegistry]))

(defn- resolve-easing
  "Convert a keyword like :ease-in-out-cubic to an Easing.Type enum value."
  [k]
  (case (or k :linear)
    :linear            Easing$Type/LINEAR
    :ease-in-quad      Easing$Type/EASE_IN_QUAD
    :ease-out-quad     Easing$Type/EASE_OUT_QUAD
    :ease-in-out-quad  Easing$Type/EASE_IN_OUT_QUAD
    :ease-in-cubic     Easing$Type/EASE_IN_CUBIC
    :ease-out-cubic    Easing$Type/EASE_OUT_CUBIC
    :ease-in-out-cubic Easing$Type/EASE_IN_OUT_CUBIC
    :ease-in-quart     Easing$Type/EASE_IN_QUART
    :ease-out-quart    Easing$Type/EASE_OUT_QUART
    :ease-in-out-quart Easing$Type/EASE_IN_OUT_QUART
    :ease-in-expo      Easing$Type/EASE_IN_EXPO
    :ease-out-expo     Easing$Type/EASE_OUT_EXPO
    :ease-in-out-expo  Easing$Type/EASE_IN_OUT_EXPO
    :ease-in-sine      Easing$Type/EASE_IN_SINE
    :ease-out-sine     Easing$Type/EASE_OUT_SINE
    :ease-in-out-sine  Easing$Type/EASE_IN_OUT_SINE
    :ease-in-elastic   Easing$Type/EASE_IN_ELASTIC
    :ease-out-elastic  Easing$Type/EASE_OUT_ELASTIC
    :ease-in-out-elastic Easing$Type/EASE_IN_OUT_ELASTIC
    :ease-in-back      Easing$Type/EASE_IN_BACK
    :ease-out-back     Easing$Type/EASE_OUT_BACK
    :ease-in-out-back  Easing$Type/EASE_IN_OUT_BACK
    :ease-in-bounce    Easing$Type/EASE_IN_BOUNCE
    :ease-out-bounce   Easing$Type/EASE_OUT_BOUNCE
    :ease-in-out-bounce Easing$Type/EASE_IN_OUT_BOUNCE
    :custom            Easing$Type/CUSTOM
    Easing$Type/LINEAR))

(defn- resolve-property
  "Convert a keyword like :x to a TimelineData.Property enum value."
  [k]
  (case k
    :x       TimelineData$Property/X
    :y       TimelineData$Property/Y
    :rotation TimelineData$Property/ROTATION
    :scale-x TimelineData$Property/SCALE_X
    :scale-y TimelineData$Property/SCALE_Y
    :alpha   TimelineData$Property/ALPHA))

(defn- get-or-create-track
  "Get existing track for entity or create a new one."
  [^TimelineData data entity-name]
  (or (.getTrack data entity-name)
      (let [track (TimelineData$Track. entity-name)]
        (.addTrack data track)
        track)))

(defn- add-keyframe!
  "Add a keyframe to a track."
  [^TimelineData$Track track prop time-ms value easing]
  (.addKeyframe track
    (resolve-property prop)
    (TimelineData$Keyframe. (double time-ms) (double value) (resolve-easing easing))))

(defn- process-action!
  "Process a single action map, mutating the TimelineData and returning updated cursor."
  [^TimelineData data cursor action]
  (let [action-type (:type action)
        easing      (:easing action)
        dur         (double (or (:dur action) 0))
        end-time    (+ cursor dur)]
    (case action-type
      :wait
      (+ cursor dur)

      :move
      (let [entity (:entity action)
            track  (get-or-create-track data entity)]
        (when-let [x (:x action)] (add-keyframe! track :x end-time x easing))
        (when-let [y (:y action)] (add-keyframe! track :y end-time y easing))
        cursor)

      :fade
      (let [entity (:entity action)
            track  (get-or-create-track data entity)]
        (add-keyframe! track :alpha end-time (:alpha action 1.0) easing)
        cursor)

      :scale
      (let [entity (:entity action)
            track  (get-or-create-track data entity)]
        (when-let [sx (:scale-x action)] (add-keyframe! track :scale-x end-time sx easing))
        (when-let [sy (:scale-y action)] (add-keyframe! track :scale-y end-time sy easing))
        cursor)

      :rotate
      (let [entity (:entity action)
            track  (get-or-create-track data entity)]
        (add-keyframe! track :rotation end-time (:angle action 0.0) easing)
        cursor)

      ;; Unknown action type — skip
      cursor)))

(defn timeline
  "Build a TimelineData from a name, duration, and vector of action maps.
   Each action is a map with :type (:move, :wait, :fade, :scale, :rotate)
   and type-specific keys."
  [name duration-ms actions]
  (let [data (TimelineData. name (double duration-ms))]
    (reduce (fn [cursor action] (process-action! data cursor action))
            0.0
            actions)
    data))

(defn register!
  "Register a TimelineData in the global TimelineRegistry for VNS interop."
  [^TimelineData data]
  (TimelineRegistry/register data)
  data)

(defn unregister!
  "Remove a named timeline from the global registry."
  [name]
  (TimelineRegistry/remove name))
