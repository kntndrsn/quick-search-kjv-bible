import { assetUrl, fetchPayload, getManifest, representation, supportsGzip, verifiedBytes } from './data.js';

let registrationPromise;
export function setOfflineRegistration(promise) {
  registrationPromise = promise;
}

async function installedShell() {
  if (!registrationPromise) throw new Error('Full offline download requires the production site over HTTPS or localhost.');
  const registration = await registrationPromise;
  const worker = registration.active || registration.installing || registration.waiting;
  if (!worker) throw new Error('Offline worker unavailable. Reload to retry.');
  if (worker.state !== 'activated') {
    await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => finish(new Error('Offline installation timed out. Reload to retry.')), 60000);
      const finish = (error) => {
        clearTimeout(timeout);
        worker.removeEventListener('statechange', changed);
        if (error) reject(error);
        else resolve();
      };
      const changed = () => {
        if (worker.state === 'activated') finish();
        if (worker.state === 'redundant') finish(new Error('Offline installation failed. Reload to retry.'));
      };
      worker.addEventListener('statechange', changed);
      changed();
    });
  }
  return new Promise((resolve, reject) => {
    const channel = new MessageChannel();
    const timeout = setTimeout(() => {
      channel.port1.close();
      reject(new Error('Offline worker did not respond. Reload to retry.'));
    }, 10000);
    channel.port1.onmessage = ({ data }) => {
      clearTimeout(timeout);
      channel.port1.close();
      resolve(data);
    };
    worker.postMessage('shell', [channel.port2]);
  });
}

export async function downloadBible(manifest, { signal, onProgress = () => {} } = {}) {
  const gzip = supportsGzip();
  const files = [...Object.values(manifest.chapters), manifest.search];
  const total = files.reduce((sum, file) => sum + representation(file, gzip).bytes, 0);
  let completed = 0;
  let cursor = 0;
  let failed;
  onProgress(0, total);
  const run = async () => {
    while (cursor < files.length && !failed) {
      const file = files[cursor++];
      try {
        if (signal?.aborted) throw new DOMException('Download cancelled', 'AbortError');
        await fetchPayload(file, manifest.version, { signal, gzip });
        completed += representation(file, gzip).bytes;
        onProgress(completed, total);
      } catch (error) {
        failed = error;
      }
    }
  };
  await Promise.all(Array.from({ length: 4 }, run));
  if (failed) throw failed;
  if (signal?.aborted) throw new DOMException('Download cancelled', 'AbortError');
  return { total, gzip };
}

export function mountOfflineControl(parent) {
  const panel = document.createElement('section');
  panel.className = 'offline-control';
  panel.innerHTML = `
    <button type="button" class="download">Download entire Bible</button>
    <button type="button" class="cancel" hidden>Cancel</button>
    <progress max="1" value="0" hidden aria-label="Bible download progress"></progress>
    <p role="status">Visited chapters are cached automatically. Download everything for offline reading and search. Browser storage can be cleared.</p>
  `;
  parent.append(panel);
  const button = panel.querySelector('.download');
  const cancel = panel.querySelector('.cancel');
  const progress = panel.querySelector('progress');
  const status = panel.querySelector('[role="status"]');
  let controller;
  window.addEventListener('offline-error', ({ detail }) => { status.textContent = detail; });
  cancel.addEventListener('click', () => controller?.abort());
  button.addEventListener('click', async () => {
    controller = new AbortController();
    button.disabled = true;
    cancel.hidden = false;
    progress.hidden = false;
    status.textContent = 'Preparing offline download...';
    try {
      const shell = await installedShell();
      const shellCache = await caches.open(shell.cache);
      for (const entry of shell.files) {
        const response = await shellCache.match(assetUrl(entry.path));
        if (!response) {
          throw new Error('Offline app files are missing. Reconnect and reload before downloading.');
        }
        await verifiedBytes(response, entry);
      }
      if (navigator.storage?.persist) await navigator.storage.persist();
      const manifest = await getManifest();
      const marker = assetUrl(`data/${manifest.version}/offline-complete-${shell.version}`);
      const cache = await caches.open(`kjv-reader-data-${manifest.version}`);
      await cache.delete(marker);
      await downloadBible(manifest, {
        signal: controller.signal,
        onProgress: (done, total) => {
          progress.max = total;
          progress.value = done;
          status.textContent = `Downloading Bible: ${Math.round(done / total * 100)}% (${(done / 1048576).toFixed(1)} / ${(total / 1048576).toFixed(1)} MB)`;
        }
      });
      await cache.put(marker, new Response('complete'));
      status.textContent = 'Entire Bible and search verified for offline use. Browser storage can still be cleared.';
      button.textContent = 'Verify offline download';
    } catch (error) {
      console.error(error);
      status.textContent = error.name === 'AbortError'
        ? 'Download cancelled. Download again to resume using already cached files.'
        : `Download incomplete: ${error.message} Already cached files will be reused on retry.`;
      button.textContent = 'Retry full download';
    } finally {
      button.disabled = false;
      cancel.hidden = true;
    }
  });
  queueMicrotask(async () => {
    if (!registrationPromise || typeof caches === 'undefined') return;
    try {
      const shell = await installedShell();
      const manifest = await getManifest();
      const cache = await caches.open(`kjv-reader-data-${manifest.version}`);
      const marker = assetUrl(`data/${manifest.version}/offline-complete-${shell.version}`);
      if (!controller && await cache.match(marker)) {
        status.textContent = 'A full offline download was completed previously. Verify it to check for missing files. Browser storage can be cleared.';
        button.textContent = 'Verify offline download';
      }
    } catch (error) {
      console.error('Offline status unavailable', error);
      if (!controller) status.textContent = `Offline status unavailable: ${error.message}`;
    }
  });
}
