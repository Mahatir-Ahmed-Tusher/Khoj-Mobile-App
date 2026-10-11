package com.example.data.model

enum class SearchMode {
    FACT_CHECK,
    WEB_SEARCH,
    DEEP_SEARCH,
    VOICE_SEARCH,
    IMAGE_SEARCH;

    companion object {
        fun fromIntentExtra(value: String?): SearchMode {
            if (value.isNullOrBlank()) return FACT_CHECK
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: FACT_CHECK
        }
    }
}

data class PendingSearchAction(
    val query: String?,
    val mode: SearchMode,
    val timestamp: Long = System.currentTimeMillis()
)
