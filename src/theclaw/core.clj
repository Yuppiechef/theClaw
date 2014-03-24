(ns theclaw.core
  (:use [theclaw.util])
  (:require
   [clojure.tools.nrepl.server :as nrepl]
   [theclaw.queues :as mq]
   [theclaw.i2c :as i2c]
   [theclaw.uart :as uart])
  (:gen-class))

(defmulti to-short class)
(defmethod to-short String [s]
  (Short/parseShort s))

(defmethod to-short Long [s]
  (short s))


(def calibration (atom {:xscale 1000 :yscale 1000}))
(def last-pos (atom [-1 0 0]))

(defn claw-move [_ {:keys [x y z claw sessionid] :as msg}]
  (let [{:keys [xscale yscale]} @calibration
        x (/ (* (Double/parseDouble (str x)) xscale) 100.0)
        y (/ (* (Double/parseDouble (str y)) yscale) 100.0)])
  (info "Position" msg)
  (i2c/write-command @i2c/d :move (short x) (short y))
  (reset! last-pos [sessionid x y])
  (mq/publish "claw.moved" {:lastx x :lasty y :time (/ (.getTime (java.util.Date.)) 1000)}))

(defn claw-rawmove [_ {:keys [x y z claw sessionid] :as msg}]
  (info "RAW Position" msg)
  (i2c/write-command @i2c/d :move (to-short x) (to-short y))  
  (if claw 
    (i2c/write-command @i2c/d :claw (to-short z) (to-short claw)))
  (reset! last-pos [sessionid x y])
  (mq/publish "claw.moved" {:lastx x :lasty y :time (/ (.getTime (java.util.Date.)) 1000)}))

(defn claw-grab [_ {:keys [sessionid] :as msg}]
  (info "Drop" msg)
  ;;after grabbing
  (let [[sessionid x y] @last-pos]
    (i2c/write-command @i2c/d :grab)
    (mq/publish "claw.grabbed" {:prizeid 12345 :lastx 10 :lasty 20 :sessionid sessionid})
    (mq/publish "claw.calibrate" {})))

(defn claw-speed [_ {:keys [accel speed sessionid] :as msg}]
  (i2c/write-command @i2c/d :speed accel speed))

(defn claw-calibrate [_ msg]
  (i2c/write-command @i2c/d :calibrate)
  (info "Calibrate" msg)
  {:msg "hi"})

(defn claw-read-rfid [id]
  (info "Reading RFID: " id)
  (mq/publish "claw.read" {:id id}))

(def queues
  {"claw.move" #'claw-move
   "claw.rawmove" #'claw-rawmove
   "claw.speed" #'claw-speed
   "claw.grab" #'claw-grab
   "claw.calibrate" #'claw-calibrate})

(defn -main [& args]
  (info "Starting nrepl on " (env :REPL_HOST) ":" (env :REPL_PORT))
  (try
    (nrepl/start-server :port (Integer/parseInt (env :REPL_PORT)) :bind (env :REPL_HOST))
    (catch Exception e (.printStackTrace e)))
  (info "Setting up i2c bus")
  (i2c/setup-bus)
  (info "Starting up queues")
  (mq/connect!)
  (doseq [[n f] queues]
    (mq/listen-queue n (fn [m b] (try (f m b) (catch Exception e (.printStackTrace e))))))
  (uart/setup-serial #'claw-read-rfid)
  (info "The Claw! Is ready."))

