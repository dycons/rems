(ns rems.util
  (:require [rems.context :as context]))

(defn select-vals
  "Select values in map `m` specified by given keys `ks`.

  Values will be returned in the order specified by `ks`.

  You can specify a `default-value` that will be used if the
  key is not found in the map. This is like `get` function."
  [m ks & [default-value]]
  (vec (reduce #(conj %1 (get m %2 default-value)) [] ks)))

(defn index-by
  "Index the collection coll with given keys `ks`.

  Result is a map indexed by the first key
  that contains a map indexed by the second key."
  [ks coll]
  (if (empty? ks)
    (first coll)
    (->> coll
         (group-by (first ks))
         (map (fn [[k v]] [k (index-by (rest ks) v)]))
         (into {}))))

(defn errorf
  "Throw a RuntimeException, args passed to `clojure.core/format`."
  [& fmt-args]
  (throw (RuntimeException. (apply format fmt-args))))

(defn get-user-id
  ([]
   (get-user-id context/*user*))
  ([user]
   (get user "eppn")))

(defn get-username
  ([]
   (get-username context/*user*))
  ([user]
   (get user "commonName")))

(defn get-user-mail
  ([]
   (get-user-mail context/*user*))
  ([user]
   (get user "mail")))

(defn get-theme-attribute
  "Fetch the attribute value from the current theme."
  [attr-name]
  (get context/*theme* attr-name))

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

(defn assoc-if
  "Like `assoc` but doesn't do anything if map is nil"
  [map key val]
  (cond
    map (assoc map key val)))

(def never-match-route
  (constantly nil))
