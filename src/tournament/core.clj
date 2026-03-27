(ns tournament.core
  (:require [hyper.core :as h]
            [tournament.todo :as todo]))

(def app
  (h/start!
   (h/create-handler #'todo/routes
                     :static-resources "public"
                     :head [[:link {:rel "stylesheet" :href "/tournament.css"}]])
   {:port 4000}))

(defn stop! [] (h/stop! app))

(comment
  (stop!))
