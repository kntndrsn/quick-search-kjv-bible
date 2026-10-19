package com.rdp.biblequicksearchkjv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rdp.biblequicksearchkjv.data.BibleRepository
import com.rdp.biblequicksearchkjv.data.UserPreferencesRepository
import com.rdp.biblequicksearchkjv.model.BibleBook
import com.rdp.biblequicksearchkjv.model.BibleLocation
import com.rdp.biblequicksearchkjv.model.SearchResults
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BibleRepository(application)
    private val preferencesRepository = UserPreferencesRepository(application)
    private val searchQuery = MutableStateFlow("")

    private val _uiState = MutableStateFlow(MainUiState(books = repository.books()))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                _uiState.update { state ->
                    state.copy(
                        currentLocation = clampLocation(preferences.location),
                        textScale = preferences.textScale
                    )
                }
            }
        }

        viewModelScope.launch {
            searchQuery
                .debounce(180)
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
        _uiState.update { it.copy(searchQuery = query) }
        searchQuery.value = query
    }

    fun updateSearchFocus(isFocused: Boolean) {
        _uiState.update { it.copy(isSearchFocused = isFocused) }
    }

    fun openBookPicker(book: BibleBook) {
        _uiState.update { it.copy(chapterPickerBook = book) }
    }

    fun dismissChapterPicker() {
        _uiState.update { it.copy(chapterPickerBook = null) }
    }

    fun openChapter(book: BibleBook, chapter: Int, highlightedVerse: Int? = null, clearSearch: Boolean = true) {
        val location = clampLocation(BibleLocation(book.id, chapter, highlightedVerse))
        _uiState.update {
            it.copy(
                currentLocation = location,
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

    fun stepTextScale(delta: Float) {
        val updated = (_uiState.value.textScale + delta).coerceIn(0.8f, 1.6f)
        _uiState.update { it.copy(textScale = updated) }
        viewModelScope.launch {
            preferencesRepository.saveTextScale(updated)
        }
    }

    fun resetTextScale() {
        _uiState.update { it.copy(textScale = 1.0f) }
        viewModelScope.launch {
            preferencesRepository.saveTextScale(1.0f)
        }
    }

    fun onChapterPageChanged(chapter: Int) {
        val current = _uiState.value.currentLocation
        if (chapter == current.chapter) return
        val updated = clampLocation(current.copy(chapter = chapter, highlightedVerse = null))
        _uiState.update { it.copy(currentLocation = updated) }
        persistLocation(updated)
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
}

data class MainUiState(
    val books: List<BibleBook> = emptyList(),
    val currentLocation: BibleLocation = BibleLocation(),
    val textScale: Float = 1.0f,
    val searchQuery: String = "",
    val searchResults: SearchResults = SearchResults(),
    val isSearchFocused: Boolean = false,
    val chapterPickerBook: BibleBook? = null
)

