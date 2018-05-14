(ns rems.autocomplete
  (:require [komponentit.autocomplete :as autocomplete]
            [rems.guide-functions]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn component
  "Multiple selectable, searchable list"
  [{:keys [value items add-fn remove-fn item->key item->text item->value value->text search-fields]
    :or {item->key :key item->value :value item->text :value value->text get search-fields [:value]}}]
  [autocomplete/multiple-autocomplete
   {:value value
    :cb add-fn
    :remove-cb remove-fn
    :item->key item->key
    :item->text item->text
    :item->value item->value
    :value->text value->text
    :search-fields search-fields
    :placeholder (text :t.autocomplete/placeholder)
    :no-results-text (text :t.autocomplete/no-results)
    :ctrl-class "autocomplete"
    :items items}])

(defn guide
  []
  (let [add-fn (fn [item] (println "add" item))
        remove-fn (fn [item] (println "remove" item))]
    [:div
     (component-info component)
     (example "autocomplete, empty"
              [component {:value nil :items (sorted-map 1 "Matti" 2 "Teppo" 3 "Seppo" 4 "Hupu" 5 "Tupu")
                          :add-fn add-fn :remove-fn remove-fn}])
     (example "autocomplete, multiple values selected"
              [component {:value #{1 5} :items (sorted-map 1 "Matti" 2 "Teppo" 3 "Seppo" 4 "Hupu" 5 "Tupu")
                          :add-fn add-fn :remove-fn remove-fn}])]))
