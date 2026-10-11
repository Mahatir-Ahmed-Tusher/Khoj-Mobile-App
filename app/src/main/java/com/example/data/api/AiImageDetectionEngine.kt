package com.example.data.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ConsensusLevel
import com.example.data.model.EnsembleResult
import com.example.data.model.EngineResult
import com.example.data.model.EngineVerdict
import com.example.data.model.ImageVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.round

class AiImageDetectionEngine(private val context: Context) {

    val fallbackEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val tag = "AiImageDetectionEngine"

    // Dedicated OkHttpClient — shared connection pool via NetworkClientProvider
    private val client = NetworkClientProvider.ocrAndLlmClient

    private fun getApiKey(keyName: String, fallback: String = ""): String {
        val prefs = context.getSharedPreferences("khoj_settings", Context.MODE_PRIVATE)
        val rawKey = prefs.getString(keyName, "")?.takeIf { it.isNotEmpty() } ?: fallback
        return rawKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    }

    /**
     * Reads a content URI, decodes it into a Bitmap (with API-level-aware downsampling),
     * compresses to JPEG at 80% quality, and returns (bytes, mimeType, fileName).
     * Throws IllegalArgumentException if the result exceeds 10 MB.
     */
    private suspend fun prepareImageBytes(uri: Uri): Triple<ByteArray, String, String> =
        withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"

            val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // API 28+ — ImageDecoder with inline downsampling
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val targetSize = 1280
                    val maxDim = maxOf(info.size.width, info.size.height)
                    if (maxDim > targetSize) {
                        val scale = targetSize.toFloat() / maxDim
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt(),
                            (info.size.height * scale).toInt()
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                // API 24–27 — BitmapFactory with inSampleSize downsampling
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                var inSampleSize = 1
                if (options.outHeight > 1280 || options.outWidth > 1280) {
                    while ((options.outHeight / 2 / inSampleSize) >= 1280 &&
                        (options.outWidth / 2 / inSampleSize) >= 1280
                    ) {
                        inSampleSize *= 2
                    }
                }
                options.inSampleSize = inSampleSize
                options.inJustDecodeBounds = false
                var bmp: Bitmap? = null
                contentResolver.openInputStream(uri)?.use {
                    bmp = BitmapFactory.decodeStream(it, null, options)
                }
                bmp ?: throw IllegalStateException("Failed to decode image bitmap")
            }

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val bytes = stream.toByteArray()

            if (bytes.size > 10 * 1024 * 1024) {
                throw IllegalArgumentException("Image is too large. Maximum size is 10MB.")
            }

            Triple(bytes, "image/jpeg", "image.jpg")
        }

    /**
     * Executes AI image detection via the centralized Khoj Serverless Gateway.
     * Backwards-compatible parameters are provided so existing callers continue to work seamlessly.
     */
    suspend fun detect(
        imageUri: Uri,
        useSightengine: Boolean = true,
        useKhoj: Boolean = false,
        useMetadata: Boolean = false
    ): EnsembleResult {
        val startTime = System.currentTimeMillis()
        val (imageBytes, mimeType, _) = prepareImageBytes(imageUri)

        val engineResult = queryGateway(imageBytes, mimeType)
        val analysisTimeMs = System.currentTimeMillis() - startTime

        return buildEnsembleResult(engineResult, analysisTimeMs)
    }

    private suspend fun queryGateway(
        imageBytes: ByteArray,
        mimeType: String
    ): EngineResult = withContext(Dispatchers.IO) {
        val defaultUrl = "https://khoj-ai-image-gateway.mahatirtusher.workers.dev/detect"
        val defaultToken = "khoj-ai-gateway-secure-token-2024"

        val configuredUrl = try {
            BuildConfig::class.java.getField("KHOJ_AI_IMAGE_GATEWAY_URL").get(null) as? String
        } catch (_: Exception) {
            null
        } ?: defaultUrl

        val configuredToken = try {
            BuildConfig::class.java.getField("KHOJ_AI_IMAGE_GATEWAY_TOKEN").get(null) as? String
        } catch (_: Exception) {
            null
        } ?: defaultToken

        val gatewayUrl = getApiKey("khoj_ai_image_gateway_url", configuredUrl).ifEmpty { defaultUrl }
        val gatewayToken = getApiKey("khoj_ai_image_gateway_token", configuredToken).ifEmpty { defaultToken }

        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "media", "image.jpg",
                    imageBytes.toRequestBody(mimeType.toMediaType())
                )
                .addFormDataPart("models", "genai")
                .build()

            val request = Request.Builder()
                .url(gatewayUrl)
                .addHeader("X-Gateway-Token", gatewayToken)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(bodyStr).optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                    return@withContext EngineResult(
                        engineName = "Khoj AI Image Detection Engine",
                        modelId = "khoj/genai-gateway",
                        architecture = "Cloudflare Edge Serverless Gateway",
                        weight = 1.0f,
                        aiScore = 0f,
                        humanScore = 0f,
                        verdict = EngineVerdict.ERROR,
                        error = errorMsg
                    )
                }

                val json = JSONObject(bodyStr)
                if (json.optString("status") != "success") {
                    val errorObj = json.optJSONObject("error")
                    val errorMsg = errorObj?.optString("message", "Detection unsuccessful") ?: "Unknown error"
                    return@withContext EngineResult(
                        engineName = "Khoj AI Image Detection Engine",
                        modelId = "khoj/genai-gateway",
                        architecture = "Cloudflare Edge Serverless Gateway",
                        weight = 1.0f,
                        aiScore = 0f,
                        humanScore = 0f,
                        verdict = EngineVerdict.ERROR,
                        error = errorMsg
                    )
                }

                val aiGenerated = json.optJSONObject("type")?.optDouble("ai_generated", -1.0) ?: -1.0
                if (aiGenerated < 0) {
                    return@withContext EngineResult(
                        engineName = "Khoj AI Image Detection Engine",
                        modelId = "khoj/genai-gateway",
                        architecture = "Cloudflare Edge Serverless Gateway",
                        weight = 1.0f,
                        aiScore = 0f,
                        humanScore = 0f,
                        verdict = EngineVerdict.ERROR,
                        error = "Score missing from response"
                    )
                }

                val aiScore = (aiGenerated * 100.0).toFloat().coerceIn(0f, 100f)
                val humanScore = (100f - aiScore).coerceIn(0f, 100f)

                val verdict = when {
                    aiScore >= 65f -> EngineVerdict.AI
                    humanScore >= 65f -> EngineVerdict.HUMAN
                    else -> EngineVerdict.HUMAN
                }

                EngineResult(
                    engineName = "Khoj AI Image Detection Engine",
                    modelId = "khoj/genai-gateway",
                    architecture = "Cloudflare Edge Serverless Gateway",
                    weight = 1.0f,
                    aiScore = (round(aiScore * 10f) / 10f),
                    humanScore = (round(humanScore * 10f) / 10f),
                    verdict = verdict,
                    details = "AI Confidence: ${String.format("%.1f", aiScore)}%"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "AI Image Detection Gateway call failed", e)
            EngineResult(
                engineName = "Khoj AI Image Detection Engine",
                modelId = "khoj/genai-gateway",
                architecture = "Cloudflare Edge Serverless Gateway",
                weight = 1.0f,
                aiScore = 0f,
                humanScore = 0f,
                verdict = EngineVerdict.ERROR,
                error = e.localizedMessage ?: "Network error"
            )
        }
    }

    private fun buildEnsembleResult(
        engineResult: EngineResult,
        analysisTimeMs: Long
    ): EnsembleResult {
        val aiScore = engineResult.aiScore
        val humanScore = engineResult.humanScore
        val confidence = maxOf(aiScore, humanScore)

        val imageVerdict = when {
            engineResult.verdict == EngineVerdict.ERROR -> ImageVerdict.UNCERTAIN
            aiScore >= 65f -> ImageVerdict.AI
            humanScore >= 65f -> ImageVerdict.HUMAN
            else -> ImageVerdict.UNCERTAIN
        }

        return EnsembleResult(
            verdict = imageVerdict,
            confidence = (round(confidence * 10f) / 10f),
            aiScore = (round(aiScore * 10f) / 10f),
            humanScore = (round(humanScore * 10f) / 10f),
            engines = listOf(engineResult),
            successfulEngines = if (engineResult.verdict != EngineVerdict.ERROR) 1 else 0,
            totalEngines = 1,
            aiVotes = if (engineResult.verdict == EngineVerdict.AI) 1 else 0,
            humanVotes = if (engineResult.verdict == EngineVerdict.HUMAN) 1 else 0,
            majorityAgreement = 100,
            consensusLevel = ConsensusLevel.SINGLE_ENGINE,
            analysisTimeMs = analysisTimeMs
        )
    }
}
