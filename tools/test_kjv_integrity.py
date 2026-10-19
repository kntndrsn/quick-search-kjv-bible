import tempfile
import unittest
from pathlib import Path
import sys
import os

sys.path.insert(0, str(Path(__file__).resolve().parent))

from kjv_integrity import compare_verses, parse_kjv_source


class KJVIntegrityTest(unittest.TestCase):
    def _write_fixture(self, content: str) -> Path:
        tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".txt", mode="w", encoding="utf-8")
        tmp.write(content)
        tmp.close()
        return Path(tmp.name)

    def test_parses_line_based_verses(self) -> None:
        fixture = """Authorized King James Version
Genesis 1:1 In the beginning God created the heaven and the earth.
Genesis 1:2 And the earth was without form, and void; and darkness [was] upon the face of the deep.
"""
        path = self._write_fixture(fixture)
        verses = parse_kjv_source(path, strict=False)

        self.assertEqual(2, len(verses))
        self.assertEqual("Genesis 1:1", verses[0].ref)
        self.assertEqual("Genesis 1:2", verses[1].ref)
        self.assertIn("darkness [was]", verses[1].text)

    def test_compare_reports_text_difference(self) -> None:
        fixture = """Genesis 1:1 In the beginning God created the heaven and the earth.
"""
        path = self._write_fixture(fixture)
        verses = parse_kjv_source(path, strict=False)
        altered = [verses[0].__class__(
            book_id=verses[0].book_id,
            book=verses[0].book,
            chapter=verses[0].chapter,
            verse=verses[0].verse,
            text="Different text",
        )]

        diffs = compare_verses(verses, altered)
        self.assertTrue(diffs)
        self.assertIn("Text mismatch", diffs[0])

    def test_detects_verse_sequence_gaps(self) -> None:
        fixture = """Genesis 1:1 In the beginning.
Genesis 1:3 And God said, Let there be light.
"""
        path = self._write_fixture(fixture)
        with self.assertRaisesRegex(ValueError, "Verse sequence gap"):
            parse_kjv_source(path, strict=True)

    def test_parses_line_based_source_with_markers(self) -> None:
        fixture = """Authorized King James Version

Genesis 1:1 In the beginning God created the heaven and the earth.
Matthew 5:7 ‹Blessed› [are] ‹the merciful: for they shall obtain mercy.›
Matthew 5:8 ¶ ‹Blessed› [are] ‹the pure in heart: for they shall see God.›
"""
        path = self._write_fixture(fixture)
        verses = parse_kjv_source(path, strict=False)

        self.assertEqual(3, len(verses))
        self.assertEqual("Matthew 5:7", verses[1].ref)
        self.assertIn("‹Blessed›", verses[1].text)
        self.assertIn("[are]", verses[1].text)
        self.assertIn("¶", verses[2].text)

    def test_obadiah_boundary_reference_present(self) -> None:
        source = Path(os.environ.get("KJV_SOURCE", r"C:\Users\rdp\AndroidStudioProjects\BibleQuickSearchKJV\tools\sources\kjv_biblesupersearch.txt"))
        if not source.exists():
            self.skipTest("Project source file not present")

        verses = parse_kjv_source(source)
        obadiah = [v for v in verses if v.book == "Obadiah" and v.chapter == 1]
        by_num = {v.verse: v.text for v in obadiah}
        self.assertIn(3, by_num)
        self.assertIn(4, by_num)
        self.assertIn(14, by_num)
        self.assertNotIn("1:4", by_num[3])

    def test_local_kjv_source_snapshot_if_present(self) -> None:
        source = Path(os.environ.get("KJV_SOURCE", r"C:\Users\rdp\AndroidStudioProjects\BibleQuickSearchKJV\tools\sources\kjv_biblesupersearch.txt"))
        if not source.exists():
            self.skipTest("Local KJV source not present")

        verses = parse_kjv_source(source)
        self.assertEqual("Genesis 1:1", verses[0].ref)
        self.assertEqual("Revelation 22:21", verses[-1].ref)
        self.assertEqual(31102, len(verses))


if __name__ == "__main__":
    unittest.main()




