package com.example.data.api

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ResolvedArticle(
    val title: String,
    val content: String,
    val author: String,
    val url: String
)

class LoginWallException(message: String) : Exception(message)

/**
 * Resolves content from user-provided URLs using the remote Cloudflare content service
 * (ARTICLE_RESOLVER_URL) and extracts a concise search query using Gemini.
 * Fully compliant with Google Play Store policies (Zero local scraping or hidden WebViews).
 */
class UrlAnalyzer {

    /**
     * Resolves and extracts article content (title, content, author) from a given URL
     * using the remote Cloudflare Worker content service (ARTICLE_RESOLVER_URL).
     */
    suspend fun resolveArticle(url: String): ResolvedArticle? = withContext(Dispatchers.IO) {
        val rawBase = BuildConfig.ARTICLE_RESOLVER_URL.trim().removeSurrounding("\"").removeSurrounding("'").trim().trimEnd('/')
        if (rawBase.isBlank()) {
            return@withContext null
        }

        val endpoint = if (rawBase.endsWith("/resolve") || rawBase.endsWith("/parse")) {
            rawBase
        } else {
            "$rawBase/resolve"
        }

        val client = NetworkClientProvider.ocrAndLlmClient
        val mediaType = "application/json".toMediaType()
        val payload = JSONObject().apply {
            put("url", url)
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(mediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) return@withContext null

                val json = JSONObject(responseBody)
                if (!json.optBoolean("success", true)) return@withContext null

                val title = json.optString("title", "").trim()
                val content = json.optString("content", "").trim()
                val author = json.optString("author", "").trim()

                if (content.isNotBlank() || title.isNotBlank()) {
                    ResolvedArticle(title = title, content = content, author = author, url = url)
                } else null
            }
        } catch (e: Exception) {
            android.util.Log.w("UrlAnalyzer", "Failed to resolve article from $url: ${e.message}")
            null
        }
    }

    /**
     * Resolves the given URL via the Cloudflare content service, parses the extracted content,
     * and uses the Query Parser LLM (Gemini) to generate a concise fact-check search claim.
     *
     * @param url The target URL to analyze.
     * @return A concise, single-sentence search query representing the core factual claim.
     */
    suspend fun analyzeUrlAndExtractClaim(url: String): String = withContext(Dispatchers.IO) {
        val resolvedContent = resolveUrlContent(url)

        if (resolvedContent.isBlank()) {
            throw Exception("Could not extract any content from the provided URL.")
        }

        ClaimExtractor.extractClaim(resolvedContent)
    }

    /**
     * Calls the remote Cloudflare Worker endpoint (ARTICLE_RESOLVER_URL/resolve)
     * to safely fetch title, caption/article text, author, and metadata.
     */
    private suspend fun resolveUrlContent(url: String): String = withContext(Dispatchers.IO) {
        val rawBase = BuildConfig.ARTICLE_RESOLVER_URL.trim().removeSurrounding("\"").removeSurrounding("'").trim().trimEnd('/')
        if (rawBase.isBlank()) {
            throw Exception("ARTICLE_RESOLVER_URL is not configured in environment.")
        }

        val endpoint = if (rawBase.endsWith("/resolve") || rawBase.endsWith("/parse")) {
            rawBase
        } else {
            "$rawBase/resolve"
        }

        val client = NetworkClientProvider.ocrAndLlmClient
        val mediaType = "application/json".toMediaType()

        val payload = JSONObject().apply {
            put("url", url)
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response from content resolver service.")

            if (!response.isSuccessful) {
                val errMessage = try {
                    JSONObject(responseBody).optString("error", "HTTP error ${response.code}")
                } catch (e: Exception) {
                    "HTTP error ${response.code}"
                }
                throw Exception("Failed to resolve URL: $errMessage")
            }

            val json = JSONObject(responseBody)
            val success = json.optBoolean("success", true)
            if (!success) {
                val error = json.optString("error", "Unknown error while resolving URL")
                throw Exception(error)
            }

            val title = json.optString("title", "").trim()
            val content = json.optString("content", "").trim()
            val author = json.optString("author", "").trim()

            // Check if content indicates a login wall
            if (content.contains("protected by Facebook login wall", ignoreCase = true) ||
                content.contains("login wall or privacy restrictions", ignoreCase = true)) {
                if (content.length < 200 && title.isBlank()) {
                    throw LoginWallException("This post is protected by a login wall. Please copy-paste the post text manually.")
                }
            }

            // Combine title, author, and content for maximum context for the Query Parser LLM
            buildString {
                if (title.isNotBlank()) append("Title: ").append(title).append("\n\n")
                if (author.isNotBlank() && author != "Unknown") append("Author: ").append(author).append("\n\n")
                append(content)
            }.trim()
        }
    }
}
