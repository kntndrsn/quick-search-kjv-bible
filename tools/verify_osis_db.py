#!/usr/bin/env python3
"""Compare the app SQLite asset (`kjv.db`) against the original OSIS XML.

This verifier intentionally does NOT use :mod:`osis_parser`. It re-reads the
XML with its own regex tokenizer, derives every verse's and heading's plain
text plus per-character style flags (red letter, added/italic, divine name),
and compares them with the stored inline-marker text. It also checks
``text_norm``, FTS4 index parity, the books table, ``user_version``, and
SQLite integrity.

Usage: ``python tools/verify_osis_db.py [--source XML] [--db DB]``
Exit code 0 = match, 1 = mismatch/error.
"""

from __future__ import annotations

import argparse
import contextlib
import html
import re
import sqlite3
import sys
from pathlib import Path
from typing import Sequence

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "tools" / "sources" / "kjvlite.xml"
DEFAULT_DB = ROOT / "app" / "src" / "main" / "assets" / "kjv.db"
EXPECTED_USER_VERSION = 5
EXPECTED_VERSE_COUNT = 31102
MAX_REPORTED = 25

RED, ITALIC, DIVINE = 1, 2, 4
MARKERS = {"\u2039": (RED, True), "\u203A": (RED, False), "[": (ITALIC, True), "]": (ITALIC, False),
           "\u2E22": (DIVINE, True), "\u2E23": (DIVINE, False)}

CANONICAL_OSIS_BOOKS = (
    "Gen Exod Lev Num Deut Josh Judg Ruth 1Sam 2Sam 1Kgs 2Kgs 1Chr 2Chr Ezra Neh Esth Job Ps Prov "
    "Eccl Song Isa Jer Lam Ezek Dan Hos Joel Amos Obad Jonah Mic Nah Hab Zeph Hag Zech Mal "
    "Matt Mark Luke John Acts Rom 1Cor 2Cor Gal Eph Phil Col 1Thess 2Thess 1Tim 2Tim Titus Phlm "
    "Heb Jas 1Pet 2Pet 1John 2John 3John Jude Rev"
).split()

_TOKEN = re.compile(
    r"<!--.*?-->|<\?.*?\?>|<!\[CDATA\[(?P<cdata>.*?)\]\]>|<!DOCTYPE[^>]*>"
    r"|<(?P<close>/)?(?P<name>[\w:.-]+)(?P<attrs>[^>]*?)(?P<empty>/)?>|(?P<text>[^<]+)",
    re.S,
)
_ATTR = re.compile(r"""([\w:.-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
_VERSE_ID = re.compile(r"^([1-3]?[A-Za-z]+)\.(\d+)\.(\d+)$")
_WS = re.compile(r"\s+")

Styled = tuple[tuple[str, int], ...]
Key = tuple[int, int, int]


def verse_key(osis_id: str) -> Key:
    m = _VERSE_ID.match(osis_id)
    if not m or m.group(1) not in CANONICAL_OSIS_BOOKS:
        raise ValueError(f"Unknown verse id {osis_id}")
    return CANONICAL_OSIS_BOOKS.index(m.group(1)) + 1, int(m.group(2)), int(m.group(3))


def collapse(chars: list[tuple[str, int]]) -> Styled:
    """Collapse whitespace runs to one unstyled space and trim the ends."""
    out: list[tuple[str, int]] = []
    for ch, flags in chars:
        if ch.isspace():
            if out and out[-1][0] != " ":
                out.append((" ", 0))
        else:
            out.append((ch, flags))
    while out and out[-1][0] == " ":
        out.pop()
    return tuple(out)


def styled_from_markers(text: str) -> Styled:
    """Decode the app's inline-marker text into (char, flags) pairs."""
    flags = 0
    chars: list[tuple[str, int]] = []
    for ch in text:
        if ch in MARKERS:
            bit, opening = MARKERS[ch]
            if opening == bool(flags & bit):
                raise ValueError(f"unbalanced or nested marker {ch!r}")
            flags ^= bit
        else:
            chars.append((ch, flags))
    if flags:
        raise ValueError("unclosed marker")
    if text != text.strip() or "  " in text:
        raise ValueError("untidy whitespace")
    return collapse(chars)


def normalize(text: str) -> str:
    """Independent mirror of Kotlin SearchNormalizer.normalizeVerseText."""
    text = "".join(ch for ch in text if ch not in MARKERS and ch not in ",;-\u2013")
    text = text.replace("\u00e6", "ae").replace("\u00c6", "ae").lower()
    text = re.sub(r"[^a-z0-9 ]", " ", text)
    return _WS.sub(" ", text).strip()


def expected_from_xml(source: Path):
    """Return (verses, headings, chapter_counts, book_order) derived from the raw XML."""
    data = source.read_text(encoding="utf-8")
    verses: dict[Key, list[tuple[str, int]]] = {}
    headings: list[tuple[Key, str, str | None, list[tuple[str, int]]]] = []
    pending: list[tuple[str, str | None, list[tuple[str, int]]]] = []
    chapter_counts: dict[int, int] = {}
    book_order: list[str] = []

    stack: list[tuple[str, int, bool]] = []  # (name, style bit, skip)
    skip = 0
    red_milestone = False
    current: Key | None = None
    heading: list | None = None  # [type, letter, chars]

    def style() -> int:
        flags = RED if red_milestone else 0
        for _, bit, _ in stack:
            flags |= bit
        return flags

    for m in _TOKEN.finditer(data):
        text = m.group("text") if m.group("text") is not None else m.group("cdata")
        if text is not None:
            if skip:
                continue
            target = heading[2] if heading is not None else verses[current] if current else None
            if target is not None:
                flags = style()
                target.extend((ch, flags) for ch in html.unescape(text))
            continue
        name = m.group("name")
        if name is None:
            continue
        if m.group("close"):
            if not stack or stack[-1][0] != name:
                raise ValueError(f"Mismatched </{name}> at offset {m.start()}")
            _, _, skipping = stack.pop()
            if skipping:
                skip -= 1
            elif name == "title" and heading is not None and not skip:
                kind, letter, chars = heading
                heading = None
                if current is not None:
                    headings.append((current, kind, letter, chars))
                else:
                    pending.append((kind, letter, chars))
            continue

        attrs = {a: html.unescape(b if b is not None else c) for a, b, c in _ATTR.findall(m.group("attrs"))}
        empty = bool(m.group("empty"))
        bit, skipping = 0, False
        if skip:
            skipping = True
        elif name == "verse" and attrs.get("sID"):
            if current is not None:
                raise ValueError(f"Verse {attrs['sID']} opened inside another verse")
            current = verse_key(attrs.get("osisID") or attrs["sID"])
            if current in verses:
                raise ValueError(f"Duplicate verse {attrs['sID']}")
            verses[current] = []
            headings.extend((current, k, l, c) for k, l, c in pending)
            pending.clear()
        elif name == "verse" and attrs.get("eID"):
            if current != verse_key(attrs["eID"]):
                raise ValueError(f"Verse end {attrs['eID']} does not match open verse")
            current = None
        elif name == "chapter" and attrs.get("osisID") and not empty:
            book, _, chap = attrs["osisID"].partition(".")
            book_id = CANONICAL_OSIS_BOOKS.index(book) + 1
            chapter_counts[book_id] = max(chapter_counts.get(book_id, 0), int(chap))
        elif name == "div" and attrs.get("type") == "book" and attrs.get("osisID"):
            book_order.append(attrs["osisID"])
        elif name in ("note", "header"):
            skipping = True
        elif name == "title":
            if attrs.get("type") in ("psalm", "acrostic"):
                heading = [attrs["type"], None, []]
            else:
                skipping = True
        elif name == "foreign" and heading is not None and attrs.get("n"):
            heading[1] = attrs["n"]
        elif name == "q" and empty:
            if attrs.get("sID") and attrs.get("who") == "Jesus":
                red_milestone = True
            elif attrs.get("eID"):
                red_milestone = False
        elif name == "q" and attrs.get("who") == "Jesus":
            bit = RED
        elif name == "transChange" and attrs.get("type") == "added":
            bit = ITALIC
        elif name == "seg" and attrs.get("type") == "x-transChange":
            bit = ITALIC
        elif name == "divineName":
            bit = DIVINE

        if not empty:
            stack.append((name, bit, skipping))
            if skipping:
                skip += 1

    if stack:
        raise ValueError(f"Unclosed element <{stack[-1][0]}>")
    if pending or current is not None:
        raise ValueError("Dangling heading or unterminated verse at end of XML")
    expected_verses = {k: collapse(v) for k, v in verses.items()}
    expected_headings = [(k, t, l, collapse(c)) for k, t, l, c in headings]
    return expected_verses, expected_headings, chapter_counts, book_order


def _plain(styled: Styled) -> str:
    return "".join(ch for ch, _ in styled)


def _describe_diff(expected: Styled, actual: Styled) -> str:
    if _plain(expected) != _plain(actual):
        return f"text differs: expected {_plain(expected)[:90]!r} got {_plain(actual)[:90]!r}"
    for i, ((ch, ef), (_, af)) in enumerate(zip(expected, actual)):
        if ef != af:
            names = lambda f: "+".join(n for b, n in ((RED, "red"), (ITALIC, "italic"), (DIVINE, "divine")) if f & b) or "plain"
            return f"style differs at char {i} {ch!r}: expected {names(ef)} got {names(af)}"
    return "differs"


def _check_fts(conn: sqlite3.Connection, fts: str, source_sql: str, add) -> None:
    expected = conn.execute(source_sql).fetchall()
    actual = conn.execute(f"SELECT docid, text_norm FROM {fts} ORDER BY docid").fetchall()
    if expected != actual:
        add(f"{fts}: index does not match source rows (expected {len(expected)} rows, found {len(actual)})")
    if conn.execute(f"SELECT sql FROM sqlite_master WHERE name = ?", (fts,)).fetchone() is None:
        add(f"{fts}: table missing")


def verify(source: Path, db: Path, strict: bool = True) -> list[str]:
    """Return a list of problems (empty when the DB matches the XML)."""
    problems: list[str] = []

    def add(msg: str) -> None:
        problems.append(msg)

    exp_verses, exp_headings, chapter_counts, book_order = expected_from_xml(source)

    with contextlib.closing(sqlite3.connect(f"file:{db}?mode=ro", uri=True)) as conn:
        if conn.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            add("sqlite integrity_check failed")
        version = conn.execute("PRAGMA user_version").fetchone()[0]
        if version != EXPECTED_USER_VERSION:
            add(f"user_version: expected {EXPECTED_USER_VERSION}, found {version}")

        # Books
        db_books = conn.execute("SELECT id, name, chapter_count FROM books ORDER BY id").fetchall()
        if len(db_books) != len(CANONICAL_OSIS_BOOKS):
            add(f"books: expected {len(CANONICAL_OSIS_BOOKS)} rows, found {len(db_books)}")
        if [r[0] for r in db_books] != list(range(1, len(db_books) + 1)):
            add("books: ids are not contiguous from 1")
        for book_id, name, count in db_books:
            if strict and chapter_counts.get(book_id) != count:
                add(f"books: {name} chapter_count={count}, XML has {chapter_counts.get(book_id)}")
            elif chapter_counts.get(book_id, 0) > count:
                add(f"books: {name} chapter_count={count} is below XML chapter {chapter_counts[book_id]}")
        if strict:
            if book_order != CANONICAL_OSIS_BOOKS:
                add("XML book order does not match the canonical 66-book order")
            if len(exp_verses) != EXPECTED_VERSE_COUNT:
                add(f"XML has {len(exp_verses)} verses, expected {EXPECTED_VERSE_COUNT}")

        # Verses
        rows = conn.execute("SELECT rowid, book_id, chapter, verse, text, text_norm FROM verses ORDER BY rowid").fetchall()
        keys = [tuple(r[1:4]) for r in rows]
        if keys != sorted(keys):
            add("verses: rowids are not in canonical order")
        if set(keys) != set(exp_verses) or len(keys) != len(exp_verses):
            missing = sorted(set(exp_verses) - set(keys))
            extra = sorted(set(keys) - set(exp_verses))
            add(f"verses: key mismatch; count expected={len(exp_verses)} actual={len(keys)} "
                f"missing={missing[:5]} extra={extra[:5]}")
        mismatches = 0
        for _, b, c, v, text, norm in rows:
            key = (b, c, v)
            if key not in exp_verses:
                continue
            try:
                actual = styled_from_markers(text)
            except ValueError as exc:
                add(f"verse {key}: malformed markers ({exc}): {text[:80]!r}")
                continue
            if actual != exp_verses[key]:
                mismatches += 1
                if mismatches <= MAX_REPORTED:
                    add(f"verse {key}: {_describe_diff(exp_verses[key], actual)}")
            if norm != normalize(text):
                add(f"verse {key}: text_norm mismatch {norm[:60]!r}")
        if mismatches > MAX_REPORTED:
            add(f"... {mismatches - MAX_REPORTED} more verse mismatches")

        # Headings
        hrows = conn.execute(
            "SELECT id, book_id, chapter, verse, type, letter, text, text_norm FROM headings ORDER BY id"
        ).fetchall()
        actual_keys = [((r[1], r[2], r[3]), r[4]) for r in hrows]
        expected_keys = [(k, t) for k, t, _, _ in exp_headings]
        if actual_keys != expected_keys:
            missing = [k for k in expected_keys if k not in actual_keys]
            extra = [k for k in actual_keys if k not in expected_keys]
            add(f"headings: key mismatch; expected {len(expected_keys)} actual {len(actual_keys)} "
                f"missing={missing[:5]} extra={extra[:5]}")
        else:
            for row, (key, kind, letter, styled) in zip(hrows, exp_headings):
                _, _, _, _, _, db_letter, text, norm = row
                try:
                    actual = styled_from_markers(text)
                except ValueError as exc:
                    add(f"heading {key} {kind}: malformed markers ({exc})")
                    continue
                if actual != styled:
                    add(f"heading {key} {kind}: {_describe_diff(styled, actual)}")
                if db_letter != letter:
                    add(f"heading {key} {kind}: letter {db_letter!r} != {letter!r}")
                if norm != normalize(text):
                    add(f"heading {key} {kind}: text_norm mismatch")

        _check_fts(conn, "verses_fts4", "SELECT rowid, text_norm FROM verses ORDER BY rowid", add)
        _check_fts(conn, "headings_fts4", "SELECT id, text_norm FROM headings ORDER BY id", add)
    return problems


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Verify kjv.db against the OSIS XML source")
    parser.add_argument("--source", default=str(DEFAULT_SOURCE))
    parser.add_argument("--db", default=str(DEFAULT_DB))
    parser.add_argument("--allow-partial-source", action="store_true",
                        help="Skip whole-Bible completeness checks (test fixtures)")
    args = parser.parse_args(argv)
    source, db = Path(args.source).resolve(), Path(args.db).resolve()
    for path in (source, db):
        if not path.exists():
            print(f"ERROR: not found: {path}")
            return 1
    try:
        problems = verify(source, db, strict=not args.allow_partial_source)
    except (ValueError, sqlite3.Error) as exc:
        print(f"ERROR: {exc}")
        return 1
    if problems:
        print(f"FAILED: {db.name} does not match {source.name}")
        for p in problems:
            print(f"- {p}")
        return 1
    print(f"PASS: {db.name} matches {source.name} (verses, headings, styling, text_norm, FTS)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
