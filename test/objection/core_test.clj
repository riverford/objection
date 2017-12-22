(ns objection.core-test
  (:require [clojure.test :refer :all]
            [objection.core :as obj])
  (:import (java.lang AutoCloseable)
           (java.util UUID)))

(deftest test-register
  (obj/stop-all!)

  (is (thrown? Throwable (obj/register nil)))
  (is (thrown? Throwable (obj/register false)))

  (let [o (Object.)]
    (is (identical? o (obj/register o))))

  (let [a (obj/register (Object.) {:alias ::a})
        b (obj/register (Object.) {:alias ::b
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
        c (Object.)
        d (Object.)]
    (obj/register a)
    (is (thrown? Throwable (obj/depend a b)))
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
         (is (thrown? Throwable (obj/depend c a))))

    (is (false? (obj/depends? c a)))
    (is (false? (obj/depends? b a)))
    (is (false? (obj/depends? nil nil)))
    (is (false? (obj/depends? a (Object.))))

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
  (is (nil? (obj/undepend (Object.) nil)))
  (is (nil? (obj/undepend nil (Object.))))
  (is (nil? (obj/undepend (Object.) (Object.))))

  (let [a (obj/register (Object.))
        b (obj/register (Object.))
        c (obj/register (Object.))]

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
            (Object.)
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

  (let [stop-counter (atom 0)
        o (obj/register
            (reify AutoCloseable
              (close [this]
                (swap! stop-counter inc))))
        id (obj/id o)]


    (is (nil? (obj/stop! o)))
    (is (= 1 @stop-counter))
    (is (nil? (obj/stop! o)))
    (is (= 1 @stop-counter))
    (is (nil? (obj/stop! id)))
    (is (= 1 @stop-counter)))

  (obj/stop-all!))

(deftest test-stop-conc
  (obj/stop-all!)

  (let [stop-counter (atom 0)
        objects (vec (for [i (range 32)]
                       (obj/register
                         (Object.)
                         {:stopfn (fn [_]
                                    (Thread/sleep (min 10 (rand-int 100)))
                                    (swap! stop-counter inc))})))]
    (mapv deref (for [o (concat (shuffle (vec objects))
                                (shuffle (vec objects)))]
                  (future
                    (obj/stop! o))))

    (->> "each object is stopped exactly once"
         (is (= (count objects) @stop-counter))))

  (let [stop-counter (atom 0)
        deps (vec (for [i (range 16)]
                    (obj/register
                      (Object.)
                      {:stopfn (fn [_]
                                 (Thread/sleep (min 10 (rand-int 100)))
                                 (swap! stop-counter inc))})))
        objects (vec (for [i (range 16)]
                       (obj/register
                         (Object.)
                         {:deps (take 3 (shuffle deps))
                          :stopfn (fn [_]
                                    (Thread/sleep (min 10 (rand-int 100)))
                                    (swap! stop-counter inc))})))]

    (mapv deref (for [o (shuffle (concat deps objects deps))]
                  (future
                    (obj/stop! o))))

    (->> "each object is stopped exactly once"
         (is (= (+ (count objects)
                   (count deps)) @stop-counter))))

  (is (empty? (obj/id-seq))))

(obj/defsingleton ::test-need-singleton
  (Object.))

(deftest test-need
  (obj/stop-all!)

  (is (thrown? Throwable (obj/need nil)))
  (is (thrown? Throwable (obj/need (Object.))))

  (let [o (obj/register (Object.) {:alias ::o})]
    (is (identical? o (obj/need ::o)))
    (is (identical? o (obj/need o "foo"))))

  (is (identical? (obj/singleton ::test-need-singleton)
                  (obj/need ::test-need-singleton)))

  (obj/stop-all!))

(obj/defsingleton ::test-singleton
  (Thread/sleep (min 10 (rand-int 100)))
  (Object.))

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

  (let [res-atom (atom [])]
    (dotimes [x 100]
      (swap! res-atom conj
             (future
               (Thread/sleep (min 10 (rand-int 100)))
               (obj/singleton ::test-singleton))))

    (->> "singleton always returns the same instance under conc construction"
         (is (= 1 (count (set (mapv deref @res-atom)))))))

  (let [res-atom (atom [])]
    (dotimes [x 100]
      (future
        (Thread/sleep (min 10 (rand-int 100)))
        (obj/stop! ::test-singleton))
      (swap! res-atom conj
             (future
               (Thread/sleep (min 10 (rand-int 100)))
               (obj/singleton ::test-singleton))))

    (->> "singleton always returns an instance"
         (is (every? some? (mapv deref @res-atom)))))

  (obj/stop-all!))


