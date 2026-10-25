package com.kntndrsn.quicksearchkjvbible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kntndrsn.quicksearchkjvbible.MainViewModel
import com.kntndrsn.quicksearchkjvbible.data.BibleRepository
import com.kntndrsn.quicksearchkjvbible.data.ThemePreference
import com.kntndrsn.quicksearchkjvbible.model.BibleBook
import com.kntndrsn.quicksearchkjvbible.model.BibleVerse
import com.kntndrsn.quicksearchkjvbible.model.ReferenceMatch
import com.kntndrsn.quicksearchkjvbible.model.SearchResults
import com.kntndrsn.quicksearchkjvbible.ui.theme.BibleQuickSearchTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BibleQuickSearchApp(viewModel: MainViewModel = viewModel()) {
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()
    if (!isReady) return
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scale = viewModel.textScale.collectAsStateWithLifecycle()
    val deferredScale = remember(scale) { { scale.value } }
    val repository = viewModel.repository
    val focusManager = LocalFocusManager.current
    var isTableOfContentsVisible by rememberSaveable { mutableStateOf(false) }

    val systemInDark = isSystemInDarkTheme()
    val isDarkTheme = when (uiState.themePreference) {
        ThemePreference.SYSTEM -> systemInDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val currentBook = repository.bookById(uiState.currentLocation.bookId)

    val isSearchActive = uiState.isSearchFocused || uiState.searchQuery.isNotBlank()

    BackHandler(
        enabled = isTableOfContentsVisible || isSearchActive || uiState.canNavigateBack
    ) {
        when {
            isTableOfContentsVisible -> {
                isTableOfContentsVisible = false
            }
            isSearchActive -> {
                focusManager.clearFocus()
                viewModel.dismissSearch()
            }
            uiState.canNavigateBack -> {
                viewModel.navigateBack()
            }
        }
    }

    BibleQuickSearchTheme(darkTheme = isDarkTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .background(MaterialTheme.colorScheme.background)
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    ReaderTopBar(
                        book = currentBook,
                        chapter = uiState.currentLocation.chapter,
                        isDarkTheme = isDarkTheme,
                        textScale = deferredScale,
                        onOpenBook = {
                            focusManager.clearFocus()
                            viewModel.openBookPicker(currentBook)
                        },
                        onOpenTableOfContents = {
                            focusManager.clearFocus()
                            isTableOfContentsVisible = !isTableOfContentsVisible
                        },
                        onDecrease = { viewModel.stepTextScale(-0.1f) },
                        onReset = viewModel::resetTextScale,
                        onIncrease = { viewModel.stepTextScale(0.1f) },
                        onToggleTheme = { viewModel.toggleTheme(isDarkTheme) }
                    )
                },
                bottomBar = {
                    BottomBar(
                        query = uiState.searchQuery,
                        isSearchFocused = uiState.isSearchFocused,
                        onOpenTableOfContents = {
                            focusManager.clearFocus()
                            isTableOfContentsVisible = !isTableOfContentsVisible
                        },
                        onQueryChange = viewModel::updateSearchQuery,
                        onFocusChanged = viewModel::updateSearchFocus,
                        onClear = {
                            viewModel.updateSearchQuery("")
                        },
                        onSearchSubmit = {
                            val refMatch = uiState.searchResults.referenceMatch
                            val firstBook = uiState.searchResults.matchingBooks.firstOrNull()
                            val firstVerse = uiState.searchResults.matchingVerses.firstOrNull()
                            when {
                                refMatch != null -> {
                                    focusManager.clearFocus()
                                    viewModel.openChapter(refMatch.book, refMatch.chapter, refMatch.verse)
                                }
                                firstVerse != null -> {
                                    focusManager.clearFocus()
                                    val book = repository.bookById(firstVerse.bookId)
                                    viewModel.openChapter(book, firstVerse.chapter, firstVerse.verse)
                                }
                                firstBook != null -> {
                                    focusManager.clearFocus()
                                    viewModel.openBookPicker(firstBook)
                                }
                                else -> {
                                    focusManager.clearFocus()
                                }
                            }
                        }
                    )
                },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    ReaderPager(
                        repository = repository,
                        bookId = uiState.currentLocation.bookId,
                        chapter = uiState.currentLocation.chapter,
                        highlightedVerse = uiState.currentLocation.highlightedVerse,
                        targetVerse = uiState.targetVerse,
                        textScale = deferredScale,
                        modifier = Modifier.fillMaxSize(),
                        onChapterChanged = viewModel::onChapterPageChanged,
                        onReaderStopped = viewModel::onReaderStopped,
                        onReaderBackgrounded = viewModel::onReaderBackgrounded,
                        onZoomCommitted = viewModel::scaleTextByZoom,
                        onVerseSelected = viewModel::onVerseSelectedInReader
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (isSearchActive) SearchResultsPanel(
                            results = uiState.searchResults,
                            repository = repository,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.fillMaxWidth(),
                            onReferenceSelected = { match ->
                                focusManager.clearFocus()
                                viewModel.openChapter(match.book, match.chapter, match.verse)
                            },
                            onBookSelected = { book ->
                                focusManager.clearFocus()
                                viewModel.openBookPicker(book)
                            },
                            onVerseSelected = { verse ->
                                focusManager.clearFocus()
                                val book = repository.bookById(verse.bookId)
                                viewModel.openChapter(book, verse.chapter, verse.verse)
                            }
                        )
                    }

                    if (isTableOfContentsVisible) {
                        TableOfContentsDrawer(
                            books = uiState.books,
                            selectedBookId = currentBook.id,
                            onDismiss = { isTableOfContentsVisible = false },
                            onBookSelected = { book ->
                                isTableOfContentsVisible = false
                                viewModel.openBookPicker(book)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    book: BibleBook,
    chapter: Int,
    isDarkTheme: Boolean,
    textScale: () -> Float,
    onOpenBook: () -> Unit,
    onOpenTableOfContents: () -> Unit,
    onDecrease: () -> Unit,
    onReset: () -> Unit,
    onIncrease: () -> Unit,
    onToggleTheme: () -> Unit
) {
    TopAppBar(
        windowInsets = TopAppBarDefaults.windowInsets.union(
            WindowInsets.displayCutout.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            )
        ),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        title = {
            Column(modifier = Modifier.clickable(onClick = onOpenBook)) {
                Text(text = book.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "Chapter $chapter",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        },
        navigationIcon = {
            TableOfContentsButton(
                onClick = onOpenTableOfContents,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp)
            )
        },
        actions = {
            TextScaleControls(textScale, onDecrease, onReset, onIncrease)
            IconButton(onClick = onToggleTheme, modifier = Modifier.padding(end = 6.dp)) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (isDarkTheme) "Switch to light mode" else "Switch to dark mode"
                )
            }
        }
    )
}

@Composable
private fun TableOfContentsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Toc,
            contentDescription = "Table of contents",
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun TableOfContentsDrawer(
    books: List<BibleBook>,
    selectedBookId: Int,
    onDismiss: () -> Unit,
    onBookSelected: (BibleBook) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.36f))
                .clickable(onClick = onDismiss)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 250.dp, max = 320.dp)
                    .fillMaxWidth(0.84f),
                tonalElevation = 3.dp,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(24.dp)
            ) {
                TableOfContentsPanel(
                    books = books,
                    selectedBookId = selectedBookId,
                    onDismiss = onDismiss,
                    onBookSelected = onBookSelected
                )
            }
        }
    }
}

@Composable
private fun TableOfContentsPanel(
    books: List<BibleBook>,
    selectedBookId: Int,
    onDismiss: () -> Unit,
    onBookSelected: (BibleBook) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedBookId, books) {
        val selectedIndex = books.indexOfFirst { it.id == selectedBookId }
        if (selectedIndex >= 0) {
            listState.scrollToItem((selectedIndex - 2).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Toc,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Table of Contents",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close table of contents")
            }
        }
        HorizontalDivider()
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = book.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${book.chapterCount} ch",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    query: String,
    isSearchFocused: Boolean,
    onOpenTableOfContents: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    onSearchSubmit: () -> Unit
) {
    val bottomInsets = WindowInsets.navigationBars
        .union(WindowInsets.ime)
        .union(
            WindowInsets.displayCutout.only(
                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
            )
        )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(bottomInsets)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TableOfContentsButton(
                onClick = onOpenTableOfContents
            )

            SearchBox(
                query = query,
                isSearchFocused = isSearchFocused,
                onQueryChange = onQueryChange,
                onFocusChanged = onFocusChanged,
                onClear = onClear,
                onSearchSubmit = onSearchSubmit,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SearchBox(
    query: String,
    isSearchFocused: Boolean,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    onSearchSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isActive = isSearchFocused || query.isNotBlank()

    Surface(
        modifier = modifier
            .heightIn(min = if (isActive) 56.dp else 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                focusRequester.requestFocus()
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (isActive) 10.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(if (isActive) 26.dp else 22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = if (isSearchFocused) "Type verse, book, or word..." else "Search",
                        style = TextStyle(
                            fontSize = if (isSearchFocused) 20.sp else 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .testTag("search_input")
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusChanged(it.isFocused) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() })
                )
            }
            if (query.isNotBlank()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsPanel(
    results: SearchResults,
    repository: BibleRepository,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    onReferenceSelected: (ReferenceMatch) -> Unit,
    onBookSelected: (BibleBook) -> Unit,
    onVerseSelected: (BibleVerse) -> Unit
) {
    val hasResults = results.referenceMatch != null ||
        results.matchingBooks.isNotEmpty() ||
        results.matchingVerses.isNotEmpty()

    if (!hasResults) return

    Card(
        modifier = modifier.heightIn(max = 260.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = if (isDarkTheme) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme) {
                Color(0xFF2E3138)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().testTag("search_results")
        ) {
            results.referenceMatch?.let { match ->
                item(key = "reference", contentType = "reference") {
                    SearchSectionTitle("Reference")
                    SearchRow(
                        title = match.label,
                        subtitle = "Open chapter for reading${match.verse?.let { ", highlight verse $it" } ?: ""}",
                        modifier = Modifier.testTag("search_reference_result"),
                        onClick = { onReferenceSelected(match) }
                    )
                }
            }

            if (results.matchingBooks.isNotEmpty()) {
                item(key = "books_header", contentType = "header") { SearchSectionTitle("Books") }
                items(results.matchingBooks, key = { it.id }, contentType = { "book" }) { book ->
                    SearchRow(
                        title = book.name,
                        subtitle = "Tap to pick a chapter quickly",
                        onClick = { onBookSelected(book) }
                    )
                }
            }

            if (results.matchingVerses.isNotEmpty()) {
                item(key = "verses_header", contentType = "header") { SearchSectionTitle("Verses") }
                items(results.matchingVerses, key = { verseKey(it.bookId, it.chapter, it.verse) }, contentType = { "verse" }) { verse ->
                    val book = repository.bookById(verse.bookId)
                    SearchRow(
                        title = "${book.name} ${verse.chapter}:${verse.verse}",
                        subtitle = plainVerseText(verse.text),
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
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
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

private fun plainVerseText(raw: String): String = buildString(raw.length) {
    raw.forEach { if (it != '‹' && it != '›' && it != '[' && it != ']') append(it) }
}

internal fun buildStyledVerseAnnotatedString(
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
    textScale: () -> Float,
    onDecrease: () -> Unit,
    onReset: () -> Unit,
    onIncrease: () -> Unit
) {
    val scale = textScale()
    val percentLabel = "${(scale * 100).roundToInt()}%"
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = onDecrease,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            modifier = Modifier.widthIn(min = 36.dp).testTag("text_scale_decrease")
        ) {
            Text("A-")
        }
        TextButton(
            onClick = onReset,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            modifier = Modifier.widthIn(min = 48.dp).testTag("text_scale_reset")
        ) {
            Text(
                text = percentLabel,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("text_scale_value").semantics {
                    contentDescription = "Text scale ${"%.1f".format(java.util.Locale.ROOT, scale)}"
                }
            )
        }
        TextButton(
            onClick = onIncrease,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            modifier = Modifier.widthIn(min = 36.dp).testTag("text_scale_increase")
        ) {
            Text("A+")
        }
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
                    modifier = Modifier.heightIn(max = 280.dp),
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



