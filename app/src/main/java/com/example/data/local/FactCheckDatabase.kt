package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.FactCheckReport
import com.example.data.model.FavoriteImageResultEntity
import com.example.data.model.ImageSearchHistoryEntity
import com.example.data.model.CmsArticle
import kotlinx.coroutines.flow.Flow

@Dao
interface FactCheckDao {
    @Query("SELECT * FROM fact_checks ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<FactCheckReport>>

    @Query("SELECT * FROM fact_checks WHERE id = :id LIMIT 1")
    suspend fun getReportById(id: String): FactCheckReport?

    @Query("SELECT * FROM fact_checks WHERE claimText LIKE :query OR summaryBn LIKE :query OR summaryEn LIKE :query")
    fun searchReports(query: String): Flow<List<FactCheckReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: FactCheckReport)

    @Query("DELETE FROM fact_checks WHERE id = :id")
    suspend fun deleteReportById(id: String)

    @Query("DELETE FROM fact_checks")
    suspend fun clearAll()
}

@Database(
    entities = [
        FactCheckReport::class, 
        ImageSearchHistoryEntity::class, 
        FavoriteImageResultEntity::class, 
        com.example.data.model.WebSearchHistoryEntity::class,
        CmsArticle::class,
        FeedItemEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class FactCheckDatabase : RoomDatabase() {
    abstract fun dao(): FactCheckDao
    abstract fun imageSearchDao(): ImageSearchDao
    abstract fun webSearchHistoryDao(): WebSearchHistoryDao
    abstract fun cmsArticleDao(): CmsArticleDao
    abstract fun feedDao(): FeedDao

    companion object {
        @Volatile
        private var INSTANCE: FactCheckDatabase? = null

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cms_articles` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `slug` TEXT NOT NULL, `title` TEXT NOT NULL, `claim` TEXT, `verdict` TEXT, `author` TEXT NOT NULL, `content` TEXT NOT NULL, `excerpt` TEXT, `thumbnailUrl` TEXT, `publishedAt` TEXT NOT NULL, `updatedAt` TEXT, `deletedAt` TEXT, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `feed_items` (`id` TEXT NOT NULL, `itemType` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `authorName` TEXT, `authorAvatar` TEXT, `likeCount` INTEGER NOT NULL, `commentCount` INTEGER NOT NULL, `isLikedByMe` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE fact_checks ADD COLUMN markdownReport TEXT DEFAULT NULL"
                )
            }
        }

        fun getDatabase(context: Context): FactCheckDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FactCheckDatabase::class.java,
                    "khoj_database"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
