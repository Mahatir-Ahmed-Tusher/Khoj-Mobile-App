package com.example.data.repository

import com.example.data.api.KhojSupabaseClient
import com.example.data.model.FeedArticleRpcResponse
import com.example.data.model.FeedItem
import com.example.data.model.FeedReportRpcResponse
import com.example.data.model.SupabaseArticle
import com.example.data.model.SupabaseReport
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import com.example.data.local.FeedDao
import com.example.data.local.FeedItemEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object FeedRepository {

    private val db get() = KhojSupabaseClient.client.postgrest
    private val json = Json { ignoreUnknownKeys = true }

    fun feedItemToEntity(item: FeedItem, sortOrder: Long): FeedItemEntity? {
        return when (item) {
            is FeedItem.ReportItem -> FeedItemEntity(
                id = item.report.id,
                itemType = "report",
                payloadJson = json.encodeToString(item.report),
                sortOrder = sortOrder,
                authorName = item.authorName,
                authorAvatar = item.authorAvatarUrl,
                likeCount = item.likeCount,
                commentCount = item.commentCount,
                isLikedByMe = item.isLikedByMe,
                createdAt = item.report.createdAt
            )
            is FeedItem.ArticleItem -> FeedItemEntity(
                id = item.article.id.ifBlank { item.article.slug },
                itemType = "article",
                payloadJson = json.encodeToString(item.article),
                sortOrder = sortOrder,
                likeCount = item.likeCount,
                commentCount = item.commentCount,
                isLikedByMe = item.isLikedByMe,
                createdAt = item.article.publishedAt
            )
            is FeedItem.NewsItem -> FeedItemEntity(
                id = item.news.link.ifBlank { item.news.title },
                itemType = "news",
                payloadJson = json.encodeToString(item.news),
                sortOrder = sortOrder,
                createdAt = ""
            )
            else -> null
        }
    }

    fun entityToFeedItem(entity: FeedItemEntity): FeedItem? {
        return try {
            when (entity.itemType) {
                "report" -> {
                    val rep = json.decodeFromString<SupabaseReport>(entity.payloadJson)
                    FeedItem.ReportItem(
                        report = rep,
                        authorName = entity.authorName,
                        authorAvatarUrl = entity.authorAvatar,
                        likeCount = entity.likeCount,
                        commentCount = entity.commentCount,
                        isLikedByMe = entity.isLikedByMe
                    )
                }
                "article" -> {
                    val art = json.decodeFromString<SupabaseArticle>(entity.payloadJson)
                    FeedItem.ArticleItem(
                        article = art,
                        likeCount = entity.likeCount,
                        commentCount = entity.commentCount,
                        isLikedByMe = entity.isLikedByMe
                    )
                }
                "news" -> {
                    val news = json.decodeFromString<com.example.data.model.NewsArticle>(entity.payloadJson)
                    FeedItem.NewsItem(news)
                }
                else -> null
            }
        } catch (e: Throwable) {
            runCatching {
                android.util.Log.e("FeedRepository", "Failed to deserialize FeedItemEntity: ${entity.id}", e)
            }
            null
        }
    }

    private val feedMutex = Mutex()
    private var activeFeedDeferred: Deferred<Result<Pair<List<FeedItem>, Pair<String?, String?>>>>? = null

    // Columns selection for articles during search fallback
    private val CMS_SUMMARY_COLUMNS = Columns.ALL

    // Thread-safe cached date formatters to avoid re-instantiating SimpleDateFormat on every item parse
    private val dateFormatWithMillis = ThreadLocal.withInitial {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US)
    }
    private val dateFormatNoMillis = ThreadLocal.withInitial {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US)
    }

    // ── Build Full Feed (Cursor Pagination via RPC) ───────────────────────────

    /**
     * Fetches a paginated page of reports and articles in a single round trip
     * using Postgres RPC functions that pre-aggregate likes and comments.
     * Features request coalescing for initial page requests.
     */
    suspend fun buildFeedPage(
        currentUserId: String?,
        reportCursor: String? = null,
        articleCursor: String? = null,
        reportLimit: Int = 20,
        articleLimit: Int = 5
    ): Result<Pair<List<FeedItem>, Pair<String?, String?>>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val isFirstPage = reportCursor == null && articleCursor == null

        // Request coalescing for initial feed loads
        val deferred = feedMutex.withLock {
            if (isFirstPage) {
                activeFeedDeferred?.let { return@withContext it.await() }
            }
            val newDeferred = async {
                runCatching {
                    kotlinx.coroutines.supervisorScope {
                        // 1. Fetch reports page
                        val reportsDeferred = async {
                            db.rpc(
                                "get_reports_page",
                                buildJsonObject {
                                    put("p_cursor", reportCursor)
                                    put("p_limit", reportLimit)
                                    put("p_user_id", currentUserId)
                                }
                            ).decodeList<FeedReportRpcResponse>()
                        }

                        // 2. Fetch articles page
                        val articlesDeferred = async {
                            db.rpc(
                                "get_articles_page",
                                buildJsonObject {
                                    put("p_cursor", articleCursor)
                                    put("p_limit", articleLimit)
                                    put("p_user_id", currentUserId)
                                }
                            ).decodeList<FeedArticleRpcResponse>()
                        }

                        val rpcReports = reportsDeferred.await()
                        val rpcArticles = articlesDeferred.await()

                        val reportItems = rpcReports.map { it.toFeedItem() }
                        val articleItems = rpcArticles.map { it.toFeedItem() }

                        // Determine next cursors based on the last item's timestamp
                        val nextReportCursor = rpcReports.lastOrNull()?.createdAt
                        val nextArticleCursor = rpcArticles.lastOrNull()?.publishedAt

                        // Interleave: insert 1 article every 4 report items
                        val merged = mutableListOf<FeedItem>()
                        val artIter = articleItems.iterator()
                        reportItems.forEachIndexed { idx, item ->
                            merged.add(item)
                            if ((idx + 1) % 4 == 0 && artIter.hasNext()) {
                                merged.add(artIter.next())
                            }
                        }
                        while (artIter.hasNext()) {
                            merged.add(artIter.next())
                        }

                        Pair(merged, Pair(nextReportCursor, nextArticleCursor))
                    }
                }.onFailure { e ->
                    android.util.Log.e("FeedRepository", "Failed to build feed page", e)
                }
            }
            if (isFirstPage) {
                activeFeedDeferred = newDeferred
            }
            newDeferred
        }

        try {
            deferred.await()
        } finally {
            if (isFirstPage) {
                feedMutex.withLock {
                    if (activeFeedDeferred == deferred) {
                        activeFeedDeferred = null
                    }
                }
            }
        }
    }

    /**
     * Synchronizes the latest feed page into Room database.
     * Used by WorkManager background sync and silent reconciliation.
     */
    suspend fun syncFeed(
        feedDao: FeedDao,
        currentUserId: String?
    ): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val result = buildFeedPage(
            currentUserId = currentUserId,
            reportCursor = null,
            articleCursor = null,
            reportLimit = 20,
            articleLimit = 5
        )
        result.mapCatching { (items, _) ->
            val entities = items.mapIndexedNotNull { idx, item ->
                val sortOrder = (items.size - idx).toLong()
                feedItemToEntity(item, sortOrder)
            }
            if (entities.isNotEmpty()) {
                feedDao.replaceFeed(entities)
            }
            Unit
        }
    }

    // ── Search (Full-Text Search via RPC) ─────────────────────────────────────

    /**
     * Performs a full text search using Postgres GIN indexes.
     */
    suspend fun searchFeedFts(
        currentUserId: String?,
        query: String
    ): List<FeedItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val rpcReports = db.rpc(
                "search_reports_fts",
                buildJsonObject {
                    put("p_query", query)
                    put("p_limit", 50)
                    put("p_user_id", currentUserId)
                }
            ).decodeList<FeedReportRpcResponse>()
            
            // Search CMS articles with lightweight columns selection
            val articles = db["cms_articles"].select(CMS_SUMMARY_COLUMNS) {
                filter {
                    or {
                        ilike("title", "%$query%")
                        ilike("excerpt", "%$query%")
                    }
                }
                order("published_at", Order.DESCENDING)
                limit(10)
            }.decodeList<SupabaseArticle>()

            val articleItems = if (articles.isNotEmpty()) {
                val postIds = articles.map { it.slug }
                val likes = InteractionRepository.fetchLikesForPosts(postIds)
                val commentCounts = InteractionRepository.fetchCommentCountsForPosts(postIds)
                val likeCounts = likes.groupBy { it["post_id"] ?: "" }.mapValues { it.value.size }
                val likedByMeMap = currentUserId?.let { uid ->
                    likes.filter { it["user_id"] == uid }.associate { (it["post_id"] ?: "") to true }
                } ?: emptyMap()

                articles.map { a ->
                    FeedItem.ArticleItem(
                        article = a,
                        likeCount = likeCounts[a.slug] ?: 0,
                        commentCount = commentCounts[a.slug] ?: 0,
                        isLikedByMe = likedByMeMap[a.slug] ?: false
                    )
                }
            } else {
                emptyList()
            }

            val reportItems = rpcReports.map { it.toFeedItem() }
            
            val items = (reportItems + articleItems).sortedByDescending { item ->
                when (item) {
                    is FeedItem.ReportItem -> parseDate(item.report.createdAt)
                    is FeedItem.ArticleItem -> parseDate(item.article.publishedAt)
                    else -> 0L
                }
            }
            items
        }.getOrElse { e ->
            android.util.Log.e("FeedRepository", "Failed to search feed: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            val cleanString = dateStr.replace("Z", "+0000")
            val formatter = if (cleanString.contains(".")) dateFormatWithMillis.get() else dateFormatNoMillis.get()
            formatter?.parse(cleanString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
