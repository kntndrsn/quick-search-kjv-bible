package com.kntndrsn.quicksearchkjvbible.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kntndrsn.quicksearchkjvbible.model.BibleLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reader_preferences")

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

class UserPreferencesRepository(private val context: Context) {
    val preferences: Flow<ReaderPreferences> = context.dataStore.data.map { prefs ->
        val themeName = prefs[Keys.themePreference]
        val themePref = themeName?.let {
            runCatching { ThemePreference.valueOf(it) }.getOrNull()
        } ?: ThemePreference.SYSTEM
        val highlightedVerse = prefs[Keys.verse]

        ReaderPreferences(
            location = BibleLocation(
                bookId = prefs[Keys.bookId] ?: 1,
                chapter = prefs[Keys.chapter] ?: 1,
                highlightedVerse = highlightedVerse,
                scrollVerse = prefs[Keys.scrollVerse] ?: highlightedVerse ?: 1
            ),
            textScale = prefs[Keys.textScale] ?: 1.0f,
            themePreference = themePref
        )
    }

    suspend fun saveLocation(location: BibleLocation) {
        context.dataStore.edit { prefs ->
            prefs[Keys.bookId] = location.bookId
            prefs[Keys.chapter] = location.chapter
            location.highlightedVerse?.let { prefs[Keys.verse] = it } ?: prefs.remove(Keys.verse)
            location.scrollVerse?.let { prefs[Keys.scrollVerse] = it } ?: prefs.remove(Keys.scrollVerse)
        }
    }

    suspend fun saveTextScale(textScale: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.textScale] = textScale
        }
    }

    suspend fun saveThemePreference(themePreference: ThemePreference) {
        context.dataStore.edit { prefs ->
            prefs[Keys.themePreference] = themePreference.name
        }
    }

    data class ReaderPreferences(
        val location: BibleLocation,
        val textScale: Float,
        val themePreference: ThemePreference = ThemePreference.SYSTEM
    )

    private object Keys {
        val bookId = intPreferencesKey("book_id")
        val chapter = intPreferencesKey("chapter")
        val verse = intPreferencesKey("verse")
        val scrollVerse = intPreferencesKey("scroll_verse")
        val textScale = floatPreferencesKey("text_scale")
        val themePreference = stringPreferencesKey("theme_preference")
    }
}
