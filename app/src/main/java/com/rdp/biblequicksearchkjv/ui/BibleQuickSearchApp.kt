package com.rdp.biblequicksearchkjv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rdp.biblequicksearchkjv.MainUiState
import com.rdp.biblequicksearchkjv.MainViewModel
import com.rdp.biblequicksearchkjv.data.BibleRepository
import com.rdp.biblequicksearchkjv.model.BibleBook
import com.rdp.biblequicksearchkjv.model.BibleVerse
import com.rdp.biblequicksearchkjv.ui.theme.BibleQuickSearchTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BibleQuickSearchApp(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { BibleRepository(context) }
    var isQuickReferenceVisible by rememberSaveable { mutableStateOf(false) }

    BibleQuickSearchTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            val configuration = LocalConfiguration.current
            // Keep the panel pinned only on true tablet-width layouts.
            val isExpanded =
                configuration.smallestScreenWidthDp >= 600 && configuration.screenWidthDp >= 720
            val currentBook = remember(uiState.currentLocation.bookId, uiState.books) {
                uiState.books.firstOrNull { it.id == uiState.currentLocation.bookId } ?: uiState.books.first()
            }

            LaunchedEffect(isExpanded) {
                if (isExpanded) {
                    isQuickReferenceVisible = false
                }
            }

            if (!isExpanded && isQuickReferenceVisible) {
                BackHandler { isQuickReferenceVisible = false }
            }

            val body: @Composable () -> Unit = {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(text = currentBook.name)
                                    Text(
                                        text = "Chapter ${uiState.currentLocation.chapter}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            navigationIcon = {
                                if (!isExpanded) {
                                    IconButton(onClick = { isQuickReferenceVisible = true }) {
                                        Icon(Icons.Default.Bookmarks, contentDescription = "Open quick reference")
                                    }
                                }
                            },
                            actions = {
                                TextScaleControls(
                                    textScale = uiState.textScale,
                                    onDecrease = { viewModel.stepTextScale(-0.1f) },
                                    onReset = viewModel::resetTextScale,
                                    onIncrease = { viewModel.stepTextScale(0.1f) }
                                )
                            }
                        )
                    },
                    bottomBar = {
                        SearchBar(
                            state = uiState,
                            onQueryChange = viewModel::updateSearchQuery,
                            onFocusChanged = viewModel::updateSearchFocus,
                            onClear = { viewModel.updateSearchQuery("") }
                        )
                    },
                    contentWindowInsets = WindowInsets.navigationBars
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (isExpanded) {
                                QuickReferencePanel(
                                    books = uiState.books,
                                    selectedBookId = currentBook.id,
                                    onBookSelected = viewModel::openBookPicker
                                )
                                Divider(modifier = Modifier.fillMaxHeight())
                            }

                            ReaderPager(
                                repository = repository,
                                currentBook = currentBook,
                                state = uiState,
                                modifier = Modifier.weight(1f),
                                onChapterChanged = viewModel::onChapterPageChanged
                            )
                        }

                        SearchResultsPanel(
                            state = uiState,
                            repository = repository,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 88.dp),
                            onReferenceSelected = { match ->
                                viewModel.openChapter(match.book, match.chapter, match.verse)
                            },
                            onBookSelected = viewModel::openBookPicker,
                            onVerseSelected = { verse ->
                                val book = repository.bookById(verse.bookId)
                                viewModel.openChapter(book, verse.chapter, verse.verse)
                            }
                        )
                    }
                }
            }

            if (isExpanded) {
                body()
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    body()
                    if (isQuickReferenceVisible) {
                        CompactQuickReferenceDrawer(
                            books = uiState.books,
                            selectedBookId = currentBook.id,
                            onDismiss = { isQuickReferenceVisible = false },
                            onBookSelected = {
                                isQuickReferenceVisible = false
                                viewModel.openBookPicker(it)
                            }
                        )
                    }
                }
            }

            uiState.chapterPickerBook?.let { book ->
                ChapterPickerDialog(
                    book = book,
                    onDismiss = viewModel::dismissChapterPicker,
                    onChapterSelected = { chapter -> viewModel.openChapter(book, chapter) }
                )
            }
        }
    }
}

@Composable
private fun CompactQuickReferenceDrawer(
    books: List<BibleBook>,
    selectedBookId: Int,
    onDismiss: () -> Unit,
    onBookSelected: (BibleBook) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .clickable(onClick = onDismiss)
        )
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.82f),
            tonalElevation = 2.dp,
            shadowElevation = 10.dp,
            shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
        ) {
            QuickReferencePanel(
                books = books,
                selectedBookId = selectedBookId,
                showDismiss = true,
                onDismiss = onDismiss,
                onBookSelected = onBookSelected
            )
        }
    }
}

@Composable
private fun QuickReferencePanel(
    books: List<BibleBook>,
    selectedBookId: Int,
    showDismiss: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    onBookSelected: (BibleBook) -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bookmarks, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Quick reference",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            if (showDismiss && onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close quick reference")
                }
            }
        }
        Divider()
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(books, key = { it.id }) { book ->
                val isSelected = book.id == selectedBookId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBookSelected(book) }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = book.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderPager(
    repository: BibleRepository,
    currentBook: BibleBook,
    state: MainUiState,
    modifier: Modifier = Modifier,
    onChapterChanged: (Int) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = state.currentLocation.chapter - 1,
        pageCount = { currentBook.chapterCount }
    )

    LaunchedEffect(currentBook.id) {
        pagerState.scrollToPage(state.currentLocation.chapter - 1)
    }

    LaunchedEffect(state.currentLocation.chapter) {
        val targetPage = state.currentLocation.chapter - 1
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState, currentBook.id) {
        snapshotFlow { pagerState.currentPage }
            .map { it + 1 }
            .distinctUntilChanged()
            .filter { it in 1..currentBook.chapterCount }
            .collect(onChapterChanged)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { page ->
        val chapter = page + 1
        val verses = remember(currentBook.id, chapter) {
            repository.chapterVerses(currentBook.id, chapter)
        }
        ReaderChapter(
            book = currentBook,
            chapter = chapter,
            verses = verses,
            highlightedVerse = if (chapter == state.currentLocation.chapter) state.currentLocation.highlightedVerse else null,
            textScale = state.textScale
        )
    }
}

@Composable
private fun ReaderChapter(
    book: BibleBook,
    chapter: Int,
    verses: List<BibleVerse>,
    highlightedVerse: Int?,
    textScale: Float
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Chapter $chapter",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Swipe left or right to move quickly between chapters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(verses, key = { "${it.bookId}-${it.chapter}-${it.verse}" }) { verse ->
            Surface(
                color = if (highlightedVerse == verse.verse) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                tonalElevation = if (highlightedVerse == verse.verse) 2.dp else 0.dp,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = verse.verse.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                    val styledVerseText = rememberStyledVerseText(verse.text, readerMode = true)
                    Text(
                        text = styledVerseText,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp * textScale),
                        lineHeight = 28.sp * textScale
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    state: MainUiState,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp)
    ) {
        TextField(
            value = state.searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChanged(it.isFocused) },
            placeholder = { Text("Search reference, book, or verse words") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SearchResultsPanel(
    state: MainUiState,
    repository: BibleRepository,
    modifier: Modifier = Modifier,
    onReferenceSelected: (com.rdp.biblequicksearchkjv.model.ReferenceMatch) -> Unit,
    onBookSelected: (BibleBook) -> Unit,
    onVerseSelected: (BibleVerse) -> Unit
) {
    val hasResults = state.searchResults.referenceMatch != null ||
        state.searchResults.matchingBooks.isNotEmpty() ||
        state.searchResults.matchingVerses.isNotEmpty()

    if (!(state.isSearchFocused || state.searchQuery.isNotBlank()) || !hasResults) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.height(320.dp)
        ) {
            state.searchResults.referenceMatch?.let { match ->
                item {
                    SearchSectionTitle("Reference")
                    SearchRow(
                        title = match.label,
                        subtitle = AnnotatedString("Open chapter for reading${match.verse?.let { ", highlight verse $it" } ?: ""}"),
                        onClick = { onReferenceSelected(match) }
                    )
                }
            }

            if (state.searchResults.matchingBooks.isNotEmpty()) {
                item { SearchSectionTitle("Books") }
                items(state.searchResults.matchingBooks, key = { it.id }) { book ->
                    SearchRow(
                        title = book.name,
                        subtitle = AnnotatedString("Tap to pick a chapter quickly"),
                        onClick = { onBookSelected(book) }
                    )
                }
            }

            if (state.searchResults.matchingVerses.isNotEmpty()) {
                item { SearchSectionTitle("Verses") }
                items(state.searchResults.matchingVerses, key = { "${it.bookId}-${it.chapter}-${it.verse}" }) { verse ->
                    val book = repository.bookById(verse.bookId)
                    SearchRow(
                        title = "${book.name} ${verse.chapter}:${verse.verse}",
                        subtitle = rememberStyledVerseText(verse.text),
                        onClick = { onVerseSelected(verse) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun SearchRow(
    title: String,
    subtitle: AnnotatedString,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun rememberStyledVerseText(raw: String): AnnotatedString {
    return rememberStyledVerseText(raw, readerMode = false)
}

@Composable
private fun rememberStyledVerseText(raw: String, readerMode: Boolean): AnnotatedString {
    val christWordColor = MaterialTheme.colorScheme.error
    val pilcrowColor = MaterialTheme.colorScheme.primary
    return remember(raw, readerMode, christWordColor, pilcrowColor) {
        buildStyledVerseAnnotatedString(
            raw = raw,
            christWordColor = christWordColor,
            pilcrowColor = pilcrowColor,
            paragraphBreakBeforePilcrow = readerMode
        )
    }
}

private fun buildStyledVerseAnnotatedString(
    raw: String,
    christWordColor: Color,
    pilcrowColor: Color,
    paragraphBreakBeforePilcrow: Boolean
): AnnotatedString {
    fun styleFor(isChristWord: Boolean, isItalic: Boolean): SpanStyle? {
        val hasStyling = isChristWord || isItalic
        if (!hasStyling) return null
        return SpanStyle(
            color = if (isChristWord) christWordColor else Color.Unspecified,
            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
        )
    }

    return buildAnnotatedString {
        val buffer = StringBuilder()
        var inChristWords = false
        var inBracket = false

        fun flush() {
            if (buffer.isEmpty()) return
            val start = length
            append(buffer.toString())
            val style = styleFor(inChristWords, inBracket)
            if (style != null) {
                addStyle(style, start, length)
            }
            buffer.clear()
        }

        for (char in raw) {
            when (char) {
                '‹' -> {
                    flush()
                    inChristWords = true
                }
                '›' -> {
                    flush()
                    inChristWords = false
                }
                '[' -> {
                    flush()
                    inBracket = true
                }
                ']' -> {
                    flush()
                    inBracket = false
                }
                '¶' -> {
                    flush()
                    if (paragraphBreakBeforePilcrow && length > 0) {
                        append('\n')
                    }
                    val start = length
                    append(char)
                    addStyle(SpanStyle(color = pilcrowColor, fontWeight = FontWeight.SemiBold), start, length)
                }
                else -> buffer.append(char)
            }
        }

        flush()
    }
}

@Composable
private fun TextScaleControls(
    textScale: Float,
    onDecrease: () -> Unit,
    onReset: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDecrease) { Text("A-") }
        TextButton(onClick = onReset) { Text(text = "A", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onIncrease) { Text("A+") }
    }
}

@Composable
private fun ChapterPickerDialog(
    book: BibleBook,
    onDismiss: () -> Unit,
    onChapterSelected: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Choose chapter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(book.name)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(56.dp),
                    modifier = Modifier.height(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(book.chapterCount) { index ->
                        val chapter = index + 1
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .size(width = 56.dp, height = 44.dp)
                                .clickable { onChapterSelected(chapter) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(chapter.toString(), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    )
}


