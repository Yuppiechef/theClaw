(ns theclaw.i2c
  (:import
   [com.pi4j.io.i2c I2CBus I2CDevice I2CFactory]
   [java.nio ByteBuffer]))

(def bus (atom nil))
(def d (atom nil))

(defn device [id]
  (.getDevice @bus id))

(defn setup-bus []
  (reset! bus (I2CFactory/getInstance I2CBus/BUS_1))
  (reset! d (device 0x04)))

(defn write-string [device string]
  (let [s (str string "\n")]
    (.write device (.getBytes s) 0 (count (.getBytes s)))))

(def commands
  {:zero 0
   :calibrate 1
   :move 2
   :grab 3
   :speed 4
   :claw 5})

(defn write-command [device command-key & [arg1 arg2 arg3 arg4]]
  (let [b (doto (ByteBuffer/allocate 6)
            (.putShort (get commands command-key))
            (.putShort (short (or arg1 0)))
            (.putShort (short (or arg2 0))))
        a (.array b)]
    (.write device a 0 (count a))))

#_ (setup-bus)
#_ (write-command :calibrate)
#_ (write-command :speed 600 600)
#_ (write-command :move 1000 1000)



