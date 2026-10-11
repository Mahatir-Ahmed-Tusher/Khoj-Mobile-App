package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.data.preferences.ResponseLanguage
import com.example.data.preferences.ResponseLanguageManager
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.example.data.prompt.FactCheckPrompt
import com.example.data.telemetry.PipelineStage
import com.example.data.telemetry.PipelineTelemetry
import com.example.data.telemetry.PipelineType
import java.util.UUID
import java.util.concurrent.TimeUnit

data class KhojChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String, // "general", "citizen-service", "fact-check"
    val verdict: String? = null,
    val sources: List<KhojChatSource> = emptyList(),
    val autoSwitched: Boolean = false,
    val isError: Boolean = false,
    val imageUri: String? = null,
    val promptForEngine: String? = null
)

data class KhojChatSource(
    val title: String,
    val url: String,
    val snippet: String = "",
    val content: String? = null
)

data class KhojChatSession(
    val id: String,
    val title: String,
    val timestamp: Long
)

sealed class KhojChatState {
    object Idle : KhojChatState()
    object AutoDetecting : KhojChatState()
    object Searching : KhojChatState()
    object Generating : KhojChatState()
    data class Success(val responseText: String, val verdict: String?, val sources: List<KhojChatSource>, val autoSwitchedToFactCheck: Boolean) : KhojChatState()
    data class Error(val message: String) : KhojChatState()
}

class KhojChatEngine(private val context: Context) {
    private val tag = "KhojChatEngine"
    private val prefsName = "khoj_chat_history_prefs"
    private val historyKey = "chat_messages_history"

    private val client = NetworkClientProvider.ocrAndLlmClient
    private val searchOrchestrator by lazy { SearchOrchestrator(context) }
    private val pipelineType = PipelineType.FACT_CHECK
    private val llmOrchestrator by lazy { LlmOrchestrator(context, pipelineType, isForPlanning = false) }
    private val planningLlmOrchestrator by lazy { LlmOrchestrator(context, pipelineType, isForPlanning = true) }
    private val queryPlanner by lazy { QueryPlanningOrchestrator(planningLlmOrchestrator, pipelineType) }

    // citizenServiceData dictionary
    private val citizenServiceData = mapOf(
        "স্মার্ট কার্ড ও জাতীয় পরিচয়পত্র" to listOf("https://nidw.gov.bd/", "https://services.nidw.gov.bd/nid-pub"),
        "জন্ম নিবন্ধন" to listOf("https://orgbdr.gov.bd/", "https://bdris.gov.bd/br/application"),
        "মৃত্যু নিবন্ধন ও সনদ" to listOf("https://orgbdr.gov.bd/", "https://everify.bdris.gov.bd/UDRNVerification"),
        "পাসপোর্ট" to listOf("https://www.epassport.gov.bd/", "http://www.passport.gov.bd/"),
        "জরুরি প্রত্যয়ন ও সনদ" to listOf("https://bdrcs.org/", "https://www.alfredemergency.org/course/emc-bangladesh"),
        "মুক্তিযোদ্ধা বিষয়ক প্রত্যয়ন ও সংশোধন" to listOf("https://prottoyon.gov.bd/", "https://molwa.gov.bd/", "http://www.bffwt.gov.bd/"),
        "ইউটিলিটি বিল (বিদ্যুৎ,ガス ও পানি)" to listOf("https://ekpay.gov.bd/", "https://nagad.com.bd/services/?service=bill-pay", "https://www.bkash.com/en/products-services/pay-bill/electricity-paybill"),
        "ট্রেড লাইসেন্স বিষয়ক সেবা" to listOf("https://www.etradelicense.gov.bd/", "https://www.bangladeshtradeportal.gov.bd/"),
        "ব্যবসায় সংক্রান্ত সেবা" to listOf("https://www.etradelicense.gov.bd/", "https://www.bangladeshtradeportal.gov.bd/"),
        "ভোক্তা সুরক্ষা ও অভিযোগ" to listOf("https://dncrp.portal.gov.bd/", "https://dncrp.com/"),
        "মোটরযানের নিবন্ধন ও লাইসেন্স" to listOf("https://brta.gov.bd/", "https://bsp.brta.gov.bd/"),
        "কর ও রাজস্ব বিষয়ক সেবা" to listOf("https://nbr.gov.bd/", "https://etaxnbr.gov.bd/"),
        "ভূমি সেবা" to listOf("http://www.minland.gov.bd/", "https://land.gov.bd/", "https://dlrs.gov.bd/")
    )

    private fun getApiKey(): String {
        val prefs = context.getSharedPreferences("khoj_settings", Context.MODE_PRIVATE)
        val customKey = prefs.getString("gemini_api_key", "")
        return if (!customKey.isNullOrEmpty()) customKey else BuildConfig.GEMINI_API_KEY
    }

    private fun getLangSearchKey2(): String {
        val prefs = context.getSharedPreferences("khoj_settings", Context.MODE_PRIVATE)
        val customKey = prefs.getString("langsearch_api_key_2", "")
        if (!customKey.isNullOrEmpty()) return customKey.trim().removeSurrounding("\"").removeSurrounding("'")
        return try {
            val key = BuildConfig.LANGSEARCH_API_KEY_2
            key.trim().removeSurrounding("\"").removeSurrounding("'")
        } catch (_: Exception) {
            "sk-5b15a8cdeeac40f1bf46a1148d129373"
        }
    }

    // 1. LangSearch implementation using LANGSEARCH_API_KEY_2
    suspend fun searchLangSearch(query: String, apiKey: String, maxResults: Int = 5): List<KhojChatSource> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey.startsWith("MY_") || apiKey.contains("PLACEHOLDER")) return@withContext emptyList()
        val jsonMediaType = "application/json".toMediaType()
        val requestBody = JSONObject().apply {
            put("query", query)
            put("freshness", "noLimit")
            put("summary", true)
            put("count", maxResults)
        }
        val request = Request.Builder()
            .url("https://api.langsearch.com/v1/web-search")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(body)
                val data = root.optJSONObject("data") ?: return@withContext emptyList()
                val webPages = data.optJSONObject("webPages") ?: return@withContext emptyList()
                val value = webPages.optJSONArray("value") ?: return@withContext emptyList()

                val sources = mutableListOf<KhojChatSource>()
                val seenUrls = mutableSetOf<String>()
                for (i in 0 until value.length()) {
                    val item = value.optJSONObject(i) ?: continue
                    val url = item.optString("url", "")
                    val title = item.optString("name", "ওয়েব তথ্যসূত্র")
                    val snippet = item.optString("summary", item.optString("snippet", ""))
                    if (url.isNotBlank() && !seenUrls.contains(url.lowercase())) {
                        seenUrls.add(url.lowercase())
                        sources.add(KhojChatSource(title = title, url = url, snippet = snippet))
                    }
                }
                sources
            }
        } catch (e: Exception) {
            Log.w(tag, "LangSearch API call failed: ${e.message}")
            emptyList()
        }
    }

    // 2. SearXNG fallback search reusing SearxngHelper.baseUrls
    suspend fun searchSearXNG(query: String): List<KhojChatSource> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val urlsToTry = SearxngHelper.baseUrls
        val allSources = mutableListOf<KhojChatSource>()
        val seenUrls = mutableSetOf<String>()

        for (baseUrl in urlsToTry) {
            val urlStr = "$baseUrl/search?q=$encodedQuery&format=json"
            val request = Request.Builder()
                .url(urlStr)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "KhojChat/1.0")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val root = JSONObject(body)
                        val resultsArr = root.optJSONArray("results")
                        if (resultsArr != null && resultsArr.length() > 0) {
                            for (i in 0 until resultsArr.length()) {
                                val item = resultsArr.optJSONObject(i) ?: continue
                                val itemUrl = item.optString("url", "")
                                val title = item.optString("title", "ওয়েব তথ্যসূত্র")
                                val snippet = item.optString("content", item.optString("snippet", ""))
                                if (itemUrl.isNotBlank() && !seenUrls.contains(itemUrl.lowercase())) {
                                    seenUrls.add(itemUrl.lowercase())
                                    allSources.add(KhojChatSource(title = title, url = itemUrl, snippet = snippet))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "SearXNG URL $baseUrl failed: ${e.message}")
            }
            if (allSources.isNotEmpty()) break
        }
        allSources
    }

    data class GeminiChatResult(
        val text: String,
        val sources: List<KhojChatSource>
    )

    private suspend fun callGeminiApi(
        apiKey: String,
        contentsArray: JSONArray,
        systemInstruction: String,
        temperature: Double,
        useGoogleSearchTool: Boolean
    ): GeminiChatResult? = withContext(Dispatchers.IO) {
        val jsonMediaType = "application/json".toMediaType()
        val requestBody = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature)
            })
            put("safetySettings", JSONArray().apply {
                val categories = listOf(
                    "HARM_CATEGORY_HARASSMENT",
                    "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "HARM_CATEGORY_DANGEROUS_CONTENT"
                )
                categories.forEach { cat ->
                    put(JSONObject().apply {
                        put("category", cat)
                        put("threshold", "BLOCK_NONE")
                    })
                }
            })
            if (useGoogleSearchTool) {
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("googleSearch", JSONObject())
                    })
                })
            }
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstruction) })
                })
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.w(tag, "Gemini API HTTP ${response.code}: $errBody")
                    return@withContext null
                }

                val bodyString = response.body?.string() ?: return@withContext null
                val root = JSONObject(bodyString)
                val candidates = root.optJSONArray("candidates") ?: return@withContext null
                if (candidates.length() == 0) return@withContext null

                val candidate = candidates.optJSONObject(0) ?: return@withContext null
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")

                val sb = StringBuilder()
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i)
                        val pText = part?.optString("text", "") ?: ""
                        sb.append(pText)
                    }
                }
                val rawText = sb.toString().trim()
                if (rawText.isEmpty()) return@withContext null

                val sources = mutableListOf<KhojChatSource>()
                val groundingMetadata = candidate.optJSONObject("groundingMetadata")
                if (groundingMetadata != null) {
                    val groundingChunks = groundingMetadata.optJSONArray("groundingChunks")
                    if (groundingChunks != null) {
                        val seenUrls = mutableSetOf<String>()
                        for (i in 0 until groundingChunks.length()) {
                            val chunk = groundingChunks.optJSONObject(i) ?: continue
                            val web = chunk.optJSONObject("web")
                            if (web != null) {
                                val uri = web.optString("uri", "")
                                val title = web.optString("title", "Grounded Source")
                                if (uri.isNotEmpty() && !seenUrls.contains(uri.lowercase())) {
                                    seenUrls.add(uri.lowercase())
                                    sources.add(KhojChatSource(title = title, url = uri))
                                }
                            }
                        }
                    }
                }
                GeminiChatResult(text = rawText, sources = sources)
            }
        } catch (e: Exception) {
            Log.w(tag, "Gemini call exception: ${e.message}")
            null
        }
    }

    // Auto-detect if general query contains fact-checking keywords
    fun shouldAutoSwitchToFactCheck(query: String): Boolean {
        val keywords = listOf(
            "যাচাই", "সত্য", "মিথ্যা", "ফ্যাক্ট চেক", "debunk", "গুজব", 
            "ভুয়া", "রটেছে", "rumor", "মিথ্যে", "সঠিক কিনা", "সত্যতা"
        )
        return keywords.any { query.contains(it, ignoreCase = true) }
    }

    private val sessionsListKey = "chat_sessions_list"
    private val activeSessionIdKey = "active_session_id"

    // Load list of all sessions
    fun loadSessions(): List<KhojChatSession> {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(sessionsListKey, "") ?: ""
        if (jsonStr.isEmpty()) {
            // Check if legacy history exists
            val legacyHistory = loadHistoryLegacy()
            if (legacyHistory.isNotEmpty()) {
                // Migrate legacy history to a default session
                val firstUserMsg = legacyHistory.find { it.isUser }?.text ?: "Previous Chat"
                val truncatedTitle = if (firstUserMsg.length > 40) firstUserMsg.take(37) + "..." else firstUserMsg
                val defaultSession = KhojChatSession(id = "legacy_session", title = truncatedTitle, timestamp = System.currentTimeMillis())
                saveHistoryForSession(defaultSession.id, legacyHistory)
                saveSessions(listOf(defaultSession))
                setActiveSessionId(defaultSession.id)
                return listOf(defaultSession)
            }
            return emptyList()
        }

        try {
            val sessions = mutableListOf<KhojChatSession>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                sessions.add(
                    KhojChatSession(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
            return sessions.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load sessions list", e)
            return emptyList()
        }
    }

    // Save list of all sessions
    fun saveSessions(sessions: List<KhojChatSession>) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        try {
            val array = JSONArray()
            sessions.forEach { sess ->
                array.put(JSONObject().apply {
                    put("id", sess.id)
                    put("title", sess.title)
                    put("timestamp", sess.timestamp)
                })
            }
            prefs.edit().putString(sessionsListKey, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(tag, "Failed to save sessions list", e)
        }
    }

    // Get current active session ID
    fun getActiveSessionId(): String? {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return prefs.getString(activeSessionIdKey, null)
    }

    // Set active session ID
    fun setActiveSessionId(id: String?) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        if (id == null) {
            prefs.edit().remove(activeSessionIdKey).apply()
        } else {
            prefs.edit().putString(activeSessionIdKey, id).apply()
        }
    }

    // Load messages of legacy history (used for migration)
    private fun loadHistoryLegacy(): List<KhojChatMessage> {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(historyKey, "") ?: ""
        if (jsonStr.isEmpty()) return emptyList()
        return parseMessagesJson(jsonStr)
    }

    // Helper to parse message JSON string
    private fun parseMessagesJson(jsonStr: String): List<KhojChatMessage> {
        try {
            val messages = mutableListOf<KhojChatMessage>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val sourcesList = mutableListOf<KhojChatSource>()
                val sourcesArr = obj.optJSONArray("sources")
                if (sourcesArr != null) {
                    for (j in 0 until sourcesArr.length()) {
                        val sObj = sourcesArr.getJSONObject(j)
                        sourcesList.add(
                            KhojChatSource(
                                title = sObj.getString("title"),
                                url = sObj.getString("url"),
                                snippet = sObj.optString("snippet", "")
                            )
                        )
                    }
                }

                messages.add(
                    KhojChatMessage(
                        id = obj.getString("id"),
                        text = obj.getString("text"),
                        isUser = obj.getBoolean("isUser"),
                        timestamp = obj.getLong("timestamp"),
                        mode = obj.getString("mode"),
                        verdict = if (obj.has("verdict")) obj.getString("verdict") else null,
                        sources = sourcesList,
                        autoSwitched = obj.optBoolean("autoSwitched", false),
                        isError = obj.optBoolean("isError", false),
                        imageUri = if (obj.has("imageUri")) obj.getString("imageUri") else null,
                        promptForEngine = if (obj.has("promptForEngine")) obj.getString("promptForEngine") else null
                    )
                )
            }
            return messages
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse messages json", e)
            return emptyList()
        }
    }

    // Load messages of a specific session
    fun loadHistoryForSession(sessionId: String): List<KhojChatMessage> {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("chat_messages_history_$sessionId", "") ?: ""
        if (jsonStr.isEmpty()) return emptyList()
        return parseMessagesJson(jsonStr)
    }

    // Save messages for a specific session
    fun saveHistoryForSession(sessionId: String, messages: List<KhojChatMessage>) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        try {
            val array = JSONArray()
            messages.forEach { msg ->
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("text", msg.text)
                    put("isUser", msg.isUser)
                    put("timestamp", msg.timestamp)
                    put("mode", msg.mode)
                    if (msg.verdict != null) put("verdict", msg.verdict)
                    put("autoSwitched", msg.autoSwitched)
                    put("isError", msg.isError)
                    if (msg.imageUri != null) put("imageUri", msg.imageUri)
                    if (msg.promptForEngine != null) put("promptForEngine", msg.promptForEngine)

                    val sourcesArr = JSONArray()
                    msg.sources.forEach { s ->
                        sourcesArr.put(JSONObject().apply {
                            put("title", s.title)
                            put("url", s.url)
                            put("snippet", s.snippet)
                        })
                    }
                    put("sources", sourcesArr)
                }
                array.put(obj)
            }
            prefs.edit().putString("chat_messages_history_$sessionId", array.toString()).apply()
        } catch (e: Exception) {
            Log.e(tag, "Failed to save history for session $sessionId", e)
        }
    }

    // Delete a specific session and its messages
    fun deleteSession(sessionId: String) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().remove("chat_messages_history_$sessionId").apply()

        val currentSessions = loadSessions().filter { it.id != sessionId }
        saveSessions(currentSessions)

        if (getActiveSessionId() == sessionId) {
            if (currentSessions.isNotEmpty()) {
                setActiveSessionId(currentSessions.first().id)
            } else {
                setActiveSessionId(null)
            }
        }
    }

    // Load messages from SharedPreferences for currently active session
    fun loadHistory(): List<KhojChatMessage> {
        val activeId = getActiveSessionId() ?: return emptyList()
        return loadHistoryForSession(activeId)
    }

    // Save messages to SharedPreferences for currently active session
    fun saveHistory(messages: List<KhojChatMessage>) {
        val activeId = getActiveSessionId()
        if (activeId != null) {
            saveHistoryForSession(activeId, messages)
        } else {
            // Fail-safe legacy fallback
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            try {
                val array = JSONArray()
                messages.forEach { msg ->
                    val obj = JSONObject().apply {
                        put("id", msg.id)
                        put("text", msg.text)
                        put("isUser", msg.isUser)
                        put("timestamp", msg.timestamp)
                        put("mode", msg.mode)
                        if (msg.verdict != null) put("verdict", msg.verdict)
                        put("autoSwitched", msg.autoSwitched)
                        put("isError", msg.isError)
                        if (msg.imageUri != null) put("imageUri", msg.imageUri)
                        if (msg.promptForEngine != null) put("promptForEngine", msg.promptForEngine)

                        val sourcesArr = JSONArray()
                        msg.sources.forEach { s ->
                            sourcesArr.put(JSONObject().apply {
                                put("title", s.title)
                                put("url", s.url)
                                put("snippet", s.snippet)
                            })
                        }
                        put("sources", sourcesArr)
                    }
                    array.put(obj)
                }
                prefs.edit().putString(historyKey, array.toString()).apply()
            } catch (e: Exception) {
                Log.e(tag, "Failed to save legacy chat history", e)
            }
        }
    }

    // Clear history of active session or all sessions
    fun clearHistory() {
        val activeId = getActiveSessionId()
        if (activeId != null) {
            deleteSession(activeId)
        } else {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit().remove(historyKey).apply()
        }
    }
    // Execute chat prompt with robust search fallbacks and memory
    fun sendChatMessage(
        query: String,
        activeMode: String,
        history: List<KhojChatMessage>,
        responseLanguage: ResponseLanguage = ResponseLanguage.AUTO
    ): Flow<KhojChatState> = flow {
        var currentMode = activeMode
        var autoSwitched = false

        // 1. Auto-switch logic
        if (currentMode == "general" && shouldAutoSwitchToFactCheck(query)) {
            emit(KhojChatState.AutoDetecting)
            currentMode = "fact-check"
            autoSwitched = true
            kotlinx.coroutines.delay(1000)
        }

        emit(KhojChatState.Searching)

        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey.startsWith("MY_") || apiKey.contains("PLACEHOLDER")) {
            emit(KhojChatState.Error("Gemini API Key is not configured. Please configure it in your secrets or Settings."))
            return@flow
        }

        val langSearchKey2 = getLangSearchKey2()

        val languageInstruction = ResponseLanguageManager(context).apply {
            setLanguage(responseLanguage)
        }.getLanguageInstructionPrompt()

        val identityInstruction = """
            Identity & Creator Guidelines:
            - You are 'Khoj AI' (খোঁজ এআই).
            - If anyone asks who you are, what model you are, or who created you, always introduce yourself clearly and proudly as 'খোঁজ এআই' (Khoj AI), created and trained by 'টিম খোঁজ' (Team Khoj).
            - If users want to learn more about you or your creators, provide the official website: [khoj-ai.bd](https://khoj-ai.bd/) (https://khoj-ai.bd/).
            - Maintain sharp conversational memory. Remember facts and questions from earlier in this conversation and reference them when appropriate.
        """.trimIndent()

        // 2. Build system instructions and context
        val baseSystemInstruction = when (currentMode) {
            "citizen-service" -> {
                // Find relevant service resources to ground the prompt
                val matchedServices = citizenServiceData.entries.filter { entry ->
                    query.contains(entry.key, ignoreCase = true) || 
                    entry.key.split(" ").any { query.contains(it, ignoreCase = true) }
                }

                val linksContext = if (matchedServices.isNotEmpty()) {
                    "Here are specific verified Bangladeshi official websites for this topic that you MUST prioritize, display, and link to in your response as markdown links:\n" +
                    matchedServices.joinToString("\n") { "- ${it.key}: " + it.value.joinToString(", ") }
                } else {
                    "Ground your answers to official government services of Bangladesh. Use standard .gov.bd domains for resources and provide them clearly in markdown links."
                }

                "You are an expert citizen service assistant of Bangladesh. Your duty is to explain administrative and public services clearly and helpfully. " +
                "Guide the user step-by-step through the procedure (fees, documents needed, step by step instructions). " +
                "Always provide official website links as markdown clickable links. " +
                "Do NOT make up URLs; rely strictly on .gov.bd domains. " +
                "Context for the current query:\n$linksContext"
            }
            "fact-check" -> {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val currentDateStr = sdf.format(java.util.Date())
                "You are 'Khoj AI' (খোঁজ এআই), a highly professional, objective, and analytical fact-checking assistant for Bangladesh.\n" +
                "TEMPORAL GROUNDING & EVIDENCE PRIMACY:\n" +
                "1. Reference Date: $currentDateStr. You are evaluating this in the real world as of this date. Events and reports up to this date reflect actual reality—never dismiss them as future or speculative.\n" +
                "2. When retrieved search evidence documents verified facts, adopt them as ground truth. Never contradict credible search evidence based on outdated internal pre-trained memory.\n" +
                "3. Do NOT recite reference dates or system instructions. Write naturally and seamlessly.\n" +
                "Analyze the user's claim or rumour carefully. " +
                "Identify the verdict clearly at the top. The verdict MUST strictly be ONE of these three: 'সত্য' (True), 'অসত্য' (False), or 'বিভ্রান্তিকর' (Misleading). " +
                "Review the evidence rigorously based on web results, citing trusted news sources or fact-checking bodies (like Rumor Scanner, Fact Watch, Boom BD, etc.)."
            }
            else -> {
                "You are 'Khoj Chat' (খোঁজ চ্যাট), a helpful, friendly, and knowledgeable AI assistant for Bangladesh. " +
                "You answer questions clearly, politely, and accurately. " +
                "Provide rich explanations, examples, and format your response beautifully using markdown."
            }
        }
        
        val fullSystemInstruction = "$baseSystemInstruction\n\n$identityInstruction\n\n$languageInstruction"

        // Build clean contents array (up to 16 turns for deep memory)
        val contentsArray = JSONArray()
        val cleanHistory = history.filterNot { it.isError }
            .filter { it.text.isNotBlank() }
            .let { list ->
                if (list.isNotEmpty() && list.last().isUser && list.last().text.trim() == query.trim()) {
                    list.dropLast(1)
                } else {
                    list
                }
            }
            .takeLast(16)

        cleanHistory.forEach { hMsg ->
            contentsArray.put(JSONObject().apply {
                put("role", if (hMsg.isUser) "user" else "model")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", hMsg.text) })
                })
            })
        }

        val promptText = if (currentMode == "fact-check") {
            "FACT CHECK CLAIM: \"$query\"\n\nPlease analyze this claim and give a structured explanation, stating the Verdict clearly."
        } else {
            query
        }

        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", promptText) })
            })
        })

        val temperature = if (currentMode == "fact-check") 0.1 else 0.7

        fun appendSourcesToSystemInstruction(base: String, sources: List<KhojChatSource>): String {
            if (sources.isEmpty()) return base
            val sb = java.lang.StringBuilder(base)
            sb.append("\n\n--- লাইভ ওয়েব অনুসন্ধান থেকে প্রাপ্ত তথ্য (Live Web Sources) ---\n")
            sources.take(6).forEachIndexed { idx, s ->
                val effectiveEvidence = if (!s.content.isNullOrBlank()) s.content.take(2000) else s.snippet
                sb.append("[${idx + 1}] ${s.title}\nলিংক: ${s.url}\nতথ্য: $effectiveEvidence\n\n")
            }
            sb.append("ওপরের লাইভ ওয়েব তথ্য ব্যবহার করে প্রশ্নের উত্তর দিন। তথ্যসূত্র থাকলে উল্লেখ করুন।")
            return sb.toString()
        }

        emit(KhojChatState.Generating)

        var finalResult: GeminiChatResult? = null

        if (currentMode == "general") {
            // Mode "খোঁজ চ্যাট":
            // 1. Gemini Google Search Grounding
            try {
                finalResult = callGeminiApi(apiKey, contentsArray, fullSystemInstruction, temperature, useGoogleSearchTool = true)
            } catch (e: Exception) {
                Log.w(tag, "Gemini Google Search Grounding failed: ${e.message}")
            }

            // 2. Fallback 1: LangSearch 2
            if (finalResult == null) {
                Log.d(tag, "Fallback 1: Querying LangSearch 2")
                val langSources = searchLangSearch(query, langSearchKey2)
                if (langSources.isNotEmpty()) {
                    val groundedInstruction = appendSourcesToSystemInstruction(fullSystemInstruction, langSources)
                    val res = callGeminiApi(apiKey, contentsArray, groundedInstruction, temperature, useGoogleSearchTool = false)
                    if (res != null) {
                        finalResult = res.copy(sources = langSources)
                    }
                }
            }

            // 3. Fallback 2: SearXNG (reusing SearxngHelper.baseUrls)
            if (finalResult == null) {
                Log.d(tag, "Fallback 2: Querying SearXNG clusters")
                val searxSources = searchSearXNG(query)
                if (searxSources.isNotEmpty()) {
                    val groundedInstruction = appendSourcesToSystemInstruction(fullSystemInstruction, searxSources)
                    val res = callGeminiApi(apiKey, contentsArray, groundedInstruction, temperature, useGoogleSearchTool = false)
                    if (res != null) {
                        finalResult = res.copy(sources = searxSources)
                    }
                }
            }

            // 4. Safety net: Direct generation via multi-tier LLM cascade
            if (finalResult == null) {
                Log.d(tag, "Safety net: Direct generation via LLM Orchestrator")
                try {
                    val fallbackReq = LlmRequest(
                        systemPrompt = fullSystemInstruction,
                        userPrompt = query,
                        jsonOutput = false,
                        temperature = temperature
                    )
                    val generated = llmOrchestrator.generateFallback(fallbackReq)
                    if (generated.isNotBlank()) {
                        finalResult = GeminiChatResult(text = generated, sources = emptyList())
                    }
                } catch (_: Exception) {
                    finalResult = callGeminiApi(apiKey, contentsArray, fullSystemInstruction, temperature, useGoogleSearchTool = false)
                }
            }
        } else {
            // Mode "নাগরিক সেবা" (Citizen Service) & "যাচাই" (Fact Check):
            if (currentMode == "fact-check") {
                Log.d(tag, "Fact-Check: Querying QueryPlanningOrchestrator and SearchOrchestrator (Bilingual + Full RAG)")
                var factCheckSources = mutableListOf<KhojChatSource>()
                try {
                    // 1. Query Planning with bilingual query generation
                    val plan = queryPlanner.plan(query, QueryPlanConfig(isFactCheck = true))
                    val qBn = plan.queryBn ?: plan.queries.firstOrNull { it.query.any { c -> c in '\u0980'..'\u09FF' } }?.query
                    val qEn = plan.queryEn ?: plan.queries.firstOrNull { it.query.none { c -> c in '\u0980'..'\u09FF' } }?.query

                    val primaryQuery = qBn ?: plan.queries.firstOrNull()?.query ?: query
                    val secondaryQuery = qEn ?: (if (primaryQuery != query) query else "")

                    // 2. Bilingual Search with Cloudflare 69 keys + fallback cascade
                    val rawSources = if (secondaryQuery.isNotBlank() && secondaryQuery != primaryQuery) {
                        searchOrchestrator.executeBilingualFallbackSearch(primaryQuery, secondaryQuery) {}
                    } else {
                        searchOrchestrator.executeFallbackSearch(primaryQuery) {}
                    }

                    // 3. Rerank
                    val rankedSources = searchOrchestrator.rerankBilingualSources(primaryQuery, secondaryQuery, rawSources)

                    // 4. Content Enrichment for top 7 URLs
                    val topUrls = rankedSources.take(7).map { it.url }
                    if (topUrls.isNotEmpty()) {
                        try {
                            val contentMap = searchOrchestrator.fetchContentForUrls(topUrls)
                            rankedSources.forEach { s ->
                                contentMap[s.url]?.let { c ->
                                    if (c.isNotBlank()) s.content = c
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(tag, "Content extraction failed in KhojChatEngine: ${e.message}")
                        }
                    }

                    factCheckSources = rankedSources.map { s ->
                        KhojChatSource(title = s.title, url = s.url, snippet = s.snippet, content = s.content)
                    }.toMutableList()

                    val citations = CitationEngine.mapToCitations(rankedSources)
                    val evidencePrompt = CitationEngine.buildEvidencePrompt(citations)

                    // 5. Fact-checking synthesis using multi-tier LLM cascade
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val currentDateStr = sdf.format(java.util.Date())

                    val factCheckSysPrompt = FactCheckPrompt.getSystemPrompt(currentDateStr) +
                        "\n\n$identityInstruction\n\n$languageInstruction\n\n" +
                        "STRUCTURE REQUIREMENT FOR KHOJ CHAT:\n" +
                        "1. Clearly state the verdict on the first line: **সত্য** (True), **অসত্য** (False), or **বিভ্রান্তিকর** (Misleading).\n" +
                        "2. Follow with a clear, concise summary paragraph explaining the fact-check verdict.\n" +
                        "3. Then provide the detailed verification findings with inline citations [1], [2] referencing the evidence found."

                    val factCheckUserPrompt = "FACT CHECK CLAIM: \"$query\"\n\n" +
                        "EVIDENCE FOUND:\n$evidencePrompt\n\n" +
                        "Analyze the evidence and provide the fact check response."

                    val llmReq = LlmRequest(
                        systemPrompt = factCheckSysPrompt,
                        userPrompt = factCheckUserPrompt,
                        jsonOutput = false,
                        temperature = 0.1
                    )

                    val generatedResponse = llmOrchestrator.generateFallback(llmReq)
                    if (generatedResponse.isNotBlank()) {
                        finalResult = GeminiChatResult(
                            text = generatedResponse,
                            sources = factCheckSources
                        )
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Full factcheck pipeline in KhojChat failed, falling back: ${e.message}")
                }
            }

            // 1. Fallback / Citizen Service: LangSearch 2
            if (finalResult == null) {
                Log.d(tag, "Citizen/Fact-Check Fallback 1: Querying LangSearch 2")
                val langSources = searchLangSearch(query, langSearchKey2)
                if (langSources.isNotEmpty()) {
                    val groundedInstruction = appendSourcesToSystemInstruction(fullSystemInstruction, langSources)
                    val res = callGeminiApi(apiKey, contentsArray, groundedInstruction, temperature, useGoogleSearchTool = false)
                    if (res != null) {
                        finalResult = res.copy(sources = langSources)
                    }
                }
            }

            // 2. Fallback 2: Gemini Google Search Grounding
            if (finalResult == null) {
                Log.d(tag, "Citizen/Fact-Check Fallback 2: Trying Gemini Google Search Grounding")
                try {
                    finalResult = callGeminiApi(apiKey, contentsArray, fullSystemInstruction, temperature, useGoogleSearchTool = true)
                } catch (e: Exception) {
                    Log.w(tag, "Gemini Google Search Grounding failed: ${e.message}")
                }
            }

            // 3. Fallback 3: SearXNG (reusing SearxngHelper.baseUrls)
            if (finalResult == null) {
                Log.d(tag, "Citizen/Fact-Check Fallback 3: Querying SearXNG clusters")
                val searxSources = searchSearXNG(query)
                if (searxSources.isNotEmpty()) {
                    val groundedInstruction = appendSourcesToSystemInstruction(fullSystemInstruction, searxSources)
                    val res = callGeminiApi(apiKey, contentsArray, groundedInstruction, temperature, useGoogleSearchTool = false)
                    if (res != null) {
                        finalResult = res.copy(sources = searxSources)
                    }
                }
            }

            // 4. Safety net: Direct generation via multi-tier LLM cascade
            if (finalResult == null) {
                Log.d(tag, "Citizen/Fact-Check Safety net: Direct generation via LLM Orchestrator")
                try {
                    val fallbackReq = LlmRequest(
                        systemPrompt = fullSystemInstruction,
                        userPrompt = query,
                        jsonOutput = false,
                        temperature = temperature
                    )
                    val generated = llmOrchestrator.generateFallback(fallbackReq)
                    if (generated.isNotBlank()) {
                        finalResult = GeminiChatResult(text = generated, sources = emptyList())
                    }
                } catch (_: Exception) {
                    finalResult = callGeminiApi(apiKey, contentsArray, fullSystemInstruction, temperature, useGoogleSearchTool = false)
                }
            }
        }

        if (finalResult == null) {
            emit(KhojChatState.Error("দুঃখিত, তথ্য অনুসন্ধানে সমস্যা হয়েছে। অনুগ্রহ করে আবার চেষ্টা করুন।"))
            return@flow
        }

        val rawText = finalResult.text
        var detectedVerdict: String? = null
        if (currentMode == "fact-check") {
            detectedVerdict = when {
                rawText.contains("অসত্য", ignoreCase = true) || rawText.contains("মিথ্যা", ignoreCase = true) || rawText.contains("ভুয়া", ignoreCase = true) || rawText.contains(" গুজব", ignoreCase = true) || rawText.contains("FALSE", ignoreCase = true) -> "false"
                (rawText.contains("সত্য", ignoreCase = true) && !rawText.contains("আংশিক", ignoreCase = true) && !rawText.contains("অসত্য", ignoreCase = true)) || rawText.contains("TRUE", ignoreCase = true) -> "true"
                else -> "misleading" // Strict 3 verdicts: true, false, misleading
            }
        }

        emit(KhojChatState.Success(
            responseText = rawText,
            verdict = detectedVerdict,
            sources = finalResult.sources,
            autoSwitchedToFactCheck = autoSwitched
        ))
    }
}

