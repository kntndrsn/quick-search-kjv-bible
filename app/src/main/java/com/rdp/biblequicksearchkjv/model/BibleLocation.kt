package com.rdp.biblequicksearchkjv.model

data class BibleLocation(
    val bookId: Int = 43,
    val chapter: Int = 3,
    val highlightedVerse: Int? = 16,
    val scrollVerse: Int? = highlightedVerse
) {
    val targetVerse: Int?
        get() = highlightedVerse ?: scrollVerse
}


