package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SearchOrchestrator(
    private val context: Context,
    private val circuitBreaker: CircuitBreaker = CircuitBreaker()
) {
    private val tag = "SearchOrchestrator"

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

    fun getWorkerUrl(): String {
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

    suspend fun fetchContentForUrls(urls: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext emptyMap()
        val workerUrl = getWorkerUrl()
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val payload = JSONObject().apply {
            val arr = JSONArray()
            urls.forEach { arr.put(it) }
            put("urls", arr)
        }

        val request = Request.Builder()
            .url("$workerUrl/content")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val timeoutClient = NetworkClientProvider.ocrAndLlmClient.newBuilder()
            .connectTimeout(12500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(12500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .writeTimeout(12500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        val resultMap = mutableMapOf<String, String>()
        try {
            timeoutClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext emptyMap()
                    val root = JSONObject(body)
                    val resultsArr = root.optJSONArray("results")
                    if (resultsArr != null) {
                        for (i in 0 until resultsArr.length()) {
                            val item = resultsArr.optJSONObject(i) ?: continue
                            val itemUrl = item.optString("url", "")
                            val content = item.optString("content", item.optString("markdown", ""))
                            if (itemUrl.isNotBlank() && content.isNotBlank()) {
                                resultMap[itemUrl] = content
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch content from Cloudflare Worker: ${e.message}")
        }
        resultMap
    }

    val providers: List<SearchProvider> by lazy {
        val list = mutableListOf<SearchProvider>()
        val workerUrl = getWorkerUrl()
        list.add(KhojWorkerSearchProvider(workerUrl))

        // Strict fallback order requested by user: Tavily -> RapidAPI -> Exa -> SearXNG -> LangSearch
        val tavilyKey = getApiKey("tavily_api_key", BuildConfig.TAVILY_API_KEY)
        val rapidApiKey = getApiKey("rapid_api_key", try { BuildConfig.RAPID_API_KEY } catch (_: Exception) { "" })
        val exaKey = getApiKey("exa_api_key", BuildConfig.EXA_API_KEY)
        val langSearchKey = getApiKey("langsearch_api_key", BuildConfig.LANGSEARCH_API_KEY)

        if (isValidKey(tavilyKey)) list.add(TavilySearchProvider(tavilyKey))
        if (isValidKey(rapidApiKey)) list.add(RapidApiSearchProvider(rapidApiKey))
        if (isValidKey(exaKey)) list.add(ExaSearchProvider(exaKey))
        list.add(SearxngSearchProvider())
        if (isValidKey(langSearchKey)) list.add(LangSearchProvider(langSearchKey))

        list
    }

    suspend fun executeFallbackSearch(query: String, onProgress: suspend (String) -> Unit): List<DeepSearchRawSource> {
        val mergedSources = mutableListOf<DeepSearchRawSource>()
        
        for (provider in providers) {
            if (circuitBreaker.isOpen()) {
                Log.w(tag, "Circuit breaker is OPEN. Skipping provider: ${provider.name}")
                continue
            }
            
            try {
                onProgress(provider.name)
                val results = withContext(Dispatchers.IO) { provider.search(query, 12) }
                mergedSources.addAll(results)
                
                if (mergedSources.isNotEmpty()) {
                    circuitBreaker.recordSuccess()
                    break // Stop fallback chain since we got results
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Propagate cancellation/timeout
            } catch (e: Exception) {
                Log.w(tag, "Provider ${provider.name} failed: ${e.message}")
                circuitBreaker.recordFailure()
            }
        }
        
        return deduplicateAndNormalize(mergedSources)
    }

    suspend fun executeBilingualFallbackSearch(
        queryBn: String,
        queryEn: String,
        onProgress: suspend (String) -> Unit
    ): List<DeepSearchRawSource> = withContext(Dispatchers.IO) {
        if (queryBn.isBlank() && queryEn.isBlank()) return@withContext emptyList()
        if (queryBn.isBlank()) return@withContext executeFallbackSearch(queryEn, onProgress)
        if (queryEn.isBlank()) return@withContext executeFallbackSearch(queryBn, onProgress)

        coroutineScope {
            val deferredBn = async {
                try {
                    executeFallbackSearch(queryBn) { providerName ->
                        onProgress("$providerName (বাংলা)")
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Bilingual search failed for Bengali query: $queryBn", e)
                    emptyList()
                }
            }

            val deferredEn = async {
                try {
                    executeFallbackSearch(queryEn) { providerName ->
                        onProgress("$providerName (Global)")
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Bilingual search failed for English query: $queryEn", e)
                    emptyList()
                }
            }

            val bnSources = deferredBn.await()
            val enSources = deferredEn.await()
            val combined = ArrayList<DeepSearchRawSource>(bnSources.size + enSources.size)
            combined.addAll(bnSources)
            combined.addAll(enSources)
            deduplicateAndNormalize(combined)
        }
    }

    suspend fun executeParallelSearches(
        plan: DeepSearchPlan,
        onProgress: suspend (sourcesFound: Int, newSources: List<DeepSearchRawSource>) -> Unit
    ): List<DeepSearchRawSource> = withContext(Dispatchers.IO) {
        val totalFoundList = mutableListOf<DeepSearchRawSource>()

        val engineGroups = listOf(
            "google,brave,bing,duckduckgo",
            "qwant,mojeek,startpage,wikipedia,wikidata",
            "" 
        )

        val deferreds = plan.queries.flatMap { weightedQuery ->
            providers.flatMap { provider ->
                if (provider is SearxngSearchProvider) {
                    engineGroups.map { engines ->
                        async {
                            try {
                                val results = provider.searchSearXNG(weightedQuery.query, engines)
                                val weighted = results.map { it.copy(queryWeight = weightedQuery.weight) }
                                if (weighted.isNotEmpty()) {
                                    synchronized(totalFoundList) {
                                        totalFoundList.addAll(weighted)
                                    }
                                    onProgress(totalFoundList.size, weighted)
                                }
                                weighted
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(tag, "SearXNG query failed for engines '$engines': ${weightedQuery.query}", e)
                                emptyList()
                            }
                        }
                    }
                } else {
                    listOf(
                        async {
                            try {
                                val results = provider.search(weightedQuery.query, 12)
                                val weighted = results.map { it.copy(queryWeight = weightedQuery.weight) }
                                if (weighted.isNotEmpty()) {
                                    synchronized(totalFoundList) {
                                        totalFoundList.addAll(weighted)
                                    }
                                    onProgress(totalFoundList.size, weighted)
                                }
                                weighted
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(tag, "Provider ${provider.name} failed for query: ${weightedQuery.query}", e)
                                emptyList()
                            }
                        }
                    )
                }
            }
        }
        val allResults = deferreds.awaitAll().flatten()
        return@withContext deduplicateAndNormalize(allResults)
    }

    data class DeepSearchResult(
        val engine: String,
        val sources: List<DeepSearchRawSource>
    )

    suspend fun executeDeepSearchFallback(
        query: String,
        plan: DeepSearchPlan? = null,
        onProgress: suspend (engineName: String, foundCount: Int, newSources: List<DeepSearchRawSource>) -> Unit
    ): DeepSearchResult = withContext(Dispatchers.IO) {
        val workerUrl = getWorkerUrl()

        // 1. Primary Engine: Cloudflare Worker /tinyfish/research (~1.3-4s, 50-70 citations)
        try {
            onProgress("TinyFish Research", 0, emptyList())
            val tinyFishProvider = TinyFishResearchProvider(workerUrl)
            val results = tinyFishProvider.search(query, 70)
            if (results.isNotEmpty()) {
                val deduped = deduplicateAndNormalize(results)
                onProgress("TinyFish Research", deduped.size, deduped)
                return@withContext DeepSearchResult("tinyfish", deduped)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "Primary /tinyfish/research failed, falling back to /degoog: ${e.message}")
        }

        // 2. Fallback 1: Cloudflare Worker /degoog (~1.5-3s, 15-20 Google organic results)
        try {
            onProgress("Degoog Search", 0, emptyList())
            val degoogProvider = DegoogSearchProvider(workerUrl)
            val results = degoogProvider.search(query, 20)
            if (results.isNotEmpty()) {
                val deduped = deduplicateAndNormalize(results)
                onProgress("Degoog Search", deduped.size, deduped)
                return@withContext DeepSearchResult("degoog", deduped)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "Fallback 1 /degoog failed, falling back to SearXNG cluster: ${e.message}")
        }

        // 3. Fallback 2: Existing SearXNG multi-engine cluster
        onProgress("SearXNG Aggregator", 0, emptyList())
        val effectivePlan = plan ?: DeepSearchPlan(
            intent = "informational",
            languageStrategy = "mixed",
            queries = listOf(WeightedQuery(query, "primary", 1.0))
        )
        val searxngSources = executeParallelSearches(effectivePlan) { count, newSources ->
            onProgress("SearXNG Aggregator", count, newSources)
        }
        return@withContext DeepSearchResult("searxng", searxngSources)
    }

    fun deduplicateAndNormalize(rawSources: List<DeepSearchRawSource>): List<DeepSearchRawSource> {
        val trackingParams = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "gclid", "fbclid", "msclkid", "ref", "referrer", "source"
        )

        fun normalizeUrl(urlStr: String): String {
            return try {
                val uri = java.net.URI(urlStr)
                val query = uri.query
                if (query.isNullOrBlank()) return urlStr.lowercase().trimEnd('/')
                val filteredQuery = query.split("&")
                    .filter { param ->
                        val key = param.substringBefore("=").lowercase()
                        key !in trackingParams
                    }
                    .joinToString("&")
                val normalized = java.net.URI(
                    uri.scheme, uri.authority, uri.path,
                    filteredQuery.ifBlank { null }, null
                ).toString()
                normalized.lowercase().trimEnd('/')
            } catch (e: Exception) {
                urlStr.lowercase().trimEnd('/')
            }
        }

        val seenUrls = mutableSetOf<String>()
        val result = mutableListOf<DeepSearchRawSource>()
        for (source in rawSources) {
            val normalized = normalizeUrl(source.url)
            if (seenUrls.add(normalized)) {
                result.add(source)
            }
            if (result.size >= 300) break
        }
        return result
    }

    fun calculateRelevanceScore(query: String, title: String, snippet: String, url: String): Double {
        val combinedText = "$title $snippet $url".lowercase()
        val domain = CitationEngine.extractDomainPublic(url).lowercase()

        val isDictionaryNoise = domain.contains("dictionary") || domain.contains("thesaurus") ||
                domain.contains("merriam-webster") || domain.contains("vocabulary.com") ||
                domain.contains("etymonline") || domain.contains("collins") || domain.contains("shabdkosh") ||
                domain.contains("english-bangla.com") || domain.contains("wiktionary.org")
        if (isDictionaryNoise) {
            val queryIsAboutWordDefinition = query.lowercase().let {
                it.contains("অর্থ কি") || it.contains("meaning") || it.contains("definition") || it.contains("শব্দার্থ")
            }
            if (!queryIsAboutWordDefinition) return 0.02
        }

        val isForeignSpamTld = domain.endsWith(".fr") || domain.endsWith(".de") || domain.endsWith(".es") ||
                domain.endsWith(".it") || domain.endsWith(".ru") || domain.endsWith(".cn") ||
                domain.endsWith(".br") || domain.endsWith(".pl") || domain.endsWith(".cz") ||
                domain.endsWith(".nl") || domain.endsWith(".se") || domain.endsWith(".no") ||
                domain.endsWith(".jp") || domain.endsWith(".kr") || domain.endsWith(".tw")
        
        val isKnownCredibleForeign = domain.contains("dw.com") || domain.contains("lemonde.fr") ||
                domain.contains("afp.com") || domain.contains("reuters.com") || domain.contains("bbc.com")
        
        if (isForeignSpamTld && !isKnownCredibleForeign) {
            return 0.04
        }

        val stopWords = setOf(
            "about", "what", "with", "this", "that", "from", "have", "here", "which", "where", "when", "how", "who", "will", "would",
            "নিয়ে", "কি", "কোন", "কোথায়", "কেন", "এবং", "অথবা", "আছে", "করা", "হয়েছে", "হলে", "থেকে", "জন্য", "এর",
            "টি", "টা", "গুলো", "গুলি", "বলে", "হয়", "হবে", "হয়ে", "এক", "একটি", "বা", "না", "তো", "যে", "সে", "তা"
        )
        val terms = query.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in stopWords }

        if (terms.isEmpty()) return 1.0

        var matchCount = 0
        for (term in terms) {
            val stem = if (term.length >= 4) term.substring(0, term.length - 1) else term
            if (combinedText.contains(term) || combinedText.contains(stem)) {
                matchCount++
            }
        }

        val matchRatio = matchCount.toDouble() / terms.size.toDouble()
        return when {
            matchRatio >= 0.5 -> 1.0
            matchRatio >= 0.25 -> 0.8
            matchRatio > 0.0 -> 0.45
            else -> 0.1
        }
    }

    fun rerankBilingualSources(
        queryBn: String,
        queryEn: String,
        sources: List<DeepSearchRawSource>
    ): List<DeepSearchRawSource> {
        val sorted = sources
            .map { source ->
                val domain = CitationEngine.extractDomainPublic(source.url)
                val tier = CitationEngine.getCredibilityTier(domain)
                val tierScore = (6.0 - tier) / 5.0   // Tier 1 -> 1.0, Tier 5 -> 0.2
                
                val relBn = if (queryBn.isNotBlank()) calculateRelevanceScore(queryBn, source.title, source.snippet, source.url) else 0.0
                val relEn = if (queryEn.isNotBlank()) calculateRelevanceScore(queryEn, source.title, source.snippet, source.url) else 0.0
                val relevance = maxOf(relBn, relEn).coerceAtLeast(0.1)

                source.copy(credibilityScore = source.queryWeight * tierScore * relevance)
            }
            .sortedByDescending { it.credibilityScore }
        return sorted
    }

    fun rerankSources(query: String, sources: List<DeepSearchRawSource>): List<DeepSearchRawSource> {
        return rerankBilingualSources(query, "", sources)
    }
}

class LangSearchProvider(private val apiKey: String) : SearchProvider {
    override val name = "LangSearch API"
    private val client = NetworkClientProvider.ocrAndLlmClient

    override suspend fun search(query: String, maxResults: Int): List<DeepSearchRawSource> = withContext(Dispatchers.IO) {
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
            
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("LangSearch API HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body")
            val root = JSONObject(body)
            val data = root.optJSONObject("data") ?: return@withContext emptyList()
            val webPages = data.optJSONObject("webPages") ?: return@withContext emptyList()
            val value = webPages.optJSONArray("value") ?: return@withContext emptyList()
            
            val sources = mutableListOf<DeepSearchRawSource>()
            for (i in 0 until value.length()) {
                val item = value.getJSONObject(i)
                val url = item.optString("url", "")
                val title = item.optString("name", "Untitled")
                val snippet = item.optString("summary", item.optString("snippet", ""))
                if (url.isNotBlank() && snippet.isNotBlank()) {
                    sources.add(DeepSearchRawSource(title, url, snippet, "langsearch"))
                }
            }
            sources
        }
    }
}

class ExaSearchProvider(private val apiKey: String) : SearchProvider {
    override val name = "Exa API"
    private val client = NetworkClientProvider.ocrAndLlmClient

    override suspend fun search(query: String, maxResults: Int): List<DeepSearchRawSource> = withContext(Dispatchers.IO) {
        val jsonMediaType = "application/json".toMediaType()
        val requestBody = JSONObject().apply {
            put("query", query)
            put("type", "auto")
            put("numResults", maxResults)
            put("contents", JSONObject().apply {
                put("highlights", JSONObject().apply {
                    put("numSentences", 2)
                })
                put("text", false)
            })
        }
        val request = Request.Builder()
            .url("https://api.exa.ai/search")
            .addHeader("x-api-key", apiKey)
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .build()
            
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Exa API HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body")
            val root = JSONObject(body)
            val resultsArr = root.optJSONArray("results") ?: return@withContext emptyList()
            
            val sources = mutableListOf<DeepSearchRawSource>()
            for (i in 0 until resultsArr.length()) {
                val item = resultsArr.getJSONObject(i)
                val url = item.optString("url", "")
                val title = item.optString("title", "Untitled")
                val contents = item.optString("text", "")
                val highlights = item.optJSONArray("highlights")
                val snippet = if (highlights != null && highlights.length() > 0) {
                    highlights.getString(0)
                } else {
                    contents.take(300)
                }
                if (url.isNotBlank() && snippet.isNotBlank()) {
                    sources.add(DeepSearchRawSource(title, url, snippet, "exa"))
                }
            }
            sources
        }
    }
}

class TavilySearchProvider(private val apiKey: String) : SearchProvider {
    override val name = "Tavily API"
    private val client = NetworkClientProvider.ocrAndLlmClient

    override suspend fun search(query: String, maxResults: Int): List<DeepSearchRawSource> = withContext(Dispatchers.IO) {
        val jsonMediaType = "application/json".toMediaType()
        val requestBody = JSONObject().apply {
            put("query", query)
            put("search_depth", "basic")
            put("include_images", false)
            put("include_answer", false)
            put("max_results", maxResults)
        }
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .build()
            
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Tavily API HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body")
            val root = JSONObject(body)
            val resultsArr = root.optJSONArray("results") ?: return@withContext emptyList()
            
            val sources = mutableListOf<DeepSearchRawSource>()
            for (i in 0 until resultsArr.length()) {
                val item = resultsArr.getJSONObject(i)
                val url = item.optString("url", "")
                val title = item.optString("title", "Untitled")
                val snippet = item.optString("content", "")
                if (url.isNotBlank() && snippet.isNotBlank()) {
                    sources.add(DeepSearchRawSource(title, url, snippet, "tavily"))
                }
            }
            sources
        }
    }
}

class SearxngSearchProvider : SearchProvider {
    override val name = "SearXNG (Internal)"
    private val client = NetworkClientProvider.deepSearchClient
    private val searxngBaseUrls: List<String>
        get() = SearxngHelper.baseUrls

    override suspend fun search(query: String, maxResults: Int): List<DeepSearchRawSource> {
        return searchSearXNG(query, "")
    }
    
    suspend fun searchSearXNG(query: String, engines: String): List<DeepSearchRawSource> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val urlsToTry = searxngBaseUrls
        val allResults = mutableListOf<DeepSearchRawSource>()

        for (baseUrl in urlsToTry) {
            val primaryUrl = if (engines.isNotBlank()) {
                "$baseUrl/search?q=$encodedQuery&format=json&engines=$engines"
            } else {
                "$baseUrl/search?q=$encodedQuery&format=json"
            }

            var results = fetchSearXNGUrl(primaryUrl)
            if (results.isEmpty() && engines.isNotBlank()) {
                val fallbackUrl = "$baseUrl/search?q=$encodedQuery&format=json"
                results = fetchSearXNGUrl(fallbackUrl)
            }
            
            if (results.isNotEmpty()) {
                allResults.addAll(results)
                if (results.size >= 12) break
            }
        }
        
        return@withContext allResults.distinctBy { it.url }
    }

    private fun fetchSearXNGUrl(urlStr: String): List<DeepSearchRawSource> {
        val request = Request.Builder()
            .url(urlStr)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "Khoj-DeepSearch/1.0")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = JSONObject(body)
                val resultsArr = root.optJSONArray("results") ?: return emptyList()

                val sources = mutableListOf<DeepSearchRawSource>()
                for (i in 0 until resultsArr.length()) {
                    val item = resultsArr.getJSONObject(i)
                    val itemUrl = item.optString("url", "")
                    val title = item.optString("title", "Web Source")
                    val snippet = item.optString("content", item.optString("snippet", ""))
                    val engine = item.optString("engine", "searxng")

                    if (itemUrl.isNotBlank() && snippet.isNotBlank()) {
                        sources.add(DeepSearchRawSource(title, itemUrl, snippet, engine))
                    }
                }
                sources
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class KhojWorkerSearchProvider(private val workerUrl: String) : SearchProvider {
    override val name = "Khoj Search API"
    private val client = NetworkClientProvider.ocrAndLlmClient.newBuilder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override suspend fun search(query: String, maxResults: Int): List<DeepSearchRawSource> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$workerUrl/search?q=$encodedQuery&max_results=$maxResults"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Worker HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body")
            val root = JSONObject(body)
            val resultsArr = root.optJSONArray("results") ?: return@withContext emptyList()
            val sources = mutableListOf<DeepSearchRawSource>()
            for (i in 0 until resultsArr.length()) {
                val item = resultsArr.optJSONObject(i) ?: continue
                val itemUrl = item.optString("url", "")
                val title = item.optString("title", "Untitled")
                val snippet = item.optString("snippet", item.optString("content", ""))
                val content = item.optString("content", "").takeIf { it.isNotBlank() }
                val engine = item.optString("engine", "khoj-worker")
                if (itemUrl.isNotBlank() && snippet.isNotBlank()) {
                    val rawSource = DeepSearchRawSource(title, itemUrl, snippet, engine)
                    if (!content.isNullOrBlank()) {
                        rawSource.content = content
                    }
                    sources.add(rawSource)
                }
            }
            sources
        }
    }
}

class RapidApiSearchProvider(private val apiKey: String) : SearchProvider {
    override val name = "RapidAPI Search"
    private val client = NetworkClientProvider.ocrAndLlmClient

    override suspend fun search(query: String, maxResults: Int): List<DeepSearchRawSource> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        // 1. Try google-search74
        val url1 = "https://google-search74.p.rapidapi.com/?query=$encodedQuery&limit=$maxResults&related_keywords=true"
        val req1 = Request.Builder()
            .url(url1)
            .addHeader("X-RapidAPI-Key", apiKey)
            .addHeader("X-RapidAPI-Host", "google-search74.p.rapidapi.com")
            .get()
            .build()

        try {
            client.newCall(req1).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val results = root.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val sources = mutableListOf<DeepSearchRawSource>()
                        for (i in 0 until results.length()) {
                            val item = results.optJSONObject(i) ?: continue
                            val u = item.optString("url", "")
                            val t = item.optString("title", "Untitled")
                            val s = item.optString("description", "")
                            if (u.isNotBlank() && s.isNotBlank()) {
                                sources.add(DeepSearchRawSource(t, u, s, "rapidapi"))
                            }
                        }
                        if (sources.isNotEmpty()) return@withContext sources
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("RapidApiSearchProvider", "google-search74 failed: ${e.message}")
        }

        // 2. Fallback to google-search3
        val url2 = "https://google-search3.p.rapidapi.com/api/v1/search/q=$encodedQuery&num=$maxResults"
        val req2 = Request.Builder()
            .url(url2)
            .addHeader("X-RapidAPI-Key", apiKey)
            .addHeader("X-RapidAPI-Host", "google-search3.p.rapidapi.com")
            .get()
            .build()

        client.newCall(req2).execute().use { response ->
            if (!response.isSuccessful) throw Exception("RapidAPI HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body")
            val root = JSONObject(body)
            val resultsArr = root.optJSONArray("results") ?: return@withContext emptyList()
            val sources = mutableListOf<DeepSearchRawSource>()
            for (i in 0 until resultsArr.length()) {
                val item = resultsArr.optJSONObject(i) ?: continue
                val u = item.optString("link", "")
                val t = item.optString("title", "Untitled")
                val s = item.optString("description", item.optString("snippet", ""))
                if (u.isNotBlank() && s.isNotBlank()) {
                    sources.add(DeepSearchRawSource(t, u, s, "rapidapi"))
                }
            }
            sources
        }
    }
}

class TinyFishResearchProvider(private val workerUrl: String) : SearchProvider {
    override val name = "TinyFish Deep Research"
    private val client = NetworkClientProvider.ocrAndLlmClient.newBuilder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override suspend fun search(query: String, maxResults: Int): List<DeepSearchRawSource> = withContext(Dispatchers.IO) {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val payload = JSONObject().apply {
            put("query", query)
            put("max_depth", 1)
        }
        val request = Request.Builder()
            .url("$workerUrl/tinyfish/research")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("TinyFish HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body from TinyFish")
            val root = JSONObject(body)
            val resultsArr = root.optJSONArray("results") ?: root.optJSONArray("sources") ?: return@withContext emptyList()
            val sources = mutableListOf<DeepSearchRawSource>()
            for (i in 0 until resultsArr.length()) {
                val item = resultsArr.optJSONObject(i) ?: continue
                val itemUrl = item.optString("url", "")
                val title = item.optString("title", "Untitled")
                val snippet = item.optString("snippet", item.optString("content", ""))
                val content = item.optString("content", "").takeIf { it.isNotBlank() }
                if (itemUrl.isNotBlank() && snippet.isNotBlank()) {
                    val rawSource = DeepSearchRawSource(title, itemUrl, snippet, "tinyfish")
                    if (!content.isNullOrBlank()) {
                        rawSource.content = content
                    }
                    sources.add(rawSource)
                }
            }
            sources
        }
    }
}

class DegoogSearchProvider(private val workerUrl: String) : SearchProvider {
    override val name = "Degoog Search"
    private val client = NetworkClientProvider.ocrAndLlmClient.newBuilder()
        .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override suspend fun search(query: String, maxResults: Int): List<DeepSearchRawSource> = withContext(Dispatchers.IO) {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val payload = JSONObject().apply {
            put("query", query)
            put("max_results", maxResults.coerceAtLeast(20))
        }
        val request = Request.Builder()
            .url("$workerUrl/degoog")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Degoog HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty body from Degoog")
            val root = JSONObject(body)
            val resultsArr = root.optJSONArray("results") ?: return@withContext emptyList()
            val sources = mutableListOf<DeepSearchRawSource>()
            for (i in 0 until resultsArr.length()) {
                val item = resultsArr.optJSONObject(i) ?: continue
                val itemUrl = item.optString("url", "")
                val title = item.optString("title", "Untitled")
                val snippet = item.optString("snippet", item.optString("content", ""))
                val content = item.optString("content", "").takeIf { it.isNotBlank() }
                if (itemUrl.isNotBlank() && snippet.isNotBlank()) {
                    val rawSource = DeepSearchRawSource(title, itemUrl, snippet, "degoog")
                    if (!content.isNullOrBlank()) {
                        rawSource.content = content
                    }
                    sources.add(rawSource)
                }
            }
            sources
        }
    }
}


