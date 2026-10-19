#!/usr/bin/env python3
"""Build a SQLite KJV asset from JSONL and verify against source text.

This script imports verse rows into `kjv.db` and then runs integrity checks to
ensure the DB content exactly matches the source file parser output.
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from pathlib import Path
from typing import Sequence

from kjv_integrity import (
    BOOKS,
    Verse,
    _load_sqlite_verses,
    canonicalize_ws,
    compare_verses,
    parse_kjv_source,
)

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_JSONL = ROOT / "tools" / "generated" / "kjv_verses.jsonl"
DEFAULT_DB = ROOT / "app" / "src" / "main" / "assets" / "kjv.db"


def load_jsonl_verses(path: Path) -> list[Verse]:
    verses: list[Verse] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        row = json.loads(line)
        try:
            book_id = int(row["book_id"])
            chapter = int(row["chapter"])
            verse = int(row["verse"])
            text = canonicalize_ws(str(row["text"]))
        except KeyError as exc:
            raise ValueError(f"Missing key {exc} on JSONL line {line_number}") from exc

        if book_id < 1 or book_id > len(BOOKS):
            raise ValueError(f"Invalid book_id={book_id} on JSONL line {line_number}")

        book_name, _ = BOOKS[book_id - 1]
        verses.append(
            Verse(
                book_id=book_id,
                book=book_name,
                chapter=chapter,
                verse=verse,
                text=text,
            )
        )

    verses.sort(key=lambda v: (v.book_id, v.chapter, v.verse))
    return verses


def create_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        PRAGMA foreign_keys = ON;

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
            PRIMARY KEY (book_id, chapter, verse),
            FOREIGN KEY (book_id) REFERENCES books(id)
        );

        CREATE INDEX idx_verses_book_chapter ON verses(book_id, chapter);
        CREATE INDEX idx_verses_chapter_verse ON verses(chapter, verse);
        """
    )


def populate_db(conn: sqlite3.Connection, verses: Sequence[Verse]) -> None:
    conn.executemany(
        "INSERT INTO books(id, name, chapter_count) VALUES(?, ?, ?)",
        [(i, name, chapter_count) for i, (name, chapter_count) in enumerate(BOOKS, start=1)],
    )
    conn.executemany(
        "INSERT INTO verses(book_id, chapter, verse, text) VALUES(?, ?, ?, ?)",
        [(v.book_id, v.chapter, v.verse, v.text) for v in verses],
    )

    # Optional FTS index for fast contains search in app queries.
    try:
        conn.execute(
            "CREATE VIRTUAL TABLE verses_fts USING fts5(book_id UNINDEXED, chapter UNINDEXED, verse UNINDEXED, text, tokenize='unicode61')"
        )
        conn.execute(
            "INSERT INTO verses_fts(book_id, chapter, verse, text) SELECT book_id, chapter, verse, text FROM verses"
        )
    except sqlite3.OperationalError:
        # Keep import compatible even when local sqlite lacks FTS5.
        pass


def write_sqlite(db_path: Path, verses: Sequence[Verse]) -> None:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    if db_path.exists():
        db_path.unlink()

    with sqlite3.connect(str(db_path)) as conn:
        create_schema(conn)
        populate_db(conn, verses)
        conn.commit()


def run_verification(
    source_path: Path,
    db_path: Path,
    imported_verses: Sequence[Verse],
    strict_source: bool,
) -> None:
    source_verses = parse_kjv_source(source_path, strict=strict_source)

    jsonl_diffs = compare_verses(source_verses, imported_verses, max_diffs=10)
    if jsonl_diffs:
        details = "\n".join(f"- {d}" for d in jsonl_diffs)
        raise ValueError("JSONL does not match source parser output:\n" + details)

    db_verses = _load_sqlite_verses(
        db_path=db_path,
        table="verses",
        book_col="book_id",
        chapter_col="chapter",
        verse_col="verse",
        text_col="text",
    )
    db_diffs = compare_verses(source_verses, db_verses, max_diffs=10)
    if db_diffs:
        details = "\n".join(f"- {d}" for d in db_diffs)
        raise ValueError("SQLite content does not match source parser output:\n" + details)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Import verse JSONL to SQLite and verify against source")
    parser.add_argument(
        "--input",
        default=str(DEFAULT_JSONL),
        help=f"Input JSONL verse rows (default: {DEFAULT_JSONL})",
    )
    parser.add_argument(
        "--db",
        default=str(DEFAULT_DB),
        help=f"Output SQLite DB path (default: {DEFAULT_DB})",
    )
    parser.add_argument(
        "--source",
        required=True,
        help="Source KJV text file used for verification",
    )
    parser.add_argument(
        "--allow-partial-source",
        action="store_true",
        help="Allow source files that do not span Genesis 1:1 through Revelation 22:21",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    input_path = Path(args.input).resolve()
    db_path = Path(args.db).resolve()
    source_path = Path(args.source).resolve()

    if not input_path.exists():
        print(f"ERROR: Input JSONL not found: {input_path}")
        return 1
    if not source_path.exists():
        print(f"ERROR: Source text not found: {source_path}")
        return 1

    try:
        verses = load_jsonl_verses(input_path)
        if not verses:
            print("ERROR: No verses found in input JSONL")
            return 1

        write_sqlite(db_path, verses)
        print(f"Wrote SQLite DB: {db_path}")
        print(f"Imported verses: {len(verses)}")

        run_verification(
            source_path=source_path,
            db_path=db_path,
            imported_verses=verses,
            strict_source=not args.allow_partial_source,
        )
        print("PASS: import verification succeeded (JSONL == source and SQLite == source)")
        return 0
    except Exception as exc:  # pragma: no cover - top-level error reporting
        print(f"ERROR: {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())


