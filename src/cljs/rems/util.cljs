(ns rems.util
  (:require  [ajax.core :refer [GET PUT POST]]
             [re-frame.core :as rf]))

;; TODO move to cljc
(defn getx
  "Like `get` but throws an exception if the key is not found."
  [m k]
  (let [e (get m k ::sentinel)]
    (if-not (= e ::sentinel)
      e
      (throw (ex-info "Missing required key" {:map m :key k})))))

(defn getx-in
  "Like `get-in` but throws an exception if the key is not found."
  [m ks]
  (reduce getx m ks))

(defn dispatch!
  "Dispatches to the given url."
  [url]
  (set! (.-location js/window) url))

(defn redirect-when-unauthorized [{:keys [status status-text]}]
  (when (= 401 status)
    (let [current-url (.. js/window -location -href)]
      (rf/dispatch [:unauthorized! current-url]))))

(defn- wrap-default-error-handler [handler]
  (fn [err]
    (redirect-when-unauthorized err)
    (when handler (handler err))))

(defn fetch
  "Fetches data from the given url with optional map of options like #'ajax.core/GET.

  Has sensible defaults with error handler, JSON and keywords.

  Additionally calls event hooks."
  [url opts]
  (js/window.rems.hooks.get url (clj->js opts))
  (GET url (merge {:error-handler (wrap-default-error-handler (:error-handler opts))
                   :response-format :transit}
                  opts)))

(defn put!
  "Dispatches a command to the given url with optional map of options like #'ajax.core/PUT.

  Has sensible defaults with error handler, JSON and keywords.

  Additionally calls event hooks."
  [url opts]
  (js/window.rems.hooks.put url (clj->js opts))
  (PUT url (merge {:error-handler (wrap-default-error-handler (:error-handler opts))
                   :format :transit
                   :response-format :transit}
                  opts)))

(defn post!
  "Dispatches a command to the given url with optional map of options like #'ajax.core/POST.

  Has sensible defaults with error handler, JSON and keywords.

  Additionally calls event hooks."
  [url opts]
  (js/window.rems.hooks.put url (clj->js opts))
  (POST url (merge {:error-handler (wrap-default-error-handler (:error-handler opts))
                    :format :transit
                    :response-format :transit}
                   opts)))
