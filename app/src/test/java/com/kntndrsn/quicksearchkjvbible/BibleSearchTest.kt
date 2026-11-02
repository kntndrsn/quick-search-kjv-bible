package com.kntndrsn.quicksearchkjvbible

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import com.kntndrsn.quicksearchkjvbible.data.BibleReferenceParser
import com.kntndrsn.quicksearchkjvbible.data.BibleRepository
import com.kntndrsn.quicksearchkjvbible.data.BookCatalog
import com.kntndrsn.quicksearchkjvbible.data.SampleBibleContent
import com.kntndrsn.quicksearchkjvbible.data.SearchNormalizer
import com.kntndrsn.quicksearchkjvbible.model.BibleHeading
import com.kntndrsn.quicksearchkjvbible.model.BibleLocation
import com.kntndrsn.quicksearchkjvbible.model.HeadingType
import com.kntndrsn.quicksearchkjvbible.ui.buildStyledVerseAnnotatedString
import com.kntndrsn.quicksearchkjvbible.ui.headingLabel
import com.kntndrsn.quicksearchkjvbible.ui.plainVerseText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BibleSearchTest {
    private val repository = BibleRepository(null, SampleBibleContent.chapterVerses)
    private val parser = BibleReferenceParser(BookCatalog.books)

    @Test
    fun parsesStandardReference() {
        val result = parser.parse("John 3:16")
        assertNotNull(result)
        assertEquals("John", result?.book?.name)
        assertEquals(3, result?.chapter)
        assertEquals(16, result?.verse)
    }

    @Test
    fun parsesOrdinalAndRomanReferences() {
        val firstSamuel = parser.parse("First Samuel 3")
        assertNotNull(firstSamuel)
        assertEquals("1 Samuel", firstSamuel?.book?.name)
        assertEquals(3, firstSamuel?.chapter)

        val secondTim = parser.parse("II Timothy 1:7")
        assertNotNull(secondTim)
        assertEquals("2 Timothy", secondTim?.book?.name)
        assertEquals(1, secondTim?.chapter)
        assertEquals(7, secondTim?.verse)
    }

    @Test
    fun findsBookByPhoneticLikeInput() {
        val result = repository.searchNavigation("Jhn")
        assertTrue(result.matchingBooks.any { it.name == "John" })
    }

    @Test
    fun findsVerseMatchesForCommonQuery() = runBlocking {
        val result = repository.search("faith")
        assertTrue(result.matchingVerses.isNotEmpty())
        assertTrue(result.matchingVerses.any { it.text.contains("faith", ignoreCase = true) })
    }

    @Test
    fun preservesPronounIAndOrdinalsInVerseSearch() = runBlocking {
        assertEquals(
            "i can do all things",
            SearchNormalizer.normalizeVerseQuery("I can do all things")
        )
        val result = repository.search("I can do all things")
        assertTrue(result.matchingVerses.any { it.bookId == 50 && it.chapter == 4 && it.verse == 13 })
    }

    @Test
    fun locationTargetVerseFallsBackToScrollVerse() {
        val highlighted = BibleLocation(bookId = 43, chapter = 3, highlightedVerse = 16, scrollVerse = 5)
        assertEquals(16, highlighted.targetVerse)

        val scrolledOnly = BibleLocation(bookId = 43, chapter = 3, highlightedVerse = null, scrollVerse = 12)
        assertEquals(12, scrolledOnly.targetVerse)
    }

    @Test
    fun defaultLocationIsGenesisChapterOne() {
        val defaultLocation = BibleLocation()
        assertEquals(1, defaultLocation.bookId)
        assertEquals(1, defaultLocation.chapter)
        assertEquals(1, defaultLocation.targetVerse)
    }

    @Test
    fun verseTextNormalizationDeletesMarkers() {
        assertEquals(
            "the lord is my shepherd i shall not want",
            SearchNormalizer.normalizeVerseText("The \u2E22Lord\u2E23 [is] my shepherd; I shall not want.")
        )
        assertEquals("lord s house", SearchNormalizer.normalizeVerseText("LORD's house"))
        assertEquals("ab", SearchNormalizer.normalizeVerseText("\u2039a\u203Ab"))
        assertEquals("the lord god", SearchNormalizer.normalizeVerseText("the \u2E22Lord\u2E23 \u2E22God\u2E23"))
    }

    @Test
    fun specialCharactersFoldForSearch() {
        assertEquals(
            "caesar aenon tubalcain jointheirs",
            SearchNormalizer.normalizeVerseText("C\u00E6sar \u00C6non Tubal\u2013cain joint-heirs")
        )
        assertEquals("their sin and if not", SearchNormalizer.normalizeVerseText("their sin\u2015; and if not"))
        assertEquals("caesar tubalcain", SearchNormalizer.normalizeVerseQuery("CAESAR Tubal-cain"))
        assertEquals("1 john 3:16", SearchNormalizer.normalize("1 John 3:16"))

        val pattern = Regex(
            listOf("tubalcain", "caesar", "aenon").joinToString("|") { SearchNormalizer.highlightPatternFor(it) }
        )
        val text = "Tubal\u2013cain and C\u00E6sar at \u00C6non; Caesar"
        assertEquals(
            listOf("Tubal\u2013cain", "C\u00E6sar", "\u00C6non", "Caesar"),
            pattern.findAll(text).map { it.value }.toList()
        )
    }

    @Test
    fun divineNameSearchMatchesMarkedVerse() = runBlocking {
        val result = repository.search("lord is my shepherd")
        assertTrue(result.matchingVerses.any { it.bookId == 19 && it.chapter == 23 && it.verse == 1 })
    }

    @Test
    fun headingsLoadWithChapterAndAreSearchable() = runBlocking {
        val verses = repository.chapterVerses(19, 23)
        assertEquals(listOf("A Psalm of David."), verses.first { it.verse == 1 }.headings.map { it.text })
        assertTrue(verses.filter { it.verse != 1 }.all { it.headings.isEmpty() })

        val headings = repository.search("psalm of david").matchingHeadings
        assertEquals(listOf(Triple(19, 23, 1)), headings.map { Triple(it.bookId, it.chapter, it.verse) })
        assertEquals(HeadingType.PSALM, headings.single().type)
        assertTrue(repository.searchHeadingText("ps").isEmpty())
    }

    @Test
    fun plainVerseTextStripsMarkersAndUppercasesDivineName() {
        assertEquals(
            "The LORD is my shepherd, said Jesus",
            plainVerseText("The \u2E22Lord\u2E23 [is] my shepherd, \u2039said Jesus\u203A")
        )
        assertEquals("\u05D0 ALEPH.", headingLabel(BibleHeading(19, 119, 1, HeadingType.ACROSTIC, "\u05D0", "ALEPH.")))
    }

    @Test
    fun styledVerseRendersItalicRedAndSmallCapsDivineName() {
        val red = Color(0xFFCC0000)
        val text = buildStyledVerseAnnotatedString("\u2039The \u2E22Lord\u2E23 [is] here.\u203A Amen", red)
        assertEquals("The LORD is here. Amen", text.text)

        fun stylesAt(index: Int) = text.spanStyles.filter { index >= it.start && index < it.end }.map { it.item }
        val lord = text.text.indexOf("LORD")
        val italic = text.text.indexOf("is ")
        val amen = text.text.indexOf("Amen")

        assertTrue(stylesAt(0).any { it.color == red })
        assertTrue(stylesAt(lord).any { it.color == red })
        assertTrue(stylesAt(lord).none { it.fontSize.isEm })
        assertTrue((lord + 1 until lord + 4).all { i -> stylesAt(i).any { it.fontSize.isEm && it.fontSize.value < 1f } })
        assertTrue(stylesAt(italic).any { it.fontStyle == FontStyle.Italic && it.color == red })
        assertTrue(stylesAt(italic - 1).none { it.fontStyle == FontStyle.Italic })
        assertTrue(stylesAt(amen).isEmpty())
        assertEquals("LORD'S", buildStyledVerseAnnotatedString("\u2E22Lord's\u2E23", red).text)
    }

    @Test
    fun ftsMatchBuilderQuotesEachTokenWithPrefixOnLast() {
        assertEquals("\"faith*\"", BibleRepository.buildFtsMatchQuery("faith"))
        assertEquals("\"i\" \"can\" \"do\" \"all\" \"thin*\"", BibleRepository.buildFtsMatchQuery("i can do all thin"))
        assertEquals("", BibleRepository.buildFtsMatchQuery(""))
    }

    @Test
    fun chapterCacheIsPopulatedAndEvictsLeastRecentlyUsed() = runBlocking {
        val repo = BibleRepository(null, SampleBibleContent.chapterVerses)
        assertNull(repo.cachedChapter(43, 3))
        val loaded = repo.chapterVerses(43, 3)
        assertSame(loaded, repo.cachedChapter(43, 3))
        for (chapter in 1..BibleRepository.CHAPTER_CACHE_SIZE) repo.prefetch(1, chapter)
        assertNull(repo.cachedChapter(43, 3))
        assertNotNull(repo.cachedChapter(1, 1))
    }
}

