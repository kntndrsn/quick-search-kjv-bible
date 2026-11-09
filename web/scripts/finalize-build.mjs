import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const dist = resolve(dirname(fileURLToPath(import.meta.url)), '../dist');
const files = ['index.html', 'manifest.webmanifest', 'favicon.svg', 'data/manifest.json', 'data/manifest.json.gz'];
for (const name of readdirSync(resolve(dist, 'assets'))) files.push(`assets/${name}`);
const entries = files.map((path) => {
  const bytes = readFileSync(resolve(dist, path));
  return { path, bytes: bytes.length, hash: createHash('sha256').update(bytes).digest('hex') };
});
const version = createHash('sha256').update(JSON.stringify(entries)).digest('hex').slice(0, 20);
const shell = { version, cache: `kjv-reader-shell-${version}`, files: entries };
const sw = readFileSync(resolve(dist, 'sw.js'), 'utf8');
writeFileSync(resolve(dist, 'sw.js'), `const SHELL = ${JSON.stringify(shell)};\n${sw}`);
