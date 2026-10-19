package com.rdp.biblequicksearchkjv

import com.rdp.biblequicksearchkjv.data.BibleReferenceParser
import com.rdp.biblequicksearchkjv.data.BibleRepository
import com.rdp.biblequicksearchkjv.data.BookCatalog
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
}

