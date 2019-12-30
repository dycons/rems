(ns rems.api.testing
  "Shared code for API testing"
  (:require [clojure.test :refer :all]
            [cheshire.core :refer [parse-stream]]
            [mount.core :as mount]
            [rems.db.testing :refer [test-data-fixture test-db-fixture caches-fixture search-index-fixture]]
            [rems.handler :refer :all]
            [ring.mock.request :refer :all]))

(defn handler-fixture [f]
  (mount/start
   #'rems.locales/translations
   #'rems.handler/handler)
  ;; TODO: silence logging somehow?
  (f)
  (mount/stop))

(def api-fixture
  (join-fixtures [test-db-fixture
                  search-index-fixture
                  caches-fixture
                  test-data-fixture
                  handler-fixture]))

(defn authenticate [request api-key user-id]
  (-> request
      (assoc-in [:headers "x-rems-api-key"] api-key)
      (assoc-in [:headers "x-rems-user-id"] user-id)))

(defn assert-response-is-ok [response]
  (assert response)
  (assert (= 200 (:status response))
          (pr-str {:status (:status response)
                   :body (when-let [body (:body response)]
                           (if (string? body)
                             body
                             (slurp body)))}))
  response)

(defn assert-response-is-server-error? [response]
  (assert (= 500 (:status response))))

(defn response-is-bad-request? [response]
  (= 400 (:status response)))

(defn response-is-unauthorized? [response]
  (= 401 (:status response)))

(defn response-is-forbidden? [response]
  (= 403 (:status response)))

(defn response-is-not-found? [response]
  (= 404 (:status response)))

(defn coll-is-empty? [data]
  (and (coll? data)
       (empty? data)))

(defn coll-is-not-empty? [data]
  (not (coll-is-empty? data)))

(defn read-body [{body :body}]
  (cond
    (nil? body) body
    (string? body) body
    true (parse-stream (clojure.java.io/reader body) true)))

(defn read-ok-body [response]
  (assert-response-is-ok response)
  (read-body response))

(defn api-call [method api body api-key user-id]
  (-> (request method api)
      (authenticate api-key user-id)
      (json-body body)
      handler
      read-ok-body))

(defn assert-success [body]
  (assert (:success body) (pr-str body))
  body)

;;; Fake login without API key

(defn- strip-cookie-attributes [cookie]
  (re-find #"[^;]*" cookie))

(defn login-with-cookies [username]
  (let [login-headers (-> (request :get "/Shibboleth.sso/Login" {:username username})
                          handler
                          :headers)
        cookie (-> (get login-headers "Set-Cookie")
                   first
                   strip-cookie-attributes)]
    (assert cookie)
    cookie))

(defn- parse-csrf-token [response]
  (let [token-regex #"var csrfToken = '([^\']*)'"
        [_ token] (re-find token-regex (:body response))]
    token))

(defn get-csrf-token [cookie]
  (let [csrf (-> (request :get "/")
                 (header "Cookie" cookie)
                 handler
                 parse-csrf-token)]
    (assert csrf)
    csrf))
