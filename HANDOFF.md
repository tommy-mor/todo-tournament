# Handoff: browser algebra via trail navigators

## What's broken and why

The design is right. The execution model is wrong.

**The design**: navigators are both filters AND actions. A path IS the test:
```clojure
(t/validate [(s/open url) (t/role "textbox") (s/fill "buy milk") (s/press "Enter") (t/role "listitem")] [])
```

**What goes wrong**: action navigators call `clojure.java.shell/sh` to run `spel` subprocesses. This blocks the JVM thread. Spel's daemon is stateful — it keeps a headless Chrome alive between commands. When the daemon wedges (browser closed, stale session, pipe not flushed), `sh/sh` blocks forever. The nREPL thread is dead. You have to kill and restart everything.

The failure cascade:
1. `spel open` → launches browser daemon (if not running), navigates
2. `spel fill @ref "text"` → talks to daemon over IPC
3. If daemon is in bad state → `sh/sh` hangs waiting for stdout
4. nREPL eval is blocked → REPL is dead
5. Kill nREPL, kill spel, kill chrome, restart everything
6. Python app still has old todos in memory → tests fail because state is dirty

**Three separate problems**:
1. **sh/sh blocks on spel** — need ProcessBuilder with timeout, or run spel from babashka/shell instead of JVM
2. **spel daemon state is fragile** — `spel close` + `spel open` between tests, or kill daemon between validators
3. **Python app state persists** — need to restart app between test runs, or add a `/reset` endpoint

**Possible fixes (pick one)**:
- **Option A**: Replace `sh/sh` with `ProcessBuilder` + timeout. Kill process after 10s. Catch TimeoutException in the navigator.
- **Option B**: Compile the navigator path to a babashka script. Run it outside the JVM. Parse results back.
- **Option C**: Use spel's Clojure SCI scripting (`spel eval-sci '...'`) to run the whole test in spel's process, not the JVM's.

## What exists

- **tommy-mor/trail** (`tommy-mor.trail`): path-based validator. A path is a sequence of `{:name str :match (fn [els] els)}` steps. `(validate path els)` reduces steps over a flat list of maps. When a step matches nothing, the trail of names IS the error message. 19 tests, stable.

- **tournament.spel**: bridge to spel (playwright-based browser CLI). `spel!` runs shell commands. `snap-els!` takes an ARIA snapshot and returns a flat list of `{:id :role :name :checked ...}` maps. Action navigators (`open`, `fill`, `click`, `press`) call spel and return fresh snapshots.

- **tournament.validators**: 7 behavioral validators for a TODO app. Currently being rewritten.

- **Two TODO apps**: Hyper (Clojure, port 4000) and evaleval (Python, port 4001). Both pass the same validators via spel snapshots.

## The design

Navigators are both **filters** and **actions**. A path IS the test:

```clojure
(t/validate
  [(open "http://localhost:4000")
   (role "textbox") (fill "buy milk")
   (press "Enter")
   (role "listitem")]
  [])
```

Reads: open URL, find textbox, fill it, press enter, assert listitem exists.

If the final step fails: `open(...) > role(textbox) > fill("buy milk") > press(Enter) > role(listitem): 0 matches`

## How action navigators work

A filter navigator narrows the list: `(role "textbox")` keeps only textboxes.

An action navigator **changes the browser and resets the list**:
- Takes matched elements (e.g. the textbox found by previous step)
- Picks the first one, gets its `@ref`
- Calls `spel fill @ref "buy milk"`
- Takes a fresh snapshot
- Returns ALL elements from the new snapshot

After an action, the next filter starts fresh against the whole page. This is correct — the page changed, the old element list is stale.

## The problem to solve

1. **validators.clj needs rewriting** to use pure navigator paths instead of imperative `open!/fill!/press!` calls interleaved with `validate-all`. Each validator should be one `(t/validate [...path...] [])` call.

2. **`validate-all` should be removed from trail**. Just use `(t/validate path els)`. If you need multiple, `(mapv #(t/validate % els) paths)`.

3. **`spel.clj` action navigators are done** (`open`, `fill`, `click`, `press`, `wait`). They need testing against both apps.

4. **Both apps need to be running** for tests. Hyper on 4000 (via nREPL `(require 'tournament.core)`), Python on 4001 (via `uvicorn`).

5. **The hyper app has a bug**: the form submission uses `$value` on a `data-on:submit__prevent` which gives form value not input value. It was partially fixed with a cursor-based approach but the SSE connection is flaky in headless browsers. The Python app (plain form POST/redirect) works reliably.

## File locations

```
~/programming/todo-tournament/
  deps.edn
  src/tournament/
    core.clj         — hyper server startup
    todo.clj         — hyper TODO app (hiccup + cursors)
    spel.clj         — spel bridge + action navigators
    validators.clj   — needs rewrite to pure paths
  python/
    app.py           — evaleval TODO app (FastAPI + forms)

~/programming/clones/hyper/  — hyper framework (local dep)

tommy-mor/spy repo (github):
  spy/src/tommy_mor/trail.clj  — the validator engine
  src/spy.clj                  — the debug tool
```

## nREPL

Port 60012 (todo-tournament project). Hyper server on 4000.

## What the rewritten validators should look like

```clojure
(defn v-add-todo [url]
  (t/validate
    [(open url)
     (t/role "textbox") (fill "buy milk")
     (press "Enter")
     (t/role "listitem")
     (t/name-match #"buy milk")]
    []))

(defn v-complete-todo [url]
  (t/validate
    [(open url)
     (t/role "textbox") (fill "buy milk")
     (press "Enter")
     (t/role "checkbox") t/unchecked click
     (t/role "checkbox") t/checked]
    []))

(defn v-delete-todo [url]
  (t/validate
    [(open url)
     (t/role "textbox") (fill "buy milk")
     (press "Enter")
     (t/role "button") (t/name-match #"×") click
     (t/absent (t/role "listitem"))]
    []))
```

Each validator is one path. Open, act, filter, act, assert. The path is the test AND the error message.
