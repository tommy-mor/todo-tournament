(ns tournament.evaleval
  (:require [clojure.string :as str]
            [dev.onionpancakes.chassis.core :as chassis]
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
;; Hiccup renderer — delegates to chassis (transitive dep via hyper)
;; ---------------------------------------------------------------------------

(defn render [hiccup]
  (chassis/html hiccup))

;; ---------------------------------------------------------------------------
;; JS DSL
;; ---------------------------------------------------------------------------

(defn morph  [sel h] (str "_morph('" sel "',`" (render h) "`)"))
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
