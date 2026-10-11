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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isUpdatingName = MutableStateFlow(false)
    val isUpdatingName: StateFlow<Boolean> = _isUpdatingName.asStateFlow()

    private val _updateNameError = MutableStateFlow<String?>(null)
    val updateNameError: StateFlow<String?> = _updateNameError.asStateFlow()

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

    fun loadProfileData(userId: String) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _isLoadingHistory.value = true
            _errorMessage.value = null
            val remoteResult = ProfileRepository.fetchSearchHistory(userId)
            val remoteHistory = remoteResult.getOrNull() ?: emptyList()

            // Trigger deep search sync
            deepSearchRepo.syncWithRemote()

            kotlinx.coroutines.flow.combine(repo.allReports, deepSearchRepo.historyFlow) { factReports, deepReports ->
                val factHistory = factReports.map { report ->
                    UserSearchHistory(
                        id = report.id,
                        userId = report.userId ?: userId,
                        query = report.claimText,
                        searchType = "factcheck",
                        reportId = report.id,
                        createdAt = formatTimestampToIso(report.timestamp)
                    )
                }
                val deepHistory = deepReports.map { deep ->
                    UserSearchHistory(
                        id = deep.id,
                        userId = deep.userId ?: userId,
                        query = deep.query,
                        searchType = "deepsearch",
                        reportId = deep.id,
                        createdAt = formatTimestampToIso(deep.timestamp)
                    )
                }
                (factHistory + deepHistory)
            }.collect { localHistoryList ->
                val mergedHistory = (localHistoryList + remoteHistory)
                    .distinctBy { it.reportId ?: it.query }
                    .sortedByDescending { getHistoryItemTimestamp(it) }

                _searchHistory.value = mergedHistory

                // Auto trigger analytics analysis if not empty and not already analyzed
                if (mergedHistory.isNotEmpty() && _analyticsResult.value == null) {
                    analyzeHabits(mergedHistory)
                }
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

    fun deleteHistoryItem(item: UserSearchHistory) {
        val itemId = item.id ?: return
        val itemQuery = item.query
        val itemReportId = item.reportId
        viewModelScope.launch {
            val result = ProfileRepository.deleteHistoryItem(itemId, itemQuery, itemReportId)
            if (result.isSuccess) {
                // Filter out the deleted item from the state list locally to give instant feedback
                _searchHistory.value = _searchHistory.value.filter { it.id != itemId }
                
                // Clear analytics and re-analyze with remaining history
                val remaining = _searchHistory.value
                if (remaining.isNotEmpty()) {
                    analyzeHabits(remaining)
                } else {
                    _analyticsResult.value = null
                }
            } else {
                val error = result.exceptionOrNull()
                _errorMessage.value = error?.localizedMessage ?: "Failed to delete history item"
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

    fun analyzeHabits(history: List<UserSearchHistory>) {
        if (history.isEmpty()) {
            _analyticsResult.value = null
            return
        }
        viewModelScope.launch {
            _isAnalyzing.value = true
            val queries = history.map { it.query }
            val result = AnalyticsEngine.analyzeSearchHabits(queries)
            if (result.isSuccess) {
                _analyticsResult.value = result.getOrNull()
            } else {
                // Keep previous analytics or clear depending on preference, we will keep it null
                _analyticsResult.value = null
            }
            _isAnalyzing.value = false
        }
    }
}
