(ns tiger.main
  (:require [tiger.server :as server])
  (:import [tiger JettyConfigurator])
  (:gen-class))

(defn -main
  []
  (server/start {:port                 8080
                 :configurator         #(JettyConfigurator/config %)
                 :send-server-version? false})
  (.addShutdownHook (Runtime/getRuntime) (Thread. server/stop)))
