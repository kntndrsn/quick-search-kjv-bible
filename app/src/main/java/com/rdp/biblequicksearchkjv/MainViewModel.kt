package com.rdp.biblequicksearchkjv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rdp.biblequicksearchkjv.data.BibleRepository
import com.rdp.biblequicksearchkjv.data.ThemePreference
import com.rdp.biblequicksearchkjv.data.UserPreferencesRepository
import com.rdp.biblequicksearchkjv.model.BibleBook
import com.rdp.biblequicksearchkjv.model.BibleLocation
import com.rdp.biblequicksearchkjv.model.SearchResults
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = BibleRepository(application)
    private val preferencesRepository = UserPreferencesRepository(application)
    private val searchQuery = MutableStateFlow("")
    private val navigationBackStack = ArrayDeque<BibleLocation>()
    private var saveScaleJob: Job? = null

    private val _uiState = MutableStateFlow(MainUiState(books = repository.books()))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val preferences = preferencesRepository.preferences.first()
            _uiState.update { state ->
                state.copy(
                    currentLocation = clampLocation(preferences.location),
                    textScale = preferences.textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE),
                    themePreference = preferences.themePreference
                )
            }
        }

        viewModelScope.launch {
            searchQuery
                .debounce(180.milliseconds)
                .distinctUntilChanged()
                .collect { query ->
                    _uiState.update { state ->
                        state.copy(
                            searchResults = repository.search(query)
                        )
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                searchResults = if (query.isBlank()) SearchResults() else it.searchResults
            )
        }
        searchQuery.value = query
    }

    fun updateSearchFocus(isFocused: Boolean) {
        _uiState.update { it.copy(isSearchFocused = isFocused) }
    }

    fun dismissSearch() {
        _uiState.update {
            it.copy(
                isSearchFocused = false,
                searchQuery = "",
                searchResults = SearchResults()
            )
        }
        searchQuery.value = ""
    }

    fun openBookPicker(book: BibleBook) {
        if (book.chapterCount == 1) {
            openChapter(book = book, chapter = 1, highlightedVerse = 1)
        } else {
            _uiState.update { it.copy(chapterPickerBook = book) }
        }
    }

    fun dismissChapterPicker() {
        _uiState.update { it.copy(chapterPickerBook = null) }
    }

    fun openChapter(
        book: BibleBook,
        chapter: Int,
        highlightedVerse: Int? = null,
        clearSearch: Boolean = true,
        recordHistory: Boolean = true
    ) {
        val previousLocation = _uiState.value.currentLocation
        val location = clampLocation(
            BibleLocation(
                bookId = book.id,
                chapter = chapter,
                highlightedVerse = highlightedVerse,
                scrollVerse = highlightedVerse ?: 1
            )
        )

        if (recordHistory && !isSamePlace(previousLocation, location)) {
            if (navigationBackStack.lastOrNull()?.let { isSamePlace(it, previousLocation) } != true) {
                if (navigationBackStack.size >= MAX_BACK_STACK_SIZE) {
                    navigationBackStack.removeFirst()
                }
                navigationBackStack.addLast(previousLocation)
            }
        }

        _uiState.update {
            it.copy(
                currentLocation = location,
                canNavigateBack = navigationBackStack.isNotEmpty(),
                chapterPickerBook = null,
                isSearchFocused = false,
                searchQuery = if (clearSearch) "" else it.searchQuery,
                searchResults = if (clearSearch) SearchResults() else it.searchResults
            )
        }
        if (clearSearch) {
            searchQuery.value = ""
        }
        persistLocation(location)
    }

    fun navigateBack(): Boolean {
        val previous = navigationBackStack.removeLastOrNull() ?: return false
        val clamped = clampLocation(previous)
        _uiState.update {
            it.copy(
                currentLocation = clamped,
                canNavigateBack = navigationBackStack.isNotEmpty(),
                chapterPickerBook = null,
                isSearchFocused = false
            )
        }
        persistLocation(clamped)
        return true
    }

    fun onVisibleVerseChanged(verse: Int) {
        if (verse < 1) return
        _uiState.update { state ->
            if (state.currentLocation.scrollVerse == verse) {
                state
            } else {
                state.copy(
                    currentLocation = state.currentLocation.copy(scrollVerse = verse)
                )
            }
        }
    }

    fun onVerseSelectedInReader(verse: Int) {
        val current = _uiState.value.currentLocation
        val newHighlight = if (current.highlightedVerse == verse) null else verse
        val updated = current.copy(
            highlightedVerse = newHighlight,
            scrollVerse = verse
        )
        _uiState.update { it.copy(currentLocation = updated) }
        persistLocation(updated)
    }

    fun stepTextScale(delta: Float) {
        val updated = (_uiState.value.textScale + delta).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        _uiState.update { it.copy(textScale = updated) }
        persistTextScale(updated, immediate = true)
    }

    fun scaleTextByZoom(zoomFactor: Float) {
        if (zoomFactor <= 0f || zoomFactor.isNaN()) return
        val current = _uiState.value.textScale
        val updated = (current * zoomFactor).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        if (updated == current) return
        _uiState.update { it.copy(textScale = updated) }
        persistTextScale(updated, immediate = false)
    }

    fun resetTextScale() {
        _uiState.update { it.copy(textScale = DEFAULT_TEXT_SCALE) }
        persistTextScale(DEFAULT_TEXT_SCALE, immediate = true)
    }

    fun toggleTheme(currentlyDark: Boolean) {
        val nextTheme = if (currentlyDark) ThemePreference.LIGHT else ThemePreference.DARK
        _uiState.update { it.copy(themePreference = nextTheme) }
        viewModelScope.launch {
            preferencesRepository.saveThemePreference(nextTheme)
        }
    }

    private fun persistTextScale(scale: Float, immediate: Boolean) {
        saveScaleJob?.cancel()
        saveScaleJob = viewModelScope.launch {
            if (!immediate) {
                delay(200.milliseconds)
            }
            preferencesRepository.saveTextScale(scale)
        }
    }

    fun onChapterPageChanged(chapter: Int) {
        val current = _uiState.value.currentLocation
        if (chapter == current.chapter) return
        val updated = clampLocation(
            current.copy(
                chapter = chapter,
                highlightedVerse = null,
                scrollVerse = 1
            )
        )
        _uiState.update { it.copy(currentLocation = updated) }
        persistLocation(updated)
    }

    private fun isSamePlace(a: BibleLocation, b: BibleLocation): Boolean {
        return a.bookId == b.bookId &&
            a.chapter == b.chapter &&
            a.targetVerse == b.targetVerse
    }

    private fun persistLocation(location: BibleLocation) {
        viewModelScope.launch {
            preferencesRepository.saveLocation(location)
        }
    }

    private fun clampLocation(location: BibleLocation): BibleLocation {
        val book = repository.bookById(location.bookId)
        return location.copy(
            chapter = min(max(location.chapter, 1), book.chapterCount)
        )
    }

    companion object {
        private const val MAX_BACK_STACK_SIZE = 50
        const val MIN_TEXT_SCALE = 0.5f
        const val MAX_TEXT_SCALE = 2.0f
        const val DEFAULT_TEXT_SCALE = 1.0f
    }
}

data class MainUiState(
    val books: List<BibleBook> = emptyList(),
    val currentLocation: BibleLocation = BibleLocation(),
    val canNavigateBack: Boolean = false,
    val textScale: Float = MainViewModel.DEFAULT_TEXT_SCALE,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val searchQuery: String = "",
    val searchResults: SearchResults = SearchResults(),
    val isSearchFocused: Boolean = false,
    val chapterPickerBook: BibleBook? = null
)




