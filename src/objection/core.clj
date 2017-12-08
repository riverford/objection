(ns objection.core
  "Objection helps you manage graphs of stateful objects that acquire resources.

  It is good for things like, connection pools, threads, thread pools, queues, channels etc."
  (:require [com.stuartsierra.dependency :as dep]
            [objection.util :as util])
  (:refer-clojure :exclude [alias])
  (:import (java.util UUID IdentityHashMap)
           (java.lang AutoCloseable)
           (java.util.concurrent ConcurrentHashMap)
           (clojure.lang IDeref IFn)))

(defonce ^:private reg
  (atom {:g (dep/graph)
         :id {}
         :meta {}
         :obj {}
         :alias {}}))

(defn- get-id
  [st x]
  (let [{:keys [id obj singletons alias]} st]
    (if (contains? id x)
      x
      (or (when-some [a (alias x)]
            (get-id st a))
          (obj (util/identity-box x))))))

(defn id
  "Returns the id of the object, the id was assigned
  when the object was first registered."
  [x]
  (get-id @reg x))

(defn object
  "Returns a registered object, can pass either an id, alias or object instance."
  [x]
  (let [st @reg
        id (get-id st x)]
    (when (some? id)
      (-> st :id (get id)))))

(defn- do-alias
  [st x name]
  (if (nil? (get-id st x))
    (throw (Exception. "Not a registered object..."))
    (let [existing-id (-> st :alias (get name))]
      (if (or (nil? existing-id) (= (get-id  st x) existing-id))
        (-> (assoc-in st [:alias name] (get-id st x))
            (update-in [:meta (get-id st x) :aliases] (fnil conj #{}) name))
        (throw (Exception. "Not allowed to reuse existing alias for different object."))))))

(declare do-depend)

(defn- do-register
  [st id obj opts]
  (let [id (or (get-id st obj) id)]
    (as->
      st st
      (assoc-in st [:id id] obj)
      (assoc-in st [:obj (util/identity-box obj)] id)
      (assoc-in st [:alias id] id)
      (update-in st [:meta id] merge (select-keys opts [:name :stopfn]))
      (reduce #(do-alias %1 id %2) st (if (contains? opts :alias)
                                        (cons (:alias opts) (:aliases opts))
                                        (:aliases opts)))
      (reduce #(do-depend %1 id %2) st (:deps opts)))))

(defprotocol IAutoStoppable
  "A protocol that can be extended to types in order to tell objection how to stop! them if a :stopfn is not provided
  on registry."
  (-stop! [this] "Extend to types to provide a stop behaviour for objects of that type."))

(extend-protocol IAutoStoppable
  nil
  (-stop! [this]
    nil)
  Object
  (-stop! [this]
    nil)
  AutoCloseable
  (-stop! [this]
    (.close this)))

(defn register
  "Registers the object with objection and returns it, will assign it an id automatically.

  Opts:

  `:name` - a name to use for the object, doesn't have to be unique.
  `:aliases` - a sequence of aliases to apply to the object
  `:deps` - a sequence of dependencies, supports passing objection ids, aliases or registered objects.
  `:stopfn` - a function of the object that performs any shutdown logic.
  "
  ([obj] (register obj {}))
  ([obj opts]

   (assert (some? obj))
   (assert (not (false? obj)))

   (let [id (str (UUID/randomUUID))]
     (swap! reg do-register id obj opts)
     obj)))

(defn alias
  "Aliases an object under the provided key, each alias can only be assigned to one object, so
  make sure it is unique."
  [x alias]
  (swap! reg do-alias x alias)
  nil)

(defn id-seq
  "Returns the seq of registered object ids."
  []
  (keys (:id @reg)))

(defn- do-depend
  [st x dependency]
  (if-some [id (get-id st x)]
    (if-some [id2 (get-id st dependency)]
      (update st :g dep/depend id id2)
      (throw (Exception. "Dependency is not a registered object...")))
    (throw (Exception. "Not a registered object..."))))

(defn- do-undepend
  [st x dependency]
  (if-some [id (get-id st x)]
    (if-some [id2 (get-id st dependency)]
      (update st :g dep/remove-edge id id2)
      (throw (Exception. "Dependency is not a registered object...")))
    (throw (Exception. "Not a registered object..."))))

(defn depend
  "Makes `x` dependent on `dependency`, both can be registered object instances, aliases or ids.
  When you `(stop! dependency)` objection will make sure that `x` is stopped first."
  [x dependency]
  (swap! reg do-depend x dependency)
  nil)

(defn undepend
  "Removes a dependency relationship between `x` and `dependency`, both of which can be registered object instances, aliases or ids. "
  [x dependency]
  (swap! reg do-undepend x dependency)
  nil)

(defn dependencies
  "Returns the ids of dependencies of `x`."
  [x]
  (dep/immediate-dependencies (:g @reg) (id x)))

(defn dependents
  "Returns the ids of the dependents of `x`."
  [x]
  (dep/immediate-dependents (:g @reg) (id x)))

(defn stop!
  "Runs the stopfn of `x` or the type specific AutoStoppable impl. e.g on AutoCloseable objects .close will be called.

  Removes the object from the registry."
  [x]
  (when x
    (let [stopping (volatile! nil)]
      (swap! reg (fn [st]
                   (if-some [id (get-id st x)]
                     (let [obj (-> st :id (get id))
                           meta (-> st :meta (get id))
                           aliases (:aliases meta)
                           dependents (dep/immediate-dependents (:g st) id)]
                       (vreset! stopping {:obj obj
                                          :meta meta
                                          :id id
                                          :dependents dependents})
                       (as->
                         st st
                         (update st :id dissoc id)
                         (update st :obj dissoc (util/identity-box obj))
                         (update st :meta dissoc id)
                         (update st :g dep/remove-all id)
                         (reduce #(update %1 :alias dissoc %2) st (cons id aliases))))
                     st)))
      (let [{:keys [meta obj dependents]} @stopping]
        (run! stop! dependents)
        (let [stopfn (:stopfn meta)]
          (if (some? stopfn)
            (stopfn obj)
            (-stop! obj)))))))

(defn stop-all!
  "Stops all current registered objects."
  []
  (run! stop! (id-seq)))

(defn rename!
  "Changes the :name of `x`"
  [x s]
  (swap! reg (fn [st] (if-some [id (get-id st x)]
                        (assoc-in st [:meta id :name] (str s))
                        st)))
  nil)

(defonce ^:private singleton-registry (atom {}))

(defn singleton
  "Like (object `k`) but if a singleton is registered under the key `k`, it will be constructed if necessary
  in order to return the instance."
  [k]
  (or (object k)
      (when-some [ctor (get @singleton-registry k)]
        (locking k
          (or (object k)
              (let [ret (ctor)]
                ;; object may already be registered
                ;; but thats ok
                (register ret {:aliases [k]})
                (alias ret k)
                ret))))))

(defn need
  "Tries to resolve `x` to a registered object, or singleton - throws an exception with the message if not possible."
  ([x] (need x nil))
  ([x error-message]
   (assert (some? x))
   (assert (not (false? x)))
   (or (object x)
       (singleton x)
       (throw (IllegalArgumentException. (str (or error-message "Not a registered object.")))))))

(defn put-singleton*
  [k f]
  (locking k
    (stop! k)
    (swap! singleton-registry assoc k f)
    nil))

(defmacro defsingleton
  "Defines a singleton that can be returned via (singleton k), if an instance already exists, it is returned - else the body is run
  to construct the instance.

  If you would prefer to return an existing instance without the possibility of constructing the instance if it does not exist, use (object k).

  Redefinition of a singleton will stop any existing instances.

  Singletons are always registered and they also receive an alias of the key used in the definition.

  To introduce dependencies, stopfn, additional aliases etc, you can register the objection in the body
  of the singleton in the normal way."
  [k & body]
  `(do
     (put-singleton* ~k (fn []
                          (register
                            (do ~@body)
                            {:aliases [~k]})))))

(defn describe
  "Returns information about `x`, which can be a registered object, alias or id."
  [x]
  (let [id (id x)
        meta (-> @reg :meta (get id))
        aliases (get meta :aliases)
        singleton-key (or (some (fn [a] (when (contains? @singleton-registry a) a)) aliases)
                          (when (contains? @singleton-registry x) x))]
    (merge
      {:registered? (some? id)}
      (when singleton-key
        {:singleton-key singleton-key})
      (select-keys meta [:name :aliases]))))

(defn status
  "Prints information about currently registered objects."
  []
  (let [st @reg
        ids (keys (:id st))]
    (println (count ids) "objects registered.")
    (when (seq ids)
      (println "-------")
      (println "objects:")
      (println "-------")
      (doseq [id ids
              :let [meta (get (:meta st) id)]]
        (println id " - " (or (:name meta)
                              (first (:aliases meta))
                              (class (get (:id st) id))))))))

(comment
  (defsingleton ::db
    (println "starting 42")
    (register
      42
      {:name "Database Connection Pool"
       :stopfn (partial println "stopping")}))

  (defsingleton ::thingy
    (need ::db)
    (println "starting 64")
    (register
      64
      {:stopfn (partial println "stopping")
       :name "A thingy"
       :deps [::db]}))

  (defn thing-that-needs-thingy
    [thingy]
    (need thingy "A registered thingy is required")


    (println "Starting a thing")
    (register (UUID/randomUUID) {:stopfn (partial println "stopping")
                                 :deps [thingy]})))