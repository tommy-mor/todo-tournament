async function _submit(form) {
  const r = await fetch(form.action || '/', {
    method: 'POST',
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: new URLSearchParams(new FormData(form))
  });
  const t = await r.text();
  if (t && t.trim()) eval(t);
  // autofocus is only processed by browsers on initial page load, not DOM updates
  const af = document.querySelector('[autofocus]');
  if (af && af !== document.activeElement) af.focus();
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
