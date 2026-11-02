# KJV Data Tools

Build and verify the app's bundled SQLite database (`app/src/main/assets/kjv.db`)
from the OSIS XML source `tools/sources/kjvlite.xml` (KJV 1769).

## Files

- `osis_parser.py` - streaming (SAX) OSIS parser producing verse and heading rows in the app's inline-marker format
- `import_osis_to_sqlite.py` - builds `kjv.db` (tables + FTS4 indexes) and then runs the verifier
- `verify_osis_db.py` - independent comparison of `kjv.db` against the original XML (does not use `osis_parser.py`)
- `test_osis_parser.py`, `test_import_osis_to_sqlite.py` - unit tests (`python -m pytest tools`); the latter also runs the full strict verification of the bundled `kjv.db` against `kjvlite.xml`

## Quick usage

```powershell
python .\tools\import_osis_to_sqlite.py          # import + verify (default paths)
python .\tools\verify_osis_db.py                 # re-verify an existing kjv.db
python .\tools\import_osis_to_sqlite.py --jsonl .\tools\generated\kjv.jsonl   # also dump parsed rows
```

Both commands exit non-zero and list mismatches with references on failure.
`--allow-partial-source` relaxes whole-Bible checks for small test fixtures.

## OSIS element mapping

| OSIS                                                                     | Stored as                                                                |
| ------------------------------------------------------------------------ | ------------------------------------------------------------------------ |
| `<verse sID/>` ... `<verse eID/>`                                        | one `verses` row per verse                                               |
| `<q who="Jesus">` (container or `sID`/`eID` milestones, may span verses) | `‹ ... ›` (red letter; reopened in each verse it spans)                  |
| `<transChange type="added">`                                             | `[ ... ]` (italic)                                                       |
| `<seg type="x-transChange" subType="x-added">`                           | `[ ... ]` (italic)                                                       |
| `<divineName>`                                                           | `⸢ ... ⸣` (small caps; source text is mixed case, e.g. `⸢Lord⸣` -> LORD) |
| `<title type="psalm">`                                                   | `headings` row, `type='psalm'`, attached to the following verse          |
| `<title type="acrostic" canonical="true">` with `<foreign n="א">`        | `headings` row, `type='acrostic'`, `letter='א'`, plain text (not italic) |
| `<inscription>`                                                          | kept inline in the verse text                                            |
| `<note>`, `<milestone>` (¶ paragraphs), book/chapter titles, `<header>`  | dropped                                                                  |
| Strong's / morphology attributes                                         | ignored                                                                  |

## Schema (`PRAGMA user_version = 5`)

- `books(id, name, chapter_count)`
- `verses(book_id, chapter, verse, text, text_norm)` - rows inserted in canonical order so rowids are stable
- `verses_fts4(text_norm, tokenize=unicode61)` - `docid = verses.rowid`
- `headings(id, book_id, chapter, verse, type, letter, text, text_norm)` - `verse` is the verse the heading precedes
- `headings_fts4(text_norm, tokenize=unicode61)` - `docid = headings.id`

FTS4 is used (not FTS5) because the app opens the asset with Android's framework
SQLite, where FTS5 is not guaranteed at minSdk 26.

`text_norm` mirrors Kotlin `SearchNormalizer.normalizeVerseText`: delete
`‹ › [ ] ⸢ ⸣ , ;` and hyphen/en dash (so `Tubal–cain` → `tubalcain`), fold
`æ`/`Æ` → `ae`, lowercase, replace characters outside `[a-z0-9 ]` with
spaces, collapse whitespace, and trim. The asset uses a 4096-byte page size and
is vacuumed after import.

## Verification

`verify_osis_db.py` re-tokenizes the raw XML with its own regex reader and compares
the database against it:

- verse keys (all 31,102), plain text, and per-character styling (red letter, italic, divine name)
- headings (keys, type, acrostic letter, text, styling)
- `text_norm`, FTS4 docid/text parity for both indexes
- `books` row count and chapter counts, `user_version`, and `PRAGMA integrity_check`

When changing the parser or schema, bump `DB_USER_VERSION` in `import_osis_to_sqlite.py`,
`EXPECTED_USER_VERSION` in `verify_osis_db.py`, and `REQUIRED_DB_USER_VERSION` in
`BibleRepository.kt` together so installed apps recopy the asset.
