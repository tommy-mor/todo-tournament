// Self-contained evaleval client — no external dependencies

function _morph(sel, html) {
  const el = document.querySelector(sel);
  if (!el) return;
  const tmp = document.createElement('div');
  tmp.innerHTML = html;
  const next = tmp.firstElementChild;
  if (!next) return;
  el.replaceWith(next);
  const af = next.querySelector('[autofocus]') || (next.hasAttribute('autofocus') ? next : null);
  if (af) af.focus();
}
window._morph = _morph;

async function _submit(form) {
  const r = await fetch(form.action || '/', {
    method: 'POST',
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: new URLSearchParams(new FormData(form))
  });
  const t = await r.text();
  if (t && t.trim()) eval(t);
}

document.addEventListener('submit', async e => {
  e.preventDefault();
  await _submit(e.target);
});

document.addEventListener('change', async e => {
  if (e.target.type === 'checkbox') {
    const form = e.target.closest('form');
    if (form) await _submit(form);
  }
});
