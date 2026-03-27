(ns tournament.evaleval-todo
  (:require [tournament.evaleval :as e]
            [clojure.string :as str]
            [ring.middleware.params :as params]
            [ring.middleware.resource :as resource]))

;; ---------------------------------------------------------------------------
;; State (global, not per-tab)
;; ---------------------------------------------------------------------------

(def todos (atom []))
(def filt  (atom :all))
(defn- rand-id [] (subs (str (java.util.UUID/randomUUID)) 0 8))

;; ---------------------------------------------------------------------------
;; View helpers
;; ---------------------------------------------------------------------------

(defn- visible []
  (case @filt
    :active    (remove :done @todos)
    :completed (filter :done @todos)
    @todos))

;; ---------------------------------------------------------------------------
;; Components
;; ---------------------------------------------------------------------------

(defn add-form []
  [:form {:id "add-form" :action "/" :method "post"}
   (e/snippet-inputs "(tournament.evaleval-todo/add $new-todo)")
   [:input.todo-new-input {:type "text" :name "new-todo" :placeholder "What needs to be done?" :autocomplete "off"}]
   [:button {:type "submit" :style "display:none"} "Add"]])

(defn todo-item [t]
  [:li.todo-item {:id (str "todo-" (:id t)) :class (when (:done t) "todo-item todo-item--done")}
   [:form {:action "/" :method "post" :style "display:contents"}
    (e/snippet-inputs (str "(tournament.evaleval-todo/toggle \"" (:id t) "\")"))
    [:input.todo-item__toggle {:type "checkbox" :checked (when (:done t) true)}]]
   [:span {:class (when (:done t) "todo-item__text--done")} (:text t)]
   [:form {:action "/" :method "post" :style "display:contents"}
    (e/snippet-inputs (str "(tournament.evaleval-todo/delete-todo \"" (:id t) "\")"))
    [:button.todo-item__delete {:type "submit"} "×"]]])

(defn todo-list []
  [:ul.todo-list {:id "todo-list"}
   (map todo-item (visible))])

(defn count-display []
  [:span.todo-footer__count {:id "count"} (str (count (remove :done @todos)) " items left")])

(defn filter-buttons []
  [:div.todo-filters {:id "filters"}
   (for [[label f] [["All" "all"] ["Active" "active"] ["Completed" "completed"]]]
     [:form {:action "/" :method "post" :style "display:contents"}
      (e/snippet-inputs (str "(tournament.evaleval-todo/set-filter \"" f "\")"))
      [:button.todo-filter__btn {:type "submit"} label]])])

(defn footer []
  [:footer.todo-footer {:id "footer"}
   (when (seq @todos)
     (list (count-display) (filter-buttons)))])

(defn page []
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:title "TodoMVC — evaleval"]
    [:link {:rel "stylesheet" :href "/tournament.css"}]
    [:script {:src "/evaleval.js" :defer true}]]
   [:body
    [:div.todo-app
     [:h1.todo-app__title "todos"]
     (add-form)
     (todo-list)
     (footer)]]])

;; ---------------------------------------------------------------------------
;; Handlers (called via eval — must be in this ns's scope)
;; ---------------------------------------------------------------------------

(defn add [text]
  (let [t {:id (rand-id) :text (str/trim text) :done false}]
    (when-not (str/blank? text)
      (swap! todos conj t))
    (e/js (e/morph "#add-form" (add-form))
          (when-not (str/blank? text) (e/append "#todo-list" (todo-item t)))
          (e/morph "#footer" (footer)))))

(defn delete-todo [id]
  (swap! todos (fn [ts] (vec (remove #(= id (:id %)) ts))))
  (e/js (e/remove-el (str "#todo-" id))
        (e/morph "#footer" (footer))))

(defn toggle [id]
  (swap! todos (fn [ts] (mapv #(if (= id (:id %)) (update % :done not) %) ts)))
  (e/js (e/morph (str "#todo-" id) (todo-item (first (filter #(= id (:id %)) @todos))))))

(defn set-filter [f]
  (reset! filt (keyword f))
  (e/js (e/morph "#todo-list" (todo-list))
        (e/morph "#filters" (filter-buttons))))

;; ---------------------------------------------------------------------------
;; Ring routes + server
;; ---------------------------------------------------------------------------

(defn get-handler [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (str "<!DOCTYPE html>" (e/render (page)))})

(defn reset-state [_]
  (reset! todos [])
  (reset! filt :all)
  {:status 200 :body ""})

(defn router [req]
  (case [(:request-method req) (:uri req)]
    [:get  "/"]      (get-handler req)
    [:post "/"]      (e/handler req)
    [:get  "/reset"] (reset-state req)
    [:post "/reset"] (reset-state req)
    {:status 404 :body "not found"}))

(def app
  (-> router
      params/wrap-params
      (resource/wrap-resource "public")))

;; start on port 4002
(def server (e/start! app 4002))
