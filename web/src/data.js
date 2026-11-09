export const BASE = import.meta.env?.BASE_URL || './';
let manifestPromise;

export function assetUrl(path) {
  return new URL(`${BASE}${path}`, globalThis.location?.href || 'http://localhost/').href;
}

export function getManifest() {
  if (!manifestPromise) {
    const gzip = supportsGzip();
    manifestPromise = fetch(assetUrl(`data/manifest.json${gzip ? '.gz' : ''}`), { cache: 'no-cache' })
      .then(async (response) => {
        if (!response.ok) throw new Error(`Bible manifest: HTTP ${response.status}`);
        const stream = gzip ? response.body.pipeThrough(new DecompressionStream('gzip')) : response.body;
        const manifest = await new Response(stream).json();
        if (!manifest.version || !manifest.chapters || !manifest.search) {
          throw new Error('Invalid Bible manifest');
        }
        return manifest;
      }).catch((error) => {
        manifestPromise = null;
        throw error;
      });
  }
  return manifestPromise;
}

export function supportsGzip() {
  if (typeof DecompressionStream === 'undefined') return false;
  try {
    new DecompressionStream('gzip');
    return true;
  } catch {
    return false;
  }
}

export function representation(descriptor, gzip = supportsGzip()) {
  return {
    url: assetUrl(descriptor.path + (gzip ? '.gz' : '')),
    bytes: gzip ? descriptor.gzipBytes : descriptor.bytes,
    hash: gzip ? descriptor.gzipSha256 : descriptor.sha256,
    gzip
  };
}

export async function verifiedBytes(response, expected) {
  const bytes = await response.arrayBuffer();
  if (bytes.byteLength !== expected.bytes) throw new Error('Bible data size mismatch');
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  const hash = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
  if (hash !== expected.hash) throw new Error('Bible data integrity check failed');
  return bytes;
}

export async function fetchPayload(descriptor, version, { signal, gzip = supportsGzip() } = {}) {
  const expected = representation(descriptor, gzip);
  const cache = typeof caches !== 'undefined' ? await caches.open(`kjv-reader-data-${version}`) : null;
  const cached = await cache?.match(expected.url);
  let bytes;
  if (cached) {
    try {
      bytes = await verifiedBytes(cached, expected);
    } catch (error) {
      console.warn('Invalid cached Bible data; fetching a verified replacement', expected.url, error);
      await cache.delete(expected.url);
    }
  }
  if (!bytes) {
    const response = await fetch(expected.url, { signal, cache: 'no-cache' });
    if (!response.ok) throw new Error(`Bible data unavailable: HTTP ${response.status}`);
    bytes = await verifiedBytes(response.clone(), expected);
    if (signal?.aborted) throw new DOMException('Download cancelled', 'AbortError');
    if (cache) await cache.put(expected.url, response);
  }
  if (signal?.aborted) throw new DOMException('Download cancelled', 'AbortError');
  return { bytes, gzip };
}

export async function readPayload(descriptor, version, options) {
  const payload = await fetchPayload(descriptor, version, options);
  const stream = new Blob([payload.bytes]).stream();
  const decoded = payload.gzip ? stream.pipeThrough(new DecompressionStream('gzip')) : stream;
  return JSON.parse(await new Response(decoded).text());
}
