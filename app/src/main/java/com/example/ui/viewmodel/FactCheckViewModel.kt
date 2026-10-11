package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.FactCheckProgress
import com.example.data.api.KhojChatEngine
import com.example.data.api.KhojChatMessage
import com.example.data.api.KhojChatState
import com.example.data.api.KhojChatSource
import com.example.data.api.KhojChatSession
import com.example.data.model.FactCheckReport
import com.example.data.repository.FactCheckRepository

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import com.example.data.preferences.ResponseLanguage
import com.example.data.preferences.ResponseLanguageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.local.PendingSearchStore
import com.example.data.model.PendingSearchAction
import com.example.data.model.SearchMode
import com.example.data.repository.AuthRepository
import io.github.jan.supabase.auth.status.SessionStatus

data class WidgetSearchAction(
    val mode: SearchMode,
    val query: String? = null,
    val autoFocus: Boolean = true,
    val id: String = UUID.randomUUID().toString()
)

class FactCheckViewModel(application: Application) : AndroidViewModel(application) {
    val pendingSearchStore = PendingSearchStore(application)
    private val repository = FactCheckRepository(application)

    private val _incomingWidgetAction = MutableStateFlow<WidgetSearchAction?>(null)
    val incomingWidgetAction: StateFlow<WidgetSearchAction?> = _incomingWidgetAction.asStateFlow()
    private val prefs = application.getSharedPreferences("khoj_settings", android.content.Context.MODE_PRIVATE)

    private fun cleanKey(key: String): String {
        return key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    }

    private val geminiApiKey = cleanKey(BuildConfig.GEMINI_API_KEY)
    private val groqApiKey = cleanKey(BuildConfig.GROQ_API_KEY)

    // Khoj Chat Repository (Application-scoped Single Source of Truth)
    val khojChatRepository = com.example.data.repository.KhojChatRepository.getInstance(application)
    val chatMessages: StateFlow<List<KhojChatMessage>> = khojChatRepository.chatMessages
    val chatState: StateFlow<KhojChatState> = khojChatRepository.chatState
    val activeChatMode: StateFlow<String> = khojChatRepository.activeChatMode
    val chatSessions: StateFlow<List<KhojChatSession>> = khojChatRepository.chatSessions
    val activeSessionId: StateFlow<String?> = khojChatRepository.activeSessionId

    fun loadSessions() = khojChatRepository.loadSessions()
    fun loadChatHistory() = khojChatRepository.loadChatHistory()
    fun setChatMode(mode: String) = khojChatRepository.setChatMode(mode)
    fun createNewSession() = khojChatRepository.createNewSession()
    fun loadSession(sessionId: String) = khojChatRepository.loadSession(sessionId)
    fun deleteSession(sessionId: String) = khojChatRepository.deleteSession(sessionId)
    fun clearAllSessions() = khojChatRepository.clearAllSessions()
    fun sendChatMessage(text: String, imageUri: String? = null, onAutoSwitched: () -> Unit = {}) = khojChatRepository.sendChatMessage(text, imageUri, onAutoSwitched)
    fun retryLastChatMessage(onAutoSwitched: () -> Unit = {}) = khojChatRepository.retryLastChatMessage(onAutoSwitched)
    fun clearChatHistory() = khojChatRepository.clearChatHistory()


    // Current checking progress
    private val _currentCheckingState = MutableStateFlow<FactCheckProgress>(FactCheckProgress.Idle)
    val currentCheckingState: StateFlow<FactCheckProgress> = _currentCheckingState.asStateFlow()
    private var lastCheckedClaim: Pair<String, String>? = null
    private var factCheckJob: kotlinx.coroutines.Job? = null

    // Accumulated streaming markdown — grows chunk-by-chunk during live generation.
    // Reset on every new checkClaim call. Read by UnifiedFactCheckReportView for live rendering.
    private val _streamingMarkdown = MutableStateFlow("")
    val streamingMarkdown: StateFlow<String> = _streamingMarkdown.asStateFlow()

    private val _isCheckingImage = MutableStateFlow(false)
    val isCheckingImage: StateFlow<Boolean> = _isCheckingImage.asStateFlow()

    // App UI Language Preference ("bn", "en")
    private val _renderingLanguage = MutableStateFlow(
        prefs.getString("app_language_option", "bn") ?: "bn"
    )
    val renderingLanguage: StateFlow<String> = _renderingLanguage.asStateFlow()

    // AI Response Language Preference
    val responseLanguageManager = ResponseLanguageManager(application)
    val responseLanguage: StateFlow<ResponseLanguage> = responseLanguageManager.responseLanguage

    // Theme Preference ("system", "dark", "light")
    private val _themePreference = MutableStateFlow(
        prefs.getString("theme_preference", "system") ?: "system"
    )
    val themePreference: StateFlow<String> = _themePreference.asStateFlow()

    // Bengali Font Preference ("tiro", "anek")
    private val _bengaliFontPreference = MutableStateFlow(
        prefs.getString("bengali_font_preference", "tiro") ?: "tiro"
    )
    val bengaliFontPreference: StateFlow<String> = _bengaliFontPreference.asStateFlow()

    // History search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Remote cached list of reports (fetched from Supabase)
    private val _remoteHistoryReports = MutableStateFlow<List<FactCheckReport>>(emptyList())
    val remoteHistoryReports = _remoteHistoryReports.asStateFlow()

    // Pending actions for deferred factcheck after login
    private val _pendingClaim = MutableStateFlow<String?>(null)
    val pendingClaim = _pendingClaim.asStateFlow()

    private val _pendingImageUri = MutableStateFlow<android.net.Uri?>(null)
    val pendingImageUri = _pendingImageUri.asStateFlow()

    private val _pendingSession = MutableStateFlow<com.example.data.model.VerificationSession?>(null)
    val pendingSession = _pendingSession.asStateFlow()

    // CMS Fact Checks (Backed by Room Flow as single source of truth for full offline support)
    val cmsFactChecks: StateFlow<List<com.example.data.model.CmsArticle>> = repository.getRecentCmsFactChecksFlow(10)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allCmsFactChecks: StateFlow<List<com.example.data.model.CmsArticle>> = repository.getAllCmsFactChecksFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isCmsLoading = MutableStateFlow(false)
    val isCmsLoading: StateFlow<Boolean> = _isCmsLoading.asStateFlow()

    private val _cmsError = MutableStateFlow<String?>(null)
    val cmsError: StateFlow<String?> = _cmsError.asStateFlow()

    // CMS Blog Articles
    val blogArticles: StateFlow<List<com.example.data.model.CmsArticle>> = repository.getRecentCmsBlogArticlesFlow(4)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allBlogArticles: StateFlow<List<com.example.data.model.CmsArticle>> = repository.getAllCmsBlogArticlesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isBlogLoading = MutableStateFlow(false)
    val isBlogLoading: StateFlow<Boolean> = _isBlogLoading.asStateFlow()

    private val _blogError = MutableStateFlow<String?>(null)
    val blogError: StateFlow<String?> = _blogError.asStateFlow()

    private var lastCmsRefreshTime: Long = 0L
    private var lastBlogRefreshTime: Long = 0L

    fun loadRecentCmsFactChecks(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (cmsFactChecks.value.isEmpty() || forceRefresh) {
                _isCmsLoading.value = true
            }
            _cmsError.value = null
            try {
                repository.syncFactCheckArticles(forceFullSync = forceRefresh)
            } catch (e: Exception) {
                _cmsError.value = e.message
            } finally {
                _isCmsLoading.value = false
            }
        }
    }

    fun refreshRecentCmsFactChecks() {
        val now = System.currentTimeMillis()
        if (now - lastCmsRefreshTime < com.example.data.local.CachePolicy.DEBOUNCE_WINDOW_MS) {
            return
        }
        lastCmsRefreshTime = now
        loadRecentCmsFactChecks(forceRefresh = true)
    }

    fun loadAllCmsFactChecks(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (allCmsFactChecks.value.isEmpty() || forceRefresh) {
                _isCmsLoading.value = true
            }
            _cmsError.value = null
            try {
                repository.syncFactCheckArticles(forceFullSync = forceRefresh)
            } catch (e: Exception) {
                _cmsError.value = e.message
            } finally {
                _isCmsLoading.value = false
            }
        }
    }

    fun refreshAllCmsFactChecks() {
        val now = System.currentTimeMillis()
        if (now - lastCmsRefreshTime < com.example.data.local.CachePolicy.DEBOUNCE_WINDOW_MS) {
            return
        }
        lastCmsRefreshTime = now
        loadAllCmsFactChecks(forceRefresh = true)
    }

    fun loadRecentCmsBlogArticles(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                _isBlogLoading.value = true
            }
            _blogError.value = null
            try {
                repository.syncBlogArticles(forceFullSync = forceRefresh)
            } catch (e: Exception) {
                _blogError.value = e.message
            } finally {
                _isBlogLoading.value = false
            }
        }
    }

    fun refreshRecentCmsBlogArticles() {
        val now = System.currentTimeMillis()
        if (now - lastBlogRefreshTime < com.example.data.local.CachePolicy.DEBOUNCE_WINDOW_MS) {
            return
        }
        lastBlogRefreshTime = now
        loadRecentCmsBlogArticles(forceRefresh = true)
    }

    fun loadAllCmsBlogArticles(forceRefresh: Boolean = false) {
        // Since both all and recent blogs pull from the same table and sync the same way,
        // we just call the shared sync method.
        loadRecentCmsBlogArticles(forceRefresh)
    }

    fun refreshAllCmsBlogArticles() {
        refreshRecentCmsBlogArticles()
    }

    fun handleIncomingWidgetIntent(mode: SearchMode, query: String?, autoFocus: Boolean = true) {
        _incomingWidgetAction.value = WidgetSearchAction(
            mode = mode,
            query = query?.take(2000),
            autoFocus = autoFocus
        )
    }

    fun consumeIncomingWidgetAction() {
        _incomingWidgetAction.value = null
    }

    fun savePendingSearch(query: String?, mode: SearchMode) {
        viewModelScope.launch {
            pendingSearchStore.savePendingSearch(query, mode)
        }
        _pendingClaim.value = query
    }

    suspend fun getValidPendingSearch(): PendingSearchAction? {
        return pendingSearchStore.getValidPendingSearch()
    }

    fun setPendingClaim(claim: String?) {
        _pendingClaim.value = claim
        savePendingSearch(claim, SearchMode.FACT_CHECK)
    }

    fun savePendingImageUri(uri: android.net.Uri) {
        _pendingImageUri.value = uri
    }

    fun savePendingSession(session: com.example.data.model.VerificationSession) {
        _pendingSession.value = session
    }

    fun setPendingImageUri(uri: android.net.Uri?) {
        _pendingImageUri.value = uri
        if (uri != null) {
            savePendingSearch(null, SearchMode.IMAGE_SEARCH)
        }
    }

    fun clearPendingActions() {
        _pendingClaim.value = null
        _pendingImageUri.value = null
        _pendingSession.value = null
        viewModelScope.launch {
            pendingSearchStore.clearPendingSearch()
        }
    }

    init {
        loadSessions()
        loadChatHistory()
        loadRecentCmsFactChecks()
        loadRecentCmsBlogArticles()
        
        viewModelScope.launch {
            try {
                AuthRepository.sessionFlow.collect { status ->
                    if (status is SessionStatus.Authenticated) {
                        val userId = AuthRepository.currentUserId
                        if (userId != null) {
                            loadRemoteHistory(userId)
                        }
                    } else {
                        _remoteHistoryReports.value = emptyList()
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("FactCheckViewModel", "Session status collection failed: ${e.message}")
            }
        }
    }

    // Dynamically switches between local Room database and synced Supabase remote database
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val historyReports: StateFlow<List<FactCheckReport>> = combine(
        _searchQuery,
        AuthRepository.sessionFlow,
        repository.allReports,
        _remoteHistoryReports
    ) { query, sessionStatus, localList, remoteList ->
        val list = if (sessionStatus is SessionStatus.Authenticated) {
            // Merge local and remote reports to ensure instant feedback while syncing
            (localList + remoteList).distinctBy { it.id }.sortedByDescending { it.timestamp }
        } else {
            localList
        }
        
        if (query.isBlank()) {
            list
        } else {
            list.filter { 
                it.claimText.contains(query, ignoreCase = true) || 
                it.verdict.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun loadRemoteHistory(userId: String) {
        viewModelScope.launch {
            val remoteList = repository.getRemoteHistoryReports(userId)
            _remoteHistoryReports.value = remoteList
        }
    }

    fun updateReportPrivacy(id: String, isPublic: Boolean, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateReportPrivacy(id, isPublic)
            AuthRepository.currentUserId?.let { userId ->
                loadRemoteHistory(userId)
            }
            onComplete()
        }
    }

    fun checkClaim(claim: String, isFromImage: Boolean = false) {
        if (claim.isBlank()) return
        lastCheckedClaim = claim to ""
        _isCheckingImage.value = false

        // Streaming Safety: Cancel any in-flight job to prevent overlapping streams
        factCheckJob?.cancel()
        _streamingMarkdown.value = ""

        factCheckJob = viewModelScope.launch {
            val trimmedClaim = claim.trim()
            val isExplicitUrl = trimmedClaim.startsWith("http://", ignoreCase = true) || trimmedClaim.startsWith("https://", ignoreCase = true)
            val isWebUrlPattern = !trimmedClaim.contains(" ") && android.util.Patterns.WEB_URL.matcher(trimmedClaim).matches()
            val isUrl = isExplicitUrl || isWebUrlPattern
            val normalizedUrl = if (isUrl && !isExplicitUrl) {
                "https://$trimmedClaim"
            } else {
                trimmedClaim
            }

            val currentResponseLanguage = responseLanguageManager.responseLanguage.value

            val collectProgress: (FactCheckProgress) -> Unit = { progress ->
                when (progress) {
                    is FactCheckProgress.StreamingChunk -> {
                        // Accumulate streamed text for live rendering in UnifiedFactCheckReportView
                        _streamingMarkdown.value += progress.chunk
                        // Keep the state as GeneratingVerdict so the overlay animation remains correct
                        _currentCheckingState.value = FactCheckProgress.GeneratingVerdict
                    }
                    is FactCheckProgress.Success -> {
                        _currentCheckingState.value = progress
                        // Replace streaming buffer with the fully-saved report markdown for consistency
                        _streamingMarkdown.value = progress.report.getEffectiveMarkdown()
                        AuthRepository.currentUserId?.let { loadRemoteHistory(it) }
                    }
                    else -> _currentCheckingState.value = progress
                }
            }

            if (isUrl) {
                try {
                    _currentCheckingState.value = FactCheckProgress.ClaimNormalization
                    val urlAnalyzer = com.example.data.api.UrlAnalyzer()
                    val extractedClaim = urlAnalyzer.analyzeUrlAndExtractClaim(normalizedUrl)
                    repository.performFactCheck(extractedClaim, currentResponseLanguage).collect(collectProgress)
                } catch (e: com.example.data.api.LoginWallException) {
                    _currentCheckingState.value = FactCheckProgress.Error(e.message ?: "Login wall detected. Please copy-paste the text manually.")
                } catch (e: Exception) {
                    _currentCheckingState.value = FactCheckProgress.Error("Failed to extract claim from URL: ${e.message}")
                }
            } else {
                repository.performFactCheck(claim, currentResponseLanguage, isFromImage).collect(collectProgress)
            }
        }
    }

    fun retryLastCheck() {
        lastCheckedClaim?.let { (claim, _) ->
            checkClaim(claim)
        }
    }

    fun extractClaimFromImage(uri: Uri, additionalContext: String? = null) {
        viewModelScope.launch {
            _isCheckingImage.value = true
            _currentCheckingState.value = FactCheckProgress.ExtractingImageText
            
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val contentResolver = getApplication<Application>().contentResolver
                    var decodedBitmap: android.graphics.Bitmap? = null
                    
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                            decodedBitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                                val targetSize = 1600
                                val maxDim = Math.max(info.size.width, info.size.height)
                                if (maxDim > targetSize) {
                                    val scale = targetSize.toFloat() / maxDim
                                    val dstWidth = (info.size.width * scale).toInt()
                                    val dstHeight = (info.size.height * scale).toInt()
                                    decoder.setTargetSize(dstWidth, dstHeight)
                                }
                                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                            }
                        } else {
                            val options = android.graphics.BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            contentResolver.openInputStream(uri)?.use { 
                                android.graphics.BitmapFactory.decodeStream(it, null, options) 
                            }
                            
                            val height = options.outHeight
                            val width = options.outWidth
                            var inSampleSize = 1
                            if (height > 1600 || width > 1600) {
                                val halfHeight = height / 2
                                val halfWidth = width / 2
                                while (halfHeight / inSampleSize >= 1600 && halfWidth / inSampleSize >= 1600) {
                                    inSampleSize *= 2
                                }
                            }
                            
                            options.inSampleSize = inSampleSize
                            options.inJustDecodeBounds = false
                            
                            contentResolver.openInputStream(uri)?.use { 
                                decodedBitmap = android.graphics.BitmapFactory.decodeStream(it, null, options) 
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e("FactCheckViewModel", "Failed to decode image safely", t)
                    }
                    decodedBitmap
                }

                if (bitmap == null) {
                    _currentCheckingState.value = FactCheckProgress.Error(
                        if (responseLanguageManager.responseLanguage.value == ResponseLanguage.BANGLA || (responseLanguageManager.responseLanguage.value == ResponseLanguage.AUTO && _renderingLanguage.value == "bn")) "দুঃখিত, ছবি থেকে লেখা এক্সট্রাক্ট করতে সমস্যা হয়েছে। দয়া করে আবার চেষ্টা করুন।\nError: Bitmap decoding failed" else "Sorry, there was a problem extracting text from the image. Please try again.\nError: Bitmap decoding failed"
                    )
                    return@launch
                }
                
                val extractedText = try {
                    com.example.data.api.ImageOcrHelper.extractTextFromBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e("FactCheckViewModel", "All OCR tiers failed", e)
                    ""
                }
                
                if (extractedText.isNotBlank() || !additionalContext.isNullOrBlank()) {
                    val combinedText = buildString {
                        if (!additionalContext.isNullOrBlank()) {
                            append(additionalContext.trim())
                            if (extractedText.isNotBlank()) {
                                append("\n\n[ছবি থেকে প্রাপ্ত তথ্য/লেখা]:\n")
                                append(extractedText.trim())
                            }
                        } else {
                            append(extractedText.trim())
                        }
                    }
                    checkClaim(combinedText, isFromImage = true)
                } else {
                    _currentCheckingState.value = FactCheckProgress.Error(
                         if (responseLanguageManager.responseLanguage.value == ResponseLanguage.BANGLA || (responseLanguageManager.responseLanguage.value == ResponseLanguage.AUTO && _renderingLanguage.value == "bn")) "ছবিতে কোনো লেখা পাওয়া যায়নি এবং কোনো বিবরণ দেওয়া হয়নি।" else "No text found in the image and no context provided."
                    )
                }

            } catch (e: Throwable) {
                Log.e("FactCheckViewModel", "Error extracting text", e)
                _currentCheckingState.value = FactCheckProgress.Error(
                    if (responseLanguageManager.responseLanguage.value == ResponseLanguage.BANGLA || (responseLanguageManager.responseLanguage.value == ResponseLanguage.AUTO && _renderingLanguage.value == "bn")) "দুঃখিত, ছবি থেকে লেখা এক্সট্রাক্ট করতে সমস্যা হয়েছে। দয়া করে আবার চেষ্টা করুন।\nError: ${e.message}" else "Sorry, there was a problem extracting text from the image. Please try again.\nError: ${e.message}"
                )
            }
        }
    }

    fun deleteReport(id: String) {
        viewModelScope.launch {
            repository.deleteReport(id)
            AuthRepository.currentUserId?.let { loadRemoteHistory(it) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _remoteHistoryReports.value = emptyList()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleRenderingLanguage() {
        val newLang = if (_renderingLanguage.value == "bn") "en" else "bn"
        setRenderingLanguage(newLang)
    }

    fun setRenderingLanguage(lang: String) {
        if (lang == "bn" || lang == "en") {
            _renderingLanguage.value = lang
            prefs.edit().putString("app_language_option", lang).apply()
        }
    }

    fun setResponseLanguage(lang: ResponseLanguage) {
        responseLanguageManager.setLanguage(lang)
    }

    fun setThemePreference(pref: String) {
        if (pref == "system" || pref == "dark" || pref == "light") {
            _themePreference.value = pref
            prefs.edit().putString("theme_preference", pref).apply()
        }
    }

    fun setBengaliFontPreference(pref: String) {
        if (pref == "tiro" || pref == "anek") {
            _bengaliFontPreference.value = pref
            prefs.edit().putString("bengali_font_preference", pref).apply()
        }
    }

    fun resetCheckingState() {
        _currentCheckingState.value = FactCheckProgress.Idle
    }

    fun isCustomPipelineActive(): Boolean {
        return repository.isCustomPipelineConfigured()
    }
}
