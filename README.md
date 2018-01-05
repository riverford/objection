# objection

> Helps you manage the necessary objects in your programs.

objection is about managing graphs of objects that acquire resources globally such
as connections, connection pools, threads, thread pools, servers, processes etc.

## Rationale
I maintain big situated programs that need to talk to multiple databases, manage threadpools, processes and so on.

> Component/Integrant/Mount already solves this problem, why not use that or make a PR or something

Objection takes a different approach that is rather more dynamic than [component], [integrant] or [mount], after feeling that they bank too hard on a static application whose topology does not change at runtime.

I am a big fan of both [integrant] and [component] and have been using them for almost as long as I have been using clojure.

Objections idea of a live and mutable system makes a different set of trade offs when approaching the problem.
One thing that is particularily nice about both integrant and component is the declarative wiring of components - I would suggest using either library alongside objection if that has value for your application.

## Installation

Not yet on clojars. Give me a few minutes :)

## Usage

### Register an object
Objection manages regular old objects that have been registered with objection.
You can register an object with `register`. See also the `construct` macro.

```clojure
(require '[objection.core :as obj]
         '[ring.adapter.jetty :as jetty]
         '[ring.util.response :as resp])

(defn start-server
  [handler port]
  (-> (jetty/run-jetty handler {:port port
                                :join? false})
      (obj/register
       {;; optional
        :name (str "Jetty Server on port " port)
        :alias [:jetty-server 8080]
        :data {:handler handler
               :port 8080}
        ;; optional, but wise!
        :stopfn (fn [server] (.stop server))})))

(start-server (fn [_] (resp/response "Hello World")) 8080)
```

### Inspect the registry

Each registred object is assigned an id, you can get all the currently registered object ids from the `id-seq` function. Alternatively use the function `(status)` to print some useful data.

```clojure
(obj/status)
;; 1 objects registered.
;; -------
;; objects:
;; -------
;; 81e73f11-dc5f-4576-b706-420fa53856d7  - Jetty Server on port 8080
```

### Inspect an object

Each registered object can be queried. Each function that takes a registered object
will work on an id (or prefix), alias, as well as the object itself.

`describe` will return data about the object.

```clojure
(obj/describe "81e7")
;; =>
{:registered? true,
 :id "81e73f11-dc5f-4576-b706-420fa53856d7",
 :name "Jetty Server on port 8080",
 :data {:handler #object[user$eval1843$fn__1844 0x45a21de2 "user$eval1843$fn__1844@45a21de2"]
        :port 8080},
 :aliases #{[:jetty-server 8080]},
 :deps #{},
 :dependents #{}}
```

`object` will return the object instance itself.
```clojure
(obj/object "81e7")
```

`id` will return the id of the object or alias if it is registered.
```clojure
(obj/id [:jetty-server 8080])
(obj/id (obj/object "81e7"))
(obj/id "81e7")

;; all return the string
;; =>
"81e73f11-dc5f-4576-b706-420fa53856d7"
```

### Stop an object

Registered objects can be stopped using the stop! function. Again an alias/id etc can be used interchangeably with the object.
`stop!` will call the `:stopfn` if one was supplied on registry, if not it will look for an implementation of the protocol `obj/IAutoStoppable` or `java.lang.AutoCloseable`.

```clojure
(obj/stop! "81e73f11-dc5f-4576-b706-420fa53856d7")
```

You can use `stop-all!` to stop each and every object currently registered.
```clojure
(obj/stop-all!)
```

### Dependencies

Registered objects can be dependent on one another, manage dependencies through
the `:deps` opt on registry, or using the `depend`/`undepend` functions.

```clojure
(defn arbitrary-object
 [server]
 ;; we use the 'construct' macro rather than register
 ;; it takes the same opts, but takes a body to be executed rather
 ;; than an existing instance.
 ;; Because of this, dependencies can be locked for stopping
 ;; while the object is constructed
 (obj/construct
  {:deps [server]
   :stopfn (fn [_] (println "stopping object"))}
  (Object.)))

;; restart the server and construct the object.
(let [server (start-server (fn [_] (resp/response "Hello World")) 8080)]
 (arbitrary-object server))

;; now if we stop the server, objection will first stop the dependent object.
(obj/stop! [:jetty-server 8080])
;; stopping object
;; => nil
```

### Singletons

Sometimes global singletons are not so bad, if they are used carefully.
For example a good candidate for a singleton is a threadpool that is local to a namespace and used to optimize functions whose api in no way needs to reflect the implementation-detail of the thread pool.
e.g the `go` macro in core.async

Define a singleton with `defsingleton`, defsingleton does not immediately evaluate its body, it is rather lazy, so they are safe to define in any order and it does not really matter what namespace they are in in the code .

Redefinition of the singleton will stop any existing instance for the singleton (and any dependent objects).

```clojure
(obj/defsingleton :my-threadpool
 ;; the register is optional as singletons will always be registered
 ;; but you can use it if you want to supply a name or deps etc
 (obj/register
  (java.util.concurrent.Executors/newFixedThreadPool 4)
  {:name "My Threadpool"
   :stopfn (fn [tp] (println "Closing threadpool") (.shutdown tp))}))
```

Grab a singleton with `singleton`, at this point the singleton definition will be evaluated
and a registered object will be returned. Repeatedly calling singleton will return the same object.

```clojure
(obj/singleton :my-threadpool)
```

Singletons are always registered and aliased with the key of the singleton. So you can call any of the normal objection functions with the singleton key
e.g
```clojure
(obj/describe :my-threadpool)
;; =>
{:registered? true,
 :singleton-key :my-threadpool,
 :singleton-ns user,
 :id "adb8b07b-959d-4327-8442-722d813e17e0",
 :aliases #{:my-threadpool},
 :deps #{},
 :dependents #{}}
```

## Todo

Pull requests wecome!

- tools.namespace reloaded support
- More introspection
- Better query based on user data
- Middleware (on-stop/on-start etc)
- Visualization and tools for humans

## License

Copyright © 2017 Riverford Organic Farmers

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[integrant]: https://github.com/weavejester/integrant
[component]: https://github.com/stuartsierra/component