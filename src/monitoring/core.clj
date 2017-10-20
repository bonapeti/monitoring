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
              ]
            (try   
            (-> c (riemann/send-event 
                    {:host (:group result) 
                     :service (:name result) 
                     :state (state_of riemann-result)
                     :metric metric})
                (deref 5000 ::timeout))
            (catch Exception e))
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
  {:name (:name request) 
   :group (:group request)
   :result (try 
             ((:sampler request)) 
             (catch Exception e 
               (.getMessage e)))})


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

(monitor "fileservice-msci-com.msci.net" "IP" working interval requests (fn [] (ip_address "fileservice-msci-com.msci.net")))

(monitor "dataservice-msci-com.msci.net" "IP" working interval requests (fn [] (ip_address "dataservice-msci-com.msci.net")))
(monitor "ftp.msci.com" "IP" working interval requests (fn [] (ip_address "ftp.msci.com")))
(monitor "ftps.msci.com" "IP" working interval requests (fn [] (ip_address "ftps.msci.com")))
(monitor "sftp.msci.com" "IP" working interval requests (fn [] (ip_address "sftp.msci.com")))
(monitor "ftp.barra.com" "IP" working interval requests (fn [] (ip_address "ftp.barra.com")))
(monitor "ftps.barra.com" "IP" working interval requests (fn [] (ip_address "ftps.barra.com")))
(monitor "sftp.barra.com" "IP" working interval requests (fn [] (ip_address "sftp.barra.com")))

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
