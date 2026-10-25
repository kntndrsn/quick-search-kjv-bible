package com.kntndrsn.quicksearchkjvbible.model

import androidx.compose.runtime.Immutable

@Immutable
// The request identity lets selecting the same reference reposition an already-open chapter.
data class VerseTarget(val verse: Int? = 1, val request: Long = 0)
