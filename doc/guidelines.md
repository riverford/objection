# Usage Guidelines

## Constructors

When you need to create an object, have the object be registered as part of constructor with `construct`.
This may require wrapping an existing constructor function.

e.g 

```clojure
(defn web-server 
  [db]
  (let [port (util/free-port)]
   (obj/construct 
    {:deps [db]
     :name "Web Server"
     :data {:db db
            :port port}
     :stopfn (fn [server] (.stop server))}
    (jetty/run-jetty
      (handler db)
      {:port port
       :join? false}))))
```

## Singletons 

Singletons should be used sparingly, only when they represent something incidental that can be encapsulated well.

A good example of a singleton is a single thread pool used to parallelize work, but where you may now want to expose the threadpool to callers.
The core.async 'go' thread pool `clojure.core.async.impl.dispatch/executor` is an example.

Another example of an incidental singleton below:

```clojure
  
(obj/defsingleton ::executor
 {:reload? false}
 (-> (Executors/newFixedThreadPool 8)
     (obj/register 
       {:name "HTTP multi-get Executor"
        :data {:threads 8}
        :stopfn (fn [executor] (.shutdown executor))})))  

(defn multi-get
  "Returns a map of url to the result of calling http/get on the url."
  [urls]
  (let [executor (obj/singleton ::executor)
        futs (mapv (fn [url] (.submit executor ^Callable (fn [] (http/get url)))) url)]
    (zipmap urls (map deref futs))))

```

In this example the thread pool is an internal optimization that is not exposed to nor injected
by the caller. 

Note: a fixed threadpool is only used to demonstrate the idea, here a custom cached executor pool 
with niceties like thread naming, idle timeouts, bounded queue and a back-pressure providing policy like caller runs would be better
for IO work like this.




