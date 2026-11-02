import contextlib
import shutil
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from import_osis_to_sqlite import main as import_main
from osis_parser import normalize_search_text
from test_osis_parser import FIXTURE
from verify_osis_db import main as verify_main
from verify_osis_db import verify

ROOT = Path(__file__).resolve().parents[1]
ASSET_DB = ROOT / "app" / "src" / "main" / "assets" / "kjv.db"
SOURCE_XML = ROOT / "tools" / "sources" / "kjvlite.xml"


class ImportOsisToSqliteTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.tmp = Path(self._tmp.name)
        self.source = self.tmp / "fixture.xml"
        self.source.write_text(FIXTURE, encoding="utf-8")
        self.db = self.tmp / "kjv.db"

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def _import(self) -> int:
        return import_main(["--source", str(self.source), "--db", str(self.db), "--allow-partial-source"])

    def test_import_builds_schema_and_verifies(self) -> None:
        self.assertEqual(0, self._import())
        with contextlib.closing(sqlite3.connect(str(self.db))) as conn:
            self.assertEqual(5, conn.execute("PRAGMA user_version").fetchone()[0])
            self.assertEqual(4096, conn.execute("PRAGMA page_size").fetchone()[0])
            self.assertEqual(66, conn.execute("SELECT COUNT(*) FROM books").fetchone()[0])
            self.assertEqual(10, conn.execute("SELECT COUNT(*) FROM verses").fetchone()[0])
            self.assertIsNone(conn.execute("SELECT name FROM sqlite_master WHERE name = 'verses_fts'").fetchone())
            self.assertIn("fts4(text_norm", conn.execute(
                "SELECT sql FROM sqlite_master WHERE name = 'verses_fts4'").fetchone()[0])
            self.assertEqual(
                ("And darkness [was] upon the deep.", "and darkness was upon the deep"),
                conn.execute("SELECT text, text_norm FROM verses WHERE book_id=1 AND chapter=1 AND verse=2").fetchone(),
            )
            self.assertEqual(
                conn.execute("SELECT rowid FROM verses WHERE book_id=1 AND chapter=1 AND verse=2").fetchone()[0],
                conn.execute("SELECT docid FROM verses_fts4 WHERE text_norm MATCH 'darkness'").fetchone()[0],
            )
            self.assertEqual(
                [(19, 119, 1, "psalm", None, "A Psalm of David.", "a psalm of david"),
                 (19, 119, 1, "acrostic", "\u05d0", "ALEPH.", "aleph")],
                conn.execute(
                    "SELECT book_id, chapter, verse, type, letter, text, text_norm FROM headings ORDER BY id"
                ).fetchall(),
            )
            self.assertEqual(
                [(1,)],
                conn.execute("SELECT docid FROM headings_fts4 WHERE text_norm MATCH 'psalm david'").fetchall(),
            )

    def test_verifier_detects_tampering(self) -> None:
        self.assertEqual(0, self._import())
        tampering = {
            "text": "UPDATE verses SET text = 'In the beginning.' WHERE book_id=1 AND chapter=1 AND verse=1",
            "italic": "UPDATE verses SET text = 'And darkness was upon the deep.' WHERE book_id=1 AND verse=2",
            "red": "UPDATE verses SET text = 'Sin no more. And she went.' WHERE book_id=40 AND verse=6",
            "divine": "UPDATE verses SET text = replace(text, '\u2e22', '') WHERE book_id=2",
            "norm": "UPDATE verses SET text_norm = 'x' WHERE book_id=1 AND verse=1",
            "fts": "DELETE FROM verses_fts4 WHERE docid = 1",
            "missing verse": "DELETE FROM verses WHERE book_id=40 AND verse=2",
            "heading": "UPDATE headings SET text = 'ALEPH' WHERE type = 'acrostic'",
            "heading fts": "DELETE FROM headings_fts4",
        }
        for name, sql in tampering.items():
            with self.subTest(name):
                copy = self.tmp / f"tampered_{name.replace(' ', '_')}.db"
                shutil.copy(self.db, copy)
                with contextlib.closing(sqlite3.connect(str(copy))) as conn:
                    conn.execute(sql)
                    conn.commit()
                self.assertTrue(verify(self.source, copy, strict=False), name)
                self.assertEqual(
                    1, verify_main(["--source", str(self.source), "--db", str(copy), "--allow-partial-source"])
                )


@unittest.skipUnless(ASSET_DB.exists() and SOURCE_XML.exists(), "bundled kjv.db or OSIS source missing")
class BundledAssetTest(unittest.TestCase):
    """Checks the committed asset against the full OSIS source."""

    def test_full_asset_matches_osis_source(self) -> None:
        # Every verse, chapter, heading, and per-character italic/red/divine-name flag, strict mode.
        self.assertEqual([], verify(SOURCE_XML, ASSET_DB, strict=True))

    def test_sample_verses_and_kotlin_normalization_parity(self) -> None:
        expected = {
            (1, 1, 1): ("In the beginning God created the heaven and the earth.",
                        "in the beginning god created the heaven and the earth"),
            (19, 23, 1): ("The \u2e22Lord\u2e23 [is] my shepherd; I shall not want.",
                          "the lord is my shepherd i shall not want"),
            (19, 119, 1): ("Blessed [are] the undefiled in the way, who walk in the law of the \u2e22Lord\u2e23.",
                           "blessed are the undefiled in the way who walk in the law of the lord"),
            (40, 5, 3): ("\u2039Blessed [are] the poor in spirit: for theirs is the kingdom of heaven.\u203a",
                         "blessed are the poor in spirit for theirs is the kingdom of heaven"),
            (43, 11, 35): ("Jesus wept.", "jesus wept"),
        }
        with contextlib.closing(sqlite3.connect(str(ASSET_DB))) as conn:
            self.assertEqual(5, conn.execute("PRAGMA user_version").fetchone()[0])
            self.assertEqual(31102, conn.execute("SELECT COUNT(*) FROM verses").fetchone()[0])
            self.assertEqual(138, conn.execute("SELECT COUNT(*) FROM headings").fetchone()[0])
            for key, (text, norm) in expected.items():
                row = conn.execute(
                    "SELECT text, text_norm FROM verses WHERE book_id=? AND chapter=? AND verse=?", key
                ).fetchone()
                self.assertEqual((text, norm), row, key)
                self.assertEqual(norm, normalize_search_text(text), key)
            self.assertEqual(
                ("psalm", None, "A Psalm of David."),
                conn.execute("SELECT type, letter, text FROM headings WHERE book_id=19 AND chapter=23").fetchone(),
            )


if __name__ == "__main__":
    unittest.main()
