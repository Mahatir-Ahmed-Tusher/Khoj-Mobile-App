package com.example.data.api

import com.example.BuildConfig
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class AnalyticsResult(
    val topic: String,
    val riskLevel: String? = null,
    val insights: String? = null,
    val advice: String? = null
)

@Serializable
private data class MistralMessage(
    val role: String,
    val content: String
)

@Serializable
private data class MistralResponseFormat(
    val type: String = "json_object"
)

@Serializable
private data class MistralRequest(
    val model: String,
    val messages: List<MistralMessage>,
    val response_format: MistralResponseFormat? = null,
    val temperature: Double? = null
)

@Serializable
private data class MistralChoice(
    val index: Int,
    val message: MistralMessage,
    val finish_reason: String? = null
)

@Serializable
private data class MistralResponse(
    val id: String = "",
    val `object`: String = "",
    val created: Long = 0,
    val model: String = "",
    val choices: List<MistralChoice> = emptyList()
)

object AnalyticsEngine {
    private val json = Json { ignoreUnknownKeys = true }

    private fun getApiKey(): String {
        return BuildConfig.MISTRAL_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    }

    private fun formatToOneLineAdvice(raw: String): String {
        // Strip leading numbering or bullet prefixes like "1. ", "১. ", "- ", "* ", "• "
        var cleaned = raw.trim()
            .replace(Regex("^(?:[০-৯0-9]+[.)\\-]|[*•\\-])\\s*"), "")
            .trim()

        // If there are multiple lines, pick the first non-empty line
        val firstLine = cleaned.lines().firstOrNull { it.isNotBlank() }?.trim() ?: cleaned
        cleaned = firstLine.replace(Regex("^(?:[০-৯0-9]+[.)\\-]|[*•\\-])\\s*"), "").trim()

        // Extract first complete sentence if there are multiple sentences
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

        return cleaned
    }

    suspend fun analyzeSearchHabits(queries: List<String>): Result<AnalyticsResult> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            if (queries.isEmpty()) {
                return@runCatching AnalyticsResult(topic = "পর্যাপ্ত ডেটা নেই (Not enough data)")
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

            val requestPayload = MistralRequest(
                model = "ministral-8b-latest",
                messages = listOf(
                    MistralMessage(role = "user", content = prompt)
                ),
                response_format = MistralResponseFormat(type = "json_object"),
                temperature = 0.2
            )

            val apiKey = getApiKey()
            val httpResponse = KhojSupabaseClient.client.httpClient.post("https://api.mistral.ai/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(requestPayload)
            }

            val responseBody = httpResponse.bodyAsText()
            if (httpResponse.status.value !in 200..299) {
                throw Exception("Mistral HTTP ${httpResponse.status.value}: $responseBody")
            }

            val decoded = json.decodeFromString<MistralResponse>(responseBody)
            val contentText = decoded.choices.firstOrNull()?.message?.content?.trim()
                ?: throw Exception("Empty response from Mistral")

            // Extract valid JSON substring between first '{' and last '}'
            val firstBrace = contentText.indexOf('{')
            val lastBrace = contentText.lastIndexOf('}')
            val cleanJson = if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                contentText.substring(firstBrace, lastBrace + 1)
            } else {
                contentText.replace("```json", "").replace("```", "").trim()
            }

            val rootObj = json.parseToJsonElement(cleanJson).jsonObject

            val topicStr = rootObj["topic"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: "আপনি যে টাইপের গুজবের সম্মুখীন হন বেশি: **তথ্য অনুসন্ধান ও যাচাই**"

            val riskStr = rootObj["riskLevel"]?.jsonPrimitive?.contentOrNull?.trim() ?: "Medium"

            val insightsStr = rootObj["insights"]?.let { elem ->
                when (elem) {
                    is JsonPrimitive -> elem.contentOrNull
                    is JsonArray -> elem.joinToString(" ") { it.jsonPrimitive.contentOrNull.orEmpty() }
                    else -> elem.toString()
                }
            }?.trim()

            val rawAdviceStr = rootObj["advice"]?.let { elem ->
                when (elem) {
                    is JsonPrimitive -> elem.contentOrNull
                    is JsonObject -> {
                        elem.values.firstOrNull()?.let { firstVal ->
                            if (firstVal is JsonPrimitive) firstVal.contentOrNull else firstVal.toString()
                        }
                    }
                    is JsonArray -> {
                        elem.firstOrNull()?.let { firstItem ->
                            if (firstItem is JsonPrimitive) firstItem.contentOrNull else firstItem.toString()
                        }
                    }
                    else -> elem.toString()
                }
            }?.trim()

            val singleLineAdvice = rawAdviceStr?.let { formatToOneLineAdvice(it) }

            AnalyticsResult(
                topic = topicStr,
                riskLevel = riskStr,
                insights = insightsStr,
                advice = singleLineAdvice
            )
        }
    }

    suspend fun transliterateName(name: String, targetLanguage: String): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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

            val requestPayload = MistralRequest(
                model = "ministral-8b-latest",
                messages = listOf(
                    MistralMessage(role = "user", content = prompt)
                ),
                temperature = 0.1
            )

            val apiKey = getApiKey()
            val contentText = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                val httpResponse = KhojSupabaseClient.client.httpClient.post("https://api.mistral.ai/v1/chat/completions") {
                    header("Authorization", "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(requestPayload)
                }
                val responseBody = httpResponse.bodyAsText()
                if (httpResponse.status.value !in 200..299) {
                    throw Exception("Mistral HTTP ${httpResponse.status.value}: $responseBody")
                }
                val decoded = json.decodeFromString<MistralResponse>(responseBody)
                decoded.choices.firstOrNull()?.message?.content?.trim()
            } ?: throw Exception("Mistral request timed out or returned empty")

            val cleanName = contentText
                .replace("```", "")
                .trim()
                .trim('"')
                .takeIf { it.isNotBlank() } ?: name

            cleanName
        }
    }
}
