(ns theclaw.srv
  (:require
   [ring.server.standalone :as s]
   [ring.middleware.params :as params]
   [ring.middleware.resource :as res]
   [ring.middleware.file-info :as finfo]
   [ring.middleware.content-type :as type]
   [ring.util.response :as r]
   [compojure.core :as c]
   [compojure.handler :as hnd]
   [hiccup.core :as h]
   [hiccup.page :as hp]
   [theclaw.core :as claw]))

(defn page [& content]
  (hp/html5
    [:head
     [:title "Bootstrapped Example"]
     [:link {:rel "stylesheet" :href "//netdna.bootstrapcdn.com/bootstrap/3.0.0/css/bootstrap.min.css"}]
     [:link {:rel "stylesheet" :href "//netdna.bootstrapcdn.com/bootstrap/3.0.0/css/bootstrap-theme.min.css"}]
     [:link {:rel "stylesheet" :href "/css/styles.css" }]
     [:script {:src "http://code.jquery.com/jquery-1.10.1.min.js"}]
     [:script {:src "//netdna.bootstrapcdn.com/bootstrap/3.0.0/js/bootstrap.min.js"}]
     [:script {:src "/js/general.js"}]]
    [:body
     [:div.navbar.navbar-inverse.navbar-fixed-top
      [:div.container
       [:div.navbar-header
        [:button.navbar-toggle {:type "button" :data-toggle "collapse" :data-target ".navbar-collapse"}
         [:span.icon-bar] [:span.icon-bar] [:span.icon-bar]]
        [:a.navbar-brand {:href "#"} "The CLAW."]]
       [:div.collapse.navbar-collapse
        [:ul.nav.navbar-nav
         [:li.active [:a {:href "#"} "Home"]]
         [:li [:a {:href "#about"} "About"]]
         [:li [:a {:href "#contact"} "Contact"]]]]]]
     [:div.container
      [:div.starter-template
       [:br] [:br] [:br] 
       [:h1 "The CLAW."]
       [:p.lead "Prepare.. To be amazed."]
       content]
      ]]))

(defn slow-tab []
  (page "Sloow."))


(defn index-page [ & [msg]]
  (page
   (if msg
     [:div.alert.alert-success msg])
   [:a.btn.btn-lg.btn-warning {:type "button" :href "/calibrate"} "Calibrate"]
   [:div#xy
    [:div#dest.glyphicon.glyphicon-screenshot]]
   [:form {:action "/" :method "POST"}
    [:div.input-group.input-group-lg
     [:span.input-group-addon "X"]
     [:input#xcoord.form-control.input-lg {:type "text" :placeholder "X-coordinate" :name "x"}]]
    [:div.input-group.input-group-lg
     [:span.input-group-addon "Y"]
     [:input#ycoord.form-control.input-lg {:type "text" :placeholder "Y-coordinate" :name "y"}]]
    [:input.btn.btn-lg.btn-success {:type "submit" :value "Go!"}]
    ]))

(defn run [params]
  (index-page (str "Moved to " (:x params) ", " (:y params))))

(def pos (atom nil))
(defn json-run [{:keys [x y]}]
  (let [[x y :as p] [(Integer/parseInt x) (Integer/parseInt y)]]
    (if @pos
      (claw/send-to [23 22 11] [10 9 25] @pos p 1))
    (reset! pos p)
    (str "{\"x\": " x ", \"y\": " y ", \"msg\":\"Success!\"}")))

(defn calibrate []
  (page "This ostensibly calibrates The CLAW."))

(c/defroutes routes
  (c/GET "/" [] (index-page))
  (c/POST "/" {params :params} (run params))
  (c/GET "/calibrate" [] (calibrate))
  (c/POST "/post" {params :params} (slow-tab))
  (c/GET "/post" {params :params} (json-run params)))

(def handler
  (->
   #'routes
   hnd/site
   params/wrap-params
   (res/wrap-resource "public")
   type/wrap-content-type
   finfo/wrap-file-info
   ))

(defonce server (atom nil))

(defn start []
  (claw/connect "10.0.0.7")
  (reset! server (s/serve #'handler {:join? false :open-browser? false})))

(defn stop []
  (swap! server #(.stop %))
  (claw/disconnect))
