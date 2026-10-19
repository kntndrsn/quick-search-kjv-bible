import com.rdp.biblequicksearchkjv.data.BibleReferenceParser
import com.rdp.biblequicksearchkjv.data.BibleRepository
import com.rdp.biblequicksearchkjv.data.BookCatalog

fun main() {
    val repository = BibleRepository()
    val parser = BibleReferenceParser(BookCatalog.books)

    check(BookCatalog.books.size == 66) { "Expected 66 books" }

    val john316 = parser.parse("John 3:16") ?: error("Failed to parse John 3:16")
    check(john316.book.name == "John")
    check(john316.chapter == 3)
    check(john316.verse == 16)

    val firstSamuel = parser.parse("First Samuel 3") ?: error("Failed to parse First Samuel 3")
    check(firstSamuel.book.name == "1 Samuel")
    check(firstSamuel.chapter == 3)

    val romanNumeral = parser.parse("II Timothy 1:7") ?: error("Failed to parse II Timothy 1:7")
    check(romanNumeral.book.name == "2 Timothy")
    check(romanNumeral.chapter == 1)
    check(romanNumeral.verse == 7)

    val phonetic = repository.search("Jhn")
    check(phonetic.matchingBooks.any { it.name == "John" }) { "Expected John in phonetic results" }

    val ordinalAlias = repository.search("1st cor")
    check(ordinalAlias.matchingBooks.any { it.name == "1 Corinthians" }) { "Expected 1 Corinthians alias match" }

    val content = repository.search("faith")
    check(content.matchingVerses.isNotEmpty()) { "Expected verse matches for faith" }

    val shepherd = repository.search("shepherd")
    check(shepherd.matchingVerses.any { it.chapter == 23 && it.bookId == 19 }) { "Expected Psalm 23 match" }

    println(
        "Harness OK: books=${BookCatalog.books.size}, ref=${john316.label}, alias=${firstSamuel.label}, verseHits=${content.matchingVerses.size}"
    )
}


