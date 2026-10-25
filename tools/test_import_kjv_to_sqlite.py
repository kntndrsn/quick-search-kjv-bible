import contextlib
import json
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from import_kjv_to_sqlite import main, normalize_verse_text
from kjv_integrity import parse_kjv_source


ROOT = Path(__file__).resolve().parents[1]
ASSET_DB = ROOT / "app" / "src" / "main" / "assets" / "kjv.db"
SOURCE = ROOT / "tools" / "sources" / "kjv_biblesupersearch.txt"


class ImportKJVToSqliteTest(unittest.TestCase):
    def test_import_and_verify_small_fixture(self) -> None:
        with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp_dir:
            tmp = Path(tmp_dir)
            source = tmp / "source.txt"
            jsonl = tmp / "verses.jsonl"
            db = tmp / "kjv.db"

            source.write_text(
                """Genesis 1:1 In the beginning God created the heaven and the earth.
Genesis 1:2 And the earth was without form, and void; and darkness was upon the face of the deep.
""",
                encoding="utf-8",
            )

            rows = [
                {
                    "book_id": 1,
                    "book": "Genesis",
                    "chapter": 1,
                    "verse": 1,
                    "text": "In the beginning God created the heaven and the earth.",
                },
                {
                    "book_id": 1,
                    "book": "Genesis",
                    "chapter": 1,
                    "verse": 2,
                    "text": "And the earth was without form, and void; and darkness was upon the face of the deep.",
                },
            ]
            jsonl.write_text("\n".join(json.dumps(r) for r in rows) + "\n", encoding="utf-8")

            rc = main([
                "--input",
                str(jsonl),
                "--db",
                str(db),
                "--source",
                str(source),
                "--allow-partial-source",
            ])
            self.assertEqual(0, rc)
            self.assertTrue(db.exists())

            with contextlib.closing(sqlite3.connect(str(db))) as conn:
                verse_count = conn.execute("SELECT COUNT(*) FROM verses").fetchone()[0]
                book_count = conn.execute("SELECT COUNT(*) FROM books").fetchone()[0]
                self.assertEqual(2, verse_count)
                self.assertEqual(66, book_count)
                self.assertEqual(2, conn.execute("PRAGMA user_version").fetchone()[0])
                self.assertEqual(4096, conn.execute("PRAGMA page_size").fetchone()[0])
                self.assertEqual("ok", conn.execute("PRAGMA integrity_check").fetchone()[0])
                self.assertIsNone(
                    conn.execute(
                        "SELECT name FROM sqlite_master WHERE name = 'verses_fts'"
                    ).fetchone()
                )
                fts_sql = conn.execute(
                    "SELECT sql FROM sqlite_master WHERE name = 'verses_fts4'"
                ).fetchone()[0]
                self.assertIn("fts4(text_norm", fts_sql)
                self.assertEqual(
                    "and the earth was without form and void and darkness was upon the face of the deep",
                    conn.execute(
                        "SELECT text_norm FROM verses WHERE book_id = 1 AND chapter = 1 AND verse = 2"
                    ).fetchone()[0],
                )
                self.assertEqual(
                    conn.execute(
                        "SELECT rowid FROM verses WHERE book_id = 1 AND chapter = 1 AND verse = 2"
                    ).fetchone()[0],
                    conn.execute(
                        "SELECT docid FROM verses_fts4 WHERE text_norm MATCH 'darkness'"
                    ).fetchone()[0],
                )

    def test_python_kotlin_normalization_parity_for_actual_sample_verses(self) -> None:
        expected = {
            (1, 1, 1): "in the beginning god created the heaven and the earth",
            (2, 20, 3): "thou shalt have no other gods before me",
            (19, 23, 1): "a psalm of david the lord is my shepherd i shall not want",
            (20, 3, 5): "trust in the lord with all thine heart and lean not unto thine own understanding",
            (23, 53, 5): "but he was wounded for our transgressions he was bruised for our iniquities the chastisement of our peace was upon him and with his stripes we are healed",
            (40, 5, 3): "blessed are the poor in spirit for theirs is the kingdom of heaven",
            (43, 3, 16): "for god so loved the world that he gave his only begotten son that whosoever believeth in him should not perish but have everlasting life",
            (43, 11, 35): "jesus wept",
            (45, 8, 28): "and we know that all things work together for good to them that love god to them who are the called according to his purpose",
            (66, 22, 21): "the grace of our lord jesus christ be with you all amen",
        }
        source_verses = parse_kjv_source(SOURCE)
        actual_text = {
            (verse.book_id, verse.chapter, verse.verse): verse.text
            for verse in source_verses
        }
        self.assertTrue(set(expected).issubset(actual_text))

        with contextlib.closing(sqlite3.connect(str(ASSET_DB))) as conn:
            row_order = conn.execute(
                "SELECT book_id, chapter, verse FROM verses ORDER BY rowid"
            ).fetchall()
            self.assertEqual(sorted(row_order), row_order)
            for reference, kotlin_normalized in expected.items():
                book_id, chapter, verse = reference
                source_text = actual_text[reference]
                db_text, db_normalized = conn.execute(
                    "SELECT text, text_norm FROM verses "
                    "WHERE book_id = ? AND chapter = ? AND verse = ?",
                    reference,
                ).fetchone()
                self.assertEqual(source_text, db_text, reference)
                # This is the Python mirror of Kotlin normalizeVerseQuery:
                # strip source-only markers first, then lowercase, allow ASCII
                # letters/digits/spaces, collapse whitespace, and trim.
                self.assertEqual(kotlin_normalized, normalize_verse_text(source_text), reference)
                self.assertEqual(kotlin_normalized, db_normalized, reference)

    def test_import_fails_when_jsonl_mismatches_source(self) -> None:
        with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp_dir:
            tmp = Path(tmp_dir)
            source = tmp / "source.txt"
            jsonl = tmp / "verses.jsonl"
            db = tmp / "kjv.db"

            source.write_text(
                """Genesis 1:1 In the beginning God created the heaven and the earth.
""",
                encoding="utf-8",
            )

            jsonl.write_text(
                json.dumps(
                    {
                        "book_id": 1,
                        "book": "Genesis",
                        "chapter": 1,
                        "verse": 1,
                        "text": "Modified text",
                    }
                )
                + "\n",
                encoding="utf-8",
            )

            rc = main([
                "--input",
                str(jsonl),
                "--db",
                str(db),
                "--source",
                str(source),
                "--allow-partial-source",
            ])
            self.assertEqual(1, rc)


if __name__ == "__main__":
    unittest.main()

