(ns theclaw.pi4j
  (:import
   [com.pi4j.io.gpio
    GpioController
    GpioFactory
    GpioPin
    GpioPinDigitalInput
    GpioPinDigitalOutput
    PinDirection
    PinMode
    PinPullResistance
    PinState
    RaspiPin]
   [com.pi4j.io.gpio.trigger
    GpioCallbackTrigger
    GpioPulseStateTrigger
    GpioSetStateTrigger
    GpioSyncStateTrigger]
   #_[com.pi4j.io.gpi.event
    GpioPinListener
    GpioPinDigitalStateChangeEvent
    GpioPinEvent
    GpioPinListenerDigital
    PinEventType]))

(def gpio (atom nil))

(defmacro build-pins [n]
  (let [pins (range n)
        consts (for [pin pins] (symbol "RaspiPin" (format "GPIO_%02d" pin)))
        body (zipmap pins consts)]
    body))

(def pins (build-pins 16))

(def pinpull
  {:up PinPullResistance/PULL_UP
   :down PinPullResistance/PULL_DOWN})

(defn start-gpio []
  (reset! gpio (GpioFactory/getInstance)))

(defn setup-input [pin name pull]
  (.provisionDigitalInputPin @gpio (get pins pin) name (pinpull pull)))

(defn setup-output [pin name pull]
  (.provisionDigitalOutputPin @gpio (get pins pin) name (pinpull pull)))

(defn setup-output [pin]
  (.provisionDigitalOutputPin @gpio (get pins pin)))

(defn pulse [pin sleep _]
  (.high pin)
  (.sleep java.util.concurrent.TimeUnit/NANOSECONDS 1)
  (.low pin)
  (.sleep java.util.concurrent.TimeUnit/NANOSECONDS (* sleep 200)))

(defn move [[pwm dir sleep] amount direction]
  (.low sleep)
  (if (= 1 direction)
    (.high dir) (.low dir))
  (doseq [_ (range 10)]
    (pulse pwm 10000 true))
  (doseq [_ (range 20)]
    (pulse pwm 8000 true))
  (doseq [_ (range 30)]
    (pulse pwm 4000 true))
  (doseq [_ (range amount)]
    (pulse pwm 1 true))
  (doseq [_ (range 50)]
    (pulse pwm 1000 true))
  (.high sleep))



(def pwm (.provisionDigitalOutputPin @gpio RaspiPin/GPIO_04))
(def dir (.provisionDigitalOutputPin @gpio RaspiPin/GPIO_03))
(def sleep (.provisionDigitalOutputPin @gpio RaspiPin/GPIO_14))


(def pwm2 (.provisionDigitalOutputPin @gpio RaspiPin/GPIO_12))
(def dir2 (.provisionDigitalOutputPin @gpio RaspiPin/GPIO_13))
(def sleep2 (.provisionDigitalOutputPin @gpio RaspiPin/GPIO_06))

(defn go [x]
  (.start (Thread. #(move [pwm2 dir2 sleep2] x 1)))
  (.start (Thread. #(move [pwm dir sleep] x 0))))
