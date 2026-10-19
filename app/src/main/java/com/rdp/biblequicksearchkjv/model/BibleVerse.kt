package com.rdp.biblequicksearchkjv.model

data class BibleVerse(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String
)

