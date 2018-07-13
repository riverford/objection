(ns objection.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [objection.core :as obj :include-macros true]))

(deftest test-register
  (obj/stop-all!)

  (is (thrown? :default (obj/register nil)))
  (is (thrown? :default (obj/register false)))

  (let [o (->Box nil)]
    (is (identical? o (obj/register o))))

  (let [a (obj/register (->Box nil) {:alias ::a})
        b (obj/register (->Box nil) {:alias ::b
                                   :deps [a]
                                   :aliases [::foo]})]
    (is (identical? a (obj/object ::a)))
    (is (identical? b (obj/object ::b)))
    (is (identical? b (obj/object ::foo)))

    (is (obj/depends? b a))
    (is (obj/depends? ::b ::a)))

  (obj/stop-all!))

(deftest test-id
  (obj/stop-all!)
  (is (nil? (obj/id nil)))
  (is (nil? (obj/id (str (random-uuid)))))
  (let [o (->Box nil)]
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

    (->> "can get id by prefix"
         (is (= (obj/id o)
                (obj/id (subs (obj/id o) 0 5)))))

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
  (is (nil? (obj/id (str (random-uuid)))))
  (let [o (->Box nil)]
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

  (let [o (->Box nil)]
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

    (let [o2 (obj/register (->Box nil))]
      (->>
        "duplicating an alias throws"
        (is (thrown? :default (obj/alias o2 ::a))))))

  (obj/stop-all!))

(deftest test-depend
  (obj/stop-all!)
  (is (thrown? :default (obj/depend nil nil)))
  (is (thrown? :default (obj/depend (->Box nil) nil)))
  (is (thrown? :default (obj/depend nil (->Box nil))))
  (is (thrown? :default (obj/depend (->Box nil) (->Box nil))))

  (let [a (->Box nil)
        b (->Box nil)
        c (->Box nil)
        d (->Box nil)]
    (obj/register a)
    (is (thrown? :default (obj/depend a b)))
    (obj/register b)
    (obj/register c)
    (obj/register d)

    (is (= #{} (obj/dependents b)))
    (is (= #{} (obj/dependencies a)))

    (is (nil? (obj/depend a b)))
    (is (nil? (obj/depend b c)))
    (is (nil? (obj/depend d c)))

    (->> "redepending is fine"
         (is (nil? (obj/depend a b))))

    (->> "cycles fail"
         (is (thrown? :default (obj/depend c a))))

    (is (false? (obj/depends? c a)))
    (is (false? (obj/depends? b a)))
    (is (false? (obj/depends? nil nil)))
    (is (false? (obj/depends? a (->Box nil))))

    (is (obj/depends? a b))
    (is (obj/depends? b c))
    (is (obj/depends? a c))
    (is (obj/depends? d c))

    (obj/alias a ::a)
    (obj/alias b ::b)
    (obj/alias c ::c)
    (obj/alias d ::d)

    (is (obj/depends? ::a ::b))
    (is (obj/depends? ::a ::c))
    (is (obj/depends? ::d ::c))

    (is (= (obj/dependencies ::a)
           (obj/dependencies a)
           (obj/dependencies (obj/id a))
           #{(obj/id b)}))

    (is (= (obj/dependents ::b)
           (obj/dependents b)
           (obj/dependents (obj/id b))
           #{(obj/id a)}))

    (obj/stop! c)
    (is (empty? (obj/id-seq))))

  (obj/stop-all!))

(deftest test-undepend
  (obj/stop-all!)

  (is (nil? (obj/undepend nil nil)))
  (is (nil? (obj/undepend (->Box nil) nil)))
  (is (nil? (obj/undepend nil (->Box nil))))
  (is (nil? (obj/undepend (->Box nil) (->Box nil))))

  (let [a (obj/register (->Box nil))
        b (obj/register (->Box nil))
        c (obj/register (->Box nil))]

    (obj/depend a b)
    (obj/depend b c)

    (is (nil? (obj/undepend b c)))
    (is (false? (obj/depends? b c)))
    (is (false? (obj/depends? a c)))
    (is (obj/depends? a b))

    (is (= #{} (obj/dependencies b)))

    (obj/stop! c)
    (is (= (hash-set
             (obj/id a)
             (obj/id b))
           (set (obj/id-seq)))))

  (obj/stop-all!))

(deftest test-stop
  (obj/stop-all!)

  (let [stop-counter (atom 0)
        o (obj/register
            (->Box nil)
            {:stopfn (fn [_] (swap! stop-counter inc))})
        id (obj/id o)]


    (is (nil? (obj/stop! o)))
    (is (= 1 @stop-counter))
    (is (nil? (obj/stop! o)))
    (is (= 1 @stop-counter))
    (is (nil? (obj/stop! id)))
    (is (= 1 @stop-counter)))

  (let [stop-counter (atom 0)
        o (obj/register
            (reify obj/IAutoStoppable
              (-stop! [this]
                (swap! stop-counter inc))))
        id (obj/id o)]


    (is (nil? (obj/stop! o)))
    (is (= 1 @stop-counter))
    (is (nil? (obj/stop! o)))
    (is (= 1 @stop-counter))
    (is (nil? (obj/stop! id)))
    (is (= 1 @stop-counter)))

  (obj/stop-all!))

(obj/defsingleton ::test-need-singleton
  (->Box nil))

(deftest test-need
  (obj/stop-all!)

  (is (thrown? :default (obj/need nil)))
  (is (thrown? :default (obj/need (->Box nil))))

  (let [o (obj/register (->Box nil) {:alias ::o})]
    (is (identical? o (obj/need ::o)))
    (is (identical? o (obj/need o "foo"))))

  (is (identical? (obj/singleton ::test-need-singleton)
                  (obj/need ::test-need-singleton)))

  (obj/stop-all!))

(obj/defsingleton ::test-singleton
  (->Box nil))

(deftest test-singleton
  (obj/stop-all!)

  (let [o (obj/singleton ::test-singleton)]
    (is (some? o))
    (is (identical? o (obj/singleton ::test-singleton)))
    (is (identical? o (obj/need ::test-singleton)))
    (is (identical? o (obj/object ::test-singleton)))
    (is (identical? o (obj/object o)))
    (is (identical? (obj/id o) (obj/id ::test-singleton))))

  (obj/stop! ::test-singleton)
  (is (nil? (obj/object ::test-singleton)))

  (obj/stop-all!))