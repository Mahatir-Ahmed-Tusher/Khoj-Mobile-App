package com.example.data.model

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
data class VisualMatch(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val domain: String,
    val thumbnail: String,
    val sourceEngine: String, // "GOOGLE LENS", "YANDEX"
    val resolution: String? = null,
    val isFavorite: Boolean = false
)

data class ImageSearchResult(
    val matches: List<VisualMatch> = emptyList(),
    val usedFallback: Boolean = false,
    val failedEngine: String? = null,
    val errorMessage: String? = null
)

@Entity(tableName = "image_search_history")
data class ImageSearchHistoryEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val imageUriOrUrl: String,
    val engineUsed: String,
    val matchCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_image_results")
data class FavoriteImageResultEntity(
    @PrimaryKey val url: String,
    val title: String,
    val domain: String,
    val thumbnail: String,
    val sourceEngine: String,
    val timestamp: Long = System.currentTimeMillis()
)
