(ns theclaw.core
  (:use [theclaw.util])
  (:require
   [clojure.tools.nrepl.server :as nrepl]
   [theclaw.queues :as mq]
   [theclaw.i2c :as i2c])
  (:gen-class))

(def last-pos (atom [-1 0 0]))

(defn claw-move [_ {:keys [x y sessionid] :as msg}]
  (info "Position" msg)
  (i2c/write-command i2c/d :move (Short/parseShort x) (Short/parseShort y))
  (reset! last-pos [sessionid x y])
  (mq/publish "claw.moved" {:lastx x :lasty y :time (/ (.getTime (java.util.Date.)) 1000)}))

(defn claw-grab [_ {:keys [sessionid] :as msg}]
  (info "Drop" msg)
  ;;after grabbing
  (let [[sessionid x y] @last-pos]
    (i2c/write-command i2c/d :grab)
    (mq/publish "claw.grabbed" {:prizeid 12345 :lastx 10 :lasty 20 :sessionid sessionid})
    (mq/publish "claw.calibrate" {}))
  )

(defn claw-calibrate [_ msg]
  (i2c/write-command :calibrate)
  (info "Calibrate" msg)
  {:msg "hi"})

(def queues
  {"claw.move" #'claw-move
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

