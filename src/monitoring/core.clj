(ns monitoring.core
  (:require 
    [clojure.core.async :as async :refer [go go-loop chan to-chan map <! <!! >! >!! timeout mix mult tap untap close! put! take!]]
    [clj-http.client :as http] 
     )
  (:gen-class))

(import '(java.time Instant))

(defn monitor [on? interval output] 
  (go-loop [counter 0]

           (if (deref on?)
            (let [start_time (Instant/now)]
                (>! output counter)
              ) 
             )
           (<! (timeout (deref interval)))
           (recur (inc counter))))


(defn printer [ch]
  (go-loop []
           (prn (<! ch))
           (recur)))


(def c (chan))
(def interval (atom 10000))
(def working (atom true))
(printer c)
(monitor working interval c)



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
