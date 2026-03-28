#lang racket

;; evaleval.rkt — The evaleval pattern for Racket
;;
;; The `server` macro is the star: it walks a Racket expression at compile time,
;; replacing (~ expr) with gensym placeholders, serializing the whole form as a
;; string, and generating a runtime chain of string-replace calls that substitute
;; each placeholder with (format "~s" expr). The result is signed with HMAC-like
;; SHA256 and embedded in a hidden form field. On POST, the snippet is verified,
;; $name placeholders are interpolated from form fields, then read+eval'd.

(provide server ~
         sign verify! interpolate
         render hidden-input
         morph append-el remove-el raw js)

(require file/sha1
         racket/string
         (for-syntax racket/base racket/list racket/syntax))

;;; --------------------------------------------------------------------------
;;; Signing
;;; --------------------------------------------------------------------------

(define secret (or (getenv "EVALEVAL_SECRET") "dev-secret"))

(define (compute-sig s)
  (sha1 (open-input-string (string-append secret s))))

(define (sign code-str)
  (string-append code-str "|" (compute-sig code-str)))

(define (verify! signed)
  (define idx
    (let loop ([i (sub1 (string-length signed))])
      (cond [(< i 0) (error 'verify! "no | separator")]
            [(char=? (string-ref signed i) #\|) i]
            [else (loop (sub1 i))])))
  (define code (substring signed 0 idx))
  (define sig  (substring signed (add1 idx)))
  (unless (equal? sig (compute-sig code))
    (error 'verify! "invalid snippet"))
  code)

;;; --------------------------------------------------------------------------
;;; Interpolation — $name placeholders replaced with form field values
;;; --------------------------------------------------------------------------

(define (interpolate code form-params)
  (regexp-replace* #rx"\\$([a-zA-Z0-9_-]+)" code
    (lambda (full name)
      (format "~s" (hash-ref form-params name "")))))

;;; --------------------------------------------------------------------------
;;; HTML rendering — hiccup-style S-expressions to HTML strings
;;;
;;; Format:  (tag hash-attrs child ...)  or  (tag child ...)
;;;          "string"  for text nodes
;;; --------------------------------------------------------------------------

(define void-elements
  (set 'area 'base 'br 'col 'embed 'hr 'img 'input
       'link 'meta 'param 'source 'track 'wbr))

(define (escape-html s)
  (regexp-replace* #rx"[&<>]" s
    (lambda (m)
      (case (string-ref m 0)
        [(#\&) "&amp;"]
        [(#\<) "&lt;"]
        [(#\>) "&gt;"]
        [else m]))))

(define (escape-attr s)
  (regexp-replace* #rx"[&<>\"]" s
    (lambda (m)
      (case (string-ref m 0)
        [(#\&) "&amp;"]
        [(#\<) "&lt;"]
        [(#\>) "&gt;"]
        [(#\") "&quot;"]
        [else m]))))

(define (render-attrs attrs)
  (apply string-append
         (for/list ([(k v) (in-hash attrs)]
                    #:when v)                     ; skip #f
           (define key-str (if (symbol? k) (symbol->string k) (format "~a" k)))
           (if (eq? v #t)
               (string-append " " key-str)        ; boolean attr
               (string-append " " key-str "=\""
                              (escape-attr (format "~a" v)) "\"")))))

(define (render x)
  (cond
    [(string? x)  (escape-html x)]
    [(number? x)  (number->string x)]
    [(or (boolean? x) (void? x) (null? x)) ""]
    ;; Element: (tag maybe-attrs children...)
    [(and (pair? x) (symbol? (car x)))
     (define tag (car x))
     (define rest-items (cdr x))
     (define-values (attrs children)
       (if (and (pair? rest-items) (hash? (car rest-items)))
           (values (car rest-items) (cdr rest-items))
           (values #hasheq() rest-items)))
     (define tag-str (symbol->string tag))
     (define attr-str (render-attrs attrs))
     (if (set-member? void-elements tag)
         (string-append "<" tag-str attr-str ">")
         (string-append "<" tag-str attr-str ">"
                        (apply string-append (map render children))
                        "</" tag-str ">"))]
    ;; List of children (from map, for/list, etc.)
    [(list? x)  (apply string-append (map render x))]
    [else ""]))

;;; --------------------------------------------------------------------------
;;; JS DSL — generate JavaScript strings for DOM updates via Idiomorph
;;; --------------------------------------------------------------------------

(define (morph sel hiccup)
  (format "Idiomorph.morph(document.querySelector('~a'),`~a`)" sel (render hiccup)))

(define (append-el sel hiccup)
  (format "document.querySelector('~a').insertAdjacentHTML('beforeend',`~a`)"
          sel (render hiccup)))

(define (remove-el sel)
  (format "document.querySelector('~a').remove()" sel))

(define (raw js-code) js-code)

(define (js . stmts)
  (string-join (filter string? stmts) ";"))

;;; --------------------------------------------------------------------------
;;; Hidden input helper
;;; --------------------------------------------------------------------------

(define (hidden-input signed-value)
  (list 'input (hasheq 'type "hidden" 'name "evaleval-snippet" 'value signed-value)))

;;; --------------------------------------------------------------------------
;;; The server macro
;;;
;;; Walks the body at compile time. (~ expr) marks render-time values that get
;;; substituted into the code string via gensym placeholders. $name tokens are
;;; left as-is for runtime interpolation from form fields.
;;;
;;; Expansion:  (hidden-input (sign <string-replace chain>))
;;; --------------------------------------------------------------------------

(define-syntax (~ stx)
  (raise-syntax-error '~ "~ used outside of server macro" stx))

(define-syntax (server stx)
  (syntax-case stx ()
    [(_ body ...)
     (let ()
       (define pairs '()) ; list of (placeholder-string . syntax-for-expr)

       ;; Walk syntax tree, replacing (~ expr) with gensym placeholders
       (define (walk s)
         (define lst (syntax->list s))
         (cond
           ;; Atom (identifier, literal, etc.)
           [(not lst) s]
           ;; Empty list
           [(null? lst) s]
           ;; (~ expr) — unquote for render-time embedding
           [(and (identifier? (car lst))
                 (eq? (syntax-e (car lst)) '~)
                 (= (length lst) 2))
            (define g (gensym "__evu"))
            (set! pairs (cons (cons (symbol->string g) (cadr lst)) pairs))
            (datum->syntax s g s)]
           ;; Recurse into sub-expressions
           [else
            (datum->syntax s (map walk lst) s)]))

       (define body-list (syntax->list #'(body ...)))
       (define wrapped (if (= 1 (length body-list))
                           (car body-list)
                           #'(begin body ...)))
       (define xformed (walk wrapped))
       (define code-str (format "~s" (syntax->datum xformed)))

       ;; Build chain:  (string-replace (string-replace code ph1 v1) ph2 v2) ...
       (define signed-expr
         (let loop ([expr (datum->syntax stx code-str)]
                    [ps   (reverse pairs)])
           (if (null? ps)
               expr
               (let ([p (car ps)])
                 (loop (with-syntax ([e  expr]
                                     [ph (datum->syntax stx (car p))]
                                     [vx (cdr p)])
                         #'(string-replace e ph (format "~s" vx)))
                       (cdr ps))))))

       (with-syntax ([signed-code signed-expr])
         #'(hidden-input (sign signed-code))))]))
