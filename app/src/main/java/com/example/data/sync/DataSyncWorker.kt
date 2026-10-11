package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.FeatureFlags
import com.example.data.local.FactCheckDatabase
import com.example.data.model.TrendingRegion
import com.example.data.repository.AuthRepository
import com.example.data.repository.FactCheckRepository
import com.example.data.repository.FeedRepository
import com.example.data.repository.TrendingRumoursRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Background worker responsible for scheduled data synchronization across:
 * 1. Blog Articles (Room)
 * 2. Trending Rumours (Cached JSON)
 * 3. Mixed Feed / Fact-checks (Room)
 */
class DataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "DataSyncWorker"
        const val WORK_NAME = "khoj_periodic_data_sync"
    }

    override suspend fun doWork(): Result {
        if (!FeatureFlags.IS_BACKGROUND_SYNC_ENABLED) {
            Log.d(TAG, "Background sync disabled by FeatureFlags. Skipping.")
            return Result.success()
        }

        Log.d(TAG, "Starting periodic background data sync...")

        return try {
            val database = FactCheckDatabase.getDatabase(applicationContext)
            val feedDao = database.feedDao()
            val factCheckRepo = FactCheckRepository(applicationContext)
            val prefs = applicationContext.getSharedPreferences("trending_rumours_prefs", Context.MODE_PRIVATE)
            val rumoursRepo = TrendingRumoursRepository(prefs)

            coroutineScope {
                // 1. Sync Fact-Check Articles
                val factCheckDeferred = async {
                    runCatching {
                        factCheckRepo.syncFactCheckArticles(forceFullSync = false)
                    }.onFailure { Log.w(TAG, "Failed to sync fact-check articles in background", it) }
                }

                // 2. Sync Blog Articles
                val blogDeferred = async {
                    runCatching {
                        factCheckRepo.syncBlogArticles(forceFullSync = false)
                    }.onFailure { Log.w(TAG, "Failed to sync blog articles in background", it) }
                }

                // 3. Sync Trending Rumours
                val rumoursDeferred = async {
                    runCatching {
                        rumoursRepo.fetchTrendingRumours(TrendingRegion.BD)
                        rumoursRepo.fetchTrendingRumours(TrendingRegion.GLOBAL)
                    }.onFailure { Log.w(TAG, "Failed to sync trending rumours in background", it) }
                }

                // 4. Sync Feed Items
                val feedDeferred = async {
                    runCatching {
                        FeedRepository.syncFeed(feedDao, AuthRepository.currentUserId)
                    }.onFailure { Log.w(TAG, "Failed to sync feed in background", it) }
                }

                factCheckDeferred.await()
                blogDeferred.await()
                rumoursDeferred.await()
                feedDeferred.await()
            }

            Log.d(TAG, "Background data sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background data sync failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
