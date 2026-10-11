package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.telemetry.PipelineStage
import com.example.data.telemetry.PipelineTelemetry
import com.example.data.telemetry.PipelineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class LlmOrchestrator(
    private val context: Context,
    private val pipelineType: PipelineType,
    private val isForPlanning: Boolean = false,
    private val circuitBreaker: CircuitBreaker = CircuitBreaker()
) {
    private val tag = "LlmOrchestrator"

    private fun getApiKey(keyName: String, fallback: String = ""): String {
        val prefs = context.getSharedPreferences("khoj_settings", Context.MODE_PRIVATE)
        val rawKey = prefs.getString(keyName, "")?.takeIf { it.isNotEmpty() } ?: fallback
        return rawKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    }

    private fun isValidKey(key: String?): Boolean {
        if (key == null) return false
        val trimmed = key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        return trimmed.isNotEmpty() && !trimmed.startsWith("MY_") && !trimmed.contains("PLACEHOLDER")
    }

    val providers: List<LlmProvider> by lazy {
        val list = mutableListOf<LlmProvider>()
        val geminiKey = getApiKey("gemini_api_key", BuildConfig.GEMINI_API_KEY)
        val zaiKey = getApiKey("zai_api_key", BuildConfig.ZAI_API_KEY)
        val groqKey = getApiKey("groq_api_key", BuildConfig.GROQ_API_KEY)
        val mistralKey = getApiKey("mistral_api_key", BuildConfig.MISTRAL_API_KEY)

        if (isForPlanning) {
            // Planning: Groq -> Mistral 3 8B -> Z.AI -> Gemini
            if (isValidKey(groqKey)) list.add(GroqLlmProvider(groqKey))
            if (isValidKey(mistralKey)) list.add(MistralLlmProvider(mistralKey))
            if (isValidKey(zaiKey)) list.add(ZaiLlmProvider(zaiKey))
            if (isValidKey(geminiKey)) list.add(GeminiLlmProvider(geminiKey))
        } else {
            // Report Generation: Prioritize Gemini 2.5 Flash, then ZAI, then Mistral, then Groq
            if (isValidKey(geminiKey)) list.add(GeminiLlmProvider(geminiKey))
            if (isValidKey(zaiKey)) list.add(ZaiLlmProvider(zaiKey))
            if (isValidKey(mistralKey)) list.add(MistralLlmProvider(mistralKey))
            if (isValidKey(groqKey)) list.add(GroqLlmProvider(groqKey))
        }
        
        list
    }

    suspend fun generateFallback(request: LlmRequest): String = withContext(Dispatchers.IO) {
        for (provider in providers) {
            try {
                PipelineTelemetry.log(pipelineType, PipelineStage.LLM, "Attempting generation with ${provider.name}")
                val result = provider.generate(request)
                if (result.isNotBlank()) {
                    return@withContext stripReasoningLeak(result)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Propagate cancellation/timeout
            } catch (e: Exception) {
                PipelineTelemetry.error(pipelineType, PipelineStage.LLM, "${provider.name} failed", e)
            }
        }
        return@withContext ""
    }

    fun streamFallback(request: LlmRequest): Flow<String> = flow {
        var success = false
        for (provider in providers) {
            try {
                PipelineTelemetry.log(pipelineType, PipelineStage.LLM, "Attempting stream with ${provider.name}")
                var receivedTokens = false
                provider.stream(request).collect { chunk ->
                    val cleanChunk = stripReasoningLeak(chunk)
                    if (cleanChunk.isNotEmpty()) {
                        receivedTokens = true
                        emit(cleanChunk)
                    }
                }
                if (receivedTokens) {
                    success = true
                    break // Stop fallback chain on successful stream completion
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Propagate cancellation/timeout
            } catch (e: Exception) {
                PipelineTelemetry.error(pipelineType, PipelineStage.LLM, "${provider.name} streaming failed midway/initial", e)
                // If we fail midway, we should ideally notify the UI or restart if nothing was emitted,
                // but Flow restart semantics require complex state. 
                // We just continue to the next provider which will emit from the beginning.
            }
        }
        if (!success) {
            throw Exception("All LLM providers failed to stream")
        }
    }.flowOn(Dispatchers.IO)

    private fun stripReasoningLeak(text: String): String {
        return text.replace(Regex("(?s)<think>.*?</think>"), "")
                   .replace(Regex("(?s)<thinking>.*?</thinking>"), "")
                   .replace(Regex("(?s)<reasoning>.*?</reasoning>"), "")
                   .trim()
    }
}

class GeminiLlmProvider(private val apiKey: String) : LlmProvider {
    override val name = "Gemini 2.5 Flash"
    private val client = NetworkClientProvider.ocrAndLlmClient

    override suspend fun generate(request: LlmRequest): String = withContext(Dispatchers.IO) {
        val jsonMediaType = "application/json".toMediaType()
        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", request.userPrompt) })
                    })
                })
            })
            if (request.systemPrompt.isNotBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", request.systemPrompt) })
                    })
                })
            }
            put("generationConfig", JSONObject().apply {
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxTokens)
                if (request.jsonOutput) {
                    put("responseMimeType", "application/json")
                }
            })
        }

        val httpRequest = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Gemini HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body")
            val root = JSONObject(body)
            val candidates = root.optJSONArray("candidates") ?: throw Exception("No candidates")
            if (candidates.length() == 0) throw Exception("Empty candidates array")
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: throw Exception("No parts")
            if (parts.length() == 0) throw Exception("Empty parts array")
            parts.getJSONObject(0).optString("text", "")
        }
    }

    override fun stream(request: LlmRequest): Flow<String> = flow {
        // Simple non-streaming fallback for Gemini for now, as real streaming requires complex SSE parsing
        // We will emit the full text as a single chunk to satisfy the Flow interface
        val result = generate(request)
        emit(result)
    }
}

class ZaiLlmProvider(private val apiKey: String) : LlmProvider {
    override val name = "Z.AI GLM-4.7-Flash"
    private val client = NetworkClientProvider.ocrAndLlmClient

    override suspend fun generate(request: LlmRequest): String = withContext(Dispatchers.IO) {
        val jsonMediaType = "application/json".toMediaType()
        val payload = JSONObject().apply {
            put("model", "glm-4.7-flash")
            put("messages", JSONArray().apply {
                if (request.systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", request.systemPrompt)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", request.userPrompt)
                })
            })
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            if (request.jsonOutput) {
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }
        }

        val httpRequest = Request.Builder()
            .url("https://api.z.ai/api/paas/v4/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Z.AI HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body")
            val root = JSONObject(body)
            val choices = root.optJSONArray("choices") ?: throw Exception("No choices")
            if (choices.length() == 0) throw Exception("Empty choices array")
            choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
        }
    }

    override fun stream(request: LlmRequest): Flow<String> = flow {
        // Implement SSE for Z.AI later, fallback to generating full
        val result = generate(request)
        emit(result)
    }
}

class GroqLlmProvider(private val apiKey: String) : LlmProvider {
    override val name = "Groq qwen3.8-27b"
    private val client = NetworkClientProvider.ocrAndLlmClient

    override suspend fun generate(request: LlmRequest): String = withContext(Dispatchers.IO) {
        val jsonMediaType = "application/json".toMediaType()
        val payload = JSONObject().apply {
            put("model", "qwen/qwen3.8-27b")
            put("messages", JSONArray().apply {
                if (request.systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", request.systemPrompt)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", request.userPrompt)
                })
            })
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            if (request.jsonOutput) {
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }
        }

        val httpRequest = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Groq HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body")
            val root = JSONObject(body)
            val choices = root.optJSONArray("choices") ?: throw Exception("No choices")
            if (choices.length() == 0) throw Exception("Empty choices array")
            choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
        }
    }

    override fun stream(request: LlmRequest): Flow<String> = flow {
        val result = generate(request)
        emit(result)
    }
}

class MistralLlmProvider(private val apiKey: String) : LlmProvider {
    override val name = "Mistral 3 8B"
    private val client = NetworkClientProvider.ocrAndLlmClient

    override suspend fun generate(request: LlmRequest): String = withContext(Dispatchers.IO) {
        val jsonMediaType = "application/json".toMediaType()
        val payload = JSONObject().apply {
            put("model", "ministral-8b-latest")
            put("messages", JSONArray().apply {
                if (request.systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", request.systemPrompt)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", request.userPrompt)
                })
            })
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            if (request.jsonOutput) {
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }
        }

        val httpRequest = Request.Builder()
            .url("https://api.mistral.ai/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Mistral HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body")
            val root = JSONObject(body)
            val choices = root.optJSONArray("choices") ?: throw Exception("No choices")
            if (choices.length() == 0) throw Exception("Empty choices array")
            choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
        }
    }

    override fun stream(request: LlmRequest): Flow<String> = flow {
        val result = generate(request)
        emit(result)
    }
}
