package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    @Query("SELECT * FROM feed_items ORDER BY sortOrder DESC LIMIT :limit")
    fun getFeedFlow(limit: Int = 50): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_items ORDER BY sortOrder DESC LIMIT :limit")
    suspend fun getFeedList(limit: Int = 50): List<FeedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedItems(items: List<FeedItemEntity>)

    @Query("DELETE FROM feed_items WHERE id = :id")
    suspend fun deleteFeedItemById(id: String)

    @Query("DELETE FROM feed_items")
    suspend fun clearFeed()

    @Query("SELECT MAX(sortOrder) FROM feed_items")
    suspend fun getLatestTimestamp(): Long?

    @Transaction
    suspend fun replaceFeed(items: List<FeedItemEntity>) {
        clearFeed()
        insertFeedItems(items)
    }
}
