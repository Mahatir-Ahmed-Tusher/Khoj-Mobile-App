package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CanonicalClaimItem
import com.example.data.model.DistrictHotspotItem
import com.example.data.model.MediaSpreadItem
import com.example.data.model.NarrativeItem
import com.example.data.model.RumorMeterData
import com.example.data.model.RumorTrendsData
import com.example.data.repository.RumorMeterRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class RumorMeterUiState {
    object Loading : RumorMeterUiState()
    data class Success(
        val meterData: RumorMeterData,
        val trendsData: RumorTrendsData,
        val narratives: List<NarrativeItem>,
        val hotspots: List<DistrictHotspotItem>,
        val mediaSpread: List<MediaSpreadItem>,
        val claims: List<CanonicalClaimItem>,
        val lastSyncTimestamp: Long
    ) : RumorMeterUiState()
    data class Error(
        val message: String,
        val fallbackData: RumorMeterData? = null
    ) : RumorMeterUiState()
}

class RumorMeterViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("rumor_meter_cache_prefs", Context.MODE_PRIVATE)
    private val repository = RumorMeterRepository(prefs)

    private val _uiState = MutableStateFlow<RumorMeterUiState>(RumorMeterUiState.Loading)
    val uiState: StateFlow<RumorMeterUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _claims = MutableStateFlow<List<CanonicalClaimItem>>(emptyList())
    val claims: StateFlow<List<CanonicalClaimItem>> = _claims.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow("ALL")
    val activeFilter: StateFlow<String> = _activeFilter.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var searchJob: Job? = null

    init {
        // 1. Instant cache load (0ms first frame)
        loadFromCache()

        // 2. Fetch fresh data from network
        fetchFullObservatoryData(isBackground = _uiState.value is RumorMeterUiState.Success)

        // 3. Start silent periodic background polling (every 60 seconds)
        startSilentAutoRefresher()
    }

    private fun loadFromCache() {
        val cachedMeter = repository.getCachedMeterData()
        val cachedTrends = repository.getCachedTrendsData() ?: RumorTrendsData()
        val cachedNarratives = repository.getCachedNarratives()
        val cachedHotspots = repository.getCachedHotspots()
        val cachedMedia = repository.getCachedMediaSpread()
        val cachedClaims = repository.getCachedClaims()
        val lastSync = repository.getLastSyncTime()

        if (cachedMeter != null) {
            _claims.value = cachedClaims
            _uiState.value = RumorMeterUiState.Success(
                meterData = cachedMeter,
                trendsData = cachedTrends,
                narratives = cachedNarratives,
                hotspots = cachedHotspots,
                mediaSpread = cachedMedia,
                claims = cachedClaims,
                lastSyncTimestamp = lastSync
            )
        }
    }

    private fun startSilentAutoRefresher() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000L) // Poll every 60 seconds
                try {
                    val freshMeter = repository.fetchMeterStats()
                    val currentState = _uiState.value
                    if (currentState is RumorMeterUiState.Success) {
                        if (currentState.meterData != freshMeter) {
                            // If stats changed, fetch rest and update state silently
                            val freshTrends = repository.fetchTrends()
                            _uiState.value = currentState.copy(
                                meterData = freshMeter,
                                trendsData = freshTrends,
                                lastSyncTimestamp = System.currentTimeMillis()
                            )
                        }
                    }
                } catch (_: Throwable) {
                    // Silent fail during background poll, keep existing UI intact
                }
            }
        }
    }

    fun refresh(showSpinner: Boolean = true) {
        if (showSpinner) {
            _isRefreshing.value = true
        }
        fetchFullObservatoryData(isBackground = !showSpinner)
    }

    private fun fetchFullObservatoryData(isBackground: Boolean) {
        viewModelScope.launch {
            try {
                val meter = repository.fetchMeterStats()
                val trends = try { repository.fetchTrends() } catch (_: Throwable) { RumorTrendsData() }
                val narratives = try { repository.fetchNarratives() } catch (_: Throwable) { emptyList() }
                val hotspots = try { repository.fetchHotspots() } catch (_: Throwable) { emptyList() }
                val media = try { repository.fetchMediaSpread() } catch (_: Throwable) { emptyList() }
                val claims = try { repository.fetchClaims(page = 1) } catch (_: Throwable) { emptyList() }

                _claims.value = claims
                _uiState.value = RumorMeterUiState.Success(
                    meterData = meter,
                    trendsData = trends,
                    narratives = narratives,
                    hotspots = hotspots,
                    mediaSpread = media,
                    claims = claims,
                    lastSyncTimestamp = System.currentTimeMillis()
                )
            } catch (e: Throwable) {
                if (_uiState.value !is RumorMeterUiState.Success) {
                    val fallback = repository.getCachedMeterData()
                    _uiState.value = RumorMeterUiState.Error(
                        message = e.localizedMessage ?: "Failed to connect to Khoj Rumour Meter",
                        fallbackData = fallback
                    )
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300L) // Debounce
            executeClaimsFilter()
        }
    }

    fun onFilterSelected(filter: String) {
        _activeFilter.value = filter
        viewModelScope.launch {
            executeClaimsFilter()
        }
    }

    private suspend fun executeClaimsFilter() {
        val q = _searchQuery.value.trim().takeIf { it.isNotBlank() }
        val filter = _activeFilter.value

        val isZombie = if (filter == "ZOMBIE") true else null
        val isCrossBorder = if (filter == "CROSS_BORDER") true else null
        val isGenderTargeted = if (filter == "GENDER_TARGETED") true else null

        try {
            val results = repository.fetchClaims(
                page = 1,
                limit = 25,
                query = q,
                zombie = isZombie,
                crossBorder = isCrossBorder,
                genderTargeted = isGenderTargeted
            )
            _claims.value = results
        } catch (_: Throwable) {
            // Keep current claims on error
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        searchJob?.cancel()
    }
}
