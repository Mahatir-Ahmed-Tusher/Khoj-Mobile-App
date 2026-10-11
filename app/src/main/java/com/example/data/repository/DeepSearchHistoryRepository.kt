package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.api.KhojSupabaseClient
import com.example.data.model.DeepSearchReport
import com.example.data.model.DeepSearchSource
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException

val Context.deepSearchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "deep_search_history_store")

@Serializable
private data class RemoteDeepSearchHistoryRow(
    val id: String,
    val user_id: String? = null,
    val query: String = "",
    val report_content: String = "",
    val sources: JsonArray? = null,
    val detected_intent: String? = null,
    val visibility: String = "public",
    val status: String = "completed",
    val created_at: String = "",
    val updated_at: String = ""
)

class DeepSearchHistoryRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    companion object {
        private const val TAG = "DeepSearchHistoryRepo"
        private val HISTORY_KEY = stringPreferencesKey("deep_search_reports_json")
        private const val MAX_HISTORY_ITEMS = 150

        @Volatile
        private var INSTANCE: DeepSearchHistoryRepository? = null

        fun getInstance(context: Context): DeepSearchHistoryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeepSearchHistoryRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Observable stream of reports from the local cache mirror.
     */
    val historyFlow: Flow<List<DeepSearchReport>> = context.deepSearchHistoryDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val rawJson = preferences[HISTORY_KEY]
            if (rawJson.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    val list = json.decodeFromString<List<DeepSearchReport>>(rawJson)
                    list.sortedByDescending { it.timestamp }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

    /**
     * Helper to parse JSON sources array into typed DeepSearchSource objects.
     */
    private fun parseSources(sourcesArray: JsonArray?): List<DeepSearchSource> {
        val parsedSources = mutableListOf<DeepSearchSource>()
        sourcesArray?.forEachIndexed { index, elem ->
            try {
                val obj = elem.jsonObject
                val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                val snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull ?: ""
                val domain = obj["domain"]?.jsonPrimitive?.contentOrNull
                    ?: try { if (url.isNotBlank()) java.net.URI(url).host?.removePrefix("www.") ?: "" else "" } catch (ex: Exception) { "" }
                parsedSources.add(
                    DeepSearchSource(
                        citationId = index + 1,
                        title = if (title.isNotBlank()) title else domain.ifBlank { "Source #${index + 1}" },
                        domain = domain,
                        url = url,
                        snippet = snippet
                    )
                )
            } catch (_: Exception) {}
        }
        return parsedSources
    }

    /**
     * Merge Sync Policy:
     * Pulls remote reports from Supabase, merges them with local DataStore cache (keyed on ID),
     * and pushes any local unsynced reports to Supabase when authenticated.
     */
    suspend fun syncWithRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = AuthRepository.currentUserId
        if (userId == null) {
            android.util.Log.d(TAG, "[DeepSearch:Sync] Skipped: No authenticated user")
            return@withContext Result.success(Unit)
        }

        runCatching {
            android.util.Log.d(TAG, "[DeepSearch:Sync] Starting sync for user: $userId")
            
            // 1. Fetch remote reports
            val remoteRows = runCatching {
                KhojSupabaseClient.client.postgrest.rpc(
                    "get_user_deep_search_history",
                    buildJsonObject {
                        put("p_user_id", userId)
                        put("p_limit", MAX_HISTORY_ITEMS)
                    }
                ).decodeList<RemoteDeepSearchHistoryRow>()
            }.getOrElse { e ->
                android.util.Log.w(TAG, "[DeepSearch:Sync] RPC get_user_deep_search_history failed, falling back to table query: ${e.message}")
                KhojSupabaseClient.client.postgrest["deep_search_reports"]
                    .select {
                        filter {
                            eq("user_id", userId)
                            exact("deleted_at", null)
                        }
                        limit(MAX_HISTORY_ITEMS.toLong())
                    }.decodeList<RemoteDeepSearchHistoryRow>()
            }

            android.util.Log.d(TAG, "[DeepSearch:Sync] Retrieved ${remoteRows.size} remote rows from Supabase")

            val mappedRemoteReports = remoteRows.map { row ->
                val timestampMs = try {
                    java.time.Instant.parse(row.created_at).toEpochMilli()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }

                DeepSearchReport(
                    id = row.id,
                    query = row.query,
                    reportContent = row.report_content,
                    sources = parseSources(row.sources),
                    detectedIntent = row.detected_intent,
                    timestamp = timestampMs,
                    isPublic = row.visibility.equals("public", ignoreCase = true),
                    userId = row.user_id ?: userId
                )
            }

            // 2. Read existing local reports
            val currentLocalReports = historyFlow.first()
            val remoteIdSet = mappedRemoteReports.map { it.id }.toSet()

            // 3. Find local reports that need uploading to Supabase (e.g. created while offline/guest)
            val unsyncedLocalReports = currentLocalReports.filter { it.id !in remoteIdSet }
            if (unsyncedLocalReports.isNotEmpty()) {
                android.util.Log.d(TAG, "[DeepSearch:Sync] Uploading ${unsyncedLocalReports.size} unsynced local reports to Supabase")
                unsyncedLocalReports.forEach { localReport ->
                    runCatching {
                        saveReport(localReport.copy(userId = userId))
                    }.onFailure { e ->
                        android.util.Log.e(TAG, "[DeepSearch:Sync] Failed to upload local report ${localReport.id}: ${e.message}")
                    }
                }
            }

            // 4. Merge: Combine remote + local without wiping
            val mergedMap = LinkedHashMap<String, DeepSearchReport>()
            mappedRemoteReports.forEach { mergedMap[it.id] = it }
            currentLocalReports.forEach { local ->
                if (!mergedMap.containsKey(local.id)) {
                    mergedMap[local.id] = local
                }
            }

            val finalMergedList = mergedMap.values
                .sortedByDescending { it.timestamp }
                .take(MAX_HISTORY_ITEMS)

            context.deepSearchHistoryDataStore.edit { preferences ->
                preferences[HISTORY_KEY] = json.encodeToString(finalMergedList)
            }
            android.util.Log.d(TAG, "[DeepSearch:Sync] Successfully synced. Total merged items in cache: ${finalMergedList.size}")
            Unit
        }.onFailure { e ->
            android.util.Log.e(TAG, "[DeepSearch:Sync] Sync failure: ${e.message}", e)
        }
    }

    /**
     * Persists report optimistically locally and reliably to Supabase cloud.
     */
    suspend fun saveReport(report: DeepSearchReport): Result<Unit> = withContext(Dispatchers.IO) {
        if (report.query.isBlank() || report.reportContent.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Report query or content is blank"))
        }

        android.util.Log.d(TAG, "[DeepSearch:Save] Saving report ${report.id} for query: '${report.query.take(30)}...'")

        // 1. Optimistically write to local DataStore cache
        context.deepSearchHistoryDataStore.edit { preferences ->
            val rawJson = preferences[HISTORY_KEY]
            val currentList = if (rawJson.isNullOrBlank()) {
                mutableListOf()
            } else {
                try {
                    json.decodeFromString<List<DeepSearchReport>>(rawJson).toMutableList()
                } catch (e: Exception) {
                    mutableListOf()
                }
            }

            currentList.removeAll { it.id == report.id || it.query.trim().equals(report.query.trim(), ignoreCase = true) }
            currentList.add(0, report)

            val trimmedList = if (currentList.size > MAX_HISTORY_ITEMS) {
                currentList.take(MAX_HISTORY_ITEMS)
            } else {
                currentList
            }

            preferences[HISTORY_KEY] = json.encodeToString(trimmedList)
        }

        // 2. Persist to Supabase cloud
        val userId = report.userId ?: AuthRepository.currentUserId
        if (userId != null) {
            return@withContext runCatching {
                // Ensure the user's profile exists in public.users to satisfy FK constraint
                val currentUser = KhojSupabaseClient.client.auth.currentUserOrNull()
                if (currentUser != null && currentUser.email != null) {
                    runCatching {
                        AuthRepository.upsertProfile(
                            displayName = currentUser.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull ?: currentUser.email?.substringBefore("@"),
                            email = currentUser.email ?: "",
                            photoUrl = currentUser.userMetadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                        )
                    }.onFailure { pe ->
                        android.util.Log.w(TAG, "[DeepSearch:Save] Profile upsert check had non-fatal warning: ${pe.message}")
                    }
                }

                val sourcesJson = buildJsonArray {
                    report.sources.forEach { s ->
                        add(buildJsonObject {
                            put("citation_id", s.citationId)
                            put("title", s.title)
                            put("domain", s.domain)
                            put("url", s.url)
                            put("snippet", s.snippet)
                        })
                    }
                }

                // Insert/upsert into deep_search_reports
                KhojSupabaseClient.client.postgrest["deep_search_reports"].upsert(
                    buildJsonObject {
                        put("id", report.id)
                        put("user_id", userId)
                        put("query", report.query)
                        put("report_content", report.reportContent)
                        put("sources", sourcesJson)
                        if (report.detectedIntent != null) {
                            put("detected_intent", report.detectedIntent)
                        }
                        put("visibility", if (report.isPublic) "public" else "private")
                        put("status", "completed")
                    }
                )
                android.util.Log.d(TAG, "[DeepSearch:Save] Successfully persisted report ${report.id} to Supabase deep_search_reports")

                // Also record the search query in user_search_history so it appears in unified profile history
                runCatching {
                    KhojSupabaseClient.client.postgrest["user_search_history"].upsert(
                        buildJsonObject {
                            put("id", report.id)
                            put("user_id", userId)
                            put("query", report.query)
                            put("search_type", "deepsearch")
                            put("report_id", report.id)
                        }
                    )
                    android.util.Log.d(TAG, "[DeepSearch:Save] Successfully recorded search in user_search_history")
                }.onFailure { ue ->
                    android.util.Log.w(TAG, "[DeepSearch:Save] Non-fatal: Failed to record in user_search_history: ${ue.message}")
                }

                Unit
            }.onFailure { e ->
                android.util.Log.e(TAG, "[DeepSearch:Save] FAILED to persist report ${report.id} to Supabase: ${e.message}", e)
            }
        } else {
            android.util.Log.d(TAG, "[DeepSearch:Save] Report ${report.id} saved locally only (user is guest)")
            Result.success(Unit)
        }
    }

    /**
     * Hydrates full report content from Supabase cloud by ID, falling back to local cache.
     */
    suspend fun fetchReportById(reportId: String): DeepSearchReport? = withContext(Dispatchers.IO) {
        if (reportId.isBlank()) return@withContext null

        android.util.Log.d(TAG, "[DeepSearch:Hydrate] Fetching full report for ID: $reportId")

        // 1. Check local cache first
        val cached = historyFlow.first().find { it.id == reportId }
        if (cached != null && !cached.isFeedPreview && cached.reportContent.length > 350) {
            android.util.Log.d(TAG, "[DeepSearch:Hydrate] Found full report in local cache")
            return@withContext cached
        }

        // 2. Fetch directly from Supabase
        runCatching {
            val row = KhojSupabaseClient.client.postgrest["deep_search_reports"]
                .select {
                    filter { eq("id", reportId) }
                    limit(1)
                }.decodeSingleOrNull<RemoteDeepSearchHistoryRow>()

            if (row != null) {
                val timestampMs = try {
                    java.time.Instant.parse(row.created_at).toEpochMilli()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }

                val fullReport = DeepSearchReport(
                    id = row.id,
                    query = row.query,
                    reportContent = row.report_content,
                    sources = parseSources(row.sources),
                    detectedIntent = row.detected_intent,
                    timestamp = timestampMs,
                    isPublic = row.visibility.equals("public", ignoreCase = true),
                    userId = row.user_id,
                    isFeedPreview = false
                )

                // Update local cache with full hydrated content
                saveReport(fullReport)
                android.util.Log.d(TAG, "[DeepSearch:Hydrate] Successfully hydrated full report from Supabase (${fullReport.reportContent.length} chars)")
                fullReport
            } else {
                android.util.Log.w(TAG, "[DeepSearch:Hydrate] Report $reportId not found in Supabase")
                cached
            }
        }.getOrElse { e ->
            android.util.Log.e(TAG, "[DeepSearch:Hydrate] Failed to fetch report $reportId from Supabase: ${e.message}")
            cached
        }
    }

    suspend fun deleteReport(id: String) = withContext(Dispatchers.IO) {
        android.util.Log.d(TAG, "[DeepSearch:Delete] Deleting report $id")
        context.deepSearchHistoryDataStore.edit { preferences ->
            val rawJson = preferences[HISTORY_KEY] ?: return@edit
            try {
                val currentList = json.decodeFromString<List<DeepSearchReport>>(rawJson).toMutableList()
                currentList.removeAll { it.id == id }
                preferences[HISTORY_KEY] = json.encodeToString(currentList)
            } catch (_: Exception) {}
        }

        val userId = AuthRepository.currentUserId
        if (userId != null) {
            runCatching {
                KhojSupabaseClient.client.postgrest["deep_search_reports"]
                    .delete {
                        filter {
                            eq("id", id)
                            eq("user_id", userId)
                        }
                    }
                KhojSupabaseClient.client.postgrest["user_search_history"]
                    .delete {
                        filter {
                            eq("report_id", id)
                            eq("user_id", userId)
                        }
                    }
            }.onFailure { e ->
                android.util.Log.e(TAG, "[DeepSearch:Delete] Failed to erase remote report: ${e.message}")
            }
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        android.util.Log.d(TAG, "[DeepSearch:Clear] Clearing all reports")
        context.deepSearchHistoryDataStore.edit { preferences ->
            preferences.remove(HISTORY_KEY)
        }

        val userId = AuthRepository.currentUserId
        if (userId != null) {
            runCatching {
                KhojSupabaseClient.client.postgrest["deep_search_reports"]
                    .delete {
                        filter {
                            eq("user_id", userId)
                        }
                    }
            }.onFailure { e ->
                android.util.Log.e(TAG, "[DeepSearch:Clear] Failed to clear remote reports: ${e.message}")
            }
        }
    }

    suspend fun getReportById(id: String): DeepSearchReport? {
        return historyFlow.first().find { it.id == id }
    }
}

