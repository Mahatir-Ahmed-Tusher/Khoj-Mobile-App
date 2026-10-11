package com.example.data.api

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Shared helper for extracting text from an image across the entire app and overlay.
 * Pipeline:
 * Tier 1 (Primary): Dedicated Bangla OCR Cloudflare Worker Gateway (BANGLA_OCR_API_URL).
 * Tier 2 (Fallback): Groq Multimodal Vision with qwen/qwen3.8-27b (GROQ_API_KEY).
 * Tier 3 (Safety Fallback): Google Gemini 2.5 Flash (GEMINI_API_KEY).
 */
object ImageOcrHelper {

    private const val TAG = "ImageOcrHelper"

    private fun cleanKey(key: String): String {
        var k = key.trim()
        if (k.startsWith("\"") && k.endsWith("\"")) k = k.substring(1, k.length - 1)
        return k.trim()
    }

    private fun cleanUrl(url: String): String {
        var u = url.trim()
        if (u.startsWith("\"") && u.endsWith("\"")) u = u.substring(1, u.length - 1)
        if (u.endsWith("/")) u = u.substring(0, u.length - 1)
        return u.trim()
    }

    private val banglaOcrApiUrl: String
        get() {
            val url = try {
                cleanUrl(BuildConfig.BANGLA_OCR_API_URL)
            } catch (_: Exception) {
                ""
            }
            return if (url.isNotBlank()) url else "https://bangla-ocr-api.mahatirtusher.workers.dev"
        }

    private val groqApiKey = cleanKey(BuildConfig.GROQ_API_KEY)
    private val geminiApiKey = cleanKey(BuildConfig.GEMINI_API_KEY)

    /**
     * Decodes and extracts text from an image Uri across Android versions.
     */
    suspend fun extractTextFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val bitmap: Bitmap? = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
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
                contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap from uri", e)
            null
        }

        if (bitmap == null) throw Exception("Failed to decode image from Uri")
        extractTextFromBitmap(bitmap)
    }

    /**
     * Extracts and cleans the claim from a bitmap.
     * Throws an exception if all tiers fail.
     */
    suspend fun extractTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        var textResponse = ""
        val ocrPrompt = "Extract only the exact text written in this image. Do not add any conversational filler, markdown, or extra explanations. Just the text."

        // ---------------------------------------------------------
        // Tier 1: Primary - Bangla OCR Cloudflare Worker Gateway
        // ---------------------------------------------------------
        try {
            val client = NetworkClientProvider.ocrAndLlmClient
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            val imageBytes = stream.toByteArray()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "image.jpg",
                    imageBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val ocrEndpoint = if (banglaOcrApiUrl.endsWith("/ocr") || banglaOcrApiUrl.endsWith("/api/ocr")) {
                banglaOcrApiUrl
            } else {
                "$banglaOcrApiUrl/ocr"
            }

            val request = Request.Builder()
                .url(ocrEndpoint)
                .post(requestBody)
                .addHeader("User-Agent", "KhojApp/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: throw Exception("Empty response from Bangla OCR Gateway")
                if (!response.isSuccessful) {
                    throw Exception("Bangla OCR Gateway failed with code ${response.code}: $responseBody")
                }
                val root = JSONObject(responseBody)
                val fullText = root.optString("full_text").trim()
                if (fullText.isNotBlank()) {
                    textResponse = fullText
                    Log.d(TAG, "Tier 1 (Bangla OCR Worker) extracted text successfully (${fullText.length} chars)")
                } else if (root.has("pages")) {
                    val pages = root.optJSONArray("pages")
                    val sb = StringBuilder()
                    if (pages != null) {
                        for (i in 0 until pages.length()) {
                            val pText = pages.optJSONObject(i)?.optString("text")?.trim() ?: ""
                            if (pText.isNotBlank()) {
                                if (sb.isNotEmpty()) sb.append("\n\n")
                                sb.append(pText)
                            }
                        }
                    }
                    textResponse = sb.toString().trim()
                }
            }
        } catch (tier1Exception: Throwable) {
            Log.e(TAG, "Tier 1 (Bangla OCR Worker) failed, falling back to Tier 2 (Groq qwen/qwen3.8-27b)", tier1Exception)
        }

        // ---------------------------------------------------------
        // Tier 2: Fallback - Groq Multimodal Vision (qwen/qwen3.8-27b)
        // ---------------------------------------------------------
        if (textResponse.isBlank()) {
            try {
                if (groqApiKey.isBlank()) {
                    throw Exception("Groq API key is blank")
                }
                val client = NetworkClientProvider.ocrAndLlmClient
                val mediaType = "application/json".toMediaType()
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val base64Image = withContext(Dispatchers.Default) {
                    Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                }

                val jsonPayload = JSONObject().apply {
                    put("model", "qwen/qwen3.8-27b")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", ocrPrompt)
                                })
                                put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().apply {
                                        put("url", "data:image/jpeg;base64,$base64Image")
                                    })
                                })
                            })
                        })
                    })
                    put("temperature", 0.6)
                    put("max_completion_tokens", 2048)
                    put("top_p", 0.95)
                }

                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .post(jsonPayload.toString().toRequestBody(mediaType))
                    .addHeader("Authorization", "Bearer $groqApiKey")
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: throw Exception("Empty response from Groq Qwen API")
                    if (!response.isSuccessful) {
                        throw Exception("Groq Qwen API failed with code ${response.code}: $responseBody")
                    }
                    val root = JSONObject(responseBody)
                    if (root.has("choices")) {
                        textResponse = root.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                        Log.d(TAG, "Tier 2 (Groq qwen/qwen3.8-27b) extracted text successfully")
                    }
                }
            } catch (tier2Exception: Throwable) {
                Log.e(TAG, "Tier 2 (Groq qwen/qwen3.8-27b) failed, falling back to Tier 3 (Gemini 2.5 Flash)", tier2Exception)
            }
        }

        // ---------------------------------------------------------
        // Tier 3: Safety Fallback - Google Gemini (gemini-2.5-flash)
        // ---------------------------------------------------------
        if (textResponse.isBlank()) {
            try {
                if (geminiApiKey.isBlank()) {
                    throw Exception("Gemini API key is blank")
                }
                val client = NetworkClientProvider.ocrAndLlmClient
                val mediaType = "application/json".toMediaType()
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val base64Image = withContext(Dispatchers.Default) {
                    Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                }

                val jsonPayload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", ocrPrompt)
                                })
                                put(JSONObject().apply {
                                    put("inlineData", JSONObject().apply {
                                        put("mimeType", "image/jpeg")
                                        put("data", base64Image)
                                    })
                                })
                            })
                        })
                    })
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiApiKey")
                    .post(jsonPayload.toString().toRequestBody(mediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: throw Exception("Empty response from Gemini API")
                    if (!response.isSuccessful) {
                        throw Exception("Gemini API failed with code ${response.code}: $responseBody")
                    }
                    val root = JSONObject(responseBody)
                    val candidates = root.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val firstPart = parts?.optJSONObject(0)
                    textResponse = firstPart?.optString("text")?.trim() ?: ""
                    Log.d(TAG, "Tier 3 (Gemini 2.5 Flash) extracted text successfully")
                }
            } catch (tier3Exception: Throwable) {
                Log.e(TAG, "Tier 3 (Gemini API) also failed", tier3Exception)
                throw tier3Exception
            }
        }

        // Clean the extracted text using an LLM
        if (textResponse.isNotBlank()) {
            val cleanedClaim = cleanOcrText(textResponse)
            if (cleanedClaim.isNotBlank()) {
                return@withContext cleanedClaim
            }
        }

        return@withContext textResponse
    }

    private suspend fun cleanOcrText(extractedText: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are an expert fact-checking claim extractor for news photocards, banners, and social media images.
            Extract the core factual claim to be verified from the OCR text using the following universal structural principles.

            UNIVERSAL STRUCTURAL PRINCIPLES:

            1. STRUCTURAL SEPARATION (CONTAINER METADATA VS. CORE ASSERTION):
               Text in news photocards and social media cards is divided into two distinct layers:
               A. Container & Publication Metadata (MUST BE ENTIRELY EXCLUDED):
                  - Publication / issue dates, timestamps, clock times, days of the week, relative posting times (e.g., dates in headers/footers, 'আজ', 'গতকাল', 'Updated', time markers).
                  - Media branding, logos, outlet names, section/category tags (e.g., 'জাতীয়', 'রাজনীতি', 'খেলা', 'বিনোদন'), watermarks, slogans.
                  - Web domains, URLs, social media calls-to-action (e.g., 'বিস্তারিত কমেন্টে', 'শেয়ার করুন', 'ফলো করুন', comment prompts).
                  - Editorial bylines and credits (e.g., 'Source: Desk', 'অনলাইন ডেস্ক', 'ডেস্ক রিপোর্ট', 'নিজস্ব প্রতিবেদক', 'স্টাফ রিপোর্টার', 'Staff Reporter', 'ছবি: সংগৃহীত', photo sources).
                  - Commercial advertisements, sponsor logos, or commercial product banners.
               B. Core Substantive Assertion (THE SOLE BASIS FOR THE CLAIM):
                  - The central headline or statement describing an event, incident, achievement, or claim: [Subject(s)] + [Action / Event] + [Context].

            2. STRICT PROHIBITION OF TEMPORAL ANCHORING FROM METADATA:
               - Card printing/publication timestamps belong strictly to the container, NOT the event itself.
               - NEVER anchor or frame the verification claim around a publication date or timestamp (NEVER prepend or write "[Date] তারিখে কি...").
               - Strip all dates and timestamps from the claim UNLESS the central headline itself explicitly announces or disputes a specific calendar date as its primary factual premise.

            3. CLAIM FORMULATION:
               - Formulate the claim as a direct, neutral verification question focusing on the core action/event:
                 [Subject] কি [Action / Event] করেছেন/ঘটেছে?
               - Event / Incident Claims: State the core factual event directly for verification.
               - Quoted Statements: Attribute a statement ONLY if a specific named real-world person or authority is explicitly quoted as having spoken it.

            4. PRESERVE DISAMBIGUATION & INSTITUTIONAL CONTEXT:
               - If an educational institution, organization, company, or geographic identity is mentioned alongside a person, ALWAYS preserve it with the person's name to prevent namesake confusion.

            5. OUTPUT FORMAT:
               - Keep the extracted claim in its original language (Bengali if Bengali, English if English).
               - Return ONLY the clean, structured single-sentence claim.
               - No explanations, no markdown formatting, no quotes, no conversational filler, no <think> tags.

            OCR text:
            $extractedText
        """.trimIndent()

        // Tier 1 for claim cleanup: Groq qwen/qwen3.8-27b (fast, resilient, high rate limit)
        if (groqApiKey.isNotBlank()) {
            try {
                val client = NetworkClientProvider.ocrAndLlmClient
                val jsonPayload = JSONObject().apply {
                    put("model", "qwen/qwen3.8-27b")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.2)
                    put("max_completion_tokens", 1024)
                }
                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $groqApiKey")
                    .build()
                client.newCall(request).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    if (!res.isSuccessful) {
                        throw Exception("Groq API failed with code ${res.code}: $body")
                    }
                    val root = JSONObject(body)
                    val content = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                    val cleanContent = content.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
                    if (cleanContent.isNotBlank()) {
                        Log.d(TAG, "Claim cleaned successfully using Groq")
                        return@withContext cleanContent
                    }
                }
            } catch (groqEx: Exception) {
                Log.w(TAG, "Groq claim cleaning failed, falling back to Gemini", groqEx)
            }
        }

        // Tier 2 for claim cleanup: Google Gemini 2.5 Flash
        if (geminiApiKey.isNotBlank()) {
            try {
                val client = NetworkClientProvider.ocrAndLlmClient
                val mediaType = "application/json".toMediaType()
                val jsonPayload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                }
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiApiKey")
                    .post(jsonPayload.toString().toRequestBody(mediaType))
                    .build()
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: throw Exception("Empty response from Gemini API")
                    if (!response.isSuccessful) {
                        throw Exception("Gemini API failed with code ${response.code}: $responseBody")
                    }
                    val root = JSONObject(responseBody)
                    val candidates = root.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val firstPart = parts?.optJSONObject(0)
                    val text = firstPart?.optString("text")?.trim() ?: ""
                    val cleanText = text.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
                    if (cleanText.isNotBlank()) {
                        Log.d(TAG, "Claim cleaned successfully using Gemini")
                        return@withContext cleanText
                    }
                }
            } catch (geminiEx: Exception) {
                Log.e(TAG, "Gemini claim cleaning also failed", geminiEx)
            }
        }

        return@withContext extractedText.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
    }
}
