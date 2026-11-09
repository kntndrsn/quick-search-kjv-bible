import assert from 'node:assert/strict';
import test from 'node:test';
import { books } from '../src/books.js';
import { headingLabel, plainVerseText, styledParts } from '../src/format.js';
import {
  highlightPatternFor,
  normalize,
  normalizeVerseQuery,
  normalizeVerseText
} from '../src/normalize.js';
import { createReferenceParser } from '../src/reference.js';
import { searchNavigation } from '../src/repository.js';

const parser = createReferenceParser(books);

test('parses standard and ordinal references', () => {
  const john = parser.parse('John 3:16');
  assert.equal(john.book.name, 'John');
  assert.equal(john.chapter, 3);
  assert.equal(john.verse, 16);
  assert.equal(john.label, 'John 3:16');

  const samuel = parser.parse('First Samuel 3');
  assert.equal(samuel.book.name, '1 Samuel');
  assert.equal(samuel.chapter, 3);
  assert.equal(samuel.verse, null);

  const timothy = parser.parse('II Timothy 1:7');
  assert.equal(timothy.book.name, '2 Timothy');
  assert.equal(timothy.chapter, 1);
  assert.equal(timothy.verse, 7);
});

test('normalizes verse text the same way as the Android app', () => {
  assert.equal(
    normalizeVerseText('The \u2E22Lord\u2E23 [is] my shepherd; I shall not want.'),
    'the lord is my shepherd i shall not want'
  );
  assert.equal(normalizeVerseText("LORD's house"), 'lord s house');
  assert.equal(normalizeVerseText('\u2039a\u203Ab'), 'ab');
  assert.equal(normalizeVerseText('the \u2E22Lord\u2E23 \u2E22God\u2E23'), 'the lord god');
  assert.equal(
    normalizeVerseText('C\u00E6sar \u00C6non Tubal\u2013cain joint-heirs'),
    'caesar aenon tubalcain jointheirs'
  );
  assert.equal(normalizeVerseQuery('CAESAR Tubal-cain'), 'caesar tubalcain');
  assert.equal(normalizeVerseQuery('I can do all things'), 'i can do all things');
  assert.equal(normalize('1 John 3:16'), '1 john 3:16');
});

test('highlight pattern finds folded spellings', () => {
  const pattern = new RegExp(
    ['tubalcain', 'caesar', 'aenon'].map(highlightPatternFor).join('|'),
    'g'
  );
  const text = 'Tubal\u2013cain and C\u00E6sar at \u00C6non; Caesar';
  assert.deepEqual(
    [...text.matchAll(pattern)].map((match) => match[0]),
    ['Tubal\u2013cain', 'C\u00E6sar', '\u00C6non', 'Caesar']
  );
});

test('plain and styled verse text', () => {
  assert.equal(
    plainVerseText('The \u2E22Lord\u2E23 [is] my shepherd, \u2039said Jesus\u203A'),
    'The LORD is my shepherd, said Jesus'
  );
  assert.equal(headingLabel({ letter: '\u05D0', text: 'ALEPH.' }), '\u05D0 ALEPH.');
  const parts = styledParts('\u2039The \u2E22Lord\u2E23 [is] here.\u203A Amen');
  assert.equal(parts.map((part) => part.text).join(''), 'The LORD is here. Amen');
  const lord = parts.find((part) => part.text === 'ORD');
  assert.equal(lord.small, true);
  assert.equal(lord.christ, true);
  assert.equal(styledParts('\u2E22Lord\'s\u2E23').map((part) => part.text).join(''), "LORD'S");
});

test('book search finds phonetic john', () => {
  const result = searchNavigation('Jhn');
  assert.ok(result.matchingBooks.some((book) => book.name === 'John'));
});
