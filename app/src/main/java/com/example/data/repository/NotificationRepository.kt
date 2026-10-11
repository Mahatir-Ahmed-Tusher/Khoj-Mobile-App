package com.example.data.repository

import com.example.data.api.KhojSupabaseClient
import com.example.data.local.CachePolicy
import com.example.data.model.CmsArticle
import com.example.data.model.DeepSearchReport
import com.example.data.model.FactCheckReport
import com.example.data.model.SupabaseNotification
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class NotificationTarget {
    data class FactCheck(val report: FactCheckReport) : NotificationTarget()
    data class DeepSearch(val report: DeepSearchReport) : NotificationTarget()
    data class Cms(val article: CmsArticle, val isBlog: Boolean) : NotificationTarget()
    object Unknown : NotificationTarget()
}

object NotificationRepository {

    private val db get() = KhojSupabaseClient.client.postgrest
    private val json = Json { ignoreUnknownKeys = true }

    private val mutex = Mutex()
    private var cachedUserId: String? = null
    private var cachedNotifications: List<SupabaseNotification>? = null
    private var lastNotificationsFetchTime: Long = 0L

    private var cachedUnreadCount: Int? = null
    private var lastUnreadCountFetchTime: Long = 0L

    private var activeFetchDeferred: Deferred<List<SupabaseNotification>>? = null
    private var activeCountDeferred: Deferred<Int>? = null

    /**
     * Fetches unified notifications (direct user interactions + broadcast announcements)
     * using the single-trip high-performance RPC get_user_notifications_page.
     */
    suspend fun fetchNotifications(
        userId: String,
        cursor: String? = null,
        limit: Int = 40,
        forceRefresh: Boolean = false
    ): List<SupabaseNotification> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. Invalidate cache if user switched
        mutex.withLock {
            if (cachedUserId != null && cachedUserId != userId) {
                cachedNotifications = null
                cachedUnreadCount = null
                cachedUserId = userId
            }
        }

        // 2. Check TTL Cache unless forceRefresh is true or cursor pagination
        if (!forceRefresh && cursor == null) {
            mutex.withLock {
                val cached = cachedNotifications
                if (cached != null && (now - lastNotificationsFetchTime) < CachePolicy.NOTIFICATIONS_TTL) {
                    return@withContext cached
                }
            }
        }

        // 3. Request Coalescing (Single Flight)
        val deferred = mutex.withLock {
            if (!forceRefresh && cursor == null) {
                activeFetchDeferred?.let { return@withContext it.await() }
            }
            val newDeferred = async {
                runCatching {
                    val rpcResponse = db.rpc(
                        "get_user_notifications_page",
                        buildJsonObject {
                            put("p_user_id", userId)
                            if (!cursor.isNullOrBlank()) {
                                put("p_cursor", cursor)
                            }
                            put("p_limit", limit)
                        }
                    )
                    rpcResponse.decodeList<SupabaseNotification>()
                }.getOrElse { e ->
                    android.util.Log.e("NotificationRepository", "Failed to fetch notifications via RPC: ${e.message}", e)
                    // Fallback to direct query if RPC is not yet created
                    runCatching {
                        db["notifications"].select(Columns.ALL) {
                            filter { eq("user_id", userId) }
                            order("created_at", Order.DESCENDING)
                            limit(limit.toLong())
                        }.decodeList<SupabaseNotification>()
                    }.getOrDefault(emptyList())
                }
            }
            if (cursor == null) {
                activeFetchDeferred = newDeferred
            }
            newDeferred
        }

        try {
            val result = deferred.await()
            if (cursor == null) {
                mutex.withLock {
                    cachedUserId = userId
                    cachedNotifications = result
                    lastNotificationsFetchTime = System.currentTimeMillis()
                }
            }
            result
        } finally {
            mutex.withLock {
                if (activeFetchDeferred == deferred) {
                    activeFetchDeferred = null
                }
            }
        }
    }

    /**
     * Marks a notification as read (direct notification or broadcast receipt).
     */
    suspend fun markAsRead(
        notificationId: String,
        userId: String,
        isBroadcast: Boolean = false
    ): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            runCatching {
                db.rpc(
                    "mark_notification_as_read",
                    buildJsonObject {
                        put("p_notification_id", notificationId)
                        put("p_user_id", userId)
                        put("p_is_broadcast", isBroadcast)
                    }
                )
            }.onFailure {
                // Fallback direct update
                if (!isBroadcast) {
                    db["notifications"].update(mapOf("is_read" to true)) {
                        filter { eq("id", notificationId) }
                    }
                }
            }

            mutex.withLock {
                cachedNotifications = cachedNotifications?.map { n ->
                    if (n.id == notificationId) n.copy(isRead = true) else n
                }
                cachedUnreadCount = ((cachedUnreadCount ?: 1) - 1).coerceAtLeast(0)
            }
            Unit
        }
    }

    /**
     * Marks all notifications as read for the given user.
     */
    suspend fun markAllRead(userId: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            runCatching {
                db.rpc(
                    "mark_all_notifications_as_read",
                    buildJsonObject {
                        put("p_user_id", userId)
                    }
                )
            }.onFailure {
                // Fallback direct update
                db["notifications"].update(mapOf("is_read" to true)) {
                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }
                }
            }

            mutex.withLock {
                cachedNotifications = cachedNotifications?.map { it.copy(isRead = true) }
                cachedUnreadCount = 0
            }
            Unit
        }
    }

    /**
     * Gets unread notification count combining direct and broadcast notifications.
     */
    suspend fun getUnreadCount(
        userId: String,
        forceRefresh: Boolean = false
    ): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // Check TTL Cache
        if (!forceRefresh) {
            mutex.withLock {
                val cached = cachedUnreadCount
                if (cached != null && (now - lastUnreadCountFetchTime) < CachePolicy.NOTIFICATIONS_TTL) {
                    return@withContext cached
                }
            }
        }

        // Request Coalescing
        val deferred = mutex.withLock {
            if (!forceRefresh) {
                activeCountDeferred?.let { return@withContext it.await() }
            }
            val newDeferred = async {
                runCatching {
                    val count = db.rpc(
                        "get_user_unread_notification_count",
                        buildJsonObject {
                            put("p_user_id", userId)
                        }
                    ).decodeAs<Int>()
                    count
                }.getOrElse { e ->
                    // Fallback to direct count
                    runCatching {
                        val response = db["notifications"].select(Columns.list("id")) {
                            count(io.github.jan.supabase.postgrest.query.Count.EXACT)
                            filter {
                                eq("user_id", userId)
                                eq("is_read", false)
                            }
                        }
                        response.countOrNull()?.toInt() ?: 0
                    }.getOrDefault(0)
                }
            }
            activeCountDeferred = newDeferred
            newDeferred
        }

        try {
            val count = deferred.await()
            mutex.withLock {
                cachedUnreadCount = count
                lastUnreadCountFetchTime = System.currentTimeMillis()
            }
            count
        } finally {
            mutex.withLock {
                if (activeCountDeferred == deferred) {
                    activeCountDeferred = null
                }
            }
        }
    }

    /**
     * Resolves the target entity for a notification by postId across CMS Articles,
     * Deep Search Reports, and Fact Check Reports.
     */
    suspend fun resolveNotificationTarget(
        postId: String?,
        type: String?
    ): NotificationTarget = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (postId.isNullOrBlank()) return@withContext NotificationTarget.Unknown

        // 1. If it's explicitly an admin CMS article / blog
        if (type == "admin_factcheck" || type == "admin_blog") {
            val article = runCatching {
                db["cms_articles"].select(Columns.ALL) {
                    filter { eq("slug", postId) }
                    limit(1)
                }.decodeSingleOrNull<CmsArticle>()
            }.getOrNull()

            if (article != null) {
                return@withContext NotificationTarget.Cms(article, isBlog = (article.type == "blog"))
            }
        }

        // 2. Check if it's a Deep Search Report (by UUID or slug)
        val deepReportDto = runCatching {
            db["deep_search_reports"].select(Columns.ALL) {
                filter { eq("id", postId) }
                limit(1)
            }.decodeSingleOrNull<com.example.data.model.RemoteDeepSearchReportDto>()
        }.onFailure { e ->
            android.util.Log.e("NotificationRepo", "[Notification:Resolve] Error querying deep_search_reports for $postId: ${e.message}")
        }.getOrNull()

        if (deepReportDto != null) {
            android.util.Log.d("NotificationRepo", "[Notification:Resolve] Successfully resolved DeepSearch target for $postId")
            return@withContext NotificationTarget.DeepSearch(deepReportDto.toDomainModel())
        }

        // 3. Check if it's a Fact Check Report
        val factReport = runCatching {
            db["factcheck_reports"].select(Columns.ALL) {
                filter { eq("id", postId) }
                limit(1)
            }.decodeSingleOrNull<FactCheckReport>()
        }.onFailure { e ->
            android.util.Log.e("NotificationRepo", "[Notification:Resolve] Error querying factcheck_reports for $postId: ${e.message}")
        }.getOrNull()

        if (factReport != null) {
            android.util.Log.d("NotificationRepo", "[Notification:Resolve] Successfully resolved FactCheck target for $postId")
            return@withContext NotificationTarget.FactCheck(factReport)
        }

        // 4. Check if it's a CMS article matching the slug
        val generalArticle = runCatching {
            db["cms_articles"].select(Columns.ALL) {
                filter { eq("slug", postId) }
                limit(1)
            }.decodeSingleOrNull<CmsArticle>()
        }.onFailure { e ->
            android.util.Log.e("NotificationRepo", "[Notification:Resolve] Error querying general cms_articles for $postId: ${e.message}")
        }.getOrNull()

        if (generalArticle != null) {
            android.util.Log.d("NotificationRepo", "[Notification:Resolve] Successfully resolved CMS target for $postId")
            return@withContext NotificationTarget.Cms(generalArticle, isBlog = (generalArticle.type == "blog"))
        }

        android.util.Log.w("NotificationRepo", "[Notification:Resolve] Could not resolve target entity for postId: $postId, type: $type")
        NotificationTarget.Unknown
    }

    fun invalidateCache() {
        cachedUserId = null
        cachedNotifications = null
        lastNotificationsFetchTime = 0L
        cachedUnreadCount = null
        lastUnreadCountFetchTime = 0L
    }
}
