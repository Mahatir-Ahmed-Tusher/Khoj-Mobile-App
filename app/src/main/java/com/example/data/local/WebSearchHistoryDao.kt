package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.WebSearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebSearchHistoryDao {
    @Query("SELECT * FROM web_search_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<WebSearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateHistory(entity: WebSearchHistoryEntity)

    @Query("DELETE FROM web_search_history WHERE query = :query")
    suspend fun deleteHistoryByQuery(query: String)

    @Query("DELETE FROM web_search_history")
    suspend fun clearAllHistory()
}
