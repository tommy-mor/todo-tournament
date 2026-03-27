import { Idiomorph } from 'https://esm.sh/idiomorph@0.7.3';
window.Idiomorph = Idiomorph;

const es = new EventSource('/sse');
es.addEventListener('exec', e => eval(e.data));

document.addEventListener('submit', async e => {
  e.preventDefault();
  const r = await fetch(e.target.action || '/', {
    method: 'POST',
    body: new FormData(e.target)
  });
  const t = await r.text();
  if (t && t.trim()) eval(t);
});
