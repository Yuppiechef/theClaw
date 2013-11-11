(ns theclaw.uart
  (:import [com.pi4j.io.serial
            Serial SerialDataEvent SerialDataListener
            SerialFactory SerialPortException]))


(def serial (SerialFactory/createInstance))

(defn setup-serial [listener-fn]
  (.addListener serial
                (into-array SerialDataListener
                            [(proxy [SerialDataListener] []
                               (dataReceived
                                 [e] (listener-fn (.getData e))))]))
  (.open serial Serial/DEFAULT_COM_PORT 9600))
