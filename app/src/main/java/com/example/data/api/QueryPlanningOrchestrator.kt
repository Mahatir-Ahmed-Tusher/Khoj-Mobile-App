package com.example.data.api

import android.util.Log
import com.example.data.telemetry.PipelineStage
import com.example.data.telemetry.PipelineTelemetry
import com.example.data.telemetry.PipelineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

class QueryPlanningOrchestrator(
    private val llmOrchestrator: LlmOrchestrator,
    private val pipelineType: PipelineType,
    private val circuitBreaker: CircuitBreaker = CircuitBreaker()
) : QueryPlanner {
    
    private val tag = "QueryPlanner"

    override suspend fun plan(query: String, config: QueryPlanConfig): DeepSearchPlan = withContext(Dispatchers.IO) {
        val cleanRaw = query.trim()
        if (circuitBreaker.isOpen()) {
            PipelineTelemetry.warn(pipelineType, PipelineStage.PLANNER, "Circuit breaker OPEN. Using heuristic planner.")
            return@withContext getHeuristicPlan(cleanRaw, config.isFactCheck)
        }

        // Single Source of Truth: If input > 250 characters and not from image, prune/extract the core claim first
        val effectiveQuery = if (!config.isFromImage && cleanRaw.length > 250) {
            PipelineTelemetry.log(pipelineType, PipelineStage.PLANNER, "Input length (${cleanRaw.length}) > 250. Pruning via ClaimExtractor...")
            try {
                ClaimExtractor.extractClaim(cleanRaw)
            } catch (e: Exception) {
                PipelineTelemetry.warn(pipelineType, PipelineStage.PLANNER, "ClaimExtractor failed, using original: ${e.message}")
                cleanRaw
            }
        } else {
            cleanRaw
        }
        
        try {
            PipelineTelemetry.log(pipelineType, PipelineStage.PLANNER, "Planning query (isFromImage: ${config.isFromImage}, Timeout: ${config.maxTimeoutMillis}ms)")
            
            val request = LlmRequest(
                systemPrompt = getPlanningPrompt(config, effectiveQuery),
                userPrompt = "Query: $effectiveQuery",
                jsonOutput = true,
                temperature = 0.0,
                maxTokens = 1000
            )

            val jsonStr = withTimeout(config.maxTimeoutMillis) {
                llmOrchestrator.generateFallback(request)
            }

            if (jsonStr.isBlank()) throw Exception("Empty response from LLM planner")
            
            val parsed = parsePlanJson(jsonStr)
            circuitBreaker.recordSuccess()
            PipelineTelemetry.log(pipelineType, PipelineStage.PLANNER, "Successfully planned: ${parsed.queries.size} queries")
            return@withContext parsed
        } catch (e: TimeoutCancellationException) {
            PipelineTelemetry.warn(pipelineType, PipelineStage.PLANNER, "Planning TIMEOUT. Falling back to heuristics.")
            return@withContext getHeuristicPlan(effectiveQuery, config.isFactCheck, config.isFromImage)
        } catch (e: Exception) {
            PipelineTelemetry.error(pipelineType, PipelineStage.PLANNER, "Planning failed. Falling back to heuristics.", e)
            circuitBreaker.recordFailure()
            return@withContext getHeuristicPlan(effectiveQuery, config.isFactCheck, config.isFromImage)
        }
    }

    private fun parsePlanJson(jsonStr: String): DeepSearchPlan {
        // Strip out <think> tags if they exist (models like Gemini/Claude might emit them)
        val cleanJsonStr = jsonStr.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
        
        val root = try {
            JSONObject(cleanJsonStr.removePrefix("```json").removeSuffix("```").trim())
        } catch (e: Exception) {
            // Attempt to extract json object if mixed with text
            val start = cleanJsonStr.indexOf("{")
            val end = cleanJsonStr.lastIndexOf("}")
            if (start != -1 && end != -1) {
                JSONObject(cleanJsonStr.substring(start, end + 1))
            } else {
                throw Exception("Failed to parse JSON: $cleanJsonStr", e)
            }
        }
        
        val intent = root.optString("intent", "informational")
        val strategy = root.optString("languageStrategy", "mixed")
        val queriesArray = root.optJSONArray("queries") ?: JSONArray()
        val queries = mutableListOf<WeightedQuery>()
        
        for (i in 0 until queriesArray.length()) {
            val qObj = queriesArray.optJSONObject(i) ?: continue
            val q = qObj.optString("query")
            val p = qObj.optString("purpose")
            val w = qObj.optDouble("weight", 1.0)
            if (q.isNotBlank()) {
                queries.add(WeightedQuery(q, p, w))
            }
        }
        
        // Extract explicit query_bn and query_en if present
        val queryBn = root.optString("query_bn", "").takeIf { it.isNotBlank() }
            ?: queries.firstOrNull { it.query.any { c -> c in '\u0980'..'\u09FF' } }?.query
        val queryEn = root.optString("query_en", "").takeIf { it.isNotBlank() }
            ?: queries.firstOrNull { it.query.none { c -> c in '\u0980'..'\u09FF' } }?.query

        if (queryBn != null && queries.none { it.query.equals(queryBn, ignoreCase = true) }) {
            queries.add(0, WeightedQuery(queryBn, "Bengali Local Search", 1.0))
        }
        if (queryEn != null && queries.none { it.query.equals(queryEn, ignoreCase = true) }) {
            queries.add(WeightedQuery(queryEn, "English Global Search", 1.0))
        }

        if (queries.isEmpty()) throw Exception("Parsed JSON had 0 queries")
        
        val distinctQueries = queries.distinctBy { it.query.lowercase().trim() }.take(4)
        return DeepSearchPlan(intent, strategy, distinctQueries, queryBn = queryBn, queryEn = queryEn)
    }

    private fun getHeuristicPlan(originalQuery: String, isFactCheck: Boolean, isFromImage: Boolean = false): DeepSearchPlan {
        val clean = originalQuery.replace(Regex("ভাইয়া|আপু|বলেন|বলবেন|কী|কি|এটা|সত্যি|মিথ্যা|গুজব|ফেসবুকে দেখলাম"), "").trim()
        val intent = if (isFromImage) "photocard_fact_check" else if (isFactCheck) "fact_check" else "informational"
        val hasBangla = clean.any { it in '\u0980'..'\u09FF' }

        // Keep keywords lowkey and focused (max 4-5 words)
        val keyWords = clean.split(Regex("\\s+")).filter { it.isNotBlank() }.take(5).joinToString(" ")

        val qBn = if (isFromImage) {
            "$keyWords ভুয়া ফটোকার্ড OR rumor scanner"
        } else if (hasBangla) {
            "$keyWords যাচাই"
        } else ""

        val qEn = if (isFromImage) "$keyWords photocard fact check" else "$keyWords fact check"

        val queries = if (isFromImage) {
            listOf(
                WeightedQuery("$keyWords ভুয়া ফটোকার্ড OR rumor scanner", "Photocard Debunk Search", 1.0),
                WeightedQuery("$keyWords সত্যতা OR যাচাই", "Authenticity Verification", 0.9),
                WeightedQuery("$keyWords news OR report", "Media Archive Search", 0.8)
            )
        } else if (isFactCheck) {
            listOf(
                WeightedQuery("$keyWords যাচাই", "Direct Claim Verification", 1.0),
                WeightedQuery("$keyWords গুজব OR ভুয়া", "Debunk Search", 0.9),
                WeightedQuery("$keyWords fact check", "Global Verification", 0.8)
            )
        } else {
            listOf(
                WeightedQuery(keyWords, "Direct Search", 1.0),
                WeightedQuery("$keyWords news", "Recent Updates", 0.8)
            )
        }
        return DeepSearchPlan(intent, "mixed", queries.take(3), queryBn = qBn.ifBlank { null }, queryEn = qEn.ifBlank { null })
    }

    private fun getPlanningPrompt(config: QueryPlanConfig, query: String): String {
        val temporalKeywords = listOf(
            // Bengali
            "আজ", "আজকে", "গতকাল", "আগামীকাল", "আগামী", "গত", "পরশু", "গতপরশু", "এই সপ্তাহে", "গত সপ্তাহে",
            "এই মাসে", "গত মাসে", "সদ্য", "এইমাত্র", "রিসেন্টলি", "এখন", "পরের বছর", "পরের দিন", "দিন", "আগের বছর", "এই বছর",
            // Banglish
            "aj", "aaj", "ajk", "ajke", "aajke", "ajky", "ajkey", "azke", "az", "aaz", "azkey", "kaal", "kal", "kalke",
            "gotokal", "agamikal", "goto", "agami", "porshu", "poroshu", "recent", "bochor", "porer bochor", "porer din",
            "ager bochor", "aager bochor", "ager bocor", "porer bocor", "ei bochor", "ai bochor", "ai bocor", "ei bocor", "ekhon",
            // English
            "today", "yesterday", "tomorrow", "the other day", "day", "last", "previous", "next", "this week", "last week",
            "next week", "this month", "last month", "recent", "recently", "now", "year", "this year", "previous year",
            "last year", "next year"
        )

        val lowercaseQuery = query.lowercase()
        val needsTemporalContext = temporalKeywords.any { lowercaseQuery.contains(it) }

        val temporalInjection = if (needsTemporalContext) {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy")
            val currentDate = java.time.LocalDate.now().format(formatter)
            "\n\n[CRITICAL TIME CONTEXT]\nToday's date is: $currentDate. Use this exact date to resolve any relative time references like 'today', 'yesterday', 'tomorrow', 'last week', 'last year', etc., in the user's query."
        } else ""

        if (config.isFromImage) {
            return """
            You are a Bilingual Photocard & Image Fact-Check Query Planner. 
            The claim originates from an extracted social media photocard, banner, or image headline.
            Output valid JSON ONLY.
            
            Generate strictly LOWKEY, SKEPTIC, 3-5 word keyword queries (NOT sentences):
            1. "query_bn": A targeted 3-5 word Bengali search phrase (e.g., "[Headline keywords] ভুয়া ফটোকার্ড OR rumor scanner").
            2. "query_en": A targeted 3-5 word English search phrase for global verification.
            3. "queries": A list of exactly 3 focused, lowkey search queries (3 to 5 words each):
               - Photocard Debunk Search: [Headline keywords] ভুয়া ফটোকার্ড OR rumor scanner
               - Media Outlet Archive Search: [Purported Outlet/Subject] [Core phrase] প্রতিবেদন
               - Direct Fact Check Anchor: [Subject keywords] fact check
            
            CORE SEARCH PRINCIPLES FOR PHOTOCARDS:
            - Strictly 3 to 5 words per query. Strip all layout metadata, design noise, category tags.
            - Include fact-checking keywords ("গুজব", "ভুয়া ফটোকার্ড", "Rumor Scanner", "Fact Check").
            
            JSON Format:
            {
              "intent": "photocard_fact_check",
              "languageStrategy": "bilingual",
              "query_bn": "...",
              "query_en": "...",
              "queries": [
                { "query": "...", "purpose": "Photocard debunk search", "weight": 1.0 },
                { "query": "...", "purpose": "Media archive search", "weight": 1.0 },
                { "query": "...", "purpose": "Direct fact check", "weight": 0.9 }
              ]
            }
            Do not include <think> tags or markdown.$temporalInjection
            """.trimIndent()
        }

        return if (config.isFactCheck) {
            """
            You are a Skeptic, Bilingual Fact-Check Query Planner. 
            Output valid JSON ONLY.
            
            Your goal is to extract LOWKEY, SKEPTIC, and HIGH-SIGNAL keyword queries (NOT long sentences).
            Search engines (Google, SearXNG) match best with 3 to 5 keyword terms. Never generate long phrases or natural language paragraphs.
            
            Generate strictly:
            1. "query_bn": A focused 3-5 word Bengali search phrase.
            2. "query_en": A focused 3-5 word English search phrase for international fact-checking.
            3. "queries": Exactly 3 distinct, skeptic search queries (3 to 5 keywords each):
               - Bengali Direct Anchor: [Core Subject] [Core Action/Number] (e.g., "রিয়াদ বিমানবন্দর হুতি হামলা ১২ নিহত")
               - Bengali Skeptic/Debunk Anchor: [Core Subject] [যাচাই OR গুজব OR ভুয়া] (e.g., "রিয়াদ বিমানবন্দরে বাংলাদেশি নিহত গুজব")
               - English Global/Fact-Check Anchor: [Core Subject keywords] fact check (e.g., "Riyadh airport drone casualties fact check")
            
            STRICT KEYWORD PRINCIPLES:
            - Length: Strictly 3 to 5 words per query. Never exceed 6 words.
            - No grammar filler: Drop conversational words (e.g., 'সম্পর্কে', 'নিয়ে', 'হলে', 'কী', 'কি', 'about', 'whether').
            - Entity Precision: Keep the essential subject name and specific number/institution.
            
            JSON Format:
            {
              "intent": "fact_check",
              "languageStrategy": "bilingual",
              "query_bn": "...",
              "query_en": "...",
              "queries": [
                { "query": "...", "purpose": "Direct anchor", "weight": 1.0 },
                { "query": "...", "purpose": "Skeptic debunk anchor", "weight": 0.9 },
                { "query": "...", "purpose": "Global fact check anchor", "weight": 0.9 }
              ]
            }
            Do not include <think> tags or markdown.$temporalInjection
            """.trimIndent()
        } else {
            """
            You are a Deep Search Query Planner. 
            Output valid JSON ONLY.
            Generate up to 4 comprehensive research queries.
            JSON Format:
            {
              "intent": "informational",
              "languageStrategy": "mixed",
              "queries": [
                { "query": "...", "purpose": "Broad overview", "weight": 1.0 },
                { "query": "...", "purpose": "Deep dive", "weight": 0.8 }
              ]
            }
            Do not include <think> tags or markdown.$temporalInjection
            """.trimIndent()
        }
    }

    suspend fun routeSearchIntent(
        query: String,
        recentHistoryContext: String = "",
        isFactCheckMode: Boolean = false
    ): SearchRoutingDecision = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) {
            return@withContext SearchRoutingDecision(needsSearch = false, searchQuery = "")
        }

        if (circuitBreaker.isOpen()) {
            PipelineTelemetry.warn(pipelineType, PipelineStage.PLANNER, "Circuit breaker OPEN for routing. Falling back to default decision.")
            val needs = isFactCheckMode || clean.length > 25
            return@withContext SearchRoutingDecision(needsSearch = needs, searchQuery = if (needs) clean else "")
        }

        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val currentDate = sdf.format(java.util.Date())

            val systemPrompt = """
                You are the Search Decision & Query Reformulation Router for 'Khoj AI' (Bangladesh).
                Analyze the user message and conversation context. Output valid JSON ONLY.
                Reference Date: $currentDate.

                Rules:
                1. needs_search = false:
                   - Greetings, chit-chat, personal remarks, advice, opinions, compliments (e.g. "হাই", "হ্যালো", "ধন্যবাদ", "কেমন আছেন", "hi", "how are you").
                   - Programming code generation, syntax, debugging, algorithms, math calculations.
                   - Static encyclopedic facts, historical facts, definitions, or science concepts (e.g. "speed of light", "capital of France").
                   - Queries asking for explanation, creative writing, or translation of static topics.
                   - Requests about the assistant itself ("who are you", "তুমি কে").
                2. needs_search = true:
                   - Breaking news, recent political, social, or incident updates in Bangladesh or worldwide.
                   - Real-time/fluctuating information (today's gold price, dollar rate, commodity price, live match score, today's weather).
                   - Verifying claims, rumours, viral social media posts, or uncertain events.
                   - Specific government administrative rules, official portal links, fees, or circulars.
                   - When fact check mode is requested.
                3. If needs_search is true:
                   - Produce "search_query": Exactly 3 to 5 high-density keyword terms in Bengali or English optimized for search engines.
                   - Drop filler words. Resolve pronouns/anaphora using the context.
                4. If needs_search is false:
                   - "search_query" MUST be empty string "".

                JSON Schema:
                {
                  "needs_search": true or false,
                  "search_query": "3-5 keyword terms or empty string"
                }
                Do not include <think> tags, markdown or extra text.
            """.trimIndent()

            val userPromptText = if (recentHistoryContext.isNotBlank()) {
                "Recent Context:\n$recentHistoryContext\n\nCurrent Message: $clean"
            } else {
                "Current Message: $clean"
            }

            val request = LlmRequest(
                systemPrompt = systemPrompt,
                userPrompt = userPromptText,
                jsonOutput = true,
                temperature = 0.0,
                maxTokens = 120
            )

            val jsonStr = withTimeout(4000L) {
                llmOrchestrator.generateFallback(request)
            }

            if (jsonStr.isBlank()) throw Exception("Empty response from LLM router")

            val cleanJson = jsonStr.replace(Regex("<think>[\\s\\S]*?</think>"), "")
                .removePrefix("```json").removeSuffix("```").trim()

            val start = cleanJson.indexOf("{")
            val end = cleanJson.lastIndexOf("}")
            val root = if (start != -1 && end != -1) {
                JSONObject(cleanJson.substring(start, end + 1))
            } else {
                JSONObject(cleanJson)
            }

            val needsSearch = root.optBoolean("needs_search", isFactCheckMode)
            val parsedSearchQuery = root.optString("search_query", "").trim()
            val effectiveQuery = if (needsSearch) {
                parsedSearchQuery.ifBlank { clean }
            } else {
                ""
            }

            circuitBreaker.recordSuccess()
            PipelineTelemetry.log(pipelineType, PipelineStage.PLANNER, "Routing decision: needsSearch=$needsSearch, query='$effectiveQuery'")
            SearchRoutingDecision(needsSearch = needsSearch, searchQuery = effectiveQuery)
        } catch (e: Exception) {
            PipelineTelemetry.warn(pipelineType, PipelineStage.PLANNER, "Routing failed (${e.message}). Using safe fallback.")
            val hasTemporal = listOf("আজ", "আজকে", "গত", "সাম্প্রতিক", "সর্বশেষ", "খবর", "দাম", "now", "today", "latest", "news")
                .any { clean.contains(it, ignoreCase = true) }
            val fallbackNeeds = isFactCheckMode || hasTemporal
            SearchRoutingDecision(
                needsSearch = fallbackNeeds,
                searchQuery = if (fallbackNeeds) clean else ""
            )
        }
    }
}
