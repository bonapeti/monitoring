(ns monitoring.core
  (:require 
    [clojure.core.async :as async :refer [go go-loop chan to-chan <! <!! >! >!! timeout thread thread-call mix mult tap untap close! put! take! pipeline-blocking]]
    [clj-http.client :as http] 
     )
  (:gen-class))

(import '(java.time Instant) '(java.lang.management ManagementFactory))

(defn monitor [on? interval request_channel sampler] 
  (go-loop [counter 0]
           (if (deref on?)
            (let [request_time (Instant/now)]
                (>! request_channel {:counter counter :request_time request_time :sampler sampler})))
           (<! (timeout (deref interval)))
           (recur (inc counter))))


(defn printer [ch]
  (go-loop []
           (prn (<! ch))
           (recur)))


(def requests (chan))
(def results (chan))

(defn take-sample [request] ((:sampler request)))


(pipeline-blocking 1 results (map take-sample) requests)

(def interval (atom 10000))
(def working (atom true))
(printer results)

(defn thread-count [] (.getThreadCount (ManagementFactory/getThreadMXBean)))


(monitor working interval requests (fn [] (.getThreadCount (ManagementFactory/getThreadMXBean))))



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
