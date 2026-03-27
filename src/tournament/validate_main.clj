(ns tournament.validate-main
  (:require [tournament.validators :as v]))

(defn -main [& [url]]
  (let [fails (v/run-all! (or url "http://localhost:4000"))]
    (System/exit fails)))
