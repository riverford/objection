(ns objection.util
  (:import (clojure.lang IDeref)))

(deftype IdentityBox [x]
  IDeref
  (deref [this]
    x)
  Object
  (equals [this o]
    (and (instance? IdentityBox o)
         (identical? x (.-x ^IdentityBox o))))
  (hashCode [this]
    (if (some? x)
      (System/identityHashCode x)
      0)))

(defn identity-box
  [x]
  (->IdentityBox x))