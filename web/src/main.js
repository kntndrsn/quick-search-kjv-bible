import { applyThemeClass, loadPrefs, mountApp } from './app.js';
import { assetUrl } from './data.js';

import { setOfflineRegistration } from './offline.js';

const root = document.getElementById('app');
applyThemeClass(loadPrefs().theme || 'system');
mountApp(root);

if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  const registration = navigator.serviceWorker.register(assetUrl('sw.js'));
  setOfflineRegistration(registration);
  registration.catch((error) => {
    console.error('Offline installation failed', error);
    window.dispatchEvent(new CustomEvent('offline-error', {
      detail: 'Offline installation failed. Reconnect and reload to retry.'
    }));
  });
}
