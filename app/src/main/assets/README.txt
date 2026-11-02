Store the bundled `kjv.db` SQLite asset here for offline verse lookup and search.
The app copies this asset into runtime storage on startup to refresh indexing.
Generate and verify `kjv.db` from the OSIS source with `python .\tools\import_osis_to_sqlite.py` (see tools/README.md).
