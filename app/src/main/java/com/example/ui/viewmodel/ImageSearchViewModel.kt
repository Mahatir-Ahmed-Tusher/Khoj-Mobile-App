package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ImageSearchPreferences
import com.example.data.model.ImageSearchHistoryEntity
import com.example.data.model.ImageSearchResult
import com.example.data.model.VisualMatch
import com.example.data.repository.ImageSearchRepository
import com.example.utils.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ImageSearchUiState {
    object Idle : ImageSearchUiState()
    data class Loading(val stepMessage: String) : ImageSearchUiState()
    data class Success(val result: ImageSearchResult) : ImageSearchUiState()
    data class Error(val message: String) : ImageSearchUiState()
}

class ImageSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ImageSearchRepository(application)
    private val preferences = ImageSearchPreferences(application)
    private val networkMonitor = NetworkMonitor(application)

    // Network connectivity state
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Engine Preference (DataStore)
    val selectedEngine: StateFlow<String> = preferences.selectedEngine
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ImageSearchPreferences.DEFAULT_ENGINE)

    // Input States
    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _inputUrl = MutableStateFlow("")
    val inputUrl: StateFlow<String> = _inputUrl.asStateFlow()

    // Search Execution UI State
    private val _uiState = MutableStateFlow<ImageSearchUiState>(ImageSearchUiState.Idle)
    val uiState: StateFlow<ImageSearchUiState> = _uiState.asStateFlow()

    // Fullscreen Pinch-Zoom Image Dialog State
    private val _zoomedImageUrl = MutableStateFlow<String?>(null)
    val zoomedImageUrl: StateFlow<String?> = _zoomedImageUrl.asStateFlow()

    // Temporary engine override for single search session
    private val _temporaryEngineOverride = MutableStateFlow<String?>(null)
    val temporaryEngineOverride: StateFlow<String?> = _temporaryEngineOverride.asStateFlow()

    // Room Favorites
    val favorites = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Trigger backend health warmup to wake up Render backend asynchronously
        viewModelScope.launch {
            repository.warmupBackend()
        }
    }

    fun setSelectedUri(uri: Uri?) {
        _selectedUri.value = uri
        if (uri != null) {
            _inputUrl.value = "" // clear text URL if image picked
        }
    }

    fun setInputUrl(url: String) {
        _inputUrl.value = url
        if (url.isNotBlank()) {
            _selectedUri.value = null // clear local Uri if text URL typed
        }
    }

    fun clearInputs() {
        _selectedUri.value = null
        _inputUrl.value = ""
        _uiState.value = ImageSearchUiState.Idle
    }

    fun setTemporaryEngine(engine: String?) {
        _temporaryEngineOverride.value = engine
    }

    fun updateEnginePreference(engine: String) {
        viewModelScope.launch {
            preferences.setSelectedEngine(engine)
        }
    }

    fun setZoomedImage(url: String?) {
        _zoomedImageUrl.value = url
    }

    fun checkAndPasteClipboardUrl() {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = clipboard?.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString()?.trim() ?: ""
            if (text.startsWith("http://") || text.startsWith("https://")) {
                setInputUrl(text)
            }
        }
    }

    fun performSearch() {
        val uri = _selectedUri.value
        val url = _inputUrl.value.trim()

        if (uri == null && url.isBlank()) {
            _uiState.value = ImageSearchUiState.Error("অনুগ্রহ করে একটি ছবি বা সঠিক লিংক নির্বাচন করুন।")
            return
        }

        if (!isOnline.value) {
            _uiState.value = ImageSearchUiState.Error("কোনো ইন্টারনেট সংযোগ নেই। দয়া করে আপনার নেটওয়ার্ক পরীক্ষা করুন।")
            return
        }

        val engineToUse = _temporaryEngineOverride.value ?: selectedEngine.value

        viewModelScope.launch {
            _uiState.value = ImageSearchUiState.Loading("✓ ছবি প্রস্তুত করা হচ্ছে…")
            val result = repository.executeImageSearch(
                imageUri = uri,
                imageUrl = url.ifBlank { null },
                selectedEngine = engineToUse,
                onProgressStep = { stepText ->
                    _uiState.value = ImageSearchUiState.Loading(stepText)
                }
            )

            if (result.errorMessage != null && result.matches.isEmpty()) {
                _uiState.value = ImageSearchUiState.Error(result.errorMessage)
            } else {
                _uiState.value = ImageSearchUiState.Success(result)
            }
        }
    }

    fun toggleFavorite(match: VisualMatch) {
        viewModelScope.launch {
            repository.toggleFavorite(match)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
