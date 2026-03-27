(ns tournament.validators
  (:require [tommy-mor.trail :as t]
            [com.blockether.spel.core :as spel]
            [com.blockether.spel.page :as page]
            [com.blockether.spel.snapshot :as snapshot]))

;; ---------------------------------------------------------------------------
;; Browser state — held in an atom, closed over by action navigators
;; ---------------------------------------------------------------------------

(def ^:private *page* (atom nil))
(def ^:private *browser* (atom nil))

(defn- snap-els!
  "Take an ARIA snapshot of the current page, return flat list of element maps."
  []
  (let [snap (snapshot/snapshot @*page* {:interactive true})]
    (->> (:refs snap)
         (map (fn [[id attrs]] (assoc attrs :id (name id))))
         vec)))

;; ---------------------------------------------------------------------------
;; Action navigators — trail steps that drive the browser
;; Each returns a fresh snapshot as the new element list
;; ---------------------------------------------------------------------------

(defn open [url]
  {:name (str "open(" url ")")
   :match (fn [_]
            (page/navigate @*page* url)
            (snap-els!))})

(defn fill [text]
  {:name (str "fill(\"" text "\")")
   :match (fn [els]
            (let [ref (str "@" (:id (first els)))]
              (page/fill @*page* ref text)
              (snap-els!)))})

(def click
  {:name "click"
   :match (fn [els]
            (let [ref (str "@" (:id (first els)))]
              (page/click @*page* ref)
              (snap-els!)))})

(defn press [key]
  {:name (str "press(" key ")")
   :match (fn [_]
            (page/press @*page* "body" key)
            (snap-els!))})

;; ---------------------------------------------------------------------------
;; Lifecycle — wrap validator runs in a browser session
;; ---------------------------------------------------------------------------

(defmacro with-browser [& body]
  `(spel/with-testing-page [pg#]
     (reset! *page* pg#)
     (try
       ~@body
       (finally
         (reset! *page* nil)))))

;; ---------------------------------------------------------------------------
;; Validators — each is one trail path
;; ---------------------------------------------------------------------------

(defn v-empty-state [url]
  (t/validate
   [(open url) (t/absent (t/role "listitem"))]
   []))

(defn v-has-input [url]
  (t/validate
   [(open url) (t/role "textbox")]
   []))

(defn v-has-heading [url]
  (t/validate
   [(open url) (t/role "heading")]
   []))

(defn v-add-todo [url]
  (t/validate
   [(open url)
    (t/role "textbox") (fill "buy milk")
    (press "Enter")
    (t/role "listitem")]
   []))

(defn v-add-todo-text [url]
  (t/validate
   [(open url)
    (t/role "textbox") (fill "buy milk")
    (press "Enter")
    (t/name-match #"buy milk")]
   []))

(defn v-add-three [url]
  (t/validate
   [(open url)
    (t/role "textbox") (fill "buy milk") (press "Enter")
    (t/role "textbox") (fill "walk dog") (press "Enter")
    (t/role "textbox") (fill "write code") (press "Enter")
    (t/role "listitem") (t/count>= 3)]
   []))

(defn v-complete-todo [url]
  (t/validate
   [(open url)
    (t/role "textbox") (fill "buy milk") (press "Enter")
    (t/role "checkbox") t/unchecked click
    (t/role "checkbox") t/checked]
   []))

(defn v-delete-todo [url]
  (t/validate
   [(open url)
    (t/role "textbox") (fill "buy milk") (press "Enter")
    (t/role "button") (t/name-match #"×|delete|remove") click
    (t/absent (t/role "listitem"))]
   []))

(defn v-count-display [url]
  (t/validate
   [(open url)
    (t/role "textbox") (fill "alpha") (press "Enter")
    (t/role "textbox") (fill "beta") (press "Enter")
    (t/name-match #"2.*item")]
   []))

(defn v-filter-buttons [url]
  (t/validate
   [(open url)
    (t/role "textbox") (fill "x") (press "Enter")
    (t/name-match #"(?i)^all$")]
   []))

;; ---------------------------------------------------------------------------
;; Runner
;; ---------------------------------------------------------------------------

(def all-validators
  {"empty-state"    v-empty-state
   "has-input"      v-has-input
   "has-heading"    v-has-heading
   "add-todo"       v-add-todo
   "add-todo-text"  v-add-todo-text
   "add-three"      v-add-three
   "complete-todo"  v-complete-todo
   "delete-todo"    v-delete-todo
   "count-display"  v-count-display
   "filter-buttons" v-filter-buttons})

(defn run-all! [url]
  (with-browser
    (println (str "Testing " url))
    (println (apply str (repeat 50 "─")))
    (doseq [[vname validator] all-validators]
      (print (str "  " vname "... "))
      (flush)
      (try
        (let [result (validator url)]
          (if (:ok result)
            (println "ok")
            (println (str "FAIL: " (:message result)))))
        (catch Exception e
          (println (str "ERROR: " (.getMessage e))))))
    (println)))
