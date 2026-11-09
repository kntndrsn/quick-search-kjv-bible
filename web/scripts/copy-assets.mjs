import { copyFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const publicDir = resolve(webRoot, 'public');
mkdirSync(publicDir, { recursive: true });

copyFileSync(
  resolve(webRoot, '../app/src/main/assets/kjv.db'),
  resolve(publicDir, 'kjv.db')
);
copyFileSync(
  resolve(webRoot, 'node_modules/sql.js/dist/sql-wasm-browser.wasm'),
  resolve(publicDir, 'sql-wasm-browser.wasm')
);
