package com.example.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.example.data.api.RumorMeterClient
import com.example.data.model.CanonicalClaimItem
import com.example.data.model.DistrictHotspotItem
import com.example.data.model.MediaSpreadItem
import com.example.data.model.NarrativeItem
import com.example.data.model.RumorMeterData
import com.example.data.model.RumorTrendsData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RumorMeterRepository(private val prefs: SharedPreferences) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "RumorMeterRepo"
        private const val KEY_METER_DATA = "cache_rumor_meter_data"
        private const val KEY_TRENDS_DATA = "cache_rumor_trends_data"
        private const val KEY_NARRATIVES_DATA = "cache_rumor_narratives_data"
        private const val KEY_HOTSPOTS_DATA = "cache_rumor_hotspots_data"
        private const val KEY_MEDIA_DATA = "cache_rumor_media_data"
        private const val KEY_CLAIMS_DATA = "cache_rumor_claims_data"
        private const val KEY_LAST_SYNC = "cache_rumor_meter_last_sync"
    }

    fun getCachedMeterData(): RumorMeterData? {
        val raw = prefs.getString(KEY_METER_DATA, null) ?: return null
        return try {
            json.decodeFromString<RumorMeterData>(raw)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to decode cached meter data", e)
            null
        }
    }

    fun saveCachedMeterData(data: RumorMeterData) {
        try {
            val encoded = json.encodeToString(data)
            prefs.edit()
                .putString(KEY_METER_DATA, encoded)
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to encode meter data for caching", e)
        }
    }

    fun getCachedTrendsData(): RumorTrendsData? {
        val raw = prefs.getString(KEY_TRENDS_DATA, null) ?: return null
        return try {
            json.decodeFromString<RumorTrendsData>(raw)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to decode cached trends data", e)
            null
        }
    }

    fun saveCachedTrendsData(data: RumorTrendsData) {
        try {
            val encoded = json.encodeToString(data)
            prefs.edit().putString(KEY_TRENDS_DATA, encoded).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to encode trends data", e)
        }
    }

    fun getCachedNarratives(): List<NarrativeItem> {
        val raw = prefs.getString(KEY_NARRATIVES_DATA, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<NarrativeItem>>(raw)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun saveCachedNarratives(list: List<NarrativeItem>) {
        try {
            prefs.edit().putString(KEY_NARRATIVES_DATA, json.encodeToString(list)).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to encode narratives", e)
        }
    }

    fun getCachedHotspots(): List<DistrictHotspotItem> {
        val raw = prefs.getString(KEY_HOTSPOTS_DATA, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<DistrictHotspotItem>>(raw)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun saveCachedHotspots(list: List<DistrictHotspotItem>) {
        try {
            prefs.edit().putString(KEY_HOTSPOTS_DATA, json.encodeToString(list)).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to encode hotspots", e)
        }
    }

    fun getCachedMediaSpread(): List<MediaSpreadItem> {
        val raw = prefs.getString(KEY_MEDIA_DATA, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<MediaSpreadItem>>(raw)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun saveCachedMediaSpread(list: List<MediaSpreadItem>) {
        try {
            prefs.edit().putString(KEY_MEDIA_DATA, json.encodeToString(list)).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to encode media spread", e)
        }
    }

    fun getCachedClaims(): List<CanonicalClaimItem> {
        val raw = prefs.getString(KEY_CLAIMS_DATA, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<CanonicalClaimItem>>(raw)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun saveCachedClaims(list: List<CanonicalClaimItem>) {
        try {
            prefs.edit().putString(KEY_CLAIMS_DATA, json.encodeToString(list)).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to encode claims", e)
        }
    }

    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC, 0L)
    }

    suspend fun fetchMeterStats(): RumorMeterData {
        val resp = RumorMeterClient.api.getMeterStats()
        if (resp.success && resp.data != null) {
            saveCachedMeterData(resp.data)
            return resp.data
        }
        throw RuntimeException(resp.error ?: "Failed to fetch rumor meter stats")
    }

    suspend fun fetchTrends(): RumorTrendsData {
        val resp = RumorMeterClient.api.getTrends()
        if (resp.success && resp.data != null) {
            saveCachedTrendsData(resp.data)
            return resp.data
        }
        throw RuntimeException(resp.error ?: "Failed to fetch rumor trends")
    }

    suspend fun fetchNarratives(limit: Int = 15): List<NarrativeItem> {
        val resp = RumorMeterClient.api.getNarratives(limit)
        if (resp.success) {
            saveCachedNarratives(resp.data)
            return resp.data
        }
        return emptyList()
    }

    suspend fun fetchHotspots(): List<DistrictHotspotItem> {
        val resp = RumorMeterClient.api.getHotspots()
        if (resp.success) {
            saveCachedHotspots(resp.data)
            return resp.data
        }
        return emptyList()
    }

    suspend fun fetchMediaSpread(): List<MediaSpreadItem> {
        val resp = RumorMeterClient.api.getMediaSpread()
        if (resp.success) {
            saveCachedMediaSpread(resp.data)
            return resp.data
        }
        return emptyList()
    }

    suspend fun fetchClaims(
        page: Int = 1,
        limit: Int = 20,
        query: String? = null,
        topic: String? = null,
        zombie: Boolean? = null,
        crossBorder: Boolean? = null,
        genderTargeted: Boolean? = null
    ): List<CanonicalClaimItem> {
        val resp = RumorMeterClient.api.getClaims(
            page = page,
            limit = limit,
            query = query,
            topic = topic,
            zombie = zombie,
            crossBorder = crossBorder,
            genderTargeted = genderTargeted
        )
        if (resp.success) {
            if (page == 1 && query.isNullOrBlank() && topic == null && zombie == null && crossBorder == null && genderTargeted == null) {
                saveCachedClaims(resp.data)
            }
            return resp.data
        }
        return emptyList()
    }
}
