package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.PendingSearchAction
import com.example.data.model.SearchMode
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.pendingSearchDataStore by preferencesDataStore("khoj_pending_search_preferences")

class PendingSearchStore(private val context: Context) {

    companion object {
        private val KEY_QUERY = stringPreferencesKey("pending_search_query")
        private val KEY_MODE = stringPreferencesKey("pending_search_mode")
        private val KEY_TIMESTAMP = longPreferencesKey("pending_search_timestamp")

        const val DEFAULT_TTL_MS = 15 * 60 * 1000L // 15 minutes TTL
    }

    suspend fun savePendingSearch(query: String?, mode: SearchMode) {
        val sanitizedQuery = query?.take(2000) ?: ""
        context.pendingSearchDataStore.edit { prefs ->
            prefs[KEY_QUERY] = sanitizedQuery
            prefs[KEY_MODE] = mode.name
            prefs[KEY_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun getValidPendingSearch(ttlMillis: Long = DEFAULT_TTL_MS): PendingSearchAction? {
        val prefs = context.pendingSearchDataStore.data.firstOrNull() ?: return null
        val modeStr = prefs[KEY_MODE] ?: return null
        val timestamp = prefs[KEY_TIMESTAMP] ?: 0L
        val query = prefs[KEY_QUERY]

        val now = System.currentTimeMillis()
        if (now - timestamp > ttlMillis || timestamp <= 0L) {
            // Stale entry, clear and ignore
            clearPendingSearch()
            return null
        }

        val mode = SearchMode.fromIntentExtra(modeStr)
        return PendingSearchAction(
            query = query,
            mode = mode,
            timestamp = timestamp
        )
    }

    suspend fun clearPendingSearch() {
        context.pendingSearchDataStore.edit { prefs ->
            prefs.remove(KEY_QUERY)
            prefs.remove(KEY_MODE)
            prefs.remove(KEY_TIMESTAMP)
        }
    }
}
