package com.example.data.api

import com.example.data.model.BanglaTextDetectResponse
import com.example.data.model.BanglaWindowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────────────────────
// BANGLA AI TEXT DETECTOR API CLIENT
// Target: POST https://bangla-ai-detector-988501677320.us-central1.run.app/api/v1/detect
// Model:  BanglaBERT fine-tuned with Temperature Scaling (T = 1.361271)
// No authentication required. All traffic is unauthenticated.
// ─────────────────────────────────────────────────────────────────────────────

object BanglaAiTextDetectorApi {

    private const val BASE_URL =
        "https://bangla-ai-detector-988501677320.us-central1.run.app"

    // Reuse the existing LLM/inference-grade client (30s read timeout is fine for this API)
    private val client = NetworkClientProvider.ocrAndLlmClient

    /**
     * Sends Bengali text to the Cloud Run API for AI-authorship detection.
     *
     * @param text          The Bengali text to analyze (minimum 1 word)
     * @param operatingMode "balanced" (default) or "high_precision"
     * @return              [BanglaTextDetectResponse] on success
     * @throws Exception    On network error or non-200 HTTP status
     */
    suspend fun detect(
        text: String,
        operatingMode: String = "balanced"
    ): BanglaTextDetectResponse = withContext(Dispatchers.IO) {

        // Mirror zero-width char cleaning from the web service (detectorService.ts)
        val cleanedText = text
            .replace(Regex("[\u200B-\u200D\uFEFF]"), "")
            .trim()

        require(cleanedText.isNotEmpty()) { "Text cannot be empty." }

        val payload = JSONObject().apply {
            put("text", cleanedText)
            put("operating_mode", operatingMode)
            put("custom_threshold", JSONObject.NULL)
        }

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/detect")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string()
            ?: throw Exception("Empty response from detector service")

        if (!response.isSuccessful) {
            val errMsg = runCatching {
                JSONObject(body).optString("error", "HTTP ${response.code}")
            }.getOrDefault("HTTP ${response.code}")
            throw Exception(errMsg)
        }

        parseResponse(JSONObject(body))
    }

    /**
     * Checks whether the backend model is loaded and ready.
     * Returns true if healthy, false if unreachable.
     */
    suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/health")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val obj = JSONObject(response.body?.string() ?: "{}")
                obj.optString("status") == "healthy" && obj.optBoolean("model_loaded", true)
            } else false
        }.getOrDefault(false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON parsing — uses org.json (already in the project, no new deps needed)
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseResponse(obj: JSONObject): BanglaTextDetectResponse {
        val windows: List<BanglaWindowResult>? =
            if (obj.has("independent_windows") && !obj.isNull("independent_windows")) {
                val arr = obj.getJSONArray("independent_windows")
                (0 until arr.length()).map { i ->
                    val w = arr.getJSONObject(i)
                    BanglaWindowResult(
                        windowId        = w.optInt("window_id", i + 1),
                        wordCount       = w.optInt("word_count", 0),
                        aiProbability   = w.optDouble("ai_probability", 0.0).toFloat(),
                        humanProbability= w.optDouble("human_probability", 1.0).toFloat(),
                        verdict         = w.optString("verdict", "Human-written"),
                        textExcerpt     = w.optString("text_excerpt", "")
                    )
                }
            } else null

        return BanglaTextDetectResponse(
            status              = obj.optString("status", "success"),
            aiProbability       = obj.optDouble("ai_probability", 0.0).toFloat(),
            humanProbability    = obj.optDouble("human_probability", 1.0).toFloat(),
            verdict             = obj.optString("verdict", "Human-written"),
            appliedThreshold    = obj.optDouble("applied_threshold", 0.5).toFloat(),
            operatingMode       = obj.optString("operating_mode", "balanced"),
            confidenceLevel     = obj.optString("confidence_level", "medium"),
            wordCount           = obj.optInt("word_count", 0),
            characterCount      = obj.optInt("character_count", 0),
            processingTimeMs    = obj.optDouble("processing_time_ms", 0.0).toFloat(),
            modelVersion        = obj.optString("model_version", "bangla-ai-text-detector"),
            analysisEngine      = obj.optString("analysis_engine", "standard_single_window"),
            nIndependentWindows = if (obj.has("n_independent_windows")) obj.optInt("n_independent_windows") else null,
            aiConsensusFraction = if (obj.has("ai_consensus_fraction") && !obj.isNull("ai_consensus_fraction"))
                obj.optDouble("ai_consensus_fraction").toFloat() else null,
            disagreementMad     = if (obj.has("disagreement_mad") && !obj.isNull("disagreement_mad"))
                obj.optDouble("disagreement_mad").toFloat() else null,
            independentWindows  = windows
        )
    }
}
