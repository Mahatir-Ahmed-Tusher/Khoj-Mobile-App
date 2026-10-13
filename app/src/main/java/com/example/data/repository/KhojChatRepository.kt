package com.example.data.repository

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.api.DeepSearchEngine
import com.example.data.api.DeepSearchProgress
import com.example.data.api.BanglaAiTextDetectorApi
import com.example.data.api.AiImageDetectionEngine
import com.example.data.api.KhojChatEngine
import com.example.data.api.KhojChatMessage
import com.example.data.api.KhojChatSession
import com.example.data.api.KhojChatSource
import com.example.data.api.KhojChatState
import com.example.data.model.EngineVerdict
import com.example.data.model.ImageVerdict
import com.example.data.preferences.ResponseLanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Application-scoped singleton repository for Khoj Chat.
 *
 * Serves as the Single Source of Truth for both:
 * 1. The in-app chat drawer (FactCheckViewModel / KhojChatDrawer)
 * 2. The floating overlay chat drawer (OverlayModeService / OverlayKhojChatDrawer)
 *
 * Guarantees real-time bi-directional synchronization, shared session persistence,
 * and survives across drawer dismissals and Activity lifecycle changes.
 */
class KhojChatRepository private constructor(private val application: Application) {

    private val tag = "KhojChatRepository"
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val chatEngine = KhojChatEngine(application)
    private val deepSearchEngine = DeepSearchEngine(application)
    private val aiImageDetectionEngine = AiImageDetectionEngine(application)
    private val responseLanguageManager = ResponseLanguageManager(application)

    private val _chatMessages = MutableStateFlow<List<KhojChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<KhojChatMessage>> = _chatMessages.asStateFlow()

    private val _chatState = MutableStateFlow<KhojChatState>(KhojChatState.Idle)
    val chatState: StateFlow<KhojChatState> = _chatState.asStateFlow()

    private val _activeChatMode = MutableStateFlow("general") // "general", "citizen-service", "fact-check"
    val activeChatMode: StateFlow<String> = _activeChatMode.asStateFlow()

    private val _chatSessions = MutableStateFlow<List<KhojChatSession>>(emptyList())
    val chatSessions: StateFlow<List<KhojChatSession>> = _chatSessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    // Track IDs of messages that have already completed typewriter rendering
    private val _animatedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val animatedMessageIds: StateFlow<Set<String>> = _animatedMessageIds.asStateFlow()

    init {
        loadSessions()
        val curActiveId = chatEngine.getActiveSessionId()
        _activeSessionId.value = curActiveId
        val initialMessages = if (curActiveId != null) {
            chatEngine.loadHistoryForSession(curActiveId)
        } else {
            chatEngine.loadHistory()
        }
        _chatMessages.value = initialMessages
        // Historical messages are already fully generated and should not re-animate
        _animatedMessageIds.value = initialMessages.map { it.id }.toSet()
    }

    fun markMessageAnimated(messageId: String) {
        _animatedMessageIds.value = _animatedMessageIds.value + messageId
    }

    fun isMessageAnimated(messageId: String): Boolean {
        return messageId in _animatedMessageIds.value
    }

    fun loadSessions() {
        _chatSessions.value = chatEngine.loadSessions()
        _activeSessionId.value = chatEngine.getActiveSessionId()
    }

    fun loadChatHistory() {
        val activeId = _activeSessionId.value ?: chatEngine.getActiveSessionId()
        val history = if (activeId != null) {
            chatEngine.loadHistoryForSession(activeId)
        } else {
            chatEngine.loadHistory()
        }
        _chatMessages.value = history
        _animatedMessageIds.value = _animatedMessageIds.value + history.map { it.id }
    }

    fun setChatMode(mode: String) {
        _activeChatMode.value = mode
    }

    fun createNewSession() {
        chatEngine.setActiveSessionId(null)
        _activeSessionId.value = null
        _chatMessages.value = emptyList()
        _animatedMessageIds.value = emptySet()
        _chatState.value = KhojChatState.Idle
    }

    fun loadSession(sessionId: String) {
        chatEngine.setActiveSessionId(sessionId)
        _activeSessionId.value = sessionId
        val sessionMessages = chatEngine.loadHistoryForSession(sessionId)
        _chatMessages.value = sessionMessages
        _animatedMessageIds.value = _animatedMessageIds.value + sessionMessages.map { it.id }
        _chatState.value = KhojChatState.Idle
    }

    fun renameSession(sessionId: String, newTitle: String) {
        chatEngine.renameSession(sessionId, newTitle)
        loadSessions()
    }

    fun togglePinSession(sessionId: String) {
        chatEngine.togglePinSession(sessionId)
        loadSessions()
    }

    fun deleteSession(sessionId: String) {
        chatEngine.deleteSession(sessionId)
        loadSessions()
        val currentActiveId = chatEngine.getActiveSessionId()
        if (currentActiveId != null) {
            val history = chatEngine.loadHistoryForSession(currentActiveId)
            _chatMessages.value = history
            _animatedMessageIds.value = history.map { it.id }.toSet()
        } else {
            _chatMessages.value = emptyList()
            _animatedMessageIds.value = emptySet()
        }
        _chatState.value = KhojChatState.Idle
    }

    fun clearAllSessions() {
        val sessionsList = chatEngine.loadSessions()
        sessionsList.forEach { sess ->
            chatEngine.deleteSession(sess.id)
        }
        chatEngine.setActiveSessionId(null)
        _activeSessionId.value = null
        _chatSessions.value = emptyList()
        _chatMessages.value = emptyList()
        _animatedMessageIds.value = emptySet()
        _chatState.value = KhojChatState.Idle
    }

    fun sendChatMessage(
        text: String,
        imageUri: String? = null,
        onAutoSwitched: () -> Unit = {},
        promptForEngine: String? = null,
        isDeepSearch: Boolean = false,
        onDeepSearchCompleted: () -> Unit = {},
        isAiTextDetection: Boolean = false,
        onAiTextDetectionCompleted: () -> Unit = {},
        isAiImageDetection: Boolean = false,
        onAiImageDetectionCompleted: () -> Unit = {},
        isWebSearchEnabled: Boolean = false
    ) {
        val effectiveText = if (text.isBlank() && isAiImageDetection && !imageUri.isNullOrBlank()) {
            "ছবিটির কৃত্রিমতা যাচাই (AI Image Detection)"
        } else {
            text
        }
        if (effectiveText.isBlank()) return
        if (isDeepSearch || isAiTextDetection || isAiImageDetection) {
            _activeChatMode.value = "general"
        }
        val currentMode = _activeChatMode.value

        // Ensure we have an active session
        var activeId = chatEngine.getActiveSessionId()
        if (activeId == null) {
            val newId = UUID.randomUUID().toString()
            val truncatedTitle = if (effectiveText.length > 45) effectiveText.take(42) + "..." else effectiveText
            val newSession = KhojChatSession(id = newId, title = truncatedTitle, timestamp = System.currentTimeMillis())

            val currentSessions = chatEngine.loadSessions().toMutableList()
            currentSessions.add(0, newSession)
            chatEngine.saveSessions(currentSessions)
            chatEngine.setActiveSessionId(newId)
            activeId = newId

            _chatSessions.value = currentSessions
            _activeSessionId.value = newId
        }

        // 1. Add User Message
        val userMsg = KhojChatMessage(
            text = effectiveText,
            isUser = true,
            mode = currentMode,
            imageUri = imageUri,
            promptForEngine = promptForEngine
        )
        val updatedHistory = _chatMessages.value + userMsg
        _chatMessages.value = updatedHistory
        chatEngine.saveHistoryForSession(activeId, updatedHistory)

        val queryForEngine = promptForEngine ?: effectiveText

        if (isAiTextDetection) {
            _chatState.value = KhojChatState.AiTextDetecting("🔍 বাংলা AI টেক্সট বিশ্লেষণ করা হচ্ছে…")
            repositoryScope.launch {
                try {
                    val result = BanglaAiTextDetectorApi.detect(text = text, operatingMode = "balanced")

                    val aiPercent = (result.aiProbability * 100).toInt()
                    val humanPercent = (result.humanProbability * 100).toInt()
                    val isAi = result.verdict.equals("AI-generated", ignoreCase = true)

                    val verdictTitle = if (isAi) {
                        "🤖 **কৃত্রিম বুদ্ধিমত্তা দ্বারা তৈরি (AI-generated)**"
                    } else {
                        "✍️ **মানুষের দ্বারা লিখিত (Human-written)**"
                    }

                    val confidenceFormatted = when (result.confidenceLevel.lowercase()) {
                        "very_high" -> "খুব উচ্চ (Very High)"
                        "high" -> "উচ্চ (High)"
                        "medium" -> "মাঝারি (Medium)"
                        "low" -> "কম (Low)"
                        else -> result.confidenceLevel
                    }

                    val sb = StringBuilder()
                    sb.append("### 🔍 বাংলা AI টেক্সট বিশ্লেষণ ফলাফল\n\n")
                    sb.append("* **চূড়ান্ত সিদ্ধান্ত (Verdict):** $verdictTitle\n")
                    sb.append("* **সম্ভাব্যতা বিশ্লেষণ:**\n")
                    sb.append("  • **AI সম্ভাবনা (AI Probability):** `$aiPercent%`\n")
                    sb.append("  • **মানব সম্ভাবনা (Human Probability):** `$humanPercent%`\n")
                    sb.append("* **নির্ভুলতার মাত্রা (Confidence Level):** $confidenceFormatted\n")
                    sb.append("* **পরিসংখ্যান:** মোট শব্দ: `${result.wordCount}`, মোট বর্ণ: `${result.characterCount}`\n")

                    if (!result.independentWindows.isNullOrEmpty() && result.independentWindows.size > 1) {
                        sb.append("\n---\n**অনুচ্ছেদভিত্তিক বিশ্লেষণ (${result.independentWindows.size}টি অংশ):**\n")
                        result.independentWindows.take(3).forEach { window ->
                            val winAi = (window.aiProbability * 100).toInt()
                            sb.append("* অংশ #${window.windowId}: `${window.verdict}` (AI: $winAi%) – *\"${window.textExcerpt.take(60)}...\"*\n")
                        }
                    }

                    val aiMsg = KhojChatMessage(
                        text = sb.toString(),
                        isUser = false,
                        mode = "general"
                    )
                    val finalHistory = _chatMessages.value + aiMsg
                    _chatMessages.value = finalHistory
                    chatEngine.saveHistoryForSession(activeId, finalHistory)
                    _chatState.value = KhojChatState.Idle
                    onAiTextDetectionCompleted()
                } catch (e: Exception) {
                    val errorText = "❌ বাংলা AI টেক্সট বিশ্লেষণে সমস্যা হয়েছে: ${e.localizedMessage ?: "সার্ভার সংযোগে ত্রুটি"}"
                    val aiMsg = KhojChatMessage(
                        text = errorText,
                        isUser = false,
                        mode = "general",
                        isError = true
                    )
                    val finalHistory = _chatMessages.value + aiMsg
                    _chatMessages.value = finalHistory
                    chatEngine.saveHistoryForSession(activeId, finalHistory)
                    _chatState.value = KhojChatState.Idle
                    onAiTextDetectionCompleted()
                }
            }
            return
        }

        if (isAiImageDetection) {
            if (imageUri.isNullOrBlank()) {
                val errorMsg = KhojChatMessage(
                    text = "❌ ছবি নির্বাচন করা হয়নি। অনুগ্রহ করে একটি ছবি সংযুক্ত করে পুনরায় চেষ্টা করুন।",
                    isUser = false,
                    mode = "general",
                    isError = true
                )
                val finalHistory = _chatMessages.value + errorMsg
                _chatMessages.value = finalHistory
                chatEngine.saveHistoryForSession(activeId, finalHistory)
                _chatState.value = KhojChatState.Idle
                onAiImageDetectionCompleted()
                return
            }

            _chatState.value = KhojChatState.AiImageDetecting("🔍 ছবির কৃত্রিমতা যাচাই করা হচ্ছে…")
            repositoryScope.launch {
                try {
                    val result = aiImageDetectionEngine.detect(Uri.parse(imageUri))

                    val verdictTitle = when (result.verdict) {
                        ImageVerdict.AI -> "🤖 **কৃত্রিম বুদ্ধিমত্তা দ্বারা তৈরি (AI-Generated Image)**"
                        ImageVerdict.HUMAN -> "📷 **আসল বা মানুষের তোলা ছবি (Real / Human-Captured Image)**"
                        ImageVerdict.UNCERTAIN -> "⚖️ **অনির্ধারিত / মিশ্র সংকেত (Uncertain / Mixed Signals)**"
                    }

                    val firstEngine = result.engines.firstOrNull()
                    val isEngineError = firstEngine?.verdict == EngineVerdict.ERROR

                    val aiMsg = if (isEngineError) {
                        KhojChatMessage(
                            text = "❌ ছবি বিশ্লেষণে সমস্যা হয়েছে: ${firstEngine?.error ?: "সার্ভার সংযোগে ত্রুটি"}",
                            isUser = false,
                            mode = "general",
                            isError = true
                        )
                    } else {
                        val sb = StringBuilder()
                        sb.append("### 🖼️ ছবি কৃত্রিম বুদ্ধিমত্তা (AI) বিশ্লেষণ ফলাফল\n\n")
                        sb.append("* **চূড়ান্ত সিদ্ধান্ত (Verdict):** $verdictTitle\n")
                        sb.append("* **সম্ভাব্যতা বিশ্লেষণ:**\n")
                        sb.append("  • **AI সম্ভাবনা (AI Probability):** `${result.aiScore}%`\n")
                        sb.append("  • **আসল / মানব সম্ভাবনা (Human/Real Probability):** `${result.humanScore}%`\n")
                        sb.append("* **নির্ভুলতার মাত্রা (Confidence):** `${result.confidence}%`\n")
                        sb.append("* **বিশ্লেষণ সময়:** `${result.analysisTimeMs} ms`\n")
                        KhojChatMessage(
                            text = sb.toString(),
                            isUser = false,
                            mode = "general"
                        )
                    }

                    val finalHistory = _chatMessages.value + aiMsg
                    _chatMessages.value = finalHistory
                    chatEngine.saveHistoryForSession(activeId, finalHistory)
                    _chatState.value = KhojChatState.Idle
                    onAiImageDetectionCompleted()
                } catch (e: Exception) {
                    val errorText = "❌ ছবি বিশ্লেষণে সমস্যা হয়েছে: ${e.localizedMessage ?: "সার্ভার সংযোগে ত্রুটি"}"
                    val aiMsg = KhojChatMessage(
                        text = errorText,
                        isUser = false,
                        mode = "general",
                        isError = true
                    )
                    val finalHistory = _chatMessages.value + aiMsg
                    _chatMessages.value = finalHistory
                    chatEngine.saveHistoryForSession(activeId, finalHistory)
                    _chatState.value = KhojChatState.Idle
                    onAiImageDetectionCompleted()
                }
            }
            return
        }

        if (isDeepSearch) {
            _chatState.value = KhojChatState.DeepSearching("🧠 Planning search queries…")
            repositoryScope.launch {
                try {
                    deepSearchEngine.executeDeepSearch(queryForEngine).collect { progress ->
                        when (progress) {
                            is DeepSearchProgress.AnalyzingIntent -> {
                                _chatState.value = KhojChatState.DeepSearching("🧠 Analyzing query intent…")
                            }
                            is DeepSearchProgress.QueryExpansion -> {
                                _chatState.value = KhojChatState.DeepSearching("🧠 Planning search queries…")
                            }
                            is DeepSearchProgress.Searching -> {
                                _chatState.value = KhojChatState.DeepSearching("🌐 Searching live web sources…")
                            }
                            is DeepSearchProgress.RankingSources -> {
                                _chatState.value = KhojChatState.DeepSearching("🔗 Verifying & ranking sources…")
                            }
                            is DeepSearchProgress.GeneratingReport -> {
                                _chatState.value = KhojChatState.DeepSearching("✍️ Generating deep research report…")
                            }
                            is DeepSearchProgress.Success -> {
                                val report = progress.report
                                val mappedSources = report.sources.map {
                                    KhojChatSource(
                                        title = it.title,
                                        url = it.url,
                                        snippet = it.snippet
                                    )
                                }
                                val aiMsg = KhojChatMessage(
                                    text = report.reportContent,
                                    isUser = false,
                                    mode = "general",
                                    sources = mappedSources
                                )
                                val finalHistory = _chatMessages.value + aiMsg
                                _chatMessages.value = finalHistory
                                chatEngine.saveHistoryForSession(activeId, finalHistory)
                                _chatState.value = KhojChatState.Idle
                                onDeepSearchCompleted()
                            }
                            is DeepSearchProgress.Error -> {
                                val aiMsg = KhojChatMessage(
                                    text = progress.message,
                                    isUser = false,
                                    mode = "general",
                                    isError = true
                                )
                                val finalHistory = _chatMessages.value + aiMsg
                                _chatMessages.value = finalHistory
                                chatEngine.saveHistoryForSession(activeId, finalHistory)
                                _chatState.value = KhojChatState.Idle
                                onDeepSearchCompleted()
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    val aiMsg = KhojChatMessage(
                        text = e.localizedMessage ?: "Deep search failed",
                        isUser = false,
                        mode = "general",
                        isError = true
                    )
                    val finalHistory = _chatMessages.value + aiMsg
                    _chatMessages.value = finalHistory
                    chatEngine.saveHistoryForSession(activeId, finalHistory)
                    _chatState.value = KhojChatState.Idle
                    onDeepSearchCompleted()
                }
            }
            return
        }

        repositoryScope.launch {
            val currentResponseLanguage = responseLanguageManager.responseLanguage.value
            chatEngine.sendChatMessage(queryForEngine, currentMode, updatedHistory, currentResponseLanguage, isWebSearchEnabled).collect { state ->
                _chatState.value = state
                when (state) {
                    is KhojChatState.AutoDetecting -> {
                        _activeChatMode.value = "fact-check"
                        onAutoSwitched()
                    }
                    is KhojChatState.Success -> {
                        val finalMode = if (state.autoSwitchedToFactCheck) "fact-check" else currentMode
                        val aiMsg = KhojChatMessage(
                            text = state.responseText,
                            isUser = false,
                            mode = finalMode,
                            verdict = state.verdict,
                            sources = state.sources,
                            autoSwitched = state.autoSwitchedToFactCheck
                        )
                        val finalHistory = _chatMessages.value + aiMsg
                        _chatMessages.value = finalHistory
                        chatEngine.saveHistoryForSession(activeId, finalHistory)
                        _chatState.value = KhojChatState.Idle

                        // Automatically revert active mode to "general" if current query ran in fact-check mode
                        if (currentMode == "fact-check" || state.autoSwitchedToFactCheck) {
                            _activeChatMode.value = "general"
                        }
                    }
                    is KhojChatState.Error -> {
                        val aiMsg = KhojChatMessage(
                            text = state.message,
                            isUser = false,
                            mode = currentMode,
                            isError = true
                        )
                        val finalHistory = _chatMessages.value + aiMsg
                        _chatMessages.value = finalHistory
                        chatEngine.saveHistoryForSession(activeId, finalHistory)
                        _chatState.value = KhojChatState.Idle
                    }
                    else -> {}
                }
            }
        }
    }

    fun retryLastChatMessage(onAutoSwitched: () -> Unit = {}) {
        val lastUserMsg = _chatMessages.value.lastOrNull { it.isUser }
        if (lastUserMsg != null && lastUserMsg.text.isNotBlank()) {
            _chatMessages.value = _chatMessages.value.filterNot { it.isError }
            sendChatMessage(
                lastUserMsg.text,
                imageUri = lastUserMsg.imageUri,
                onAutoSwitched = onAutoSwitched,
                promptForEngine = lastUserMsg.promptForEngine
            )
        }
    }

    fun clearChatHistory() {
        chatEngine.clearHistory()
        loadSessions()
        val activeId = chatEngine.getActiveSessionId()
        if (activeId != null) {
            _chatMessages.value = chatEngine.loadHistoryForSession(activeId)
        } else {
            _chatMessages.value = emptyList()
        }
        _chatState.value = KhojChatState.Idle
    }

    fun prepareEditMessage(messageId: String) {
        val current = _chatMessages.value
        val targetIndex = current.indexOfFirst { it.id == messageId }
        if (targetIndex != -1) {
            val updated = current.take(targetIndex)
            _chatMessages.value = updated
            val activeId = chatEngine.getActiveSessionId()
            if (activeId != null) {
                chatEngine.saveHistoryForSession(activeId, updated)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: KhojChatRepository? = null

        fun getInstance(context: Context): KhojChatRepository {
            return instance ?: synchronized(this) {
                instance ?: KhojChatRepository(context.applicationContext as Application).also { instance = it }
            }
        }
    }
}
