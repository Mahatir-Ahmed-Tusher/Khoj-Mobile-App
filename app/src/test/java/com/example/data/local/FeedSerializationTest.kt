package com.example.data.local

import com.example.data.model.CmsArticle
import com.example.data.model.FeedItem
import com.example.data.model.NewsArticle
import com.example.data.model.SupabaseReport
import com.example.data.repository.FeedRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedSerializationTest {

    @Test
    fun reportItem_serializationRoundTrip_preservesAllFields() {
        val report = SupabaseReport(
            id = "rep_101",
            userId = "user_1",
            originalQuery = "Did the election date change?",
            processedQuery = "Election date claim",
            verdict = "False",
            confidenceScore = 0.95,
            sources = null,
            modelVersion = "v1.0",
            processingTimeMs = 120,
            language = "en",
            status = "completed",
            reportContent = "Detailed fact check content",
            isPublic = true,
            createdAt = "2026-01-01T12:00:00Z",
            updatedAt = "2026-01-01T12:00:00Z"
        )
        val reportItem = FeedItem.ReportItem(
            report = report,
            authorName = "Analyst Sarah",
            authorAvatarUrl = "https://example.com/avatar.png",
            likeCount = 14,
            commentCount = 3,
            isLikedByMe = true
        )

        val entity = FeedRepository.feedItemToEntity(reportItem, sortOrder = 500L)
        assertNotNull(entity)
        assertEquals("rep_101", entity!!.id)
        assertEquals("report", entity.itemType)
        assertEquals(500L, entity.sortOrder)

        val roundTripped = FeedRepository.entityToFeedItem(entity)
        assertTrue(roundTripped is FeedItem.ReportItem)
        val result = roundTripped as FeedItem.ReportItem
        assertEquals("rep_101", result.report.id)
        assertEquals("Analyst Sarah", result.authorName)
        assertEquals(14, result.likeCount)
        assertEquals(3, result.commentCount)
        assertTrue(result.isLikedByMe)
    }

    @Test
    fun newsItem_serializationRoundTrip_preservesNewsData() {
        val news = NewsArticle(
            title = "Major breakthrough in clean energy technology",
            description = "Scientists discover high-efficiency catalyst",
            imageUrl = "https://example.com/clean-energy.jpg",
            link = "https://example.com/news/clean-energy"
        )
        val newsItem = FeedItem.NewsItem(news)

        val entity = FeedRepository.feedItemToEntity(newsItem, sortOrder = 490L)
        assertNotNull(entity)
        assertEquals("https://example.com/news/clean-energy", entity!!.id)
        assertEquals("news", entity.itemType)

        val roundTripped = FeedRepository.entityToFeedItem(entity)
        assertTrue(roundTripped is FeedItem.NewsItem)
        val result = roundTripped as FeedItem.NewsItem
        assertEquals("Major breakthrough in clean energy technology", result.news.title)
        assertEquals("https://example.com/clean-energy.jpg", result.news.imageUrl)
    }

    @Test
    fun invalidEntity_deserializesGracefullyToNull() {
        val corruptedEntity = FeedItemEntity(
            id = "corrupted_1",
            itemType = "report",
            payloadJson = "{ corrupted json }}}",
            sortOrder = 10L,
            createdAt = "2026-01-01T00:00:00Z"
        )

        val result = FeedRepository.entityToFeedItem(corruptedEntity)
        assertNull(result)
    }
}
