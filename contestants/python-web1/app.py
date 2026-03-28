"""
TODO app — evaleval.hiccup + FastAPI. Web 1.0 forms. No JS.
Second contestant in the tournament.
"""

import uuid
from fastapi import FastAPI, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from evaleval.hiccup import render, RawContent

app = FastAPI()

# ---------------------------------------------------------------------------
# State (in-memory)
# ---------------------------------------------------------------------------

TODOS: list[dict] = []
FILTER = "all"


def active_todos():
    return [t for t in TODOS if not t["done"]]


def completed_todos():
    return [t for t in TODOS if t["done"]]


def visible_todos():
    if FILTER == "active":
        return active_todos()
    elif FILTER == "completed":
        return completed_todos()
    return TODOS


# ---------------------------------------------------------------------------
# Hiccup components
# ---------------------------------------------------------------------------

def todo_item(todo):
    tid = todo["id"]
    return ["li",
        ["form", {"method": "post", "action": f"/toggle/{tid}",
                  "style": "display:inline"},
            ["input", {"type": "checkbox",
                       **({"checked": "true"} if todo["done"] else {}),
                       "onchange": "this.form.submit()"}]],
        ["span", {"style": "text-decoration: line-through;" if todo["done"] else ""},
            todo["text"]],
        ["form", {"method": "post", "action": f"/delete/{tid}",
                  "style": "display:inline; margin-left:8px;"},
            ["button", {"type": "submit",
                        "style": "background:none; border:none; cursor:pointer; color:#c33;"},
                "×"]]]


def filter_btn(label, value):
    active = FILTER == value
    return ["form", {"method": "post", "action": f"/filter/{value}",
                     "style": "display:inline;"},
        ["button", {"type": "submit",
                    "style": f"cursor:pointer; border:1px solid {'#333' if active else '#ddd'}; border-radius:3px; padding:2px 8px; background:none;"},
            label]]


def page():
    todos = visible_todos()
    return ["html",
        ["head",
            ["meta", {"charset": "utf-8"}],
            ["meta", {"name": "viewport", "content": "width=device-width, initial-scale=1"}],
            ["title", "TodoMVC — evaleval"]],
        ["body", {"style": "font-family:system-ui,sans-serif; max-width:550px; margin:40px auto; padding:0 20px;"},
            ["h1", {"style": "text-align:center; font-weight:100; font-size:48px; color:#c33; margin-bottom:20px;"},
                "todos"],

            # add form
            ["form", {"method": "post", "action": "/add"},
                ["input", {"type": "text", "name": "text",
                           "placeholder": "What needs to be done?",
                           "autofocus": "true",
                           "style": "width:100%; padding:12px; font-size:16px; border:1px solid #ddd; border-radius:4px; box-sizing:border-box;"}]],

            # toggle all + list
            *([] if not TODOS else [
                ["div", {"style": "margin-top:12px;"},
                    ["form", {"method": "post", "action": "/toggle-all",
                              "style": "display:inline;"},
                        ["button", {"type": "submit"}, "Toggle all"]],
                    ["ul", {"style": "list-style:none; padding:0; margin:12px 0;"},
                        *[todo_item(t) for t in todos]]]]),

            # footer
            *([] if not TODOS else [
                ["div", {"style": "display:flex; justify-content:space-between; align-items:center; padding:12px 0; color:#666; font-size:14px;"},
                    ["span", f"{len(active_todos())} items left"],
                    ["div", {"style": "display:flex; gap:8px;"},
                        filter_btn("All", "all"),
                        filter_btn("Active", "active"),
                        filter_btn("Completed", "completed")],
                    *([] if not completed_todos() else [
                        ["form", {"method": "post", "action": "/clear-completed",
                                  "style": "display:inline;"},
                            ["button", {"type": "submit",
                                        "style": "cursor:pointer; border:none; background:none; color:#c33;"},
                                "Clear completed"]]])]])]]


def html_response():
    return HTMLResponse("<!DOCTYPE html>" + render(page()))


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.get("/", response_class=HTMLResponse)
async def index():
    return html_response()


@app.post("/add")
async def add(text: str = Form("")):
    text = text.strip()
    if text:
        TODOS.append({"id": uuid.uuid4().hex[:8], "text": text, "done": False})
    return RedirectResponse("/", status_code=303)


@app.post("/toggle/{todo_id}")
async def toggle(todo_id: str):
    for t in TODOS:
        if t["id"] == todo_id:
            t["done"] = not t["done"]
    return RedirectResponse("/", status_code=303)


@app.post("/delete/{todo_id}")
async def delete(todo_id: str):
    global TODOS
    TODOS = [t for t in TODOS if t["id"] != todo_id]
    return RedirectResponse("/", status_code=303)


@app.post("/toggle-all")
async def toggle_all():
    all_done = all(t["done"] for t in TODOS)
    for t in TODOS:
        t["done"] = not all_done
    return RedirectResponse("/", status_code=303)


@app.post("/filter/{filt}")
async def set_filter(filt: str):
    global FILTER
    if filt in ("all", "active", "completed"):
        FILTER = filt
    return RedirectResponse("/", status_code=303)


@app.post("/clear-completed")
async def clear_completed():
    global TODOS
    TODOS = [t for t in TODOS if not t["done"]]
    return RedirectResponse("/", status_code=303)


@app.get("/reset")
@app.post("/reset")
async def reset():
    global TODOS, FILTER
    TODOS = []
    FILTER = "all"
    return RedirectResponse("/", status_code=303)
