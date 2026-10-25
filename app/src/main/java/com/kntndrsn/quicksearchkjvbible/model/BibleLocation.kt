package com.kntndrsn.quicksearchkjvbible.model

import androidx.compose.runtime.Immutable

@Immutable
data class BibleLocation(
    val bookId: Int = 1,
    val chapter: Int = 1,
    val highlightedVerse: Int? = null,
    val scrollVerse: Int? = highlightedVerse ?: 1
) {
    val targetVerse: Int?
        get() = highlightedVerse ?: scrollVerse
}
