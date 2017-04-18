(ns rems.test.catalogue
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.catalogue :refer :all]
            [rems.context :as context]
            rems.test.tempura))

(use-fixtures :once rems.test.tempura/fake-tempura-fixture)

(defn check-catalogue-item-text [props]
  (hiccup-text (first (hiccup-find [:td] (#'rems.catalogue/catalogue-item props)))))

(deftest test-catalogue-item
  (testing "catalogue item with urn"
    (let [urn "http://urn.fi/urn:nbn:fi:lb-201403262"
          c (#'rems.catalogue/catalogue-item {:title "U" :resid urn})
          link (first (hiccup-find [:a] c))]
      (is (= :a.catalogue-item-link (first link)) "is a link")
      (is (= urn (:href (second link))) "links to the urn")
      (is (= :_blank (:target (second link))) "opens in new tab")))

  (testing "catalogue item with localizations"
    (let [props {:title "NO" :localizations {:fi {:title "FI"} :en {:title "EN"}}}]
      (testing "without localizations"
        (is (= "NO" (check-catalogue-item-text (dissoc props :localizations)))))
      (testing "for Finnish session"
        (binding [context/*lang* :fi]
          (is (= "FI" (check-catalogue-item-text props)))))
      (testing "for English session"
        (binding [context/*lang* :en]
          (is (= "EN" (check-catalogue-item-text props))))))))

(defn check-row-text [row text]
  (is (= text (hiccup-text (first (hiccup-find [:td] row))))))

(deftest test-catalogue-list
  (let [c (#'rems.catalogue/catalogue-list [{:title "B"} {:title "A"} {:title "C"}])
        rows (rest (hiccup-find [:tr] c))]
    (is (= 3 (count rows)))
    (check-row-text (nth rows 0) "A")
    (check-row-text (nth rows 1) "B")
    (check-row-text (nth rows 2) "C")))
