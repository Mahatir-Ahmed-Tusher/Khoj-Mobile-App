package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Serializable
data class AnalyticsResult(
    val topic: String,
    val riskLevel: String? = null,
    val insights: String? = null,
    val advice: String? = null
)

object AnalyticsEngine {
    private const val TAG = "AnalyticsEngine"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getCleanKey(key: String): String {
        return key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    }

    private fun formatToOneLineAdvice(raw: String): String {
        var cleaned = raw.trim()
            .replace(Regex("^(?:[০-৯0-9]+[.)\\-]|[*•\\-])\\s*"), "")
            .trim()

        val firstLine = cleaned.lines().firstOrNull { it.isNotBlank() }?.trim() ?: cleaned
        cleaned = firstLine.replace(Regex("^(?:[০-৯0-9]+[.)\\-]|[*•\\-])\\s*"), "").trim()

        val dariIndex = cleaned.indexOf('।')
        val periodIndex = cleaned.indexOf('.')
        val cutIndex = when {
            dariIndex != -1 && periodIndex != -1 -> minOf(dariIndex, periodIndex)
            dariIndex != -1 -> dariIndex
            periodIndex != -1 -> periodIndex
            else -> -1
        }

        if (cutIndex != -1 && cutIndex < cleaned.length - 1) {
            cleaned = cleaned.substring(0, cutIndex + 1).trim()
        }

        // Limit to reasonable word count for single-line display
        val words = cleaned.split(Regex("\\s+"))
        if (words.size > 25) {
            cleaned = words.take(25).joinToString(" ") + "।"
        }

        return cleaned
    }

    private fun cleanTopicString(rawTopic: String): String {
        var cleaned = rawTopic.trim()
            .replace("```json", "")
            .replace("```", "")
            .replace("{", "")
            .replace("}", "")
            .trim()
            .trim('"')

        if (cleaned.contains("\"topic\":")) {
            val idx = cleaned.indexOf("\"topic\":")
            cleaned = cleaned.substring(idx + 8).trim().trim('"')
        }

        val prefixBn = "আপনি যে টাইপের গুজবের সম্মুখীন হন বেশি:"
        if (!cleaned.startsWith("আপনি যে")) {
            cleaned = "$prefixBn **$cleaned**"
        }
        return cleaned
    }

    private fun parseAnalyticsJson(rawJsonText: String): AnalyticsResult {
        // Find JSON block boundary
        val firstBrace = rawJsonText.indexOf('{')
        val lastBrace = rawJsonText.lastIndexOf('}')
        val cleanJson = if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            rawJsonText.substring(firstBrace, lastBrace + 1)
        } else {
            rawJsonText.replace("```json", "").replace("```", "").trim()
        }

        val rootObj = JSONObject(cleanJson)

        val rawTopic = rootObj.optString("topic", "").takeIf { it.isNotBlank() }
            ?: "তথ্য অনুসন্ধান ও যাচাই"
        val topic = cleanTopicString(rawTopic)

        val riskLevel = rootObj.optString("riskLevel", "Medium").let {
            when (it.lowercase().trim()) {
                "high", "উচ্চ" -> "High"
                "low", "কম" -> "Low"
                else -> "Medium"
            }
        }

        val insights = rootObj.optString("insights", "").takeIf { it.isNotBlank() }

        // Advice might be returned as String, JSONObject, or JSONArray
        val rawAdvice: String = when (val adviceObj = rootObj.opt("advice")) {
            is String -> adviceObj
            is JSONObject -> {
                // If it's a map like {"1": "text", "2": "text"}
                val firstKey = adviceObj.keys().asSequence().firstOrNull()
                if (firstKey != null) adviceObj.optString(firstKey) else ""
            }
            is JSONArray -> {
                if (adviceObj.length() > 0) adviceObj.optString(0) else ""
            }
            else -> ""
        }

        val advice = formatToOneLineAdvice(
            rawAdvice.ifBlank {
                "চাঞ্চল্যকর কোনো তথ্য বা খবর সোশ্যাল মিডিয়ায় দেখলে বিশ্বাস করার আগে নির্ভরযোগ্য সংবাদমাধ্যম থেকে যাচাই করে নিন।"
            }
        )

        return AnalyticsResult(
            topic = topic,
            riskLevel = riskLevel,
            insights = insights,
            advice = advice
        )
    }

    /**
     * Algorithmic heuristic analysis fallback when all AI LLM endpoints are unreachable/offline.
     * Guarantees that the user always gets a high-quality, customized habit analysis.
     */
    private fun generateHeuristicAnalysis(queries: List<String>): AnalyticsResult {
        val politicsKeywords = listOf("শেখ হাসিনা", "তারেক", "বিএনপি", "আওয়ামী", "নির্বাচন", "ভোট", "সরকার", "উপদেষ্টা", "পুলিশ", "সেনা", "রাজনীতি", "মামলা", "গ্রেফতার")
        val healthKeywords = listOf("ক্যান্সার", "টিকা", "ওষুধ", "করোনা", "ডেঙ্গু", "ডায়াবেটিস", "খাদ্য", "বিষাক্ত", "পানি", "স্বাস্থ্য", "চিকিৎসা", "হাসপাতাল")
        val intlKeywords = listOf("ভারত", "পাকিস্তান", "যুক্তরাষ্ট্র", "আমেরিকা", "ইসরায়েল", "ফিলিস্তিন", "ইরান", "ট্রাম্প", "যুদ্ধ", "চীন")
        val viralKeywords = listOf("অলৌকিক", "গুজব", "ভিডিও", "ফেসবুক", "সত্য", "মিথ্যা", "ভাইরাল", "মৃত্যু", "মারা গেছেন")

        var politicsCount = 0
        var healthCount = 0
        var intlCount = 0
        var viralCount = 0

        val joined = queries.joinToString(" ").lowercase()
        for (kw in politicsKeywords) if (joined.contains(kw.lowercase())) politicsCount++
        for (kw in healthKeywords) if (joined.contains(kw.lowercase())) healthCount++
        for (kw in intlKeywords) if (joined.contains(kw.lowercase())) intlCount++
        for (kw in viralKeywords) if (joined.contains(kw.lowercase())) viralCount++

        val maxCount = maxOf(politicsCount, healthCount, intlCount, viralCount)
        return when {
            maxCount == 0 -> AnalyticsResult(
                topic = "আপনি যে টাইপের গুজবের সম্মুখীন হন বেশি: **চলমান সামাজিক ও ভাইরাল তথ্য**",
                riskLevel = "Medium",
                insights = "আপনার অনুসন্ধানের তথ্য নির্দেশ করে যে আপনি সামাজিক যোগাযোগ মাধ্যমে ছড়িয়ে পড়া সাম্প্রতিক আলোচিত দাবিগুলোর সত্যতা যাচাই করতে সচেষ্ট।",
                advice = "চাঞ্চল্যকর যেকোনো তথ্য বা ভিডিও বিশ্বাস করার পূর্বে অনুমোদিত ফ্যাক্টচেক সাইট বা মূলধারার গণমাধ্যম থেকে যাচাই করে নিন।"
            )
            politicsCount >= maxCount -> AnalyticsResult(
                topic = "আপনি যে টাইপের গুজবের সম্মুখীন হন বেশি: **রাজনীতি, নির্বাচন ও শাসনব্যবস্থা**",
                riskLevel = "High",
                insights = "আপনার সার্চ হিস্ট্রির অধিকাংশ অনুসন্ধান **রাজনৈতিক ব্যক্তিত্ব, দলের বিবৃতি ও নির্বাচন সংক্রান্ত** ভাইরাল দাবিসমূহ যাচাইয়ের সাথে সংশ্লিষ্ট।",
                advice = "রাজনৈতিক স্পর্শকাতর খবরের ক্ষেত্রে নির্বাচন কমিশন ও সরকারি প্রেস রিলিজের মতো সরাসরি দাপ্তরিক উৎস মিলিয়ে নিন।"
            )
            healthCount >= maxCount -> AnalyticsResult(
                topic = "আপনি যে টাইপের গুজবের সম্মুখীন হন বেশি: **স্বাস্থ্য, চিকিৎসা ও পথ্য**",
                riskLevel = "High",
                insights = "আপনার অনুসন্ধানগুলোর উল্লেখযোগ্য অংশ **চিকিৎসা বিজ্ঞান ও স্বাস্থ্য সচেতনতা সম্পর্কিত** ভুল ধারণা ও প্রতিকারের তথ্যের ওপর কেন্দ্রীভূত।",
                advice = "সোশ্যাল মিডিয়ায় প্রচারিত কোনো ঘরোয়া প্রতিকার গ্রহণের পূর্বে সর্বদা নিবন্ধিত চিকিৎসকের পরামর্শ নিন।"
            )
            intlCount >= maxCount -> AnalyticsResult(
                topic = "আপনি যে টাইপের গুজবের সম্মুখীন হন বেশি: **আন্তর্জাতিক ঘটনা ও ভূরাজনীতি**",
                riskLevel = "Medium",
                insights = "আপনি বৈশ্বিক কূটনীতি, আন্তর্জাতিক সংঘাত এবং পার্শ্ববর্তী দেশগুলোর সাথে সম্পর্কিত প্রচারিত খবরগুলো বেশি যাচাই করেন।",
                advice = "আন্তর্জাতিক সংবাদের সত্যতা নিশ্চিতে আন্তর্জাতিক খ্যাতিসম্পন্ন বার্তা সংস্থার প্রতিবেদন পর্যবেক্ষণ করুন।"
            )
            else -> AnalyticsResult(
                topic = "আপনি যে টাইপের গুজবের সম্মুখীন হন বেশি: **সামাজিক মাধ্যম ও ভাইরাল মিথ**",
                riskLevel = "Medium",
                insights = "আপনার সার্চ প্যাটার্ন দেখায় যে আপনি ফেসবুক ও সোশ্যাল মিডিয়ায় চাঞ্চল্য সৃষ্টিকারী ভুল তথ্যের মুখোমুখি হন বেশি।",
                advice = "কোনো ছবি বা সংবাদের সত্যতা যাচাই করতে রিভার্স ইমেজ সার্চ ও অফিশিয়াল বিবৃতি পর্যবেক্ষণ করুন।"
            )
        }
    }

    suspend fun analyzeSearchHabits(queries: List<String>): Result<AnalyticsResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (queries.isEmpty()) {
                return@runCatching AnalyticsResult(
                    topic = "পর্যাপ্ত ডেটা নেই (Not enough data)",
                    riskLevel = "Low",
                    insights = "বিশ্লেষণ করার জন্য আপনার অনুসন্ধান তালিকায় পর্যাপ্ত সার্চ নেই।",
                    advice = "নতুন কোনো দাবি বা সংবাদ যাচাই করতে সার্চ করুন।"
                )
            }

            val prompt = """
                You are an expert data analyst for a fact-checking platform in Bangladesh. 
                Given the following list of recent fact-check search queries performed by a specific user, provide a thoughtful analysis of their search habits.
                
                Return a JSON object with EXACTLY the following four fields:
                1. "topic": A string starting with "আপনি যে টাইপের গুজবের সম্মুখীন হন বেশি: " followed by the main categories in Bengali with markdown bold (e.g., "**রাজনীতি, সামাজিক বিষয় ও স্বাস্থ্য**").
                2. "riskLevel": A single word indicating vulnerability to misinformation: "Low", "Medium", or "High".
                3. "insights": A brief Bengali summary (1 to 2 sentences) about what their search history indicates. Use markdown bold for key themes.
                4. "advice": A STRICTLY SINGLE-LINE concise actionable Bengali advice/tip for digital literacy (maximum 15 to 20 words).
                   IMPORTANT CONSTRAINTS FOR ADVICE:
                   - It MUST be a single plain string (1 short sentence).
                   - Do NOT return an object, map, or list.
                   - Do NOT include numbered points or multiple items.
                   - Do NOT include lengthy paragraphs or examples.
                   - Example: "সোশ্যাল মিডিয়ার যেকোনো চাঞ্চল্যকর খবর শেয়ারের আগে বিশ্বস্ত সংবাদ মাধ্যম থেকে যাচাই করে নিন।"
                
                User's recent queries:
                ${queries.take(50).joinToString(", ") { "\"$it\"" }}
            """.trimIndent()

            val client = NetworkClientProvider.ocrAndLlmClient

            // 1. Try Mistral (ministral-8b-latest)
            val mistralKey = getCleanKey(BuildConfig.MISTRAL_API_KEY)
            if (mistralKey.isNotBlank() && !mistralKey.startsWith("MY_")) {
                try {
                    val payload = JSONObject().apply {
                        put("model", "ministral-8b-latest")
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            })
                        })
                        put("response_format", JSONObject().apply { put("type", "json_object") })
                        put("temperature", 0.2)
                    }

                    val request = Request.Builder()
                        .url("https://api.mistral.ai/v1/chat/completions")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer $mistralKey")
                        .post(payload.toString().toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            val root = JSONObject(bodyStr)
                            val choices = root.optJSONArray("choices")
                            val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                            if (!content.isNullOrBlank()) {
                                Log.d(TAG, "Successfully analyzed habits using Mistral")
                                return@runCatching parseAnalyticsJson(content)
                            }
                        } else {
                            Log.w(TAG, "Mistral returned HTTP ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Mistral habit analysis failed: ${e.message}")
                }
            }

            // 2. Try Groq (llama-3.3-70b-versatile) fallback
            val groqKey = getCleanKey(BuildConfig.GROQ_API_KEY)
            if (groqKey.isNotBlank() && !groqKey.startsWith("MY_")) {
                try {
                    val payload = JSONObject().apply {
                        put("model", "llama-3.3-70b-versatile")
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            })
                        })
                        put("response_format", JSONObject().apply { put("type", "json_object") })
                        put("temperature", 0.2)
                    }

                    val request = Request.Builder()
                        .url("https://api.groq.com/openai/v1/chat/completions")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer $groqKey")
                        .post(payload.toString().toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            val root = JSONObject(bodyStr)
                            val choices = root.optJSONArray("choices")
                            val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                            if (!content.isNullOrBlank()) {
                                Log.d(TAG, "Successfully analyzed habits using Groq fallback")
                                return@runCatching parseAnalyticsJson(content)
                            }
                        } else {
                            Log.w(TAG, "Groq returned HTTP ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Groq habit analysis failed: ${e.message}")
                }
            }

            // 3. Try Gemini fallback
            val geminiKey = getCleanKey(BuildConfig.GEMINI_API_KEY)
            if (geminiKey.isNotBlank() && !geminiKey.startsWith("MY_")) {
                try {
                    val payload = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", "$prompt\n\nReturn strict JSON.")
                                    })
                                })
                            })
                        })
                        put("generationConfig", JSONObject().apply {
                            put("response_mime_type", "application/json")
                            put("temperature", 0.2)
                        })
                    }

                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            val root = JSONObject(bodyStr)
                            val candidates = root.optJSONArray("candidates")
                            val text = candidates?.optJSONObject(0)
                                ?.optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.optJSONObject(0)
                                ?.optString("text")
                            if (!text.isNullOrBlank()) {
                                Log.d(TAG, "Successfully analyzed habits using Gemini fallback")
                                return@runCatching parseAnalyticsJson(text)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Gemini habit analysis failed: ${e.message}")
                }
            }

            // 4. Deterministic algorithmic heuristic fallback
            Log.d(TAG, "Falling back to intelligent heuristic habit analysis")
            generateHeuristicAnalysis(queries)
        }
    }

    suspend fun transliterateName(name: String, targetLanguage: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (name.isBlank()) return@runCatching name

            val prompt = """
                You are transliterating a person's name.
                This is NOT translation.
                Preserve pronunciation.
                Do not replace the name with a different name.
                Do not add honorifics.
                Do not abbreviate.
                Return exactly one transliterated name in target language: $targetLanguage.
                Return no explanation.
                Return no punctuation.
                If the name is already written in the requested script, return it unchanged.

                Examples:
                Sagar -> সাগর
                Akif -> আকিফ
                মাহাথির -> Mahatir

                Input Name: "$name"
                Output ONLY the name.
            """.trimIndent()

            val client = NetworkClientProvider.ocrAndLlmClient
            val mistralKey = getCleanKey(BuildConfig.MISTRAL_API_KEY)

            if (mistralKey.isNotBlank()) {
                try {
                    val payload = JSONObject().apply {
                        put("model", "ministral-8b-latest")
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            })
                        })
                        put("temperature", 0.1)
                    }

                    val request = Request.Builder()
                        .url("https://api.mistral.ai/v1/chat/completions")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer $mistralKey")
                        .post(payload.toString().toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            val root = JSONObject(bodyStr)
                            val content = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                            if (!content.isNullOrBlank()) {
                                return@runCatching content.replace("```", "").trim().trim('"')
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Fallback: return unchanged
            name
        }
    }
}
