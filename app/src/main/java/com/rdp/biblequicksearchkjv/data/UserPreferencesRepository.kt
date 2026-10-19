package com.rdp.biblequicksearchkjv.data

import android.content.Context
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.rdp.biblequicksearchkjv.model.BibleLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reader_preferences")

class UserPreferencesRepository(private val context: Context) {
    val preferences: Flow<ReaderPreferences> = context.dataStore.data.map { prefs ->
        ReaderPreferences(
            location = BibleLocation(
                bookId = prefs[Keys.bookId] ?: 43,
                chapter = prefs[Keys.chapter] ?: 3,
                highlightedVerse = prefs[Keys.verse]
            ),
            textScale = prefs[Keys.textScale] ?: 1.0f
        )
    }

    suspend fun saveLocation(location: BibleLocation) {
        context.dataStore.edit { prefs ->
            prefs[Keys.bookId] = location.bookId
            prefs[Keys.chapter] = location.chapter
            location.highlightedVerse?.let { prefs[Keys.verse] = it } ?: prefs.remove(Keys.verse)
        }
    }

    suspend fun saveTextScale(textScale: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.textScale] = textScale
        }
    }

    data class ReaderPreferences(
        val location: BibleLocation,
        val textScale: Float
    )

    private object Keys {
        val bookId = intPreferencesKey("book_id")
        val chapter = intPreferencesKey("chapter")
        val verse = intPreferencesKey("verse")
        val textScale = floatPreferencesKey("text_scale")
    }
}

