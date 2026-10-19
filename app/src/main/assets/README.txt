Store the bundled `kjv.db` SQLite asset here for offline verse lookup and search.
The app copies this asset into runtime storage on startup to refresh indexing.
Generate `kjv.db` with `python .\tools\build_kjv_pipeline.py --source ".\tools\sources\kjv_biblesupersearch.txt"`.
