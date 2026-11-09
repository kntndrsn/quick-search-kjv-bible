import { compactNormalized, normalize } from './normalize.js';

const chapterVerseRegex = /^(\d+)(?:[: .-]+(\d+))?.*$/;

function buildAliases(bookList) {
  const entries = [];
  for (const book of bookList) {
    for (const alias of book.aliases) {
      const normalizedAlias = normalize(alias);
      entries.push({
        book,
        normalizedAlias,
        compactAlias: compactNormalized(normalizedAlias)
      });
    }
  }
  entries.sort((a, b) => b.compactAlias.length - a.compactAlias.length);
  return entries;
}

export function createReferenceParser(bookList) {
  const aliasesByLength = buildAliases(bookList);

  function parseNormalized(normalizedQuery, compactQuery) {
    if (!normalizedQuery.trim()) return null;
    const aliasEntry = aliasesByLength.find((entry) =>
      normalizedQuery.startsWith(entry.normalizedAlias) || compactQuery.startsWith(entry.compactAlias)
    );
    if (!aliasEntry) return null;

    let remainder = '';
    if (normalizedQuery.startsWith(aliasEntry.normalizedAlias)) {
      remainder = normalizedQuery.slice(aliasEntry.normalizedAlias.length).trim();
    } else if (compactQuery.startsWith(aliasEntry.compactAlias)) {
      remainder = compactQuery.slice(aliasEntry.compactAlias.length);
    }

    const match = chapterVerseRegex.exec(remainder);
    if (!match) return null;
    const chapter = Number.parseInt(match[1], 10);
    if (!Number.isInteger(chapter) || chapter < 1 || chapter > aliasEntry.book.chapterCount) return null;
    const verseText = match[2];
    const verse = verseText ? Number.parseInt(verseText, 10) : null;
    return {
      book: aliasEntry.book,
      chapter,
      verse: verse != null && verse >= 1 ? verse : null,
      label: verse != null && verse >= 1
        ? `${aliasEntry.book.name} ${chapter}:${verse}`
        : `${aliasEntry.book.name} ${chapter}`
    };
  }

  return {
    parse(query) {
      const normalizedQuery = normalize(query);
      return parseNormalized(normalizedQuery, compactNormalized(normalizedQuery));
    },
    parseNormalized
  };
}
