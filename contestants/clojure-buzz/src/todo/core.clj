(ns todo.core
  (:require [buzz.core :as buzz :refer [client defpart defui server server!]]
            [clojure.string :as str]
            [org.httpkit.server :as http]))

(defonce todos (atom []))
(defonce filt  (atom "all"))

(defn- rand-id []
  (subs (str (java.util.UUID/randomUUID)) 0 8))

(defn add! [title]
  (let [title (some-> title str/trim not-empty)]
    (when title
      (swap! todos conj {:id (rand-id) :text title :done false}))))

(defn toggle! [id]
  (swap! todos (fn [ts]
                 (mapv #(if (= id (:id %)) (update % :done not) %) ts))))

(defn delete! [id]
  (swap! todos (fn [ts] (vec (remove #(= id (:id %)) ts)))))

(defn visible []
  (case @filt
    "active"    (vec (remove :done @todos))
    "completed" (vec (filter :done @todos))
    (vec @todos)))

(defn reset-state! []
  (reset! todos [])
  (reset! filt "all"))

(defpart todo-row [{:keys [id text done]}]
  [:li {:key id
        :class (str "todo-item" (when done " todo-item--done"))}
   [:input {:type "checkbox"
            :class "todo-item__toggle"
            :checked done
            :on-change (fn [_] (server! (toggle! (client id))))}]
   [:span {:class (when done "todo-item__text--done")} text]
   [:button {:class "todo-item__delete"
             :on-click (fn [_] (server! (delete! (client id))))}
    "×"]])

(defpart filter-btn [label value f]
  [:button {:class (str "todo-filter__btn"
                        (when (= f value) " todo-filter__btn--active"))
            :on-click (fn [_] (server! (reset! filt (client value))))}
   label])

(defpart footer [left f]
  [:footer {:class "todo-footer"}
   [:span {:class "todo-footer__count"} (str left " items left")]
   [:div {:class "todo-filters"}
    (filter-btn "All" "all" f)
    (filter-btn "Active" "active" f)
    (filter-btn "Completed" "completed" f)]])

(defui todo-app []
  (let [items (server (visible))
        left  (server (count (remove :done @todos)))
        has?  (server (boolean (seq @todos)))
        f     (server @filt)]
    [:div {:class "todo-app"}
     [:h1 {:class "todo-app__title"} "todos"]
     [:input {:type "text"
              :class "todo-new-input"
              :placeholder "What needs to be done?"
              :autofocus true
              :autocomplete "off"
              :on-key-down (fn [e]
                             (when (= "Enter" (.-key e))
                               (server! (add! (client (.. e -target -value))))
                               (set! (.. e -target -value) "")
                               (.focus (.-target e))))}]
     [:ul {:class "todo-list"}
      (for [t items] (todo-row t))]
     (when has? (footer left f))]))

(def ui
  (buzz/handler {:index "public/index.html"
                 :watch [todos filt]
                 :render-interval-ms 0
                 :mounts [{:el "app" :ui #'todo-app}]}))

(defn app [req]
  (if (= "/reset" (:uri req))
    (do (reset-state!)
        {:status 200 :headers {"Content-Type" "text/plain"} :body ""})
    (or (ui req) {:status 404 :body "not found"})))

(defn -main [& [port]]
  (let [port (Integer/parseInt (or port "4005"))]
    (http/run-server app {:port port})
    (println (str "Buzz todo on http://localhost:" port))
    @(promise)))
