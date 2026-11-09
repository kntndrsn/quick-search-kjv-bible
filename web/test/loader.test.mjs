import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { webcrypto } from 'node:crypto';
import test from 'node:test';
import { fetchPayload, getManifest, readPayload, representation, supportsGzip } from '../src/data.js';
import { loadChapter } from '../src/repository.js';
import { downloadBible } from '../src/offline.js';

globalThis.crypto ||= webcrypto;
const cacheStores = new Map();
globalThis.caches = {
  async open(name) {
    if (!cacheStores.has(name)) cacheStores.set(name, new Map());
    const store = cacheStores.get(name);
    return {
      async match(key) { return store.get(key)?.clone(); },
      async put(key, response) { store.set(key, response.clone()); },
      async delete(key) { return store.delete(key); }
    };
  }
};
let requests = [];
let offline = false;
let corruptPath;
globalThis.fetch = async (url, options = {}) => {
  requests.push(url);
  if (options.signal?.aborted) throw new DOMException('Cancelled', 'AbortError');
  if (offline) throw new TypeError('Network offline');
  const path = new URL(url).pathname.replace(/^\//, '');
  if (url === corruptPath) return new Response('corrupt');
  try {
    return new Response(readFileSync(new URL(`../public/${path}`, import.meta.url)));
  } catch {
    return new Response('Missing', { status: 404 });
  }
};

test('chapter requests are deduplicated and do not request the search corpus', async () => {
  const [first, second] = await Promise.all([loadChapter(43, 3), loadChapter(43, 3)]);
  assert.equal(first, second);
  assert.match(first[15].text, /loved the world/);
  assert.equal(requests.filter((url) => url.includes('43-3.json')).length, 1);
  assert.ok(!requests.some((url) => url.includes('/search.json')));
  assert.equal(requests.filter((url) => url.includes('manifest')).length, 1);
});

test('gzip and compatibility JSON decode identically and corrupted data fails explicitly', async () => {
  const manifest = await getManifest();
  const descriptor = manifest.chapters['19-23'];
  const gzip = await readPayload(descriptor, manifest.version, { gzip: true });
  const plain = await readPayload(descriptor, manifest.version, { gzip: false });
  assert.deepEqual(gzip, plain);
  assert.equal(gzip[0].headings[0].text, 'A Psalm of David.');
  const bad = manifest.chapters['1-2'];
  corruptPath = representation(bad).url;
  const before = requests.length;
  await assert.rejects(readPayload(bad, manifest.version), /size mismatch/);
  assert.equal(requests.length, before + 1, 'no success-shaped fallback request');
  corruptPath = null;
  await readPayload(bad, manifest.version);
  const cache = await caches.open(`kjv-reader-data-${manifest.version}`);
  await cache.put(representation(bad).url, new Response('broken cached bytes'));
  await assert.rejects(readPayload(bad, manifest.version), /size mismatch/);
  assert.equal(await cache.match(representation(bad).url), undefined);
  await readPayload(bad, manifest.version);
  await assert.rejects(loadChapter(99, 1), /No chapter/);
});

test('cancelled downloads remain incomplete; resuming verifies every chapter and offline search data', async () => {
  const manifest = await getManifest();
  const controller = new AbortController();
  await assert.rejects(downloadBible(manifest, {
    signal: controller.signal,
    onProgress(done) { if (done) controller.abort(); }
  }), { name: 'AbortError' });
  let completed;
  const result = await downloadBible(manifest, {
    onProgress(done, total) { completed = { done, total }; }
  });

  assert.equal(completed.done, completed.total);
  assert.equal(result.total, completed.total);
  offline = true;
  for (const descriptor of [...Object.values(manifest.chapters), manifest.search]) {
    await fetchPayload(descriptor, manifest.version);
  }
  const search = await readPayload(manifest.search, manifest.version);
  assert.equal(search.verses.length, 31102);
  assert.equal(Object.keys(manifest.chapters).length, 1189);
  const cache = await caches.open(`kjv-reader-data-${manifest.version}`);
  await cache.delete(representation(manifest.chapters['66-22']).url);
  await assert.rejects(readPayload(manifest.chapters['66-22'], manifest.version), /Network offline/);
  offline = false;
});

test('browsers without native gzip decoding select compatibility JSON', async () => {
  const native = globalThis.DecompressionStream;
  try {
    globalThis.DecompressionStream = undefined;
    assert.equal(supportsGzip(), false);
    const manifest = await getManifest();
    const descriptor = manifest.chapters['1-3'];
    assert.ok(representation(descriptor).url.endsWith('.json'));
    const verses = await readPayload(descriptor, manifest.version);
    assert.equal(verses[0].chapter, 3);
  } finally {
    globalThis.DecompressionStream = native;
  }
});
