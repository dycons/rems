(ns ^:integration rems.api.test-api
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.api-key :as api-key]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures :once api-fixture)

(deftest test-api-not-found
  (testing "unknown endpoint"
    (let [resp (-> (request :get "/api/unknown")
                   handler)]
      (is (response-is-not-found? resp))))
  (testing "known endpoint, wrong method,"
    (testing "unauthorized"
      (let [resp (-> (request :get "/api/blacklist/remove")
                     handler)]
        (is (response-is-not-found? resp))))
    (testing "authorized,"
      (testing "missing params"
        ;; Surprisingly hard to find a POST route that isn't shadowed
        ;; by a GET route. For example, GET /api/applications/command
        ;; hits the /api/applications/:application-id route.
        (let [resp (-> (request :get "/api/blacklist/remove")
                       (authenticate "42" "handler")
                       handler)]
          (is (response-is-not-found? resp)))))))

(deftest test-api-key-security
  (testing ":api-key role"
    (testing "available for valid api key"
      (let [resp (-> (request :post "/api/email/send-reminders")
                     (authenticate "42" "owner")
                     handler)]
        (is (= 200 (:status resp)))))
    (testing "not available when using wrong API key"
      (let [username "alice"
            ;; need cookies and csrf to actually get a forbidden instead of "invalid csrf token"
            ;; TODO check this
            cookie (login-with-cookies username)
            csrf (get-csrf-token cookie)
            resp (-> (request :post "/api/email/send-reminders")
                     (header "Cookie" cookie)
                     (header "x-csrf-token" csrf)
                     (header "x-rems-api-key" "WRONG")
                     handler)]
        (is (response-is-forbidden? resp)))))
  (testing "api key user whitelist"
    (api-key/add-api-key! "43" {:comment "all users" :users nil})
    (api-key/add-api-key! "44" {:comment "only alice & malice" :users ["alice" "malice"]})
    (testing "> api key without whitelist can impersonate any user >"
      (doseq [user ["owner" "alice" "malice"]]
        (testing user
          (is (= 200 (:status (api-response :get "/api/catalogue/" nil
                                            "43" user)))))))
    (testing "> api key with whitelist can only impersonate given users >"
      (doseq [user ["alice" "malice"]]
        (testing user
          (is (= 200 (:status (api-response :get "/api/catalogue/" nil
                                            "44" user))))))
      (is (response-is-unauthorized? (api-response :get "/api/catalogue/" nil
                                                   "44" "owner")))))
  (testing "api key path whitelist"
    (api-key/add-api-key! "45" {:comment "all paths" :paths nil})
    (api-key/add-api-key! "46" {:comment "limited paths" :paths ["/api/applications" "/api/my-applications"]})
    (api-key/add-api-key! "47" {:comment "regex path" :paths ["/api/c.*"]})
    (testing "> api key without whitelist can access any path >"
      (doseq [path ["/api/applications" "/api/my-applications" "/api/catalogue"]]
        (testing path
          (is (= 200 (:status (api-response :get path nil
                                            "45" "owner")))))))
    (testing "> api key with whitelist can access only given paths >"
      (doseq [path ["/api/applications" "/api/my-applications"]]
        (testing path
          (is (= 200 (:status (api-response :get path nil
                                            "46" "owner"))))))
      (is (response-is-unauthorized? (api-response :get "/api/catalogue" nil
                                                   "46" "owner"))))
    (testing "> api key with regex can access only matching paths >"
      (doseq [path ["/api/catalogue" "/api/catalogue-items"]]
        (testing path
          (is (= 200 (:status (api-response :get path nil
                                            "47" "owner"))))))
      (is (response-is-unauthorized? (api-response :get "/api/applications" nil
                                                   "47" "owner"))))))

(deftest test-health-api
  (let [body (-> (request :get "/api/health")
                 handler
                 read-ok-body)]
    (is (:healthy body))
    (is (string? (:latest-event body)))
    (is (not (empty? (:latest-event body))))))

(deftest test-keepalive-api
  (assert-response-is-ok (-> (request :get "/keepalive")
                             handler))
  (is true))
