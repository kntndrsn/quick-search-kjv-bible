import { bookById, books } from './books.js';
import {
  compactNormalized,
  normalize,
  normalizeVerseQuery,
  soundex,
  soundexOfCompact
} from './normalize.js';
import { createReferenceParser } from './reference.js';
import { getManifest, readPayload } from './data.js';

export const MAX_VERSE_RESULTS = 25;
export const MAX_HEADING_RESULTS = 10;
export const MAX_BOOK_RESULTS = 6;
export const MIN_VERSE_QUERY_LENGTH = 3;
const CHAPTER_CACHE_SIZE = 8;

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

const chapterCache = new Map();
const chapterRequests = new Map();

function remember(key, value) {
  if (chapterCache.has(key)) chapterCache.delete(key);
  chapterCache.set(key, value);
  if (chapterCache.size > CHAPTER_CACHE_SIZE) {
    const oldest = chapterCache.keys().next().value;
    chapterCache.delete(oldest);
  }
  return value;
}

export async function loadChapter(bookId, chapter) {
  const key = `${bookId}:${chapter}`;
  const cached = chapterCache.get(key);
  if (cached) return remember(key, cached);

  if (chapterRequests.has(key)) return chapterRequests.get(key);
  const request = (async () => {
    const manifest = await getManifest();
    const descriptor = manifest.chapters[`${bookId}-${chapter}`];
    if (!descriptor) throw new Error(`No chapter ${bookId}:${chapter}`);
    const verses = await readPayload(descriptor, manifest.version);
    if (!Array.isArray(verses) || !verses.length) throw new Error('Invalid chapter data');
    return remember(key, verses);
  })().finally(() => chapterRequests.delete(key));
  chapterRequests.set(key, request);
  return request;
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

let worker;
let nextRequest = 0;
const searches = new Map();

export async function searchText(query) {
  if (normalizeVerseQuery(query).length < MIN_VERSE_QUERY_LENGTH) {
    return { matchingVerses: [], matchingHeadings: [] };
  }
  const manifest = await getManifest();
  if (!worker) {
    worker = new Worker(new URL('./search-worker.js', import.meta.url), { type: 'module' });
    worker.onmessage = ({ data }) => {
      const pending = searches.get(data.id);
      if (!pending) return;
      searches.delete(data.id);
      if (data.error) pending.reject(new Error(data.error));
      else pending.resolve(data.result);
    };
    worker.onerror = (event) => {
      for (const pending of searches.values()) pending.reject(new Error(event.message || 'Search worker failed'));
      searches.clear();
      worker.terminate();
      worker = null;
    };
  }
  return new Promise((resolve, reject) => {
    const id = ++nextRequest;
    searches.set(id, { resolve, reject });
    worker.postMessage({ id, query, manifest });
  });
}

export function bookName(id) {
  return bookById(id).name;
}
