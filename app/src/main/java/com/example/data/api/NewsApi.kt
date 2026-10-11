package com.example.data.api

import com.example.data.model.NewsArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches news from the khoj-news-backend proxy.
 *
 * The backend is on Render's free tier and can take up to ~30 seconds to wake
 * from sleep, so timeouts are intentionally generous (40s).
 *
 * IMPORTANT: This class never contacts newsdata.io directly. All requests go
 * through https://khoj-news-backend.onrender.com — the API key lives only on
 * that server and is never referenced here.
 */
object NewsApi {

    private const val BASE_URL = "https://khoj-news-backend.onrender.com"
    private const val ENDPOINT = "/api/latest-news"

    private val client = NetworkClientProvider.baseOkHttpClient.newBuilder()
        .connectTimeout(40, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches the latest news articles, filtering out duplicates.
     * Returns an empty list on network failure (callers handle error state).
     */
    suspend fun fetchLatestNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL$ENDPOINT")
            .get()
            .build()

        val responseBody = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("News fetch failed: HTTP ${response.code}")
            response.body?.string() ?: error("Empty response body")
        }

        val root = JSONObject(responseBody)
        val results = root.optJSONArray("results") ?: return@withContext emptyList()

        val articles = mutableListOf<NewsArticle>()
        for (i in 0 until results.length()) {
            val obj = results.getJSONObject(i)
            // Filter out duplicate stories (same headline from multiple sources)
            if (obj.optBoolean("duplicate", false)) continue

            val title = obj.optString("title").takeIf { it.isNotBlank() } ?: continue
            val description = obj.optString("description").takeIf { it.isNotBlank() }
            val imageUrl = obj.optString("image_url").takeIf { it.isNotBlank() }
            val link = obj.optString("link").takeIf { it.isNotBlank() } ?: continue

            articles.add(NewsArticle(title, description, imageUrl, link))
        }
        articles
    }
}
