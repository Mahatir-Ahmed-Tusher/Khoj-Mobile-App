package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.FavoriteImageResultEntity
import com.example.data.model.ImageSearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageSearchDao {

    // ── Search History ────────────────────────────────────────────────────────

    @Query("SELECT * FROM image_search_history ORDER BY timestamp DESC LIMIT 50")
    fun getAllHistory(): Flow<List<ImageSearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: ImageSearchHistoryEntity)

    @Query("DELETE FROM image_search_history WHERE id = :id")
    suspend fun deleteHistoryById(id: String)

    @Query("DELETE FROM image_search_history")
    suspend fun clearHistory()

    // ── Favorite Results ──────────────────────────────────────────────────────

    @Query("SELECT * FROM favorite_image_results ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteImageResultEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_image_results WHERE url = :url)")
    fun isFavorite(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(item: FavoriteImageResultEntity)

    @Query("DELETE FROM favorite_image_results WHERE url = :url")
    suspend fun deleteFavoriteByUrl(url: String)
}
