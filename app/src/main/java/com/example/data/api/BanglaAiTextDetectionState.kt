package com.example.data.api

import com.example.data.model.BanglaTextDetectResponse

// ─────────────────────────────────────────────────────────────────────────────
// BANGLA AI TEXT DETECTION — UI State
// ─────────────────────────────────────────────────────────────────────────────

sealed class BanglaAiTextDetectionState {
    /** Initial state — no analysis started */
    object Idle : BanglaAiTextDetectionState()

    /** Request in flight to the Cloud Run API */
    object Loading : BanglaAiTextDetectionState()

    /** API returned a valid response */
    data class Success(val response: BanglaTextDetectResponse) : BanglaAiTextDetectionState()

    /** Network error or API error */
    data class Error(val message: String) : BanglaAiTextDetectionState()
}
