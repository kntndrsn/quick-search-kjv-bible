package com.rdp.biblequicksearchkjv.data

import com.rdp.biblequicksearchkjv.model.BibleBook
import com.rdp.biblequicksearchkjv.model.ReferenceMatch

class BibleReferenceParser(
    private val books: List<BibleBook>
) {
    private val aliasesByLength = books.flatMap { book ->
        book.aliases.map { alias ->
            AliasEntry(book = book, alias = alias)
        }
    }.sortedByDescending { SearchNormalizer.compact(it.alias).length }

    fun parse(query: String): ReferenceMatch? {
        val normalizedQuery = SearchNormalizer.normalize(query)
        val compactQuery = SearchNormalizer.compact(query)
        if (normalizedQuery.isBlank()) return null

        val aliasEntry = aliasesByLength.firstOrNull { entry ->
            normalizedQuery.startsWith(SearchNormalizer.normalize(entry.alias)) ||
                compactQuery.startsWith(SearchNormalizer.compact(entry.alias))
        } ?: return null

        val normalizedAlias = SearchNormalizer.normalize(aliasEntry.alias)
        val compactAlias = SearchNormalizer.compact(aliasEntry.alias)
        val remainder = when {
            normalizedQuery.startsWith(normalizedAlias) -> normalizedQuery.removePrefix(normalizedAlias).trim()
            compactQuery.startsWith(compactAlias) -> compactQuery.removePrefix(compactAlias)
            else -> ""
        }

        val match = Regex("^(\\d+)(?:[: .-]+(\\d+))?.*$").find(remainder) ?: return null
        val chapter = match.groupValues[1].toIntOrNull() ?: return null
        if (chapter !in 1..aliasEntry.book.chapterCount) return null
        val verse = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull()

        return ReferenceMatch(
            book = aliasEntry.book,
            chapter = chapter,
            verse = verse
        )
    }

    private data class AliasEntry(
        val book: BibleBook,
        val alias: String
    )
}

