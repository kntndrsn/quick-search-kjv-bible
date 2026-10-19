# Bible Quick Search KJV

Offline-first Android Bible reader scaffold focused on instant startup, quick reference lookup, adaptive layouts for phones/foldables/tablets, chapter swipe navigation, and persistent reading position.

## Current milestone

This implementation creates the Android project structure and the core reader/search experience:

- consistent reading UI across phones, foldables (including Pixel Fold), and tablets
- slide-over Table of Contents drawer (hidden by default on all devices) opened via top-left or lower-left Table of Contents buttons
- simple bottom search box with a magnifying glass icon and oversized text input when active
- system Back navigation support that returns to the previous reading chapter and verse after searching
- quick chapter picker from search book matches and Table of Contents selections
- swipe left/right chapter navigation
- text scaling controls with persistence
- last-opened location persistence
- book-name, alias, and phonetic-aware search
- full verse-content search using local SQLite FTS5 + normalized text indexing

## Project structure

- `app/src/main/java/com/rdp/biblequicksearchkjv` - Compose UI, view model, data and search logic
- `app/src/main/assets` - generated `kjv.db` SQLite asset used by the app
- `app/src/main/res` - Android resources and adaptive launcher icons
- `tools/sources/kjv_biblesupersearch.txt` - canonical KJV source text tracked in source control
- `tools/*.py` - Python pipeline scripts and tests to populate and verify `kjv.db`

## Source of truth

- Source file for reindexing: `tools/sources/kjv_biblesupersearch.txt`
- Parser and verifier: `tools/kjv_integrity.py`
- One-step rebuild: `tools/build_kjv_pipeline.py`

## KJV source integrity verification (automated)

Use the Python verifier to guarantee each stored verse/chapter in the app matches the source text.

- Script: `tools/kjv_integrity.py`
- Tool docs: `tools/README.md`
- Attribution/provenance notes: `THIRD_PARTY_NOTICES.md`

Typical flow (uses `tools/sources/kjv_biblesupersearch.txt` by default):

```powershell
python .\tools\build_kjv_pipeline.py
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

