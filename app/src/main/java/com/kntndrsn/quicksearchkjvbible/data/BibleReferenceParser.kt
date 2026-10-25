package com.kntndrsn.quicksearchkjvbible.data

import com.kntndrsn.quicksearchkjvbible.model.BibleBook
import com.kntndrsn.quicksearchkjvbible.model.ReferenceMatch

class BibleReferenceParser(
    books: List<BibleBook>
) {
    private val aliasesByLength by lazy { buildAliases(books) }

    fun parse(query: String): ReferenceMatch? {
        val normalizedQuery = SearchNormalizer.normalize(query)
        return parseNormalized(normalizedQuery, SearchNormalizer.compactNormalized(normalizedQuery))
    }

    /** [normalizedQuery] must be SearchNormalizer.normalize(query) and [compactQuery] its compact form. */
    fun parseNormalized(normalizedQuery: String, compactQuery: String): ReferenceMatch? {
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

        private fun buildAliases(books: List<BibleBook>): List<AliasEntry> = books.flatMap { book ->
            book.aliases.map { alias ->
                val normalizedAlias = SearchNormalizer.normalize(alias)
                AliasEntry(
                    book = book,
                    alias = alias,
                    normalizedAlias = normalizedAlias,
                    compactAlias = SearchNormalizer.compactNormalized(normalizedAlias)
                )
            }
        }.sortedByDescending { it.compactAlias.length }
    }
}
