import contextlib
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from build_kjv_pipeline import main


class BuildKJVPipelineTest(unittest.TestCase):
    def test_pipeline_runs_end_to_end_for_fixture(self) -> None:
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

            rc = main([
                "--source",
                str(source),
                "--jsonl",
                str(jsonl),
                "--db",
                str(db),
                "--allow-partial-source",
            ])

            self.assertEqual(0, rc)
            self.assertTrue(jsonl.exists())
            self.assertTrue(db.exists())

            with contextlib.closing(sqlite3.connect(str(db))) as conn:
                count = conn.execute("SELECT COUNT(*) FROM verses").fetchone()[0]
                self.assertEqual(2, count)


if __name__ == "__main__":
    unittest.main()

