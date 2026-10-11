package com.example.data

object FeatureFlags {
    // Phase 1: Blog Sync
    // Set to false to immediately fall back to the old, non-Room, direct-fetch logic.
    const val USE_LOCAL_BLOG_CACHE = true

    // Phase 4: WorkManager Background Sync
    // Set to false to disable all background worker execution.
    const val IS_BACKGROUND_SYNC_ENABLED = true
}
