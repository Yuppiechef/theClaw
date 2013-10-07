(ns theclaw.queues
  (:use [theclaw.util])
  (:require
   [langohr.core :as rmq]
   [langohr.channel :as lch]
   [langohr.queue :as lq]
   [langohr.consumers :as lcons]
   [langohr.basic :as lb]
   [clojure.data.json :as json]))

(def conn (atom nil))

(defn connect! []
  (swap!
   conn
   #(if % %
        (do
          (info "Connecting to Rabbit")
          (rmq/connect {:host (env :RABBIT_HOST)
                        :port (Integer/parseInt (env :RABBIT_PORT))
                        :username (env :RABBIT_USER)
                        :password (env :RABBIT_PWD)
                        :vhost (env :RABBIT_VHOST)})))))

(defn disconnect! []
  (swap! conn #(do (if % (rmq/close %)) nil)))

(defmulti decode-body (fn [metadata _] (:content-type metadata)))
(defmethod decode-body "application/json"
  [_ body]
  (json/read-str (String. body) :key-fn keyword))

(defmethod decode-body :default
  [_ body] body)

(defn publish [routing-key msg]
  (let [ch (lch/open (connect!))]
    (lb/publish ch "publish" routing-key
                (json/write-str msg :key-fn #(if (keyword? %) (name %) %))
                :content-type "application/json")))

(defn message-fn
  [f ch {:keys [reply-to] :as metadata} ^bytes payload]
  (info "Receiving message for " (:routing-key metadata))
  (info "Metadata: " metadata)
  (let [body (decode-body metadata payload)
        response (f metadata body)]
    (if (and reply-to response)
      (publish reply-to response))))

(defn- listen-fn [queuename f]
  (let [ch (lch/open (connect!))]
    (lq/declare ch queuename :durable true :exclusive false :auto-delete false )
    (lq/bind ch queuename "process" :routing-key queuename)
    (lcons/subscribe ch queuename (partial #'message-fn f) :auto-ack true)))

(defn listen-queue [queuename fn]
  (doto (Thread. (partial listen-fn queuename fn))
    (.setDaemon true)
    (.start)))
