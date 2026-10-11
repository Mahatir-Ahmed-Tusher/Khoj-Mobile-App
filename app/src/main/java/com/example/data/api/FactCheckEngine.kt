package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.data.preferences.ResponseLanguage
import com.example.data.preferences.ResponseLanguageManager
import com.example.data.model.ContradictingEvidence
import com.example.data.model.FactCheckReport
import com.example.data.model.FactCheckSource
import com.example.data.prompt.FactCheckPrompt
import com.example.data.prompt.SharedCitationPrompt
import com.example.data.telemetry.PipelineStage
import com.example.data.telemetry.PipelineTelemetry
import com.example.data.telemetry.PipelineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed class FactCheckProgress {
    object Idle : FactCheckProgress()
    object ExtractingImageText : FactCheckProgress()
    object ClaimNormalization : FactCheckProgress()
    object QueryPlanning : FactCheckProgress()
    data class Searching(val source: String) : FactCheckProgress()
    data class RankingSources(val count: Int, val domains: List<String> = emptyList()) : FactCheckProgress()
    object GeneratingVerdict : FactCheckProgress()
    data class Success(val report: FactCheckReport) : FactCheckProgress()
    data class Error(val message: String) : FactCheckProgress()
    data class StreamingChunk(val chunk: String) : FactCheckProgress()
}

class FactCheckEngine(private val context: Context) {
    private val tag = "FactCheckEngine"

    private val pipelineType = PipelineType.FACT_CHECK
    private val searchOrchestrator by lazy { SearchOrchestrator(context) }
    private val llmOrchestrator by lazy { LlmOrchestrator(context, pipelineType, isForPlanning = false) }
    private val planningLlmOrchestrator by lazy { LlmOrchestrator(context, pipelineType, isForPlanning = true) }
    private val queryPlanner by lazy { QueryPlanningOrchestrator(planningLlmOrchestrator, pipelineType) }

    fun isCustomPipelineConfigured(): Boolean {
        return searchOrchestrator.providers.isNotEmpty()
    }

    private fun getFriendlyErrorMessage(e: Throwable): String {
        val msg = e.message ?: ""
        val isNetwork = e is java.net.UnknownHostException || 
                        e is java.net.ConnectException || 
                        e is java.net.SocketTimeoutException || 
                        msg.contains("Unable to resolve host", ignoreCase = true) ||
                        msg.contains("Failed to connect to", ignoreCase = true) ||
                        msg.contains("timeout", ignoreCase = true)

        return if (isNetwork) {
            "ইন্টারনেট সংযোগ ত্রুটি: অনুগ্রহ করে আপনার ডিভাইসের ইন্টারনেট সংযোগ বা DNS সেটিংস পরীক্ষা করুন এবং পুনরায় চেষ্টা করুন।"
        } else {
            e.localizedMessage ?: e.message ?: "An unexpected error occurred"
        }
    }
    fun factCheckStream(
        claim: String, 
        responseLanguage: ResponseLanguage = ResponseLanguage.AUTO,
        isFromImage: Boolean = false
    ): Flow<FactCheckProgress> = channelFlow {
        send(FactCheckProgress.ClaimNormalization)

        val cleanRaw = claim.trim()
        val isDetectedImage = isFromImage || cleanRaw.contains("[ছবি থেকে প্রাপ্ত তথ্য/লেখা]") || cleanRaw.contains("[ছবি থেকে প্রাপ্ত")

        val effectiveClaim = if (isDetectedImage) {
            // For images, ImageOcrHelper.cleanOcrText already distilled the photocard claim.
            // Parse out the clean photocard claim and any user-provided context without re-pruning.
            val parts = cleanRaw.split(Regex("\\[ছবি থেকে প্রাপ্ত তথ্য/লেখা\\]:?"))
            val userContext = parts.getOrNull(0)?.trim()?.removePrefix("[")?.removeSuffix("]")?.trim() ?: ""
            val ocrClaim = parts.getOrNull(1)?.trim() ?: cleanRaw
            
            if (userContext.isNotBlank() && !ocrClaim.contains(userContext)) {
                "$userContext: $ocrClaim"
            } else {
                ocrClaim
            }
        } else {
            val sentences = cleanRaw.split(Regex("[।\n!?]")).map { it.trim() }.filter { it.isNotBlank() }
            val hasQuestions = cleanRaw.contains("?") || cleanRaw.contains("কী") || cleanRaw.contains("কি ") || cleanRaw.contains("নাকি")
            val needsNormalization = cleanRaw.length > 120 || sentences.size > 2 || cleanRaw.contains("\n") || hasQuestions

            if (needsNormalization) {
                PipelineTelemetry.log(pipelineType, PipelineStage.PLANNER, "Normalizing claim (${cleanRaw.length} chars, ${sentences.size} sentences)...")
                try {
                    val distilled = ClaimExtractor.extractClaim(cleanRaw)
                    if (distilled.isNotBlank()) distilled else cleanRaw
                } catch (e: Exception) {
                    PipelineTelemetry.warn(pipelineType, PipelineStage.PLANNER, "Claim normalization failed, using raw: ${e.message}")
                    cleanRaw
                }
            } else {
                cleanRaw
            }
        }

        PipelineTelemetry.log(pipelineType, PipelineStage.PLANNER, "Effective claim to verify (isFromImage: $isDetectedImage): ${effectiveClaim.take(60)}")
        
        send(FactCheckProgress.QueryPlanning)
        val plan = queryPlanner.plan(effectiveClaim, QueryPlanConfig(isFactCheck = true, isFromImage = isDetectedImage))
        
        // Collect queries and search
        send(FactCheckProgress.Searching("Fallback Cascade"))
        
        val qBn = plan.queryBn ?: plan.queries.firstOrNull { it.query.any { c -> c in '\u0980'..'\u09FF' } }?.query
        val qEn = plan.queryEn ?: plan.queries.firstOrNull { it.query.none { c -> c in '\u0980'..'\u09FF' } }?.query

        val primaryQuery = qBn ?: plan.queries.firstOrNull()?.query ?: effectiveClaim
        val secondaryQuery = qEn ?: (if (primaryQuery != effectiveClaim) effectiveClaim else "")

        val rawSources = if (secondaryQuery.isNotBlank() && secondaryQuery != primaryQuery) {
            searchOrchestrator.executeBilingualFallbackSearch(primaryQuery, secondaryQuery) { providerName ->
                send(FactCheckProgress.Searching(providerName))
            }
        } else {
            searchOrchestrator.executeFallbackSearch(primaryQuery) { providerName ->
                send(FactCheckProgress.Searching(providerName))
            }
        }

        val domains = rawSources.mapNotNull {
            try { java.net.URI(it.url).host } catch(e: Exception) { null }
        }.distinct().take(10)
        send(FactCheckProgress.RankingSources(rawSources.size, domains))
        val rankedSources = searchOrchestrator.rerankBilingualSources(primaryQuery, secondaryQuery, rawSources)
        
        // Step 2 of RAG: Content Enrichment for top 6-7 authoritative sources from Cloudflare Worker
        val topUrls = rankedSources.take(7).map { it.url }
        if (topUrls.isNotEmpty()) {
            try {
                val contentMap = searchOrchestrator.fetchContentForUrls(topUrls)
                rankedSources.forEach { source ->
                    contentMap[source.url]?.let { cleanContent ->
                        if (cleanContent.isNotBlank()) {
                            source.content = cleanContent
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Content extraction failed, falling back to snippets: ${e.message}")
            }
        }

        val citations = CitationEngine.mapToCitations(rankedSources)
        val evidencePrompt = CitationEngine.buildEvidencePrompt(citations)

        send(FactCheckProgress.GeneratingVerdict)
        
        // Build LLM Request for Synthesis
        val languageInstruction = ResponseLanguageManager(context).apply {
            setLanguage(responseLanguage)
        }.getLanguageInstructionPrompt()

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val currentDateStr = sdf.format(java.util.Date())

        val systemInstruction = FactCheckPrompt.getSystemPrompt(currentDateStr) +
                "\n\n$languageInstruction\n\n" +
                "YOUR TASK:\n" +
                "Return ONLY the markdown report. DO NOT return a JSON object.\n" +
                "You MUST structure the report exactly as requested in the FORMAT REQUIREMENTS.\n"

        val userPrompt = buildString {
            append("FACT CHECK CLAIM TO VERIFY: \"$effectiveClaim\"\n\n")
            if (cleanRaw != effectiveClaim) {
                append("UNVERIFIED USER SUBMISSION (FOR CONTEXT ONLY, DO NOT ASSUME FACTUALITY):\n\"$cleanRaw\"\n\n")
            }
            append("EVIDENCE FOUND:\n$evidencePrompt\n\n")
            append("Analyze the evidence and generate the fact check report in Markdown format. Base your verdict, summary, and background strictly on the verified EVIDENCE FOUND with inline citations [1], [2]. Do not adopt unverified user assertions as facts.")
        }

        val llmReq = LlmRequest(
            systemPrompt = systemInstruction,
            userPrompt = userPrompt,
            jsonOutput = false,
            temperature = 0.1
        )

        val markdownBuilder = java.lang.StringBuilder()
        llmOrchestrator.streamFallback(llmReq).collect { chunk ->
            markdownBuilder.append(chunk)
            send(FactCheckProgress.StreamingChunk(chunk))
        }

        val finalMarkdown = markdownBuilder.toString()
        
        // Extraction of Verdict strictly locked to 3 official verdicts: true, false, misleading
        var verdictStr = "misleading" // Safe default
        val upperMd = finalMarkdown.uppercase()
        if (upperMd.contains("**TRUE**") || upperMd.contains("**সত্য**")) {
            verdictStr = "true"
        } else if (upperMd.contains("**FALSE**") || upperMd.contains("**অসত্য**")) {
            verdictStr = "false"
        } else if (upperMd.contains("**MISLEADING**") || upperMd.contains("**বিভ্রান্তিকর**")) {
            verdictStr = "misleading"
        }

        val extractedSummary = finalMarkdown.substringAfter("## সারাংশ:")
            .substringBefore("# বিস্তারিত ব্যাখ্যা")
            .substringBefore("## দাবির উৎস ও প্রসঙ্গ")
            .trim()
        val summaryText = if (extractedSummary.isNotBlank()) extractedSummary.take(600) else ""

        // Save legacy format for compatibility, but `markdownReport` is the source of truth
        val report = FactCheckReport(
            id = UUID.randomUUID().toString(),
            claimText = effectiveClaim,
            normalizedClaim = effectiveClaim,
            verdict = verdictStr,
            confidence = 85,
            summaryBn = summaryText,
            summaryEn = summaryText,
            reasoningBn = "",
            reasoningEn = "",
            sourcesJson = serializeCitationsAsSources(citations),
            contradictingSourcesJson = "[]",
            markdownReport = finalMarkdown
        )

        send(FactCheckProgress.Success(report))
        PipelineTelemetry.log(pipelineType, PipelineStage.PERSISTENCE, "Generated markdown report.")
    }.flowOn(Dispatchers.IO).catch { e ->
        PipelineTelemetry.error(pipelineType, PipelineStage.STREAM, "FactCheck flow failed", e)
        emit(FactCheckProgress.Error(getFriendlyErrorMessage(e)))
    }

    // Wrap the stream to emit a single Success for legacy UI compat if needed
    fun factCheck(claim: String, responseLanguage: ResponseLanguage = ResponseLanguage.AUTO, isFromImage: Boolean = false): Flow<FactCheckProgress> = factCheckStream(claim, responseLanguage, isFromImage)

    private fun serializeCitationsAsSources(citations: List<com.example.data.model.Citation>): String {
        val arr = JSONArray()
        citations.forEach { c ->
            arr.put(JSONObject().apply {
                put("title", c.title)
                put("url", c.url)
                put("snippet", c.snippet)
                put("domain", c.domain)
                put("credibilityTier", CitationEngine.getCredibilityTier(c.domain))
                put("publishDate", "")
            })
        }
        return arr.toString()
    }

    // Helper to deserialize sources
    fun deserializeSources(json: String): List<FactCheckSource> {
        if (json.isEmpty()) return emptyList()
        val list = mutableListOf<FactCheckSource>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    FactCheckSource(
                        title = obj.optString("title", ""),
                        url = obj.optString("url", ""),
                        snippet = obj.optString("snippet", ""),
                        domain = obj.optString("domain", ""),
                        credibilityTier = obj.optInt("credibilityTier", 3),
                        publishDate = obj.optString("publishDate", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Deserialization of sources failed", e)
        }
        return list
    }

    // Helper to deserialize contradictions
    fun deserializeContradictions(json: String): List<ContradictingEvidence> {
        if (json.isEmpty()) return emptyList()
        val list = mutableListOf<ContradictingEvidence>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ContradictingEvidence(
                        note = obj.optString("note", ""),
                        url = obj.optString("url", ""),
                        title = obj.optString("title", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Deserialization of contradictions failed", e)
        }
        return list
    }
}
