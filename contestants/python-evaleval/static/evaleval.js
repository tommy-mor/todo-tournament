// Self-contained evaleval client — no external dependencies

function _morph(sel, html) {
  const el = document.querySelector(sel);
  if (!el) return;
  // parse the new html into a real element and swap
  const tmp = document.createElement('div');
  tmp.innerHTML = html;
  const next = tmp.firstElementChild;
  if (next) el.replaceWith(next);
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
  const input = document.querySelector('#add-form input[name=new-todo]');
  if (input) input.focus();
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
