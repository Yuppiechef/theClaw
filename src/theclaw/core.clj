(ns theclaw.core
  (:require [server.socket :as s])
  (:require [clojure.tools.nrepl.server :as nrepl])
  (:import [java.io DataOutputStream])
  (:gen-class))

(defonce server (atom nil))
(defonce client (atom nil))

(defn ubyte [val]
  (if (>= val 128)
    (byte (- val 256))
    (byte val)))

(defn uint32 [bytes]
  (reduce #(+ (bit-shift-left %1 8) %2) 0 (reverse bytes)))

(defn read-uint32 [in]
  (uint32 (for [x (range 4)] (.read in))))

(defn to-uint32 [value]
  (first
   (reduce (fn [[a v] _]
             [(conj a (bit-and v 0xFF)) (bit-shift-right v 8)])
           [[] value] (range 4))))

(defn write-uint32 [value]
  (map ubyte (to-uint32 value)))

(defn signed [n]
  (if (bit-test n 31)
    (- n 4294967296) n))

(defn serve [in out]
  (let [[command p1 p2 res :as cmd] (repeatedly 4 #(read-uint32 in))]
    (.write
     out
     (byte-array
      (concat
       (write-uint32 command)
       (write-uint32 p1)
       (write-uint32 p2)
       (write-uint32 res))))
    (cond
     (.isClosed (:server-socket @server)) (do (println "Server closed") nil)
     (= command -16843009) (do (println "Socket closed") nil)
     :else (recur in out))))

(defn server-fn [in out]
  (#'serve in out))

(defn start []
  (reset! server (s/create-server 8888 #'server-fn)))

(defn stop []
  (s/close-server @server)
  (reset! server nil))

(defn connect [host]
  (reset! client (java.net.Socket. host 8888)))

(defn disconnect []
  (.close @client)
  (reset! client nil))




;; Actual PiGPIO functions
(def pigpio-cmds
  {:setmode [0 "gpio" "mode value" "status"]
   :getmode [1 "gpio" nil "mode or error"]
   :setpullupdown [2 "gpio" "pull up down value" "status"]
   :read [3 "gpio" nil "level or error"]
   :write [4 "user gpio" "level" "status"]
   :pwm [5 "user gpio" "duty cycle" "status"]
   :setpwmrange [6 "user gpio" "range" "real range or error"]
   :setpwmfrequency [7 "user gpio" "frequency" "selected frequency or error"]
   :servo [8 "user gpio" "pulse width" "status"]
   :setwatchdog [9 "user gpio" "timeout" "status"]
   :readbits1 [10 nil nil "levels"]
   :readbits2 [11 nil nil "levels"]
   :writebits1clear [12 "bits" nil "0"]
   :writebits2clear [13 "bits" nil "0"]
   :writebits1set [14 "bits" nil "0"]
   :writebits2set [15 "bits" nil "0"]
   :tick [16 nil nil "tick"]
   :hardwarerevision [17 nil nil "revision"]
   :notifyopen [18 nil nil "handle or error"]
   :notifybegin [19 "handle" "bits" "status"]
   :notifypause [20 "handle" nil "status"]
   :notifyclose [21 "handle" nil "status"]
   :getpwmrange [22 "user gpio" nil "range or error"]
   :getpwmfrequency [23 "user gpio" nil "frequency or error"]
   :getpwmrealrange [24 "user gpio" nil "real range or error"]
   :help [25 nil nil "0"]})

(def error-codes
  {-1	"gpioInitialise failed"  
   -2	"gpio not 0-31"  
   -3	"gpio not 0-53"  
   -4	"mode not 0-7"  
   -5	"level not 0-1"  
   -6	"pud not 0-2"  
   -7	"pulsewidth not 0 or 500-2500"  
   -8	"dutycycle not 0-255"  
   -9	"timer not 0-9"  
   -10	"ms not 10-60000"  
   -11	"timetype not 0-1"  
   -12	"seconds < 0"  
   -13	"micros not 0-999999"  
   -14	"gpioSetTimerFunc failed"  
   -15	"timeout not 0-60000"  
   -16	"DEPRECATED"
   -17	"clock peripheral not 0-1"
   -18	"clock source not 0-1"  
   -19	"clock micros not 1, 2, 4, 5, 8, or 10"  
   -20	"buf millis not 100-10000"  
   -21	"dutycycle not 25-40000"  
   -22	"signum not 0-63"  
   -23	"can't open pathname"  
   -24	"no handle available"  
   -25	"unknown notify handle"  
   -26	"ifFlags > 3"  
   -27	"DMA channel not 0-14"  
   -28	"socket port not 1024-32000"  
   -29	"unrecognized fifo command"})

(defn send-pigpio [command p1 p2]
  (let [[cmd v1 v2 rescode :as m] (get pigpio-cmds command)]
    (cond
     (and v1 (not p1)) (throw (RuntimeException. (str command " arg1 - " v1 " is required")))
     (and v2 (not p2)) (throw (RuntimeException. (str command " arg2 - " v2 " is required")))
     :else
     (do
       (let [out (.getOutputStream @client)]
         (.write
          out
          (byte-array
           (concat
            (write-uint32 cmd)
            (write-uint32 p1)
            (write-uint32 p2)
            (write-uint32 0))))
         (.flush out))
       (let [[_ _ _ res :as r] (repeatedly 4 #(read-uint32 (.getInputStream @client)))
             s (signed res)
             err (if (neg? s) (get error-codes s))]
         (println "Received: " r)
         (if err
           (RuntimeException. (str command " " err))
           res))))))

(defn test []
  (start)
  (connect "127.0.0.1")
  (let [out (.getOutputStream @client)]
    (doseq [x (range 1 5)]
      (write-uint32 out -3))
    (.flush out))
  (disconnect)
  (stop))

(defn test [n x]
  (connect "10.0.0.9")
  (.write (.getOutputStream @client)
          (byte-array
           [(byte 5) (byte 0) (byte 0) (byte 0)
            (byte n) (byte 0) (byte 0) (byte 0)
            (byte x) (byte 0) (byte 0) (byte 0)
            (byte 0) (byte 0) (byte 0) (byte 0)]))
  (.flush (.getOutputStream @client))
  (print (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client))
         (.read (.getInputStream @client)))
  (disconnect))

(defn test2 [n x]
  (connect "10.0.0.9")
  (println (send-pigpio :pwm n x))
  (disconnect))

(defn -main [& args]
  (println "Starting nrepl on 0.0.0.0:4005")
  (try
    (nrepl/start-server :port 4005 :bind "0.0.0.0")
    (catch Exception e (.printStackTrace e))))


(defn send-range [pwm dir sleep & [d]]
  (send-pigpio :write sleep 0)
  (send-pigpio :setpwmrange pwm 5000)
  (send-pigpio :pwm pwm 80)
  (send-pigpio :write dir (or d 0))
  (doseq [x (range 8)]
    (println (* x 200))
    (Thread/sleep 100)
    (send-pigpio :setpwmfrequency pwm (* (inc x) 100)))
  (send-pigpio :setpwmfrequency pwm 1100)
  (Thread/sleep 100)
  (doseq [x (reverse (range 4))]
    (println (* x 200))
    (Thread/sleep 100)
    (send-pigpio :setpwmfrequency pwm (* x 200)))
  (send-pigpio :pwm pwm 0)
  (send-pigpio :write sleep 1))

(defn send-sine [[pwm dir sleep] size]
  (send-pigpio :setpwmrange pwm 255)
  (send-pigpio :pwm pwm 80)
  (send-pigpio :write dir (if (neg? size) 1 0))
  (send-pigpio :write sleep 0)
  (let [ms (Math/abs size)]
    (doseq [x (range ms)]
      (println x " - " (* (Math/sin (* (/ x ms) Math/PI)) 240))
      (send-pigpio :setpwmfrequency pwm (int (* (Math/sin (* (/ x ms) Math/PI)) 240)))
      (Thread/sleep 10)))
  (send-pigpio :pwm pwm 0)
  (send-pigpio :write sleep 1)
  )

(defn csend-sine [size]
  (doseq [x (range 0 100)]
    (println x " - " (* (Math/sin (* (/ x 100) Math/PI )) (* (/ size 200) 800)))
    (Thread/sleep 10)))

(defn csend-list [size]
  (let [time (/ (Math/sqrt size) 5)]
    (for [x (range 0 (* time 106))]
      (* (Math/sin (* (/ x 100) Math/PI )) (* (/ size time) 800 11.3)))))

(defn send-to [xset yset [lx ly] [nx ny] scale]
  (doto (Thread. (partial send-sine xset (* scale (- nx lx)))) .start)
  (doto (Thread. (partial send-sine yset (* scale (- ny ly)))) .start))

(defn start-pwm [pwm dir sleep & [d]]
  (.start
   (Thread.
    #(send-range pwm dir sleep d))))


(defn close-claw []
  (send-pigpio :servo 7 1500))

(defn open-claw []
  (send-pigpio :servo 7 700))

(defn disengage-claw []
  (send-pigpio :servo 7 0))


(defn play-flags []
  (start-pwm 23 22 11 0)
  (start-pwm 10 9 25 1))
