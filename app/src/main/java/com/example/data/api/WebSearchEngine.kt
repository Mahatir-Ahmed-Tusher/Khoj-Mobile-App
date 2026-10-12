package com.example.data.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val engine: String,
    val imageUrl: String? = null,
    val source: String? = null
)

class WebSearchEngine {
    private val tag = "WebSearchEngine"
    private val httpClient = NetworkClientProvider.webSearchClient
        
    private fun getWorkerUrl(context: Context?): String {
        val defaultUrl = "https://khoj-search-api.mahatirtusher.workers.dev"
        if (context != null) {
            val prefs = context.getSharedPreferences("khoj_settings", Context.MODE_PRIVATE)
            val custom = prefs.getString("khoj_search_api_url", null)
            if (!custom.isNullOrBlank()) return custom.trimEnd('/')
        }
        return try {
            val buildConfigUrl = com.example.BuildConfig.KHOJ_SEARCH_API_URL
            if (!buildConfigUrl.isNullOrBlank()) buildConfigUrl.trimEnd('/') else defaultUrl
        } catch (_: Exception) {
            defaultUrl
        }
    }

    suspend fun searchDegoog(workerUrl: String, query: String, maxResults: Int = 20): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val payload = JSONObject().apply {
            put("query", query)
            put("max_results", maxResults)
        }
        val request = Request.Builder()
            .url("$workerUrl/degoog")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(body)
                val resultsArr = root.optJSONArray("results") ?: return@withContext emptyList()
                val sources = mutableListOf<WebSearchResult>()
                for (i in 0 until resultsArr.length()) {
                    val item = resultsArr.optJSONObject(i) ?: continue
                    val itemUrl = item.optString("url", "")
                    val title = item.optString("title", "Google Result")
                    val snippet = item.optString("snippet", item.optString("content", ""))
                    val engine = item.optString("engine", "google")
                    if (itemUrl.isNotBlank() && snippet.isNotBlank()) {
                        sources.add(
                            WebSearchResult(
                                title = title,
                                url = itemUrl,
                                snippet = snippet,
                                engine = engine,
                                imageUrl = null,
                                source = "Google Organic"
                            )
                        )
                    }
                }
                sources
            }
        } catch (e: Exception) {
            Log.w(tag, "searchDegoog failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchKhojWorker(workerUrl: String, query: String, maxResults: Int = 20): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$workerUrl/search?q=$encodedQuery&max_results=$maxResults"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(body)
                val resultsArr = root.optJSONArray("results") ?: return@withContext emptyList()
                val sources = mutableListOf<WebSearchResult>()
                for (i in 0 until resultsArr.length()) {
                    val item = resultsArr.optJSONObject(i) ?: continue
                    val itemUrl = item.optString("url", "")
                    val title = item.optString("title", "Web Result")
                    val snippet = item.optString("snippet", item.optString("content", ""))
                    val engine = item.optString("engine", "khoj-worker")
                    if (itemUrl.isNotBlank() && snippet.isNotBlank()) {
                        sources.add(
                            WebSearchResult(
                                title = title,
                                url = itemUrl,
                                snippet = snippet,
                                engine = engine,
                                imageUrl = null,
                                source = "Khoj Search"
                            )
                        )
                    }
                }
                sources
            }
        } catch (e: Exception) {
            Log.w(tag, "searchKhojWorker failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchTinyFish(workerUrl: String, query: String, maxResults: Int = 20): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$workerUrl/tinyfish/search?q=$encodedQuery&max_results=$maxResults"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(body)
                val resultsArr = root.optJSONArray("results") ?: return@withContext emptyList()
                val sources = mutableListOf<WebSearchResult>()
                for (i in 0 until resultsArr.length()) {
                    val item = resultsArr.optJSONObject(i) ?: continue
                    val itemUrl = item.optString("url", "")
                    val title = item.optString("title", "TinyFish Result")
                    val snippet = item.optString("snippet", item.optString("content", ""))
                    val engine = item.optString("engine", "tinyfish")
                    if (itemUrl.isNotBlank() && snippet.isNotBlank()) {
                        sources.add(
                            WebSearchResult(
                                title = title,
                                url = itemUrl,
                                snippet = snippet,
                                engine = engine,
                                imageUrl = null,
                                source = "TinyFish Search"
                            )
                        )
                    }
                }
                sources
            }
        } catch (e: Exception) {
            Log.w(tag, "searchTinyFish failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Web Search Fallback Chain:
     * 1. Primary: /degoog (Google Organic)
     * 2. Fallback 1: SearXNG Cluster
     * 3. Fallback 2: /tinyfish/search (TinyFish Dedicated 21-Key Rotation)
     */
    suspend fun searchWithFallback(context: Context?, query: String, category: String, page: Int = 1): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val isGeneral = category.isBlank() || category.equals("general", ignoreCase = true)
        
        // For non-general categories (images, videos, etc.) or pagination (page > 1), use SearXNG directly
        if (!isGeneral || page > 1) {
            return@withContext searchSearXNG(query, category, page)
        }

        val workerUrl = getWorkerUrl(context)

        // 1. Primary: /degoog
        try {
            val degoogResults = searchDegoog(workerUrl, query, 20)
            if (degoogResults.isNotEmpty()) {
                return@withContext degoogResults
            }
        } catch (e: Exception) {
            Log.w(tag, "Primary /degoog failed: ${e.message}")
        }

        // 2. Fallback 1: SearXNG
        try {
            val searxngResults = searchSearXNG(query, category, page)
            if (searxngResults.isNotEmpty()) {
                return@withContext searxngResults
            }
        } catch (e: Exception) {
            Log.w(tag, "Fallback 1 SearXNG failed: ${e.message}")
        }

        // 3. Fallback 2: /tinyfish/search
        try {
            val tinyfishResults = searchTinyFish(workerUrl, query, 20)
            if (tinyfishResults.isNotEmpty()) {
                return@withContext tinyfishResults
            }
        } catch (e: Exception) {
            Log.w(tag, "Fallback 2 /tinyfish/search failed: ${e.message}")
        }

        emptyList()
    }

    suspend fun generateWebSearchOverview(context: Context, systemPrompt: String, userPrompt: String): String {
        val orchestrator = LlmOrchestrator(context, com.example.data.telemetry.PipelineType.DEEP_SEARCH)
        val request = LlmRequest(systemPrompt = systemPrompt, userPrompt = userPrompt, temperature = 0.3)
        return orchestrator.generateFallback(request)
    }

    suspend fun searchSearXNG(query: String, category: String, page: Int = 1): List<WebSearchResult> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val urlsToTry = SearxngHelper.baseUrls
        
        val resultDeferred = kotlinx.coroutines.CompletableDeferred<List<WebSearchResult>>()
        var failedCount = 0
        
        val jobs = urlsToTry.map { baseUrl ->
            launch {
                val primaryUrl = if (category.isNotBlank() && category != "general") {
                    "$baseUrl/search?q=$encodedQuery&format=json&categories=$category&pageno=$page"
                } else {
                    "$baseUrl/search?q=$encodedQuery&format=json&pageno=$page"
                }
                
                var results = fetchSearXNGUrl(primaryUrl)
                if (results.isEmpty()) {
                    results = fetchSearXNGUrl(primaryUrl)
                }
                
                if (results.isNotEmpty()) {
                    resultDeferred.complete(results.distinctBy { it.url })
                } else {
                    synchronized(this@withContext) {
                        failedCount++
                        if (failedCount == urlsToTry.size) {
                            resultDeferred.complete(emptyList())
                        }
                    }
                }
            }
        }
        
        if (urlsToTry.isEmpty()) return@withContext emptyList()
        val finalResult = resultDeferred.await()
        jobs.forEach { it.cancel() }
        return@withContext finalResult
    }

    private fun fetchSearXNGUrl(urlStr: String): List<WebSearchResult> {
        val request = Request.Builder()
            .url(urlStr)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "Khoj-WebSearch/1.0")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = JSONObject(body)
                val resultsArr = root.optJSONArray("results") ?: return emptyList()

                val sources = mutableListOf<WebSearchResult>()
                for (i in 0 until resultsArr.length()) {
                    val item = resultsArr.getJSONObject(i)
                    val itemUrl = item.optString("url", "")
                    val title = item.optString("title", "Web Source")
                    val snippet = item.optString("content", item.optString("snippet", ""))
                    val engine = item.optString("engine", "searxng")
                    
                    // Dynamic image parsing as requested
                    val imageUrl = item.optString("img_src", null)
                        ?: item.optString("thumbnail_src", null)
                        ?: item.optString("thumbnail", null)
                        ?: item.optString("image", null)
                        ?: if (item.has("thumbnail") && !item.isNull("thumbnail")) item.getString("thumbnail") else null
                        
                    val sourceStr = item.optString("source", null)

                    if (itemUrl.isNotBlank()) {
                        sources.add(
                            WebSearchResult(
                                title = title,
                                url = itemUrl,
                                snippet = snippet,
                                engine = engine,
                                imageUrl = imageUrl?.takeIf { it.isNotBlank() },
                                source = sourceStr?.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
                sources
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
