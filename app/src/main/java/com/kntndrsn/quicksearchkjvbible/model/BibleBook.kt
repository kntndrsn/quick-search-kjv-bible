package com.kntndrsn.quicksearchkjvbible.model

import androidx.compose.runtime.Immutable

@Immutable
data class BibleBook(
    val id: Int,
    val name: String,
    val shortName: String,
    val chapterCount: Int,
    val aliases: List<String>
)
