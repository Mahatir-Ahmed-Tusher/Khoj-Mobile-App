package com.example.data.local

/**
 * Centralized caching policy and time-to-live (TTL) configurations for the data layer.
 * Designed to minimize database hits under tight connection pool constraints (~60 max connections).
 */
object CachePolicy {
    /** TTL for feed pages (5 minutes) */
    const val FEED_TTL = 5 * 60 * 1000L

    /** TTL for CMS fact-check articles (15 minutes) */
    const val CMS_FACTCHECK_TTL = 15 * 60 * 1000L

    /** TTL for CMS blog articles (30 minutes) */
    const val CMS_BLOG_TTL = 30 * 60 * 1000L

    /** TTL for user notifications (2 minutes) */
    const val NOTIFICATIONS_TTL = 2 * 60 * 1000L

    /** Minimum duration (in ms) between pull-to-refresh calls to prevent database hammering */
    const val DEBOUNCE_WINDOW_MS = 2000L
}
