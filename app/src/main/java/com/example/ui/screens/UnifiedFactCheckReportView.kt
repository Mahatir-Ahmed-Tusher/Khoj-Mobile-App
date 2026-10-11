package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.data.model.FactCheckReport
import com.example.data.api.FactCheckProgress
import com.example.data.model.FactCheckSource
import com.example.ui.components.MarkdownText
import androidx.compose.ui.platform.LocalUriHandler

/**
 * UnifiedFactCheckReportView
 *
 * The single source of truth for rendering a fact-check report body across all surfaces:
 * - FactCheckResultScreen (live result after a new check)
 * - HistoryDetailScreen   (report opened from history)
 * - FeedDetailScreen      (report opened from the feed)
 *
 * It renders via `report.getEffectiveMarkdown()`, which:
 *   - For new reports: renders the raw `markdownReport` produced by the new LLM pipeline.
 *   - For legacy reports: synthesizes a fallback markdown from summaryBn/reasoningBn fields.
 *
 * This guarantees 1:1 visual fidelity between the live result and any archived view.
 */
@Composable
fun UnifiedFactCheckReportView(
    report: FactCheckReport?,
    streamingText: String? = null,
    liveState: FactCheckProgress? = null,
    fontToUse: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    sources: List<FactCheckSource> = emptyList(),
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val onCitationClick: (Int) -> Unit = { idx ->
        // Citations are 1-indexed; open the URL for the matching source
        val source = sources.getOrNull(idx - 1)
        val url = source?.url ?: ""
        if (url.startsWith("http")) uriHandler.openUri(url)
    }
    AnimatedContent(
        targetState = report,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier.fillMaxWidth(),
        label = "report_content_transition"
    ) { targetReport ->
        if (targetReport != null) {
            // ── Completed Report ─────────────────────────────────────────────
            MarkdownText(
                text = targetReport.getEffectiveMarkdown(),
                fontToUse = fontToUse,
                textColor = MaterialTheme.colorScheme.onSurface,
                fontSize = 15,
                lineHeight = 24,
                onCitationClick = onCitationClick,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (!streamingText.isNullOrBlank()) {
            // ── Streaming In-Progress ─────────────────────────────────────────
            MarkdownText(
                text = streamingText,
                fontToUse = fontToUse,
                textColor = MaterialTheme.colorScheme.onSurface,
                fontSize = 15,
                lineHeight = 24,
                onCitationClick = onCitationClick,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (liveState != null) {
            // ── Pipeline Status Placeholder ───────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                val statusText = when (liveState) {
                    is FactCheckProgress.ClaimNormalization -> "দাবিটি প্রক্রিয়াকরণ করা হচ্ছে..."
                    is FactCheckProgress.QueryPlanning     -> "অনুসন্ধানের কৌশল তৈরি করা হচ্ছে..."
                    is FactCheckProgress.Searching         -> "তথ্য খোঁজা হচ্ছে (${liveState.source})..."
                    is FactCheckProgress.RankingSources    -> "${liveState.count}টি উৎস যাচাই করা হচ্ছে..."
                    is FactCheckProgress.GeneratingVerdict -> "রিপোর্ট তৈরি হচ্ছে — অপেক্ষা করুন..."
                    is FactCheckProgress.Error             -> "⚠️ ${liveState.message}"
                    else                                   -> "অপেক্ষা করুন..."
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
