package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("image_search_preferences")

class ImageSearchPreferences(private val context: Context) {

    companion object {
        val SELECTED_ENGINE_KEY = stringPreferencesKey("selected_engine")
        
        const val ENGINE_GOOGLE_LENS = "google_lens"
        const val ENGINE_YANDEX = "yandex"
        const val ENGINE_BOTH = "google_lens,yandex"
        
        const val DEFAULT_ENGINE = ENGINE_GOOGLE_LENS
    }

    val selectedEngine: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_ENGINE_KEY] ?: DEFAULT_ENGINE
    }

    suspend fun setSelectedEngine(engine: String) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_ENGINE_KEY] = engine
        }
    }
}
