// SHELL is injected by the production build, including hashed worker assets.
const scope = self.registration.scope;
const urlFor = (path) => new URL(path, scope).href;

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(SHELL.cache);
    await Promise.all(SHELL.files.map(async (file) => {
      const response = await fetch(urlFor(file.path), { cache: 'reload' });
      if (!response.ok) throw new Error(`Offline install failed: ${file.path}`);
      const bytes = await response.clone().arrayBuffer();
      const digest = await crypto.subtle.digest('SHA-256', bytes);
      const hash = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
      if (hash !== file.hash) throw new Error(`Offline install integrity failure: ${file.path}`);
      await cache.put(urlFor(file.path), response);
    }));
  })());
});

self.addEventListener('activate', (event) => {
  // Keep older offline sets intact for tabs still using them.
  event.waitUntil(self.clients.claim());
});

self.addEventListener('message', (event) => {
  if (event.data === 'shell') event.ports[0]?.postMessage(SHELL);
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET' || !request.url.startsWith(scope)) return;
  const url = new URL(request.url);
  const relative = url.pathname.slice(new URL(scope).pathname.length);
  if (relative.startsWith('data/') && !['data/manifest.json', 'data/manifest.json.gz'].includes(relative)) {
    // Payloads are verified and cached by the reader, never replaced with HTML.
    return;
  }
  event.respondWith((async () => {
    const cache = await caches.open(SHELL.cache);
    const shell = await cache.match(request, { ignoreSearch: true });
    if (shell) return shell;
    if (request.mode === 'navigate') {
      const index = await cache.match(urlFor('index.html'));
      if (index) return index;
    }
    try {
      return await fetch(request);
    } catch {
      return new Response('Offline asset unavailable', { status: 503 });
    }
  })());
});
