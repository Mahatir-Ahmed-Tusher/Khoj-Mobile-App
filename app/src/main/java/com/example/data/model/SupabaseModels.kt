package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import com.example.data.model.NewsArticle

// ─── Users ───────────────────────────────────────────────────────────────────

@Serializable
data class SupabaseUser(
    val id: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val role: String = "user",
    @SerialName("account_status") val accountStatus: String = "active",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_login_at") val lastLoginAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

// ─── Search History ────────────────────────────────────────────────────────────

@Serializable
data class UserSearchHistory(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val query: String,
    @SerialName("search_type") val searchType: String = "factcheck",
    @SerialName("report_id") val reportId: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class UserSearchHistoryInsert(
    @SerialName("user_id") val userId: String,
    val query: String,
    @SerialName("search_type") val searchType: String,
    @SerialName("report_id") val reportId: String? = null
)

// ─── Fact-Check Reports ──────────────────────────────────────────────────────

@Serializable
data class SupabaseSource(
    val url: String = "",
    val title: String = "",
    @SerialName("credibilityScore") val credibilityScore: Double? = null,
    val snippet: String? = null,
    val language: String? = null,
    val tier: Int? = null
)

@Serializable
data class SupabaseReport(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("original_query") val originalQuery: String = "",
    @SerialName("processed_query") val processedQuery: String? = null,
    val verdict: String = "unverified",
    @SerialName("confidence_score") val confidenceScore: Double = 0.0,
    val sources: JsonArray? = null,
    @SerialName("model_version") val modelVersion: String? = null,
    @SerialName("processing_time_ms") val processingTimeMs: Int = 0,
    val language: String = "bn",
    val status: String = "completed",
    @SerialName("report_content") val reportContent: String? = null,
    @SerialName("is_public") val isPublic: Boolean? = true,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class SupabaseReportInsert(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("original_query") val originalQuery: String,
    @SerialName("processed_query") val processedQuery: String? = null,
    val verdict: String = "unverified",
    @SerialName("confidence_score") val confidenceScore: Double = 0.0,
    val sources: List<SupabaseSource> = emptyList(),
    @SerialName("model_version") val modelVersion: String? = null,
    @SerialName("processing_time_ms") val processingTimeMs: Int = 0,
    val language: String = "bn",
    val status: String = "completed",
    @SerialName("report_content") val reportContent: String? = null,
    @SerialName("is_public") val isPublic: Boolean? = true
)

// ─── CMS Articles ─────────────────────────────────────────────────────────────

@Serializable
data class SupabaseArticle(
    val id: String = "",
    val type: String = "factcheck",
    val slug: String = "",
    val title: String = "",
    val claim: String? = null,
    val verdict: String? = null,
    val author: String = "",
    val content: String? = "",
    val excerpt: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("created_at") val createdAt: String = ""
) {
    fun toCmsArticle(): CmsArticle = CmsArticle(
        id = id,
        type = type,
        slug = slug,
        title = title,
        claim = claim,
        verdict = verdict,
        author = author,
        content = content ?: "",
        excerpt = excerpt,
        thumbnailUrl = thumbnailUrl,
        publishedAt = publishedAt
    )
}

// ─── Trending Rumours ─────────────────────────────────────────────────────────

@Serializable
data class SupabaseTrendingRumour(
    val id: String = "",
    val title: String = "",
    val url: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("created_at") val createdAt: String = ""
)

// ─── Likes ────────────────────────────────────────────────────────────────────

@Serializable
data class SupabaseLike(
    val id: String = "",
    @SerialName("post_id") val postId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

// ─── Comments ─────────────────────────────────────────────────────────────────

@Serializable
data class SupabaseComment(
    val id: String = "",
    @SerialName("post_id") val postId: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("comment_text") val commentText: String = "",
    @SerialName("parent_id") val parentId: String? = null,
    val depth: Int = 0,
    @SerialName("created_at") val createdAt: String = ""
)

// ─── Notifications ────────────────────────────────────────────────────────────

@Serializable
data class SupabaseNotification(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("actor_name") val actorName: String? = null,
    @SerialName("actor_avatar_url") val actorAvatarUrl: String? = null,
    val type: String = "like",
    val title: String? = null,
    @SerialName("title_bn") val titleBn: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    val message: String? = null,
    @SerialName("message_bn") val messageBn: String? = null,
    @SerialName("message_en") val messageEn: String? = null,
    @SerialName("post_id") val postId: String? = null,
    @SerialName("post_title") val postTitle: String? = null,
    @SerialName("comment_id") val commentId: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("is_broadcast") val isBroadcast: Boolean = false,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
) {
    fun getDisplayTitle(isBn: Boolean): String {
        return if (isBn) {
            titleBn ?: title ?: when (type) {
                "like" -> "নতুন লাইক"
                "comment" -> "নতুন মন্তব্য"
                "reply" -> "নতুন উত্তর"
                "admin_factcheck" -> "নতুন ফ্যাক্টচেক"
                "admin_blog" -> "নতুন ব্লগ"
                else -> "বিজ্ঞপ্তি"
            }
        } else {
            titleEn ?: when (type) {
                "like" -> "New Like"
                "comment" -> "New Comment"
                "reply" -> "New Reply"
                "admin_factcheck" -> "New Fact Check"
                "admin_blog" -> "New Blog"
                else -> "Notification"
            }
        }
    }

    fun getDisplayMessage(isBn: Boolean): String {
        return if (isBn) {
            messageBn ?: message ?: when (type) {
                "like" -> "${actorName ?: "কেউ একজন"} আপনার রিপোর্টে লাইক দিয়েছেন"
                "comment" -> "${actorName ?: "কেউ একজন"} আপনার রিপোর্টে মন্তব্য করেছেন"
                "reply" -> "${actorName ?: "কেউ একজন"} আপনার মন্তব্যের উত্তর দিয়েছেন"
                else -> "নতুন একটি বিজ্ঞপ্তি এসেছে"
            }
        } else {
            messageEn ?: when (type) {
                "like" -> "${actorName ?: "Someone"} liked your report"
                "comment" -> "${actorName ?: "Someone"} commented on your report"
                "reply" -> "${actorName ?: "Someone"} replied to your comment"
                else -> "You have a new notification"
            }
        }
    }
}

// ─── Feed Item (union type for the Feed UI) ───────────────────────────────────

sealed class FeedItem {
    data class ReportItem(
        val report: SupabaseReport,
        val authorName: String?,
        val authorAvatarUrl: String?,
        val likeCount: Int,
        val commentCount: Int,
        val isLikedByMe: Boolean
    ) : FeedItem()

    data class ArticleItem(
        val article: SupabaseArticle,
        val likeCount: Int,
        val commentCount: Int,
        val isLikedByMe: Boolean
    ) : FeedItem()
    
    data class NewsItem(
        val news: NewsArticle
    ) : FeedItem()
    
    object MoreNewsButtonItem : FeedItem()
}

// ─── RPC Responses (Pagination + Aggregated Counts) ───────────────────────────

@Serializable
data class FeedReportRpcResponse(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("original_query") val originalQuery: String? = "",
    @SerialName("processed_query") val processedQuery: String? = null,
    val verdict: String? = "unverified",
    @SerialName("confidence_score") val confidenceScore: Double? = 0.0,
    val sources: JsonArray? = null,
    @SerialName("model_version") val modelVersion: String? = null,
    @SerialName("processing_time_ms") val processingTimeMs: Int? = 0,
    val language: String? = "bn",
    val status: String? = "completed",
    @SerialName("report_content") val reportContent: String? = null,
    @SerialName("is_public") val isPublic: Boolean? = true,
    @SerialName("created_at") val createdAt: String? = "",
    @SerialName("updated_at") val updatedAt: String? = "",
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_avatar") val authorAvatar: String? = null,
    @SerialName("like_count") val likeCount: Int? = 0,
    @SerialName("comment_count") val commentCount: Int? = 0,
    @SerialName("is_liked_by_me") val isLikedByMe: Boolean = false
) {
    fun toFeedItem(): FeedItem.ReportItem {
        return FeedItem.ReportItem(
            report = SupabaseReport(
                id = id, userId = userId, originalQuery = originalQuery ?: "", processedQuery = processedQuery,
                verdict = verdict ?: "unverified", confidenceScore = confidenceScore ?: 0.0, sources = sources,
                modelVersion = modelVersion, processingTimeMs = processingTimeMs ?: 0, language = language ?: "bn",
                status = status ?: "completed", reportContent = reportContent, isPublic = isPublic ?: true,
                createdAt = createdAt ?: "", updatedAt = updatedAt ?: ""
            ),
            authorName = authorName,
            authorAvatarUrl = authorAvatar,
            likeCount = likeCount ?: 0,
            commentCount = commentCount ?: 0,
            isLikedByMe = isLikedByMe
        )
    }
}

@Serializable
data class FeedArticleRpcResponse(
    val id: String = "",
    val type: String = "factcheck",
    val slug: String = "",
    val title: String = "",
    val claim: String? = null,
    val verdict: String? = null,
    val author: String = "",
    val content: String? = null,
    val excerpt: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("is_liked_by_me") val isLikedByMe: Boolean = false
) {
    fun toFeedItem(): FeedItem.ArticleItem {
        return FeedItem.ArticleItem(
            article = SupabaseArticle(
                id = id, type = type, slug = slug, title = title, claim = claim, verdict = verdict,
                author = author, content = content ?: "", excerpt = excerpt, thumbnailUrl = thumbnailUrl,
                tags = emptyList(), // Tags are ignored in feed UI currently
                publishedAt = publishedAt, createdAt = createdAt
            ),
            likeCount = likeCount,
            commentCount = commentCount,
            isLikedByMe = isLikedByMe
        )
    }
}

