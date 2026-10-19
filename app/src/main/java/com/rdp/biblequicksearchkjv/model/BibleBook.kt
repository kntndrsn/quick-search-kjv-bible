package com.rdp.biblequicksearchkjv.model

data class BibleBook(
    val id: Int,
    val name: String,
    val shortName: String,
    val chapterCount: Int,
    val aliases: List<String>
)

