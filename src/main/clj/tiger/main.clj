(ns tiger.main
  (:import [tiger WebApp])
  (:gen-class))

(defn -main
  []
  (WebApp/main (make-array String 0)))
