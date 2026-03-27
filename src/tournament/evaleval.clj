(ns tournament.evaleval
  (:require [clojure.string :as str]
            [org.httpkit.server :as http-kit]
            [ring.util.response :as resp])
  (:import [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

;; ---------------------------------------------------------------------------
;; Signing
;; ---------------------------------------------------------------------------

(def ^:private secret (or (System/getenv "EVALEVAL_SECRET") "dev-secret"))

(defn- hmac [s]
  (let [mac (Mac/getInstance "HmacSHA256")
        key (SecretKeySpec. (.getBytes secret "UTF-8") "HmacSHA256")]
    (.init mac key)
    (let [b (.doFinal mac (.getBytes s "UTF-8"))]
      (apply str (map #(format "%02x" (bit-and % 0xff)) b)))))

(defn sign [code-str]
  (str code-str "|" (hmac code-str)))

(defn verify! [signed]
  (let [idx (.lastIndexOf signed "|")
        code (subs signed 0 idx)
        sig  (subs signed (inc idx))]
    (when-not (= sig (hmac code))
      (throw (ex-info "invalid snippet" {:status 403})))
    code))

;; ---------------------------------------------------------------------------
;; Interpolation
;; ---------------------------------------------------------------------------

(defn interpolate [code form-params]
  (str/replace code #"\$([a-zA-Z0-9_-]+)"
               (fn [[_ k]] (pr-str (get form-params k "")))))

;; ---------------------------------------------------------------------------
;; Hiccup renderer
;; ---------------------------------------------------------------------------

(def ^:private void-tags #{:area :base :br :col :embed :hr :img :input :link :meta :param :source :track :wbr})

(defn render [x]
  (cond
    (nil? x)    ""
    (string? x) x
    (number? x) (str x)
    (seq? x)    (apply str (map render x))
    (vector? x)
    (let [[tag & rest] x
          attrs  (when (map? (first rest)) (first rest))
          children (if attrs (drop 1 rest) rest)
          tag-name (name tag)
          attr-str (when attrs
                     (str/join "" (for [[k v] attrs
                                        :when (and v (not= v false))]
                                    (if (true? v)
                                      (str " " (name k))
                                      (str " " (name k) "=\"" v "\"")))))]
      (if (void-tags tag)
        (str "<" tag-name attr-str ">")
        (str "<" tag-name attr-str ">"
             (apply str (map render children))
             "</" tag-name ">")))
    :else (str x)))

;; ---------------------------------------------------------------------------
;; JS DSL
;; ---------------------------------------------------------------------------

(defn morph  [sel h] (str "Idiomorph.morph(document.querySelector('" sel "'),`" (render h) "`)"))
(defn append [sel h] (str "document.querySelector('" sel "').insertAdjacentHTML('beforeend',`" (render h) "`)"))
(defn remove-el [sel] (str "document.querySelector('" sel "').remove()"))
(defn js [& stmts]   (resp/response (str/join ";" (remove nil? stmts))))

;; ---------------------------------------------------------------------------
;; Snippet embed
;; ---------------------------------------------------------------------------

(defn snippet-inputs [code-str]
  [:input {:type "hidden" :name "evaleval-snippet" :value (sign code-str)}])

;; ---------------------------------------------------------------------------
;; POST handler
;; ---------------------------------------------------------------------------

(defn handler [req]
  (let [params  (merge (:form-params req) (:params req))
        signed  (get params "evaleval-snippet")
        code    (verify! signed)
        interp  (interpolate code (dissoc params "evaleval-snippet"))]
    (eval (read-string interp))))

;; ---------------------------------------------------------------------------
;; Server start
;; ---------------------------------------------------------------------------

(defn start! [ring-handler port]
  (http-kit/run-server ring-handler {:port port}))
