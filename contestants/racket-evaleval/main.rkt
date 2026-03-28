#lang racket

;; TodoMVC — Racket evaleval contestant
;;
;; Uses X-expressions (Racket's native HTML representation) throughout.
;; The evaleval pattern: forms embed signed Racket code in hidden fields.
;; On POST, the code is verified, interpolated, and eval'd. The response
;; is JavaScript that morphs the DOM via Idiomorph.

(require "evaleval.rkt"
         racket/string
         racket/list
         web-server/servlet-env
         web-server/http/request-structs
         web-server/http/response-structs
         web-server/http/bindings
         net/url-structs)

;;; --------------------------------------------------------------------------
;;; Eval namespace — snippets are eval'd here so they can access all bindings
;;; --------------------------------------------------------------------------

(define-namespace-anchor ns-anchor)
(define app-ns (namespace-anchor->namespace ns-anchor))

;;; --------------------------------------------------------------------------
;;; State
;;; --------------------------------------------------------------------------

(define todos (box '()))
(define filt  (box "all"))

(define (rand-id)
  (number->string (+ (random 89999999) 10000000)))

(define (visible)
  (define f (unbox filt))
  (define ts (unbox todos))
  (cond
    [(equal? f "active")    (filter (λ (t) (not (hash-ref t 'done))) ts)]
    [(equal? f "completed") (filter (λ (t) (hash-ref t 'done)) ts)]
    [else ts]))

(define (find-todo id)
  (findf (λ (t) (equal? (hash-ref t 'id) id)) (unbox todos)))

;;; --------------------------------------------------------------------------
;;; Components — all return X-expressions
;;; --------------------------------------------------------------------------

(define (footer)
  (define ts (unbox todos))
  `(footer ((class "todo-footer") (id "footer"))
     ,@(if (null? ts)
           '()
           (list
            `(span ((class "todo-footer__count") (id "count"))
               ,(format "~a items left"
                        (length (filter (λ (t) (not (hash-ref t 'done))) ts))))
            `(div ((class "todo-filters") (id "filters"))
               ,@(for/list ([pair '(("All" . "all") ("Active" . "active") ("Completed" . "completed"))])
                   (define label (car pair))
                   (define f (cdr pair))
                   `(form ((method "post") (style "display:contents"))
                      ,(server
                        (begin (set-box! filt (~ f))
                               (js (morph "#todo-list" (todo-list))
                                   (morph "#footer" (footer)))))
                      (button ((type "submit") (class "todo-filter__btn"))
                        ,label))))))))

(define (todo-item t)
  (define id    (hash-ref t 'id))
  (define done? (hash-ref t 'done))
  `(li ((id ,(string-append "todo-" id))
        (class ,(if done? "todo-item todo-item--done" "todo-item")))
     ;; Toggle checkbox
     (form ((method "post") (style "display:contents"))
       ,(server
         (begin
           (set-box! todos
             (map (λ (t) (if (equal? (hash-ref t 'id) (~ id))
                             (hash-set t 'done (not (hash-ref t 'done)))
                             t))
                  (unbox todos)))
           (js (morph (~ (string-append "#todo-" id))
                      (todo-item (find-todo (~ id))))
               (morph "#footer" (footer)))))
       (input ((type "checkbox") (class "todo-item__toggle")
               (onchange "this.form.requestSubmit()")
               ,@(if done? '((checked "")) '()))))
     ;; Text
     (span ((class ,(if done? "todo-item__text--done" "")))
       ,(hash-ref t 'text))
     ;; Delete button
     (form ((method "post") (style "display:contents"))
       ,(server
         (begin
           (set-box! todos
             (filter (λ (t) (not (equal? (hash-ref t 'id) (~ id))))
                     (unbox todos)))
           (js (remove-el (~ (string-append "#todo-" id)))
               (morph "#footer" (footer)))))
       (button ((type "submit") (class "todo-item__delete")) "\u00d7"))))

(define (todo-list)
  `(ul ((id "todo-list") (class "todo-list"))
     ,@(map todo-item (visible))))

(define (add-form)
  `(form ((id "add-form") (action "/") (method "post"))
     ,(server
       (let ([text (string-trim $new-todo)])
         (when (not (equal? text ""))
           (set-box! todos
             (append (unbox todos)
                     (list (hasheq 'id (rand-id) 'text text 'done #f)))))
         (js (morph "#add-form" (add-form))
             (and (not (equal? text ""))
                  (append-el "#todo-list" (todo-item (last (unbox todos)))))
             (morph "#footer" (footer))
             (raw "document.querySelector('.todo-new-input').focus()"))))
     (input ((type "text") (name "new-todo") (class "todo-new-input")
             (placeholder "What needs to be done?") (autocomplete "off")
             (autofocus "autofocus")))
     (button ((type "submit") (style "display:none")) "Add")))

(define (page)
  `(html ()
     (head ()
       (meta ((charset "utf-8")))
       (title () "TodoMVC \u2014 Racket evaleval")
       (link ((rel "stylesheet") (href "/tournament.css")))
       (script ((src "/idiomorph.min.js")) "")
       (script ((src "/evaleval.js") (defer "defer")) ""))
     (body ()
       (div ((class "todo-app"))
         (h1 ((class "todo-app__title")) "todos")
         ,(add-form)
         ,(todo-list)
         ,(footer)))))

;;; --------------------------------------------------------------------------
;;; HTTP helpers
;;; --------------------------------------------------------------------------

(define (html-response body)
  (response/full 200 #"OK" (current-seconds) #"text/html; charset=utf-8" '()
                 (list (string->bytes/utf-8 body))))

(define (text-response body)
  (response/full 200 #"OK" (current-seconds) #"text/plain; charset=utf-8" '()
                 (list (string->bytes/utf-8 body))))

(define (get-path req)
  (define parts (url-path (request-uri req)))
  (if (null? parts)
      "/"
      (let ([p (string-append "/" (string-join (map path/param-path parts) "/"))])
        (if (and (> (string-length p) 1) (string-suffix? p "/"))
            (substring p 0 (sub1 (string-length p)))
            p))))

(define (get-form-params req)
  (for/hash ([b (in-list (request-bindings/raw req))]
             #:when (binding:form? b))
    (values (bytes->string/utf-8 (binding-id b))
            (bytes->string/utf-8 (binding:form-value b)))))

;;; --------------------------------------------------------------------------
;;; Route handlers
;;; --------------------------------------------------------------------------

(define (handle-get req)
  (html-response (string-append "<!DOCTYPE html>" (render (page)))))

(define (handle-post req)
  (define params (get-form-params req))
  (define signed (hash-ref params "evaleval-snippet" #f))
  (unless signed (error 'handle-post "missing evaleval-snippet"))
  (define code (verify! signed))
  (define interp (interpolate code (hash-remove params "evaleval-snippet")))
  (define result (eval (read (open-input-string interp)) app-ns))
  (text-response (if (string? result) result "")))

(define (handle-reset _req)
  (set-box! todos '())
  (set-box! filt "all")
  (text-response ""))

;;; --------------------------------------------------------------------------
;;; Main dispatch
;;; --------------------------------------------------------------------------

(define (app req)
  (define method (request-method req))
  (define path   (get-path req))
  (cond
    [(and (equal? method #"GET")  (equal? path "/"))  (handle-get req)]
    [(and (equal? method #"POST") (equal? path "/"))  (handle-post req)]
    [(equal? path "/reset")                            (handle-reset req)]
    [else (response/full 404 #"Not Found" (current-seconds)
                         #"text/plain" '() (list #"not found"))]))

;;; --------------------------------------------------------------------------
;;; Server start
;;; --------------------------------------------------------------------------

(define port
  (if (> (vector-length (current-command-line-arguments)) 0)
      (string->number (vector-ref (current-command-line-arguments) 0))
      4000))

(define static-dir
  (simplify-path (build-path (path-only (syntax-source #'here)) ".." ".." "resources" "public")))

(printf "Racket evaleval todo on port ~a\n" port)
(printf "Static files from ~a\n" static-dir)

(serve/servlet app
               #:port port
               #:listen-ip #f
               #:launch-browser? #f
               #:servlet-path "/"
               #:servlet-regexp #rx"^/(|reset)$"
               #:extra-files-paths (list static-dir))
