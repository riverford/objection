(ns objection.core-test
  (:require [clojure.test :refer :all]
            [objection.core :as obj])
  (:import (java.lang AutoCloseable)
           (java.util UUID)))

(deftest test-id
  (obj/stop-all!)
  (is (nil? (obj/id nil)))
  (is (nil? (obj/id (str (UUID/randomUUID)))))
  (let [o (Object.)]
    (->> "before registry, id returns nil"
         (is (nil? (obj/id o))))
    (obj/register o)
    (->> "id should be present"
         (is (some? (obj/id o))))
    (->> "id should be a string"
         (is (string? (obj/id o))))
    (->> "(id (id x)) just returns id"
         (is (= (obj/id o)
                (obj/id (obj/id o)))))
    (->> "id returned by describe and 'id' should be the same"
         (is (= (obj/id o) (:id (obj/describe o)))))

    (->> "id should be present in id seq"
         (is (contains? (set (obj/id-seq)) (obj/id o))))

    (let [oldid (obj/id o)]
      (obj/register o)
      (->> "reregistering does not change id"
           (is (= oldid (obj/id o))))

      (obj/stop! o)
      (->> "id should be nil once an object has been stopped"
           (is (nil? (obj/id o))))

      (->> "oldid should return nil"
           (is (nil? (obj/id oldid))))

      (obj/register o)
      (->> "reregistring a stopped obj generates a new id"
           (is (not= oldid (obj/id o))))
      (obj/stop-all!))))

(deftest test-object
  (obj/stop-all!)
  (is (nil? (obj/object nil)))
  (is (nil? (obj/id (str (UUID/randomUUID)))))
  (let [o (Object.)]
    (->> "before object is registered, return nil"
         (is (nil? (obj/object o))))
    (obj/register o)
    (->> "object applied to object is identity"
         (is (identical? o (obj/object o))))
    (->> "object applied to id returns the object instance."
         (is (identical? o (obj/object (obj/id o)))))
    (obj/stop! o)
    (->> "after object has been stopped, return nil again"
         (is (nil? (obj/object o)))))
  (obj/stop-all!))

(deftest test-alias
  (obj/stop-all!)
  (is (nil? (obj/id ::a)))
  (is (nil? (obj/object ::a)))

  (let [o (Object.)]
    (obj/register o)
    (obj/alias o ::a)

    (->> "alias can be used interchangeably with id in obj calls"
         (is (identical? o (obj/object ::a))))
    (->> "alias can be used interchangeably with id in id calls"
         (is (= (obj/id ::a) (obj/id o))))
    (->> "alias can be used interchangeably with id in describe calls"
         (is (= (obj/describe ::a) (obj/describe o))))
    (->> "alias can be used in alter data calls"
         (is (= {:foo :bar}
                (obj/alter-data! ::a assoc :foo :bar))))
    (is (= {:foo :bar}
           (obj/data (obj/id o))
           (obj/data o)
           (obj/data ::a)))
    (->> "alias can be used interchangeably with id in data calls"
         (is (= (obj/data ::a) (obj/data o))))

    (testing "adding a second alias"
      (obj/alias o ::b)
      (is (= {:foo :bar}
             (obj/data (obj/id o))
             (obj/data o)
             (obj/data ::a)
             (obj/data ::b))))

    (testing "realiasing same obj is fine"
      (is (nil? (obj/alias o ::a)))
      (is (nil? (obj/alias o ::b))))

    (->> "testing aliases on describe"
         (is (= #{::a ::b}
                (:aliases (obj/describe o))
                (:aliases (obj/describe ::a))
                (:aliases (obj/describe ::b)))))

    (let [o2 (obj/register (Object.))]
      (->>
        "duplicating an alias throws"
        (is (thrown? Throwable (obj/alias o2 ::a))))))

  (obj/stop-all!))

(deftest test-depend
  (obj/stop-all!)
  (is (thrown? Throwable (obj/depend nil nil)))
  (is (thrown? Throwable (obj/depend (Object.) nil)))
  (is (thrown? Throwable (obj/depend nil (Object.))))
  (is (thrown? Throwable (obj/depend (Object.) (Object.))))

  (let [a (Object.)
        b (Object.)
        c (Object.)]
    (obj/register a)
    (is (thrown? Throwable (obj/depend a b)))
    (obj/register b)
    (obj/register c)

    (is (nil? (obj/depend a b)))
    (is (nil? (obj/depend b c)))

    (->> "redepending is fine"
         (is (nil? (obj/depend a b))))

    (->> "cycles fail"
         (is (thrown? Throwable (obj/depend c a))))

    (is (false? (obj/depends? b a)))
    (is (false? (obj/depends? nil nil)))
    (is (false? (obj/depends? a (Object.))))

    (is (obj/depends? a b))
    (is (obj/depends? b c))
    (is (obj/depends? a c))

    (obj/alias a ::a)
    (obj/alias b ::b)
    (obj/alias c ::c)

    (is (obj/depends? ::a ::b))
    (is (obj/depends? ::a ::c))

    (obj/stop! c)
    (is (empty? (obj/id-seq))))

  (obj/stop-all!))