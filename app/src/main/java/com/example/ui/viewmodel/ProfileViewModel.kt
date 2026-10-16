package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.AnalyticsEngine
import com.example.data.api.AnalyticsResult
import com.example.data.model.UserSearchHistory
import com.example.data.repository.ProfileRepository
import com.example.data.repository.FactCheckRepository
import com.example.data.local.UserAvatarManager
import com.example.data.model.SupabaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = FactCheckRepository(application)
    private val deepSearchRepo = com.example.data.repository.DeepSearchHistoryRepository.getInstance(application)
    private var historyJob: kotlinx.coroutines.Job? = null

    private val _searchHistory = MutableStateFlow<List<UserSearchHistory>>(emptyList())
    val searchHistory: StateFlow<List<UserSearchHistory>> = _searchHistory.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    private val _analyticsResult = MutableStateFlow<AnalyticsResult?>(null)
    val analyticsResult: StateFlow<AnalyticsResult?> = _analyticsResult.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isUpdatingName = MutableStateFlow(false)
    val isUpdatingName: StateFlow<Boolean> = _isUpdatingName.asStateFlow()

    private val _updateNameError = MutableStateFlow<String?>(null)
    val updateNameError: StateFlow<String?> = _updateNameError.asStateFlow()

    private val _remoteHistory = MutableStateFlow<List<UserSearchHistory>>(emptyList())
    private val _locallyDeletedItemIds = MutableStateFlow<Set<String>>(emptySet())

    private val _customAvatarUrl = MutableStateFlow<String?>(null)
    val customAvatarUrl: StateFlow<String?> = _customAvatarUrl.asStateFlow()

    private val _effectiveAvatarUrl = MutableStateFlow<String>("")
    val effectiveAvatarUrl: StateFlow<String> = _effectiveAvatarUrl.asStateFlow()

    fun loadAvatarState(user: SupabaseUser?) {
        val app = getApplication<Application>()
        val effective = UserAvatarManager.getEffectiveAvatarUrl(app, user)
        val custom = user?.id?.let { UserAvatarManager.getCustomAvatar(app, it) }
        _customAvatarUrl.value = custom
        _effectiveAvatarUrl.value = effective
    }

    fun saveCustomAvatar(user: SupabaseUser?, avatarUrl: String) {
        val userId = user?.id ?: return
        val app = getApplication<Application>()
        UserAvatarManager.saveCustomAvatar(app, userId, avatarUrl)
        loadAvatarState(user)
    }

    fun deleteCustomAvatar(user: SupabaseUser?) {
        val userId = user?.id ?: return
        val app = getApplication<Application>()
        UserAvatarManager.deleteCustomAvatar(app, userId)
        loadAvatarState(user)
    }

    private val profilePrefs = application.getSharedPreferences("profile_prefs", android.content.Context.MODE_PRIVATE)
    private var activeUserId: String? = null

    private fun getAnalyticsCacheKey(userId: String?): String {
        return "cached_analytics_result_${userId ?: "default"}"
    }

    private fun loadCachedAnalytics(userId: String?) {
        val key = getAnalyticsCacheKey(userId)
        val cachedJson = profilePrefs.getString(key, null)
        if (!cachedJson.isNullOrBlank()) {
            try {
                _analyticsResult.value = kotlinx.serialization.json.Json.decodeFromString<AnalyticsResult>(cachedJson)
            } catch (e: Exception) {
                _analyticsResult.value = null
            }
        } else {
            _analyticsResult.value = null
        }
    }

    private fun saveCachedAnalytics(userId: String?, result: AnalyticsResult?) {
        val key = getAnalyticsCacheKey(userId)
        if (result != null) {
            try {
                val encoded = kotlinx.serialization.json.Json.encodeToString(AnalyticsResult.serializer(), result)
                profilePrefs.edit().putString(key, encoded).apply()
            } catch (_: Exception) {}
        } else {
            profilePrefs.edit().remove(key).apply()
        }
    }

    fun loadProfileData(userId: String) {
        activeUserId = userId
        loadCachedAnalytics(userId)
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _isLoadingHistory.value = true
            _errorMessage.value = null

            // Trigger deep search sync
            deepSearchRepo.syncWithRemote()

            val remoteResult = ProfileRepository.fetchSearchHistory(userId)
            val fetchedRemote = remoteResult.getOrNull() ?: emptyList()
            
            // Filter out any locally deleted item IDs so they don't resurrect from cloud
            val deletedIds = _locallyDeletedItemIds.value
            _remoteHistory.value = fetchedRemote.filter { item ->
                val idMatch = item.id != null && deletedIds.contains(item.id)
                val reportMatch = item.reportId != null && deletedIds.contains(item.reportId)
                !idMatch && !reportMatch
            }

            kotlinx.coroutines.flow.combine(
                repo.allReports,
                deepSearchRepo.historyFlow,
                _remoteHistory
            ) { factReports, deepReports, remoteList ->
                val currentDeleted = _locallyDeletedItemIds.value
                val factHistory = factReports
                    .filter { !currentDeleted.contains(it.id) }
                    .map { report ->
                        UserSearchHistory(
                            id = report.id,
                            userId = report.userId ?: userId,
                            query = report.claimText,
                            searchType = "factcheck",
                            reportId = report.id,
                            createdAt = formatTimestampToIso(report.timestamp)
                        )
                    }
                val deepHistory = deepReports
                    .filter { !currentDeleted.contains(it.id) }
                    .map { deep ->
                        UserSearchHistory(
                            id = deep.id,
                            userId = deep.userId ?: userId,
                            query = deep.query,
                            searchType = "deepsearch",
                            reportId = deep.id,
                            createdAt = formatTimestampToIso(deep.timestamp)
                        )
                    }
                val filteredRemote = remoteList.filter { item ->
                    val idMatch = item.id != null && currentDeleted.contains(item.id)
                    val reportMatch = item.reportId != null && currentDeleted.contains(item.reportId)
                    !idMatch && !reportMatch
                }
                (factHistory + deepHistory + filteredRemote)
                    .distinctBy { it.reportId ?: it.query }
                    .sortedByDescending { getHistoryItemTimestamp(it) }
            }.collect { mergedHistory ->
                _searchHistory.value = mergedHistory
                _isLoadingHistory.value = false
            }
        }
    }

    private fun formatTimestampToIso(timestamp: Long): String {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.format(java.util.Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    private fun getHistoryItemTimestamp(item: UserSearchHistory): Long {
        if (item.createdAt.isNullOrBlank()) return 0L
        return try {
            val cleanString = item.createdAt.replace("Z", "+0000")
            val formatPattern = if (cleanString.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSSZ" else "yyyy-MM-dd'T'HH:mm:ssZ"
            val sdf = java.text.SimpleDateFormat(formatPattern, java.util.Locale.US)
            sdf.parse(cleanString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun deleteHistoryItem(item: UserSearchHistory, onCompleted: (() -> Unit)? = null) {
        val itemId = item.id ?: return
        val itemQuery = item.query
        val itemReportId = item.reportId

        // Blacklist IDs to prevent any resurrection
        val targetIds = listOfNotNull(itemId, itemReportId).distinct()
        _locallyDeletedItemIds.value = _locallyDeletedItemIds.value + targetIds

        // 1. OPTIMISTIC INSTANT UPDATE (0ms delay)
        _remoteHistory.value = _remoteHistory.value.filter { remoteItem ->
            val matchId = remoteItem.id != null && targetIds.contains(remoteItem.id)
            val matchReport = remoteItem.reportId != null && targetIds.contains(remoteItem.reportId)
            !matchId && !matchReport
        }
        _searchHistory.value = _searchHistory.value.filter { currentItem ->
            val matchId = currentItem.id != null && targetIds.contains(currentItem.id)
            val matchReport = currentItem.reportId != null && targetIds.contains(currentItem.reportId)
            !matchId && !matchReport
        }

        // If all history items are deleted, clear cached analytics
        if (_searchHistory.value.isEmpty()) {
            _analyticsResult.value = null
            saveCachedAnalytics(activeUserId, null)
        }

        onCompleted?.invoke()

        viewModelScope.launch {
            // 2. Local persistence deletions (Room SQLite & DataStore)
            if (!itemReportId.isNullOrBlank()) {
                repo.deleteReport(itemReportId)
                deepSearchRepo.deleteReport(itemReportId)
            }
            if (itemId != itemReportId) {
                repo.deleteReport(itemId)
                deepSearchRepo.deleteReport(itemId)
            }

            // 3. Remote Supabase deletion (concurrent)
            val result = ProfileRepository.deleteHistoryItem(itemId, itemQuery, itemReportId)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                android.util.Log.e("ProfileViewModel", "Remote deletion notice: ${error?.message}")
            }
        }
    }

    fun updateProfileName(userId: String, fullName: String, onDone: () -> Unit) {
        if (fullName.isBlank()) {
            _updateNameError.value = "Name cannot be blank"
            return
        }
        viewModelScope.launch {
            _isUpdatingName.value = true
            _updateNameError.value = null
            val result = ProfileRepository.updateProfileName(userId, fullName.trim())
            if (result.isSuccess) {
                onDone()
            } else {
                val error = result.exceptionOrNull()
                _updateNameError.value = error?.localizedMessage ?: "Failed to update profile name"
            }
            _isUpdatingName.value = false
        }
    }

    fun analyzeHabits(history: List<UserSearchHistory>, targetUserId: String? = activeUserId) {
        if (history.isEmpty()) {
            _analyticsResult.value = null
            _analysisError.value = null
            saveCachedAnalytics(targetUserId, null)
            return
        }
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null
            val queries = history.map { it.query }
            val result = AnalyticsEngine.analyzeSearchHabits(queries)
            if (result.isSuccess) {
                val newResult = result.getOrNull()
                _analyticsResult.value = newResult
                _analysisError.value = null
                saveCachedAnalytics(targetUserId, newResult)
            } else {
                val error = result.exceptionOrNull()
                _analysisError.value = error?.localizedMessage ?: "Failed to analyze search habits"
            }
            _isAnalyzing.value = false
        }
    }
}
