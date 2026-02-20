(ns com.jvn.clojure.scene
  "Data-driven VN scene builder for JVN using Clojure.
   
   Usage:
     (require '[com.jvn.clojure.scene :as s])
   
     (def prologue
       (s/scene \"prologue\"
         {:characters {\"codel\" \"Codel\"
                       \"narrator\" \"Narrator\"}
          :backgrounds {\"classroom\" \"assets/backgrounds/classroom.png\"}
          :nodes
          [{:type :bg :id \"classroom\"}
           {:type :show :char \"codel\" :pos :center :expr \"neutral\"}
           {:type :say :speaker \"codel\" :text \"Welcome to the tutorial!\"}
           {:type :say :speaker \"codel\" :text \"Let me show you around.\"}
           {:type :choice :options [{:text \"Sure!\" :target \"accept\"}
                                    {:text \"Maybe later\" :target \"decline\"}]}
           {:type :label :name \"accept\"}
           {:type :say :speaker \"codel\" :text \"Great! Let's go!\"}
           {:type :transition :kind :fade :dur 500}
           {:type :label :name \"decline\"}
           {:type :say :speaker \"codel\" :text \"No worries.\"}
           {:type :end}]}))"
  (:import [com.jvn.core.vn VnScenarioBuilder VnScenario Choice Choice$Builder
            CharacterPosition VnTransition VnTransition$TransitionType]))

(defn- resolve-position [k]
  (case (or k :center)
    :left      CharacterPosition/LEFT
    :center    CharacterPosition/CENTER
    :right     CharacterPosition/RIGHT
    :far-left  CharacterPosition/FAR_LEFT
    :far-right CharacterPosition/FAR_RIGHT
    CharacterPosition/CENTER))

(defn- resolve-transition [k]
  (case (or k :fade)
    :fade       VnTransition$TransitionType/FADE
    :dissolve   VnTransition$TransitionType/DISSOLVE
    :slide-left VnTransition$TransitionType/SLIDE_LEFT
    :slide-right VnTransition$TransitionType/SLIDE_RIGHT
    :none       VnTransition$TransitionType/NONE
    VnTransition$TransitionType/FADE))

(defn- process-node!
  "Process a single node map, mutating the VnScenarioBuilder."
  [^VnScenarioBuilder builder node]
  (case (:type node)
    :bg
    (.background builder (str (:id node)))

    :show
    (.show builder (str (:char node))
           (str (or (:expr node) "neutral"))
           (resolve-position (:pos node)))

    :hide
    (.hide builder (str (:char node)))

    :say
    (.dialogue builder (str (:speaker node)) (str (:text node)))

    :narrate
    (.dialogue builder "" (str (:text node)))

    :choice
    (let [options (:options node)
          choices (java.util.ArrayList.)]
      (doseq [{:keys [text target condition]} options]
        (let [b (-> (Choice/builder) (.text (str text)))]
          (when target (.targetLabel b (str target)))
          (when condition (.condition b (str condition)))
          (.add choices (.build b))))
      (.choices builder choices))

    :label
    (.label builder (str (:name node)))

    :jump
    (.jump builder (str (:target node)))

    :wait
    (.waitMs builder (long (or (:dur node) 0)))

    :transition
    (.transition builder
                 (resolve-transition (:kind node))
                 (long (or (:dur node) 500))
                 (:bg node))

    :bgm
    (.playBgm builder (str (:track node)) (boolean (get node :loop true)))

    :bgm-stop
    (.stopBgm builder)

    :sfx
    (.playSfx builder (str (:track node)))

    :external
    (.external builder (str (:provider node)) (str (:payload node)))

    :end
    (.end builder)

    ;; Unknown node type — skip
    nil))

(defn scene
  "Build a VnScenario from a name and a config map.
   Config keys:
     :characters  — map of id->display-name
     :backgrounds — map of id->asset-path
     :nodes       — vector of node maps"
  [name config]
  (let [builder (VnScenarioBuilder. (str name))]
    ;; Register characters
    (doseq [[id display-name] (:characters config)]
      (.addCharacter builder (str id) (str display-name)))
    ;; Register backgrounds
    (doseq [[id path] (:backgrounds config)]
      (.addBackground builder (str id) (str path)))
    ;; Process nodes
    (doseq [node (:nodes config)]
      (process-node! builder node))
    (.build builder)))
