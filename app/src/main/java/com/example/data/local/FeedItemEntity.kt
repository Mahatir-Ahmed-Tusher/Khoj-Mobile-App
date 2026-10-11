package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@Entity(tableName = "feed_items")
data class FeedItemEntity(
    @PrimaryKey val id: String,
    val itemType: String, // "report", "article", "news"
    val payloadJson: String,
    val sortOrder: Long,
    val authorName: String? = null,
    val authorAvatar: String? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isLikedByMe: Boolean = false,
    val createdAt: String = ""
)
