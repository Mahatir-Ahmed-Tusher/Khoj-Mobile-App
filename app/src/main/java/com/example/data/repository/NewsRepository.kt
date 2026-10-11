package com.example.data.repository

import com.example.data.api.NewsApi
import com.example.data.model.NewsArticle

object NewsRepository {
    /**
     * Fetches the latest non-duplicate news articles from the backend proxy.
     * Returns a [Result] so callers can differentiate success from failure.
     */
    suspend fun getNews(): Result<List<NewsArticle>> {
        return try {
            val articles = NewsApi.fetchLatestNews()
            Result.success(articles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
