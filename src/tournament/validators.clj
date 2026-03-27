(ns tournament.validators
  (:require [tommy-mor.trail :as t]
            [tournament.spel :as s]))

;; ---------------------------------------------------------------------------
;; Validators are sequential scripts. Open, act, assert. Like Rama tests.
;; Each one is (defn v-name [url] ...) that returns failures or nil.
;; ---------------------------------------------------------------------------

(defn v-empty-state [url]
  (s/open! url)
  (let [els (s/snap-els!)]
    (t/validate-all
     [[(t/role "heading")]
      [(t/role "textbox")]
      [(t/absent (t/role "listitem"))]]
     els)))

(defn v-add-todo [url]
  (s/open! url)
  (let [input (s/find-ref [(t/role "textbox")])]
    (s/fill! input "buy milk")
    (s/press! "Enter")
    (Thread/sleep 1000)
    (let [els (s/snap-els!)]
      (t/validate-all
       [[(t/role "listitem")]
        [(t/name-match #"buy milk")]
        [(t/role "textbox")]]
       els))))

(defn v-add-three [url]
  (s/open! url)
  (doseq [text ["buy milk" "walk dog" "write code"]]
    (let [input (s/find-ref [(t/role "textbox")])]
      (s/fill! input text)
      (s/press! "Enter")
      (Thread/sleep 500)))
  (let [els (s/snap-els!)]
    (t/validate-all
     [[(t/role "listitem") (t/count>= 3)]
      [(t/name-match #"buy milk")]
      [(t/name-match #"walk dog")]
      [(t/name-match #"write code")]]
     els)))

(defn v-complete-todo [url]
  (s/open! url)
  (let [input (s/find-ref [(t/role "textbox")])]
    (s/fill! input "buy milk")
    (s/press! "Enter")
    (Thread/sleep 1000))
  (let [cb (s/find-ref [(t/role "checkbox") t/unchecked])]
    (s/click! cb)
    (Thread/sleep 500))
  (let [els (s/snap-els!)]
    (t/validate-all
     [[(t/role "checkbox") t/checked]]
     els)))

(defn v-delete-todo [url]
  (s/open! url)
  (let [input (s/find-ref [(t/role "textbox")])]
    (s/fill! input "buy milk")
    (s/press! "Enter")
    (Thread/sleep 1000))
  (let [del (s/find-ref [(t/role "button") (t/name-match #"×|delete|remove")])]
    (s/click! del)
    (Thread/sleep 500))
  (let [els (s/snap-els!)]
    (t/validate-all
     [[(t/absent (t/role "listitem"))]]
     els)))

(defn v-count-display [url]
  (s/open! url)
  (doseq [text ["alpha" "beta"]]
    (let [input (s/find-ref [(t/role "textbox")])]
      (s/fill! input text)
      (s/press! "Enter")
      (Thread/sleep 500)))
  (let [els (s/snap-els!)]
    (t/validate-all
     [[(t/name-match #"2.*item")]]
     els)))

(defn v-filter-buttons [url]
  (s/open! url)
  (let [input (s/find-ref [(t/role "textbox")])]
    (s/fill! input "something")
    (s/press! "Enter")
    (Thread/sleep 1000))
  (let [els (s/snap-els!)]
    (t/validate-all
     [[(t/name-match #"(?i)^all$")]
      [(t/name-match #"(?i)^active$")]
      [(t/name-match #"(?i)^completed$")]]
     els)))

;; ---------------------------------------------------------------------------
;; Runner
;; ---------------------------------------------------------------------------

(def all-validators
  [["empty-state"    v-empty-state]
   ["add-todo"       v-add-todo]
   ["add-three"      v-add-three]
   ["complete-todo"  v-complete-todo]
   ["delete-todo"    v-delete-todo]
   ["count-display"  v-count-display]
   ["filter-buttons" v-filter-buttons]])

(defn run-all! [url]
  (println (str "Testing " url))
  (println (apply str (repeat 50 "─")))
  (doseq [[name validator] all-validators]
    (print (str "  " name "... "))
    (flush)
    (try
      (let [failures (validator url)]
        (if (seq failures)
          (do
            (println "FAIL")
            (doseq [{:keys [message]} failures]
              (println (str "    " message))))
          (println "ok")))
      (catch Exception e
        (println (str "ERROR: " (.getMessage e))))))
  (println))
