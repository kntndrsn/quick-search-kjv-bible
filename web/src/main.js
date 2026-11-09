import initSqlJs from 'sql.js';
import { applyThemeClass, loadPrefs, mountApp } from './app.js';
import { attachDatabase } from './repository.js';

const root = document.getElementById('app');
const prefs = loadPrefs();
applyThemeClass(prefs.theme || 'system');

root.innerHTML = `
  <div class="loading">
    <h1>Quick Search KJV Bible</h1>
    <p id="load-status">Loading the Bible…</p>
    <progress id="load-progress" max="1" value="0"></progress>
  </div>
`;

boot().catch((error) => {
  const status = document.getElementById('load-status');
  if (status) status.textContent = 'The Bible could not be loaded. Reopen the page while online once, then it will stay on this device.';
  console.error(error);
});

async function boot() {
  await activateServiceWorker();
  const [SQL, bytes] = await Promise.all([
    initSqlJs({ locateFile: (file) => `/${file}` }),
    fetchDatabase()
  ]);
  attachDatabase(new SQL.Database(bytes));
  mountApp(root);
  await rememberOffline(bytes);
}

async function activateServiceWorker() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return;
  await navigator.serviceWorker.register('/sw.js');
  if (navigator.serviceWorker.controller) return;
  await new Promise((resolve) => {
    const timeout = setTimeout(resolve, 4000);
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      clearTimeout(timeout);
      resolve();
    }, { once: true });
  });
}

async function rememberOffline(bytes) {
  if (!('caches' in window)) return;
  const cache = await caches.open('kjv-web-v1');
  await cache.put('/kjv.db', new Response(bytes.slice(), {
    headers: { 'Content-Type': 'application/octet-stream' }
  }));
  const paths = new Set([
    '/',
    '/index.html',
    '/manifest.webmanifest',
    '/favicon.svg',
    '/sw.js',
    '/sql-wasm-browser.wasm'
  ]);
  for (const entry of performance.getEntriesByType('resource')) {
    const url = new URL(entry.name);
    if (url.origin === location.origin && url.pathname !== '/kjv.db') paths.add(`${url.pathname}${url.search}`);
  }
  await Promise.all([...paths].map(async (path) => {
    try {
      const response = await fetch(path);
      if (response.ok) await cache.put(path, response);
    } catch {
      // The database bytes are already stored. A later online visit fills anything missed.
    }
  }));
}

async function fetchDatabase() {
  const response = await fetch('/kjv.db');
  if (!response.ok || !response.body) {
    if (!response.ok) throw new Error(`kjv.db ${response.status}`);
    return new Uint8Array(await response.arrayBuffer());
  }
  const total = Number(response.headers.get('content-length')) || 0;
  const reader = response.body.getReader();
  const chunks = [];
  let received = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    received += value.length;
    const progress = document.getElementById('load-progress');
    const status = document.getElementById('load-status');
    if (progress && total) {
      progress.max = total;
      progress.value = received;
    }
    if (status && total) {
      status.textContent = `Loading the Bible… ${Math.round((received / total) * 100)}%`;
    }
  }
  const bytes = new Uint8Array(received);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.length;
  }
  return bytes;
}
