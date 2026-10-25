package com.kntndrsn.quicksearchkjvbible.data

import com.kntndrsn.quicksearchkjvbible.model.BibleVerse

object SampleBibleContent {
    private fun verses(bookId: Int, chapter: Int, vararg lines: Pair<Int, String>): List<BibleVerse> =
        lines.map { (verse, text) ->
            BibleVerse(bookId = bookId, chapter = chapter, verse = verse, text = text)
        }

    val chapterVerses: Map<Pair<Int, Int>, List<BibleVerse>> = mapOf(
        (1 to 1) to verses(
            1, 1,
            1 to "In the beginning God created the heaven and the earth.",
            2 to "And the earth was without form, and void; and darkness was upon the face of the deep.",
            3 to "And God said, Let there be light: and there was light.",
            26 to "And God said, Let us make man in our image, after our likeness.",
            31 to "And God saw every thing that he had made, and, behold, it was very good."
        ),
        (19 to 23) to verses(
            19, 23,
            1 to "The Lord is my shepherd; I shall not want.",
            2 to "He maketh me to lie down in green pastures: he leadeth me beside the still waters.",
            3 to "He restoreth my soul: he leadeth me in the paths of righteousness for his name's sake.",
            4 to "Yea, though I walk through the valley of the shadow of death, I will fear no evil: for thou art with me.",
            5 to "Thou preparest a table before me in the presence of mine enemies.",
            6 to "Surely goodness and mercy shall follow me all the days of my life."
        ),
        (20 to 3) to verses(
            20, 3,
            5 to "Trust in the Lord with all thine heart; and lean not unto thine own understanding.",
            6 to "In all thy ways acknowledge him, and he shall direct thy paths.",
            7 to "Be not wise in thine own eyes: fear the Lord, and depart from evil."
        ),
        (23 to 40) to verses(
            23, 40,
            28 to "But they that wait upon the Lord shall renew their strength; they shall mount up with wings as eagles.",
            29 to "He giveth power to the faint; and to them that have no might he increaseth strength.",
            31 to "They shall run, and not be weary; and they shall walk, and not faint."
        ),
        (40 to 11) to verses(
            40, 11,
            28 to "Come unto me, all ye that labour and are heavy laden, and I will give you rest.",
            29 to "Take my yoke upon you, and learn of me; for I am meek and lowly in heart.",
            30 to "For my yoke is easy, and my burden is light."
        ),
        (43 to 1) to verses(
            43, 1,
            1 to "In the beginning was the Word, and the Word was with God, and the Word was God.",
            2 to "The same was in the beginning with God.",
            3 to "All things were made by him; and without him was not any thing made that was made.",
            4 to "In him was life; and the life was the light of men.",
            14 to "And the Word was made flesh, and dwelt among us, full of grace and truth."
        ),
        (43 to 3) to verses(
            43, 3,
            14 to "And as Moses lifted up the serpent in the wilderness, even so must the Son of man be lifted up:",
            15 to "That whosoever believeth in him should not perish, but have eternal life.",
            16 to "For God so loved the world, that he gave his only begotten Son.",
            17 to "For God sent not his Son into the world to condemn the world; but that the world through him might be saved.",
            18 to "He that believeth on him is not condemned."
        ),
        (45 to 8) to verses(
            45, 8,
            1 to "There is therefore now no condemnation to them which are in Christ Jesus.",
            6 to "For to be carnally minded is death; but to be spiritually minded is life and peace.",
            28 to "And we know that all things work together for good to them that love God.",
            31 to "If God be for us, who can be against us?",
            38 to "For I am persuaded, that neither death, nor life, nor angels, nor principalities, nor powers."
        ),
        (46 to 13) to verses(
            46, 13,
            4 to "Charity suffereth long, and is kind; charity envieth not; charity vaunteth not itself, is not puffed up.",
            5 to "Doth not behave itself unseemly, seeketh not her own, is not easily provoked.",
            7 to "Beareth all things, believeth all things, hopeth all things, endureth all things.",
            13 to "And now abideth faith, hope, charity, these three; but the greatest of these is charity."
        ),
        (49 to 2) to verses(
            49, 2,
            8 to "For by grace are ye saved through faith; and that not of yourselves: it is the gift of God:",
            9 to "Not of works, lest any man should boast.",
            10 to "For we are his workmanship, created in Christ Jesus unto good works."
        ),
        (50 to 4) to verses(
            50, 4,
            4 to "Rejoice in the Lord alway: and again I say, Rejoice.",
            6 to "Be careful for nothing; but in every thing by prayer and supplication with thanksgiving let your requests be made known unto God.",
            7 to "And the peace of God, which passeth all understanding, shall keep your hearts and minds through Christ Jesus.",
            13 to "I can do all things through Christ which strengtheneth me."
        ),
        (58 to 11) to verses(
            58, 11,
            1 to "Now faith is the substance of things hoped for, the evidence of things not seen.",
            6 to "But without faith it is impossible to please him.",
            8 to "By faith Abraham, when he was called to go out into a place which he should after receive for an inheritance, obeyed."
        ),
        (66 to 21) to verses(
            66, 21,
            1 to "And I saw a new heaven and a new earth.",
            3 to "Behold, the tabernacle of God is with men, and he will dwell with them.",
            4 to "And God shall wipe away all tears from their eyes; and there shall be no more death."
        )
    )

    val searchableVerses: List<BibleVerse> = chapterVerses.values.flatten()
}
