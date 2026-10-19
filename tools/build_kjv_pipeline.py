#!/usr/bin/env python3
"""One-command KJV data pipeline: export -> import -> verify.

Pipeline stages:
1) Export clean verse rows from source text to JSONL.
2) Import JSONL into SQLite (`kjv.db`).
3) Verify JSONL and SQLite content against source parser output.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Sequence

from import_kjv_to_sqlite import load_jsonl_verses, run_verification, write_sqlite
from kjv_integrity import export_jsonl_verses

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "tools" / "sources" / "kjv_biblesupersearch.txt"
DEFAULT_JSONL = ROOT / "tools" / "generated" / "kjv_verses.jsonl"
DEFAULT_DB = ROOT / "app" / "src" / "main" / "assets" / "kjv.db"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run export -> import -> verify in one command")
    parser.add_argument(
        "--source",
        default=str(DEFAULT_SOURCE),
        help=f"Path to KJV source text file (default: {DEFAULT_SOURCE})",
    )
    parser.add_argument("--jsonl", default=str(DEFAULT_JSONL), help=f"Verse JSONL output path (default: {DEFAULT_JSONL})")
    parser.add_argument("--db", default=str(DEFAULT_DB), help=f"SQLite DB output path (default: {DEFAULT_DB})")
    parser.add_argument(
        "--allow-partial-source",
        action="store_true",
        help="Allow source files that do not span Genesis 1:1 through Revelation 22:21",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    source_path = Path(args.source).resolve()
    jsonl_path = Path(args.jsonl).resolve()
    db_path = Path(args.db).resolve()

    if not source_path.exists():
        print(f"ERROR: Source text not found: {source_path}")
        return 1

    strict = not args.allow_partial_source

    try:
        print("[1/3] Exporting verse JSONL...")
        exported_count = export_jsonl_verses(source_path=source_path, output_path=jsonl_path, strict=strict)
        print(f"Exported verses: {exported_count}")
        print(f"JSONL path: {jsonl_path}")

        print("[2/3] Importing JSONL into SQLite...")
        verses = load_jsonl_verses(jsonl_path)
        if not verses:
            print("ERROR: No verses found in exported JSONL")
            return 1
        write_sqlite(db_path=db_path, verses=verses)
        print(f"SQLite path: {db_path}")
        print(f"Imported verses: {len(verses)}")

        print("[3/3] Verifying imported content against source...")
        run_verification(
            source_path=source_path,
            db_path=db_path,
            imported_verses=verses,
            strict_source=strict,
        )

        print("PASS: Pipeline completed (export -> import -> verify)")
        return 0
    except Exception as exc:  # pragma: no cover - top-level error reporting
        print(f"ERROR: {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())

