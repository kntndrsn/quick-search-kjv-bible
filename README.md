# Bible Quick Search KJV

Offline-first Android Bible reader scaffold focused on instant startup, quick reference lookup, adaptive layouts for phones/foldables/tablets, chapter swipe navigation, and persistent reading position.

## Current milestone

This implementation creates the Android project structure and the core reader/search experience:

- adaptive reading UI for phones and larger screens
- left-side quick reference book menu on larger layouts
- bottom search box styled for fast lookups
- quick chapter picker from search book matches and book menu selections
- swipe left/right chapter navigation
- text scaling controls with persistence
- last-opened location persistence
- book-name, alias, and phonetic-aware search
- full verse-content search using local SQLite indexing

## Project structure

- `app/src/main/java/com/rdp/biblequicksearchkjv` - Compose UI, view model, data and search logic
- `app/src/main/assets` - generated `kjv.db` SQLite asset used by the app
- `app/src/main/res` - Android resources

## Source of truth

- Source file for reindexing: `tools/sources/kjv_biblesupersearch.txt`
- Parser and verifier: `tools/kjv_integrity.py`
- One-step rebuild: `tools/build_kjv_pipeline.py`

## KJV source integrity verification (automated)

Use the Python verifier to guarantee each stored verse/chapter in the app matches the source text.

- Script: `tools/kjv_integrity.py`
- Tool docs: `tools/README.md`
- Attribution/provenance notes: `THIRD_PARTY_NOTICES.md`

Typical flow:

```powershell
python .\tools\build_kjv_pipeline.py --source ".\tools\sources\kjv_biblesupersearch.txt"
```

The verifier fails on verse-count/reference/text mismatches and prints exact failing references.
The import step writes `kjv.db` and automatically runs source integrity verification.

Manual/expanded commands are documented in `tools/README.md`.

## Attribution and public-domain sourcing

- Keep attribution neutral in app UI (example: `Text sourced via public domain digital archives`).
- Keep source hash/provenance records for reproducible imports.
- See `THIRD_PARTY_NOTICES.md` for implementation guidance.

## Typical commands

If the Gradle wrapper is added later, these commands are the intended entry points:

```powershell
.\gradlew.bat tasks
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

