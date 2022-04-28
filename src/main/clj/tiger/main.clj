(ns tiger.main
  (:require [tiger.server :as server])
  (:import [tiger WebApp])
  (:gen-class))

(defn -main
  []
  (server/start {:port                 8080
                 :configurator         #(WebApp/config %)
                 :send-server-version? false})
  (.addShutdownHook (Runtime/getRuntime) (Thread. server/stop)))
