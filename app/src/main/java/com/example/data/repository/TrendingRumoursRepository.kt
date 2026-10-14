package com.example.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import com.example.data.api.SupabaseClient
import com.example.data.api.TrendingRumoursClient
import com.example.data.model.TrendingRegion
import com.example.data.model.TrendingRumourUi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TrendingRumoursRepository(private val prefs: SharedPreferences) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun getBdSourcePriority(source: String): Int {
        val s = source.lowercase().trim()
        return when {
            s.contains("fact") && s.contains("watch") || s.contains("ফ্যাক্ট") -> 1
            s.contains("dissent") || s.contains("ডিসেন্ট") -> 2
            s.contains("rumor") || s.contains("rumour") || s.contains("রিউমার") -> 3
            else -> 4
        }
    }

    private fun sortRumoursForRegion(region: TrendingRegion, list: List<TrendingRumourUi>): List<TrendingRumourUi> {
        return if (region == TrendingRegion.BD) {
            list.sortedBy { getBdSourcePriority(it.source) }
        } else {
            list
        }
    }

    fun getCachedRumours(region: TrendingRegion): List<TrendingRumourUi> {
        val key = "trending_rumours_cache_${region.name}"
        val cacheStr = prefs.getString(key, null)
        if (!cacheStr.isNullOrBlank()) {
            return try {
                val decoded = json.decodeFromString<List<TrendingRumourUi>>(cacheStr)
                sortRumoursForRegion(region, decoded)
            } catch (e: Exception) {
                Log.e("TrendingRumours", "Failed to decode cache", e)
                emptyList()
            }
        }
        return emptyList()
    }

    fun getLastSyncTime(region: TrendingRegion): Long {
        return prefs.getLong("trending_rumours_last_sync_${region.name}", 0L)
    }

    fun saveCachedRumours(region: TrendingRegion, rumours: List<TrendingRumourUi>) {
        val key = "trending_rumours_cache_${region.name}"
        val timeKey = "trending_rumours_last_sync_${region.name}"
        try {
            val cacheStr = json.encodeToString(rumours)
            prefs.edit()
                .putString(key, cacheStr)
                .putLong(timeKey, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e("TrendingRumours", "Failed to encode cache", e)
        }
    }

    suspend fun fetchTrendingRumours(region: TrendingRegion): List<TrendingRumourUi> {
        // 1. Try Cloud Run backend first (fast timeout configured on the client).
        try {
            val response = TrendingRumoursClient.api.getTrendingRumours(region.apiValue)

            if (response.rumours.isNotEmpty()) {
                val list = response.rumours.map {
                    TrendingRumourUi(
                        source = it.source,
                        title = it.title,
                        url = it.url,
                        timestamp = it.fetchedAt,
                        thumbnail = it.thumbnail
                    )
                }
                val sorted = sortRumoursForRegion(region, list)
                saveCachedRumours(region, sorted)
                return sorted
            }
            Log.w("TrendingRumours", "Cloud Run returned empty list, falling back to Supabase")
        } catch (e: Exception) {
            Log.w("TrendingRumours", "Cloud Run fetch failed, falling back to Supabase", e)
        }

        // 2. Cloud Run failed or returned nothing — fall back to Supabase safely.
        try {
            val supabaseResult = SupabaseClient.api.getTrendingRumours(apiKey = BuildConfig.SUPABASE_ANON_KEY)
            val list = supabaseResult.map {
                TrendingRumourUi(
                    source = it.source,
                    title = it.title,
                    url = it.url,
                    timestamp = it.publishedAt,
                    thumbnail = null
                )
            }
            val sorted = sortRumoursForRegion(region, list)
            if (sorted.isNotEmpty()) {
                saveCachedRumours(region, sorted)
                return sorted
            }
        } catch (e: Exception) {
            Log.w("TrendingRumours", "Supabase fetch failed", e)
        }

        // 3. Fall back to cached rumours (offline mode)
        return getCachedRumours(region)
    }
}
