package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.WebSearchEngine
import com.example.data.api.WebSearchResult
import com.example.data.local.FactCheckDatabase
import com.example.data.model.WebSearchHistoryEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WebSearchCategory(val tag: String, val searxngCategory: String) {
    GENERAL("General", "general"),
    IMAGES("Images", "images"),
    NEWS("News", "news"),
    VIDEOS("Videos", "videos"),
    MAPS("Maps", "map")
}

data class CategoryState(
    val results: List<WebSearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val currentPage: Int = 1,
    val hasError: Boolean = false,
    val hasReachedEnd: Boolean = false
)

class WebSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = WebSearchEngine()
    private val historyDao = FactCheckDatabase.getDatabase(application).webSearchHistoryDao()

    val searchHistory: StateFlow<List<WebSearchHistoryEntity>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentQuery = MutableStateFlow("")
    val currentQuery = _currentQuery.asStateFlow()

    private val _activeCategory = MutableStateFlow(WebSearchCategory.GENERAL)
    val activeCategory = _activeCategory.asStateFlow()

    // Category states mapped by category
    private val _categoryStates = MutableStateFlow<Map<WebSearchCategory, CategoryState>>(emptyMap())
    val categoryStates = _categoryStates.asStateFlow()

    private val _aiOverview = MutableStateFlow<String?>(null)
    val aiOverview = _aiOverview.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading = _isAiLoading.asStateFlow()

    private val _aiHasError = MutableStateFlow(false)
    val aiHasError = _aiHasError.asStateFlow()

    private var activeSearchJobs = mutableMapOf<WebSearchCategory, Job>()
    private var activeAiJob: Job? = null

    fun setQuery(query: String) {
        _currentQuery.value = query
    }

    fun setCategory(category: WebSearchCategory) {
        if (_activeCategory.value == category) return
        _activeCategory.value = category
        // Fetch if not already loaded
        val state = _categoryStates.value[category]
        if (state == null || state.results.isEmpty()) {
            performSearch(category, page = 1)
        }
    }

    fun initiateSearch(query: String) {
        if (query.isBlank()) return
        
        // Cancel all previous in-flight requests
        cancelAllRequests()
        
        _currentQuery.value = query
        _activeCategory.value = WebSearchCategory.GENERAL
        
        // Clear previous state and caches
        _categoryStates.value = emptyMap()
        _aiOverview.value = null
        _aiHasError.value = false
        _isAiLoading.value = false

        // Update History
        viewModelScope.launch {
            historyDao.insertOrUpdateHistory(WebSearchHistoryEntity(query.trim(), System.currentTimeMillis()))
        }

        // Start search for General tab
        performSearch(WebSearchCategory.GENERAL, page = 1)
    }

    fun loadNextPage() {
        val category = _activeCategory.value
        val state = _categoryStates.value[category] ?: return
        if (state.isLoading || state.hasReachedEnd) return

        performSearch(category, page = state.currentPage + 1)
    }

    private fun performSearch(category: WebSearchCategory, page: Int) {
        // Cancel any existing job for this specific category
        activeSearchJobs[category]?.cancel()
        
        val currentState = _categoryStates.value[category] ?: CategoryState()
        _categoryStates.value = _categoryStates.value + (category to currentState.copy(isLoading = true, hasError = false))

        val job = viewModelScope.launch {
            val results = engine.searchWithFallback(getApplication<Application>(), _currentQuery.value, category.searxngCategory, page)
            
            val updatedState = _categoryStates.value[category]!!
            if (results.isEmpty()) {
                _categoryStates.value = _categoryStates.value + (category to updatedState.copy(
                    isLoading = false,
                    hasReachedEnd = true
                ))
            } else {
                val newResults = if (page == 1) results else updatedState.results + results
                _categoryStates.value = _categoryStates.value + (category to updatedState.copy(
                    isLoading = false,
                    results = newResults,
                    currentPage = page
                ))
                
                // If it's the General tab and page 1, trigger AI Overview
                if (category == WebSearchCategory.GENERAL && page == 1 && _aiOverview.value == null) {
                    generateAiOverview(results.take(10))
                }
            }
        }
        activeSearchJobs[category] = job
    }

    private fun generateAiOverview(topResults: List<WebSearchResult>) {
        if (topResults.isEmpty()) return
        
        activeAiJob?.cancel()
        _isAiLoading.value = true
        _aiHasError.value = false
        
        activeAiJob = viewModelScope.launch {
            val contextText = topResults.joinToString("\n\n") { "Source: ${it.url}\nTitle: ${it.title}\nContent: ${it.snippet}" }
            
            val languageInstruction = com.example.data.preferences.ResponseLanguageManager(getApplication<Application>()).getLanguageInstructionPrompt()

            val systemPrompt = """
                You are a direct web search summarizer. Your task is to generate a concise, factual summary strictly based on the provided search results.
                Do not include external facts. Use inline citations in the format [1], [2] corresponding to the source order.
                $languageInstruction
            """.trimIndent()

            val userPrompt = "Query: ${_currentQuery.value}\n\nSearch Results:\n$contextText"
            
            val overview = engine.generateWebSearchOverview(getApplication<Application>(), systemPrompt, userPrompt)
            
            if (overview.isNotBlank()) {
                _aiOverview.value = overview
                _isAiLoading.value = false
            } else {
                _aiHasError.value = true
                _isAiLoading.value = false
            }
        }
    }
    
    fun retryActiveCategory() {
        val category = _activeCategory.value
        val state = _categoryStates.value[category]
        val page = state?.currentPage ?: 1
        performSearch(category, page = if (state?.results.isNullOrEmpty()) 1 else page)
    }

    fun retryAiOverview() {
        val state = _categoryStates.value[WebSearchCategory.GENERAL]
        if (state != null && state.results.isNotEmpty()) {
            generateAiOverview(state.results.take(10))
        }
    }

    private fun cancelAllRequests() {
        activeSearchJobs.values.forEach { it.cancel() }
        activeSearchJobs.clear()
        activeAiJob?.cancel()
        activeAiJob = null
    }

    fun deleteHistoryItem(query: String) {
        viewModelScope.launch { historyDao.deleteHistoryByQuery(query) }
    }

    fun clearAllHistory() {
        viewModelScope.launch { historyDao.clearAllHistory() }
    }
}
