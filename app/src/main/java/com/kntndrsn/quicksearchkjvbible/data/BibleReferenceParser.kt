package com.rdp.biblequicksearchkjv.data

import com.rdp.biblequicksearchkjv.model.BibleBook
import com.rdp.biblequicksearchkjv.model.ReferenceMatch

class BibleReferenceParser(
    books: List<BibleBook>
) {
    private val aliasesByLength = books.flatMap { book ->
        book.aliases.map { alias ->
            AliasEntry(
                book = book,
                alias = alias,
                normalizedAlias = SearchNormalizer.normalize(alias),
                compactAlias = SearchNormalizer.compact(alias)
            )
        }
    }.sortedByDescending { it.compactAlias.length }

    fun parse(query: String): ReferenceMatch? {
        val normalizedQuery = SearchNormalizer.normalize(query)
        val compactQuery = SearchNormalizer.compact(query)
        if (normalizedQuery.isBlank()) return null

        val aliasEntry = aliasesByLength.firstOrNull { entry ->
            normalizedQuery.startsWith(entry.normalizedAlias) ||
                compactQuery.startsWith(entry.compactAlias)
        } ?: return null

        val remainder = when {
            normalizedQuery.startsWith(aliasEntry.normalizedAlias) ->
                normalizedQuery.removePrefix(aliasEntry.normalizedAlias).trim()
            compactQuery.startsWith(aliasEntry.compactAlias) ->
                compactQuery.removePrefix(aliasEntry.compactAlias)
            else -> ""
        }

        val match = chapterVerseRegex.find(remainder) ?: return null
        val chapter = match.groupValues[1].toIntOrNull() ?: return null
        if (chapter !in 1..aliasEntry.book.chapterCount) return null
        val verse = match.groupValues
            .getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.toIntOrNull()
            ?.takeIf { it >= 1 }

        return ReferenceMatch(
            book = aliasEntry.book,
            chapter = chapter,
            verse = verse
        )
    }

    private data class AliasEntry(
        val book: BibleBook,
        val alias: String,
        val normalizedAlias: String,
        val compactAlias: String
    )

    companion object {
        private val chapterVerseRegex = Regex("^(\\d+)(?:[: .-]+(\\d+))?.*$")
    }
}

