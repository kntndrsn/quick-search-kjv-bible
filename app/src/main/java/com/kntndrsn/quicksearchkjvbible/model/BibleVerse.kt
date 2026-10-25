package com.kntndrsn.quicksearchkjvbible.model

import androidx.compose.runtime.Immutable

@Immutable
data class BibleVerse(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String
)
