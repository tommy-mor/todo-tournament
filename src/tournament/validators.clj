(ns tournament.validators
  (:require [tommy-mor.trail :as t]
            [tournament.spel :as s]))

;; ---------------------------------------------------------------------------
;; Each validator is one path. The path IS the test.
;; Action navigators (open, fill, press, click) drive the browser.
;; Filter navigators (role, name-match, checked) narrow elements.
;; When a filter fails, the trail tells you exactly where and why.
;; ---------------------------------------------------------------------------

(defn v-empty-state [url]
  (t/validate
   [(s/open url) (t/absent (t/role "listitem"))]
   []))

(defn v-has-input [url]
  (t/validate
   [(s/open url) (t/role "textbox")]
   []))

(defn v-has-heading [url]
  (t/validate
   [(s/open url) (t/role "heading")]
   []))

(defn v-add-todo [url]
  (t/validate
   [(s/open url)
    (t/role "textbox") (s/fill "buy milk")
    (s/press "Enter")
    (t/role "listitem")]
   []))

(defn v-add-todo-text [url]
  (t/validate
   [(s/open url)
    (t/role "textbox") (s/fill "buy milk")
    (s/press "Enter")
    (t/name-match #"buy milk")]
   []))

(defn v-add-three [url]
  (t/validate
   [(s/open url)
    (t/role "textbox") (s/fill "buy milk") (s/press "Enter")
    (t/role "textbox") (s/fill "walk dog") (s/press "Enter")
    (t/role "textbox") (s/fill "write code") (s/press "Enter")
    (t/role "listitem") (t/count>= 3)]
   []))

(defn v-complete-todo [url]
  (t/validate
   [(s/open url)
    (t/role "textbox") (s/fill "buy milk") (s/press "Enter")
    (t/role "checkbox") t/unchecked s/click
    (t/role "checkbox") t/checked]
   []))

(defn v-delete-todo [url]
  (t/validate
   [(s/open url)
    (t/role "textbox") (s/fill "buy milk") (s/press "Enter")
    (t/role "button") (t/name-match #"×|delete|remove") s/click
    (t/absent (t/role "listitem"))]
   []))

(defn v-count-display [url]
  (t/validate
   [(s/open url)
    (t/role "textbox") (s/fill "alpha") (s/press "Enter")
    (t/role "textbox") (s/fill "beta") (s/press "Enter")
    (t/name-match #"2.*item")]
   []))

(defn v-filter-buttons [url]
  (t/validate
   [(s/open url)
    (t/role "textbox") (s/fill "x") (s/press "Enter")
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
  (println (str "Testing " url))
  (println (apply str (repeat 50 "─")))
  (doseq [[name validator] all-validators]
    (print (str "  " name "... "))
    (flush)
    (try
      (let [result (validator url)]
        (if (:ok result)
          (println "ok")
          (println (str "FAIL: " (:message result)))))
      (catch Exception e
        (println (str "ERROR: " (.getMessage e))))))
  (println))
