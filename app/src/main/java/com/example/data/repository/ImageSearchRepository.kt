package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.FactCheckDatabase
import com.example.data.model.FavoriteImageResultEntity
import com.example.data.model.ImageSearchHistoryEntity
import com.example.data.model.ImageSearchResult
import com.example.data.model.VisualMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

class ImageSearchRepository(private val context: Context) {

    private val db = FactCheckDatabase.getDatabase(context)
    private val dao = db.imageSearchDao()

    private val okHttpClient = com.example.data.api.NetworkClientProvider.ocrAndLlmClient

    private val baseUrl: String
        get() {
            val raw = BuildConfig.KHOJ_IMAGE_SEARCH_BACKEND.trim().removeSurrounding("\"").removeSurrounding("'")
            return if (raw.endsWith("/")) raw.dropLast(1) else raw
        }

    // ── Backend Health Warmup ──────────────────────────────────────────────────

    suspend fun warmupBackend() = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            okHttpClient.newCall(request).execute().close()
            Log.d("ImageSearchRepo", "Backend health warmup successful.")
        } catch (e: Exception) {
            Log.w("ImageSearchRepo", "Backend health warmup warning: ${e.message}")
        }
    }

    // ── Client-side Image Downscaling & Compression ──────────────────────────

    suspend fun compressImageUri(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val originalBitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val maxDim = Math.max(info.size.width, info.size.height)
                    if (maxDim > 1600) {
                        val scale = 1600f / maxDim
                        val targetW = (info.size.width * scale).toInt()
                        val targetH = (info.size.height * scale).toInt()
                        decoder.setTargetSize(targetW, targetH)
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                val maxDim = Math.max(options.outWidth, options.outHeight)
                var inSampleSize = 1
                if (maxDim > 1600) {
                    while (maxDim / inSampleSize >= 1600) {
                        inSampleSize *= 2
                    }
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    this.inJustDecodeBounds = false
                }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            }

            if (originalBitmap == null) return@withContext null

            val outputStream = ByteArrayOutputStream()
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val compressedBytes = outputStream.toByteArray()
            Log.d("ImageSearchRepo", "Compressed image size: ${compressedBytes.size / 1024} KB")
            compressedBytes
        } catch (e: Exception) {
            Log.e("ImageSearchRepo", "Image compression failed: ${e.message}", e)
            null
        }
    }

    // ── Execute Search ────────────────────────────────────────────────────────

    suspend fun executeImageSearch(
        imageUri: Uri?,
        imageUrl: String?,
        selectedEngine: String,
        onProgressStep: suspend (String) -> Unit
    ): ImageSearchResult = withContext(Dispatchers.IO) {
        onProgressStep("✓ ছবি প্রস্তুত")

        // 1. Prepare payload
        var compressedBytes: ByteArray? = null
        if (imageUri != null) {
            compressedBytes = compressImageUri(imageUri)
        }

        if (compressedBytes == null && imageUrl.isNullOrBlank()) {
            return@withContext ImageSearchResult(
                errorMessage = "অনুগ্রহ করে একটি ছবি নির্বাচন করুন অথবা একটি ভ্যালিড লিংক দিন।"
            )
        }

        onProgressStep("✓ ফাইল আপলোড হচ্ছে")

        // 2. Perform search request
        var currentEngineToTry = selectedEngine
        var isFallbackAttempt = false
        var failedEngineName: String? = null

        var rawResult = performSearchNetworkCall(currentEngineToTry, compressedBytes, imageUrl, onProgressStep)

        // Silent Fallback logic:
        // Case 1: If Google Lens mode failed, automatically retry Yandex
        if (rawResult.errorMessage != null && selectedEngine == "google_lens") {
            Log.w("ImageSearchRepo", "Google Lens failed (${rawResult.errorMessage}). Initiating silent fallback to Yandex...")
            onProgressStep("✓ Yandex-এ পুনরায় অনুসন্ধান হচ্ছে")
            currentEngineToTry = "yandex"
            isFallbackAttempt = true
            failedEngineName = "Google Lens"
            rawResult = performSearchNetworkCall(currentEngineToTry, compressedBytes, imageUrl, onProgressStep)
        }
        // Case 2: If Yandex mode failed, automatically retry Google Lens
        else if (rawResult.errorMessage != null && selectedEngine == "yandex") {
            Log.w("ImageSearchRepo", "Yandex failed (${rawResult.errorMessage}). Initiating silent fallback to Google Lens...")
            onProgressStep("✓ Google Lens-এ পুনরায় অনুসন্ধান হচ্ছে")
            currentEngineToTry = "google_lens"
            isFallbackAttempt = true
            failedEngineName = "Yandex"
            rawResult = performSearchNetworkCall(currentEngineToTry, compressedBytes, imageUrl, onProgressStep)
        }

        if (rawResult.errorMessage != null && rawResult.matches.isEmpty()) {
            return@withContext rawResult
        }

        onProgressStep("✓ ফলাফল সংকলন করা হচ্ছে")

        val result = rawResult.copy(
            usedFallback = isFallbackAttempt,
            failedEngine = if (isFallbackAttempt) failedEngineName else null
        )

        // Save to search history
        runCatching {
            dao.insertHistory(
                ImageSearchHistoryEntity(
                    imageUriOrUrl = imageUrl ?: imageUri?.toString() ?: "Uploaded Image",
                    engineUsed = selectedEngine,
                    matchCount = result.matches.size
                )
            )
        }

        result
    }

    private suspend fun performSearchNetworkCall(
        engineString: String,
        compressedBytes: ByteArray?,
        imageUrl: String?,
        onProgressStep: suspend (String) -> Unit
    ): ImageSearchResult = withContext(Dispatchers.IO) {
        try {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("engines", engineString)
                .addFormDataPart("timeout_seconds", "35")

            if (compressedBytes != null) {
                val mediaType = "image/jpeg".toMediaTypeOrNull()
                val body = compressedBytes.toRequestBody(mediaType)
                builder.addFormDataPart("file", "search_image.jpg", body)
            } else if (!imageUrl.isNullOrBlank()) {
                builder.addFormDataPart("image_url", imageUrl.trim())
            }

            if (engineString == "google_lens,yandex") {
                onProgressStep("✓ Google Lens ও Yandex-এ অনুসন্ধান চলছে")
            } else if (engineString.contains("google_lens")) {
                onProgressStep("✓ Google Lens-এ অনুসন্ধান চলছে")
            } else if (engineString.contains("yandex")) {
                onProgressStep("✓ Yandex-এ অনুসন্ধান চলছে")
            }

            val request = Request.Builder()
                .url("$baseUrl/search")
                .post(builder.build())
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: throw Exception("Empty response from backend")
                if (!response.isSuccessful) {
                    Log.e("ImageSearchRepo", "Search error ${response.code}: $bodyString")
                    return@withContext ImageSearchResult(
                        errorMessage = "সার্ভার সংযোগে ত্রুটি (HTTP ${response.code})"
                    )
                }

                val parsedMatches = parseEngineWiseResponse(bodyString)
                val deduplicatedMatches = deduplicateMatches(parsedMatches)

                var reportedError: String? = null
                if (deduplicatedMatches.isEmpty()) {
                    val root = runCatching { JSONObject(bodyString) }.getOrNull()
                    reportedError = root?.optString("detail", null)
                        ?: root?.optJSONObject(engineString)?.optString("error", null)
                        ?: root?.optJSONObject("google_lens")?.optString("error", null)
                        ?: root?.optJSONObject("yandex")?.optString("error", null)
                }

                ImageSearchResult(matches = deduplicatedMatches, errorMessage = reportedError)
            }
        } catch (e: Exception) {
            Log.e("ImageSearchRepo", "Network search error", e)
            ImageSearchResult(
                errorMessage = e.localizedMessage ?: "নেটওয়ার্ক সংযোগে সমস্যা হয়েছে।"
            )
        }
    }

    // ── Response Parser ───────────────────────────────────────────────────────

    private fun parseEngineWiseResponse(jsonStr: String): List<VisualMatch> {
        val matchesList = mutableListOf<VisualMatch>()
        try {
            val root = JSONObject(jsonStr)

            // 1. Google Lens parsing
            if (root.has("google_lens")) {
                val googleObj = root.optJSONObject("google_lens")
                val visualMatchesArr = googleObj?.optJSONArray("visual_matches") ?: JSONArray()
                for (i in 0 until visualMatchesArr.length()) {
                    val item = visualMatchesArr.optJSONObject(i) ?: continue
                    val title = item.optString("title", "Google Lens Result")
                    val url = item.optString("url", item.optString("link", ""))
                    val thumb = item.optString("thumbnail", item.optString("image", ""))
                    val source = item.optString("source", extractDomain(url))

                    if (url.isNotBlank()) {
                        matchesList.add(
                            VisualMatch(
                                title = title.ifBlank { "Visual Match" },
                                url = url,
                                domain = source.ifBlank { extractDomain(url) },
                                thumbnail = thumb,
                                sourceEngine = "GOOGLE LENS"
                            )
                        )
                    }
                }
            }

            // 2. Yandex parsing
            if (root.has("yandex")) {
                val yandexObj = root.optJSONObject("yandex")
                val rawArr = yandexObj?.optJSONArray("raw")
                    ?: yandexObj?.optJSONArray("results")
                    ?: JSONArray()

                for (i in 0 until rawArr.length()) {
                    val item = rawArr.optJSONObject(i) ?: continue
                    val title = item.optString("title", "Yandex Result")
                    val url = item.optString("url", item.optString("origin_url", ""))
                    val thumb = item.optString("thumbnail", item.optString("thumb", ""))
                    val source = item.optString("source", extractDomain(url))

                    if (url.isNotBlank()) {
                        matchesList.add(
                            VisualMatch(
                                title = title.ifBlank { "Yandex Match" },
                                url = url,
                                domain = source.ifBlank { extractDomain(url) },
                                thumbnail = thumb,
                                sourceEngine = "YANDEX"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ImageSearchRepo", "Error parsing response JSON", e)
        }
        return matchesList
    }

    // ── Deduplication & Normalization ─────────────────────────────────────────

    private fun deduplicateMatches(matches: List<VisualMatch>): List<VisualMatch> {
        val seenUrls = mutableSetOf<String>()
        return matches.filter { match ->
            val normalized = normalizeUrl(match.url)
            seenUrls.add(normalized)
        }
    }

    private fun normalizeUrl(urlStr: String): String {
        return try {
            val uri = URI(urlStr)
            val query = uri.query
            if (query.isNullOrBlank()) return urlStr.lowercase().trimEnd('/')
            val filteredQuery = query.split("&")
                .filter { param ->
                    val key = param.substringBefore("=").lowercase()
                    key !in setOf("utm_source", "utm_medium", "utm_campaign", "gclid", "fbclid", "ref")
                }
                .joinToString("&")
            URI(uri.scheme, uri.authority, uri.path, filteredQuery.ifBlank { null }, null).toString().lowercase().trimEnd('/')
        } catch (e: Exception) {
            urlStr.lowercase().trimEnd('/')
        }
    }

    private fun extractDomain(urlStr: String): String {
        return try {
            var host = URI(urlStr).host ?: return urlStr
            if (host.startsWith("www.")) host = host.substring(4)
            host
        } catch (e: Exception) {
            urlStr
        }
    }

    // ── Room DB Favorites & History Expositions ────────────────────────────────

    val history: Flow<List<ImageSearchHistoryEntity>> = dao.getAllHistory()
    val favorites: Flow<List<FavoriteImageResultEntity>> = dao.getAllFavorites()

    fun isFavorite(url: String): Flow<Boolean> = dao.isFavorite(url)

    suspend fun toggleFavorite(match: VisualMatch) = withContext(Dispatchers.IO) {
        val entity = FavoriteImageResultEntity(
            url = match.url,
            title = match.title,
            domain = match.domain,
            thumbnail = match.thumbnail,
            sourceEngine = match.sourceEngine
        )
        try {
            dao.insertFavorite(entity)
        } catch (e: Exception) {
            dao.deleteFavoriteByUrl(match.url)
        }
    }

    suspend fun removeFavorite(url: String) = withContext(Dispatchers.IO) {
        dao.deleteFavoriteByUrl(url)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearHistory()
    }
}
