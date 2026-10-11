package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class Citation(
    val id: Int = 0,
    val title: String = "",
    val domain: String = "",
    val url: String = "",
    val snippet: String = "",
    val favicon: String? = null,
    val credibilityScore: Double = 0.0,
    val provider: String = "",
    val content: String? = null
)

data class FactCheckSource(
    val title: String,
    val url: String,
    val snippet: String = "",
    val domain: String = "",
    val credibilityTier: Int = 3,
    val publishDate: String = ""
)

data class ContradictingEvidence(
    val note: String,
    val url: String = "",
    val title: String = ""
)

@Entity(tableName = "fact_checks")
data class FactCheckReport(
    @PrimaryKey val id: String, // Typically UUID or generated
    val claimText: String,
    val normalizedClaim: String,
    val verdict: String, // "true" (সত্য), "false" (অসত্য), "unverifiable" (অনিশ্চিত), "misleading"
    val confidence: Int,
    val summaryBn: String,
    val summaryEn: String,
    val reasoningBn: String,
    val reasoningEn: String,
    val sourcesJson: String, // Serialized List<FactCheckSource>
    val contradictingSourcesJson: String, // Serialized List<ContradictingEvidence>
    val markdownReport: String? = null, // The single generated Markdown report in the app language
    val timestamp: Long = System.currentTimeMillis(),
    val isPublic: Boolean = true,
    val userId: String? = null
) {
    /**
     * Fallback for legacy reports to render cleanly in UnifiedFactCheckReportView.
     */
    fun getEffectiveMarkdown(): String {
        if (!markdownReport.isNullOrBlank()) {
            return markdownReport
        }
        
        // Synthesize dynamic markdown for legacy reports
        val reportBuilder = StringBuilder()
        reportBuilder.append("### ফ্যাক্ট-চেক রিপোর্ট\n\n")
        
        reportBuilder.append("**দাবি (Claim):**\n")
        reportBuilder.append("${this.claimText}\n\n")
        
        val verdictUpper = this.verdict.uppercase()
        reportBuilder.append("**রায় (Verdict):**\n**$verdictUpper**\n\n")
        
        val summary = this.summaryBn.takeIf { it.isNotBlank() } ?: this.summaryEn
        reportBuilder.append("**সারাংশ (Summary):**\n$summary\n\n")
        
        reportBuilder.append("---\n\n")
        
        reportBuilder.append("## বিস্তারিত ব্যাখ্যা (Detailed Explanation)\n\n")
        
        val reasoning = this.reasoningBn.takeIf { it.isNotBlank() } ?: this.reasoningEn
        reportBuilder.append("$reasoning\n\n")
        
        return reportBuilder.toString()
    }
}
