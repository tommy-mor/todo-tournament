(ns evaleval.serve
  (:require [evaleval.core :as e]
            [evaleval.todo :as todo]))

(defn -main [& [port]]
  (e/start! todo/app (Integer/parseInt (or port "4002")))
  @(promise))
