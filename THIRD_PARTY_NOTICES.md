# Third-Party Notices

## King James Bible text source

- Source file (in repo): `tools/sources/kjvlite.xml` (OSIS XML, "King James Version (1769)", OSIS work `Bible.KJV`)
- Source SHA-256: `684b5ee60862d334f3bb2a6df8e9981d787fe6c97b7be943c0cff1e00c6043c9`

### Provenance note

- Rebuild commands and verification tooling live under `tools/`.
- Every import run verifies the generated `kjv.db` against the XML source with an independent verifier (`tools/verify_osis_db.py`).
- Record the source SHA-256 above when the source file changes.

> This file is operational guidance for engineering implementation and is not legal advice.
