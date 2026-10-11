package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.BanglaAiTextDetectionState
import com.example.data.api.BanglaAiTextDetectorApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// BANGLA AI TEXT DETECTOR VIEWMODEL
// Manages the text input and detection state for BanglaAiTextDetectorScreen.
// Follows the same MVVM pattern as AiImageDetectionViewModel.
// ─────────────────────────────────────────────────────────────────────────────

class BanglaAiTextDetectorViewModel : ViewModel() {

    // ── State ────────────────────────────────────────────────────────────────

    private val _detectionState =
        MutableStateFlow<BanglaAiTextDetectionState>(BanglaAiTextDetectionState.Idle)
    val detectionState: StateFlow<BanglaAiTextDetectionState> = _detectionState.asStateFlow()

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _operatingMode = MutableStateFlow("balanced") // "balanced" | "high_precision"
    val operatingMode: StateFlow<String> = _operatingMode.asStateFlow()

    private var activeJob: Job? = null

    // ── Word / char helpers ───────────────────────────────────────────────────

    val wordCount: Int get() = countWords(_text.value)
    val charCount: Int get() = _text.value.length

    /** Short text: < 25 words — accuracy is lower, show warning */
    val isShortText: Boolean get() = wordCount in 1..24

    /** Long text: > 120 words — multi-window analysis will run */
    val isLongText: Boolean get() = wordCount > 120

    // ── User actions ──────────────────────────────────────────────────────────

    fun onTextChange(newText: String) {
        _text.value = newText
        // Reset result if user modifies text after a successful analysis
        if (_detectionState.value is BanglaAiTextDetectionState.Success ||
            _detectionState.value is BanglaAiTextDetectionState.Error
        ) {
            _detectionState.value = BanglaAiTextDetectionState.Idle
        }
    }

    fun onModeChange(mode: String) {
        _operatingMode.value = mode
    }

    /**
     * Launches the AI text detection request.
     * Any previously running request is cancelled first.
     */
    fun analyze() {
        val inputText = _text.value.trim()
        if (inputText.isEmpty()) return

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _detectionState.value = BanglaAiTextDetectionState.Loading
            try {
                val result = BanglaAiTextDetectorApi.detect(
                    text = inputText,
                    operatingMode = _operatingMode.value
                )
                _detectionState.value = BanglaAiTextDetectionState.Success(result)
            } catch (e: Exception) {
                _detectionState.value = BanglaAiTextDetectionState.Error(
                    e.message ?: "সংযোগ স্থাপন করা সম্ভব হয়নি"
                )
            }
        }
    }

    /** Cancels any in-flight request without clearing text or result */
    fun cancelAnalysis() {
        activeJob?.cancel()
        activeJob = null
        _detectionState.value = BanglaAiTextDetectionState.Idle
    }

    /** Resets everything to the initial blank state */
    fun clearAll() {
        cancelAnalysis()
        _text.value = ""
        _detectionState.value = BanglaAiTextDetectionState.Idle
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun countWords(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
}
