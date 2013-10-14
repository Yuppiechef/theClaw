(ns theclaw.core
  (:use [theclaw.util])
  (:require
   [clojure.tools.nrepl.server :as nrepl]
   [theclaw.queues :as mq]
   [theclaw.i2c :as i2c])
  (:gen-class))

(def calibration (atom {:xscale 1000 :yscale 1000}))
(def last-pos (atom [-1 0 0]))

(defn claw-move [_ {:keys [x y z claw sessionid] :as msg}]
  (let [{:keys [xscale yscale]} @calibration
        x (/ (* (Double/parseDouble (str x)) xscale) 100.0)
        y (/ (* (Double/parseDouble (str y)) yscale) 100.0)])
  (info "Position" msg)
  (i2c/write-command i2c/d :move (short x) (short y))
  (reset! last-pos [sessionid x y])
  (mq/publish "claw.moved" {:lastx x :lasty y :time (/ (.getTime (java.util.Date.)) 1000)}))

(defn claw-rawmove [_ {:keys [x y z claw sessionid] :as msg}]
  (info "RAW Position" msg)
  (i2c/write-command i2c/d :move (Short/parseShort x) (Short/parseShort y))
  (if claw 
    (i2c/write-command i2c/d :claw (Short/parseShort z) (Short/parseShort claw)))
  (reset! last-pos [sessionid x y])
  (mq/publish "claw.moved" {:lastx x :lasty y :time (/ (.getTime (java.util.Date.)) 1000)}))

(defn claw-grab [_ {:keys [sessionid] :as msg}]
  (info "Drop" msg)
  ;;after grabbing
  (let [[sessionid x y] @last-pos]
    (i2c/write-command i2c/d :grab)
    (mq/publish "claw.grabbed" {:prizeid 12345 :lastx 10 :lasty 20 :sessionid sessionid})
    (mq/publish "claw.calibrate" {})))

(defn claw-speed [_ {:keys [accel speed sessionid] :as msg}]
  (i2c/write-command i2c/d :speed))

(defn claw-calibrate [_ msg]
  (i2c/write-command :calibrate)
  (info "Calibrate" msg)
  {:msg "hi"})

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
  (info "Starting up queues")
  (mq/connect!)
  (doseq [[n f] queues]
    (mq/listen-queue n f))
  (info "The Claw! Is ready."))

