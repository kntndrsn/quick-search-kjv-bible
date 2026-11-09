const RAW_BOOKS = [
  [1, 'Genesis', 'Gen', 50, ['ge', 'genesis', 'gen']],
  [2, 'Exodus', 'Exo', 40, ['ex', 'exo', 'exodus']],
  [3, 'Leviticus', 'Lev', 27, ['lev', 'le', 'leviticus']],
  [4, 'Numbers', 'Num', 36, ['num', 'nu', 'numbers']],
  [5, 'Deuteronomy', 'Deut', 34, ['deu', 'deut', 'deuteronomy']],
  [6, 'Joshua', 'Josh', 24, ['jos', 'josh', 'joshua']],
  [7, 'Judges', 'Judg', 21, ['jdg', 'judg', 'judges']],
  [8, 'Ruth', 'Ruth', 4, ['rth', 'ruth']],
  [9, '1 Samuel', '1 Sam', 31, ['1 samuel', '1 sam', 'first samuel', 'first sam', 'i samuel']],
  [10, '2 Samuel', '2 Sam', 24, ['2 samuel', '2 sam', 'second samuel', 'second sam', 'ii samuel']],
  [11, '1 Kings', '1 Kgs', 22, ['1 kings', '1 kgs', 'first kings', 'i kings']],
  [12, '2 Kings', '2 Kgs', 25, ['2 kings', '2 kgs', 'second kings', 'ii kings']],
  [13, '1 Chronicles', '1 Chr', 29, ['1 chronicles', '1 chr', 'first chronicles', 'i chronicles']],
  [14, '2 Chronicles', '2 Chr', 36, ['2 chronicles', '2 chr', 'second chronicles', 'ii chronicles']],
  [15, 'Ezra', 'Ezra', 10, ['ezr', 'ezra']],
  [16, 'Nehemiah', 'Neh', 13, ['neh', 'nehemiah']],
  [17, 'Esther', 'Est', 10, ['est', 'esther']],
  [18, 'Job', 'Job', 42, ['job']],
  [19, 'Psalms', 'Ps', 150, ['ps', 'psalm', 'psalms', 'psa']],
  [20, 'Proverbs', 'Prov', 31, ['prov', 'proverbs', 'pr']],
  [21, 'Ecclesiastes', 'Eccl', 12, ['eccl', 'ecclesiastes', 'ecc']],
  [22, 'Song of Solomon', 'Song', 8, ['song of solomon', 'song', 'song of songs', 'songs']],
  [23, 'Isaiah', 'Isa', 66, ['isa', 'isaiah']],
  [24, 'Jeremiah', 'Jer', 52, ['jer', 'jeremiah']],
  [25, 'Lamentations', 'Lam', 5, ['lam', 'lamentations']],
  [26, 'Ezekiel', 'Ezek', 48, ['ezek', 'ezekiel']],
  [27, 'Daniel', 'Dan', 12, ['dan', 'daniel']],
  [28, 'Hosea', 'Hos', 14, ['hos', 'hosea']],
  [29, 'Joel', 'Joel', 3, ['joel', 'jl']],
  [30, 'Amos', 'Amos', 9, ['amos', 'am']],
  [31, 'Obadiah', 'Obad', 1, ['obad', 'obadiah', 'ob']],
  [32, 'Jonah', 'Jonah', 4, ['jonah', 'jon']],
  [33, 'Micah', 'Mic', 7, ['mic', 'micah']],
  [34, 'Nahum', 'Nah', 3, ['nah', 'nahum']],
  [35, 'Habakkuk', 'Hab', 3, ['hab', 'habakkuk']],
  [36, 'Zephaniah', 'Zeph', 3, ['zeph', 'zephaniah']],
  [37, 'Haggai', 'Hag', 2, ['hag', 'haggai']],
  [38, 'Zechariah', 'Zech', 14, ['zech', 'zechariah']],
  [39, 'Malachi', 'Mal', 4, ['mal', 'malachi']],
  [40, 'Matthew', 'Matt', 28, ['matt', 'matthew', 'mt']],
  [41, 'Mark', 'Mark', 16, ['mark', 'mk']],
  [42, 'Luke', 'Luke', 24, ['luke', 'lk']],
  [43, 'John', 'John', 21, ['john', 'jn', 'jhn']],
  [44, 'Acts', 'Acts', 28, ['acts', 'ac']],
  [45, 'Romans', 'Rom', 16, ['rom', 'romans', 'ro']],
  [46, '1 Corinthians', '1 Cor', 16, ['1 corinthians', '1 cor', 'first corinthians', 'i corinthians']],
  [47, '2 Corinthians', '2 Cor', 13, ['2 corinthians', '2 cor', 'second corinthians', 'ii corinthians']],
  [48, 'Galatians', 'Gal', 6, ['gal', 'galatians']],
  [49, 'Ephesians', 'Eph', 6, ['eph', 'ephesians']],
  [50, 'Philippians', 'Phil', 4, ['phil', 'philippians']],
  [51, 'Colossians', 'Col', 4, ['col', 'colossians']],
  [52, '1 Thessalonians', '1 Thess', 5, ['1 thessalonians', '1 thess', 'first thessalonians', 'i thessalonians']],
  [53, '2 Thessalonians', '2 Thess', 3, ['2 thessalonians', '2 thess', 'second thessalonians', 'ii thessalonians']],
  [54, '1 Timothy', '1 Tim', 6, ['1 timothy', '1 tim', 'first timothy', 'i timothy']],
  [55, '2 Timothy', '2 Tim', 4, ['2 timothy', '2 tim', 'second timothy', 'ii timothy']],
  [56, 'Titus', 'Titus', 3, ['titus', 'tit']],
  [57, 'Philemon', 'Phlm', 1, ['philemon', 'phlm']],
  [58, 'Hebrews', 'Heb', 13, ['hebrews', 'heb']],
  [59, 'James', 'Jas', 5, ['james', 'jas']],
  [60, '1 Peter', '1 Pet', 5, ['1 peter', '1 pet', 'first peter', 'i peter']],
  [61, '2 Peter', '2 Pet', 3, ['2 peter', '2 pet', 'second peter', 'ii peter']],
  [62, '1 John', '1 Jn', 5, ['1 john', 'first john', 'i john']],
  [63, '2 John', '2 Jn', 1, ['2 john', 'second john', 'ii john']],
  [64, '3 John', '3 Jn', 1, ['3 john', 'third john', 'iii john']],
  [65, 'Jude', 'Jude', 1, ['jude', 'jud']],
  [66, 'Revelation', 'Rev', 22, ['revelation', 'rev', 'apocalypse']]
];

function distinct(values) {
  const seen = new Set();
  const out = [];
  for (const value of values) {
    if (seen.has(value)) continue;
    seen.add(value);
    out.push(value);
  }
  return out;
}

export const books = RAW_BOOKS.map(([id, name, shortName, chapterCount, aliases]) => ({
  id,
  name,
  shortName,
  chapterCount,
  aliases: distinct([...aliases, name, shortName])
}));

const booksById = new Map(books.map((book) => [book.id, book]));

export function bookById(id) {
  return booksById.get(id) || books[0];
}

export const pages = books.flatMap((book) =>
  Array.from({ length: book.chapterCount }, (_, index) => ({ bookId: book.id, chapter: index + 1 }))
);

const pageOffset = new Map();
{
  let cursor = 0;
  for (const book of books) {
    pageOffset.set(book.id, cursor);
    cursor += book.chapterCount;
  }
}

export function pageIndex(bookId, chapter) {
  const start = pageOffset.get(bookId);
  if (start == null) return 0;
  const book = bookById(bookId);
  const clamped = Math.min(book.chapterCount, Math.max(1, chapter));
  return start + clamped - 1;
}

export function pageAt(index) {
  const clamped = Math.min(pages.length - 1, Math.max(0, index));
  return pages[clamped];
}
