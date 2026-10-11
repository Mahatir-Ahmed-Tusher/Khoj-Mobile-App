package com.example.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import org.json.JSONArray
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.FactCheckProgress
import com.example.data.model.FactCheckReport
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.delay
import com.example.ui.components.MarkdownText
import com.example.ui.theme.*

/**
 * Persistent bottom drawer that slides up from the bottom of the screen.
 *
 * Shows two states:
 *  1. **Progress** — animated pipeline step label + live streaming markdown.
 *  2. **Report** — completed fact-check verdict badge + full markdown report.
 *
 * If dismissed (drag down or tap ×), the drawer closes but [OverlayStateRepository]
 * retains the session. Reopening the FAB and triggering any action will restore the
 * drawer at the same point (in-progress or completed).
 *
 * The input step is no longer in this drawer — it was moved to the action-specific dialogs.
 */
@Composable
fun OverlayResultDrawer(
    factCheckState: FactCheckProgress,
    streamingMarkdown: String,
    pendingClaimText: String?,
    sessionSource: String?,
    isListening: Boolean,
    onVerify: (String) -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    fontToUse: FontFamily = FontFamily.Default
) {
    val drawerShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Dimmed scrim — tap anywhere above the drawer to dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        )

        // ── Drawer card ──────────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .align(Alignment.BottomCenter)
                .clip(drawerShape),
            shape = drawerShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Drag handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = SlatePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Khoj Fact Check",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = fontToUse
                        )
                        // Source badge
                        val sourceLabel = when (sessionSource) {
                            "clipboard" -> "Clipboard"
                            "screenshot" -> "Screenshot"
                            "voice" -> "Voice"
                            "text" -> "Selected Text"
                            else -> null
                        }
                        if (sourceLabel != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SlatePrimary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = sourceLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SlatePrimary,
                                    fontFamily = fontToUse,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Body — switches between progress and report
                AnimatedContent(
                    targetState = factCheckState,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "drawer_body",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) { state ->
                    when (state) {
                        is FactCheckProgress.Idle -> DrawerIdleState(fontToUse = fontToUse)

                        is FactCheckProgress.ClaimNormalization,
                        is FactCheckProgress.QueryPlanning,
                        is FactCheckProgress.Searching,
                        is FactCheckProgress.RankingSources,
                        is FactCheckProgress.GeneratingVerdict -> {
                            DrawerProgressState(
                                state = state,
                                streamingMarkdown = streamingMarkdown,
                                fontToUse = fontToUse
                            )
                        }

                        is FactCheckProgress.Success -> {
                            DrawerReportState(
                                report = state.report,
                                onNewCheck = onReset,
                                fontToUse = fontToUse
                            )
                        }

                        is FactCheckProgress.Error -> {
                            DrawerErrorState(
                                message = state.message,
                                onReset = onReset,
                                fontToUse = fontToUse
                            )
                        }

                        else -> DrawerIdleState(fontToUse = fontToUse)
                    }
                }
            }
        }
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun DrawerIdleState(fontToUse: FontFamily = FontFamily.Default) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Tap the FAB to fact-check content",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                fontFamily = fontToUse
            )
        }
    }
}

@Composable
private fun DrawerProgressState(
    state: FactCheckProgress,
    streamingMarkdown: String,
    fontToUse: FontFamily = FontFamily.Default
) {
    val stepLabel = when (state) {
        is FactCheckProgress.ClaimNormalization -> "Normalizing claim…"
        is FactCheckProgress.QueryPlanning -> "Planning search queries…"
        is FactCheckProgress.Searching -> "Searching from khoj's curated list and from entire web…"
        is FactCheckProgress.RankingSources -> "Ranking ${state.count} sources…"
        is FactCheckProgress.GeneratingVerdict -> "Generating verdict and writing the report..."
        else -> "Processing…"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "progress")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SlatePrimary.copy(alpha = 0.08f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = SlatePrimary.copy(alpha = dotAlpha)
            )
            Text(
                text = stepLabel,
                color = SlatePrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                fontFamily = fontToUse
            )
        }

        if (state is FactCheckProgress.RankingSources && state.domains.isNotEmpty()) {
            AnimatedFavicons(domains = state.domains)
        }

        if (streamingMarkdown.isNotBlank()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                MarkdownText(
                    text = streamingMarkdown,
                    fontToUse = fontToUse,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AnimatedFavicons(domains: List<String>) {
    var shuffledDomains by remember(domains) { mutableStateOf(domains) }
    
    LaunchedEffect(domains) {
        while(true) {
            delay(1200)
            shuffledDomains = shuffledDomains.shuffled()
        }
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    ) {
        items(shuffledDomains, key = { it }) { domain ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://www.google.com/s2/favicons?domain=${domain}&sz=64")
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .animateItem()
            )
        }
    }
}
@Composable
private fun DrawerReportState(
    report: FactCheckReport,
    onNewCheck: () -> Unit,
    fontToUse: FontFamily = FontFamily.Default
) {
    var currentTab by remember { mutableStateOf("report") }

    val verdictColor = when (report.verdict?.lowercase()) {
        "true" -> Color(0xFF16A34A)
        "false" -> Color(0xFFDC2626)
        else -> Color(0xFFD97706)
    }
    val verdictLabel = when (report.verdict?.lowercase()) {
        "true" -> "✓ সত্য (TRUE)"
        "false" -> "✗ অসত্য (FALSE)"
        else -> "⚠ বিভ্রান্তিকর (MISLEADING)"
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = verdictLabel,
                    color = verdictColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    fontFamily = fontToUse
                )
                Text(
                    text = "${report.confidence}% confidence",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = fontToUse
                )
            }

            Text(
                text = "References ➔",
                color = SlatePrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = fontToUse,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { currentTab = "references" }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        AnimatedContent(
            targetState = currentTab,
            modifier = Modifier.weight(1f),
            label = "tab_content"
        ) { tab ->
            if (tab == "report") {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    val markdown = report.getEffectiveMarkdown()
                    if (markdown.isNotBlank()) {
                        MarkdownText(
                            text = markdown,
                            fontToUse = fontToUse,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val summary = report.summaryEn.ifBlank { report.summaryBn }
                        Text(
                            text = summary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            fontFamily = fontToUse
                        )
                    }
                }
            } else {
                ReferencesTab(
                    sourcesJson = report.sourcesJson,
                    onBack = { currentTab = "report" },
                    fontToUse = fontToUse
                )
            }
        }

        OutlinedButton(
            onClick = onNewCheck,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("New Check", fontWeight = FontWeight.SemiBold, fontFamily = fontToUse)
        }
    }
}

@Composable
private fun ReferencesTab(
    sourcesJson: String,
    onBack: () -> Unit,
    fontToUse: FontFamily = FontFamily.Default
) {
    val uriHandler = LocalUriHandler.current
    val sources = remember(sourcesJson) {
        val list = mutableListOf<Map<String, String>>()
        try {
            val array = JSONArray(sourcesJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    mapOf(
                        "title" to obj.optString("title", ""),
                        "url" to obj.optString("url", ""),
                        "domain" to obj.optString("domain", "")
                    )
                )
            }
        } catch (e: Exception) {
            // ignore parsing errors
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onBack)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp), tint = SlatePrimary)
            Spacer(Modifier.width(8.dp))
            Text("Report", color = SlatePrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = fontToUse)
        }

        Spacer(Modifier.height(4.dp))

        if (sources.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No references found.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = fontToUse)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sources.forEach { source ->
                    val url = source["url"] ?: ""
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (url.isNotBlank()) {
                                    try {
                                        uriHandler.openUri(url)
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source["title"] ?: "Unknown Title",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    fontFamily = fontToUse
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = source["domain"]?.takeIf { it.isNotBlank() } ?: url,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    fontFamily = fontToUse
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerErrorState(
    message: String,
    onReset: () -> Unit,
    fontToUse: FontFamily = FontFamily.Default
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = ColorFalse,
            modifier = Modifier.size(44.dp)
        )
        Text(text = "Verification Failed", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = fontToUse)
        Text(
            text = message,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontFamily = fontToUse
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Try Again", fontFamily = fontToUse)
        }
    }
}
