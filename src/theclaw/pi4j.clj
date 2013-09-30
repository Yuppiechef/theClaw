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
   [com.pi4j.io.gpi.event
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
  (.provisionDigitalInputPin (get pins pin) name (pinpull pull)))

(defn setup-output [pin name pull]
  (.provisionDigitalOuputPin (get pins pin) name (pinpull pull)))


