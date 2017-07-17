(ns monitoring.core
  (:require 
    [clojure.core.async :as async :refer [go go-loop chan to-chan map <! <!! >! >!! timeout thread thread-call mix mult tap untap close! put! take!]]
    [clj-http.client :as http] 
     )
  (:gen-class))

(import '(java.time Instant))

(defn monitor [on? interval request_channel sampler] 
  (go-loop [counter 0]
           (if (deref on?)
            (let [request_time (Instant/now)]
                (>! request_channel {:counter counter :request_time request_time :sampler sampler})))
           (<! (timeout (deref interval)))
           (recur (inc counter))))


(defn dispatcher [request_channel result_channel]
  (go-loop [request (<! request_channel)]
           (>! result_channel ((:sampler request)))
           (recur (<! request_channel))))

(defn printer [ch]
  (go-loop []
           (prn (<! ch))
           (recur)))


(def requests (chan))
(def results (chan))

(def interval (atom 10000))
(def working (atom true))
(printer results)

(dispatcher requests results)
(monitor working interval requests rand)



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
