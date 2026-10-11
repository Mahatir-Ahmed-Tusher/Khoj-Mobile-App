package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.CmsArticle
import kotlinx.coroutines.flow.Flow

@Dao
interface CmsArticleDao {
    @Query("SELECT * FROM cms_articles WHERE type = :type AND deletedAt IS NULL ORDER BY publishedAt DESC")
    fun getArticlesByType(type: String): Flow<List<CmsArticle>>
    
    @Query("SELECT * FROM cms_articles WHERE type = :type AND deletedAt IS NULL ORDER BY publishedAt DESC LIMIT :limit")
    fun getRecentArticlesByType(type: String, limit: Int): Flow<List<CmsArticle>>

    @Query("SELECT * FROM cms_articles WHERE type IN (:types) AND deletedAt IS NULL ORDER BY publishedAt DESC")
    fun getArticlesByTypes(types: List<String>): Flow<List<CmsArticle>>
    
    @Query("SELECT * FROM cms_articles WHERE type IN (:types) AND deletedAt IS NULL ORDER BY publishedAt DESC LIMIT :limit")
    fun getRecentArticlesByTypes(types: List<String>, limit: Int): Flow<List<CmsArticle>>

    @Query("SELECT * FROM cms_articles WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getArticleById(id: String): CmsArticle?

    @Query("SELECT * FROM cms_articles WHERE slug = :slug AND deletedAt IS NULL LIMIT 1")
    suspend fun getArticleBySlug(slug: String): CmsArticle?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<CmsArticle>)

    @Query("DELETE FROM cms_articles WHERE id = :id")
    suspend fun deleteArticleById(id: String)
    
    @Query("SELECT MAX(updatedAt) FROM cms_articles WHERE type = :type")
    suspend fun getLastSyncTime(type: String): String?
    
    @Transaction
    suspend fun syncArticles(newArticles: List<CmsArticle>) {
        // Insert or update new articles
        insertArticles(newArticles)
        // Note: Tombstoned items (deletedAt != null) will also be inserted/updated, 
        // effectively soft-deleting them from the UI queries.
    }
}
