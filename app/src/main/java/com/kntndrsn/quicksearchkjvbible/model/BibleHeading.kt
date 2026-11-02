package com.kntndrsn.quicksearchkjvbible.model

import androidx.compose.runtime.Immutable

enum class HeadingType(val dbValue: String) {
    /** Psalm superscription, e.g. "A Psalm of David." */
    PSALM("psalm"),

    /** Acrostic stanza title in Psalm 119, e.g. letter "א" with text "ALEPH." */
    ACROSTIC("acrostic");

    companion object {
        fun fromDb(value: String): HeadingType = entries.first { it.dbValue == value }
    }
}

/** A canonical heading displayed before [verse]. [text] may contain inline verse markers. */
@Immutable
data class BibleHeading(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val type: HeadingType,
    val letter: String?,
    val text: String
)
