package com.kntndrsn.quicksearchkjvbible

import com.kntndrsn.quicksearchkjvbible.data.BibleReferenceParser
import com.kntndrsn.quicksearchkjvbible.data.BibleRepository
import com.kntndrsn.quicksearchkjvbible.data.BookCatalog
import com.kntndrsn.quicksearchkjvbible.data.SampleBibleContent
import com.kntndrsn.quicksearchkjvbible.data.SearchNormalizer
import com.kntndrsn.quicksearchkjvbible.model.BibleLocation
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
            SearchNormalizer.normalizeVerseText("\u00B6 A Psalm of David. The LORD [is] my shepherd; I shall not want.")
                .removePrefix("a psalm of david ")
        )
        assertEquals("lord s house", SearchNormalizer.normalizeVerseText("LORD's house"))
        assertEquals("ab", SearchNormalizer.normalizeVerseText("\u2039a\u203Ab"))
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

