package com.kntndrsn.quicksearchkjvbible.model

import androidx.compose.runtime.Immutable

@Immutable
data class BibleVerse(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
    /** Headings (psalm superscription, acrostic stanza title) shown before this verse. */
    val headings: List<BibleHeading> = emptyList()
)
