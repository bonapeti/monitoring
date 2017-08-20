(ns monitoring.core
  (:require 
    [clojure.core.async :as async :refer [go go-loop chan to-chan <! <!! >! >!! timeout thread thread-call mix mult tap untap close! put! take! pipeline-blocking]]
    [clj-http.client :as http] 
    [riemann.client :as riemann] 
    )
  (:gen-class))

(import '(java.time Instant) '(java.lang.management ManagementFactory) '(java.net InetAddress))


(defn monitor [n on? interval request_channel sampler] 
  (go-loop [counter 0]
           (if (deref on?)
            (let [request_time (Instant/now)]
                (>! request_channel {:name n :counter counter :request_time request_time :sampler sampler})))
           (<! (timeout (deref interval)))
           (recur (inc counter))))


(defn resolve-dns [host] (InetAddress/getByName host))

(defn start-riemann [host results]
  (let [c (riemann/tcp-client {:host host})]
     (go-loop [result (<! results)]
        (-> c (riemann/send-event {:service (:name result) :state nil})
          (deref 5000 ::timeout))
        (recur (<! results))
      )))

(defn printer [ch]
  (go-loop []
           (prn (<! ch))
           (recur)))


(def requests (chan))
(def results (chan))

(defn take-sample [request] 
  {:name (:name request) 
   :result (try 
             ((:sampler request)) 
             (catch Exception e (.getMessage e)))})


(pipeline-blocking 1 results (map take-sample) requests)

(def interval (atom 10000))
(def working (atom true))
;;(printer results)
(start-riemann "127.0.0.1" results)

(monitor "jvm.thread.count" working interval requests (fn [] (.getThreadCount (ManagementFactory/getThreadMXBean))))
(monitor "jvm.os.load" working interval requests (fn [] (.getSystemLoadAverage (ManagementFactory/getOperatingSystemMXBean))))
(monitor "index.hu" working interval requests (fn [] (resolve-dns "index.hu")))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (do
    (def c (chan))
    (def interval (atom 10000))
    (printer c)
    (monitor interval c)
    (prn "Started...")
    )
  )
