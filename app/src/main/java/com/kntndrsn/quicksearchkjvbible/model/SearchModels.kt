package com.kntndrsn.quicksearchkjvbible.model

import androidx.compose.runtime.Immutable

@Immutable
data class ReferenceMatch(
    val book: BibleBook,
    val chapter: Int,
    val verse: Int?
) {
    val label: String = buildString {
        append(book.name)
        append(' ')
        append(chapter)
        verse?.let {
            append(':')
            append(it)
        }
    }
}

@Immutable
data class SearchResults(
    val referenceMatch: ReferenceMatch? = null,
    val matchingBooks: List<BibleBook> = emptyList(),
    val matchingVerses: List<BibleVerse> = emptyList()
)
