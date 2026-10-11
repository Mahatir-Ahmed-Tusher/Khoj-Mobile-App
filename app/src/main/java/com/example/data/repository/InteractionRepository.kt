package com.example.data.repository

import com.example.data.api.KhojSupabaseClient
import com.example.data.model.SupabaseComment
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

object InteractionRepository {

    private val db get() = KhojSupabaseClient.client.postgrest

    // ── Likes ─────────────────────────────────────────────────────────────────

    suspend fun fetchLikesForPosts(postIds: List<String>): List<Map<String, String>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (postIds.isEmpty()) return@withContext emptyList()
        runCatching {
            db["post_likes"].select(Columns.list("post_id", "user_id")) {
                filter { isIn("post_id", postIds) }
            }.decodeList<Map<String, String>>()
        }.getOrDefault(emptyList())
    }

    suspend fun fetchCommentCountsForPosts(postIds: List<String>): Map<String, Int> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (postIds.isEmpty()) return@withContext emptyMap()
        runCatching {
            db["post_comments"].select(Columns.list("post_id")) {
                filter { isIn("post_id", postIds) }
            }.decodeList<Map<String, String>>()
                .groupBy { it["post_id"] ?: "" }
                .mapValues { it.value.size }
        }.getOrDefault(emptyMap())
    }

    suspend fun likePost(postId: String, userId: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            db["post_likes"].insert(
                mapOf("post_id" to postId, "user_id" to userId)
            )
            Unit
        }.onFailure { e ->
            android.util.Log.e("InteractionRepo", "Failed to like post $postId: ${e.message}")
        }
    }

    suspend fun unlikePost(postId: String, userId: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            db["post_likes"].delete {
                filter {
                    eq("post_id", postId)
                    eq("user_id", userId)
                }
            }
            Unit
        }.onFailure { e ->
            android.util.Log.e("InteractionRepo", "Failed to unlike post $postId: ${e.message}")
        }
    }

    // ── Comments (Cursor Paginated) ──────────────────────────────────────────

    /**
     * Fetches comments for a post using cursor-based pagination.
     * @param postId ID of the post/article
     * @param cursor Timestamp string of the last item for fetching subsequent pages
     * @param limit Number of comments per page (default 20)
     */
    suspend fun fetchComments(
        postId: String,
        cursor: String? = null,
        limit: Int = 20
    ): List<SupabaseComment> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            db["post_comments"].select(Columns.ALL) {
                filter {
                    eq("post_id", postId)
                    if (!cursor.isNullOrBlank()) {
                        gt("created_at", cursor)
                    }
                }
                order("created_at", Order.ASCENDING)
                limit(limit.toLong())
            }.decodeList<SupabaseComment>()
        }.getOrElse { e ->
            android.util.Log.e("InteractionRepo", "Failed to fetch comments for post $postId: ${e.message}")
            emptyList()
        }
    }

    suspend fun addComment(
        postId: String,
        userId: String,
        text: String,
        parentId: String? = null
    ): Result<SupabaseComment> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val depth = if (parentId != null) 1 else 0
            val payload = mutableMapOf<String, Any>(
                "post_id" to postId,
                "user_id" to userId,
                "comment_text" to text,
                "depth" to depth
            )
            if (!parentId.isNullOrBlank()) {
                payload["parent_id"] = parentId
            }
            db["post_comments"].insert(payload) { select() }.decodeSingle<SupabaseComment>()
        }.onFailure { e ->
            android.util.Log.e("InteractionRepo", "Failed to add comment on post $postId: ${e.message}", e)
        }
    }

    suspend fun deleteComment(commentId: String, userId: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            db["post_comments"].delete {
                filter {
                    eq("id", commentId)
                    eq("user_id", userId)
                }
            }
            Unit
        }.onFailure { e ->
            android.util.Log.e("InteractionRepo", "Failed to delete comment $commentId: ${e.message}")
        }
    }
}
