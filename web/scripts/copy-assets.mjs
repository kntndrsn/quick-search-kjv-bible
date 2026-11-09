import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const result = spawnSync(process.env.PYTHON || 'python', [resolve(webRoot, 'scripts/export-data.py')], {
  stdio: 'inherit'
});
if (result.error) throw result.error;
if (result.status !== 0) process.exit(result.status || 1);
