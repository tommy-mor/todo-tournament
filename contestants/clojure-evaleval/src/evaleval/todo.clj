(ns evaleval.todo
  (:require [evaleval.core :as e]
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

(defn footer []
  [:footer.todo-footer {:id "footer"}
   (when (seq @todos)
     (list
      [:span.todo-footer__count {:id "count"}
       (str (count (remove :done @todos)) " items left")]
      [:div.todo-filters {:id "filters"}
       (for [[label f] [["All" :all] ["Active" :active] ["Completed" :completed]]]
         [:form {:method "post" :style "display:contents"}
          (e/server
           (do (reset! filt ~f)
               (e/js (e/morph "#todo-list" (todo-list))
                     (e/morph "#footer" (footer)))))
          [:button.todo-filter__btn {:type "submit"} label]])]))])

(defn todo-item [t]
  [:li.todo-item {:id (str "todo-" (:id t))
                  :class (when (:done t) "todo-item todo-item--done")}
   [:form {:method "post" :style "display:contents"}
    (e/server
     (do (swap! todos (fn [ts] (mapv #(if (= ~(:id t) (:id %)) (update % :done not) %) ts)))
         (e/js (e/morph ~(str "#todo-" (:id t))
                        (todo-item (first (filter #(= ~(:id t) (:id %)) @todos))))
               (e/morph "#footer" (footer)))))
    [:input.todo-item__toggle {:type "checkbox" :checked (when (:done t) true)
                               :onchange "this.form.requestSubmit()"}]]
   [:span {:class (when (:done t) "todo-item__text--done")} (:text t)]
   [:form {:method "post" :style "display:contents"}
    (e/server
     (do (swap! todos (fn [ts] (vec (remove #(= ~(:id t) (:id %)) ts))))
         (e/js (e/remove-el ~(str "#todo-" (:id t)))
               (e/morph "#footer" (footer)))))
    [:button.todo-item__delete {:type "submit"} "×"]]])

(defn todo-list []
  [:ul.todo-list {:id "todo-list"}
   (map todo-item (visible))])

(defn add-form []
  [:form {:id "add-form" :action "/" :method "post"}
   (e/server
    (let [text (str/trim $new-todo)]
      (when-not (str/blank? text)
        (swap! todos conj {:id (rand-id) :text text :done false}))
      (e/js (e/morph "#add-form" (add-form))
            (when-not (str/blank? text)
              (e/append "#todo-list" (todo-item (last @todos))))
            (e/morph "#footer" (footer))
            (e/raw "document.querySelector('[autofocus]').focus()"))))
   [:input.todo-new-input {:type "text" :name "new-todo"
                           :placeholder "What needs to be done?" :autocomplete "off"}]
   [:button {:type "submit" :style "display:none"} "Add"]])

(defn page []
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:title "TodoMVC — evaleval"]
    [:link {:rel "stylesheet" :href "/tournament.css"}]
    [:script {:src "/idiomorph.min.js"}]
    [:script {:src "/evaleval.js" :defer true}]]
   [:body
    [:div.todo-app
     [:h1.todo-app__title "todos"]
     (add-form)
     (todo-list)
     (footer)]]])

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
  (binding [e/*eval-ns* (find-ns 'evaleval.todo)]
    (case [(:request-method req) (:uri req)]
      [:get  "/"]      (get-handler req)
      [:post "/"]      (e/handler req)
      [:get  "/reset"] (reset-state req)
      [:post "/reset"] (reset-state req)
      {:status 404 :body "not found"})))

(def app
  (-> router
      params/wrap-params
      (resource/wrap-resource "public")))
