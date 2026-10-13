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
    val content: String? = null,
    val date: String? = null
)

data class KhojChatSession(
    val id: String,
    val title: String,
    val timestamp: Long,
    val isPinned: Boolean = false
)

sealed class KhojChatState {
    object Idle : KhojChatState()
    object AutoDetecting : KhojChatState()
    object Searching : KhojChatState()
    object Generating : KhojChatState()
    data class DeepSearching(val stageText: String) : KhojChatState()
    data class AiTextDetecting(val stageText: String) : KhojChatState()
    data class AiImageDetecting(val stageText: String) : KhojChatState()
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

    private val urlAnalyzer by lazy { UrlAnalyzer() }

    fun getSearchWorkerUrl(): String {
        val defaultUrl = "https://khoj-search-api.mahatirtusher.workers.dev"
        val prefs = context.getSharedPreferences("khoj_settings", Context.MODE_PRIVATE)
        val custom = prefs.getString("khoj_search_api_url", null)
        if (!custom.isNullOrBlank()) return custom.trimEnd('/')
        return try {
            val buildConfigUrl = BuildConfig.KHOJ_SEARCH_API_URL
            if (!buildConfigUrl.isNullOrBlank()) buildConfigUrl.trimEnd('/') else defaultUrl
        } catch (_: Exception) {
            defaultUrl
        }
    }

    suspend fun searchTinyFish(workerUrl: String, query: String, maxResults: Int = 8): List<KhojChatSource> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$workerUrl/tinyfish/search?q=$encodedQuery&max_results=$maxResults"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(body)
                val resultsArr = root.optJSONArray("results") ?: return@withContext emptyList()
                val sources = mutableListOf<KhojChatSource>()
                for (i in 0 until resultsArr.length()) {
                    val item = resultsArr.optJSONObject(i) ?: continue
                    val itemUrl = item.optString("url", "")
                    val title = item.optString("title", "ওয়েব ফলাফল")
                    val snippet = item.optString("snippet", item.optString("content", ""))
                    if (itemUrl.isNotBlank() && snippet.isNotBlank()) {
                        sources.add(KhojChatSource(title = title, url = itemUrl, snippet = snippet))
                    }
                }
                sources
            }
        } catch (e: Exception) {
            Log.w(tag, "searchTinyFish failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchDegoog(workerUrl: String, query: String, maxResults: Int = 8): List<KhojChatSource> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$workerUrl/degoog?q=$encodedQuery&max_results=$maxResults"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(body)
                val resultsArr = root.optJSONArray("results") ?: return@withContext emptyList()
                val sources = mutableListOf<KhojChatSource>()
                for (i in 0 until resultsArr.length()) {
                    val item = resultsArr.optJSONObject(i) ?: continue
                    val itemUrl = item.optString("url", "")
                    val title = item.optString("title", "ওয়েব ফলাফল")
                    val snippet = item.optString("snippet", item.optString("content", ""))
                    if (itemUrl.isNotBlank() && snippet.isNotBlank()) {
                        sources.add(KhojChatSource(title = title, url = itemUrl, snippet = snippet))
                    }
                }
                sources
            }
        } catch (e: Exception) {
            Log.w(tag, "searchDegoog failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchWithTinyFishAndDegoogFallback(query: String): List<KhojChatSource> = withContext(Dispatchers.IO) {
        val workerUrl = getSearchWorkerUrl()
        // 1. Primary: /tinyfish/search
        val tinyFishSources = searchTinyFish(workerUrl, query, 8)
        if (tinyFishSources.isNotEmpty()) {
            return@withContext tinyFishSources
        }
        // 2. Fallback: /degoog
        val degoogSources = searchDegoog(workerUrl, query, 8)
        if (degoogSources.isNotEmpty()) {
            return@withContext degoogSources
        }
        emptyList()
    }

    fun sanitizeChatResponse(rawText: String, userQuery: String): String {
        var cleaned = rawText.trim()

        // 1. Strip religious & sectarian greeting prefixes from the very beginning of the response
        val religiousGreetingRegex = Regex(
            """^(?:[\s*#_]*(?:নমস্কার|সালাম|আসসালামু\s*আলাইকুম|আদাব|নমস্কার\s*বন্ধুরা|সালাম\s*বন্ধুরা)[!।,\s*#_]*)+""",
            RegexOption.IGNORE_CASE
        )
        cleaned = cleaned.replace(religiousGreetingRegex, "").trim()

        // 2. Strip unsolicited self-introduction boilerplate if user didn't ask identity
        val asksIdentity = listOf("তুমি কে", "তোমার নাম", "কে তৈরি করেছে", "কে বানিয়েছে", "who are you", "what is your name", "who created you")
            .any { userQuery.contains(it, ignoreCase = true) }

        if (!asksIdentity) {
            val unsolicitedIntroRegex = Regex(
                """^(?:[\s*#_]*আমি\s+খোঁজ\s+(?:এআই|চ্যাট)[^।!\n]*[।!\n]+[\s]*)""",
                RegexOption.IGNORE_CASE
            )
            cleaned = cleaned.replace(unsolicitedIntroRegex, "").trim()
        }

        return cleaned
    }

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
                    val date = item.optString("datePublished", item.optString("dateLastCrawled", "")).takeIf { it.isNotBlank() }
                    if (url.isNotBlank() && !seenUrls.contains(url.lowercase())) {
                        seenUrls.add(url.lowercase())
                        sources.add(KhojChatSource(title = title, url = url, snippet = snippet, date = date))
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
                                val date = item.optString("publishedDate", item.optString("date", "")).takeIf { it.isNotBlank() }
                                if (itemUrl.isNotBlank() && !seenUrls.contains(itemUrl.lowercase())) {
                                    seenUrls.add(itemUrl.lowercase())
                                    allSources.add(KhojChatSource(title = title, url = itemUrl, snippet = snippet, date = date))
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

    companion object {
        // Comprehensive auto web-search trigger keywords across Bengali, English, and Banglish (930+ keywords & phrases)
        val webSearchTriggerKeywords = listOf(
            // --- 1. Fact-check, Verification, Debunk, References & Sources (বাংলা & English) ---
            "যাচাই", "সত্য", "মিথ্যা", "ফ্যাক্ট চেক", "ফ্যাক্টচেক", "ফেক্টচেক", "ফেক্ট চেক", "চেক", "চ্যাক",
            "debunk", "গুজব", "ভুয়া", "ভুয়া", "রটেছে", "rumor", "মিথ্যে", "সঠিক কিনা", "সত্যতা",
            "আসল নাকি নকল", "সত্যি নাকি মিথ্যা", "ভুয়া খবর", "ভুয়া খবর", "ঘটনাটি সত্যি", "খবরটি সত্যি",
            "দাবি করা হচ্ছে", "দাবি উঠেছে", "ফাঁস", "লিক", "নকল", "ধোঁকা", "কারসাজি", "ডিপফেক",
            "প্রতারণা", "জালিয়াতি", "ভুয়া ভিডিও", "ভুয়া ভিডিও", 
            "ভেরিফাই", "ভ্যারিফাই", "ভ্যারিফাইড", "ভেরিফাইড", "verified", "varify",
            "shottota", "shotyota", "sotyota", "sathyata", "shathyata", "jachai",
            "খোঁজ ফ্যাক্টচেক", "খোঁজ ফ্যাক্টচেকার", "খোঁজ", "খোঁজ লাগাও", "খুজ লাগাও", "খোজ লাগাও",
            "khoj factcheck", "khoj ai", "khoj lagao", "khoj lagau", "khuj lagau", "khoj lagaw",
            "fact check", "factcheck", "fectcheck", "fect check", "check", "chek", "khuj", "khoz", "khuz",
            "fake news", "is it true", "real or fake", "hoax", "misinformation", "disinformation", 
            "scam", "fraud", "deepfake", "manipulated", "leaked", "leak", "claim check",
            "খন্ডন", "খণ্ডন", "ডিবাংক", "khondon",
            "reference", "ref", "refs", "রেফারেন্স", "র্যাফারেন্স", "র্যাফার্যান্স", "রেফার্যান্স", "রেফ",
            "তথ্যপ্রমাণ", "তথ্য", "প্রমাণ", "যুক্তিপ্রমাণ", "তথ্যসমৃদ্ধ", "informative", "information", "info", "তত্ত্ব",

            // --- 2. Religious Scriptures, Fatwa, Fiqh & Theological References ---
            "হাদীস ঘেঁটে", "হাদিস ঘেটে", "হাদীস ঘেটে", "হাদিসে", "হাদীসে", "কোরানে", "কোরআনে", "কুরানে",
            "হাদিস", "হাদীস", "হাদিছ", "হাদীছ", "হাদিছে", "হাদীছে",
            "in hadith", "hadis", "hadith", "quran", "koran", "kuran",
            "fatwa", "fotwa", "fotua", "fotoa", "fotowa", "ফতোয়া", "ফতুয়া",
            "হারাম", "হালাল", "শিরক", "কুফর", "কাফির", "kafir", "kaafir",
            "আয়াত", "ayah", "ayat", "মাসলা", "মাসালা", "মাছালা",

            // --- 3. People, Profiles, Biographies, Leaders, Entities & 1971 Liberation War ---
            "মাহাথির আহমেদ তুষার", "মাহাথির", "মাহাতির", "আহমেদ", "তুষার", 
            "mahatir ahmed tusher", "mahathir ahmed tushar", "tusher", "mahatir", "ahmed", 
            "নিফাত নাফসীন নিসা", "নিফাত", "নাফসীন", "নিসা", "nifat nafsin nisa", 
            "banglaturing", "progga", "progga ai", "প্রজ্ঞা এআই", "this github repo",
            "github repo", "git repo", "github repository", "git repository", "git rep",
            "গিটহাব রিপো", "গিটহাব রিপোজিটরি", "গিটহাব রিপোজিটোরি", "গিট রিপো", "গিট রিপোজিটরি", 
            "গীটহাব রিপো", "গীটহাব রিপোজিটরি", "গীটহাব রিপোজিটোরি", "গীট রিপো", "গীট রিপোজিটরি",
            "জীবনী", "জীবনি", "জিবনি", "jiboni", "biography", "about his life", "about her life",
            "who is", "look him up", "look her up", "seek about", "run this name", "এই নামটা কার", "লোকটা কে",
            "হাদি", "হাদী", "ওসমান হাদি", "ওসমান হাদী", "osman hadi", "jaber", "inquilab manch", "ইনকিলাব মঞ্চ",
            "হাসিনা", "ফ্যাসিস্ট", "ফেসিস্ট", "fascist", "তারেক রহমান", "তারেক জিয়া",
            "বঙ্গবন্ধু", "শেখ", "মুজিব", "মুজিবুর", "sheikh", "mujib", "rahman", "mujibur", "ziaur rahman",
            "andolon", "andolone", "আন্দোলন", "আন্দোলনে", "মুভমেন্ট", "movement",
            "১৯৭১", "1971", "গণহত্যা", "মুক্তিযুদ্ধ", "মুক্তি যুদ্ধ", "একাত্তর", "একাত্তরের",
            "muktijuddho", "mukti juddho", "moktijoddo", "liberation war",
            "বুদ্ধিজীবী", "বুদ্ধিজীবি", "বুদ্ধিজিবী", "বুদ্ধিজিবি",
            "জেনোসাইড", "জেনসাইড", "genocide", "massacre", "ম্যাসাকার",

            // --- 4. Law, Constitution, Governance, Crimes & Court Cases ---
            "সংবিধান", "বিধিনিষেদ", "আইনী", "আইনি", "law", "constitute", "আইন", "বিধান", "বিদান", "ফয়সালা", "বন্দোবস্ত", "বন্দবস্ত",
            "bidhan", "bidhaan", "bidan", "ain", "foysala",
            "হত্যা", "হত্যামামলা", "মামলা", "lawsuit", "sue",
            "sorkari", "সরকারি", "সরকারী",

            // --- 5. Trends & Viral Phenomena ---
            "trandy", "trendy", "trand", "trend", 
            "ট্র্যান্ড", "ট্র্যানড", "ট্রেনড", "ট্রেন্ড", "ট্রেন্ডি", "ট্র্যান্ডি", "ট্র্যানডি", "ট্রেনডি",

            // --- 6. Academic, Research, Publications, Media, Music & Science ---
            "books", "works", "রিলিজ", "release", "released", "mukti pabe", "mukti", "rilis", "রিলিস", 
            "শুভমুক্তি", "reliz", "cutting edge", "state of art", "state-of-art", "sota", 
            "literature survey", "related works",
            "publish", "published", "প্রকাশিত", "prokashito", "prokasito", "পাব্লিশ", "পাবলিশ", "পাবলিস",
            "প্রকাশ", "প্রকাস", "prokas", "prokash", "publication", "prokashoni", "prokashona", "prokashon",
            "প্রকাশনা", "প্রকাশনী", "প্রকাশন", "জার্নাল", "journal", "রকমারি", "রকমারী", "rokomari", 
            "book website", "book sites",
            "pdf download link", "download link", "pdf", "doc file", "api key", "api", "api doc", "documentation",
            "anime", "ai models", "ai model", "model", "parameters", "ai", "এআই মডেল", "rag",
            "best courses", "best", "tutorial", "টিউটোরিয়াল", "টিউটরিয়াল", "কোর্স", "course",
            "suggest", "সাজেস্ট", "তাঁর বইয়ে", "in his book", "in her book",
            "nice songs", "nice song",
            "বিবর্তন", "জিন", "বৈজ্ঞানিক", "papers", "paper", "পেপারস", "পেপার",
            "জাতীয় দৈনিক", "ন্যাশনাল ডেইলি", "পত্রিকা", "খবরের কাগজ", "খবরের ওয়েবসাইট", "খবরের সাইট", "khoborer site",
            "potrika", "patrika", "ম্যাগাজিন", "magazine", "monthly", "quarterly", "ত্রৈমাসিক", "মেগাজিন", "megazine",
            "issue", "latest issue", "এই সংখ্যা", "সংখ্যায়",
            "next movie", "next song", "next book", "next volume", "next season", "next episode",
            "নেক্সট বই", "নেক্সট গান", "নেক্সট ভলিউম", "নেক্সট সিজন", "নেক্সট এপিসোড", 
            "পরবর্তী বই", "পরবর্তী গান", "পরবর্তী ভলিউম", "পরবর্তী সিজন", "পরবর্তী এপিসোড",
            "সেরা সিনেমা", "সেরা মুভি", "সেরা বই", "সেরা পর্ব", "সেরা এপিসোড",
            "shera movies", "shera movie", "sera movie", "sera cinema", "shera cinema", "sera ep", "shera episode",

            // --- 6. News, Real-Time, Timeliness & Happening Now (বাংলা & English) ---
            "বর্তমান", "বর্তমানে", "বত্তমান", "বত্তমানে", "at present", "present",
            "today", "next day", "the day after tomorrow", "yesterday", "tomorrow",
            "পরশু", "আগামী পরশু", "গত পরশু", "আগামী", "আগামি", "গত",
            "এই মুহুত্তে", "এইমুহুত্তে", "এই মূহুত্তে", "এইমূহুত্তে",
            "ব্রেকিং", "ব্রেকিং নিউজ", "তাজা খবর", "তাজা সংবাদ", "আজকের খবর", "আজকের সংবাদ", 
            "সর্বশেষ খবর", "সর্বশেষ সংবাদ", "সাম্প্রতিক", "সম্প্রতি", "এইমাত্র", "এইমাত্র পাওয়া", 
            "আজকাল", "আজকে", "আজকের", "আজ ", "আজকের দিন", "ঘটেছে", "কবে", "কোনদিন", "কোন দিন",
            "কখন", "কবে আসবে", "কখন আসবে", "কখনকার", "কি ঘটতেসে", "কী ঘটতেসে", "কি ঘটতেছে", "কী ঘটতেছে", 
            "কী ঘটছে", "কি ঘটসে", "এই মুহুর্তে", "এইমুহুর্তে", "এই মূহুর্তে", "এইমূহুর্তে",
            "কীভাবে হবে", "কিভাবে হবে", "কত দিন পর হবে", "কতোদিন পর হবে",
            "সর্বশেষ আপডেট কি", "লাস্ট", "সর্বশেষ", "সবশেষ", "আপডেট", "আপদেত", "সর্বশেষ সংস্করণ", 
            "লেটেস্ট", "ল্যাটেস্ট", "আপটুডেট", "আপ টু ডেট", "আপটু ডেট", "আপটুডেইট", "আপ টু ডেইট",
            "এই বছর", "এই মাসে", "এই মাস", "গত মাস", "লাস্ট মান্থ", "গত সপ্তাহে", "এই সপ্তাহে", "এই সপ্তাহ", "গত সপ্তাহ", "এই উইক", "গত উইক",
            "লাইভ খবর", "লাইভ আপডেট", "সরাসরি খবর", "নতুন খবর", "খবরের আপডেট",

            // --- 7. Explicit Web Search & Crawl Directives (বাংলা & English) ---
            "ওয়েব সার্চ করো", "ওয়েব সার্চ করো", "ওয়েব সার্চ", "ওয়েব সার্চ", "ওয়েব", "ওয়েব", 
            "সার্চ", "গুগল", "নেট সার্চ", "ইন্টারনেট ঘাঁটো", "জুলাই আন্দোলন", "ইন্টারনেট ঘাটো", "ইন্টারনেট ঘেটে", 
            "ইন্টারনেট ঘেঁটে", "নেট ঘাঁটো", "নেট ঘেটে", "নেট ঘাটো", "নেট ঘাইটা", "নেট ঘাঁইটা", 
            "খুঁজো", "খোঁজো", "খুঁজে জানাও", "খুঁজে দেখো", "খোঁজে দেখো", "khuija", "জানাও",
            "khujo", "khuje dao", "khuje daw", "khuje", "খুঁজে", "খুজে", "খোঁজে", "খোজে", "এনে", "ene", "aina", "আইনা",
            "কোথায়", "কই", "পাবো", "khoje", "koi pabo", "pabo", "pbo", "kothay", "pawa jabe", "paoa jbe", "paoa jabe",
            "ইন্টারনেট", "এভাইলেবল", "উপলব্ধ", "available", "online", "অনলাইনে", "অনলাইন", "ওনলাইন", 
            "এভেলেবল", "এবেলেবল", "abalabol", "unavailable", "availability", "এভাইলেবিলিটি", "এবেলেবিলিটি",
            "next update", "current", "recent", "কারেন্ট", "রিসেন্ট",
            "web search", "web", "search", "scrape", "internet", "websearch", "google", 
            "net search", "search once", "look it up", "chatgpt this", "find it", 
            "website", "ওয়েবসাইট", "ওয়েবসাইট", "খবর", "khobor",
            "last update ki", "latest", "last", "update", "updated", "up to date", 
            "accurate", "accurately", "in details", "near me", "this moment", "how to",

            // --- 8. Weather, Climate & Disasters (বাংলা & English) ---
            "আবহাওয়া", "আজ", "আবহাওয়া", "ওয়েদার", "আজকের ওয়েদার", "আজকের আবহাওয়া", "আজকের আবহাওয়া", "আজকের তাপমাত্রা", "তাপমাত্রা", 
            "temp", "টেম্পারেচার", "তাপ", "ট্যাম্পারেচার", "ট্যাম্প", "in my area", "temp in my area", 
            "in my location", "amar areay", "taapmatra", "tapmatra", "taap", "humidity", "হিউমিডিটি", "আর্দ্রতা", 
            "বৃষ্টি হবে কি", "বৃষ্টি হবে কী", "ওয়েদাএ", "wether kmn", "wether koto",
            "বৃষ্টি", "বৃষ্টিপাত", "ঝড়", "ঝড়", "ঝড়বৃষ্টি", "ঘূর্ণিঝড়", "ঘূর্ণিঝড়", "কালবৈশাখী", 
            "বন্যা", "ভূমিকম্প", "শৈত্যপ্রবাহ", "কুয়াশা", "গরম", "তাপপ্রবাহ", "বৃষ্টির সম্ভাবনা",
            "weather", "weather today", "today weather", "forecast", "temperature today", 
            "rain today", "rainfall", "cyclone update", "earthquake", "heatwave", "storm",
            "cold wave", "weather forecast",

            // --- 9. Markets, Commodity Prices & Currency (বাংলা & English) ---
            "দাম", "প্রাইস", "প্রাইজ", "কত বাড়সে", "কত বেড়েছে", "কতো বাড়সে", "খরচ কত", "খরচ কতো",
            "সোনার দাম", "আজকের সোনার দাম", "রূপার দাম", "রুপার দাম", "ডলার রেট", "আজকের ডলার রেট", 
            "ডলারের দাম", "টাকার মান", "মুদ্রার বিনিময় হার", "তেলের দাম", "পেট্রোল", "ডিজেল", 
            "জ্বালানি তেল", "এলপিজি গ্যাস", "বাজার দর", "কাঁচাবাজার", "চালের দাম", "পেঁয়াজের দাম", 
            "মুদ্রাস্ফীতি", "শেয়ার বাজার", "স্টক মার্কেট", "দ্রব্যমূল্য",
            "daam", "dam", "cost koto", "khoros koto", "khoroch koto", "khoroc koto", "price koto", "prize koto",
            "today price", "gold price", "silver price", "dollar rate", "exchange rate", 
            "currency rate", "fuel price", "petrol price", "diesel price", "stock market", 
            "inflation rate",

            // --- 10. Sports, Tournaments & Live Scores ---
            "আজকের খেলা", "খেলার খবর", "লাইভ স্কোর", "ক্রিকেট স্কোর", "ফুটবল ম্যাচ", "বিশ্বকাপ", 
            "টস", "ফলাফল", "কে জিতল", "জিতসে", "জিতেছে", "জিতলো", "ম্যাচ কে জিতেছে", "খেলার সূচি", "আজকের ম্যাচ",
            "স্কোর", "পয়েন্ট", "point", "score", "jitlo",
            "match er score", "vs", "match today", "cricket score", "football score", "world cup", "who won",

            // --- 11. Critical Breaking Incidents, Public & Exams ---
            "মারা গেছেন", "মারা গেছে", "মৃত্যু হয়েছে", "নিহত", "আহত", "গ্রেপ্তার", "গ্রেফতার", 
            "আটক", "পদত্যাগ", "বরখাস্ত", "বদলি", "নিষিদ্ধ", "বিধি নিষেধ", "নিয়ম কানুন", 
            "রেজাল্ট", "রেসাল্ট", "রেজালট", "রেসালট", "folafol", "result", "result kobe", "result kokhon",
            "পরীক্ষার রেজাল্ট", "ফল প্রকাশ", "নিয়োগ বিজ্ঞপ্তি", "বিজ্ঞপ্তি", "ছুটির ঘোষণা", "বন্ধ ঘোষণা", "দুর্ঘটনা", "অগ্নিকাণ্ড",
            "passed away", "died today", "arrested", "resigned", "exam result", "circular", "job circular",

            // --- 12. Banglish (Romanized Bengali) ---
            "sotti naki", "sotti ki", "aaj", "aj", "ajk", "aajke", "aajker", "aajker din", "az", "aaz", "azke", "aazke", "azker", "aazker", 
            "shotti naki", "shotti ki", "shotti kina", "sothik kina", 
            "mithya", "gujob", "vua", "bhua", "rotese", "ashol naki nokol", "viral video",
            "ajker news", "ajker khobor", "ajke", "ajker", "ajkal", "samprotik", "shomprotik", 
            "ei matro", "notun khobor", "shorboshesh", "kobe", "kondin", "kon din", "kbe",
            "kokhon", "kokhn", "kokon", "kbe asbe", "kobe ashbe", "kokhon asbe", "kokhon ashbe", 
            "kokhn asbe", "kokhn ashbe", "ashbey", "ashby", "kivabe ashbe", "kibhabe ashbe", 
            "kibabe asbe", "kivave ashbe", "sorboses", "shorboshesh", "shobshesh", "sobses",
            "kotodin por hobe", "how will it be", "after how many days",
            "koto", "koto barse", "koto berese", "koto bereche", "koto hobe",
            "ei bochor", "ei mashe", "ai mash", "ei mash", "ai bochor", "ai bosor", "ei bosor", 
            "goto mash", "goto month", "ei month", "ai month", "ei year", "ai year", "last month",
            "this week", "last week", "ei shoptahe", "ei shoptaho", "ei shoptah", "ai shoptahe", 
            "shoptahe", "shoptaho", "ai shoptaho", "ai week", "ei week", "goto shoptay",
            "porer boi", "porer gaan", "porer gan", "porer gun", "porer season", "porer episode", "porer ep", "porer volume",
            "bortomane", "bortoman", "bortomaan", "bortomaane", "bottoman", "bottomaan",
            "mohorte", "muhurte", "aimohorte", "eimohorte", "ei mohorte", "ai mohorte",
            "kalke", "kaal", "kal", "kalk", "kaalk", "kalky",
            "porshu", "porsu", "agami", "agame", "goto",
            "eimuhurte", "aimuhurte", "ei muhurte", "ai muhurte", "aimuhurute", "eimhurute", "ki ghottese", 
            "internet ghato", "internet ghete", "net ghato", "net ghete", "net ghaita", 
            "amar location er ashepashe",
            "abohawa", "abohaoa", "abhawa", "abawa", "tapmatra", 
            "thanda kmn", "thanda koto", "tanda koto", "tanda kmn", "tanda kemon", "tanda kamon", "thanda kemon",
            "brishti", "jhor", "bonna", "vumikompo", "sonar dam", "shonar dam", "dollar rate", 
            "dollar er dam", "teler dam", "bazar dor", "share bazar", "ajker khela", 
            "khelar khobor", "live score", "ke jitlo", "jitse", "jiteche", "mara gese", "mara gechen", "mrittu", 
            "greftar", "podotyag", "durghotona", "porikhar result", "fol prokash", "niyog"
        )

        fun containsAutoWebTriggerKeywords(query: String): Boolean {
            if (query.isBlank()) return false
            val lower = query.lowercase().trim()
            val tokens = lower.split(Regex("""[\s,?.!।:;'"\-\(\)]+""")).filter { it.isNotBlank() }.toSet()
            return webSearchTriggerKeywords.any { kw ->
                if (kw.length <= 6 && !kw.contains(" ")) {
                    tokens.contains(kw) || lower.startsWith("$kw ") || lower.endsWith(" $kw") || lower.contains(" $kw ") || lower == kw
                } else {
                    lower.contains(kw)
                }
            }
        }
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
                        timestamp = obj.getLong("timestamp"),
                        isPinned = obj.optBoolean("isPinned", false)
                    )
                )
            }
            return sessions.sortedWith(
                compareByDescending<KhojChatSession> { it.isPinned }
                    .thenByDescending { it.timestamp }
            )
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
                    put("isPinned", sess.isPinned)
                })
            }
            prefs.edit().putString(sessionsListKey, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(tag, "Failed to save sessions list", e)
        }
    }

    // Rename an existing session
    fun renameSession(sessionId: String, newTitle: String) {
        val current = loadSessions().map {
            if (it.id == sessionId) it.copy(title = newTitle.trim()) else it
        }
        saveSessions(current)
    }

    // Toggle pin status for a session
    fun togglePinSession(sessionId: String): Boolean {
        var newPinState = false
        val current = loadSessions().map {
            if (it.id == sessionId) {
                newPinState = !it.isPinned
                it.copy(isPinned = newPinState)
            } else it
        }
        saveSessions(current)
        return newPinState
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
                                snippet = sObj.optString("snippet", ""),
                                date = if (sObj.has("date")) sObj.optString("date", null) else null
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
                            if (!s.date.isNullOrBlank()) put("date", s.date)
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
        responseLanguage: ResponseLanguage = ResponseLanguage.AUTO,
        isWebSearchEnabled: Boolean = false
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

        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey.startsWith("MY_") || apiKey.contains("PLACEHOLDER")) {
            emit(KhojChatState.Error("Gemini API Key is not configured. Please configure it in your secrets or Settings."))
            return@flow
        }

        val langSearchKey2 = getLangSearchKey2()

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val currentDateStr = sdf.format(java.util.Date())

        val languageInstruction = ResponseLanguageManager(context).apply {
            setLanguage(responseLanguage)
        }.getLanguageInstructionPrompt()

        val identityInstruction = """
            Identity & Response Behavior Guidelines:
            - You are 'Khoj AI' (খোঁজ এআই).
            - STRICT ANTI-REPETITION & ZERO UNSOLICITED INTRODUCTION RULE:
              * NEVER start, pepper, or end normal messages by introducing yourself (e.g., NEVER say 'আমি খোঁজ এআই...', 'আমি খোঁজ চ্যাট...', 'আমি টিম খোঁজ দ্বারা তৈরি...', 'আমি একটি এআই সহায়ক...').
              * NEVER use mechanical boilerplate opening or closing greetings like 'আমি আপনাকে কীভাবে সাহায্য করতে পারি?' or 'আমি তথ্য দিতে সর্বদা প্রস্তুত'.
              * Jump straight and directly into the substantive answer from the very first word.
            - DYNAMIC & CREATIVE IDENTITY RESPONSE (ONLY WHEN EXPLICITLY ASKED):
              * If and ONLY if the user specifically asks about your identity (e.g., 'তুমি কে?', 'তোমার নাম কি?', 'কে তোমাকে বানিয়েছে?', 'who are you?', 'তোমার কাজ কি?'):
                1. Answer creatively, naturally, and contextually based on the user's specific query and tone—NEVER recite a canned, rigid, or memorized script.
                2. Core factual anchor: Your name is 'খোঁজ' / 'খোঁজ এআই' (Khoj AI), developed by 'টিম খোঁজ' (Team Khoj) as an intelligent fact-checking, verification, and knowledge assistant tailored for Bangladesh.
                3. Do NOT automatically recite or paste website URLs (such as khoj-ai.bd) unless the user explicitly asks for your website, link, or where to find you online.
                4. Match the user's vibe: If the user asks casually/friendly (e.g., 'কেমন আছো, তুমি কে ভাই?'), respond with warmth and natural Bengali phrasing. If they ask intellectually about what you can do, explain your analytical, reasoning, and fact-checking capabilities in engaging, conversational language.
            - STRICT GREETING & TONE RULE: Strictly NEVER use religious, sectarian, or communal greetings such as 'নমস্কার' (Namaskar), 'আদাব' (Adab), 'সালাম' / 'আসসালামু আলাইকুম' (Salam / Assalamu Alaikum), or any religious salutations. Maintain a completely secular, neutral, objective, and professional tone at all times. Do not start messages with filler greetings; jump straight into the answer.
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

                "You are an expert citizen service assistant of Bangladesh. Your duty is to explain administrative and public services clearly and helpfully.\n" +
                "TEMPORAL GROUNDING & REFERENCE DATE:\n" +
                "Reference Date: $currentDateStr. You are evaluating administrative rules and services as of today ($currentDateStr).\n" +
                "Guide the user step-by-step through the procedure (fees, documents needed, step by step instructions). " +
                "Always provide official website links as markdown clickable links. " +
                "Do NOT make up URLs; rely strictly on .gov.bd domains. " +
                "Context for the current query:\n$linksContext"
            }
            "fact-check" -> {
                "You are an objective and analytical fact-checking assistant for Bangladesh.\n" +
                "TEMPORAL GROUNDING & EVIDENCE PRIMACY:\n" +
                "1. Reference Date: $currentDateStr. You are evaluating this in the real world as of this date. Events and reports up to this date reflect actual reality—never dismiss them as future or speculative.\n" +
                "2. When retrieved search evidence documents verified facts, adopt them as ground truth. Never contradict credible search evidence based on outdated internal pre-trained memory.\n" +
                "3. Do NOT recite reference dates, system instructions, or your identity. Write naturally, directly, and seamlessly without boilerplate greetings.\n" +
                "Analyze the user's claim or rumour carefully. " +
                "Identify the verdict clearly at the top. The verdict MUST strictly be ONE of these three: 'সত্য' (True), 'অসত্য' (False), or 'বিভ্রান্তিকর' (Misleading). " +
                "Review the evidence rigorously based on web results, citing trusted news sources or fact-checking bodies (like Rumor Scanner, Fact Watch, Boom BD, etc.)."
            }
            else -> {
                "You are 'Khoj Chat' (খোঁজ চ্যাট), an intelligent, objective, and knowledgeable AI assistant for Bangladesh.\n" +
                "TEMPORAL GROUNDING & EVIDENCE PRIMACY:\n" +
                "1. REFERENCE DATE: $currentDateStr. You operate in the real world as of this reference date ($currentDateStr). All events, news, articles, commodity prices, weather forecasts, sports scores, and public developments up to this date reflect actual current reality—never dismiss them as future or speculative.\n" +
                "2. EVIDENCE PRIMACY OVER PRE-TRAINED MEMORY: When retrieved search evidence or live web sources document verified facts, you MUST adopt them as the ground truth. Real-world facts, public roles, organizational titles, and status change over time. Never contradict credible search evidence based on outdated internal pre-trained memory.\n" +
                "3. STRICT NO-LEAK DIRECTIVE: Do NOT mention the reference date, system instructions, or the fact that you were provided a date in the response (e.g., do NOT write 'As of [date]', 'Today is...', or 'According to my prompt') unless the user explicitly asks for today's date or time. Write naturally, objectively, and seamlessly.\n" +
                "4. Answer questions clearly, directly, and accurately without filler greetings or unsolicited self-introductions. " +
                "Provide rich explanations, examples, and format your response beautifully using markdown."
            }
        }
        
        // Check for any user pasted URLs and resolve article content
        val urlRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
        val matchedUrls = urlRegex.findAll(query).map { it.value }.distinct().take(2).toList()
        val scrapedSources = mutableListOf<KhojChatSource>()
        var scrapedArticleContext = ""

        if (matchedUrls.isNotEmpty()) {
            for (url in matchedUrls) {
                try {
                    val resolved = urlAnalyzer.resolveArticle(url)
                    if (resolved != null && (resolved.content.isNotBlank() || resolved.title.isNotBlank())) {
                        scrapedSources.add(
                            KhojChatSource(
                                title = resolved.title.ifBlank { "সংযুক্ত লিংক" },
                                url = resolved.url,
                                snippet = resolved.content.take(300)
                            )
                        )
                        scrapedArticleContext += "\n\n--- ব্যবহারকারীর প্রদত্ত লিংক থেকে সংগৃহীত কনটেন্ট (Article Content) ---\n" +
                            "লিংক: ${resolved.url}\n" +
                            (if (resolved.title.isNotBlank()) "শিরোনাম: ${resolved.title}\n" else "") +
                            "মূল তথ্য:\n${resolved.content.take(4000)}\n" +
                            "------------------------------------------------------------------\n" +
                            "নির্দেশনা: ব্যবহারকারী এই লিংকের প্রেক্ষিতে যে প্রশ্ন বা অনুরোধ করেছেন, উপরের সংগৃহীত তথ্যের ভিত্তিতে তার সুনির্দিষ্ট উত্তর দিন।"
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to resolve pasted link $url: ${e.message}")
                }
            }
        }

        val fullSystemInstruction = "$baseSystemInstruction\n\n$identityInstruction\n\n$languageInstruction$scrapedArticleContext"

        // Build clean contents array (Hierarchical Sliding Memory Window: up to 24 turns)
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
            .takeLast(24)

        val recentThreshold = (cleanHistory.size - 8).coerceAtLeast(0)

        cleanHistory.forEachIndexed { index, hMsg ->
            val textToSend = if (hMsg.isUser || index >= recentThreshold) {
                hMsg.text
            } else {
                // Compress older assistant responses to key excerpt (~200 words)
                val words = hMsg.text.split(Regex("""\s+"""))
                if (words.size > 200) {
                    words.take(200).joinToString(" ") + " … [সংক্ষিপ্ত বিবরণ]"
                } else {
                    hMsg.text
                }
            }
            contentsArray.put(JSONObject().apply {
                put("role", if (hMsg.isUser) "user" else "model")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", textToSend) })
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

        // 3. Web Search activation decision
        // In "general" (Khoj Chat) mode: Search is OFF by default. It activates ONLY if user toggles web button,
        // or if the query contains verification/claim trigger keywords, or if user pasted a link.
        val shouldExecuteWebSearch = isWebSearchEnabled || containsAutoWebTriggerKeywords(query) || scrapedSources.isNotEmpty()

        var finalResult: GeminiChatResult? = null

        if (currentMode == "general") {
            // Mode "Khoj Chat":
            val webSources = if (shouldExecuteWebSearch) {
                emit(KhojChatState.Searching)
                val results = searchWithTinyFishAndDegoogFallback(query)
                emit(KhojChatState.Generating)
                results
            } else {
                emit(KhojChatState.Generating)
                emptyList()
            }
            val allSources = (scrapedSources + webSources).distinctBy { it.url }

            val groundedInstruction = if (allSources.isNotEmpty()) {
                appendSourcesToSystemInstruction(fullSystemInstruction, allSources)
            } else {
                fullSystemInstruction
            }

            try {
                finalResult = callGeminiApi(apiKey, contentsArray, groundedInstruction, temperature, useGoogleSearchTool = false)
                if (finalResult != null && allSources.isNotEmpty()) {
                    finalResult = finalResult.copy(sources = allSources)
                }
            } catch (e: Exception) {
                Log.w(tag, "Gemini call in general mode failed: ${e.message}")
            }

            // Safety net: Direct generation via multi-tier LLM cascade
            if (finalResult == null) {
                Log.d(tag, "Safety net: Direct generation via LLM Orchestrator")
                try {
                    val fallbackReq = LlmRequest(
                        systemPrompt = groundedInstruction,
                        userPrompt = query,
                        jsonOutput = false,
                        temperature = temperature
                    )
                    val generated = llmOrchestrator.generateFallback(fallbackReq)
                    if (generated.isNotBlank()) {
                        finalResult = GeminiChatResult(text = generated, sources = allSources)
                    }
                } catch (_: Exception) {
                    finalResult = callGeminiApi(apiKey, contentsArray, groundedInstruction, temperature, useGoogleSearchTool = false)
                    if (finalResult != null && allSources.isNotEmpty()) {
                        finalResult = finalResult.copy(sources = allSources)
                    }
                }
            }
        } else if (currentMode == "citizen-service") {
            // Mode "Citizen Service":
            val webSources = if (shouldExecuteWebSearch) {
                emit(KhojChatState.Searching)
                val results = searchWithTinyFishAndDegoogFallback(query)
                emit(KhojChatState.Generating)
                results
            } else {
                emit(KhojChatState.Generating)
                emptyList()
            }
            val allSources = (scrapedSources + webSources).distinctBy { it.url }

            val groundedInstruction = if (allSources.isNotEmpty()) {
                appendSourcesToSystemInstruction(fullSystemInstruction, allSources)
            } else {
                fullSystemInstruction
            }

            try {
                finalResult = callGeminiApi(apiKey, contentsArray, groundedInstruction, temperature, useGoogleSearchTool = false)
                if (finalResult != null && allSources.isNotEmpty()) {
                    finalResult = finalResult.copy(sources = allSources)
                }
            } catch (e: Exception) {
                Log.w(tag, "Gemini call in citizen-service mode failed: ${e.message}")
            }

            // Safety net: Direct generation via multi-tier LLM cascade
            if (finalResult == null) {
                try {
                    val fallbackReq = LlmRequest(
                        systemPrompt = groundedInstruction,
                        userPrompt = query,
                        jsonOutput = false,
                        temperature = temperature
                    )
                    val generated = llmOrchestrator.generateFallback(fallbackReq)
                    if (generated.isNotBlank()) {
                        finalResult = GeminiChatResult(text = generated, sources = allSources)
                    }
                } catch (_: Exception) {
                    finalResult = callGeminiApi(apiKey, contentsArray, groundedInstruction, temperature, useGoogleSearchTool = false)
                    if (finalResult != null && allSources.isNotEmpty()) {
                        finalResult = finalResult.copy(sources = allSources)
                    }
                }
            }
        } else {
            // Mode "Fact Check":
            emit(KhojChatState.Searching)
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

                if (scrapedSources.isNotEmpty()) {
                    factCheckSources.addAll(0, scrapedSources)
                }

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

                emit(KhojChatState.Generating)
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

            // Fallback for Fact-Check: Primary /tinyfish/search, Fallback /degoog
            if (finalResult == null) {
                val webSources = searchWithTinyFishAndDegoogFallback(query)
                val allSources = (scrapedSources + webSources).distinctBy { it.url }
                val groundedInstruction = if (allSources.isNotEmpty()) {
                    appendSourcesToSystemInstruction(fullSystemInstruction, allSources)
                } else {
                    fullSystemInstruction
                }
                emit(KhojChatState.Generating)
                val res = callGeminiApi(apiKey, contentsArray, groundedInstruction, temperature, useGoogleSearchTool = false)
                if (res != null) {
                    finalResult = res.copy(sources = allSources)
                }
            }

            // Safety net: Direct generation via multi-tier LLM cascade
            if (finalResult == null) {
                try {
                    val fallbackReq = LlmRequest(
                        systemPrompt = fullSystemInstruction,
                        userPrompt = query,
                        jsonOutput = false,
                        temperature = temperature
                    )
                    val generated = llmOrchestrator.generateFallback(fallbackReq)
                    if (generated.isNotBlank()) {
                        finalResult = GeminiChatResult(text = generated, sources = scrapedSources)
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
        val sanitizedText = sanitizeChatResponse(rawText, query)
        var detectedVerdict: String? = null
        if (currentMode == "fact-check") {
            detectedVerdict = when {
                sanitizedText.contains("অসত্য", ignoreCase = true) || sanitizedText.contains("মিথ্যা", ignoreCase = true) || sanitizedText.contains("ভুয়া", ignoreCase = true) || sanitizedText.contains(" গুজব", ignoreCase = true) || sanitizedText.contains("FALSE", ignoreCase = true) -> "false"
                (sanitizedText.contains("সত্য", ignoreCase = true) && !sanitizedText.contains("আংশিক", ignoreCase = true) && !sanitizedText.contains("অসত্য", ignoreCase = true)) || sanitizedText.contains("TRUE", ignoreCase = true) -> "true"
                else -> "misleading" // Strict 3 verdicts: true, false, misleading
            }
        }

        emit(KhojChatState.Success(
            responseText = sanitizedText,
            verdict = detectedVerdict,
            sources = finalResult.sources,
            autoSwitchedToFactCheck = autoSwitched
        ))
    }
}

