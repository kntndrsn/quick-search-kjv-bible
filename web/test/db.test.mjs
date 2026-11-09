import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import initSqlJs from 'sql.js';
import { plainVerseText } from '../src/format.js';
import { attachDatabase, loadChapter, searchHeadingText, searchVerseText } from '../src/repository.js';

test('existing fts4 database serves chapters and search', async () => {
  const SQL = await initSqlJs();
  const bytes = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../../app/src/main/assets/kjv.db'));
  attachDatabase(new SQL.Database(bytes));

  const psalm = loadChapter(19, 23);
  assert.equal(psalm[0].headings[0].text, 'A Psalm of David.');
  assert.ok(psalm.slice(1).every((verse) => verse.headings.length === 0));

  const john = loadChapter(43, 3);
  const verse16 = john.find((verse) => verse.verse === 16);
  assert.match(verse16.text, /loved the world/);

  const faith = searchVerseText('faith');
  assert.ok(faith.length > 0);
  assert.ok(faith.length <= 25);

  const things = searchVerseText('I can do all things');
  assert.ok(things.some((verse) => verse.bookId === 50 && verse.chapter === 4 && verse.verse === 13));

  const shepherd = searchVerseText('lord is my shepherd');
  assert.ok(shepherd.some((verse) => verse.bookId === 19 && verse.chapter === 23 && verse.verse === 1));

  const headings = searchHeadingText('psalm of david');
  assert.equal(headings.length, 10);
  assert.ok(headings.every((heading) => heading.type === 'psalm' && /psalm of david/i.test(plainVerseText(heading.text))));
  assert.deepEqual(searchHeadingText('ps'), []);
});
