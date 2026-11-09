import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { gunzipSync } from 'node:zlib';
import test from 'node:test';
import { searchData } from '../src/search-engine.js';

const manifest = JSON.parse(readFileSync(new URL('../public/data/manifest.json', import.meta.url)));
const data = JSON.parse(gunzipSync(readFileSync(new URL(`../public/${manifest.search.path}.gz`, import.meta.url))));

test('all chapters, headings, styling, gzip payloads and searches match Android SQLite', () => {
  const queries = [
    'faith', 'i can do all things', 'lord is my shepherd', 'psalm of david',
    'ps', 'i can do all thin', 'caesar', 'tubalcain', 'aenon', 'jointheirs',
    'lord s house', 'merc', 'righteousness', 'ove', 'the world loved', 'aleph'
  ];
  const result = spawnSync(process.env.PYTHON || 'python', [
    'scripts/verify-data.py', JSON.stringify(queries)
  ], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  const fixtures = JSON.parse(result.stdout);
  for (const query of queries) assert.deepEqual(searchData(data, query), fixtures[query], query);
});

test('compressed payloads are smaller and chapter reading is independent of search', () => {
  assert.equal(Object.keys(manifest.chapters).length, 1189);
  const genesis = manifest.chapters['1-1'];
  assert.ok(genesis.gzipBytes < 5000);
  assert.ok(manifest.search.gzipBytes < manifest.search.bytes / 2);
  assert.ok(manifest.search.gzipBytes < 17620992 / 3);
});
