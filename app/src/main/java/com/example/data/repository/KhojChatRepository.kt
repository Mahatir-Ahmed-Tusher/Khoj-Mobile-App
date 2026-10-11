package com.example.data.repository

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.data.api.KhojChatEngine
import com.example.data.api.KhojChatMessage
import com.example.data.api.KhojChatSession
import com.example.data.api.KhojChatState
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

    init {
        loadSessions()
        val curActiveId = chatEngine.getActiveSessionId()
        _activeSessionId.value = curActiveId
        if (curActiveId != null) {
            _chatMessages.value = chatEngine.loadHistoryForSession(curActiveId)
        } else {
            _chatMessages.value = chatEngine.loadHistory()
        }
    }

    fun loadSessions() {
        _chatSessions.value = chatEngine.loadSessions()
        _activeSessionId.value = chatEngine.getActiveSessionId()
    }

    fun loadChatHistory() {
        val activeId = _activeSessionId.value ?: chatEngine.getActiveSessionId()
        if (activeId != null) {
            _chatMessages.value = chatEngine.loadHistoryForSession(activeId)
        } else {
            _chatMessages.value = chatEngine.loadHistory()
        }
    }

    fun setChatMode(mode: String) {
        _activeChatMode.value = mode
    }

    fun createNewSession() {
        chatEngine.setActiveSessionId(null)
        _activeSessionId.value = null
        _chatMessages.value = emptyList()
        _chatState.value = KhojChatState.Idle
    }

    fun loadSession(sessionId: String) {
        chatEngine.setActiveSessionId(sessionId)
        _activeSessionId.value = sessionId
        _chatMessages.value = chatEngine.loadHistoryForSession(sessionId)
        _chatState.value = KhojChatState.Idle
    }

    fun deleteSession(sessionId: String) {
        chatEngine.deleteSession(sessionId)
        loadSessions()
        val currentActiveId = chatEngine.getActiveSessionId()
        if (currentActiveId != null) {
            _chatMessages.value = chatEngine.loadHistoryForSession(currentActiveId)
        } else {
            _chatMessages.value = emptyList()
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
        _chatState.value = KhojChatState.Idle
    }

    fun sendChatMessage(
        text: String,
        imageUri: String? = null,
        onAutoSwitched: () -> Unit = {},
        promptForEngine: String? = null
    ) {
        if (text.isBlank()) return
        val currentMode = _activeChatMode.value

        // Ensure we have an active session
        var activeId = chatEngine.getActiveSessionId()
        if (activeId == null) {
            val newId = UUID.randomUUID().toString()
            val truncatedTitle = if (text.length > 45) text.take(42) + "..." else text
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
            text = text,
            isUser = true,
            mode = currentMode,
            imageUri = imageUri,
            promptForEngine = promptForEngine
        )
        val updatedHistory = _chatMessages.value + userMsg
        _chatMessages.value = updatedHistory
        chatEngine.saveHistoryForSession(activeId, updatedHistory)

        repositoryScope.launch {
            val currentResponseLanguage = responseLanguageManager.responseLanguage.value
            val queryForEngine = promptForEngine ?: text
            chatEngine.sendChatMessage(queryForEngine, currentMode, updatedHistory, currentResponseLanguage).collect { state ->
                _chatState.value = state
                when (state) {
                    is KhojChatState.AutoDetecting -> {
                        _activeChatMode.value = "fact-check"
                        onAutoSwitched()
                    }
                    is KhojChatState.Success -> {
                        if (state.autoSwitchedToFactCheck) {
                            _activeChatMode.value = "fact-check"
                        }
                        val aiMsg = KhojChatMessage(
                            text = state.responseText,
                            isUser = false,
                            mode = if (state.autoSwitchedToFactCheck) "fact-check" else currentMode,
                            verdict = state.verdict,
                            sources = state.sources,
                            autoSwitched = state.autoSwitchedToFactCheck
                        )
                        val finalHistory = _chatMessages.value + aiMsg
                        _chatMessages.value = finalHistory
                        chatEngine.saveHistoryForSession(activeId, finalHistory)
                        _chatState.value = KhojChatState.Idle
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
