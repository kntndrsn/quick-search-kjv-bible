package com.kntndrsn.quicksearchkjvbible.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.core.content.pm.PackageInfoCompat
import com.kntndrsn.quicksearchkjvbible.model.BibleBook
import com.kntndrsn.quicksearchkjvbible.model.BibleLocation
import com.kntndrsn.quicksearchkjvbible.model.BibleVerse
import com.kntndrsn.quicksearchkjvbible.model.SearchResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Bible data access. All public members are thread-safe; suspend functions switch to
 * [Dispatchers.IO] internally so they are safe to call from the main thread.
 */
@Stable
class BibleRepository internal constructor(
    context: Context?,
    private val sampleChapters: Map<Pair<Int, Int>, List<BibleVerse>>
) {
    constructor(context: Context? = null) : this(context, emptyMap())

    private val books = BookCatalog.books
    private val parser = BibleReferenceParser(books)
    private val appContext = context?.applicationContext
    private val database: SQLiteDatabase? by lazy { appContext?.let { getOrOpenDatabase(it) } }
    private val chapterCache = ChapterCache(CHAPTER_CACHE_SIZE)

    fun books(): List<BibleBook> = books

    fun bookById(id: Int): BibleBook = BookCatalog.bookById(id)

    suspend fun chapterVerses(bookId: Int, chapter: Int): List<BibleVerse> {
        chapterCache.get(bookId, chapter)?.let { return it }
        return withContext(Dispatchers.IO) { loadChapter(bookId, chapter) }
    }

    /** Non-blocking: returns the chapter only if it is already in the in-memory cache. */
    fun cachedChapter(bookId: Int, chapter: Int): List<BibleVerse>? = chapterCache.get(bookId, chapter)

    suspend fun prefetch(bookId: Int, chapter: Int) {
        if (chapterCache.get(bookId, chapter) != null) return
        withContext(Dispatchers.IO) { loadChapter(bookId, chapter) }
    }

    /** Opens (copying if needed) the database, precomputes search indexes, and loads the start chapter. */
    suspend fun warmUp(bookId: Int = 1, chapter: Int = 1) {
        withContext(Dispatchers.IO) {
            database
            val book = bookById(bookId)
            loadChapter(book.id, chapter.coerceIn(1, book.chapterCount))
        }
    }

    suspend fun warmUp(location: BibleLocation) = warmUp(location.bookId, location.chapter)

    /**
     * Immediate, CPU-only book and reference matching (no database access). Safe to call on
     * every keystroke; [SearchResults.matchingVerses] is always empty.
     */
    fun searchNavigation(query: String): SearchResults {
        val normalized = SearchNormalizer.normalize(query)
        if (normalized.isBlank() && SearchNormalizer.normalizeVerseQuery(query).isBlank()) {
            return SearchResults()
        }
        val compact = SearchNormalizer.compactNormalized(normalized)
        return SearchResults(
            referenceMatch = parser.parseNormalized(normalized, compact),
            matchingBooks = matchBooks(normalized, compact)
        )
    }

    /** Verse text search only (FTS4 with LIKE fallback). */
    suspend fun searchVerseText(query: String): List<BibleVerse> {
        val normalizedVerseQuery = SearchNormalizer.normalizeVerseQuery(query)
        if (normalizedVerseQuery.length < MIN_VERSE_QUERY_LENGTH) return emptyList()
        return withContext(Dispatchers.IO) { searchVerses(normalizedVerseQuery) }
    }

    /** Full search: books, reference and verse text. */
    suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        val navigation = searchNavigation(query)
        val normalizedVerseQuery = SearchNormalizer.normalizeVerseQuery(query)
        if (normalizedVerseQuery.length < MIN_VERSE_QUERY_LENGTH) {
            navigation
        } else {
            navigation.copy(matchingVerses = searchVerses(normalizedVerseQuery))
        }
    }

    private fun matchBooks(normalized: String, compact: String): List<BibleBook> {
        val querySoundex = SearchNormalizer.soundexOfCompact(compact)
        val normalizedSoundex = SearchNormalizer.soundex(normalized)
        val index = BookCatalog.searchIndex
        val matches = ArrayList<ScoredBook>()
        for (entry in index) {
            var matched = false
            var best = Int.MAX_VALUE
            for (alias in entry.aliases) {
                if (!matched) {
                    matched = alias.normalized.contains(normalized) ||
                        alias.compact.contains(compact) ||
                        alias.soundex == querySoundex
                }
                val score = when {
                    alias.normalized == normalized || alias.compact == compact -> 0
                    alias.normalized.startsWith(normalized) || alias.compact.startsWith(compact) -> 1
                    alias.normalized.contains(normalized) || alias.compact.contains(compact) -> 2
                    alias.soundex == normalizedSoundex -> 3
                    else -> 4
                }
                if (score < best) best = score
            }
            if (matched) matches += ScoredBook(entry.book, best)
        }
        matches.sortWith(compareBy<ScoredBook> { it.score }.thenBy { it.book.id })
        val count = minOf(MAX_BOOK_RESULTS, matches.size)
        return List(count) { matches[it].book }
    }

    private class ScoredBook(val book: BibleBook, val score: Int)

    private fun loadChapter(bookId: Int, chapter: Int): List<BibleVerse> {
        chapterCache.get(bookId, chapter)?.let { return it }
        val db = database
        val verses = if (db != null) {
            loadChapterVersesFromDb(db, bookId, chapter).ifEmpty {
                throw IllegalStateException("No verses in bundled database for book $bookId chapter $chapter")
            }
        } else {
            sampleChapters[bookId to chapter]
                ?: listOf(
                    BibleVerse(
                        bookId = bookId,
                        chapter = chapter,
                        verse = 1,
                        text = "No verse content is available for this chapter yet."
                    )
                )
        }
        chapterCache.put(bookId, chapter, verses)
        return verses
    }

    private fun loadChapterVersesFromDb(db: SQLiteDatabase, bookId: Int, chapter: Int): List<BibleVerse> =
        db.rawQuery(
            "SELECT verse, text FROM verses WHERE book_id = ? AND chapter = ? ORDER BY verse",
            arrayOf(bookId.toString(), chapter.toString())
        ).use { cursor ->
            val verses = ArrayList<BibleVerse>(cursor.count.coerceAtLeast(0))
            while (cursor.moveToNext()) {
                verses += BibleVerse(
                    bookId = bookId,
                    chapter = chapter,
                    verse = cursor.getInt(0),
                    text = cursor.getString(1)
                )
            }
            verses
        }

    private fun searchVerses(normalizedVerseQuery: String): List<BibleVerse> {
        val db = database
        if (db != null) return searchVersesInDb(db, normalizedVerseQuery)
        return sampleChapters.values
            .asSequence()
            .flatten()
            .filter { verse -> SearchNormalizer.normalizeVerseText(verse.text).contains(normalizedVerseQuery) }
            .take(MAX_VERSE_RESULTS)
            .toList()
    }

    private fun searchVersesInDb(db: SQLiteDatabase, normalizedQuery: String): List<BibleVerse> {
        val verses = LinkedHashMap<Long, BibleVerse>()

        db.rawQuery(FTS_QUERY, arrayOf(buildFtsMatchQuery(normalizedQuery))).use { cursor ->
            while (cursor.moveToNext()) {
                val item = readVerse(cursor)
                verses.putIfAbsent(verseKey(item), item)
            }
        }

        if (verses.size < MAX_VERSE_RESULTS) {
            db.rawQuery(LIKE_QUERY, arrayOf(buildLikePattern(normalizedQuery))).use { cursor ->
                while (cursor.moveToNext() && verses.size < MAX_VERSE_RESULTS) {
                    val item = readVerse(cursor)
                    verses.putIfAbsent(verseKey(item), item)
                }
            }
        }

        return verses.values.toList()
    }

    private fun readVerse(cursor: android.database.Cursor) = BibleVerse(
        bookId = cursor.getInt(0),
        chapter = cursor.getInt(1),
        verse = cursor.getInt(2),
        text = cursor.getString(3)
    )

    private fun verseKey(verse: BibleVerse): Long =
        (verse.bookId.toLong() shl 32) or (verse.chapter.toLong() shl 16) or verse.verse.toLong()

    /** Thread-safe LRU cache of loaded chapters. */
    private class ChapterCache(private val maxSize: Int) {
        private val map = object : LinkedHashMap<Long, List<BibleVerse>>(maxSize + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<BibleVerse>>?): Boolean =
                size > maxSize
        }

        private fun key(bookId: Int, chapter: Int): Long = (bookId.toLong() shl 32) or chapter.toLong()

        @Synchronized
        fun get(bookId: Int, chapter: Int): List<BibleVerse>? = map[key(bookId, chapter)]

        @Synchronized
        fun put(bookId: Int, chapter: Int, verses: List<BibleVerse>) {
            map[key(bookId, chapter)] = verses
        }
    }

    companion object {
        const val MAX_VERSE_RESULTS = 25
        const val MAX_BOOK_RESULTS = 6
        const val MIN_VERSE_QUERY_LENGTH = 3
        const val CHAPTER_CACHE_SIZE = 8

        /** Minimum PRAGMA user_version of the bundled database this code understands. */
        const val REQUIRED_DB_USER_VERSION = 2

        private const val TAG = "BibleRepository"
        private const val DB_NAME = "kjv.db"
        private const val DB_VERSION_SIDECAR = "kjv.db.version"

        private const val FTS_QUERY =
            "SELECT v.book_id, v.chapter, v.verse, v.text FROM verses_fts4 f " +
                "JOIN verses v ON v.rowid = f.docid WHERE verses_fts4 MATCH ? LIMIT 25"

        private const val LIKE_QUERY =
            "SELECT book_id, chapter, verse, text FROM verses WHERE text_norm LIKE ? LIMIT 25"

        /**
         * Builds an FTS4 MATCH expression from a [SearchNormalizer.normalizeVerseQuery] result:
         * every token quoted individually, with a prefix `*` inside the final token's quotes,
         * e.g. "i can do all thin" -> `"i" "can" "do" "all" "thin*"`.
         */
        fun buildFtsMatchQuery(normalizedQuery: String): String {
            val sb = StringBuilder(normalizedQuery.length * 2 + 3)
            for (token in normalizedQuery.split(' ')) {
                val clean = token.filter { it in 'a'..'z' || it in '0'..'9' }
                if (clean.isEmpty()) continue
                if (sb.isNotEmpty()) sb.append('"').append(' ')
                sb.append('"').append(clean)
            }
            if (sb.isEmpty()) return ""
            sb.append("*\"")
            return sb.toString()
        }

        fun buildLikePattern(normalizedQuery: String): String = "%$normalizedQuery%"

        @Volatile
        private var sharedDatabase: SQLiteDatabase? = null

        @Synchronized
        private fun getOrOpenDatabase(context: Context): SQLiteDatabase {
            sharedDatabase?.takeIf { it.isOpen }?.let { return it }

            val dir = context.noBackupFilesDir
            val dbFile = File(dir, DB_NAME)
            val sidecar = File(dir, DB_VERSION_SIDECAR)
            val versionCode = appVersionCode(context)
            if (needsCopy(dbFile, sidecar, versionCode)) {
                copyDatabaseFromAssets(context, dir, dbFile)
                writeSidecar(dir, sidecar, versionCode)
            }
            return SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).also { sharedDatabase = it }
        }

        private fun appVersionCode(context: Context): Long = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0)
        )

        private fun needsCopy(dbFile: File, sidecar: File, versionCode: Long): Boolean {
            if (!dbFile.exists()) return true
            val userVersion = readUserVersion(dbFile)
            if (userVersion < REQUIRED_DB_USER_VERSION) {
                Log.i(TAG, "Installed $DB_NAME user_version=$userVersion < $REQUIRED_DB_USER_VERSION; recopying")
                return true
            }
            if (!sidecar.exists()) {
                Log.i(TAG, "Missing $DB_VERSION_SIDECAR; recopying $DB_NAME")
                return true
            }
            val stored = try {
                sidecar.readText().trim().toLong()
            } catch (e: IOException) {
                Log.w(TAG, "Unreadable $DB_VERSION_SIDECAR; recopying $DB_NAME", e)
                return true
            } catch (e: NumberFormatException) {
                Log.w(TAG, "Corrupt $DB_VERSION_SIDECAR; recopying $DB_NAME", e)
                return true
            }
            return stored != versionCode
        }

        /**
         * Reads PRAGMA user_version straight from the SQLite header (big-endian int at offset 60).
         * Returns -1 for a truncated or unreadable file so the caller recopies the asset.
         */
        internal fun readUserVersion(file: File): Int = try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 64) {
                    Log.w(TAG, "Installed $DB_NAME is truncated (${raf.length()} bytes)")
                    -1
                } else {
                    raf.seek(60)
                    raf.readInt()
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Unable to read $DB_NAME header", e)
            -1
        }

        private fun copyDatabaseFromAssets(context: Context, dir: File, dbFile: File) {
            val tmpFile = File(dir, "$DB_NAME.tmp")
            context.assets.open(DB_NAME).use { input ->
                FileOutputStream(tmpFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    output.fd.sync()
                }
            }
            for (suffix in arrayOf("-journal", "-wal", "-shm")) File(dir, DB_NAME + suffix).delete()
            if (!tmpFile.renameTo(dbFile)) {
                dbFile.delete()
                if (!tmpFile.renameTo(dbFile)) {
                    tmpFile.delete()
                    error("Unable to install $DB_NAME")
                }
            }
        }

        private fun writeSidecar(dir: File, sidecar: File, versionCode: Long) {
            val tmp = File(dir, "$DB_VERSION_SIDECAR.tmp")
            FileOutputStream(tmp).use { output ->
                output.write(versionCode.toString().toByteArray())
                output.fd.sync()
            }
            if (!tmp.renameTo(sidecar)) {
                sidecar.delete()
                if (!tmp.renameTo(sidecar)) {
                    tmp.delete()
                    error("Unable to install $DB_VERSION_SIDECAR")
                }
            }
        }
    }
}
