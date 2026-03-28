#lang racket

;; evaleval.rkt — The evaleval pattern for Racket
;;
;; The `server` macro walks a body at compile time, replacing (~ expr) with
;; gensym placeholders, serializes the whole form with format ~s, and builds a
;; runtime string-replace chain that substitutes each placeholder with the
;; printed runtime value. The result is SHA1-signed and embedded in a hidden
;; form field. On POST, the snippet is verified, $name placeholders are
;; interpolated from form fields, then read+eval'd.
;;
;; HTML uses Racket's native X-expression format throughout:
;;   (tag ((attr "val") ...) children ...)

(provide server ~
         sign verify! interpolate
         render hidden-input
         morph append-el remove-el raw js)

(require file/sha1
         racket/string
         xml
         (for-syntax racket/base racket/list syntax/stx))

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
    (for/last ([i (in-range (string-length signed))]
               #:when (char=? (string-ref signed i) #\|))
      i))
  (unless idx (error 'verify! "no | separator"))
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
;;; HTML rendering — X-expressions via Racket's xml library
;;; --------------------------------------------------------------------------

(define html-void-elements
  '(area base br col embed hr img input link meta param source track wbr))

(define (render xexpr)
  (parameterize ([empty-tag-shorthand html-void-elements])
    (xexpr->string xexpr)))

;;; --------------------------------------------------------------------------
;;; JS DSL — generate JavaScript strings for DOM updates via Idiomorph
;;; --------------------------------------------------------------------------

(define (morph sel xexpr)
  (format "Idiomorph.morph(document.querySelector('~a'),`~a`)" sel (render xexpr)))

(define (append-el sel xexpr)
  (format "document.querySelector('~a').insertAdjacentHTML('beforeend',`~a`)"
          sel (render xexpr)))

(define (remove-el sel)
  (format "document.querySelector('~a').remove()" sel))

(define (raw js-code) js-code)

(define (js . stmts)
  (string-join (filter string? stmts) ";"))

;;; --------------------------------------------------------------------------
;;; Hidden input helper — returns an X-expression
;;; --------------------------------------------------------------------------

(define (hidden-input signed-value)
  `(input ((type "hidden") (name "evaleval-snippet") (value ,signed-value))))

;;; --------------------------------------------------------------------------
;;; The server macro
;;;
;;; Walks the body at compile time. (~ expr) marks render-time values that get
;;; substituted into the serialized code string via gensym placeholders.
;;; $name tokens are left as-is for runtime interpolation from form fields.
;;;
;;; Expansion:  (hidden-input (sign <string-replace chain>))
;;; --------------------------------------------------------------------------

(define-syntax (~ stx)
  (raise-syntax-error '~ "~ used outside of server macro" stx))

(define-syntax (server stx)
  (syntax-case stx ()
    [(_ body ...)
     (let ()
       (define pairs '()) ; (placeholder-string . syntax-for-expr) ...

       (define (walk s)
         (cond
           [(stx-pair? s)
            (define head (stx-car s))
            (define rest (syntax->list s))
            (cond
              ;; (~ expr) — splice render-time value
              [(and rest (= 2 (length rest))
                    (identifier? head)
                    (free-identifier=? head #'~))
               (define g (gensym "__evu"))
               (set! pairs (cons (cons (symbol->string g) (cadr rest)) pairs))
               (datum->syntax s g s)]
              ;; Recurse into sub-expressions
              [rest  (datum->syntax s (map walk rest) s)]
              ;; Dotted pair
              [else  (datum->syntax s (cons (walk (stx-car s))
                                            (walk (stx-cdr s))) s)])]
           [else s]))

       (define body-list (syntax->list #'(body ...)))
       (define wrapped (if (= 1 (length body-list))
                           (car body-list)
                           #'(begin body ...)))
       (define xformed (walk wrapped))
       (define code-str (format "~s" (syntax->datum xformed)))

       ;; Build chain:  (string-replace (string-replace code ph1 v1) ph2 v2) ...
       (define signed-expr
         (for/fold ([expr (datum->syntax stx code-str)])
                   ([p (in-list (reverse pairs))])
           (with-syntax ([e  expr]
                         [ph (datum->syntax stx (car p))]
                         [vx (cdr p)])
             #'(string-replace e ph (format "~s" vx)))))

       (with-syntax ([signed-code signed-expr])
         #'(hidden-input (sign signed-code))))]))
