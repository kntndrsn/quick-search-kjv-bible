import { bookById, books } from './books.js';
import {
  buildFtsMatchQuery,
  compactNormalized,
  normalize,
  normalizeVerseQuery,
  soundex,
  soundexOfCompact
} from './normalize.js';
import { createReferenceParser } from './reference.js';

export const MAX_VERSE_RESULTS = 25;
export const MAX_HEADING_RESULTS = 10;
export const MAX_BOOK_RESULTS = 6;
export const MIN_VERSE_QUERY_LENGTH = 3;
const CHAPTER_CACHE_SIZE = 8;

const FTS_QUERY =
  'SELECT v.book_id, v.chapter, v.verse, v.text FROM verses_fts4 f ' +
  'JOIN verses v ON v.rowid = f.docid WHERE verses_fts4 MATCH ? LIMIT 25';
const LIKE_QUERY =
  'SELECT book_id, chapter, verse, text FROM verses WHERE text_norm LIKE ? LIMIT 25';
const HEADING_FTS_QUERY =
  'SELECT h.book_id, h.chapter, h.verse, h.type, h.letter, h.text, h.id FROM headings_fts4 f ' +
  'JOIN headings h ON h.id = f.docid WHERE headings_fts4 MATCH ? ORDER BY h.id LIMIT 10';
const HEADING_LIKE_QUERY =
  'SELECT book_id, chapter, verse, type, letter, text, id FROM headings ' +
  'WHERE text_norm LIKE ? ORDER BY id LIMIT 10';

const parser = createReferenceParser(books);

const searchIndex = books.map((book) => {
  const candidates = [...new Set([...book.aliases, book.name, book.shortName])];
  return {
    book,
    aliases: candidates.map((alias) => {
      const normalized = normalize(alias);
      const compact = compactNormalized(normalized);
      return { normalized, compact, soundex: soundexOfCompact(compact) };
    })
  };
});

let database = null;
const chapterCache = new Map();

export function attachDatabase(db) {
  database = db;
  chapterCache.clear();
}

function rows(sql, params) {
  const stmt = database.prepare(sql);
  stmt.bind(params);
  const out = [];
  while (stmt.step()) out.push(stmt.getAsObject());
  stmt.free();
  return out;
}

function remember(key, value) {
  if (chapterCache.has(key)) chapterCache.delete(key);
  chapterCache.set(key, value);
  if (chapterCache.size > CHAPTER_CACHE_SIZE) {
    const oldest = chapterCache.keys().next().value;
    chapterCache.delete(oldest);
  }
  return value;
}

export function loadChapter(bookId, chapter) {
  const key = `${bookId}:${chapter}`;
  const cached = chapterCache.get(key);
  if (cached) return remember(key, cached);

  const headings = rows(
    'SELECT book_id, chapter, verse, type, letter, text FROM headings WHERE book_id = ? AND chapter = ? ORDER BY id',
    [bookId, chapter]
  );
  const byVerse = new Map();
  for (const heading of headings) {
    const list = byVerse.get(heading.verse) || [];
    list.push({
      bookId: heading.book_id,
      chapter: heading.chapter,
      verse: heading.verse,
      type: heading.type,
      letter: heading.letter,
      text: heading.text
    });
    byVerse.set(heading.verse, list);
  }
  const verses = rows(
    'SELECT verse, text FROM verses WHERE book_id = ? AND chapter = ? ORDER BY verse',
    [bookId, chapter]
  ).map((row) => ({
    bookId,
    chapter,
    verse: row.verse,
    text: row.text,
    headings: byVerse.get(row.verse) || []
  }));
  if (verses.length === 0) throw new Error(`No verses for book ${bookId} chapter ${chapter}`);
  return remember(key, verses);
}

function matchBooks(normalized, compact) {
  const querySoundex = soundexOfCompact(compact);
  const normalizedSoundex = soundex(normalized);
  const matches = [];
  for (const entry of searchIndex) {
    let matched = false;
    let best = Number.MAX_SAFE_INTEGER;
    for (const alias of entry.aliases) {
      if (!matched) {
        matched = alias.normalized.includes(normalized) ||
          alias.compact.includes(compact) ||
          alias.soundex === querySoundex;
      }
      let score = 4;
      if (alias.normalized === normalized || alias.compact === compact) score = 0;
      else if (alias.normalized.startsWith(normalized) || alias.compact.startsWith(compact)) score = 1;
      else if (alias.normalized.includes(normalized) || alias.compact.includes(compact)) score = 2;
      else if (alias.soundex === normalizedSoundex) score = 3;
      if (score < best) best = score;
    }
    if (matched) matches.push({ book: entry.book, score: best });
  }
  matches.sort((a, b) => a.score - b.score || a.book.id - b.book.id);
  return matches.slice(0, MAX_BOOK_RESULTS).map((match) => match.book);
}

export function searchNavigation(query) {
  const normalized = normalize(query);
  const verseQuery = normalizeVerseQuery(query);
  if (!normalized.trim() && !verseQuery.trim()) {
    return { referenceMatch: null, matchingBooks: [], matchingVerses: [], matchingHeadings: [] };
  }
  const compact = compactNormalized(normalized);
  return {
    referenceMatch: parser.parseNormalized(normalized, compact),
    matchingBooks: matchBooks(normalized, compact),
    matchingVerses: [],
    matchingHeadings: []
  };
}

function verseKey(verse) {
  return `${verse.bookId}:${verse.chapter}:${verse.verse}`;
}

function readVerse(row) {
  return {
    bookId: row.book_id,
    chapter: row.chapter,
    verse: row.verse,
    text: row.text,
    headings: []
  };
}

function readHeading(row) {
  return {
    bookId: row.book_id,
    chapter: row.chapter,
    verse: row.verse,
    type: row.type,
    letter: row.letter,
    text: row.text,
    id: row.id
  };
}

export function searchVerseText(query) {
  const normalized = normalizeVerseQuery(query);
  if (normalized.length < MIN_VERSE_QUERY_LENGTH || !database) return [];
  const match = buildFtsMatchQuery(normalized);
  const verses = new Map();
  if (match) {
    for (const row of rows(FTS_QUERY, [match])) {
      const verse = readVerse(row);
      if (!verses.has(verseKey(verse))) verses.set(verseKey(verse), verse);
    }
  }
  if (verses.size < MAX_VERSE_RESULTS) {
    for (const row of rows(LIKE_QUERY, [`%${normalized}%`])) {
      if (verses.size >= MAX_VERSE_RESULTS) break;
      const verse = readVerse(row);
      if (!verses.has(verseKey(verse))) verses.set(verseKey(verse), verse);
    }
  }
  return [...verses.values()];
}

export function searchHeadingText(query) {
  const normalized = normalizeVerseQuery(query);
  if (normalized.length < MIN_VERSE_QUERY_LENGTH || !database) return [];
  const match = buildFtsMatchQuery(normalized);
  const headings = new Map();
  if (match) {
    for (const row of rows(HEADING_FTS_QUERY, [match])) {
      const heading = readHeading(row);
      if (!headings.has(heading.id)) headings.set(heading.id, heading);
    }
  }
  if (headings.size < MAX_HEADING_RESULTS) {
    for (const row of rows(HEADING_LIKE_QUERY, [`%${normalized}%`])) {
      if (headings.size >= MAX_HEADING_RESULTS) break;
      const heading = readHeading(row);
      if (!headings.has(heading.id)) headings.set(heading.id, heading);
    }
  }
  return [...headings.values()];
}

export function bookName(id) {
  return bookById(id).name;
}
