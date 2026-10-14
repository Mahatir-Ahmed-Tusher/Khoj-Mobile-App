package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TrendingRegion
import com.example.data.model.TrendingRumourUi
import com.example.data.repository.TrendingRumoursRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class TrendingRumoursState {
    object Loading : TrendingRumoursState()
    data class Success(val rumours: List<TrendingRumourUi>) : TrendingRumoursState()
    data class Error(val message: String, val cachedRumours: List<TrendingRumourUi>? = null) : TrendingRumoursState()
}

class TrendingRumoursViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsPrefs = application.getSharedPreferences("khoj_settings", Context.MODE_PRIVATE)
    private val cachePrefs = application.getSharedPreferences("trending_rumours_prefs", Context.MODE_PRIVATE)
    private val repository = TrendingRumoursRepository(cachePrefs)

    private val _uiState = MutableStateFlow<TrendingRumoursState>(TrendingRumoursState.Loading)
    val uiState: StateFlow<TrendingRumoursState> = _uiState.asStateFlow()

    private var cachedList: List<TrendingRumourUi>? = null
    private var activeRegion: TrendingRegion = currentRegion()

    private val cacheListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "trending_rumours_cache_${activeRegion.name}") {
            val freshCache = repository.getCachedRumours(activeRegion)
            if (freshCache.isNotEmpty() && freshCache != cachedList) {
                cachedList = freshCache
                _uiState.value = TrendingRumoursState.Success(freshCache)
            }
        }
    }

    init {
        cachePrefs.registerOnSharedPreferenceChangeListener(cacheListener)

        val region = activeRegion
        val localCache = repository.getCachedRumours(region)
        if (localCache.isNotEmpty()) {
            cachedList = localCache
            _uiState.value = TrendingRumoursState.Success(localCache)
        }

        // Auto-fetch fresh rumours in background on startup without blocking the user
        fetchTrendingRumours(isBackgroundSync = true)
    }

    private fun currentRegion(): TrendingRegion {
        val lang = settingsPrefs.getString("app_language_option", "bn") ?: "bn"
        return if (lang == "bn") TrendingRegion.BD else TrendingRegion.GLOBAL
    }

    fun onLanguageChanged(isBn: Boolean) {
        val newRegion = if (isBn) TrendingRegion.BD else TrendingRegion.GLOBAL
        if (activeRegion == newRegion && _uiState.value is TrendingRumoursState.Success) {
            return
        }
        activeRegion = newRegion

        // 1. Instantly switch to the cached data for new region without waiting
        val localCache = repository.getCachedRumours(newRegion)
        if (localCache.isNotEmpty()) {
            cachedList = localCache
            _uiState.value = TrendingRumoursState.Success(localCache)
        } else {
            _uiState.value = TrendingRumoursState.Loading
        }

        // 2. Fetch fresh rumours in background for new region
        fetchTrendingRumours(isBackgroundSync = localCache.isNotEmpty())
    }

    /**
     * Automatic freshness sync: called on screen view / resume.
     * If cache is older than 10 minutes, silently triggers a background fetch without showing skeletons.
     */
    fun autoSyncIfNeeded(force: Boolean = false) {
        val lastSync = repository.getLastSyncTime(activeRegion)
        val staleThresholdMs = 10 * 60 * 1000L // 10 minutes
        val isStale = (System.currentTimeMillis() - lastSync) > staleThresholdMs
        val isCacheEmpty = cachedList.isNullOrEmpty()

        if (force || isStale || isCacheEmpty) {
            fetchTrendingRumours(isBackgroundSync = !isCacheEmpty)
        }
    }

    fun fetchTrendingRumours(isBackgroundSync: Boolean = false) {
        val region = activeRegion
        if (!isBackgroundSync && _uiState.value !is TrendingRumoursState.Success) {
            _uiState.value = TrendingRumoursState.Loading
        }
        viewModelScope.launch {
            try {
                val rumours = repository.fetchTrendingRumours(region)
                if (rumours.isNotEmpty()) {
                    cachedList = rumours
                    _uiState.value = TrendingRumoursState.Success(rumours)
                } else if (cachedList.isNullOrEmpty()) {
                    _uiState.value = TrendingRumoursState.Success(emptyList())
                }
            } catch (e: Exception) {
                // If we already have cached data, retain it gracefully
                if (cachedList.isNullOrEmpty()) {
                    _uiState.value = TrendingRumoursState.Error(
                        message = e.localizedMessage ?: "Failed to fetch trending rumours",
                        cachedRumours = cachedList
                    )
                }
            }
        }
    }

    /** Forced refresh: always shows skeleton, then re-fetches. */
    fun refresh() {
        _uiState.value = TrendingRumoursState.Loading
        fetchTrendingRumours(isBackgroundSync = false)
    }

    override fun onCleared() {
        super.onCleared()
        cachePrefs.unregisterOnSharedPreferenceChangeListener(cacheListener)
    }
}
