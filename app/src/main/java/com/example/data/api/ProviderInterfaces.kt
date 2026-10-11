package com.example.data.api

import kotlinx.coroutines.flow.Flow

/**
 * Standardized configuration for LLM requests.
 */
data class LlmRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val jsonOutput: Boolean = false,
    val temperature: Double = 0.3,
    val maxTokens: Int = 8192,
    val targetLanguage: String = "bn"
)

/**
 * Configuration for Query Planning logic.
 */
data class QueryPlanConfig(
    val maxTimeoutMillis: Long = 8000L,
    val isFactCheck: Boolean = true,
    val isFromImage: Boolean = false
)

data class WeightedQuery(
    val query: String,
    val purpose: String,
    val weight: Double
)

data class DeepSearchPlan(
    val intent: String,                 // e.g. "comparison", "informational", "fact_check"
    val languageStrategy: String,       // "english_only" | "bengali_only" | "mixed"
    val queries: List<WeightedQuery>,
    val queryBn: String? = null,
    val queryEn: String? = null
)

data class DeepSearchRawSource(
    val title: String,
    val url: String,
    val snippet: String,
    val engine: String,
    var queryWeight: Double = 1.0,      // populated after we know which query produced it
    var credibilityScore: Double = 1.0,
    var citationId: Int = 0,            // 1-based index assigned post-sorting
    var content: String? = null         // Extracted full article clean markdown
)

/**
 * Polymorphic interface for all Search Providers.
 */
interface SearchProvider {
    val name: String
    suspend fun search(query: String, maxResults: Int = 20): List<DeepSearchRawSource>
}

/**
 * Polymorphic interface for all LLM Providers.
 */
interface LlmProvider {
    val name: String
    suspend fun generate(request: LlmRequest): String
    fun stream(request: LlmRequest): Flow<String>
}

/**
 * Polymorphic interface for Query Planning logic.
 */
interface QueryPlanner {
    suspend fun plan(query: String, config: QueryPlanConfig): DeepSearchPlan
}
