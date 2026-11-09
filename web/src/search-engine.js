import { normalizeVerseQuery } from './normalize.js';

function indexedMatches(index, tokens) {
  let matches = null;
  for (let i = 0; i < tokens.length; i += 1) {
    const token = tokens[i];
    const positions = new Set();
    if (i === tokens.length - 1) {
      for (const word of Object.keys(index)) {
        if (word.startsWith(token)) for (const position of index[word]) positions.add(position);
      }
    } else {
      for (const position of index[token] || []) positions.add(position);
    }
    matches = matches === null ? positions : new Set([...matches].filter((p) => positions.has(p)));
    if (!matches.size) break;
  }
  return [...(matches || [])].sort((a, b) => a - b);
}

function searchRecords(records, index, normalized, limit) {
  const positions = new Set(indexedMatches(index, normalized.split(' ')).slice(0, limit));
  if (positions.size < limit) {
    for (let i = 0; i < records.length && positions.size < limit; i += 1) {
      if (records[i].normalized.includes(normalized)) positions.add(i);
    }
  }
  return [...positions].map((p) => {
    const { normalized: ignored, ...row } = records[p];
    return row;
  });
}

export function searchData(data, query) {
  const normalized = normalizeVerseQuery(query);
  if (normalized.length < 3) return { matchingVerses: [], matchingHeadings: [] };
  return {
    matchingVerses: searchRecords(data.verses, data.verseIndex, normalized, 25),
    matchingHeadings: searchRecords(data.headings, data.headingIndex, normalized, 10)
  };
}
