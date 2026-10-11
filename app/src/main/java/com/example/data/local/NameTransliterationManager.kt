package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.api.AnalyticsEngine
import java.lang.Character.UnicodeScript
import java.security.MessageDigest
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.nameDataStore by preferencesDataStore(name = "khoj_name_transliteration")

enum class Script {
    BENGALI,
    LATIN,
    MIXED,
    UNKNOWN
}

@Serializable
data class NameTransliterationCache(
    val version: Int = 1,
    val original: String,
    val bn: String,
    val en: String
)

object NameTransliterationManager {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStoreMutex = Mutex()
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<String>>()
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun detectDominantScript(name: String): Script {
        var hasBengali = false
        var hasLatin = false

        var i = 0
        while (i < name.length) {
            val codePoint = name.codePointAt(i)
            val script = UnicodeScript.of(codePoint)
            if (script == UnicodeScript.BENGALI) {
                hasBengali = true
            } else if (script == UnicodeScript.LATIN) {
                hasLatin = true
            }
            i += Character.charCount(codePoint)
        }

        return when {
            hasBengali && hasLatin -> Script.MIXED
            hasBengali -> Script.BENGALI
            hasLatin -> Script.LATIN
            else -> Script.UNKNOWN
        }
    }

    fun hashName(name: String): String {
        val normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFC)
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun getOrTransliterateName(context: Context, originalName: String?, isBn: Boolean): String = withContext(Dispatchers.IO) {
        if (originalName.isNullOrBlank()) return@withContext ""
        val trimmedName = originalName.trim()
        val script = detectDominantScript(trimmedName)

        // Rule: If script is MIXED or UNKNOWN, return original as-is without API call
        if (script == Script.MIXED || script == Script.UNKNOWN) {
            return@withContext trimmedName
        }

        // Rule: If already in requested language script, return original as-is
        if (isBn && script == Script.BENGALI) {
            return@withContext trimmedName
        }
        if (!isBn && script == Script.LATIN) {
            return@withContext trimmedName
        }

        val targetLang = if (isBn) "bn" else "en"
        val nameHash = hashName(trimmedName)
        val prefKey = stringPreferencesKey("name_cache_$nameHash")

        // 1. Check DataStore cache
        val cachedJson = runCatching {
            context.nameDataStore.data.map { prefs -> prefs[prefKey] }.firstOrNull()
        }.getOrNull()

        if (cachedJson != null) {
            val cache = runCatching { json.decodeFromString<NameTransliterationCache>(cachedJson) }.getOrNull()
            if (cache != null && cache.version == 1) {
                val cachedResult = if (isBn) cache.bn else cache.en
                if (cachedResult.isNotBlank()) {
                    return@withContext cachedResult
                }
            }
        }

        // 2. In-flight request deduplication
        val inFlightKey = "$nameHash:$targetLang"
        val deferred = inFlightRequests.computeIfAbsent(inFlightKey) {
            val newDeferred = CompletableDeferred<String>()
            managerScope.launch {
                try {
                    val aiResult = AnalyticsEngine.transliterateName(trimmedName, targetLang)
                        .getOrDefault(trimmedName)

                    // Dual cache creation: Populates both languages in one object
                    val cacheObj = if (script == Script.LATIN) {
                        NameTransliterationCache(
                            version = 1,
                            original = trimmedName,
                            bn = if (isBn) aiResult else trimmedName,
                            en = trimmedName
                        )
                    } else {
                        NameTransliterationCache(
                            version = 1,
                            original = trimmedName,
                            bn = trimmedName,
                            en = if (!isBn) aiResult else trimmedName
                        )
                    }

                    // Thread-safe DataStore write
                    dataStoreMutex.withLock {
                        runCatching {
                            context.nameDataStore.edit { prefs ->
                                prefs[prefKey] = json.encodeToString(cacheObj)
                            }
                        }.onFailure { e ->
                            android.util.Log.e("NameTransliterator", "DataStore save error: ${e.message}")
                        }
                    }

                    val finalResult = if (isBn) cacheObj.bn else cacheObj.en
                    newDeferred.complete(finalResult)
                } catch (e: Throwable) {
                    newDeferred.complete(trimmedName)
                } finally {
                    inFlightRequests.remove(inFlightKey)
                }
            }
            newDeferred
        }

        try {
            deferred.await()
        } catch (e: Throwable) {
            trimmedName
        }
    }

    /**
     * Best-effort async precompute during auth/profile sync.
     * Never blocks or throws errors.
     */
    fun precomputeAsync(context: Context, originalName: String?) {
        if (originalName.isNullOrBlank()) return
        managerScope.launch {
            runCatching {
                val trimmed = originalName.trim()
                val script = detectDominantScript(trimmed)
                if (script == Script.LATIN) {
                    getOrTransliterateName(context, trimmed, isBn = true)
                } else if (script == Script.BENGALI) {
                    getOrTransliterateName(context, trimmed, isBn = false)
                }
            }
        }
    }
}
