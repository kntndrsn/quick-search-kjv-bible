import json
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from import_kjv_to_sqlite import main


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

            with sqlite3.connect(str(db)) as conn:
                verse_count = conn.execute("SELECT COUNT(*) FROM verses").fetchone()[0]
                book_count = conn.execute("SELECT COUNT(*) FROM books").fetchone()[0]
                self.assertEqual(2, verse_count)
                self.assertEqual(66, book_count)

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



