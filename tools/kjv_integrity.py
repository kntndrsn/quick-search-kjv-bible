#!/usr/bin/env python3
"""KJV source parser and integrity verifier.

This tool parses verse-keyed source lines in the form:
`Book Chapter:Verse text`
and verifies that stored app data (SQLite/JSONL) matches source verses exactly
after deterministic whitespace canonicalization.
"""

from __future__ import annotations

import argparse
import dataclasses
import hashlib
import json
import re
import sqlite3
import sys
from pathlib import Path
from typing import Sequence

BOOKS: Sequence[tuple[str, int]] = (
    ("Genesis", 50), ("Exodus", 40), ("Leviticus", 27), ("Numbers", 36),
    ("Deuteronomy", 34), ("Joshua", 24), ("Judges", 21), ("Ruth", 4),
    ("1 Samuel", 31), ("2 Samuel", 24), ("1 Kings", 22), ("2 Kings", 25),
    ("1 Chronicles", 29), ("2 Chronicles", 36), ("Ezra", 10), ("Nehemiah", 13),
    ("Esther", 10), ("Job", 42), ("Psalms", 150), ("Proverbs", 31),
    ("Ecclesiastes", 12), ("Song of Solomon", 8), ("Isaiah", 66), ("Jeremiah", 52),
    ("Lamentations", 5), ("Ezekiel", 48), ("Daniel", 12), ("Hosea", 14),
    ("Joel", 3), ("Amos", 9), ("Obadiah", 1), ("Jonah", 4), ("Micah", 7),
    ("Nahum", 3), ("Habakkuk", 3), ("Zephaniah", 3), ("Haggai", 2),
    ("Zechariah", 14), ("Malachi", 4), ("Matthew", 28), ("Mark", 16),
    ("Luke", 24), ("John", 21), ("Acts", 28), ("Romans", 16),
    ("1 Corinthians", 16), ("2 Corinthians", 13), ("Galatians", 6),
    ("Ephesians", 6), ("Philippians", 4), ("Colossians", 4), ("1 Thessalonians", 5),
    ("2 Thessalonians", 3), ("1 Timothy", 6), ("2 Timothy", 4), ("Titus", 3),
    ("Philemon", 1), ("Hebrews", 13), ("James", 5), ("1 Peter", 5),
    ("2 Peter", 3), ("1 John", 5), ("2 John", 1), ("3 John", 1),
    ("Jude", 1), ("Revelation", 22),
)

BOOK_NAME_TO_ID: dict[str, int] = {name: idx for idx, (name, _) in enumerate(BOOKS, start=1)}
BOOK_PATTERN = "|".join(sorted((re.escape(name) for name, _ in BOOKS), key=len, reverse=True))
REFERENCE_LINE_RE = re.compile(
    rf"^\s*(?P<book>{BOOK_PATTERN})\s+(?P<chapter>\d+):(?P<verse>\d+)\s+(?P<text>.+?)\s*$"
)


@dataclasses.dataclass(frozen=True)
class Verse:
    book_id: int
    book: str
    chapter: int
    verse: int
    text: str

    @property
    def ref(self) -> str:
        return f"{self.book} {self.chapter}:{self.verse}"


def canonicalize_ws(text: str) -> str:
    return " ".join(text.split())


def _read_text_with_fallback(path: Path) -> str:
    data = path.read_bytes()
    for enc in ("utf-8-sig", "utf-8", "cp1252", "latin-1"):
        try:
            return data.decode(enc)
        except UnicodeDecodeError:
            continue
    raise UnicodeDecodeError("unknown", b"", 0, 1, f"Unable to decode {path}")


def parse_kjv_source(source_path: Path, strict: bool = True) -> list[Verse]:
    text = _read_text_with_fallback(source_path)
    verses = _parse_reference_lines(text.splitlines())

    if not verses:
        raise ValueError("No verses parsed from source")
    _validate_reference_sequence(verses, require_full_contiguity=strict)
    if strict:
        if verses[0].book_id != 1 or verses[0].chapter != 1 or verses[0].verse != 1:
            raise ValueError("Parsed stream does not start at Genesis 1:1")
        if verses[-1].book_id != 66 or verses[-1].chapter != 22 or verses[-1].verse != 21:
            raise ValueError("Parsed stream does not end at Revelation 22:21")

    return verses


def _parse_reference_lines(lines: Sequence[str]) -> list[Verse]:
    """Parse one-verse-per-line sources (e.g., 'Matthew 5:7 <text>')."""
    verses: list[Verse] = []
    for raw in lines:
        line = raw.strip()
        if not line:
            continue

        match = REFERENCE_LINE_RE.match(line)
        if not match:
            continue

        book_name = match.group("book")
        book_id = BOOK_NAME_TO_ID[book_name]
        chapter = int(match.group("chapter"))
        verse_num = int(match.group("verse"))
        verse_text = canonicalize_ws(match.group("text"))
        verses.append(
            Verse(
                book_id=book_id,
                book=book_name,
                chapter=chapter,
                verse=verse_num,
                text=verse_text,
            )
        )

    return verses


def _validate_reference_sequence(verses: Sequence[Verse], require_full_contiguity: bool) -> None:
    """Fail fast on duplicate, missing, or out-of-order references."""
    prev: Verse | None = None
    for current in verses:
        if prev is None:
            prev = current
            continue

        prev_key = (prev.book_id, prev.chapter, prev.verse)
        current_key = (current.book_id, current.chapter, current.verse)
        if current_key <= prev_key:
            raise ValueError(f"Out-of-order or duplicate reference at {prev.ref} -> {current.ref}")

        if require_full_contiguity:
            if current.book_id == prev.book_id and current.chapter == prev.chapter:
                expected_verse = prev.verse + 1
                if current.verse != expected_verse:
                    raise ValueError(
                        f"Verse sequence gap at {prev.ref} -> {current.ref}; expected verse {expected_verse}"
                    )
            elif current.book_id == prev.book_id:
                expected_chapter = prev.chapter + 1
                if current.chapter != expected_chapter or current.verse != 1:
                    raise ValueError(
                        f"Chapter sequence gap at {prev.ref} -> {current.ref}; expected chapter {expected_chapter}:1"
                    )
            else:
                expected_book = prev.book_id + 1
                if current.book_id != expected_book or current.chapter != 1 or current.verse != 1:
                    raise ValueError(
                        f"Book sequence gap at {prev.ref} -> {current.ref}; expected book_id {expected_book} at 1:1"
                    )

        prev = current


def _hash_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def build_manifest(verses: Sequence[Verse], source_path: Path) -> dict:
    chapter_rows: list[dict] = []
    grouped: dict[tuple[int, int], list[Verse]] = {}
    for v in verses:
        grouped.setdefault((v.book_id, v.chapter), []).append(v)

    for (book_id, chapter), chunk in sorted(grouped.items()):
        payload = "\n".join(f"{v.verse}\t{v.text}" for v in chunk)
        chapter_rows.append(
            {
                "book_id": book_id,
                "book": chunk[0].book,
                "chapter": chapter,
                "verse_count": len(chunk),
                "chapter_sha256": _hash_text(payload),
            }
        )

    entire_payload = "\n".join(f"{v.book_id}|{v.chapter}|{v.verse}|{v.text}" for v in verses)
    source_hash = hashlib.sha256(source_path.read_bytes()).hexdigest()

    return {
        "schema_version": 1,
        "source_file": str(source_path),
        "source_sha256": source_hash,
        "total_books": 66,
        "total_verses": len(verses),
        "global_sha256": _hash_text(entire_payload),
        "chapters": chapter_rows,
    }


def _load_sqlite_verses(db_path: Path, table: str, book_col: str, chapter_col: str, verse_col: str, text_col: str) -> list[Verse]:
    query = (
        f"SELECT {book_col}, {chapter_col}, {verse_col}, {text_col} "
        f"FROM {table} ORDER BY {book_col}, {chapter_col}, {verse_col}"
    )
    with sqlite3.connect(str(db_path)) as conn:
        rows = conn.execute(query).fetchall()

    verses: list[Verse] = []
    for row in rows:
        bid, chapter, verse, text = row
        book_name, _ = BOOKS[int(bid) - 1]
        verses.append(
            Verse(
                book_id=int(bid),
                book=book_name,
                chapter=int(chapter),
                verse=int(verse),
                text=canonicalize_ws(str(text)),
            )
        )
    return verses


def _load_jsonl_verses(path: Path) -> list[Verse]:
    verses: list[Verse] = []
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        row = json.loads(line)
        bid = int(row["book_id"])
        book_name, _ = BOOKS[bid - 1]
        verses.append(
            Verse(
                book_id=bid,
                book=book_name,
                chapter=int(row["chapter"]),
                verse=int(row["verse"]),
                text=canonicalize_ws(str(row["text"])),
            )
        )
    verses.sort(key=lambda v: (v.book_id, v.chapter, v.verse))
    return verses


def export_jsonl_verses(source_path: Path, output_path: Path, strict: bool = True) -> int:
    source_verses = parse_kjv_source(source_path, strict=strict)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with output_path.open("w", encoding="utf-8", newline="\n") as f:
        for v in source_verses:
            row = {
                "book_id": v.book_id,
                "book": v.book,
                "chapter": v.chapter,
                "verse": v.verse,
                "text": v.text,
            }
            f.write(json.dumps(row, ensure_ascii=False) + "\n")

    return len(source_verses)


def compare_verses(expected: Sequence[Verse], actual: Sequence[Verse], max_diffs: int = 10) -> list[str]:
    diffs: list[str] = []
    if len(expected) != len(actual):
        diffs.append(f"Verse count mismatch: expected {len(expected)}, actual {len(actual)}")

    for idx, (exp, act) in enumerate(zip(expected, actual), start=1):
        if (exp.book_id, exp.chapter, exp.verse) != (act.book_id, act.chapter, act.verse):
            diffs.append(
                f"Reference mismatch at row {idx}: expected {exp.ref}, actual {act.ref}"
            )
        elif exp.text != act.text:
            diffs.append(
                f"Text mismatch at {exp.ref}: expected='{exp.text[:120]}' actual='{act.text[:120]}'"
            )
        if len(diffs) >= max_diffs:
            break

    return diffs


def cmd_parse_stats(args: argparse.Namespace) -> int:
    source = Path(args.source)
    verses = parse_kjv_source(source)
    first, last = verses[0], verses[-1]
    print(f"Parsed verses: {len(verses)}")
    print(f"First verse: {first.ref}")
    print(f"Last verse: {last.ref}")
    print(f"Source SHA256: {hashlib.sha256(source.read_bytes()).hexdigest()}")
    return 0


def cmd_build_manifest(args: argparse.Namespace) -> int:
    source = Path(args.source)
    output = Path(args.output)
    verses = parse_kjv_source(source)
    manifest = build_manifest(verses, source)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Wrote manifest: {output}")
    print(f"Total verses: {manifest['total_verses']}")
    print(f"Global SHA256: {manifest['global_sha256']}")
    return 0


def cmd_verify_sqlite(args: argparse.Namespace) -> int:
    source_verses = parse_kjv_source(Path(args.source))
    db_verses = _load_sqlite_verses(
        db_path=Path(args.db),
        table=args.table,
        book_col=args.book_col,
        chapter_col=args.chapter_col,
        verse_col=args.verse_col,
        text_col=args.text_col,
    )
    diffs = compare_verses(source_verses, db_verses, max_diffs=args.max_diffs)
    if diffs:
        print("FAILED: data mismatch")
        for d in diffs:
            print(f"- {d}")
        return 1
    print(f"PASS: SQLite content matches source ({len(source_verses)} verses)")
    return 0


def cmd_verify_jsonl(args: argparse.Namespace) -> int:
    source_verses = parse_kjv_source(Path(args.source))
    jsonl_verses = _load_jsonl_verses(Path(args.input))
    diffs = compare_verses(source_verses, jsonl_verses, max_diffs=args.max_diffs)
    if diffs:
        print("FAILED: data mismatch")
        for d in diffs:
            print(f"- {d}")
        return 1
    print(f"PASS: JSONL content matches source ({len(source_verses)} verses)")
    return 0


def cmd_export_jsonl(args: argparse.Namespace) -> int:
    output = Path(args.output)
    count = export_jsonl_verses(
        source_path=Path(args.source),
        output_path=output,
        strict=not args.allow_partial_source,
    )

    print(
        f"Wrote {count} verse rows to {output}"
    )
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="KJV source integrity tooling")
    sub = parser.add_subparsers(dest="command", required=True)

    parse_stats = sub.add_parser("parse-stats", help="Parse source and print counts/hashes")
    parse_stats.add_argument("--source", required=True, help="Path to KJV source text file")
    parse_stats.set_defaults(func=cmd_parse_stats)

    build = sub.add_parser("build-manifest", help="Build per-chapter hash manifest from source")
    build.add_argument("--source", required=True, help="Path to KJV source text file")
    build.add_argument("--output", required=True, help="Output JSON manifest path")
    build.set_defaults(func=cmd_build_manifest)

    sqlite_verify = sub.add_parser("verify-sqlite", help="Verify SQLite verse table against source")
    sqlite_verify.add_argument("--source", required=True, help="Path to KJV source text file")
    sqlite_verify.add_argument("--db", required=True, help="Path to SQLite DB")
    sqlite_verify.add_argument("--table", default="verses", help="Verse table name")
    sqlite_verify.add_argument("--book-col", default="book_id", help="Book id column")
    sqlite_verify.add_argument("--chapter-col", default="chapter", help="Chapter column")
    sqlite_verify.add_argument("--verse-col", default="verse", help="Verse column")
    sqlite_verify.add_argument("--text-col", default="text", help="Verse text column")
    sqlite_verify.add_argument("--max-diffs", type=int, default=10, help="Maximum diffs to print")
    sqlite_verify.set_defaults(func=cmd_verify_sqlite)

    jsonl_verify = sub.add_parser("verify-jsonl", help="Verify JSONL verses against source")
    jsonl_verify.add_argument("--source", required=True, help="Path to KJV source text file")
    jsonl_verify.add_argument("--input", required=True, help="Path to JSONL verse rows")
    jsonl_verify.add_argument("--max-diffs", type=int, default=10, help="Maximum diffs to print")
    jsonl_verify.set_defaults(func=cmd_verify_jsonl)

    export_jsonl = sub.add_parser(
        "export-jsonl",
        help="Export clean verse JSONL for app indexing",
    )
    export_jsonl.add_argument("--source", required=True, help="Path to KJV source text file")
    export_jsonl.add_argument("--output", required=True, help="Output JSONL path")
    export_jsonl.add_argument(
        "--allow-partial-source",
        action="store_true",
        help="Allow source files that do not span Genesis 1:1 through Revelation 22:21",
    )
    export_jsonl.set_defaults(func=cmd_export_jsonl)

    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    sys.exit(main())





