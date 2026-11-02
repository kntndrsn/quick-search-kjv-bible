#!/usr/bin/env python3
"""Build the app's SQLite asset (`kjv.db`) from the OSIS XML source.

Schema (PRAGMA user_version = 5):

- ``books(id, name, chapter_count)``
- ``verses(book_id, chapter, verse, text, text_norm)`` — ``text`` uses the
  inline-marker format from :mod:`osis_parser`; rows are inserted in canonical
  order so rowids are stable.
- ``verses_fts4(text_norm)`` — FTS4 index with ``docid = verses.rowid``.
- ``headings(id, book_id, chapter, verse, type, letter, text, text_norm)`` —
  psalm superscriptions and acrostic stanza titles preceding ``verse``.
- ``headings_fts4(text_norm)`` — FTS4 index with ``docid = headings.id``.

After writing, the database is verified against the XML with
:mod:`verify_osis_db` unless ``--skip-verify`` is given.
"""

from __future__ import annotations

import argparse
import contextlib
import json
import sqlite3
import sys
from pathlib import Path
from typing import Sequence

from osis_parser import BOOKS, ParsedBible, normalize_search_text, parse_osis

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "tools" / "sources" / "kjvlite.xml"
DEFAULT_DB = ROOT / "app" / "src" / "main" / "assets" / "kjv.db"
DB_USER_VERSION = 5

SCHEMA = f"""
PRAGMA foreign_keys = ON;
PRAGMA user_version = {DB_USER_VERSION};

CREATE TABLE books (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    chapter_count INTEGER NOT NULL
);

CREATE TABLE verses (
    book_id INTEGER NOT NULL,
    chapter INTEGER NOT NULL,
    verse INTEGER NOT NULL,
    text TEXT NOT NULL,
    text_norm TEXT NOT NULL,
    PRIMARY KEY (book_id, chapter, verse),
    FOREIGN KEY (book_id) REFERENCES books(id)
);

CREATE INDEX idx_verses_book_chapter ON verses(book_id, chapter);
CREATE INDEX idx_verses_chapter_verse ON verses(chapter, verse);

CREATE VIRTUAL TABLE verses_fts4 USING fts4(text_norm, tokenize=unicode61);

CREATE TABLE headings (
    id INTEGER PRIMARY KEY,
    book_id INTEGER NOT NULL,
    chapter INTEGER NOT NULL,
    verse INTEGER NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('psalm', 'acrostic')),
    letter TEXT,
    text TEXT NOT NULL,
    text_norm TEXT NOT NULL,
    FOREIGN KEY (book_id, chapter, verse) REFERENCES verses(book_id, chapter, verse)
);

CREATE INDEX idx_headings_book_chapter ON headings(book_id, chapter);

CREATE VIRTUAL TABLE headings_fts4 USING fts4(text_norm, tokenize=unicode61);
"""


def write_sqlite(db_path: Path, bible: ParsedBible) -> None:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    if db_path.exists():
        db_path.unlink()

    verses = sorted(bible.verses, key=lambda v: (v.book_id, v.chapter, v.verse))
    headings = sorted(
        enumerate(bible.headings),
        key=lambda item: (item[1].book_id, item[1].chapter, item[1].verse, item[0]),
    )
    with contextlib.closing(sqlite3.connect(str(db_path))) as conn:
        conn.execute("PRAGMA page_size = 4096")
        conn.executescript(SCHEMA)
        conn.executemany(
            "INSERT INTO books(id, name, chapter_count) VALUES(?, ?, ?)",
            [(i, name, count) for i, (_, name, count) in enumerate(BOOKS, start=1)],
        )
        conn.executemany(
            "INSERT INTO verses(book_id, chapter, verse, text, text_norm) VALUES(?, ?, ?, ?, ?)",
            [(v.book_id, v.chapter, v.verse, v.text, normalize_search_text(v.text)) for v in verses],
        )
        conn.execute("INSERT INTO verses_fts4(docid, text_norm) SELECT rowid, text_norm FROM verses")
        conn.executemany(
            "INSERT INTO headings(id, book_id, chapter, verse, type, letter, text, text_norm) "
            "VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
            [
                (row_id, h.book_id, h.chapter, h.verse, h.type, h.letter, h.text, normalize_search_text(h.text))
                for row_id, (_, h) in enumerate(headings, start=1)
            ],
        )
        conn.execute("INSERT INTO headings_fts4(docid, text_norm) SELECT id, text_norm FROM headings")
        conn.commit()
        conn.execute("VACUUM")


def write_jsonl(path: Path, bible: ParsedBible) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for v in bible.verses:
            row = {"kind": "verse", "book_id": v.book_id, "book": v.book,
                   "chapter": v.chapter, "verse": v.verse, "text": v.text}
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
        for h in bible.headings:
            row = {"kind": "heading", "book_id": h.book_id, "chapter": h.chapter, "verse": h.verse,
                   "type": h.type, "letter": h.letter, "text": h.text}
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Import OSIS XML into the app SQLite asset and verify it")
    parser.add_argument("--source", default=str(DEFAULT_SOURCE), help=f"OSIS XML source (default: {DEFAULT_SOURCE})")
    parser.add_argument("--db", default=str(DEFAULT_DB), help=f"Output SQLite DB (default: {DEFAULT_DB})")
    parser.add_argument("--jsonl", help="Optionally also write parsed verses/headings as JSONL for inspection")
    parser.add_argument(
        "--allow-partial-source",
        action="store_true",
        help="Allow sources that do not span Genesis 1:1 through Revelation 22:21 (test fixtures)",
    )
    parser.add_argument("--skip-verify", action="store_true", help="Do not run verify_osis_db after import")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    source = Path(args.source).resolve()
    db_path = Path(args.db).resolve()
    if not source.exists():
        print(f"ERROR: OSIS source not found: {source}")
        return 1

    strict = not args.allow_partial_source
    try:
        bible = parse_osis(source, strict=strict)
        write_sqlite(db_path, bible)
        print(f"Wrote SQLite DB: {db_path}")
        print(f"Imported verses: {len(bible.verses)}  headings: {len(bible.headings)}")
        if args.jsonl:
            write_jsonl(Path(args.jsonl).resolve(), bible)
            print(f"Wrote JSONL: {Path(args.jsonl).resolve()}")
    except Exception as exc:  # pragma: no cover - top-level error reporting
        print(f"ERROR: {exc}")
        return 1

    if args.skip_verify:
        return 0
    from verify_osis_db import main as verify_main

    verify_args = ["--source", str(source), "--db", str(db_path)]
    if not strict:
        verify_args.append("--allow-partial-source")
    return verify_main(verify_args)


if __name__ == "__main__":
    sys.exit(main())
