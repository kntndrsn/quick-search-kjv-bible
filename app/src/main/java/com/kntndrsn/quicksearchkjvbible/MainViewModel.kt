package com.kntndrsn.quicksearchkjvbible

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kntndrsn.quicksearchkjvbible.data.BibleRepository
import com.kntndrsn.quicksearchkjvbible.data.ThemePreference
import com.kntndrsn.quicksearchkjvbible.data.UserPreferencesRepository
import com.kntndrsn.quicksearchkjvbible.model.BibleBook
import com.kntndrsn.quicksearchkjvbible.model.BibleLocation
import com.kntndrsn.quicksearchkjvbible.model.SearchResults
import com.kntndrsn.quicksearchkjvbible.model.VerseTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = BibleRepository(application)
    private val preferencesRepository = UserPreferencesRepository(application)
    private val searchQuery = MutableStateFlow("")
    private val navigationBackStack = ArrayDeque<BibleLocation>()
    private val persistenceJob = SupervisorJob()
    private val persistenceScope = CoroutineScope(persistenceJob + Dispatchers.IO)
    private val pendingPreferences = Channel<PendingPreferences>(Channel.CONFLATED)
    private var saveScaleJob: Job? = null
    private var saveLocationJob: Job? = null
    private var latestLocation = BibleLocation()
    private var preferencesLoaded = false

    private val _uiState = MutableStateFlow(MainUiState(books = repository.books()))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val _textScale = MutableStateFlow(DEFAULT_TEXT_SCALE)
    val textScale: StateFlow<Float> = _textScale.asStateFlow()
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        persistenceScope.launch {
            for (pending in pendingPreferences) {
                preferencesRepository.saveLocation(pending.location)
                preferencesRepository.saveTextScale(pending.textScale)
            }
        }
        viewModelScope.launch {
            val initialChapterLoad = async(Dispatchers.IO) { repository.warmUp() }
            val preferences = preferencesRepository.preferences.first()
            val location = clampLocation(preferences.location)
            latestLocation = location
            _textScale.value = preferences.textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
            _uiState.update {
                it.copy(
                    currentLocation = location,
                    targetVerse = VerseTarget(location.scrollVerse),
                    themePreference = preferences.themePreference
                )
            }
            initialChapterLoad.await()
            if (location.bookId != 1 || location.chapter != 1) {
                repository.warmUp(location.bookId, location.chapter)
            }
            preferencesLoaded = true
            _isReady.value = true
        }
        viewModelScope.launch {
            searchQuery.mapLatest { query ->
                val navigation = repository.searchNavigation(query)
                _uiState.update {
                    if (it.searchQuery == query) it.copy(searchResults = navigation) else it
                }
                delay(120)
                query to navigation.copy(matchingVerses = repository.searchVerseText(query))
            }.flowOn(Dispatchers.Default).collectLatest { (query, results) ->
                _uiState.update {
                    if (it.searchQuery == query) it.copy(searchResults = results) else it
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        if (_uiState.value.searchQuery == query) return
        _uiState.update { it.copy(searchQuery = query, searchResults = SearchResults()) }
        searchQuery.value = query
    }

    fun updateSearchFocus(isFocused: Boolean) {
        _uiState.update { it.copy(isSearchFocused = isFocused) }
    }

    fun dismissSearch() {
        _uiState.update {
            it.copy(isSearchFocused = false, searchQuery = "", searchResults = SearchResults())
        }
        searchQuery.value = ""
    }

    fun openBookPicker(book: BibleBook) {
        if (book.chapterCount == 1) {
            openChapter(book, 1, highlightedVerse = 1)
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
        val previous = latestLocation
        val location = clampLocation(
            BibleLocation(book.id, chapter, highlightedVerse, highlightedVerse ?: 1)
        )
        if (recordHistory && !isSamePlace(previous, location) &&
            navigationBackStack.lastOrNull()?.let { isSamePlace(it, previous) } != true
        ) {
            if (navigationBackStack.size >= MAX_BACK_STACK_SIZE) navigationBackStack.removeFirst()
            navigationBackStack.addLast(previous)
        }
        _uiState.update {
            it.copy(
                currentLocation = location,
                targetVerse = VerseTarget(location.scrollVerse, it.targetVerse.request + 1),
                canNavigateBack = navigationBackStack.isNotEmpty(),
                chapterPickerBook = null,
                isSearchFocused = false,
                searchQuery = if (clearSearch) "" else it.searchQuery,
                searchResults = if (clearSearch) SearchResults() else it.searchResults
            )
        }
        if (clearSearch) searchQuery.value = ""
        latestLocation = location
        persistLocation()
    }

    fun navigateBack(): Boolean {
        val location = clampLocation(navigationBackStack.removeLastOrNull() ?: return false)
        _uiState.update {
            it.copy(
                currentLocation = location,
                targetVerse = VerseTarget(location.scrollVerse, it.targetVerse.request + 1),
                canNavigateBack = navigationBackStack.isNotEmpty(),
                chapterPickerBook = null,
                isSearchFocused = false
            )
        }
        latestLocation = location
        persistLocation()
        return true
    }

    fun onReaderStopped(bookId: Int, chapter: Int, verse: Int) {
        if (!preferencesLoaded || verse < 1 ||
            latestLocation.bookId != bookId || latestLocation.chapter != chapter
        ) return
        latestLocation = latestLocation.copy(scrollVerse = verse)
        persistLocation(debounce = true)
    }

    fun onReaderBackgrounded(bookId: Int, chapter: Int, verse: Int) {
        onReaderStopped(bookId, chapter, verse)
        flushPreferences()
    }

    fun onVerseSelectedInReader(verse: Int) {
        val current = _uiState.value.currentLocation
        val updated = latestLocation.copy(
            highlightedVerse = if (current.highlightedVerse == verse) null else verse
        )
        latestLocation = updated
        // Keep the navigation target fixed when toggling a highlight.
        _uiState.update {
            it.copy(currentLocation = current.copy(highlightedVerse = updated.highlightedVerse))
        }
        persistLocation()
    }

    fun stepTextScale(delta: Float) {
        setTextScale(_textScale.value + delta)
    }

    fun scaleTextByZoom(zoomFactor: Float) {
        if (zoomFactor.isFinite() && zoomFactor > 0f) {
            setTextScale(_textScale.value * zoomFactor)
        }
    }

    fun resetTextScale() = setTextScale(DEFAULT_TEXT_SCALE)

    private fun setTextScale(scale: Float) {
        val updated = scale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        _textScale.value = updated
        saveScaleJob?.cancel()
        saveScaleJob = viewModelScope.launch {
            delay(500)
            enqueuePreferences()
        }
    }

    fun toggleTheme(currentlyDark: Boolean) {
        val theme = if (currentlyDark) ThemePreference.LIGHT else ThemePreference.DARK
        _uiState.update { it.copy(themePreference = theme) }
        persistenceScope.launch { preferencesRepository.saveThemePreference(theme) }
    }

    fun onChapterPageChanged(bookId: Int, chapter: Int) {
        if (!preferencesLoaded) return
        val current = _uiState.value.currentLocation
        if (bookId == current.bookId && chapter == current.chapter) return
        val location = clampLocation(BibleLocation(bookId, chapter))
        latestLocation = location
        _uiState.update {
            it.copy(
                currentLocation = location,
                targetVerse = VerseTarget(location.scrollVerse, it.targetVerse.request + 1)
            )
        }
        persistLocation()
    }

    fun flushPreferences() {
        if (!preferencesLoaded) return
        saveLocationJob?.cancel()
        saveScaleJob?.cancel()
        enqueuePreferences()
    }

    override fun onCleared() {
        flushPreferences()
        pendingPreferences.close()
        // This scope outlives viewModelScope long enough to finish the final DataStore write.
        persistenceJob.complete()
        super.onCleared()
    }

    private fun isSamePlace(a: BibleLocation, b: BibleLocation): Boolean =
        a.bookId == b.bookId && a.chapter == b.chapter && a.targetVerse == b.targetVerse

    private fun persistLocation(debounce: Boolean = false) {
        if (!preferencesLoaded) return
        saveLocationJob?.cancel()
        saveLocationJob = viewModelScope.launch {
            if (debounce) delay(500)
            enqueuePreferences()
        }
    }

    private fun enqueuePreferences() {
        pendingPreferences.trySend(PendingPreferences(latestLocation, _textScale.value))
    }

    private fun clampLocation(location: BibleLocation): BibleLocation {
        val book = repository.bookById(location.bookId)
        return location.copy(bookId = book.id, chapter = location.chapter.coerceIn(1, book.chapterCount))
    }

    companion object {
        private const val MAX_BACK_STACK_SIZE = 50
        const val MIN_TEXT_SCALE = 0.5f
        const val MAX_TEXT_SCALE = 2.0f
        const val DEFAULT_TEXT_SCALE = 1.0f
    }

    private data class PendingPreferences(val location: BibleLocation, val textScale: Float)
}

@Immutable
data class MainUiState(
    val books: List<BibleBook> = emptyList(),
    val currentLocation: BibleLocation = BibleLocation(),
    val targetVerse: VerseTarget = VerseTarget(),
    val canNavigateBack: Boolean = false,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val searchQuery: String = "",
    val searchResults: SearchResults = SearchResults(),
    val isSearchFocused: Boolean = false,
    val chapterPickerBook: BibleBook? = null
)
