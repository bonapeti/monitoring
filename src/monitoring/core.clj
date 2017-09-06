(ns monitoring.core
  (:require 
    [clojure.core.async :as async :refer [go go-loop chan to-chan <! <!! >! >!! timeout thread thread-call mix mult tap untap close! put! take! pipeline-blocking]]
    [clj-http.client :as http] 
    [riemann.client :as riemann] 
    )
  (:gen-class))

(import '(java.time Instant) '(java.lang.management ManagementFactory) '(java.net InetAddress))


(defn monitor [g n on? interval request_channel sampler] 
  (go-loop [counter 0]
           (if (deref on?)
            (let [request_time (Instant/now)]
                (>! request_channel {:group g :name n :counter counter :request_time request_time :sampler sampler})))
           (<! (timeout (deref interval)))
           (recur (inc counter))))


(defn resolve-dns [host] (InetAddress/getByName host))
;;(defn connect [host port] (


(defn start-riemann [host results]
  (let [c (riemann/tcp-client {:host host})]
     (go-loop [result (<! results)]
        (let [riemann-result (:result result) metric (if (instance? Number riemann-result) riemann-result nil)]
              (-> c (riemann/send-event {:host (:group result) :service (:name result) :state nil :metric metric})
          (deref 5000 ::timeout)))
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
   :group (:group request)
   :result (try 
             ((:sampler request)) 
             (catch Exception e (.getMessage e)))})


(pipeline-blocking 1 results (map take-sample) requests)

(def interval (atom 10000))
(def working (atom true))
;;(printer results)
(start-riemann "127.0.0.1" results)

(monitor "localmachine" "jvm.thread.count" working interval requests (fn [] (.getThreadCount (ManagementFactory/getThreadMXBean))))
(monitor "localmachine" "jvm.os.load" working interval requests (fn [] (.getSystemLoadAverage (ManagementFactory/getOperatingSystemMXBean))))
(monitor "index.hu" "dns" working interval requests (fn [] (if (= (resolve-dns "217.20.130.99") (resolve-dns "index.hu")) 1 0 )))

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
