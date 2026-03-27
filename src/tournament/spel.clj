(ns tournament.spel
  (:require [cheshire.core :as json]
            [clojure.java.shell :as sh]
            [tommy-mor.trail :as t]))

;; ---------------------------------------------------------------------------
;; Shell bridge
;; ---------------------------------------------------------------------------

(defn spel! [& args]
  (let [{:keys [out err exit]} (apply sh/sh "spel" (map str args))]
    (when-not (zero? exit)
      (throw (ex-info (str "spel: " err) {:args args})))
    out))

(defn snap-els! []
  (->> (:refs (json/parse-string (spel! "snapshot" "-i" "--json") true))
       (map (fn [[id attrs]] (assoc attrs :id (name id))))
       vec))

;; ---------------------------------------------------------------------------
;; Action navigators — these drive the browser, return fresh snapshot
;; ---------------------------------------------------------------------------

(defn open [url]
  {:name (str "open(" url ")")
   :match (fn [_]
            (spel! "open" url)
            (Thread/sleep 1500)
            (snap-els!))})

(defn fill [text]
  {:name (str "fill(\"" text "\")")
   :match (fn [els]
            (let [ref (:id (first els))]
              (spel! "fill" (str "@" ref) text)
              (Thread/sleep 300)
              (snap-els!)))})

(def click
  {:name "click"
   :match (fn [els]
            (let [ref (:id (first els))]
              (spel! "click" (str "@" ref))
              (Thread/sleep 500)
              (snap-els!)))})

(defn press [key]
  {:name (str "press(" key ")")
   :match (fn [_]
            (spel! "press" key)
            (Thread/sleep 800)
            (snap-els!))})

(defn wait [ms]
  {:name (str "wait(" ms ")")
   :match (fn [els]
            (Thread/sleep ms)
            (snap-els!))})
