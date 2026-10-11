package com.example.data.model

// ─────────────────────────────────────────────────────────────────────────────
// BANGLA AI TEXT DETECTION — Data Models
// Mirrors types/detector.ts from bangla-ai-factchecker web app
// API: POST https://bangla-ai-detector-988501677320.us-central1.run.app/api/v1/detect
// ─────────────────────────────────────────────────────────────────────────────

/** Per-window result for long document multi-window analysis (> ~120 words) */
data class BanglaWindowResult(
    val windowId: Int,
    val wordCount: Int,
    val aiProbability: Float,
    val humanProbability: Float,
    val verdict: String,        // "AI-generated" | "Human-written"
    val textExcerpt: String
)

/** Full response from POST /api/v1/detect */
data class BanglaTextDetectResponse(
    val status: String,                // "success" | "error"
    val aiProbability: Float,          // 0.0 – 1.0
    val humanProbability: Float,       // 0.0 – 1.0
    val verdict: String,               // "AI-generated" | "Human-written"
    val appliedThreshold: Float,
    val operatingMode: String,         // "balanced" | "high_precision"
    val confidenceLevel: String,       // "very_high" | "high" | "medium" | "low"
    val wordCount: Int,
    val characterCount: Int,
    val processingTimeMs: Float,
    val modelVersion: String,
    val analysisEngine: String,        // "standard_single_window" | "long_doc_multi_window"
    // Multi-window extras (null for short texts)
    val nIndependentWindows: Int? = null,
    val aiConsensusFraction: Float? = null,
    val disagreementMad: Float? = null,
    val independentWindows: List<BanglaWindowResult>? = null
)
