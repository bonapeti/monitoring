(ns monitoring.core
  (:require 
    [clojure.core.async :as async :refer [go go-loop chan to-chan <! <!! >! >!! timeout thread thread-call mix mult tap untap close! put! take! pipeline-blocking]]
    [clj-http.client :as http] 
    [riemann.client :as riemann]
    [metrics.core :refer [new-registry]]
    [metrics.meters :refer [meter mark! rates]]
    [clojure.tools.logging :as log]
    )
  (:gen-class))


(import '(java.time Instant) '(java.lang.management ManagementFactory) '(java.net InetSocketAddress InetAddress Socket))

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

(def reg (new-registry))
(def app "traktor")

(defn monitor [g n on? interval request_channel sampler] 
  (go-loop [counter 0]
           (if (deref on?)
            (let [request_time (Instant/now)]
                (>! request_channel 
                    {:group g 
                     :name n 
                     :request_time request_time 
                     :sampler sampler
                     :interval (deref interval)})))
           (<! (timeout (deref interval)))
           (recur (inc counter))))


(defn resolve-dns [host] (InetAddress/getByName host))

(def riemann-error-meter (meter reg "riemann-errors"))

(defn start-riemann [host results]
  (let [c (riemann/tcp-client {:host host}) ]
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
                     :metric metric
                     :ttl (/ (:interval result) 1000)})
                (deref 5000 ::timeout))
            (catch Exception e
              (do 
                (mark! riemann-error-meter)
                (log/error e))))
        (recur (<! results))
      ))))

(defn printer [ch]
  (go-loop [next (<! ch)]
           (if (= app (:group next)) (log/info next))
           (recur (<! ch))))


(def requests (chan))
(def results (chan 20))

(def mult-results (mult results))

(defn take-sample [request] 
  (let [result-map {:name (:name request) :group (:group request) :interval (:interval request)}]
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



(defn ip-address [dns]
  (InetAddress/getByName dns))

(defn validate_dns [dns ip]
  (if (= (InetAddress/getByName ip) (InetAddress/getByName dns)) 1 0)) 

(monitor app "jvm.thread.count" working interval requests (fn [] (.getThreadCount (ManagementFactory/getThreadMXBean))))
(monitor app "jvm.os.load" working interval requests (fn [] (.getSystemLoadAverage (ManagementFactory/getOperatingSystemMXBean))))
(monitor app "internal.errors" working interval requests (fn [] (rates riemann-error-meter)))

(defn open-port [host port time_out]
    (let [socket (new Socket)]
      (try 
        (.connect socket (new InetSocketAddress host port) time_out) "open"
    (finally (.close socket)))))

(defn network-service [url port on? interval request_channel]
(do
  (monitor url "Expected IP" on? interval request_channel (fn [] (ip-address url)))
  (monitor url "Port open?" on? interval request_channel (fn [] (open-port url port (/ (deref interval) 2))))))



(def https 443)
(def ftp-port 21)
(def sftp-port 22)

(network-service "fileservice-msci-com.msci.net" https working interval requests)
(network-service "dataservice-msci-com.msci.net" https working interval requests)
(network-service "fileservice.msci.com" https working interval requests)
(network-service "dataservice.msci.com" https working interval requests) 
(network-service "ftp.msci.com" ftp-port working interval requests)
(network-service "ftps.msci.com" ftp-port working interval requests)
(network-service "sftp.msci.com" sftp-port working interval requests)
(network-service "ftp.barra.com" ftp-port working interval requests)
(network-service "ftps.barra.com" ftp-port working interval requests)
(network-service "sftp.barra.com" sftp-port working interval requests)
(network-service "api-portal-msci-com.msci.net" https working interval requests)
(network-service "api-portal.msci.com" https working interval requests)

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (do
    (prn "Started...")
    )
  )
