package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.data.preferences.ResponseLanguage
import com.example.data.preferences.ResponseLanguageManager
import com.example.data.model.DeepSearchReport
import com.example.data.model.DeepSearchSource
import com.example.data.prompt.DeepSearchPrompt
import com.example.data.telemetry.PipelineStage
import com.example.data.telemetry.PipelineTelemetry
import com.example.data.telemetry.PipelineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.util.UUID

// ── Progress events emitted by executeDeepSearch() flow ───────────────────────
sealed class DeepSearchProgress {
    data class AnalyzingIntent(val status: String) : DeepSearchProgress()
    data class QueryExpansion(val queries: List<WeightedQuery>) : DeepSearchProgress()
    data class Searching(val currentEngine: String, val enginesUsed: Int, val rawSourcesFound: Int) : DeepSearchProgress()
    data class RankingSources(val initialCount: Int, val filteredCount: Int) : DeepSearchProgress()
    data class GeneratingReport(val usingEvidenceCount: Int) : DeepSearchProgress()
    data class StreamingChunk(val markdownChunk: String) : DeepSearchProgress()
    data class Success(val report: DeepSearchReport) : DeepSearchProgress()
    data class Error(val message: String) : DeepSearchProgress()
}

/**
 * DeepSearchEngine — Facade over the shared Orchestrators.
 *
 * Supports two construction modes:
 *  1. `DeepSearchEngine(context)` — Full orchestrator access (preferred for new code).
 *  2. `DeepSearchEngine()` — Lazy-context mode for Composables using LocalContext.
 *     Call `setContext(context)` before any use.
 *
 * Public API surface matches both:
 *  - New: `executeDeepSearch(query): Flow<DeepSearchProgress>` — used by ViewModels.
 *  - Legacy: individual step methods used by `DeepSearchScreen` UI coroutines.
 */
class DeepSearchEngine(private var context: Context? = null) {
    private val tag = "DeepSearchEngine"
    private val pipelineType = PipelineType.DEEP_SEARCH

    private val _searchOrchestrator: SearchOrchestrator? get() = context?.let { SearchOrchestrator(it) }
    private val _llmOrchestrator: LlmOrchestrator? get() = context?.let { LlmOrchestrator(it, pipelineType, isForPlanning = false) }
    private val _planningLlmOrchestrator: LlmOrchestrator? get() = context?.let { LlmOrchestrator(it, pipelineType, isForPlanning = true) }
    private val _queryPlanner: QueryPlanningOrchestrator? get() = _planningLlmOrchestrator?.let { QueryPlanningOrchestrator(it, pipelineType) }

    /** Called by Composable when context becomes available */
    fun setContext(ctx: Context) { context = ctx }

    // ── New Pipeline (Flow-based) ─────────────────────────────────────────────

    fun executeDeepSearch(query: String): Flow<DeepSearchProgress> = channelFlow {
        val so = requireNotNull(_searchOrchestrator) { "Context must be set before calling executeDeepSearch" }
        val lo = requireNotNull(_llmOrchestrator) { "Context must be set before calling executeDeepSearch" }
        val qp = requireNotNull(_queryPlanner) { "Context must be set before calling executeDeepSearch" }

        PipelineTelemetry.log(pipelineType, PipelineStage.PLANNER, "Starting DeepSearch for: ${query.take(50)}")
        send(DeepSearchProgress.AnalyzingIntent("Analyzing query intent & language footprint..."))

        val plan = qp.plan(query, QueryPlanConfig(maxTimeoutMillis = 3500L, isFactCheck = false))
        send(DeepSearchProgress.QueryExpansion(plan.queries))

        val searchResult = so.executeDeepSearchFallback(query, plan) { engineName, found, _ ->
            send(DeepSearchProgress.Searching(engineName, 1, found))
        }
        val rawSources = searchResult.sources
        val activeEngine = searchResult.engine

        send(DeepSearchProgress.RankingSources(rawSources.size, rawSources.size))
        val rankedSources = so.rerankSources(query, rawSources)

        val selectionRatio = when {
            activeEngine.contains("tinyfish", ignoreCase = true) -> 0.65
            activeEngine.contains("degoog", ignoreCase = true)   -> 0.75
            else                                                 -> 0.40
        }
        val topSources = rankedSources
            .take(((rankedSources.size * selectionRatio).toInt().coerceIn(12, 50)).coerceAtMost(rankedSources.size))

        val citations = CitationEngine.mapToCitations(topSources)
        val evidencePrompt = CitationEngine.buildEvidencePrompt(citations)
        send(DeepSearchProgress.GeneratingReport(topSources.size))

        val languageInstruction = context?.let { ctx ->
            ResponseLanguageManager(ctx).getLanguageInstructionPrompt()
        } ?: ""

        val llmReq = LlmRequest(
            systemPrompt = DeepSearchPrompt.SYSTEM_PROMPT +
                    (if (languageInstruction.isNotBlank()) "\n\n$languageInstruction\n\n" else "\n\n") +
                    "YOUR TASK:\nReturn ONLY the markdown report. Use proper Markdown headings.",
            userPrompt = "RESEARCH QUERY: \"$query\"\n\nEVIDENCE FOUND:\n$evidencePrompt\n\n" +
                    "Analyze the evidence and generate the research report.",
            jsonOutput = false,
            temperature = 0.3
        )
        val markdownBuilder = StringBuilder()
        lo.streamFallback(llmReq).collect { chunk ->
            markdownBuilder.append(chunk)
            send(DeepSearchProgress.StreamingChunk(chunk))
        }

        val report = DeepSearchReport(
            id = UUID.randomUUID().toString(),
            query = query,
            reportContent = markdownBuilder.toString(),
            sources = citations.map {
                DeepSearchSource(citationId = it.id, title = it.title, domain = it.domain, url = it.url, snippet = it.snippet)
            },
            detectedIntent = plan.intent,
            timestamp = System.currentTimeMillis(),
            isPublic = true
        )
        PipelineTelemetry.log(pipelineType, PipelineStage.PERSISTENCE, "DeepSearch report generated successfully.")
        send(DeepSearchProgress.Success(report))
    }.flowOn(Dispatchers.IO).catch { e ->
        PipelineTelemetry.error(pipelineType, PipelineStage.STREAM, "DeepSearch failed", e)
        emit(DeepSearchProgress.Error(e.localizedMessage ?: "Unknown DeepSearch error"))
    }

    // ── Legacy Step-by-Step API (used by DeepSearchScreen directly) ───────────
    // These delegate to the shared Orchestrators and maintain full backward compatibility.

    /** Stage 0: Plan search intent and generate weighted queries. */
    suspend fun planSearchIntent(query: String): DeepSearchPlan {
        val qp = requireNotNull(_queryPlanner) { "Context must be set before calling planSearchIntent" }
        return qp.plan(query, QueryPlanConfig(maxTimeoutMillis = 3500L, isFactCheck = false))
    }

    /** Stage 1: Execute fallback search (TinyFish -> Degoog -> SearXNG). */
    suspend fun executeParallelSearches(
        plan: DeepSearchPlan,
        onProgress: suspend (foundCount: Int, newSources: List<DeepSearchRawSource>) -> Unit
    ): List<DeepSearchRawSource> {
        val so = requireNotNull(_searchOrchestrator) { "Context must be set before calling executeParallelSearches" }
        val primaryQuery = plan.queries.firstOrNull()?.query ?: ""
        val searchResult = so.executeDeepSearchFallback(primaryQuery, plan) { _, foundCount, newSources ->
            onProgress(foundCount, newSources)
        }
        return searchResult.sources
    }

    /** Stage 2: Deduplicate and normalize raw sources. */
    fun deduplicateAndNormalize(rawSources: List<DeepSearchRawSource>): List<DeepSearchRawSource> {
        val so = requireNotNull(_searchOrchestrator) { "Context must be set before calling deduplicateAndNormalize" }
        return so.deduplicateAndNormalize(rawSources)
    }

    /** Stage 3: Re-rank sources by credibility and query relevance. */
    fun rerankSources(query: String, sources: List<DeepSearchRawSource>): List<DeepSearchRawSource> {
        val so = requireNotNull(_searchOrchestrator) { "Context must be set before calling rerankSources" }
        return so.rerankSources(query, sources)
    }

    /** Scoring helper — exposed for live source filtering in the screen. */
    fun calculateRelevanceScore(query: String, title: String, snippet: String, url: String): Double {
        val so = requireNotNull(_searchOrchestrator) { "Context must be set before calling calculateRelevanceScore" }
        return so.calculateRelevanceScore(query, title, snippet, url)
    }

    /** Domain extraction helper — exposed for card display in the screen. */
    fun extractDomainPublic(url: String): String = CitationEngine.extractDomainPublic(url)

    /**
     * Stage 4: Synthesize the final research report from ranked sources.
     *
     * This is a non-streaming blocking call used by the legacy screen's coroutine pattern.
     * The screen handles its own typewriter animation after receiving the complete markdown.
     */
    suspend fun synthesizeReport(
        query: String,
        plan: DeepSearchPlan,
        allSortedSources: List<DeepSearchRawSource>,
        responseLanguage: ResponseLanguage = ResponseLanguage.AUTO
    ): String {
        val lo = requireNotNull(_llmOrchestrator) { "Context must be set before calling synthesizeReport" }

        val activeEngine = allSortedSources.firstOrNull()?.engine ?: "searxng"
        val selectionRatio = when {
            activeEngine.contains("tinyfish", ignoreCase = true) -> 0.65
            activeEngine.contains("degoog", ignoreCase = true)   -> 0.75
            else                                                 -> 0.40
        }
        val topSources = allSortedSources
            .take(((allSortedSources.size * selectionRatio).toInt().coerceIn(12, 50)).coerceAtMost(allSortedSources.size))
        val citations = CitationEngine.mapToCitations(topSources)
        val evidencePrompt = CitationEngine.buildEvidencePrompt(citations)

        val languageInstruction = ResponseLanguageManager(context ?: throw IllegalStateException("Context required")).apply {
            setLanguage(responseLanguage)
        }.getLanguageInstructionPrompt()

        val systemInstruction = DeepSearchPrompt.SYSTEM_PROMPT +
                "\n\n$languageInstruction\n\n" +
                "YOUR TASK:\nReturn ONLY the markdown report. DO NOT return a JSON object.\n" +
                "Use proper Markdown headings. Focus on accuracy and depth.\n"

        val userPrompt = "RESEARCH QUERY: \"$query\"\n\n" +
                "EVIDENCE FOUND:\n$evidencePrompt\n\n" +
                "Analyze the evidence and generate the research report."

        val llmReq = LlmRequest(
            systemPrompt = systemInstruction,
            userPrompt = userPrompt,
            jsonOutput = false,
            temperature = 0.3
        )

        return lo.generateFallback(llmReq)
    }

    // ── Serialization helpers (used by ReportScreen for legacy reports) ────────
    // These mirror the old engine's static parsing that FactCheckEngine also relied on.

    fun deserializeSources(json: String): List<com.example.data.model.FactCheckSource> {
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                com.example.data.model.FactCheckSource(
                    title = obj.optString("title"),
                    url = obj.optString("url"),
                    snippet = obj.optString("snippet"),
                    domain = obj.optString("domain"),
                    credibilityTier = obj.optInt("credibilityTier", 3),
                    publishDate = obj.optString("publishDate")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deserializeContradictions(json: String): List<com.example.data.model.ContradictingEvidence> {
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                com.example.data.model.ContradictingEvidence(
                    note = obj.optString("note"),
                    url = obj.optString("url"),
                    title = obj.optString("title")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
