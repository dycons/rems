(ns rems.email.core
  "Sending emails based on application events."
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [postal.core :as postal]
            [rems.application-util :as application-util]
            [rems.application.model]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.user-settings :as user-settings]
            [rems.db.users :as users]
            [rems.email.outbox :as email-outbox]
            [rems.scheduler :as scheduler]
            [rems.text :refer [text text-format with-language]]
            [rems.util :as util])
  (:import [javax.mail.internet InternetAddress]
           [org.joda.time Duration]))

;;; Mapping events to emails

;; TODO list of resources?
;; TODO use real name when addressing user?

;; move this to a util namespace if its needed somewhere else
(defn- link-to-application [application-id]
  (str (:public-url env) "application/" application-id))

(defn- invitation-link [token]
  (str (:public-url env) "accept-invitation?token=" token))

(defn- format-application-for-email [application]
  (str
   (case (util/getx env :application-id-column)
     :external-id (:application/external-id application)
     :id (:application/id application))
   (when-not (empty? (:application/description application))
     (str ", \"" (:application/description application) "\""))))

(defn- resources-for-email [application]
  (->> (:application/resources application)
       (map #(get-in % [:catalogue-item/title context/*lang*]))
       (str/join ", ")))

(defn- handlers [application]
  (get-in application [:application/workflow :workflow.dynamic/handlers]))

(defn- other-handlers [event application]
  (filter #(not= (:userid %) (:event/actor event)) (handlers application)))

(defmulti ^:private event-to-emails-impl
  (fn [event _application] (:event/type event)))

(defmethod event-to-emails-impl :default [_event _application]
  [])

(defn- emails-to-recipients [recipients event application subject-text body-text]
  (vec
   (for [recipient recipients]
     (with-language (:language (user-settings/get-user-settings (:userid recipient)))
       (fn []
         {:to-user (:userid recipient)
          :subject (text-format subject-text
                                (application-util/get-member-name recipient)
                                (application-util/get-member-name (:event/actor-attributes event))
                                (format-application-for-email application)
                                (application-util/get-applicant-name application)
                                (resources-for-email application)
                                (link-to-application (:application/id event)))
          :body (str
                 (text-format body-text
                              (application-util/get-member-name recipient)
                              (application-util/get-member-name (:event/actor-attributes event))
                              (format-application-for-email application)
                              (application-util/get-applicant-name application)
                              (resources-for-email application)
                              (link-to-application (:application/id event)))
                 (text :t.email/regards)
                 (text :t.email/footer))})))))

(defmethod event-to-emails-impl :application.event/approved [event application]
  (concat (emails-to-recipients (application-util/applicant-and-members application)
                                event application
                                :t.email.application-approved/subject-to-applicant
                                :t.email.application-approved/message-to-applicant)
          (emails-to-recipients (other-handlers event application)
                                event application
                                :t.email.application-approved/subject-to-handler
                                :t.email.application-approved/message-to-handler)))

(defmethod event-to-emails-impl :application.event/rejected [event application]
  (concat (emails-to-recipients (application-util/applicant-and-members application)
                                event application
                                :t.email.application-rejected/subject-to-applicant
                                :t.email.application-rejected/message-to-applicant)
          (emails-to-recipients (other-handlers event application)
                                event application
                                :t.email.application-rejected/subject-to-handler
                                :t.email.application-rejected/message-to-handler)))

(defmethod event-to-emails-impl :application.event/revoked [event application]
  (concat (emails-to-recipients (application-util/applicant-and-members application)
                                event application
                                :t.email.application-revoked/subject-to-applicant
                                :t.email.application-revoked/message-to-applicant)
          (emails-to-recipients (other-handlers event application)
                                event application
                                :t.email.application-revoked/subject-to-handler
                                :t.email.application-revoked/message-to-handler)))

(defmethod event-to-emails-impl :application.event/closed [event application]
  (concat (emails-to-recipients (application-util/applicant-and-members application)
                                event application
                                :t.email.application-closed/subject-to-applicant
                                :t.email.application-closed/message-to-applicant)
          (emails-to-recipients (other-handlers event application)
                                event application
                                :t.email.application-closed/subject-to-handler
                                :t.email.application-closed/message-to-handler)))

(defmethod event-to-emails-impl :application.event/returned [event application]
  (concat (emails-to-recipients [(:application/applicant application)]
                                event application
                                :t.email.application-returned/subject-to-applicant
                                :t.email.application-returned/message-to-applicant)
          (emails-to-recipients (other-handlers event application)
                                event application
                                :t.email.application-returned/subject-to-handler
                                :t.email.application-returned/message-to-handler)))

(defmethod event-to-emails-impl :application.event/licenses-added [event application]
  (emails-to-recipients (application-util/applicant-and-members application)
                        event application
                        :t.email.application-licenses-added/subject
                        :t.email.application-licenses-added/message))

(defmethod event-to-emails-impl :application.event/submitted [event application]
  (if (= (:event/time event)
         (:application/first-submitted application))
    (emails-to-recipients (handlers application)
                          event application
                          :t.email.application-submitted/subject
                          :t.email.application-submitted/message)
    (emails-to-recipients (handlers application)
                          event application
                          :t.email.application-resubmitted/subject
                          :t.email.application-resubmitted/message)))

(defmethod event-to-emails-impl :application.event/comment-requested [event application]
  (emails-to-recipients (:application/commenters event)
                        event application
                        :t.email.comment-requested/subject
                        :t.email.comment-requested/message))

(defmethod event-to-emails-impl :application.event/commented [event application]
  (emails-to-recipients (handlers application)
                        event application
                        :t.email.commented/subject
                        :t.email.commented/message))

(defmethod event-to-emails-impl :application.event/remarked [event application]
  (emails-to-recipients (handlers application)
                        event application
                        :t.email.remarked/subject
                        :t.email.remarked/message))

(defmethod event-to-emails-impl :application.event/decided [event application]
  (emails-to-recipients (handlers application)
                        event application
                        :t.email.decided/subject
                        :t.email.decided/message))

(defmethod event-to-emails-impl :application.event/decision-requested [event application]
  (emails-to-recipients (:application/deciders event)
                        event application
                        :t.email.decision-requested/subject
                        :t.email.decision-requested/message))

(defmethod event-to-emails-impl :application.event/member-added [event application]
  ;; TODO email to applicant? email to handler?
  (emails-to-recipients [(:application/member event)]
                        event application
                        :t.email.member-added/subject
                        :t.email.member-added/message))

(defmethod event-to-emails-impl :application.event/member-invited [event application]
  (with-language (:default-language env)
    (fn []
      [{:to (:email (:application/member event))
        :subject (text-format :t.email.member-invited/subject
                              (:name (:application/member event))
                              (application-util/get-applicant-name application)
                              (format-application-for-email application)
                              (invitation-link (:invitation/token event)))
        :body (str
               (text-format :t.email.member-invited/message
                            (:name (:application/member event))
                            (application-util/get-applicant-name application)
                            (format-application-for-email application)
                            (invitation-link (:invitation/token event)))
               (text :t.email/regards)
               (text :t.email/footer))}])))

;; TODO member-joined?

(defn event-to-emails [event]
  (when-let [app-id (:application/id event)]
    (event-to-emails-impl (rems.application.model/enrich-event event users/get-user (constantly nil))
                          (applications/get-unrestricted-application app-id))))

;;; Generic poller infrastructure

;;; Email poller

;; You can test email sending by:
;;
;; 1. running mailhog: docker run -p 1025:1025 -p 8025:8025 mailhog/mailhog
;; 2. adding {:mail-from "rems@example.com" :smtp-host "localhost" :smtp-port 1025} to dev-config.edn
;; 3. generating some emails
;;    - you can reset the email poller state with (common/set-poller-state! :rems.poller.email/poller nil)
;; 4. open http://localhost:8025 in your browser to view the emails

(defn- validate-address
  "Returns nil for a valid email address, string message for an invalid one."
  [email]
  (try
    (InternetAddress. email)
    nil
    (catch Throwable t
      (str "Invalid address "
           (pr-str email)
           ": "
           t))))

(deftest test-validate-address
  (is (nil? (validate-address "valid@example.com")))
  (is (string? (validate-address "")))
  (is (string? (validate-address nil)))
  (is (string? (validate-address "test@test_example.com"))))

(defn send-email! [email-spec]
  (let [host (:smtp-host env)
        port (:smtp-port env)
        email (assoc email-spec
                     :from (:mail-from env)
                     :to (or (:to email-spec)
                             (:email
                              (users/get-user
                               (:to-user email-spec)))))
        to-error (validate-address (:to email))]
    (when (:to email)
      (log/info "sending email:" (pr-str email))
      (cond
        to-error
        (do
          (log/warn "failed address validation:" to-error)
          (str "failed address validation: " to-error))

        (not (and host port))
        (do
          (log/info "no smtp server configured, only pretending to send email")
          nil)

        :else
        (try
          (postal/send-message {:host host :port port} email)
          nil
          (catch Throwable e ; e.g. email address does not exist
            (log/warn e "failed sending email:" (pr-str email))
            (str "failed sending email: " e)))))))

(defn run []
  (doseq [email (email-outbox/get-emails {:due-now? true})]
    (if-let [error (send-email! (:email-outbox/email email))]
      (let [email (email-outbox/attempt-failed! email error)]
        (when (not (:email-outbox/next-attempt email))
          (log/warn "all attempts to send email" (:email-outbox/id email) "failed")))
      (email-outbox/attempt-succeeded! (:email-outbox/id email)))))

(mount/defstate email-poller
  :start (scheduler/start! run (Duration/standardSeconds 10))
  :stop (scheduler/stop! email-poller))

(comment
  (mount/start #{#'email-poller})
  (mount/stop #{#'email-poller}))
