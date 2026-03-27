import uuid
from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse, PlainTextResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from evaleval import Signer, Three, Two, Selector, MORPH, APPEND, REMOVE, render
from evaleval import SnippetExecutionError

app = FastAPI()
app.mount("/static", StaticFiles(directory="static"), name="static")

signer = Signer()
TODOS: list[dict] = []
FILTER = "all"

# --- helpers ---
def active(): return [t for t in TODOS if not t["done"]]
def completed(): return [t for t in TODOS if t["done"]]
def visible():
    if FILTER == "active": return active()
    if FILTER == "completed": return completed()
    return TODOS

# --- components (hiccup) ---
def count_display():
    return ["span", {"id": "count", "class": "todo-footer__count"},
            f"{len(active())} items left"]

def filter_buttons():
    btns = []
    for label, f in [("All", "all"), ("Active", "active"), ("Completed", "completed")]:
        btns.append(["form", {"method": "post", "style": "display:contents"},
                     *signer.snippet_hidden(f"set_filter('{f}')"),
                     ["button", {"type": "submit", "class": "todo-filter__btn"}, label]])
    return ["div", {"id": "filters", "class": "todo-filters"}, *btns]

def footer():
    if not TODOS:
        return ["footer", {"id": "footer", "class": "todo-footer"}]
    return ["footer", {"id": "footer", "class": "todo-footer"},
            count_display(), filter_buttons()]

def todo_item(t):
    done_class = "todo-item todo-item--done" if t["done"] else "todo-item"
    return ["li", {"id": f"todo-{t['id']}", "class": done_class},
            ["form", {"method": "post", "style": "display:contents"},
             *signer.snippet_hidden(f"toggle('{t['id']}')"),
             ["input", {"type": "checkbox", "class": "todo-item__toggle",
                        **({"checked": "true"} if t["done"] else {}),
                        "onchange": "this.form.requestSubmit()"}]],
            ["span", {"class": "todo-item__text--done" if t["done"] else ""}, t["text"]],
            ["form", {"method": "post", "style": "display:contents"},
             *signer.snippet_hidden(f"delete_todo('{t['id']}')"),
             ["button", {"type": "submit", "class": "todo-item__delete"}, "×"]]]

def todo_list():
    return ["ul", {"id": "todo-list", "class": "todo-list"},
            *[todo_item(t) for t in visible()]]

def add_form():
    return ["form", {"id": "add-form", "method": "post"},
            *signer.snippet_hidden("add($new_todo)"),
            ["input", {"type": "text", "name": "new_todo", "class": "todo-new-input",
                       "placeholder": "What needs to be done?", "autocomplete": "off"}],
            ["button", {"type": "submit", "style": "display:none"}, "Add"]]

def page():
    return f"""<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>TodoMVC — Python evaleval</title>
  <link rel="stylesheet" href="/static/tournament.css">
  <script src="/static/idiomorph.min.js"></script>
  <script src="/static/evaleval.js" defer></script>
</head>
<body>
  <div class="todo-app">
    <h1 class="todo-app__title">todos</h1>
    {render(add_form())}
    {render(todo_list())}
    {render(footer())}
  </div>
</body>
</html>"""

# --- handlers (called via eval) ---
def add(new_todo: str):
    text = new_todo.strip()
    if text:
        t = {"id": uuid.uuid4().hex[:8], "text": text, "done": False}
        TODOS.append(t)
    js = [
        Three[Selector("#add-form")][MORPH][add_form()],
    ]
    if text:
        js += [Three[Selector("#todo-list")][APPEND][todo_item(TODOS[-1])]]
    js += [
        Three[Selector("#footer")][MORPH][footer()],
        "document.querySelector('[autofocus]') && document.querySelector('[autofocus]').focus()",
    ]
    return PlainTextResponse(";".join(str(j) for j in js))

def toggle(todo_id: str):
    t = next((t for t in TODOS if t["id"] == todo_id), None)
    if not t: return PlainTextResponse("", status_code=404)
    t["done"] = not t["done"]
    return PlainTextResponse(";".join([
        str(Three[Selector(f"#todo-{todo_id}")][MORPH][todo_item(t)]),
        str(Three[Selector("#footer")][MORPH][footer()]),
    ]))

def delete_todo(todo_id: str):
    global TODOS
    TODOS = [t for t in TODOS if t["id"] != todo_id]
    return PlainTextResponse(";".join([
        str(Two[Selector(f"#todo-{todo_id}")][REMOVE]),
        str(Three[Selector("#footer")][MORPH][footer()]),
    ]))

def set_filter(f: str):
    global FILTER
    FILTER = f
    return PlainTextResponse(";".join([
        str(Three[Selector("#todo-list")][MORPH][todo_list()]),
        str(Three[Selector("#footer")][MORPH][footer()]),
    ]))

# --- routes ---
@app.get("/", response_class=HTMLResponse)
async def index():
    return page()

@app.post("/")
async def do(request: Request):
    form = await request.form()
    try:
        snippet = signer.verify_snippet(form)
        return eval(snippet)
    except SnippetExecutionError as e:
        return PlainTextResponse(e.message, status_code=e.status_code)

@app.get("/reset")
@app.post("/reset")
async def reset():
    global TODOS, FILTER
    TODOS = []
    FILTER = "all"
    return PlainTextResponse("")
