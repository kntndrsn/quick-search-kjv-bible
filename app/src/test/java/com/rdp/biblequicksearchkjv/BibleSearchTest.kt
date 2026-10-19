package com.rdp.biblequicksearchkjv

import com.rdp.biblequicksearchkjv.data.BibleReferenceParser
import com.rdp.biblequicksearchkjv.data.BibleRepository
import com.rdp.biblequicksearchkjv.data.BookCatalog
import com.rdp.biblequicksearchkjv.data.SearchNormalizer
import com.rdp.biblequicksearchkjv.model.BibleLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BibleSearchTest {
    private val repository = BibleRepository()
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
        val result = repository.search("Jhn")
        assertTrue(result.matchingBooks.any { it.name == "John" })
    }

    @Test
    fun findsVerseMatchesForCommonQuery() {
        val result = repository.search("faith")
        assertTrue(result.matchingVerses.isNotEmpty())
        assertTrue(result.matchingVerses.any { it.text.contains("faith", ignoreCase = true) })
    }

    @Test
    fun preservesPronounIAndOrdinalsInVerseSearch() {
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
}


