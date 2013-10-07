(ns theclaw.util)

(defn env [prop]
  (.get (System/getenv) (name prop)))


(def logformat (delay (java.text.SimpleDateFormat. (env "LOG_DATE_FMT"))))

(defmacro log [level & args]
  `(println (.format @logformat (java.util.Date.))
            (str "  " ~level "  " ~(:ns (meta &form)) ":" ~(:line (meta &form)) "] " ~@(interpose " " args))))

(defmacro info [& args]
  `(log "INFO" ~@args))

(defmacro warn [& args]
  `(log "WARN" ~@args))

(defmacro error [& args]
  `(log "ERROR" ~@args))
