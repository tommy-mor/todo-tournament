import { Idiomorph } from 'https://esm.sh/idiomorph@0.7.3';
window.Idiomorph = Idiomorph;

const es = new EventSource('/sse');
es.addEventListener('exec', e => eval(e.data));

async function submitForm(form) {
  const r = await fetch(form.action || '/', { method: 'POST', body: new FormData(form) });
  const t = await r.text();
  if (t && t.trim()) eval(t);
}

document.addEventListener('submit', async e => {
  e.preventDefault();
  await submitForm(e.target);
});

document.addEventListener('change', async e => {
  if (e.target.type === 'checkbox') {
    const form = e.target.closest('form');
    if (form) await submitForm(form);
  }
});
