"""Export static web payloads without writing the Android database."""

import dataclasses
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from osis_parser import BOOKS, normalize_search_text, parse_osis


def encode(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def compress(value):
    compressed = bytearray(gzip.compress(value, mtime=0))
    compressed[9] = 255  # Normalize the gzip OS byte across build platforms.
    return bytes(compressed)


def generate(output):
    previous = None
    if (output / "manifest.json").exists():
        previous = json.loads((output / "manifest.json").read_bytes()).get("version")
    bible = parse_osis(ROOT / "tools" / "sources" / "kjvlite.xml")
    chapters = {}
    headings = {}
    for index, heading in enumerate(bible.headings, 1):
        row = dataclasses.asdict(heading)
        row = {
            "bookId": row.pop("book_id"), **row, "id": index,
            "normalized": normalize_search_text(heading.text),
        }
        headings.setdefault((heading.book_id, heading.chapter, heading.verse), []).append(row)
    verses = []
    for verse in bible.verses:
        row = {
            "bookId": verse.book_id, "chapter": verse.chapter,
            "verse": verse.verse, "text": verse.text,
            "headings": [
                {k: v for k, v in h.items() if k not in ("id", "normalized")}
                for h in headings.get((verse.book_id, verse.chapter, verse.verse), [])
            ],
        }
        chapters.setdefault(f"{verse.book_id}-{verse.chapter}", []).append(row)
        verses.append({**row, "headings": [], "normalized": normalize_search_text(verse.text)})
    heading_rows = sorted(
        (h for group in headings.values() for h in group),
        key=lambda h: (h["bookId"], h["chapter"], h["verse"], h["id"]),
    )
    for index, heading in enumerate(heading_rows, 1):
        heading["id"] = index

    def index_records(records):
        index = {}
        for position, row in enumerate(records):
            for token in sorted(set(row["normalized"].split())):
                index.setdefault(token, []).append(position)
        return index

    payloads = {f"chapters/{key}": encode(rows) for key, rows in chapters.items()}
    payloads["search"] = encode({
        "verses": verses, "headings": heading_rows,
        "verseIndex": index_records(verses), "headingIndex": index_records(heading_rows),
    })
    if len(verses) != 31102 or len(chapters) != sum(b[2] for b in BOOKS):
        raise ValueError("Incomplete Bible export")
    compressed_payloads = {name: compress(plain) for name, plain in payloads.items()}
    version = hashlib.sha256(encode({
        name: [hashlib.sha256(payloads[name]).hexdigest(),
               hashlib.sha256(compressed_payloads[name]).hexdigest()]
        for name in sorted(payloads)
    })).hexdigest()[:20]
    target = output / version
    manifest = {"version": version, "chapters": {}, "search": None}
    for name, plain in payloads.items():
        compressed = compressed_payloads[name]
        path = target / f"{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(plain)
        path.with_suffix(".json.gz").write_bytes(compressed)
        descriptor = {
            "path": f"data/{version}/{name}.json",
            "bytes": len(plain), "gzipBytes": len(compressed),
            "sha256": hashlib.sha256(plain).hexdigest(),
            "gzipSha256": hashlib.sha256(compressed).hexdigest(),
        }
        if name == "search":
            manifest["search"] = descriptor
        else:
            manifest["chapters"][name.split("/")[1]] = descriptor
    output.mkdir(parents=True, exist_ok=True)
    manifest_bytes = encode(manifest)
    (output / "manifest.json").write_bytes(manifest_bytes)
    (output / "manifest.json.gz").write_bytes(compress(manifest_bytes))
    if previous and previous != version and re.fullmatch(r"[0-9a-f]{20}", previous):
        previous_dir = output / previous
        if (previous_dir.is_dir() and not previous_dir.is_symlink()
                and previous_dir.resolve().parent == output.resolve()):
            shutil.rmtree(previous_dir)
    print(f"Exported {len(verses)} verses, {len(chapters)} chapters; "
          f"search gzip: {manifest['search']['gzipBytes']:,} bytes")


if __name__ == "__main__":
    generate(ROOT / "web" / "public" / "data")
