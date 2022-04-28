(ns tiger.server
  (:require
    [ring.adapter.jetty :refer [run-jetty]]
    [ring.util.response :refer [redirect response]]
    [reitit.ring :as reitit])
  (:import
    [org.eclipse.jetty.server Server]))

(defonce server (atom nil))

(defn index-handler [_]
  (redirect "/dashboard" :found))

(defn oi-handler [_]
  (response "Oi!"))

(def routes
  [
   ["/" {:get index-handler}]
   ["/oi" {:get oi-handler}]])

(def app-handler
  (reitit/ring-handler
    (reitit/router routes)
    (reitit/routes
      (reitit/create-resource-handler {:root "webapp" :path "/"})
      (reitit/create-default-handler))))

(defn start
  [options]
  (reset! server (run-jetty app-handler options)))

(defn stop
  []
  (when-let [^Server jetty @server]
    (.stop jetty)
    (reset! server nil))
  (shutdown-agents))
