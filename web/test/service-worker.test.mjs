import assert from 'node:assert/strict';
import { createHash, webcrypto } from 'node:crypto';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';

test('service worker verifies its shell, pins manifests, and never intercepts privacy or data failures', async () => {
  const scope = 'https://example.test/project/bible/';
  const body = '<html>reader</html>';
  const shell = {
    cache: 'kjv-reader-shell-test',
    files: ['index.html', 'data/manifest.json.gz'].map((path) => ({
      path, bytes: Buffer.byteLength(body),
      hash: createHash('sha256').update(body).digest('hex')
    }))
  };
  const handlers = new Map();
  const stores = new Map([['unrelated-site-cache', new Map()]]);
  let offline = false;
  let corrupt = false;
  let claimed = false;
  const context = vm.createContext({
    SHELL: shell, URL, Response, crypto: webcrypto, setTimeout,
    self: {
      registration: { scope },
      clients: { async claim() { claimed = true; } },
      addEventListener(name, handler) { handlers.set(name, handler); }
    },
    caches: {
      async open(name) {
        if (!stores.has(name)) stores.set(name, new Map());
        const entries = stores.get(name);
        return {
          async put(url, response) { entries.set(url, response.clone()); },
          async match(request) {
            const url = typeof request === 'string' ? request : request.url;
            return entries.get(url.split('?')[0])?.clone();
          }
        };
      }
    },
    fetch: async () => {
      if (offline) throw new Error('Network offline');
      return new Response(corrupt ? 'bad' : body);
    }
  });
  vm.runInContext(readFileSync(new URL('../public/sw.js', import.meta.url), 'utf8'), context);
  let pending;
  handlers.get('install')({ waitUntil(promise) { pending = promise; } });
  await pending;
  handlers.get('activate')({ waitUntil(promise) { pending = promise; } });
  await pending;
  assert.equal(claimed, true);
  assert.ok(stores.has('unrelated-site-cache'));

  const request = async (path, mode = 'cors') => {
    let response;
    handlers.get('fetch')({
      request: { method: 'GET', url: path, mode },
      respondWith(promise) { response = promise; }
    });
    return response ? await response : null;
  };
  offline = true;
  assert.equal(await request('https://example.test/project/privacy.html', 'navigate'), null);
  assert.equal(await request(scope + 'data/version/chapters/1-1.json.gz'), null);
  assert.equal(await (await request(scope + 'data/manifest.json.gz')).text(), body);
  assert.equal(await (await request(scope, 'navigate')).text(), body);
  assert.equal((await request(scope + 'missing.js')).status, 503);
  offline = false;
  corrupt = true;
  handlers.get('install')({ waitUntil(promise) { pending = promise; } });
  await assert.rejects(pending, /integrity failure/);
});
