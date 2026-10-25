# KJV Integrity Tools

These scripts verify that app-stored Bible content matches the source text exactly.

## Files

- `kjv_integrity.py` - parser + verifier + manifest builder
- `import_kjv_to_sqlite.py` - builds `kjv.db` from JSONL and auto-verifies against source
- `build_kjv_pipeline.py` - one command for `export -> import -> verify`
- `test_kjv_integrity.py` - unit tests for parser/compare behavior
- `test_import_kjv_to_sqlite.py` - SQLite schema/import checks and sample normalization parity checks

## Source expectations

The parser expects a verse-keyed source in `Book Chapter:Verse text` format
(for example `Matthew 5:7 ...`).

The project now keeps a reproducible verse-keyed source at:

- `tools/sources/kjv_biblesupersearch.txt`

## Quick usage

Single-step pipeline:

```powershell
python .\tools\build_kjv_pipeline.py --source ".\tools\sources\kjv_biblesupersearch.txt"
```

Expanded/manual flow:

```powershell
python .\tools\kjv_integrity.py parse-stats --source ".\tools\sources\kjv_biblesupersearch.txt"
python .\tools\kjv_integrity.py build-manifest --source ".\tools\sources\kjv_biblesupersearch.txt" --output .\tools\generated\kjv_manifest.json
python .\tools\kjv_integrity.py export-jsonl --source ".\tools\sources\kjv_biblesupersearch.txt" --output .\tools\generated\kjv_verses.jsonl
```

`export-jsonl` writes canonical verse rows for indexing.

The importer preserves the `books` columns and existing `verses` columns, adds
`verses.text_norm TEXT NOT NULL`, and sets `PRAGMA user_version=2`. Verse rows
are inserted in canonical book/chapter/verse order so their SQLite rowids are
stable. `text_norm` is built by deleting source markup (`‹`, `›`, `[`, `]`,
`¶`, `,`, `;`) and then applying the Kotlin
`SearchNormalizer.normalizeVerseQuery` rules: lowercase, replace characters
outside `[a-z0-9 ]` with spaces, collapse whitespace, and trim. The FTS4
virtual table `verses_fts4(text_norm, tokenize=unicode61)` uses each verse's
rowid as `docid`; the former FTS5 `verses_fts` table is not created. The asset
is written with a 4096-byte page size and vacuumed before it is closed.

## Verify indexed app data

Build `app/src/main/assets/kjv.db` from JSONL and automatically run source verification:

```powershell
python .\tools\import_kjv_to_sqlite.py --input .\tools\generated\kjv_verses.jsonl --db .\app\src\main\assets\kjv.db --source ".\tools\sources\kjv_biblesupersearch.txt"
```

This import command validates both:

- `JSONL == source parser output`
- `SQLite verses table == source parser output`

By default, source verification is strict (`Genesis 1:1` through `Revelation 22:21`).
For small test fixtures only, you can pass `--allow-partial-source`.

If your app uses SQLite with `verses(book_id, chapter, verse, text)`:

```powershell
python .\tools\kjv_integrity.py verify-sqlite --source ".\tools\sources\kjv_biblesupersearch.txt" --db .\app\src\main\assets\kjv.db
```

If you export verses as JSONL rows with `book_id`, `chapter`, `verse`, `text`:

```powershell
python .\tools\kjv_integrity.py verify-jsonl --source ".\tools\sources\kjv_biblesupersearch.txt" --input .\tools\exports\verses.jsonl
```

Both commands fail fast and print mismatches with references.

`python -m pytest tools` also checks ten real sample verses against expected
Kotlin normalization results, including bracketed text and red-letter markers
in Matthew 5:3, and verifies the same values are persisted in SQLite. This is
a Python-side mirror of the Kotlin normalizer contract; run the app's JVM unit
tests as well to exercise the Kotlin implementation itself.

## Current snapshot note

Running `parse-stats` on `tools/sources/kjv_biblesupersearch.txt` yields all `31102` verses.

## Distribution reminder

- Keep source provenance records (`source_sha256` and manifest) when rebuilding assets.


