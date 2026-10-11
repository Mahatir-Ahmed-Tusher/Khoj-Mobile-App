package com.example.overlay

import android.graphics.Bitmap
import com.example.data.api.BanglaAiTextDetectionState
import com.example.data.api.FactCheckProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-scoped singleton — single source of truth for the entire overlay session.
 *
 * Survives across:
 * - Drawer dismissal and reopening
 * - Dialog dismiss/reopen cycles
 * - Navigation between Activities (while OverlayModeService keeps the process alive)
 *
 * Write API: only [OverlayModeService] should call the mutating functions.
 * Read API: overlay Compose windows observe the StateFlows.
 */
object OverlayStateRepository {

    // ── Dialog visibility ──────────────────────────────────────────────────
    /** Which overlay action dialog is currently visible, if any. */
    enum class ActiveDialog { NONE, CLIPBOARD, SCREENSHOT, MIC }

    private val _activeDialog = MutableStateFlow(ActiveDialog.NONE)
    val activeDialog: StateFlow<ActiveDialog> = _activeDialog.asStateFlow()

    // ── Result drawer visibility & type ───────────────────────────────────
    enum class ActiveDrawerType { NONE, FACT_CHECK, AI_TEXT_DETECTOR, KHOJ_CHAT }

    private val _activeDrawerType = MutableStateFlow(ActiveDrawerType.NONE)
    val activeDrawerType: StateFlow<ActiveDrawerType> = _activeDrawerType.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    // ── AI Text Detector state ─────────────────────────────────────────────
    private val _aiDetectionState = MutableStateFlow<BanglaAiTextDetectionState>(BanglaAiTextDetectionState.Idle)
    val aiDetectionState: StateFlow<BanglaAiTextDetectionState> = _aiDetectionState.asStateFlow()

    private val _aiDetectorInputText = MutableStateFlow("")
    val aiDetectorInputText: StateFlow<String> = _aiDetectorInputText.asStateFlow()

    // ── Pending claim text (from clipboard, voice, or text selection) ──────
    private val _pendingClaimText = MutableStateFlow<String?>(null)
    val pendingClaimText: StateFlow<String?> = _pendingClaimText.asStateFlow()

    // ── Screenshot bitmap (held between capture and user confirm) ──────────
    private val _pendingBitmap = MutableStateFlow<Bitmap?>(null)
    val pendingBitmap: StateFlow<Bitmap?> = _pendingBitmap.asStateFlow()

    // ── Voice transcription (live partial results) ─────────────────────────
    private val _voiceTranscription = MutableStateFlow("")
    val voiceTranscription: StateFlow<String> = _voiceTranscription.asStateFlow()

    // ── Fact-check pipeline progress ───────────────────────────────────────
    private val _factCheckState = MutableStateFlow<FactCheckProgress>(FactCheckProgress.Idle)
    val factCheckState: StateFlow<FactCheckProgress> = _factCheckState.asStateFlow()

    // ── Streaming markdown (live LLM output) ──────────────────────────────
    private val _streamingMarkdown = MutableStateFlow("")
    val streamingMarkdown: StateFlow<String> = _streamingMarkdown.asStateFlow()

    // ── Session source label ───────────────────────────────────────────────
    // "clipboard" | "screenshot" | "voice"
    private val _sessionSource = MutableStateFlow<String?>(null)
    val sessionSource: StateFlow<String?> = _sessionSource.asStateFlow()

    // ── MediaProjection token state ────────────────────────────────────────
    private val _isMediaProjectionReady = MutableStateFlow(false)
    val isMediaProjectionReady: StateFlow<Boolean> = _isMediaProjectionReady.asStateFlow()

    // ──────────────────────────────────────────────────────────────────────
    // Dialog management
    // ──────────────────────────────────────────────────────────────────────

    fun showDialog(dialog: ActiveDialog) {
        _activeDialog.value = dialog
    }

    fun dismissDialog() {
        _activeDialog.value = ActiveDialog.NONE
    }

    // ──────────────────────────────────────────────────────────────────────
    // Drawer management
    // ──────────────────────────────────────────────────────────────────────

    fun openAiTextDrawer() {
        _activeDrawerType.value = ActiveDrawerType.AI_TEXT_DETECTOR
        _isDrawerOpen.value = true
    }

    fun openFactCheckDrawer() {
        _activeDrawerType.value = ActiveDrawerType.FACT_CHECK
        _isDrawerOpen.value = true
    }

    fun openKhojChatDrawer() {
        _activeDrawerType.value = ActiveDrawerType.KHOJ_CHAT
        _isDrawerOpen.value = true
    }

    fun closeDrawer() {
        _activeDrawerType.value = ActiveDrawerType.NONE
        _isDrawerOpen.value = false
    }

    fun setDrawerOpen(open: Boolean) {
        _isDrawerOpen.value = open
        if (!open) {
            _activeDrawerType.value = ActiveDrawerType.NONE
        } else if (_activeDrawerType.value == ActiveDrawerType.NONE) {
            _activeDrawerType.value = ActiveDrawerType.FACT_CHECK
        }
    }

    fun setAiDetectionState(state: BanglaAiTextDetectionState) {
        _aiDetectionState.value = state
    }

    fun setAiDetectorInputText(text: String) {
        _aiDetectorInputText.value = text
    }

    // ──────────────────────────────────────────────────────────────────────
    // Payload setters
    // ──────────────────────────────────────────────────────────────────────

    fun setPendingClaimText(text: String?, source: String? = null) {
        _pendingClaimText.value = text
        if (source != null) _sessionSource.value = source
    }

    fun setPendingBitmap(bitmap: Bitmap?) {
        _pendingBitmap.value = bitmap
        if (bitmap != null) _sessionSource.value = "screenshot"
    }

    fun setVoiceTranscription(text: String) {
        _voiceTranscription.value = text
    }

    // ──────────────────────────────────────────────────────────────────────
    // Pipeline state
    // ──────────────────────────────────────────────────────────────────────

    fun setFactCheckState(state: FactCheckProgress) {
        _factCheckState.value = state
    }

    fun appendStreamingMarkdown(chunk: String) {
        _streamingMarkdown.value += chunk
    }

    fun resetStreamingMarkdown(initial: String = "") {
        _streamingMarkdown.value = initial
    }

    fun setMediaProjectionReady(ready: Boolean) {
        _isMediaProjectionReady.value = ready
    }

    // ──────────────────────────────────────────────────────────────────────
    // Session reset
    // ──────────────────────────────────────────────────────────────────────

    /** Resets pipeline state only. Does NOT reset the drawer visibility. */
    fun resetSession() {
        _factCheckState.value = FactCheckProgress.Idle
        _pendingClaimText.value = null
        _pendingBitmap.value = null
        _streamingMarkdown.value = ""
        _sessionSource.value = null
        _voiceTranscription.value = ""
    }

    /** Full teardown — called when OverlayModeService is destroyed. */
    fun fullReset() {
        resetSession()
        _activeDialog.value = ActiveDialog.NONE
        _activeDrawerType.value = ActiveDrawerType.NONE
        _isDrawerOpen.value = false
        _isMediaProjectionReady.value = false
        _aiDetectionState.value = BanglaAiTextDetectionState.Idle
        _aiDetectorInputText.value = ""
    }
}
