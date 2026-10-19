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
        val normalizedVerseQuery = SearchNormalizer.normalizeVerseQuery(query)
        if (normalized.isBlank() && normalizedVerseQuery.isBlank()) return SearchResults()

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

        val verseResults = if (normalizedVerseQuery.length < 3) {
            emptyList()
        } else {
            val dbMatches = searchVersesInDb(normalizedVerseQuery)
            if (dbMatches.isNotEmpty()) {
                dbMatches
            } else {
                SampleBibleContent.searchableVerses
                    .filter { verse ->
                        SearchNormalizer.normalizeVerseQuery(verse.text).contains(normalizedVerseQuery)
                    }
                    .take(25)
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
        val verses = LinkedHashMap<Triple<Int, Int, Int>, BibleVerse>()

        try {
            val ftsQuery = "\"${normalizedQuery.replace("\"", "")}\""
            db.rawQuery(
                """
                SELECT book_id, chapter, verse, text
                FROM verses_fts
                WHERE verses_fts MATCH ?
                LIMIT 25
                """.trimIndent(),
                arrayOf(ftsQuery)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val item = BibleVerse(
                        bookId = cursor.getInt(0),
                        chapter = cursor.getInt(1),
                        verse = cursor.getInt(2),
                        text = cursor.getString(3)
                    )
                    verses[Triple(item.bookId, item.chapter, item.verse)] = item
                }
            }
        } catch (_: Exception) {
            // Fall through to LIKE query if FTS5 is unavailable.
        }

        if (verses.size < 25) {
            val remaining = 25 - verses.size
            db.rawQuery(
                """
                SELECT book_id, chapter, verse, text
                FROM verses
                WHERE lower(
                    replace(
                        replace(
                            replace(
                                replace(
                                    replace(
                                        replace(
                                            replace(
                                                replace(text, '‹', ''),
                                                '›', ''
                                            ),
                                            '[', ''
                                        ),
                                        ']', ''
                                    ),
                                    '¶ ', ''
                                ),
                                '¶', ''
                            ),
                            ',', ''
                        ),
                        ';', ''
                    )
                ) LIKE ?
                LIMIT ?
                """.trimIndent(),
                arrayOf("%$normalizedQuery%", remaining.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val item = BibleVerse(
                        bookId = cursor.getInt(0),
                        chapter = cursor.getInt(1),
                        verse = cursor.getInt(2),
                        text = cursor.getString(3)
                    )
                    verses.putIfAbsent(Triple(item.bookId, item.chapter, item.verse), item)
                }
            }
        }

        return verses.values.toList()
    }

    private fun openDatabaseFromAssets(context: Context): SQLiteDatabase? {
        return getOrOpenDatabase(context)
    }

    companion object {
        @Volatile
        private var sharedDatabase: SQLiteDatabase? = null

        @Synchronized
        private fun getOrOpenDatabase(context: Context): SQLiteDatabase? {
            sharedDatabase?.takeIf { it.isOpen }?.let { return it }

            return try {
                val dbFile = File(context.noBackupFilesDir, "kjv.db")
                val tmpFile = File(context.noBackupFilesDir, "kjv.db.tmp")
                context.assets.open("kjv.db").use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (!dbFile.exists() || dbFile.length() != tmpFile.length()) {
                    tmpFile.copyTo(dbFile, overwrite = true)
                }
                tmpFile.delete()

                SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                ).also {
                    sharedDatabase = it
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

