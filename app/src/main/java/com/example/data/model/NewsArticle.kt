package com.example.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a single news article from the khoj-news-backend proxy.
 * Fields map to newsdata.io's response format as passed through by the backend.
 */
@Serializable
data class NewsArticle(
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val link: String
)

@Serializable
data class NewsResponse(
    val status: String,
    val results: List<NewsArticle>
)
