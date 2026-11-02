import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from osis_parser import normalize_search_text, parse_osis, tidy

FIXTURE = """<?xml version="1.0" encoding="UTF-8"?>
<osis xmlns="http://www.bibletechnologies.net/2003/OSIS/namespace">
<osisText osisIDWork="KJV">
<header><work osisWork="KJV"><title>Header title is ignored</title></work></header>
<div type="book" osisID="Gen" canonical="true">
<title type="main">THE FIRST BOOK OF MOSES CALLED GENESIS</title>
<chapter osisID="Gen.1" chapterTitle="CHAPTER 1.">
<title type="chapter">CHAPTER 1.</title>
<verse osisID="Gen.1.1" sID="Gen.1.1"/>In the beginning God created the heaven and the earth.<verse eID="Gen.1.1"/>
<verse osisID="Gen.1.2" sID="Gen.1.2"/><milestone type="x-extra-p"/>And darkness <transChange type="added">was</transChange> upon the deep.<note type="study">deep: Heb. <seg type="x-transChange" subType="x-added">abyss</seg></note><verse eID="Gen.1.2"/>
<verse osisID="Gen.1.3" sID="Gen.1.3"/><milestone type="x-p" marker="\u00b6"/>The <seg><divineName>Lord</divineName></seg>&apos;s word <seg type="x-transChange" subType="x-added">is</seg> sure.<verse eID="Gen.1.3"/>
</chapter>
</div>
<div type="book" osisID="Exod" canonical="true">
<chapter osisID="Exod.28">
<verse osisID="Exod.28.1" sID="Exod.28.1"/>Grave upon it, <inscription>HOLINESS TO THE <seg><divineName>Lord</divineName></seg></inscription>.<verse eID="Exod.28.1"/>
</chapter>
</div>
<div type="book" osisID="Ps" canonical="true">
<chapter osisID="Ps.119">
<title type="psalm" canonical="true">A Psalm of David.</title><verse osisID="Ps.119.1" sID="Ps.119.1"/><title type="acrostic" canonical="true"><foreign n="\u05d0">ALEPH.</foreign></title>Blessed <transChange type="added">are</transChange> the undefiled.<verse eID="Ps.119.1"/>
</chapter>
</div>
<div type="book" osisID="Matt" canonical="true">
<chapter osisID="Matt.5">
<verse osisID="Matt.5.2" sID="Matt.5.2"/>And he taught them, saying,<verse eID="Matt.5.2"/>
<verse osisID="Matt.5.3" sID="Matt.5.3"/><q who="Jesus" marker="">Blessed <transChange type="added">are</transChange> the poor in spirit.<verse eID="Matt.5.3"/>
<verse osisID="Matt.5.4" sID="Matt.5.4"/>Blessed <transChange type="added">are</transChange> they that mourn.</q> Then he went.<verse eID="Matt.5.4"/>
<verse osisID="Matt.5.5" sID="Matt.5.5"/>He said, <q who="Jesus" sID="q1"/>Go thy way.<verse eID="Matt.5.5"/>
<verse osisID="Matt.5.6" sID="Matt.5.6"/>Sin no more.<q eID="q1"/> And she went.<verse eID="Matt.5.6"/>
</chapter>
</div>
</osisText>
</osis>
"""


class OsisParserTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls._tmp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        source = Path(cls._tmp.name) / "fixture.xml"
        source.write_text(FIXTURE, encoding="utf-8")
        cls.bible = parse_osis(source, strict=False)
        cls.text = {(v.book_id, v.chapter, v.verse): v.text for v in cls.bible.verses}

    @classmethod
    def tearDownClass(cls) -> None:
        cls._tmp.cleanup()

    def test_plain_verse_and_ignored_titles(self) -> None:
        self.assertEqual("In the beginning God created the heaven and the earth.", self.text[(1, 1, 1)])
        self.assertEqual(10, len(self.bible.verses))

    def test_added_words_italic_markers_and_notes_and_milestones_dropped(self) -> None:
        self.assertEqual("And darkness [was] upon the deep.", self.text[(1, 1, 2)])

    def test_divine_name_and_seg_transchange_italic(self) -> None:
        self.assertEqual("The \u2e22Lord\u2e23's word [is] sure.", self.text[(1, 1, 3)])

    def test_inscription_text_kept_inline(self) -> None:
        self.assertEqual("Grave upon it, HOLINESS TO THE \u2e22Lord\u2e23.", self.text[(2, 28, 1)])

    def test_red_letter_container_spanning_verses(self) -> None:
        self.assertEqual("And he taught them, saying,", self.text[(40, 5, 2)])
        self.assertEqual("\u2039Blessed [are] the poor in spirit.\u203a", self.text[(40, 5, 3)])
        self.assertEqual("\u2039Blessed [are] they that mourn.\u203a Then he went.", self.text[(40, 5, 4)])

    def test_red_letter_milestones_spanning_verses(self) -> None:
        self.assertEqual("He said, \u2039Go thy way.\u203a", self.text[(40, 5, 5)])
        self.assertEqual("\u2039Sin no more.\u203a And she went.", self.text[(40, 5, 6)])

    def test_psalm_and_acrostic_headings(self) -> None:
        self.assertEqual("Blessed [are] the undefiled.", self.text[(19, 119, 1)])
        headings = [(h.book_id, h.chapter, h.verse, h.type, h.letter, h.text) for h in self.bible.headings]
        self.assertEqual(
            [
                (19, 119, 1, "psalm", None, "A Psalm of David."),
                (19, 119, 1, "acrostic", "\u05d0", "ALEPH."),
            ],
            headings,
        )
        for heading in self.bible.headings:
            self.assertNotIn("[", heading.text)

    def test_strict_mode_rejects_partial_source(self) -> None:
        source = Path(self._tmp.name) / "fixture.xml"
        with self.assertRaises(ValueError):
            parse_osis(source, strict=True)

    def test_tidy_and_normalize(self) -> None:
        self.assertEqual("a [b] c", tidy(" a [ b ]  c "))
        self.assertEqual("a b", tidy("a \u2039 \u203a b"))
        self.assertEqual("the lord s word is sure", normalize_search_text("The \u2e22Lord\u2e23's word [is] sure."))
        self.assertEqual("void and darkness", normalize_search_text("void; and darkness"))
        self.assertEqual("caesar aenon tubalcain jointheirs", normalize_search_text("C\u00e6sar \u00c6non Tubal\u2013cain joint-heirs"))
        self.assertEqual("their sin and if not", normalize_search_text("their sin\u2015; and if not"))


if __name__ == "__main__":
    unittest.main()
