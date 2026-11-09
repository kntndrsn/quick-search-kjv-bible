"""Verify all exported web records against Android and emit search parity fixtures."""
import gzip
import json
from pathlib import Path
import sqlite3
import sys

ROOT = Path(__file__).resolve().parents[2]
output = ROOT / "web" / "public"
manifest = json.loads((output / "data" / "manifest.json").read_bytes())


def read(descriptor):
    path = output / descriptor["path"]
    plain = path.read_bytes()
    assert gzip.decompress(path.with_suffix(".json.gz").read_bytes()) == plain
    return json.loads(plain)


with sqlite3.connect(ROOT / "app" / "src" / "main" / "assets" / "kjv.db") as db:
    db.row_factory = sqlite3.Row
    verses = [dict(row) for row in db.execute("SELECT book_id, chapter, verse, text, text_norm FROM verses ORDER BY rowid")]
    headings = [dict(row) for row in db.execute("SELECT * FROM headings ORDER BY id")]
    search = read(manifest["search"])
    assert len(search["verses"]) == len(verses) == 31102
    assert len(search["headings"]) == len(headings)
    assert len(manifest["chapters"]) == 1189
    chapter_rows = {}
    for key, descriptor in manifest["chapters"].items():
        chapter_rows[key] = read(descriptor)
    assert sum(len(rows) for rows in chapter_rows.values()) == 31102
    for source, exported in zip(verses, search["verses"]):
        assert (source["book_id"], source["chapter"], source["verse"], source["text"], source["text_norm"]) == (
            exported["bookId"], exported["chapter"], exported["verse"], exported["text"], exported["normalized"])
        chapter_verse = chapter_rows[f'{source["book_id"]}-{source["chapter"]}'][source["verse"] - 1]
        assert chapter_verse["text"] == source["text"]
        assert chapter_verse["verse"] == source["verse"]
        expected_headings = [
            {"bookId": h["book_id"], **{k: h[k] for k in ("chapter", "verse", "type", "letter", "text")}}
            for h in headings
            if (h["book_id"], h["chapter"], h["verse"]) == (source["book_id"], source["chapter"], source["verse"])
        ]
        assert chapter_verse["headings"] == expected_headings
    for source, exported in zip(headings, search["headings"]):
        assert (source["id"], source["text_norm"], source["text"], source["type"], source["letter"]) == (
            exported["id"], exported["normalized"], exported["text"], exported["type"], exported["letter"])

    queries = json.loads(sys.argv[1]) if len(sys.argv) > 1 else []
    fixtures = {}
    for query in queries:
        if len(query) < 3:
            fixtures[query] = {"matchingVerses": [], "matchingHeadings": []}
            continue
        match = " ".join(f'"{word}{"*" if i == len(query.split()) - 1 else ""}"' for i, word in enumerate(query.split()))
        result = {}
        for table, limit, key in (("verses", 25, "matchingVerses"), ("headings", 10, "matchingHeadings")):
            id_col = "rowid" if table == "verses" else "id"
            ordering = "" if table == "verses" else " ORDER BY r.id"
            selected = db.execute(
                f"SELECT r.{id_col} AS record_id FROM {table}_fts4 f JOIN {table} r "
                f"ON r.{id_col} = f.docid WHERE {table}_fts4 MATCH ?{ordering} LIMIT {limit}", (match,)
            ).fetchall()
            ids = [row["record_id"] for row in selected]
            for row in db.execute(f"SELECT {id_col} AS record_id FROM {table} WHERE text_norm LIKE ?{ordering.replace('r.', '')} LIMIT {limit}", (f"%{query}%",)):
                if len(ids) >= limit:
                    break
                if row["record_id"] not in ids:
                    ids.append(row["record_id"])
            records = search["verses" if table == "verses" else "headings"]
            result[key] = [{k: v for k, v in records[i - 1].items() if k != "normalized"} for i in ids]
        fixtures[query] = result
    print(json.dumps(fixtures, ensure_ascii=False))
