package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single Source of Truth for extracting / distilling core claims from large text inputs
 * (> 140 chars) originating from URLs, Image OCR, Manual Searchbox Pastes, or DeepSearch.
 *
 * Primary Engine: Mistral 3 8B (ministral-8b-latest) via MISTRAL_API_KEY
 * Fallback Engine: Gemini 2.5 Flash with Smart Inverted-Pyramid Windowing
 */
object ClaimExtractor {
    private const val TAG = "ClaimExtractor"
    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Extracts a clean, 1-2 sentence fact-check claim preserving core entities and metrics.
     * If the input is already a concise assertion (<= 250 chars, <= 2 sentences, no paragraph breaks),
     * returns it as-is without API overhead.
     */
    suspend fun extractClaim(rawText: String): String = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        val sentences = trimmed.split(Regex("[।\n!?]")).map { it.trim() }.filter { it.isNotBlank() }
        val hasQuestions = trimmed.contains("?") || trimmed.contains("কী") || trimmed.contains("কি ") || trimmed.contains("নাকি")

        val isLargeInput = trimmed.length > 250 || sentences.size >= 3 || trimmed.contains("\n\n")
        if (!isLargeInput && !hasQuestions) {
            return@withContext trimmed
        }

        // Apply smart windowing if text is unusually huge (> 2500 chars)
        val condensedText = getCondensedText(trimmed)

        // Tier 1: Mistral 3 8B (ministral-8b-latest)
        val mistralKey = BuildConfig.MISTRAL_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (isValidKey(mistralKey)) {
            try {
                val claim = extractWithMistral(condensedText, mistralKey)
                if (claim.isNotBlank()) {
                    Log.d(TAG, "Successfully extracted claim via Mistral 3 8B: ${claim.take(60)}")
                    return@withContext cleanExtractedClaim(claim)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mistral 3 8B extraction failed, falling back to Gemini: ${e.message}")
            }
        }

        // Tier 2: Gemini 2.5 Flash Fallback
        val geminiKey = BuildConfig.GEMINI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (isValidKey(geminiKey)) {
            try {
                val claim = extractWithGemini(condensedText, geminiKey)
                if (claim.isNotBlank()) {
                    Log.d(TAG, "Successfully extracted claim via Gemini 2.5 Flash fallback: ${claim.take(60)}")
                    return@withContext cleanExtractedClaim(claim)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini fallback extraction failed: ${e.message}")
            }
        }

        // Tier 3: Deterministic Safe Fallback (Take lead paragraph or first 250 chars)
        Log.w(TAG, "All LLM extractors failed. Using deterministic lead fallback.")
        return@withContext getDeterministicLeadFallback(trimmed)
    }

    private fun isValidKey(key: String?): Boolean {
        if (key == null) return false
        val trimmed = key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        return trimmed.isNotEmpty() && !trimmed.startsWith("MY_") && !trimmed.contains("PLACEHOLDER")
    }

    /**
     * Inverted Pyramid Windowing: Preserves title, lead paragraphs, and conclusion while
     * pruning noisy body text if length exceeds 2,500 characters.
     */
    fun getCondensedText(fullText: String): String {
        if (fullText.length <= 2500) return fullText
        val lead = fullText.take(1800).trim()
        val tail = fullText.takeLast(500).trim()
        return "$lead\n\n[...]\n\n$tail"
    }

    private const val SYSTEM_PROMPT = """You are an elite, skeptic fact-checking claim distillation engine.
Analyze the provided text (article, social media post, or statement) and distill the single core factual assertion that needs verification.

STRICT PRINCIPLES (NO EXCEPTIONS):
1. SINGLE CONCISE DECLARATIVE STATEMENT (একটি স্পষ্ট ও সংক্ষিপ্ত বিবৃতি):
   - Formulate the claim strictly as EXACTLY 1 direct, declarative sentence (maximum 25-30 words).
   - NEVER output a question (NO '?', 'কীভাবে', 'কতটুকু', 'কি', 'why', 'how'). If the input is a question or inquiry, convert it into the underlying affirmative claim being tested.
   - Be skeptic, sharp, and lowkey. Avoid dramatic adjectives, reactions, and conversational narrative.

2. CORE FACTUAL ANCHORS ONLY (মূল বিতর্কিত দাবি ও সংখ্যা):
   - Retain strictly the core disputable allegation: the key subject/entity, the claimed event, and any critical metric (e.g., casualty count, exact percentage, test finding).
   - Strip all secondary administrative details, preventative advice, advisory quotes, future plans, and background commentary.

3. PRESERVE DISPUTED EXTREMITY (দাবির চরমতা অক্ষুণ্ণ রাখা):
   - Retain the exact degree of certainty, extremity, or exaggeration present in the source allegation (e.g., if the text claims "ধুমপান করলেই যক্ষা হয়", do NOT soften or align it with medical reality to say "ধুমপান যক্ষার ঝুঁকি বাড়ায়").
   - Distill what is ALLEGED by the poster/author, without pre-correcting or altering the dispute.

4. FORMAT & LENGTH:
   - Output strictly 1 short sentence (maximum 25-30 words).
   - Output in the primary language of the source text (Bengali for Bengali input, English for English input).
   - Output ONLY the distilled claim text. Do NOT add labels (e.g., 'দাবি:', 'Claim:'), prefixes, markdown, or quotes."""

    private suspend fun extractWithMistral(text: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val client = NetworkClientProvider.ocrAndLlmClient

        val payload = JSONObject().apply {
            put("model", "ministral-8b-latest")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", text)
                })
            })
            put("temperature", 0.1)
            put("max_tokens", 90)
        }

        val request = Request.Builder()
            .url("https://api.mistral.ai/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Mistral HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body")
            val root = JSONObject(body)
            val choices = root.optJSONArray("choices") ?: throw Exception("No choices")
            if (choices.length() == 0) throw Exception("Empty choices array")
            choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")?.trim() ?: ""
        }
    }

    private suspend fun extractWithGemini(text: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val client = NetworkClientProvider.ocrAndLlmClient

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$SYSTEM_PROMPT\n\nINPUT TEXT:\n$text")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 100)
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Gemini HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body")
            val root = JSONObject(body)
            val candidates = root.optJSONArray("candidates") ?: throw Exception("No candidates")
            if (candidates.length() == 0) throw Exception("Empty candidates array")
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: throw Exception("No parts")
            if (parts.length() == 0) throw Exception("Empty parts array")
            parts.getJSONObject(0).optString("text", "").trim()
        }
    }

    private fun cleanExtractedClaim(claim: String): String {
        return claim.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removePrefix("**")
            .removeSuffix("**")
            .replace(Regex("^(দাবি\\s*[:：]|মূল দাবি\\s*[:：]|Claim\\s*[:：])\\s*", RegexOption.IGNORE_CASE), "")
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()
    }

    private fun getDeterministicLeadFallback(text: String): String {
        val firstSentence = text.split(Regex("[।\n.!?]")).firstOrNull { it.isNotBlank() }?.trim()
        return if (!firstSentence.isNullOrBlank() && firstSentence.length <= 250) {
            firstSentence
        } else {
            text.take(250).trim()
        }
    }
}
