package com.kntndrsn.quicksearchkjvbible.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kntndrsn.quicksearchkjvbible.MainViewModel
import com.kntndrsn.quicksearchkjvbible.data.BibleRepository
import com.kntndrsn.quicksearchkjvbible.model.BibleBook
import com.kntndrsn.quicksearchkjvbible.model.BibleVerse
import com.kntndrsn.quicksearchkjvbible.model.VerseTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
private class ChapterIndex(books: List<BibleBook>) {
    private val offsets = IntArray((books.maxOfOrNull { it.id } ?: 0) + 1)
    private val pageBooks = IntArray(books.sumOf { it.chapterCount })
    val size: Int get() = pageBooks.size

    init {
        var page = 0
        books.forEach { book ->
            offsets[book.id] = page
            repeat(book.chapterCount) { pageBooks[page++] = book.id }
        }
    }

    fun page(bookId: Int, chapter: Int): Int = offsets[bookId] + chapter - 1
    fun bookId(page: Int): Int = pageBooks[page]
    fun chapter(page: Int): Int = page - offsets[pageBooks[page]] + 1
}

internal fun verseKey(bookId: Int, chapter: Int, verse: Int): Int =
    bookId * 1_000_000 + chapter * 1_000 + verse

@Composable
internal fun ReaderPager(
    repository: BibleRepository,
    bookId: Int,
    chapter: Int,
    highlightedVerse: Int?,
    targetVerse: VerseTarget?,
    textScale: () -> Float,
    modifier: Modifier = Modifier,
    onChapterChanged: (Int, Int) -> Unit,
    onReaderStopped: (Int, Int, Int) -> Unit,
    onReaderBackgrounded: (Int, Int, Int) -> Unit,
    onZoomCommitted: (Float) -> Unit,
    onVerseSelected: (Int) -> Unit
) {
    val index = remember(repository) { ChapterIndex(repository.books()) }
    val desiredPage = index.page(bookId, chapter)
    val pagerState = rememberPagerState(initialPage = desiredPage, pageCount = { index.size })
    val chapterChanged by rememberUpdatedState(onChapterChanged)

    // The settled-page collector must not report the old page during an explicit navigation.
    var requestedPage by remember { mutableStateOf<Int?>(desiredPage) }
    LaunchedEffect(desiredPage) {
        requestedPage = desiredPage
        if (pagerState.settledPage != desiredPage) pagerState.scrollToPage(desiredPage)
        requestedPage = null
    }
    LaunchedEffect(pagerState, index) {
        snapshotFlow { pagerState.settledPage to requestedPage }
            .distinctUntilChanged()
            .collectLatest { (page, requested) ->
                if (requested != null) return@collectLatest
                chapterChanged(index.bookId(page), index.chapter(page))
                listOf(page - 1, page + 1).filter { it in 0 until index.size }.forEach { adjacent ->
                    this@LaunchedEffect.launch { repository.prefetch(index.bookId(adjacent), index.chapter(adjacent)) }
                }
            }
    }

    var gestureScale by remember { mutableFloatStateOf(1f) }
    var gestureOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    val currentScale by rememberUpdatedState(textScale)
    val commitZoom by rememberUpdatedState(onZoomCommitted)
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = modifier
            .testTag("chapter_pager")
            .graphicsLayer {
                scaleX = gestureScale
                scaleY = gestureScale
                transformOrigin = gestureOrigin
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zooming = false
                    var totalZoom = 1f
                    val initialScale = currentScale()
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                val zoom = event.calculateZoom()
                                if (zoom.isFinite() && zoom > 0f) {
                                    if (!zooming) {
                                        val centroid = event.calculateCentroid()
                                        gestureOrigin = TransformOrigin(
                                            (centroid.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                            (centroid.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        )
                                    }
                                    zooming = true
                                    totalZoom = (totalZoom * zoom).coerceIn(
                                        MainViewModel.MIN_TEXT_SCALE / initialScale,
                                        MainViewModel.MAX_TEXT_SCALE / initialScale
                                    )
                                    gestureScale = totalZoom
                                }
                            }
                            if (zooming) event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        gestureScale = 1f
                        if (zooming && totalZoom != 1f) commitZoom(totalZoom)
                    }
                }
            }
    ) { page ->
        val pageBook = index.bookId(page)
        val pageChapter = index.chapter(page)
        val isSelected = pageBook == bookId && pageChapter == chapter
        ReaderChapter(
            repository = repository,
            bookId = pageBook,
            chapter = pageChapter,
            highlightedVerse = if (isSelected) highlightedVerse else null,
            targetVerse = if (isSelected) targetVerse else null,
            textScale = textScale,
            isSettled = page == pagerState.settledPage && isSelected,
            onReaderStopped = onReaderStopped,
            onReaderBackgrounded = onReaderBackgrounded,
            onVerseSelected = onVerseSelected
        )
    }
}

@Immutable
private data class ReaderVerseText(val verse: BibleVerse, val text: AnnotatedString)

@Immutable
private data class ChapterText(val verses: List<ReaderVerseText> = emptyList())

// Compose 1.7's lint detector misses producer assignments with the Kotlin 2 compiler.
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
internal fun ReaderChapter(
    repository: BibleRepository,
    bookId: Int,
    chapter: Int,
    highlightedVerse: Int?,
    targetVerse: VerseTarget?,
    textScale: () -> Float,
    isSettled: Boolean,
    onReaderStopped: (Int, Int, Int) -> Unit,
    onReaderBackgrounded: (Int, Int, Int) -> Unit,
    onVerseSelected: (Int) -> Unit
) {
    val verses by produceState(
        initialValue = repository.cachedChapter(bookId, chapter).orEmpty(),
        repository, bookId, chapter
    ) {
        this.value = repository.cachedChapter(bookId, chapter) ?: repository.chapterVerses(bookId, chapter)
    }
    val christColor = MaterialTheme.colorScheme.error
    val numberColor = MaterialTheme.colorScheme.primary
    val chapterText by produceState(ChapterText(), verses, christColor, numberColor, isSettled) {
        if (!isSettled) {
            this.value = ChapterText()
            return@produceState
        }
        this.value = withContext(Dispatchers.Default) {
            ChapterText(verses.map { verse ->
                ReaderVerseText(verse, buildAnnotatedString {
                    val start = length
                    append(verse.verse.toString())
                    addStyle(SpanStyle(color = numberColor, fontWeight = FontWeight.Bold, fontSize = 0.78.em), start, length)
                    append("  ")
                    append(buildStyledVerseAnnotatedString(verse.text, christColor, numberColor))
                })
            })
        }
    }
    val listState = rememberLazyListState()
    val settled by rememberUpdatedState(isSettled)
    val stopped by rememberUpdatedState(onReaderStopped)
    val backgrounded by rememberUpdatedState(onReaderBackgrounded)
    val renderedVerses by rememberUpdatedState(chapterText.verses)
    fun visibleVerse(): Int =
        renderedVerses.getOrNull((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))?.verse?.verse ?: 1

    val hasContent = chapterText.verses.isNotEmpty()
    LaunchedEffect(bookId, chapter, targetVerse, hasContent) {
        if (chapterText.verses.isEmpty()) return@LaunchedEffect
        val target = targetVerse?.verse ?: return@LaunchedEffect
        val verseIndex = chapterText.verses.indexOfFirst { it.verse.verse == target }
        if (target <= 1) listState.scrollToItem(0)
        else if (verseIndex >= 0) listState.scrollToItem(verseIndex + 1, -48)
    }
    LaunchedEffect(listState, bookId, chapter) {
        // Snapshot observation stays local; only a stopped scroll crosses the ViewModel boundary.
        snapshotFlow { listState.firstVisibleItemIndex to listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (_, scrolling) ->
                if (!scrolling && settled && renderedVerses.isNotEmpty()) {
                    stopped(bookId, chapter, visibleVerse())
                }
            }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, bookId, chapter) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && settled && renderedVerses.isNotEmpty()) {
                backgrounded(bookId, chapter, visibleVerse())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (settled && renderedVerses.isNotEmpty()) stopped(bookId, chapter, visibleVerse())
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scale = textScale()
    val baseStyle = MaterialTheme.typography.bodyLarge
    val verseStyle = remember(scale, baseStyle) {
        baseStyle.copy(fontSize = (18f * scale).sp, lineHeight = (26f * scale).sp)
    }
    val verticalPadding = (3f * scale).coerceIn(1.5f, 8f).dp
    val highlightColor = MaterialTheme.colorScheme.secondaryContainer
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().then(
            if (isSettled && chapterText.verses.isNotEmpty()) Modifier.testTag("chapter_list") else Modifier
        ),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy((2f * scale).coerceIn(1f, 6f).dp)
    ) {
        item(key = verseKey(bookId, chapter, 0), contentType = "header") {
            ChapterHeader(repository.bookById(bookId).name, chapter)
        }
        items(
            chapterText.verses,
            key = { verseKey(it.verse.bookId, it.verse.chapter, it.verse.verse) },
            contentType = { "verse" }
        ) { verse ->
            ReaderVerse(
                verse = verse,
                highlighted = highlightedVerse == verse.verse.verse,
                style = verseStyle,
                verticalPadding = verticalPadding,
                highlightColor = highlightColor,
                onVerseSelected = onVerseSelected
            )
        }
    }
}

@Composable
private fun ChapterHeader(bookName: String, chapter: Int) {
    Column(
        modifier = Modifier.padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(bookName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Chapter $chapter",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Swipe left or right to move between chapters. Pinch to resize text.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReaderVerse(
    verse: ReaderVerseText,
    highlighted: Boolean,
    style: TextStyle,
    verticalPadding: Dp,
    highlightColor: androidx.compose.ui.graphics.Color,
    onVerseSelected: (Int) -> Unit
) {
    Text(
        text = verse.text,
        style = style,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier.drawBehind {
                        drawRoundRect(highlightColor, cornerRadius = CornerRadius(10.dp.toPx()))
                    }
                } else {
                    Modifier
                }
            )
            .clickable { onVerseSelected(verse.verse.verse) }
            .padding(horizontal = 8.dp, vertical = verticalPadding)
    )
}
