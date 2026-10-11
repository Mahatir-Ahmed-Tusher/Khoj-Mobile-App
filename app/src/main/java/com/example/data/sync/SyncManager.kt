package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Manages background synchronization scheduling via Android WorkManager.
 * Incorporates randomized jitter and battery/network constraints to protect backend infrastructure.
 */
object SyncManager {

    private const val TAG = "SyncManager"
    private const val PERIODIC_SYNC_INTERVAL_HOURS = 6L
    private const val MAX_JITTER_MINUTES = 45L

    /**
     * Schedules the periodic background data sync.
     * Uses ExistingPeriodicWorkPolicy.KEEP to preserve existing schedules across app launches.
     */
    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        // Randomized initial delay (0 - 45 mins) to prevent thundering herds across 200k+ active installs
        val initialJitterMinutes = Random.nextLong(0L, MAX_JITTER_MINUTES + 1L)

        val syncRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(
            repeatInterval = PERIODIC_SYNC_INTERVAL_HOURS,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialJitterMinutes, TimeUnit.MINUTES)
            .addTag(DataSyncWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DataSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        Log.d(TAG, "Periodic sync scheduled (every ${PERIODIC_SYNC_INTERVAL_HOURS}h, initial jitter: ${initialJitterMinutes}m)")
    }

    /**
     * Enqueues an immediate, one-time background sync.
     */
    fun enqueueOneTimeSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(constraints)
            .addTag(DataSyncWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${DataSyncWorker.WORK_NAME}_one_time",
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )

        Log.d(TAG, "One-time sync enqueued.")
    }
}
