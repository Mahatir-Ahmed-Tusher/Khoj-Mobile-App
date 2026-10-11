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
private data class MistralRequest(
    val model: String,
    val messages: List<MistralMessage>
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
    private val API_KEY = BuildConfig.MISTRAL_API_KEY.removeSurrounding("\"")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyzeSearchHabits(queries: List<String>): Result<AnalyticsResult> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            if (queries.isEmpty()) {
                return@runCatching AnalyticsResult(topic = "पर्याप्त ডেটা নেই (Not enough data)")
            }

            val prompt = """
                You are an expert data analyst for a fact-checking platform in Bangladesh. 
                Given the following list of recent fact-check search queries performed by a specific user, provide a thoughtful analysis of their search habits.
                
                Return a JSON object with the following fields:
                1. "topic": A string starting with "আপনি যে টাইপের গুজবের সম্মুখীন হোন বেশি: " followed by the main category (e.g., "রাজনীতি", "স্বাস্থ্য", "প্রযুক্তি").
                2. "riskLevel": A string indicating vulnerability to misinformation: "Low", "Medium", or "High".
                3. "insights": A brief Bengali sentence about what their searches suggest (e.g., "আপনি মূলত সাম্প্রতিক সামাজিক পরিবর্তনের খবরগুলো যাচাই করতে পছন্দ করেন।").
                4. "advice": A brief Bengali tip for better digital literacy based on their habits.
                
                User's recent queries:
                ${queries.take(50).joinToString(", ") { "\"$it\"" }}
            """.trimIndent()

            val requestPayload = MistralRequest(
                model = "mistral-small-latest",
                messages = listOf(
                    MistralMessage(role = "user", content = prompt)
                )
            )

            val httpResponse = KhojSupabaseClient.client.httpClient.post("https://api.mistral.ai/v1/chat/completions") {
                header("Authorization", "Bearer $API_KEY")
                contentType(ContentType.Application.Json)
                setBody(requestPayload)
            }

            val responseBody = httpResponse.bodyAsText()
            val decoded = json.decodeFromString<MistralResponse>(responseBody)
            val contentText = decoded.choices.firstOrNull()?.message?.content?.trim() ?: throw Exception("Empty response from Mistral")

            val cleanJson = contentText.replace("```json", "").replace("```", "").trim()
            try {
                json.decodeFromString<AnalyticsResult>(cleanJson)
            } catch (e: Exception) {
                AnalyticsResult(topic = contentText) // Fallback if AI fails to return proper JSON
            }
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
                model = "mistral-small-latest",
                messages = listOf(
                    MistralMessage(role = "user", content = prompt)
                )
            )

            val contentText = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                val httpResponse = KhojSupabaseClient.client.httpClient.post("https://api.mistral.ai/v1/chat/completions") {
                    header("Authorization", "Bearer $API_KEY")
                    contentType(ContentType.Application.Json)
                    setBody(requestPayload)
                }
                val responseBody = httpResponse.bodyAsText()
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
