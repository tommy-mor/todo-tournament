(ns tournament.todo
  (:require [hyper.core :as h])
  (:import [java.util UUID]))

;; ---------------------------------------------------------------------------
;; State
;; ---------------------------------------------------------------------------

(defn todos* []  (h/tab-cursor :todos []))
(defn filter* [] (h/tab-cursor :filter :all))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn active-todos [todos] (remove :done? todos))
(defn completed-todos [todos] (filter :done? todos))

(defn visible-todos [todos filt]
  (case filt
    :all       todos
    :active    (active-todos todos)
    :completed (completed-todos todos)))

(defn add-todo [todos text]
  (if (clojure.string/blank? text)
    todos
    (conj todos {:id (str (UUID/randomUUID))
                 :text (clojure.string/trim text)
                 :done? false})))

(defn toggle-todo [todos id]
  (mapv (fn [t] (if (= id (:id t)) (update t :done? not) t)) todos))

(defn delete-todo [todos id]
  (vec (remove #(= id (:id %)) todos)))

(defn clear-completed [todos]
  (vec (active-todos todos)))

(defn toggle-all [todos]
  (let [all-done? (every? :done? todos)]
    (mapv #(assoc % :done? (not all-done?)) todos)))

;; ---------------------------------------------------------------------------
;; Components
;; ---------------------------------------------------------------------------

(defn todo-item [todo]
  [:li {:class (str "todo-item" (when (:done? todo) " todo-item--done"))}
   [:input.todo-item__toggle {:type "checkbox"
                              :checked (:done? todo)
                              :data-on:change (h/action (swap! (todos*) toggle-todo (:id todo)))}]
   [:span {:class (when (:done? todo) "todo-item__text--done")}
    (:text todo)]
   [:button.todo-item__delete
    {:data-on:click (h/action (swap! (todos*) delete-todo (:id todo)))}
    "×"]])

(defn todo-list []
  (let [todos @(todos*)
        filt @(filter*)
        visible (visible-todos todos filt)]
    [:div
     (let [input* (h/tab-cursor :input-text "")]
       [:form.todo-form
        {:data-on:submit__prevent
         (h/action
          (let [text @(h/tab-cursor :input-text "")]
            (swap! (todos*) add-todo text)
            (reset! (h/tab-cursor :input-text) "")))}
        [:input.todo-new-input {:type "text"
                                :name "todo"
                                :placeholder "What needs to be done?"
                                :autofocus true
                                :value @input*
                                :data-on:input (h/action (reset! (h/tab-cursor :input-text) $value))}]])

     (when (seq todos)
       [:div.todo-main
        [:label.todo-toggle-all-label
         [:input.todo-toggle-all {:type "checkbox"
                                  :checked (every? :done? todos)
                                  :data-on:change (h/action (swap! (todos*) toggle-all))}]
         " Toggle all"]
        [:ul.todo-list
         (map todo-item visible)]])

     (when (seq todos)
       [:div.todo-footer
        [:span.todo-footer__count (str (count (active-todos todos)) " items left")]
        [:div.todo-filters
         (for [[label fval] [["All" :all] ["Active" :active] ["Completed" :completed]]]
           [:button
            {:class (str "todo-filter__btn"
                         (when (= filt fval) " todo-filter__btn--active"))
             :data-on:click (h/action (reset! (filter*) fval))}
            label])]
        (when (seq (completed-todos todos))
          [:button.todo-clear-completed
           {:data-on:click (h/action (swap! (todos*) clear-completed))}
           "Clear completed"])])]))

;; ---------------------------------------------------------------------------
;; Page
;; ---------------------------------------------------------------------------

(defn page [_]
  [:div.todo-app
   [:h1.todo-app__title "todos"]
   (todo-list)])

;; ---------------------------------------------------------------------------
;; Routes
;; ---------------------------------------------------------------------------

(def routes
  [["/" {:name  :home
         :title "TodoMVC — Hyper"
         :get   #'page}]])
