package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@Entity(tableName = "cms_articles")
data class CmsArticle(
    @PrimaryKey
    val id: String,
    val type: String, // "factcheck" or "blog"
    val slug: String,
    val title: String,
    val claim: String? = null,
    val verdict: String? = null,
    val author: String,
    val content: String = "",
    val excerpt: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
) {
    @get:androidx.room.Ignore
    val isMythbusting: Boolean
        get() = type.equals("mythbusting", ignoreCase = true) || 
                type.equals("myth_busting", ignoreCase = true) || 
                type.equals("myth-busting", ignoreCase = true)

    @get:androidx.room.Ignore
    val isMuktijoddha: Boolean
        get() = type.equals("muktijoddha", ignoreCase = true) || 
                type.equals("mukti", ignoreCase = true) || 
                type.equals("mukti_corner", ignoreCase = true) || 
                type.equals("mukti-corner", ignoreCase = true)
}
