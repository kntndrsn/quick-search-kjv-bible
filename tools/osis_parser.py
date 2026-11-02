#!/usr/bin/env python3
"""Streaming OSIS parser for the KJV source (`tools/sources/kjvlite.xml`).

Produces verse rows whose text uses the app's inline-marker format:

- ``‹ … ›``  words of Jesus (``<q who="Jesus">``, container or sID/eID milestones)
- ``[ … ]``  translator additions, rendered in italics (``<transChange type="added">`` and
  ``<seg type="x-transChange">``)
- ``⸢ … ⸣``  divine name, rendered in small caps (``<divineName>``)

Study notes, paragraph milestones, Strong's/morphology attributes, and book or
chapter titles are dropped. Psalm superscriptions (``title type="psalm"``) and
acrostic stanza titles (``title type="acrostic"``) are emitted as headings
attached to the verse they precede. Heading text is plain except for any
inline markers inside it; the acrostic letter comes from ``<foreign n="...">``.
"""

from __future__ import annotations

import dataclasses
import re
import xml.sax
from pathlib import Path
from typing import Sequence

RED_OPEN, RED_CLOSE = "\u2039", "\u203A"
ADDED_OPEN, ADDED_CLOSE = "[", "]"
DIVINE_OPEN, DIVINE_CLOSE = "\u2E22", "\u2E23"
STYLE_MARKERS = RED_OPEN + RED_CLOSE + ADDED_OPEN + ADDED_CLOSE + DIVINE_OPEN + DIVINE_CLOSE

# (OSIS id, display name, chapter count) in canonical order; book_id = index + 1.
BOOKS: Sequence[tuple[str, str, int]] = (
    ("Gen", "Genesis", 50), ("Exod", "Exodus", 40), ("Lev", "Leviticus", 27),
    ("Num", "Numbers", 36), ("Deut", "Deuteronomy", 34), ("Josh", "Joshua", 24),
    ("Judg", "Judges", 21), ("Ruth", "Ruth", 4), ("1Sam", "1 Samuel", 31),
    ("2Sam", "2 Samuel", 24), ("1Kgs", "1 Kings", 22), ("2Kgs", "2 Kings", 25),
    ("1Chr", "1 Chronicles", 29), ("2Chr", "2 Chronicles", 36), ("Ezra", "Ezra", 10),
    ("Neh", "Nehemiah", 13), ("Esth", "Esther", 10), ("Job", "Job", 42),
    ("Ps", "Psalms", 150), ("Prov", "Proverbs", 31), ("Eccl", "Ecclesiastes", 12),
    ("Song", "Song of Solomon", 8), ("Isa", "Isaiah", 66), ("Jer", "Jeremiah", 52),
    ("Lam", "Lamentations", 5), ("Ezek", "Ezekiel", 48), ("Dan", "Daniel", 12),
    ("Hos", "Hosea", 14), ("Joel", "Joel", 3), ("Amos", "Amos", 9),
    ("Obad", "Obadiah", 1), ("Jonah", "Jonah", 4), ("Mic", "Micah", 7),
    ("Nah", "Nahum", 3), ("Hab", "Habakkuk", 3), ("Zeph", "Zephaniah", 3),
    ("Hag", "Haggai", 2), ("Zech", "Zechariah", 14), ("Mal", "Malachi", 4),
    ("Matt", "Matthew", 28), ("Mark", "Mark", 16), ("Luke", "Luke", 24),
    ("John", "John", 21), ("Acts", "Acts", 28), ("Rom", "Romans", 16),
    ("1Cor", "1 Corinthians", 16), ("2Cor", "2 Corinthians", 13), ("Gal", "Galatians", 6),
    ("Eph", "Ephesians", 6), ("Phil", "Philippians", 4), ("Col", "Colossians", 4),
    ("1Thess", "1 Thessalonians", 5), ("2Thess", "2 Thessalonians", 3),
    ("1Tim", "1 Timothy", 6), ("2Tim", "2 Timothy", 4), ("Titus", "Titus", 3),
    ("Phlm", "Philemon", 1), ("Heb", "Hebrews", 13), ("Jas", "James", 5),
    ("1Pet", "1 Peter", 5), ("2Pet", "2 Peter", 3), ("1John", "1 John", 5),
    ("2John", "2 John", 1), ("3John", "3 John", 1), ("Jude", "Jude", 1),
    ("Rev", "Revelation", 22),
)
OSIS_TO_BOOK_ID: dict[str, int] = {osis: i for i, (osis, _, _) in enumerate(BOOKS, start=1)}
EXPECTED_VERSE_COUNT = 31102
HEADING_TYPES = ("psalm", "acrostic")
_CLOSER = {RED_OPEN: RED_CLOSE, ADDED_OPEN: ADDED_CLOSE, DIVINE_OPEN: DIVINE_CLOSE}

_WS = re.compile(r"\s+")
_EMPTY_PAIRS = re.compile(
    "|".join(re.escape(o) + r"\s*" + re.escape(c) for o, c in (
        (RED_OPEN, RED_CLOSE), (ADDED_OPEN, ADDED_CLOSE), (DIVINE_OPEN, DIVINE_CLOSE)))
)
_VERSE_ID = re.compile(r"^([1-3]?[A-Za-z]+)\.(\d+)\.(\d+)$")

_NORM_DELETE = str.maketrans({"\u00e6": "ae", "\u00c6": "ae", **{c: None for c in STYLE_MARKERS + ",;-\u2013"}})
_NORM_REJECT = re.compile(r"[^a-z0-9 ]")


@dataclasses.dataclass(frozen=True)
class Verse:
    book_id: int
    chapter: int
    verse: int
    text: str

    @property
    def book(self) -> str:
        return BOOKS[self.book_id - 1][1]

    @property
    def ref(self) -> str:
        return f"{self.book} {self.chapter}:{self.verse}"


@dataclasses.dataclass(frozen=True)
class Heading:
    book_id: int
    chapter: int
    verse: int
    type: str
    letter: str | None
    text: str

    @property
    def ref(self) -> str:
        return f"{BOOKS[self.book_id - 1][1]} {self.chapter}:{self.verse} ({self.type})"


@dataclasses.dataclass(frozen=True)
class ParsedBible:
    verses: list[Verse]
    headings: list[Heading]


def normalize_search_text(text: str) -> str:
    """Python mirror of Kotlin SearchNormalizer.normalizeVerseText."""
    cleaned = _NORM_REJECT.sub(" ", text.translate(_NORM_DELETE).lower())
    return _WS.sub(" ", cleaned).strip()


def strip_markers(text: str) -> str:
    return text.translate(str.maketrans("", "", STYLE_MARKERS))


def tidy(text: str) -> str:
    """Collapse whitespace and drop empty marker pairs; also move edge spaces outside markers."""
    text = _WS.sub(" ", text)
    previous = None
    while previous != text:
        previous = text
        text = _EMPTY_PAIRS.sub(" ", text)
        for opener in (RED_OPEN, ADDED_OPEN, DIVINE_OPEN):
            text = text.replace(opener + " ", " " + opener)
        for closer in (RED_CLOSE, ADDED_CLOSE, DIVINE_CLOSE):
            text = text.replace(" " + closer, closer + " ")
        text = _WS.sub(" ", text)
    return text.strip()


def parse_verse_id(osis_id: str) -> tuple[int, int, int]:
    match = _VERSE_ID.match(osis_id)
    if not match or match.group(1) not in OSIS_TO_BOOK_ID:
        raise ValueError(f"Unrecognized verse osisID: {osis_id!r}")
    return OSIS_TO_BOOK_ID[match.group(1)], int(match.group(2)), int(match.group(3))


class _Handler(xml.sax.ContentHandler):
    def __init__(self) -> None:
        super().__init__()
        self.verses: list[Verse] = []
        self.headings: list[Heading] = []
        self.stray_text: list[str] = []
        self._stack: list[str] = []          # style opener (or "") to close for each open element
        self._names: list[str] = []          # open element names
        self._skip_depth = 0                 # >0 while inside dropped content (notes, ignored titles)
        self._verse_ref: tuple[int, int, int] | None = None
        self._verse_buf: list[str] = []
        self._active: list[str] = []         # style openers currently in effect (may span verses)
        self._heading: dict | None = None
        self._pending_headings: list[dict] = []

    # -- output helpers -------------------------------------------------
    def _target(self) -> list[str] | None:
        if self._heading is not None:
            return self._heading["buf"]
        if self._verse_ref is not None:
            return self._verse_buf
        return None

    def _emit(self, text: str) -> None:
        target = self._target()
        if target is not None:
            target.append(text)

    def _open_style(self, opener: str) -> None:
        self._active.append(opener)
        self._emit(opener)

    def _close_style(self, opener: str) -> None:
        index = len(self._active) - 1 - self._active[::-1].index(opener)
        del self._active[index]
        self._emit(_CLOSER[opener])

    # -- SAX callbacks --------------------------------------------------
    def startElement(self, name: str, attrs) -> None:  # noqa: N802 (SAX API)
        closer = ""
        self._names.append(name)
        if self._skip_depth:
            self._skip_depth += 1
            self._stack.append(closer)
            return

        if name == "verse":
            if attrs.get("sID"):
                self._start_verse(attrs.get("osisID") or attrs["sID"])
            elif attrs.get("eID"):
                self._end_verse(attrs["eID"])
        elif name == "note":
            self._skip_depth = 1
        elif name == "title":
            title_type = attrs.get("type")
            if title_type in HEADING_TYPES:
                if self._heading is not None:
                    raise ValueError("Nested heading titles are not supported")
                self._heading = {"type": title_type, "letter": None, "buf": []}
                closer = "\0heading"
            else:
                self._skip_depth = 1
        elif name == "header":
            self._skip_depth = 1
        elif name == "q" and attrs.get("who") == "Jesus" and attrs.get("sID"):
            self._open_style(RED_OPEN)
        elif name == "q" and attrs.get("eID") and RED_OPEN in self._active:
            self._close_style(RED_OPEN)
        elif name == "q" and attrs.get("who") == "Jesus":
            self._open_style(RED_OPEN)
            closer = RED_OPEN
        elif (name == "transChange" and attrs.get("type") == "added") or (
            name == "seg" and attrs.get("type") == "x-transChange"
        ):
            self._open_style(ADDED_OPEN)
            closer = ADDED_OPEN
        elif name == "divineName":
            self._open_style(DIVINE_OPEN)
            closer = DIVINE_OPEN
        elif name == "foreign" and self._heading is not None and attrs.get("n"):
            self._heading["letter"] = attrs["n"]
        self._stack.append(closer)

    def endElement(self, name: str) -> None:  # noqa: N802 (SAX API)
        closer = self._stack.pop()
        self._names.pop()
        if self._skip_depth:
            self._skip_depth -= 1
            return
        if closer == "\0heading":
            self._finish_heading()
        elif closer:
            self._close_style(closer)

    def characters(self, content: str) -> None:
        if self._skip_depth:
            return
        target = self._target()
        if target is not None:
            target.append(content)
        elif content.strip() and "chapter" in self._names:
            self.stray_text.append(content.strip())

    # -- verse / heading lifecycle -------------------------------------
    def _start_verse(self, osis_id: str) -> None:
        if self._verse_ref is not None:
            raise ValueError(f"Verse {osis_id} starts before previous verse ended")
        self._verse_ref = parse_verse_id(osis_id)
        self._verse_buf = list(self._active)
        for pending in self._pending_headings:
            self._add_heading(pending)
        self._pending_headings.clear()

    def _end_verse(self, osis_id: str) -> None:
        ref = parse_verse_id(osis_id)
        if self._verse_ref != ref:
            raise ValueError(f"Verse end {osis_id} does not match open verse {self._verse_ref}")
        self._verse_buf.extend(_CLOSER[o] for o in reversed(self._active))
        text = tidy("".join(self._verse_buf))
        if not strip_markers(text).strip():
            raise ValueError(f"Empty verse text for {osis_id}")
        self.verses.append(Verse(*ref, text=text))
        self._verse_ref = None
        self._verse_buf = []

    def _finish_heading(self) -> None:
        heading = self._heading
        self._heading = None
        assert heading is not None
        if self._verse_ref is not None:
            self._add_heading(heading)
        else:
            self._pending_headings.append(heading)

    def _add_heading(self, heading: dict) -> None:
        assert self._verse_ref is not None
        text = tidy("".join(heading["buf"]))
        self.headings.append(Heading(*self._verse_ref, type=heading["type"], letter=heading["letter"], text=text))


def parse_osis(source_path: Path, strict: bool = True) -> ParsedBible:
    handler = _Handler()
    parser = xml.sax.make_parser()
    parser.setFeature(xml.sax.handler.feature_namespaces, False)
    parser.setContentHandler(handler)
    with source_path.open("rb") as f:
        parser.parse(f)

    if handler._pending_headings:
        raise ValueError("Heading title(s) found with no following verse")
    if handler.stray_text:
        sample = "; ".join(handler.stray_text[:5])
        raise ValueError(f"{len(handler.stray_text)} text run(s) found outside verses: {sample}")
    verses = handler.verses
    if not verses:
        raise ValueError("No verses parsed from OSIS source")
    validate_sequence(verses, strict=strict)
    return ParsedBible(verses=verses, headings=handler.headings)


def validate_sequence(verses: Sequence[Verse], strict: bool) -> None:
    """Fail fast on duplicate, missing, or out-of-order references and malformed text."""
    prev: Verse | None = None
    for v in verses:
        _validate_markers(v)
        if prev is not None:
            pk, ck = (prev.book_id, prev.chapter, prev.verse), (v.book_id, v.chapter, v.verse)
            if ck <= pk:
                raise ValueError(f"Out-of-order or duplicate reference at {prev.ref} -> {v.ref}")
            if strict:
                same_book = v.book_id == prev.book_id
                if same_book and v.chapter == prev.chapter:
                    ok = v.verse == prev.verse + 1
                elif same_book:
                    ok = v.chapter == prev.chapter + 1 and v.verse == 1
                else:
                    ok = v.book_id == prev.book_id + 1 and v.chapter == 1 and v.verse == 1
                if not ok:
                    raise ValueError(f"Reference gap at {prev.ref} -> {v.ref}")
        prev = v

    if strict:
        first, last = verses[0], verses[-1]
        if (first.book_id, first.chapter, first.verse) != (1, 1, 1):
            raise ValueError("Parsed stream does not start at Genesis 1:1")
        if (last.book_id, last.chapter, last.verse) != (66, 22, 21):
            raise ValueError("Parsed stream does not end at Revelation 22:21")
        if len(verses) != EXPECTED_VERSE_COUNT:
            raise ValueError(f"Expected {EXPECTED_VERSE_COUNT} verses, parsed {len(verses)}")
        chapters: dict[int, int] = {}
        for v in verses:
            chapters[v.book_id] = max(chapters.get(v.book_id, 0), v.chapter)
        for book_id, (_, name, count) in enumerate(BOOKS, start=1):
            if chapters.get(book_id) != count:
                raise ValueError(f"{name}: expected {count} chapters, parsed {chapters.get(book_id)}")


def _validate_markers(v: Verse) -> None:
    if "<" in v.text or ">" in v.text or "\u00B6" in v.text:
        raise ValueError(f"Unexpected markup in {v.ref}: {v.text[:80]!r}")
    for opener, closer in ((RED_OPEN, RED_CLOSE), (ADDED_OPEN, ADDED_CLOSE), (DIVINE_OPEN, DIVINE_CLOSE)):
        depth = 0
        for ch in v.text:
            if ch == opener:
                depth += 1
                if depth > 1:
                    raise ValueError(f"Nested {opener} markers in {v.ref}")
            elif ch == closer:
                depth -= 1
                if depth < 0:
                    raise ValueError(f"Unbalanced {closer} marker in {v.ref}")
        if depth:
            raise ValueError(f"Unclosed {opener} marker in {v.ref}")


if __name__ == "__main__":  # pragma: no cover - manual inspection helper
    import sys

    src = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / "sources" / "kjvlite.xml"
    bible = parse_osis(src)
    print(f"Verses: {len(bible.verses)}  Headings: {len(bible.headings)}")
