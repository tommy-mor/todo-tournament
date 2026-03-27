(ns tournament.validators
  (:require [tommy-mor.trail :as t]))

;; ---------------------------------------------------------------------------
;; TODO-specific navigators (built on trail primitives)
;; ---------------------------------------------------------------------------

(def todos       (t/role "listitem"))
(def input       (t/role "textbox"))
(def checkboxes  (t/role "checkbox"))
(def buttons     (t/role "button"))
(def heading     (t/role "heading"))

(defn like [pat] (t/name-match pat))
(def count-display (like #"\d+.*item"))

;; ---------------------------------------------------------------------------
;; Specs: just paths. The trail IS the name.
;; Use (t/label ...) only when intent isn't obvious from navigators alone.
;; ---------------------------------------------------------------------------

(def empty-state-specs
  [[(t/absent todos)]
   [input]
   [heading]])

(def after-add-one-specs
  [[todos (t/count= 1)]
   [todos (t/name-match #"buy milk")]
   [input]
   [count-display]])

(def after-add-three-specs
  [[todos (t/count= 3)]
   [(t/name= "buy milk")]
   [(t/name= "walk dog")]
   [(t/name= "write code")]])

(def after-complete-specs
  [[checkboxes t/checked]
   [checkboxes t/unchecked]
   [count-display]])

(def after-delete-specs
  [[todos (t/count= 2)]])

(def filter-specs
  [[(like #"(?i)^all$")]
   [(like #"(?i)^active$")]
   [(like #"(?i)^completed$")]])

(def clear-completed-specs
  [[(like #"(?i)clear completed")]])

(def xss-specs
  [[(like #"<script>")]])

;; ---------------------------------------------------------------------------
;; Runner
;; ---------------------------------------------------------------------------

(defn run-specs [specs elements]
  (t/validate-all specs elements))

(defn report [specs elements]
  (let [failures (run-specs specs elements)]
    (if (seq failures)
      (do
        (println (str (count failures) " of " (count specs) " failed:"))
        (doseq [{:keys [fail message]} failures]
          (println (str "  FAIL " (clojure.string/join " > " fail) ": " message))))
      (println (str "All " (count specs) " specs pass.")))))
