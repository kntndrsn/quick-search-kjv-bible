package com.rdp.biblequicksearchkjv.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.rdp.biblequicksearchkjv.model.BibleBook
import com.rdp.biblequicksearchkjv.model.BibleVerse
import com.rdp.biblequicksearchkjv.model.SearchResults
import java.io.File

class BibleRepository(context: Context? = null) {
    private val books = BookCatalog.books
    private val parser = BibleReferenceParser(books)
    private val appContext = context?.applicationContext
    private val database: SQLiteDatabase? by lazy { appContext?.let(::openDatabaseFromAssets) }

    fun books(): List<BibleBook> = books

    fun bookById(id: Int): BibleBook = books.firstOrNull { it.id == id } ?: books.first()

    fun chapterVerses(bookId: Int, chapter: Int): List<BibleVerse> {
        val dbRows = loadChapterVersesFromDb(bookId, chapter)
        if (dbRows.isNotEmpty()) return dbRows

        return SampleBibleContent.chapterVerses[bookId to chapter]
            ?: listOf(
                BibleVerse(
                    bookId = bookId,
                    chapter = chapter,
                    verse = 1,
                    text = "No verse content is available for this chapter yet."
                )
            )
    }

    fun search(query: String): SearchResults {
        val normalized = SearchNormalizer.normalize(query)
        if (normalized.isBlank()) return SearchResults()

        val compact = SearchNormalizer.compact(query)
        val querySoundex = SearchNormalizer.soundex(query)

        val bookResults = books
            .filter { book ->
                val candidates = book.aliases + book.name + book.shortName
                candidates.any { alias ->
                    val normalizedAlias = SearchNormalizer.normalize(alias)
                    val compactAlias = SearchNormalizer.compact(alias)
                    normalizedAlias.contains(normalized) ||
                        compactAlias.contains(compact) ||
                        SearchNormalizer.soundex(alias) == querySoundex
                }
            }
            .sortedWith(compareBy<BibleBook> {
                val scoreAlias = bookScore(it, normalized, compact)
                scoreAlias
            }.thenBy { it.id })
            .take(6)

        val verseResults = if (normalized.length < 3) {
            emptyList()
        } else {
            val dbMatches = searchVersesInDb(normalized)
            if (dbMatches.isNotEmpty()) {
                dbMatches
            } else {
                SampleBibleContent.searchableVerses
                    .filter { verse -> SearchNormalizer.normalize(verse.text).contains(normalized) }
                    .take(5)
            }
        }

        return SearchResults(
            referenceMatch = parser.parse(query),
            matchingBooks = bookResults,
            matchingVerses = verseResults
        )
    }

    private fun bookScore(book: BibleBook, normalized: String, compact: String): Int {
        val candidates = (book.aliases + book.name + book.shortName).distinct()
        return candidates.minOf { candidate ->
            val normalizedCandidate = SearchNormalizer.normalize(candidate)
            val compactCandidate = SearchNormalizer.compact(candidate)
            when {
                normalizedCandidate == normalized || compactCandidate == compact -> 0
                normalizedCandidate.startsWith(normalized) || compactCandidate.startsWith(compact) -> 1
                normalizedCandidate.contains(normalized) || compactCandidate.contains(compact) -> 2
                SearchNormalizer.soundex(candidate) == SearchNormalizer.soundex(normalized) -> 3
                else -> 4
            }
        }
    }

    private fun loadChapterVersesFromDb(bookId: Int, chapter: Int): List<BibleVerse> {
        val db = database ?: return emptyList()
        val verses = mutableListOf<BibleVerse>()
        db.rawQuery(
            "SELECT verse, text FROM verses WHERE book_id = ? AND chapter = ? ORDER BY verse",
            arrayOf(bookId.toString(), chapter.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                verses += BibleVerse(
                    bookId = bookId,
                    chapter = chapter,
                    verse = cursor.getInt(0),
                    text = cursor.getString(1)
                )
            }
        }
        return verses
    }

    private fun searchVersesInDb(normalizedQuery: String): List<BibleVerse> {
        val db = database ?: return emptyList()
        val verses = mutableListOf<BibleVerse>()
        db.rawQuery(
            """
            SELECT book_id, chapter, verse, text
            FROM verses
            WHERE lower(text) LIKE ?
            LIMIT 5
            """.trimIndent(),
            arrayOf("%$normalizedQuery%")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                verses += BibleVerse(
                    bookId = cursor.getInt(0),
                    chapter = cursor.getInt(1),
                    verse = cursor.getInt(2),
                    text = cursor.getString(3)
                )
            }
        }
        return verses
    }

    private fun openDatabaseFromAssets(context: Context): SQLiteDatabase? {
        return try {
            val dbFile = File(context.noBackupFilesDir, "kjv.db")
            // Always refresh from the bundled asset so stale prior indexing is replaced.
            context.assets.open("kjv.db").use { input ->
                dbFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (_: Exception) {
            null
        }
    }
}

