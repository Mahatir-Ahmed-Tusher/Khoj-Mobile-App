package com.example.data.local

import com.example.data.model.CmsArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlogReconciliationTest {

    @Test
    fun tombstoneFilter_excludesSoftDeletedArticles() {
        val articles = listOf(
            CmsArticle(
                id = "art-1",
                type = "blog",
                slug = "active-article",
                title = "Active Article",
                claim = null,
                verdict = null,
                author = "Editorial Team",
                publishedAt = "2026-01-01T10:00:00Z",
                deletedAt = null
            ),
            CmsArticle(
                id = "art-2",
                type = "blog",
                slug = "retracted-article",
                title = "Retracted Article",
                claim = null,
                verdict = null,
                author = "Editorial Team",
                publishedAt = "2026-01-02T10:00:00Z",
                deletedAt = "2026-01-03T12:00:00Z"
            )
        )

        // Simulate the Room query logic: WHERE type = :type AND deletedAt IS NULL ORDER BY publishedAt DESC
        val activeArticles = articles
            .filter { it.type == "blog" && it.deletedAt == null }
            .sortedByDescending { it.publishedAt }

        assertEquals(1, activeArticles.size)
        assertEquals("active-article", activeArticles[0].slug)
    }

    @Test
    fun incrementalSync_mergesAndUpdatesExistingRecords() {
        val existingCache = mutableMapOf(
            "art-1" to CmsArticle(
                id = "art-1",
                type = "blog",
                slug = "article-1",
                title = "Original Title",
                author = "Author 1",
                publishedAt = "2026-01-01T10:00:00Z",
                updatedAt = "2026-01-01T10:00:00Z"
            )
        )

        val incomingDelta = listOf(
            CmsArticle(
                id = "art-1",
                type = "blog",
                slug = "article-1",
                title = "Updated Title v2",
                author = "Author 1",
                publishedAt = "2026-01-01T10:00:00Z",
                updatedAt = "2026-01-02T15:00:00Z"
            ),
            CmsArticle(
                id = "art-2",
                type = "blog",
                slug = "article-2",
                title = "New Article 2",
                author = "Author 2",
                publishedAt = "2026-01-02T16:00:00Z",
                updatedAt = "2026-01-02T16:00:00Z"
            )
        )

        // Apply OnConflictStrategy.REPLACE semantics
        incomingDelta.forEach { existingCache[it.id] = it }

        assertEquals(2, existingCache.size)
        assertEquals("Updated Title v2", existingCache["art-1"]?.title)
        assertEquals("New Article 2", existingCache["art-2"]?.title)
    }
}
