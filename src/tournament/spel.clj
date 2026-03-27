(ns tournament.spel
  (:require [cheshire.core :as json]
            [clojure.java.shell :as sh]
            [tommy-mor.trail :as t]))

(defn spel!
  "Run a spel command. Returns stdout string."
  [& args]
  (let [{:keys [out err exit]} (apply sh/sh "spel" (map str args))]
    (when-not (zero? exit)
      (throw (ex-info (str "spel error: " err) {:args args :exit exit})))
    out))

(defn open! [url]
  (spel! "open" url)
  (Thread/sleep 1000)) ;; let page settle

(defn snapshot!
  "Take an interactive spel snapshot, return parsed JSON."
  []
  (json/parse-string (spel! "snapshot" "-i" "--json") true))

(defn refs
  "Extract refs from snapshot as flat list of maps with :id added."
  [snap]
  (->> (:refs snap)
       (map (fn [[id attrs]] (assoc attrs :id (name id))))
       vec))

(defn snap-els!
  "Snapshot → flat list of elements for trail."
  []
  (refs (snapshot!)))

;; -- Actions --

(defn click! [ref] (spel! "click" (str "@" ref)))
(defn fill! [ref text] (spel! "fill" (str "@" ref) text))
(defn type! [ref text] (spel! "type" (str "@" ref) text))
(defn press! [key] (spel! "press" key))
(defn check! [ref] (spel! "check" (str "@" ref)))
(defn uncheck! [ref] (spel! "uncheck" (str "@" ref)))

;; -- Find element by trail path --

(defn find-ref
  "Find the first element matching a trail path, return its :id for use with spel commands."
  [path]
  (let [els (snap-els!)
        result (t/validate path els)]
    (if (:ok result)
      (:id (first (:matched result)))
      (throw (ex-info (str "Could not find element: " (:message result))
                      result)))))

;; -- Validate --

(defn validate!
  "Snapshot the browser and validate specs. Returns failures."
  [specs]
  (t/validate-all specs (snap-els!)))

(defn report! [specs]
  (let [els (snap-els!)
        failures (t/validate-all specs els)]
    (if (seq failures)
      (do
        (println (str (count failures) " of " (count specs) " failed:"))
        (doseq [{:keys [fail message]} failures]
          (println (str "  FAIL " message))))
      (println (str "All " (count specs) " pass.")))
    failures))
