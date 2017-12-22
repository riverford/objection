# objection

> Another component alternative.

objection is about managing graphs of objects that acquire resources globally such
as connections, connection pools, threads, thread pools, servers, processes etc.

Should be usable on its own, or in addition to an existing library in the same vain such as
[component](https://github.com/stuartsierra/component), [integrant](https://github.com/weavejester/integrant) or [mount](https://github.com/tolitius/mount).

## A bit about me

I maintain big ass situated programs with loads of threads and connections and things.

There are threads everywhere I just can't control the number of threads.
Sometimes I wake up in the morning and can't move for threads.

## The objection you WILL have

> Component/Integrant/Mount already solves this problem, why not use that or make a PR or something

## It's true, many people have tried this to solve this problem in the past

* Component
* Mount
* Integrant

Component and Integrant give you declarative dependency graphs of objects and some help with lifecycles of those objects.
It is most useful for static systems where all components are known ahead of time and particpate in the same 'system'.

Mount is interesting but fundamentally seems to bank on 'singletons', and doesn't really attempt to solve the problem for
non-singleton objects.

## What do I want to achieve with objection?

I would like a system that acknowledges that there is a global-ness to things like connections, processes and threads. They consume resources
both internally and externally in ways that are not managed by the garbage collector.

I would like my system to be 'live' and allow me to control the running state from the repl, and introspect what is going on. I never want to lose
a reference to a channel and then be unable to stop it without closing the REPL.

I would like to be able to construct new connections, processes etc at runtime, perhaps on multiple threads and have a library
help me with dependency tracking and clean shut downs.

I also think that global singletons are sometimes the right approach to state, in particular consider the mostly private details of the thread pools
used by clojure, core.async, memoization caches and so on. But also do not think vars are always the right container for these kinds of resource.

I would prefer to be enabled to program with data instead of with objects when possible.

## Warning

This is not yet production ready, I have put this on github in order to gather feedback and thoughts about this approach. As such it is not yet
available on clojars.

## What does it do

- Makes sure you have the ability to stop/close/dispose of objects in the correct order.
- Gives you a registry that you can use to look up registered instances of objects.
- Provides you tools for [singleton](#singletons) objects.
- You can stop things even if you've lost the pointer to them!!!

## How is this different to component / integrant

- Dynamic global registry of stateful objects and their dependency graph
- Registry is mutated over time on any thread, no explicit single initialization
- No notion of 'system' only object instances and the dependencies between them.
- No start function definition needed.
- Also provides a singleton model that provides repl convenience and a way to manage objects
  that you do not want to expose in your api. The core.async threadpool is an example of this.
- No required protocols or boxing around managed objects, can be integrated into existing functions locally without changing function
  signature.

## How is this different to mount

- no coupling to namespaces/vars to reference a singleton
- lazy construction when needed instead of with some kind of start! fn.
- singletons are supported, yet can mix/match with explicit object construction and arg passing styles.

## Usage

### Basics

```clojure
(require '[objection.core :as obj])
```

Lets say we have some resources such as a connection pool, and a process that spawns a thread that
depends on that connection pool, and a naive function start! that spins everything up.

```clojure

;; we will assume some private function to create a conn pool
(defn- create-conn-pool*
  []
  (Object.))

(defn create-conn-pool
  []
  (create-conn-pool*))

(defn spawn-thread
  [db]
  (doto
    (Thread. (fn []
               (while true
                 (try
                   (println "Working with db")
                   (Thread/sleep 5000)
                   (catch InterruptedException e
                     (println "Thread interrupted"))))))
    (.start)))

(defn start!
 []
 (let [db (create-conn-pool)]
   (spawn-thread db)))
```

I'll rewrite these functions to use objection.

```clojure

(defn create-conn-pool
  []
  (obj/register
    (create-conn-pool*)
    {:name "Connection pool"
     :stopfn (fn [cp] (println "closing connection pool") (.shutdown cp))}))

(defn spawn-thread
  [db]
  (obj/register
    (doto
      (Thread. (fn []
                 (while true
                   (try
                     (println "Working with db")
                     (Thread/sleep 5000)
                     (catch InterruptedException e
                       (println "Thread interrupted"))))))
      (.start))
    {:name "Thread doing stuff"
     :stopfn (fn [t] (println "closing thread") (.interrupt t) (.join t))
     :deps [db]}))


(defn start!
 []
 (let [db (create-conn-pool)]
   (spawn-thread db)))
```

Instead of just returning the connection pool, we first register it. We pass
a name and a `:stopfn`. `register` returns the object, so a caller
would just get the connection pool as it did before.

Similarly we register the thread, we use the key `:deps` to tell objection that
the thread is dependent on the db.

Now we can call start!

```clojure
(start!)
```

Call `obj/status` to print information about the registered objects.

```clojure
(obj/status)

;; 2 objects registered.
;; -------
;; objects:
;; -------
;; 367f10e2-588a-4022-af17-fb13822e09d6  -  Connection pool
;; cb54d904-6164-48cb-b651-32626b282f1f  -  Thread doing stuff
;; => nil
```

As you can see objection keeps track of these resources for you. On the left, is the auto-generated
id of each object, on the right - the name we passed in to register.

Say we want to shut down the connection pool we can call `stop!` to do this.

We do not have a pointer to the connection pool, as we just called (start!) and start does not return it.

Thats ok, we can use the id (or prefix of id) instead.

```clojure
(obj/stop! "367f10e2-588a-4022-af17-fb13822e09d6")
;; closing thread
;; thread interrupted
;; closing connection pool
```

As you can see, objection knew the thread was dependent on the connection pool, so first stopped the thread for you.

And yes, there is a function `(stop-all!)` for when you absolutely, positively have to kill every object in the room.

Lets start the system again, and see what else we can do.

```clojure
(start!)

(obj/status)

;; 2 objects registered.
;; ------------------------
;; objects:
;; -------
;; 3f9433b2-78b6-405d-af18-7e9484a70341  -  Connection pool
;; 4602ce42-d03f-4b30-98a2-e543b22b0976  -  Thread doing stuff
;; => nil
```

You can use the `object` function, to return a registered instance.

```clojure
(obj/object "4602ce42-d03f-4b30-98a2-e543b22b0976")
;; => #object[java.lang.Thread 0x4242a3db "Thread[Thread-14,5,main]"]
```

This can often be quite useful at the repl.

You can use the function `alias` to give the object a name that you can use in place of the id.
An object can have many aliases, but each alias can only be assigned to one object.

```clojure
(obj/alias "4602ce42-d03f-4b30-98a2-e543b22b0976" ::my-thread)

(obj/object ::my-thread)
;; => #object[java.lang.Thread 0x4242a3db "Thread[Thread-14,5,main]"]
```

n.b You can also pass an alias via `:alias` when calling `register`, or multiple aliases with `:aliases`.

Most functions can take an id, alias or object instance interchangeably, e.g

```clojure
(obj/stop! ::my-thread)

;; closing thread
;; thread interrupted
;; => nil
```

### Singletons

If you are sure you only need one instance of a thing in a program at a time - you can
use `defsingleton` to define a singleton.

```clojure
(import java.util.concurrent.Executors)

(obj/defsingleton ::threadpool
  (println "Creating threadpool")
  (obj/register
   (Executors/newFixedThreadPool 4)
   {:stopfn (fn [e] (println "closing thread pool") (.shutdown e))}))
```

A singleton definition is not evaluated immediately. You use the function `singleton` to
return a singleton instance, at which point it will be constructed if necessary.

```clojure

(obj/singleton ::threadpool)
;; creating threadpool
;;
;; =>
;; #object[java.util.concurrent.ThreadPoolExecutor
;;         0xa15f47f
;;        "java.util.concurrent.ThreadPoolExecutor@a15f47f[Running, pool size = 0, active threads = 0, queued tasks = 0, completed tasks = 0]"]
```

Calling `singleton` again, will just return the instance.

```clojure

(obj/singleton ::threadpool)
;; #object[java.util.concurrent.ThreadPoolExecutor
;;         0xa15f47f
;;        "java.util.concurrent.ThreadPoolExecutor@a15f47f[Running, pool size = 0, active threads = 0, queued tasks = 0, completed tasks = 0]"]
```

A constructed singleton is always registered, though if you can call `register` explicitly to
pass extra opts (such as `:stopfn`).

If you redefine the singleton, e.g to increase the number of threads in the pool. objection will automatically `stop!`
the instance (of course first closing any dependent objects)

```clojure

(obj/defsingleton ::threadpool
  (println "Creating threadpool")
  (obj/register
   (Executors/newFixedThreadPool 8) ;; moar threads
   {:stopfn (fn [e] (println "closing thread pool") (.shutdown e))}))

;; closing threadpool
;; => nil
```

### Other stuff

- You can implement `IAutoStoppable` if you don't want to provide a `:stopfn`, objection knows about `java.lang.AutoCloseable` already.
- You can get the objection id of an instance/alias via `id`
- You can get info such as name about an instance via `describe`
- You can can call `id-seq` to get a sequence of all the registered ids.
- You can call `depend`/`undepend`/`alias`/`unalias` to introduce dependencies and aliases
  after an instance is registered.
- `need` can be used as a kind of ensure/assert that throws if an instance is not available. e.g
  `(need ::threadpool)` would return my thread pool if an object is registered with that alias, or a singleton exists
  with that key.

## Todo

 - Is this a good idea?
 - User metadata / query
 - More automatic data (registered time?)
 - objection.integrant (automatic integrant registration)
 - Tests
 - More introspection / mBeans?
 - Visualization

## License

Copyright © 2017 Riverford Organic Farmers

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
