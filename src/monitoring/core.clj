(ns monitoring.core
  (:require 
    [clojure.core.async :as async :refer [go go-loop chan to-chan <! <!! >! >!! timeout thread thread-call mix mult tap untap close! put! take! pipeline-blocking]]
    [clj-http.client :as http] 
    [riemann.client :as riemann] 
    )
  (:gen-class))

(import '(java.time Instant) '(java.lang.management ManagementFactory) '(java.net InetAddress))

(defprotocol HasState
  "Defines Riemann state for an object"
  (state_of [this] "Returns the state string"))

(extend-protocol HasState
  java.lang.Object
  (state_of [this]
    (.toString this)))

(extend-protocol HasState
  java.net.InetAddress
  (state_of [this]
    (.getHostAddress this)))

(defn monitor [g n on? interval request_channel sampler] 
  (go-loop [counter 0]
           (if (deref on?)
            (let [request_time (Instant/now)]
                (>! request_channel 
                    {:group g 
                     :name n 
                     :request_time request_time 
                     :sampler sampler})))
           (<! (timeout (deref interval)))
           (recur (inc counter))))


(defn resolve-dns [host] (InetAddress/getByName host))

(defn start-riemann [host results]
  (let [c (riemann/tcp-client {:host host})]
     (go-loop [result (<! results)]
        (let [riemann-result (:result result) 
              metric (if (instance? Number riemann-result) riemann-result nil)
              state (:state result)
              ]
            (try   
            (-> c (riemann/send-event 
                    {:host (:group result) 
                     :service (:name result) 
                     :state (if (not (nil? state)) state (state_of riemann-result))
                     :metric metric})
                (deref 5000 ::timeout))
            (catch Exception e
              (prn (.getMessage e))))
              )
        (recur (<! results))
      )))

(defn printer [ch]
  (go-loop []
           (prn (<! ch))
           (recur)))


(def requests (chan))
(def results (chan 20))

(def mult-results (mult results))

(defn take-sample [request] 
  (let [result-map {:name (:name request) :group (:group request)}]
    (try 
         (assoc result-map :result ((:sampler request)) :state "ok")
      (catch Exception e 
          (assoc result-map :result (.getMessage e) :state "critical")
          ))))


(pipeline-blocking 1 results (map take-sample) requests)

(def interval (atom 10000))
(def working (atom true))

(def riemann-results (chan 20))
(tap mult-results riemann-results)
(start-riemann "127.0.0.1" riemann-results)

(def printer-results (chan 20))
(tap mult-results printer-results)
(printer printer-results)

(def app "traktor")

(defn ip_address [dns]
  (InetAddress/getByName dns))

(defn validate_dns [dns ip]
  (if (= (InetAddress/getByName ip) (InetAddress/getByName dns)) 1 0)) 

(monitor app "jvm.thread.count" working interval requests (fn [] (.getThreadCount (ManagementFactory/getThreadMXBean))))
(monitor app "jvm.os.load" working interval requests (fn [] (.getSystemLoadAverage (ManagementFactory/getOperatingSystemMXBean))))

(defn network-service [url on? interval request_channel]
  (monitor url "Expected IP" on? interval request_channel (fn [] (ip_address url))))


(network-service "fileservice-msci-com.msci.net" working interval requests)
(network-service "dataservice-msci-com.msci.net" working interval requests)
(network-service "ftp.msci.com" working interval requests)
(network-service "ftps.msci.com" working interval requests)
(network-service "sftp.msci.com" working interval requests)
(network-service "ftp.barra.com" working interval requests)
(network-service "ftps.barra.com" working interval requests)
(network-service "sftp.barra.com" working interval requests)
(network-service "api-portal-msci-com.msci.net" working interval requests)

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
