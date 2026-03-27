(ns tournament.validators
  (:require [tommy-mor.trail :as t]
            [com.blockether.spel.core :as spel]
            [com.blockether.spel.page :as page]
            [com.blockether.spel.locator :as loc]
            [com.blockether.spel.snapshot :as snapshot])
  (:import [com.microsoft.playwright.options AriaRole]))

;; ---------------------------------------------------------------------------
;; Browser state — dynamic var, rebound per test via with-browser
;; ---------------------------------------------------------------------------

(def ^:dynamic *page* nil)

(defn- snap-els!
  "Take an ARIA snapshot, return flat list of element maps with :id."
  []
  (page/wait-for-load-state *page* :networkidle)
  (let [snap (snapshot/capture-snapshot *page* {:interactive? true})]
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
            (page/navigate *page* url)
            (snap-els!))})

(defn reset-app [url]
  {:name (str "reset-app(" url ")")
   :match (fn [_]
            (page/navigate *page* (str url "/reset"))
            (page/navigate *page* url)
            (snap-els!))})

(defn fill [text]
  {:name (str "fill(\"" text "\")")
   :match (fn [els]
            (when (empty? els)
              (throw (ex-info "Cannot fill: no elements matched." {})))
            (when (> (count els) 1)
              (throw (ex-info (str "Ambiguous fill: " (count els) " elements matched.")
                              {:count (count els) :elements els})))
            (let [ref (str "@" (:id (first els)))
                  locator (page/get-by-ref *page* ref)]
              (loc/fill locator text)
              els))})

(defn click []
  {:name "click"
   :match (fn [els]
            (when (empty? els)
              (throw (ex-info "Cannot click: no elements matched." {})))
            (when (> (count els) 1)
              (throw (ex-info (str "Ambiguous click: " (count els) " elements matched.")
                              {:count (count els) :elements els})))
            (let [ref (str "@" (:id (first els)))
                  locator (page/get-by-ref *page* ref)]
              (loc/click locator)
              (Thread/sleep 150)
              (snap-els!)))})

(defn press [key]
  {:name (str "press(" key ")")
   :match (fn [els]
            (when (empty? els)
              (throw (ex-info "Cannot press: no elements matched." {})))
            (when (> (count els) 1)
              (throw (ex-info (str "Ambiguous press: " (count els) " elements matched.")
                              {:count (count els) :elements els})))
            (let [ref (str "@" (:id (first els)))
                  locator (page/get-by-ref *page* ref)]
              (loc/press locator key)
              (Thread/sleep 150)
              (snap-els!)))})

(def focused
  {:name "focused"
   :match (fn [els]
            (when (empty? els)
              (throw (ex-info "Cannot check focus: no elements matched." {})))
            (when (> (count els) 1)
              (throw (ex-info (str "Ambiguous focused: " (count els) " elements matched.") {})))
            (let [ref     (str "@" (:id (first els)))
                  locator (page/get-by-ref *page* ref)
                  active? (.evaluate locator "el => el === document.activeElement" nil)]
              (when-not active?
                (throw (ex-info (str "Element is not focused: " (:name (first els))) {})))
              els))})

;; ---------------------------------------------------------------------------
;; Lifecycle
;; ---------------------------------------------------------------------------

(defmacro with-browser [& body]
  `(spel/with-testing-page [pg#]
     (binding [*page* pg#]
       ~@body)))

;; ---------------------------------------------------------------------------
;; Failure reporting
;; ---------------------------------------------------------------------------

(defn report-failure [test-name result]
  (binding [*out* *err*]
    (println (str "\n=== FAIL: " test-name " ==="))
    (println (str "error: " (:message result)))
    (when-let [snap (:snapshot result)]
      (println "aria-snapshot-at-failure:")
      (println snap))
    (println (str "fix: make the '" test-name "' validator pass by editing the app code.\n"))))

;; ---------------------------------------------------------------------------
;; Validators — each is one trail path
;; ---------------------------------------------------------------------------

(defn v-empty-state [url]
  (t/validate
   [(reset-app url) (open url) (t/absent (t/role "listitem"))]
   []))

(defn v-has-input [url]
  (t/validate
   [(reset-app url) (open url) (t/role "textbox")]
   []))

(defn v-has-heading [url]
  (t/validate
   [(reset-app url) (open url) (t/role "heading")]
   []))

(defn v-add-todo [url]
  (t/validate
   [(reset-app url)
    (open url)
    (t/role "textbox") (fill "buy milk")
    (press "Enter")
    (t/role "listitem")]
   []))

(defn v-add-todo-text [url]
  (t/validate
   [(reset-app url)
    (open url)
    (t/role "textbox") (fill "buy milk")
    (press "Enter")
    (t/name-match #"buy milk")]
   []))

(defn v-add-three [url]
  (t/validate
   [(reset-app url)
    (open url)
    (t/role "textbox") (fill "buy milk") (press "Enter")
    (t/role "textbox") (fill "walk dog") (press "Enter")
    (t/role "textbox") (fill "write code") (press "Enter")
    (t/role "listitem") (t/count>= 3)]
   []))

(defn v-complete-todo [url]
  (t/validate
   [(reset-app url)
    (open url)
    (t/role "textbox") (fill "buy milk") (press "Enter")
    (t/role "checkbox") t/unchecked (click)
    (t/role "checkbox") t/checked]
   []))

(defn v-delete-todo [url]
  (t/validate
   [(reset-app url)
    (open url)
    (t/role "textbox") (fill "buy milk") (press "Enter")
    (t/role "button") (t/name-match #"×|delete|remove") (click)
    (t/absent (t/role "listitem"))]
   []))

(defn v-count-display [url]
  (t/validate
   [(reset-app url)
    (open url)
    (t/role "textbox") (fill "alpha") (press "Enter")
    (t/role "textbox") (fill "beta") (press "Enter")
    (t/name-match #"2.*item")]
   []))

(defn v-filter-buttons [url]
  (t/validate
   [(reset-app url)
    (open url)
    (t/role "textbox") (fill "x") (press "Enter")
    (t/name-match #"(?i)^all$")]
   []))

(defn v-input-refocused [url]
  (t/validate
   [(reset-app url)
    (open url)
    (t/role "textbox") (fill "buy milk") (press "Enter")
    (t/role "textbox") focused]
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
   "count-display"   v-count-display
   "filter-buttons"  v-filter-buttons
   "input-refocused" v-input-refocused})

(defn run-all! [url]
  (with-browser
    (println (str "Testing " url))
    (println (apply str (repeat 50 "─")))
    (let [fails (atom 0)]
      (doseq [[vname validator] all-validators]
        (print (str "  " vname "... "))
        (flush)
        (try
          (let [result (validator url)]
            (if (:ok result)
              (println "✓")
              (do
                (println (str "✗  " (:message result)))
                (swap! fails inc)
                (report-failure vname result))))
          (catch Exception e
            (println (str "ERROR: " (.getMessage e)))
            (swap! fails inc))))
      (println)
      @fails)))
