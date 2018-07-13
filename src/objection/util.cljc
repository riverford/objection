(ns objection.util
  #?(:clj (:import (clojure.lang IDeref))))

#?(:clj
   (deftype IdentityBox [x]
     IDeref
     (deref [this] x)
     Object
     (equals [this o]
       (and (instance? IdentityBox o)
            (identical? x (.-x ^IdentityBox o))))
     (hashCode [this]
       (if (some? x)
         (.hashCode x)
         (hash nil))))
   :cljs
   (deftype IdentityBox [x]
     IDeref
     (-deref [this] x)
     IEquiv
     (-equiv [this o] (and (instance? IdentityBox o)
                           (identical? x (.-x o))))
     IHash
     (-hash [this]
       (hash x))))

(defn identity-box
  [x]
  (->IdentityBox x))