(defproject riverford/objection "0.1.2-SNAPSHOT"
  :description "Manages global resources."
  :url "https://github.com/riverford/objection"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.stuartsierra/dependency "0.2.0"]]
  :profiles {:dev {:plugins [[lein-codox "0.10.3"]]
                   :codox {:source-uri "https://github.com/riverford/objection/blob/{version}/{filepath}#L{line}"
                           :output-path "doc"
                           :metadata {:doc/format :markdown}}}})
