package com.example.data.repository

import android.content.Context
import com.example.data.api.FactCheckEngine
import com.example.data.api.FactCheckProgress
import com.example.data.local.FactCheckDatabase
import com.example.data.model.FactCheckReport
import com.example.data.model.SupabaseReportInsert
import com.example.data.model.UserSearchHistoryInsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import com.example.data.local.CachePolicy
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.example.data.preferences.ResponseLanguage

class FactCheckRepository(private val context: Context) {
    private val database = FactCheckDatabase.getDatabase(context)
    private val dao = database.dao()
    private val cmsDao = database.cmsArticleDao()
    private val engine = FactCheckEngine(context)

    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    // Expose local reports reactively
    val allReports: Flow<List<FactCheckReport>> = dao.getAllReports()

    // Search reports
    fun searchReports(query: String): Flow<List<FactCheckReport>> {
        return dao.searchReports("%$query%")
    }

    // Run remote fact check and automatically insert the success report into Room!
    fun performFactCheck(
        claim: String, 
        responseLanguage: ResponseLanguage = ResponseLanguage.AUTO,
        isFromImage: Boolean = false
    ): Flow<FactCheckProgress> {
        return flow {
            engine.factCheckStream(claim, responseLanguage, isFromImage).collect { progress ->
                val finalProgress = if (progress is FactCheckProgress.Success) {
                    val currentUserId = AuthRepository.currentUserId

                    // 1. Save to local database (Immediate UI feedback with 0ms delay)
                    val localReport = progress.report.copy(
                        userId = currentUserId,
                        isPublic = true
                    )
                    dao.insertReport(localReport)

                    // 2. Sync to Supabase asynchronously in background (Non-blocking)
                    if (!currentUserId.isNullOrEmpty()) {
                        repositoryScope.launch {
                            runCatching {
                                // Ensure user profile exists in public.users table to satisfy FK constraint
                                val currentUser = AuthRepository.getCurrentUser()
                                if (currentUser != null) {
                                    AuthRepository.upsertProfile(
                                        displayName = currentUser.displayName,
                                        email = currentUser.email ?: "",
                                        photoUrl = currentUser.photoUrl
                                    )
                                }

                                val safeVerdict = normalizeVerdictForDB(progress.report.verdict)
                                val localSources = engine.deserializeSources(progress.report.sourcesJson)
                                val supabaseSources = localSources.map { s ->
                                    com.example.data.model.SupabaseSource(
                                        url = s.url,
                                        title = s.title,
                                        snippet = s.snippet,
                                        tier = s.credibilityTier
                                    )
                                }
                                val fullMarkdownContent = progress.report.markdownReport?.takeIf { it.isNotBlank() }
                                    ?: progress.report.getEffectiveMarkdown()
                                val supabaseReport = SupabaseReportInsert(
                                    id = progress.report.id,
                                    userId = currentUserId,
                                    originalQuery = progress.report.claimText,
                                    verdict = safeVerdict,
                                    confidenceScore = progress.report.confidence.toDouble() / 100.0,
                                    language = if (responseLanguage == ResponseLanguage.ENGLISH) "en" else "bn",
                                    reportContent = fullMarkdownContent,
                                    sources = supabaseSources,
                                    isPublic = true
                                )
                                com.example.data.api.KhojSupabaseClient.client
                                    .postgrest["factcheck_reports"]
                                    .insert(supabaseReport)

                                // Insert into user_search_history for cross-platform profile syncing
                                val searchHistory = UserSearchHistoryInsert(
                                    userId = currentUserId,
                                    query = progress.report.claimText,
                                    searchType = "factcheck",
                                    reportId = progress.report.id
                                )
                                com.example.data.api.KhojSupabaseClient.client
                                    .postgrest["user_search_history"]
                                    .insert(searchHistory)
                            }.onFailure { e ->
                                android.util.Log.e("FactCheckRepository", "Failed to sync to Supabase in background: ${e.message}")
                            }
                        }
                    }
                    FactCheckProgress.Success(localReport)
                } else {
                    progress
                }
                emit(finalProgress)
            }
        }
    }

    private fun normalizeVerdictForDB(rawVerdict: String?): String {
        return when (rawVerdict?.lowercase()?.trim()) {
            "true", "partially_true" -> "true"
            "false", "untrue", "debunk" -> "false"
            else -> "misleading"
        }
    }

    // Get report by ID
    suspend fun getReportById(id: String): FactCheckReport? {
        return dao.getReportById(id)
    }

    // Delete a report permanently across all local and remote layers
    suspend fun deleteReport(id: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // 1. Delete from local Room database immediately
        dao.deleteReportById(id)
        
        // 2. Delete from Supabase remote database concurrently with foreign key safety
        val userId = AuthRepository.currentUserId
        if (userId != null) {
            runCatching {
                kotlinx.coroutines.coroutineScope {
                    val tasks = listOf(
                        async {
                            runCatching {
                                com.example.data.api.KhojSupabaseClient.client.postgrest["post_likes"]
                                    .delete { filter { eq("post_id", id) } }
                            }
                        },
                        async {
                            runCatching {
                                com.example.data.api.KhojSupabaseClient.client.postgrest["post_comments"]
                                    .delete { filter { eq("post_id", id) } }
                            }
                        },
                        async {
                            runCatching {
                                com.example.data.api.KhojSupabaseClient.client.postgrest["user_search_history"]
                                    .delete {
                                        filter {
                                            eq("report_id", id)
                                            eq("user_id", userId)
                                        }
                                    }
                            }
                        },
                        async {
                            runCatching {
                                com.example.data.api.KhojSupabaseClient.client.postgrest["user_search_history"]
                                    .delete {
                                        filter {
                                            eq("id", id)
                                            eq("user_id", userId)
                                        }
                                    }
                            }
                        },
                        async {
                            runCatching {
                                com.example.data.api.KhojSupabaseClient.client.postgrest["factcheck_reports"]
                                    .delete {
                                        filter {
                                            eq("id", id)
                                            eq("user_id", userId)
                                        }
                                    }
                            }
                        },
                        async {
                            runCatching {
                                com.example.data.api.KhojSupabaseClient.client.postgrest["deep_search_reports"]
                                    .delete {
                                        filter {
                                            eq("id", id)
                                            eq("user_id", userId)
                                        }
                                    }
                            }
                        }
                    )
                    tasks.awaitAll()
                }
            }.onFailure { e ->
                android.util.Log.e("FactCheckRepository", "Error during concurrent remote report deletion: ${e.message}")
            }
        }
    }

    // Clear history
    suspend fun clearHistory() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        dao.clearAll()
        
        val userId = AuthRepository.currentUserId
        if (userId != null) {
            runCatching {
                com.example.data.api.KhojSupabaseClient.client.postgrest["user_search_history"]
                    .delete {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                com.example.data.api.KhojSupabaseClient.client.postgrest["factcheck_reports"]
                    .delete {
                        filter {
                            eq("user_id", userId)
                        }
                    }
            }.onFailure { e ->
                android.util.Log.e("FactCheckRepository", "Error during remote history clear: ${e.message}")
            }
        }
    }

    // Access raw engine configuration helper
    fun isCustomPipelineConfigured(): Boolean {
        return engine.isCustomPipelineConfigured()
    }

    // Fetch remote history reports from Supabase, prioritizing local Room entities when more complete
    suspend fun getRemoteHistoryReports(userId: String): List<FactCheckReport> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            // 1. Fetch user search history list
            val historyList = com.example.data.api.KhojSupabaseClient.client
                .postgrest["user_search_history"]
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(50)
                }
                .decodeList<com.example.data.model.UserSearchHistory>()

            if (historyList.isEmpty()) return@runCatching emptyList()

            // 2. Fetch corresponding factcheck_reports
            val reportIds = historyList.mapNotNull { it.reportId }.filter { it.isNotEmpty() }
            if (reportIds.isEmpty()) return@runCatching emptyList()

            val remoteReports = com.example.data.api.KhojSupabaseClient.client
                .postgrest["factcheck_reports"]
                .select {
                    filter {
                        isIn("id", reportIds)
                    }
                }
                .decodeList<com.example.data.model.SupabaseReport>()

            // 3. Map SupabaseReport entries to FactCheckReport entries, prioritizing local Room reports when available
            val reportsMap = remoteReports.associateBy { it.id }
            historyList.mapNotNull { history ->
                reportsMap[history.reportId]?.let { supabaseReport ->
                    // Prioritize local Room database report if it has a complete markdown body
                    val localReport = dao.getReportById(supabaseReport.id)
                    if (localReport != null && !localReport.markdownReport.isNullOrBlank() &&
                        localReport.markdownReport.length > (supabaseReport.reportContent?.length ?: 0)) {
                        localReport
                    } else {
                        // Serialize sources to JSON string format used by local model
                        val sourcesArr = org.json.JSONArray()
                        supabaseReport.sources?.forEach { element ->
                            try {
                                val obj = element.jsonObject
                                val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
                                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                                val snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull ?: ""
                                val score = obj["credibilityScore"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                                val tier = if (score != null) {
                                    if (score >= 0.8) 1 else if (score >= 0.6) 2 else 3
                                } else {
                                    obj["tier"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 3
                                }
                                sourcesArr.put(org.json.JSONObject().apply {
                                    put("title", title)
                                    put("url", url)
                                    put("snippet", snippet)
                                    put("domain", try { java.net.URI(url).host ?: "" } catch(e: Exception) { "" })
                                    put("credibilityTier", tier)
                                    put("publishDate", "")
                                })
                            } catch (e: Exception) {
                                // ignore malformed elements
                            }
                        }

                        FactCheckReport(
                            id = supabaseReport.id,
                            claimText = supabaseReport.processedQuery ?: supabaseReport.originalQuery,
                            normalizedClaim = supabaseReport.processedQuery ?: supabaseReport.originalQuery,
                            verdict = supabaseReport.verdict,
                            confidence = (supabaseReport.confidenceScore * 100).toInt(),
                            summaryBn = supabaseReport.reportContent ?: "",
                            summaryEn = supabaseReport.reportContent ?: "",
                            reasoningBn = supabaseReport.reportContent ?: "",
                            reasoningEn = supabaseReport.reportContent ?: "",
                            markdownReport = supabaseReport.reportContent,
                            sourcesJson = sourcesArr.toString(),
                            contradictingSourcesJson = "[]",
                            isPublic = supabaseReport.isPublic ?: true,
                            userId = supabaseReport.userId,
                            timestamp = try {
                                val isoString = history.createdAt ?: supabaseReport.createdAt
                                if (isoString != null) {
                                    val cleanString = isoString.replace("Z", "+0000")
                                    val formatPattern = if (cleanString.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSSZ" else "yyyy-MM-dd'T'HH:mm:ssZ"
                                    val sdf = java.text.SimpleDateFormat(formatPattern, java.util.Locale.US)
                                    sdf.parse(cleanString)?.time ?: System.currentTimeMillis()
                                } else {
                                    System.currentTimeMillis()
                                }
                            } catch (e: Throwable) {
                                System.currentTimeMillis()
                            }
                        )
                    }
                }
            }
        }.getOrElse { e ->
            android.util.Log.e("FactCheckRepository", "Failed to fetch remote history: ${e.message}")
            emptyList()
        }
    }

    suspend fun updateReportPrivacy(id: String, isPublic: Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // 1. Update Room local DB
        val localReport = dao.getReportById(id)
        if (localReport != null) {
            dao.insertReport(localReport.copy(isPublic = isPublic))
        }

        // 2. Update Supabase factcheck_reports
        val userId = AuthRepository.currentUserId
        if (userId != null) {
            runCatching {
                com.example.data.api.KhojSupabaseClient.client.postgrest["factcheck_reports"]
                    .update(mapOf("is_public" to isPublic)) {
                        filter {
                            eq("id", id)
                            eq("user_id", userId)
                        }
                    }
            }.onFailure { e ->
                android.util.Log.e("FactCheckRepository", "Failed to update report privacy in Supabase: ${e.message}")
            }
        }
    }

    companion object {
        private val CMS_SUMMARY_COLUMNS = Columns.ALL
        private val FACTCHECK_CMS_TYPES = listOf("factcheck", "mythbusting", "muktijoddha")
        private val cmsMutex = Mutex()
        private var cachedRecentFactChecks: List<com.example.data.model.CmsArticle>? = null
        private var lastRecentFactChecksTime: Long = 0L

        private var cachedAllFactChecks: List<com.example.data.model.CmsArticle>? = null
        private var lastAllFactChecksTime: Long = 0L

        private var activeRecentFactChecksDeferred: Deferred<List<com.example.data.model.CmsArticle>>? = null
        private var activeAllFactChecksDeferred: Deferred<List<com.example.data.model.CmsArticle>>? = null
        private val fullArticleCache = java.util.concurrent.ConcurrentHashMap<String, com.example.data.model.CmsArticle>()

        suspend fun getCmsArticleBySlug(slug: String): com.example.data.model.CmsArticle? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            fullArticleCache[slug]?.let { if (it.content.isNotBlank()) return@withContext it }
            runCatching {
                val item = com.example.data.api.KhojSupabaseClient.client
                    .postgrest["cms_articles"]
                    .select(Columns.ALL) {
                        filter { eq("slug", slug) }
                    }
                    .decodeSingleOrNull<com.example.data.model.CmsArticle>()
                if (item != null && item.content.isNotBlank()) {
                    fullArticleCache[item.id] = item
                    fullArticleCache[item.slug] = item
                }
                item
            }.getOrNull()
        }

        suspend fun getCmsArticleById(id: String): com.example.data.model.CmsArticle? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            fullArticleCache[id]?.let { if (it.content.isNotBlank()) return@withContext it }
            runCatching {
                val item = com.example.data.api.KhojSupabaseClient.client
                    .postgrest["cms_articles"]
                    .select(Columns.ALL) {
                        filter { eq("id", id) }
                    }
                    .decodeSingleOrNull<com.example.data.model.CmsArticle>()
                if (item != null && item.content.isNotBlank()) {
                    fullArticleCache[item.id] = item
                    if (item.slug.isNotBlank()) fullArticleCache[item.slug] = item
                }
                item
            }.getOrNull()
        }

        suspend fun getFullCmsArticle(article: com.example.data.model.CmsArticle): com.example.data.model.CmsArticle = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (article.content.isNotBlank()) {
                fullArticleCache[article.id] = article
                if (article.slug.isNotBlank()) fullArticleCache[article.slug] = article
                return@withContext article
            }

            val cached = fullArticleCache[article.id] ?: fullArticleCache[article.slug]
            if (cached != null && cached.content.isNotBlank()) {
                return@withContext cached
            }

            var fetched: com.example.data.model.CmsArticle? = null
            if (article.slug.isNotBlank()) {
                fetched = getCmsArticleBySlug(article.slug)
            }
            if (fetched == null || fetched.content.isBlank()) {
                fetched = getCmsArticleById(article.id)
            }

            if (fetched != null && fetched.content.isNotBlank()) {
                fullArticleCache[article.id] = fetched
                if (fetched.slug.isNotBlank()) fullArticleCache[fetched.slug] = fetched
                fetched
            } else {
                article
            }
        }
    }

    suspend fun getRecentCmsFactChecks(limit: Int = 10, forceRefresh: Boolean = false): List<com.example.data.model.CmsArticle> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cmsMutex.withLock {
                val cached = cachedRecentFactChecks
                if (cached != null && (now - lastRecentFactChecksTime) < CachePolicy.CMS_FACTCHECK_TTL) {
                    return@withContext cached
                }
            }
        }

        val deferred = cmsMutex.withLock {
            if (!forceRefresh) {
                activeRecentFactChecksDeferred?.let { return@withContext it.await() }
            }
            val newDeferred = async {
                runCatching {
                    com.example.data.api.KhojSupabaseClient.client
                        .postgrest["cms_articles"]
                        .select(CMS_SUMMARY_COLUMNS) {
                            filter { isIn("type", FACTCHECK_CMS_TYPES) }
                            order("published_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                            limit(limit.toLong())
                        }
                        .decodeList<com.example.data.model.CmsArticle>()
                }.getOrElse { e ->
                    android.util.Log.e("FactCheckRepository", "Failed to fetch CMS articles: ${e.message}", e)
                    throw e
                }
            }
            activeRecentFactChecksDeferred = newDeferred
            newDeferred
        }

        try {
            val result = deferred.await()
            if (result.isNotEmpty()) {
                cmsDao.syncArticles(result)
            }
            cmsMutex.withLock {
                cachedRecentFactChecks = result
                lastRecentFactChecksTime = System.currentTimeMillis()
            }
            result
        } finally {
            cmsMutex.withLock {
                if (activeRecentFactChecksDeferred == deferred) {
                    activeRecentFactChecksDeferred = null
                }
            }
        }
    }

    suspend fun getAllCmsFactChecks(forceRefresh: Boolean = false): List<com.example.data.model.CmsArticle> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cmsMutex.withLock {
                val cached = cachedAllFactChecks
                if (cached != null && (now - lastAllFactChecksTime) < CachePolicy.CMS_FACTCHECK_TTL) {
                    return@withContext cached
                }
            }
        }

        val deferred = cmsMutex.withLock {
            if (!forceRefresh) {
                activeAllFactChecksDeferred?.let { return@withContext it.await() }
            }
            val newDeferred = async {
                runCatching {
                    com.example.data.api.KhojSupabaseClient.client
                        .postgrest["cms_articles"]
                        .select(CMS_SUMMARY_COLUMNS) {
                            filter { isIn("type", FACTCHECK_CMS_TYPES) }
                            order("published_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        }
                        .decodeList<com.example.data.model.CmsArticle>()
                }.getOrElse { e ->
                    android.util.Log.e("FactCheckRepository", "Failed to fetch CMS articles: ${e.message}", e)
                    throw e
                }
            }
            activeAllFactChecksDeferred = newDeferred
            newDeferred
        }

        try {
            val result = deferred.await()
            if (result.isNotEmpty()) {
                cmsDao.syncArticles(result)
            }
            cmsMutex.withLock {
                cachedAllFactChecks = result
                lastAllFactChecksTime = System.currentTimeMillis()
            }
            result
        } finally {
            cmsMutex.withLock {
                if (activeAllFactChecksDeferred == deferred) {
                    activeAllFactChecksDeferred = null
                }
            }
        }
    }

    fun getRecentCmsFactChecksFlow(limit: Int = 10): Flow<List<com.example.data.model.CmsArticle>> {
        return cmsDao.getRecentArticlesByTypes(FACTCHECK_CMS_TYPES, limit)
    }

    fun getAllCmsFactChecksFlow(): Flow<List<com.example.data.model.CmsArticle>> {
        return cmsDao.getArticlesByTypes(FACTCHECK_CMS_TYPES)
    }

    suspend fun syncFactCheckArticles(forceFullSync: Boolean = false) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        cmsMutex.withLock {
            runCatching {
                val lastSync = if (forceFullSync) null else {
                    listOfNotNull(
                        cmsDao.getLastSyncTime("factcheck"),
                        cmsDao.getLastSyncTime("mythbusting"),
                        cmsDao.getLastSyncTime("muktijoddha")
                    ).minOrNull()
                }
                val query = com.example.data.api.KhojSupabaseClient.client
                    .postgrest["cms_articles"]
                    .select(CMS_SUMMARY_COLUMNS) {
                        filter { 
                            isIn("type", FACTCHECK_CMS_TYPES)
                            if (lastSync != null) {
                                gt("updated_at", lastSync)
                            }
                        }
                    }
                val updatedArticles = query.decodeList<com.example.data.model.CmsArticle>()
                if (updatedArticles.isNotEmpty()) {
                    cmsDao.syncArticles(updatedArticles)
                }
            }.onFailure { e ->
                android.util.Log.e("FactCheckRepository", "Failed to sync fact-check articles", e)
            }
        }
    }

    fun getRecentCmsBlogArticlesFlow(limit: Int = 4): Flow<List<com.example.data.model.CmsArticle>> {
        if (!com.example.data.FeatureFlags.USE_LOCAL_BLOG_CACHE) {
            return kotlinx.coroutines.flow.flow {
                val list = runCatching {
                    com.example.data.api.KhojSupabaseClient.client
                        .postgrest["cms_articles"]
                        .select(CMS_SUMMARY_COLUMNS) {
                            filter { eq("type", "blog") }
                            order("published_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                            limit(limit.toLong())
                        }
                        .decodeList<com.example.data.model.CmsArticle>()
                }.getOrDefault(emptyList())
                emit(list)
            }
        }
        return cmsDao.getRecentArticlesByType("blog", limit)
    }

    fun getAllCmsBlogArticlesFlow(): Flow<List<com.example.data.model.CmsArticle>> {
        if (!com.example.data.FeatureFlags.USE_LOCAL_BLOG_CACHE) {
            return kotlinx.coroutines.flow.flow {
                val list = runCatching {
                    com.example.data.api.KhojSupabaseClient.client
                        .postgrest["cms_articles"]
                        .select(CMS_SUMMARY_COLUMNS) {
                            filter { eq("type", "blog") }
                            order("published_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        }
                        .decodeList<com.example.data.model.CmsArticle>()
                }.getOrDefault(emptyList())
                emit(list)
            }
        }
        return cmsDao.getArticlesByType("blog")
    }

    suspend fun syncBlogArticles(forceFullSync: Boolean = false) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        cmsMutex.withLock {
            runCatching {
                val lastSync = if (forceFullSync) null else cmsDao.getLastSyncTime("blog")
                val query = com.example.data.api.KhojSupabaseClient.client
                    .postgrest["cms_articles"]
                    .select(CMS_SUMMARY_COLUMNS) {
                        filter { 
                            eq("type", "blog")
                            if (lastSync != null) {
                                gt("updated_at", lastSync)
                            }
                        }
                    }
                val updatedArticles = query.decodeList<com.example.data.model.CmsArticle>()
                if (updatedArticles.isNotEmpty()) {
                    cmsDao.syncArticles(updatedArticles)
                }
            }.onFailure { e ->
                android.util.Log.e("FactCheckRepository", "Failed to sync blog articles", e)
            }
        }
    }
}


