(ns com.jvn.clojure.menu
  "Data-driven menu configuration for JVN using Clojure maps.
   
   Usage:
     (require '[com.jvn.clojure.menu :as menu])
   
     (def my-style
       (menu/style \"neon\"
         {:item-color      \"#00ff88\"
          :selected-color  \"#ffff00\"
          :hover-color     \"#66ffaa\"
          :disabled-color  \"#555555\"
          :font-family     \"Monospace\"
          :font-weight     \"BOLD\"
          :font-size       24
          :shadow-color    \"#00000088\"
          :shadow-offset-x 2.0
          :shadow-offset-y 2.0
          :opacity         0.95
          :prefix          \"  \"
          :selected-prefix \"> \"
          :disabled-prefix \"- \"
          :button-asset          \"btn.png\"
          :button-selected-asset \"btn_sel.png\"
          :button-hover-asset    \"btn_hover.png\"
          :button-disabled-asset \"btn_dis.png\"
          :button-padding-x 18.0
          :button-padding-y 0.0
          :title-color        \"#ffffff\"
          :title-font-family  \"Georgia\"
          :title-font-weight  \"BOLD\"
          :title-font-size    36
          :title-shadow-color \"#000000\"
          :hints-color       \"#aaaaaa\"
          :hints-font-family \"Arial\"
          :hints-font-size   14
          :bg-asset   \"bg.png\"
          :bg-color   \"#1a1a2e\"
          :bg-opacity 0.9}))
   
     (def my-layout
       (menu/layout \"compact\"
         {:list-y-start      0.4
          :line-height       36.0
          :list-width-factor 0.8
          :text-align        \"center\"
          :hints-bottom-margin 16.0
          :title-y           50.0}))"
  (:import [com.jvn.core.menu.config MenuStyleSpec MenuLayoutSpec]))

(defn- box-double
  "Box a Clojure number as java.lang.Double, or nil."
  [v]
  (when v (Double/valueOf (double v))))

(defn- box-int
  "Box a Clojure number as java.lang.Integer, or nil."
  [v]
  (when v (Integer/valueOf (int v))))

(defn style
  "Create a MenuStyleSpec from a Clojure map.
   Keys use kebab-case and are automatically mapped to the Java record fields."
  [id m]
  (MenuStyleSpec.
    (str id)
    ;; Item colors
    (get m :item-color)
    (get m :selected-color)
    (get m :hover-color)
    (get m :disabled-color)
    ;; Prefixes
    (get m :prefix)
    (get m :selected-prefix)
    (get m :disabled-prefix)
    ;; Font
    (get m :font-family)
    (get m :font-weight)
    (box-int (get m :font-size))
    ;; Text effects
    (get m :shadow-color)
    (box-double (get m :shadow-offset-x))
    (box-double (get m :shadow-offset-y))
    (box-double (get m :opacity))
    ;; Button skins
    (get m :button-asset)
    (get m :button-selected-asset)
    (get m :button-hover-asset)
    (get m :button-disabled-asset)
    (box-double (get m :button-padding-x))
    (box-double (get m :button-padding-y))
    ;; Title
    (get m :title-color)
    (get m :title-font-family)
    (get m :title-font-weight)
    (box-int (get m :title-font-size))
    (get m :title-shadow-color)
    ;; Hints
    (get m :hints-color)
    (get m :hints-font-family)
    (box-int (get m :hints-font-size))
    ;; Background
    (get m :bg-asset)
    (get m :bg-color)
    (box-double (get m :bg-opacity))))

(defn layout
  "Create a MenuLayoutSpec from a Clojure map."
  [id m]
  (MenuLayoutSpec.
    (str id)
    (double (get m :list-y-start 0.35))
    (double (get m :line-height 40.0))
    (double (get m :list-width-factor 1.0))
    (str (get m :text-align "center"))
    (double (get m :hints-bottom-margin 20.0))
    (box-double (get m :title-y))))

(defn merge-styles
  "Merge two style maps, with overrides taking precedence."
  [base overrides]
  (merge base overrides))

(defn merge-layouts
  "Merge two layout maps, with overrides taking precedence."
  [base overrides]
  (merge base overrides))
