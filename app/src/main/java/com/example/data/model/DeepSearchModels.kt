package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class DeepSearchSource(
    val citationId: Int = 0,
    val title: String = "",
    val domain: String = "",
    val url: String = "",
    val snippet: String = ""
)

@Serializable
data class DeepSearchReport(
    val id: String = java.util.UUID.randomUUID().toString(),
    val query: String = "",
    val reportContent: String = "",
    val sources: List<DeepSearchSource> = emptyList(),
    val detectedIntent: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isPublic: Boolean = true,
    val userId: String? = null,
    val isFeedPreview: Boolean = false
)

/**
 * Remote DTO matching Supabase `deep_search_reports` table schema directly.
 */
@Serializable
data class RemoteDeepSearchReportDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("query") val query: String = "",
    @SerialName("report_content") val reportContent: String = "",
    @SerialName("sources") val sources: JsonArray? = null,
    @SerialName("detected_intent") val detectedIntent: String? = null,
    @SerialName("visibility") val visibility: String = "public",
    @SerialName("status") val status: String = "completed",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
) {
    fun toDomainModel(): DeepSearchReport {
        val parsedSources = mutableListOf<DeepSearchSource>()
        sources?.forEachIndexed { index, elem ->
            try {
                val obj = elem.jsonObject
                val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                val snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull ?: ""
                val domain = obj["domain"]?.jsonPrimitive?.contentOrNull
                    ?: try { if (url.isNotBlank()) java.net.URI(url).host?.removePrefix("www.") ?: "" else "" } catch (ex: Exception) { "" }
                parsedSources.add(
                    DeepSearchSource(
                        citationId = index + 1,
                        title = if (title.isNotBlank()) title else domain.ifBlank { "Source #${index + 1}" },
                        domain = domain,
                        url = url,
                        snippet = snippet
                    )
                )
            } catch (_: Exception) {}
        }

        val timestampMs = try {
            if (createdAt.isNotBlank()) {
                java.time.Instant.parse(createdAt).toEpochMilli()
            } else {
                System.currentTimeMillis()
            }
        } catch (_: Exception) {
            System.currentTimeMillis()
        }

        return DeepSearchReport(
            id = id,
            query = query,
            reportContent = reportContent,
            sources = parsedSources,
            detectedIntent = detectedIntent,
            timestamp = timestampMs,
            isPublic = visibility.equals("public", ignoreCase = true),
            userId = userId,
            isFeedPreview = false
        )
    }
}

/**
 * Checks whether a Supabase report represents a Deep Search AI research report.
 */
fun SupabaseReport.isDeepSearch(): Boolean {
    val v = verdict.lowercase().trim()
    val m = modelVersion?.lowercase()?.trim() ?: ""
    return v == "deepsearch" || v == "deep_search" || v == "deep search" || v == "research" ||
           m.contains("deepsearch") || m.contains("deep_search") || m.contains("deep-search") ||
           (reportContent != null && reportContent.contains("## ") && (v == "unverified" || v.isEmpty() || v == "completed"))
}

/**
 * Converts a SupabaseReport from Feed / Backend into a canonical DeepSearchReport.
 */
fun SupabaseReport.toDeepSearchReport(isFeedPreview: Boolean = false): DeepSearchReport {
    val parsedSources = mutableListOf<DeepSearchSource>()
    this.sources?.forEachIndexed { index, element ->
        try {
            val obj = element.jsonObject
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
            val snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull ?: ""
            val domain = try {
                if (url.isNotBlank()) java.net.URI(url).host?.removePrefix("www.") ?: "" else ""
            } catch (e: Exception) {
                ""
            }
            parsedSources.add(
                DeepSearchSource(
                    citationId = index + 1,
                    title = if (title.isNotBlank()) title else domain.ifBlank { "Source #${index + 1}" },
                    domain = domain,
                    url = url,
                    snippet = snippet
                )
            )
        } catch (e: Exception) {
            // Ignore malformed JSON entries
        }
    }

    val ts = try {
        if (this.createdAt.isNotEmpty()) {
            val cleanString = this.createdAt.replace("Z", "+0000")
            val formatPattern = if (cleanString.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSSZ" else "yyyy-MM-dd'T'HH:mm:ssZ"
            val sdf = java.text.SimpleDateFormat(formatPattern, java.util.Locale.US)
            sdf.parse(cleanString)?.time ?: System.currentTimeMillis()
        } else {
            System.currentTimeMillis()
        }
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    return DeepSearchReport(
        id = this.id.ifBlank { java.util.UUID.randomUUID().toString() },
        query = this.processedQuery ?: this.originalQuery,
        reportContent = this.reportContent ?: "",
        sources = parsedSources,
        detectedIntent = null,
        timestamp = ts,
        isPublic = this.isPublic ?: true,
        userId = this.userId,
        isFeedPreview = isFeedPreview
    )
}

