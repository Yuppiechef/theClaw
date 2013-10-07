(ns theclaw.core
  (:use [theclaw.util])
  (:require
   [clojure.tools.nrepl.server :as nrepl]
   [theclaw.queues :as mq])
  (:gen-class))

(defn claw-move [_ {:keys [x y sessionid] :as msg}]
  (info "Position" msg)
  (mq/publish "claw.moved" {:lastx 10 :lasty 20 :sequence 1}))

(defn claw-grab [_ {:keys [sessionid] :as msg}]
  (info "Drop" msg)
  ;;after grabbing
  (mq/publish "claw.grabbed" {:prizeid 12345 :x 10 :y 20})
  (mq/publish "claw.calibrate" {})
  )

(defn claw-calibrate [_ msg]
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

