package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.api.DeepSearchEngine
import com.example.data.model.DeepSearchReport
import com.example.data.model.DeepSearchSource
import com.example.data.repository.DeepSearchHistoryRepository
import com.example.ui.components.DeepSearchMarkdownRenderer
import com.example.ui.components.OfflineStateCard
import com.example.ui.theme.SlatePrimary
import com.example.utils.NetworkErrorHelper
import com.example.utils.NetworkMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── Data Models ──────────────────────────────────────────────────────────────

data class DeepSearchSourceItem(
    val citationId: Int,
    val title: String,
    val domain: String,
    val url: String,
    val snippet: String = ""
)

class DeepSearchThreadBlock(
    val id: String,
    val query: String,
    initialStatusText: String = "",
    initialIsLoading: Boolean = false,
    initialIsError: Boolean = false,
    initialIsPartialError: Boolean = false,
    initialErrorMessage: String = "",
    initialTotalSourceCount: Int = 0,
    initialDetectedIntent: String? = null,
    initialFullAnswerText: String = "",
    initialDisplayedAnswerText: String = "",
    initialIsPublic: Boolean = true,
    val liveSources: MutableList<DeepSearchSourceItem> = mutableStateListOf(),
    val allSortedSources: MutableList<DeepSearchSourceItem> = mutableStateListOf(),
    val suggestions: MutableList<String> = mutableStateListOf()
) {
    var statusText by mutableStateOf(initialStatusText)
    var isLoading by mutableStateOf(initialIsLoading)
    var isError by mutableStateOf(initialIsError)
    var isPartialError by mutableStateOf(initialIsPartialError)
    var errorMessage by mutableStateOf(initialErrorMessage)
    var totalSourceCount by mutableStateOf(initialTotalSourceCount)
    var detectedIntent by mutableStateOf(initialDetectedIntent)
    var fullAnswerText by mutableStateOf(initialFullAnswerText)
    var displayedAnswerText by mutableStateOf(initialDisplayedAnswerText)
    var isPublic by mutableStateOf(initialIsPublic)
}

// ── Main Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepSearchScreen(
    initialQuery: String = "",
    initialReport: DeepSearchReport? = null,
    isBn: Boolean,
    fontToUse: FontFamily = FontFamily.Default,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val engine = remember(context) { DeepSearchEngine(context) }
    val networkMonitor = remember { NetworkMonitor(context) }
    val historyRepo = remember { DeepSearchHistoryRepository.getInstance(context) }
    
    val responseLanguageManager = remember(context) { com.example.data.preferences.ResponseLanguageManager(context) }
    val responseLanguage by responseLanguageManager.responseLanguage.collectAsState()

    var activePipelineJob by remember { mutableStateOf<Job?>(null) }
    val threadBlocks = remember { mutableStateListOf<DeepSearchThreadBlock>() }
    var followUpInput by remember { mutableStateOf("") }
    var showSourcesSheet by remember { mutableStateOf(false) }
    var sheetSources by remember { mutableStateOf<List<DeepSearchSourceItem>>(emptyList()) }
    var sheetTitle by remember { mutableStateOf("") }
    var referenceSearchQuery by remember { mutableStateOf("") }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showShareModal by remember { mutableStateOf(false) }
    var shareModalBlock by remember { mutableStateOf<DeepSearchThreadBlock?>(null) }

    // Handle system back button to cancel active coroutines safely
    BackHandler {
        activePipelineJob?.cancel()
        onBack()
    }

    // ── Real Pipeline Runner ─────────────────────────────────────────────────
    fun triggerPipeline(block: DeepSearchThreadBlock) {
        activePipelineJob?.cancel()
        activePipelineJob = coroutineScope.launch {
            try {
                // Network check
                val isOnline = networkMonitor.isOnline.first()
                if (!isOnline) {
                    block.isLoading = false
                    block.isError = true
                    block.errorMessage = if (isBn)
                        "কোনো ইন্টারনেট সংযোগ নেই। দয়া করে আপনার নেটওয়ার্ক পরীক্ষা করুন।"
                    else
                        "No Internet Connection. Please check your network."
                    return@launch
                }

                // Stage 0: Intent & Query Planning
                block.statusText = if (isBn) "🧠 অনুসন্ধান পরিকল্পনা তৈরি হচ্ছে…" else "🧠 Planning search queries…"
                block.isLoading = true
                val plan = engine.planSearchIntent(block.query)
                block.detectedIntent = plan.intent

                // Stage 1: Parallel SearXNG Execution (Discovered in Arrival Order)
                block.statusText = if (isBn) "🌐 ওয়েব অনুসন্ধান চলছে…" else "🌐 Searching the web…"
                val rawSources = engine.executeParallelSearches(plan) { foundCount, newSources ->
                    block.statusText = if (isBn) "🌐 $foundCount টি সোর্স খুঁজে পাওয়া গেছে…" else "🌐 Found $foundCount web sources…"
                    newSources.forEach { raw ->
                        val domain = engine.extractDomainPublic(raw.url)
                        val relevance = engine.calculateRelevanceScore(block.query, raw.title, raw.snippet, raw.url)
                        if (relevance >= 0.35) {
                            block.liveSources.add(
                                DeepSearchSourceItem(
                                    citationId = 0,
                                    title = raw.title,
                                    domain = domain,
                                    url = raw.url,
                                    snippet = raw.snippet
                                )
                            )
                        }
                    }
                }

                if (rawSources.isEmpty()) {
                    block.isLoading = false
                    block.isError = true
                    block.errorMessage = if (isBn)
                        "কোনো তথ্য খুঁজে পাওয়া যায়নি। ইন্টারনেট সংযোগ পরীক্ষা করুন।"
                    else
                        "No results found. Please check your internet connection."
                    return@launch
                }

                // Stage 2: Deduplication
                block.statusText = if (isBn) "🔗 তথ্যসূত্র যাচাই ও বিশ্লেষণ করা হচ্ছে…" else "🔗 Verifying & ranking sources…"
                val dedupSources = engine.deduplicateAndNormalize(rawSources)

                // Stage 3: Re-Ranking & Deterministic Citation ID Assignment
                val sortedSources = engine.rerankSources(block.query, dedupSources)
                block.totalSourceCount = sortedSources.size

                if (sortedSources.isEmpty()) {
                    block.isLoading = false
                    block.isError = true
                    block.errorMessage = if (isBn)
                        "যাচাই-বাছাইয়ের পর কোনো তথ্যসূত্র পাওয়া যায়নি। দয়া করে ভিন্নভাবে সার্চ করুন।"
                    else
                        "No sources survived the verification process. Please try a different query."
                    return@launch
                }

                block.allSortedSources.clear()
                sortedSources.forEach { raw ->
                    block.allSortedSources.add(
                        DeepSearchSourceItem(
                            citationId = raw.citationId,
                            title = raw.title,
                            domain = engine.extractDomainPublic(raw.url),
                            url = raw.url,
                            snippet = raw.snippet
                        )
                    )
                }

                // Stage 4: Multi-LLM Report Synthesis (Zero-Latency Instant Generation)
                block.statusText = if (isBn) "✍️ গভীর গবেষণা প্রতিবেদন তৈরি হচ্ছে…" else "✍️ Generating deep research report…"
                val markdownReport = engine.synthesizeReport(
                    query = block.query,
                    plan = plan,
                    allSortedSources = sortedSources,
                    responseLanguage = responseLanguage
                )

                if (markdownReport.isBlank()) {
                    block.isLoading = false
                    block.isError = true
                    block.errorMessage = if (isBn)
                        "গবেষণা প্রতিবেদন তৈরি করা সম্ভব হয়নি। দয়া করে পুনরায় চেষ্টা করুন।"
                    else
                        "Failed to generate research report. Please try again."
                    return@launch
                }

                block.fullAnswerText = markdownReport
                block.isLoading = false

                // Auto-save generated report to DataStore history
                val savedReport = DeepSearchReport(
                    id = block.id,
                    query = block.query,
                    reportContent = markdownReport,
                    sources = block.allSortedSources.map {
                        DeepSearchSource(
                            citationId = it.citationId,
                            title = it.title,
                            domain = it.domain,
                            url = it.url,
                            snippet = it.snippet
                        )
                    },
                    detectedIntent = block.detectedIntent,
                    timestamp = System.currentTimeMillis(),
                    isPublic = block.isPublic,
                    userId = com.example.data.repository.AuthRepository.currentUserId
                )
                coroutineScope.launch {
                    historyRepo.saveReport(savedReport)
                }

                // Stage 5: Progressive token streaming of real response (Fast & Snappy 2s animation)
                val fullText = markdownReport
                val chunkSize = 36
                var currentLen = 0
                while (currentLen < fullText.length) {
                    currentLen = (currentLen + chunkSize).coerceAtMost(fullText.length)
                    block.displayedAnswerText = fullText.substring(0, currentLen)
                    if (threadBlocks.isNotEmpty()) {
                        try {
                            listState.scrollToItem(threadBlocks.lastIndex)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // Ignored during text streaming to allow scrolling
                        }
                    }
                    delay(12)
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                block.isLoading = false
                block.isError = true
                block.errorMessage = if (isBn) "গবেষণা প্রতিবেদন তৈরিতে ত্রুটি হয়েছে।" else "Error generating research report."
            }
        }
    }

    LaunchedEffect(initialReport?.id, initialQuery) {
        if (initialReport != null) {
            // Display initial report immediately
            threadBlocks.clear()
            val preloadedBlock = DeepSearchThreadBlock(
                id = initialReport.id,
                query = initialReport.query,
                initialStatusText = "",
                initialIsLoading = false,
                initialIsError = false,
                initialIsPartialError = false,
                initialErrorMessage = "",
                initialTotalSourceCount = initialReport.sources.size,
                initialDetectedIntent = initialReport.detectedIntent,
                initialFullAnswerText = initialReport.reportContent,
                initialDisplayedAnswerText = initialReport.reportContent,
                initialIsPublic = initialReport.isPublic
            )
            initialReport.sources.forEach { src ->
                preloadedBlock.allSortedSources.add(
                    DeepSearchSourceItem(
                        citationId = src.citationId,
                        title = src.title,
                        domain = src.domain,
                        url = src.url,
                        snippet = src.snippet
                    )
                )
            }
            threadBlocks.add(preloadedBlock)

            // If this report came from a feed preview or needs hydration, fetch full report in background
            if (initialReport.isFeedPreview || initialReport.reportContent.length <= 350) {
                android.util.Log.d("DeepSearchScreen", "[DeepSearch:Hydrate] Hydrating report ${initialReport.id} (isFeedPreview=${initialReport.isFeedPreview})")
                val fullReport = historyRepo.fetchReportById(initialReport.id)
                if (fullReport != null && fullReport.reportContent.isNotBlank()) {
                    android.util.Log.d("DeepSearchScreen", "[DeepSearch:Hydrate] Updated UI with hydrated full report (${fullReport.reportContent.length} chars)")
                    preloadedBlock.fullAnswerText = fullReport.reportContent
                    preloadedBlock.displayedAnswerText = fullReport.reportContent
                    if (fullReport.sources.isNotEmpty() && preloadedBlock.allSortedSources.size < fullReport.sources.size) {
                        preloadedBlock.allSortedSources.clear()
                        fullReport.sources.forEach { src ->
                            preloadedBlock.allSortedSources.add(
                                DeepSearchSourceItem(
                                    citationId = src.citationId,
                                    title = src.title,
                                    domain = src.domain,
                                    url = src.url,
                                    snippet = src.snippet
                                )
                            )
                        }
                        preloadedBlock.totalSourceCount = fullReport.sources.size
                    }
                }
            }
        } else if (initialQuery.isNotBlank() && threadBlocks.isEmpty()) {
            val initialBlock = DeepSearchThreadBlock(
                id = java.util.UUID.randomUUID().toString(),
                query = initialQuery
            )
            threadBlocks.add(initialBlock)
            triggerPipeline(initialBlock)
        }
    }

    // Check if any block is currently loading or streaming text
    val isAnyLoading = threadBlocks.any { it.isLoading || it.displayedAnswerText.length < it.fullAnswerText.length }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Consistent Top App Bar ──────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            activePipelineJob?.cancel()
                            onBack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isBn) "ফিরে যান" else "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBn) "ডিপ রিসার্চ রিপোর্ট" else "Deep Research Report",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = SlatePrimary
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 0.5.dp
            )

            // ── Scrollable Thread Content ───────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                itemsIndexed(threadBlocks, key = { _, block -> block.id }) { _, block ->
                    DeepSearchBlockItem(
                        block = block,
                        isBn = isBn,
                        fontToUse = fontToUse,
                        onSourceClick = { sourceUrl ->
                            val intent = CustomTabsIntent.Builder().build()
                            intent.launchUrl(context, Uri.parse(sourceUrl))
                        },
                        onViewAllSources = { sources, title ->
                            sheetSources = sources
                            sheetTitle = title
                            referenceSearchQuery = ""
                            showSourcesSheet = true
                        },
                        onSuggestionClick = { suggestion ->
                            val newBlock = DeepSearchThreadBlock(
                                id = java.util.UUID.randomUUID().toString(),
                                query = suggestion
                            )
                            threadBlocks.add(newBlock)
                            coroutineScope.launch {
                                delay(100)
                                listState.animateScrollToItem(threadBlocks.lastIndex)
                                triggerPipeline(newBlock)
                            }
                        },
                        onRetryClick = {
                            block.isError = false
                            block.isPartialError = false
                            block.isLoading = true
                            block.liveSources.clear()
                            block.allSortedSources.clear()
                            block.displayedAnswerText = ""
                            block.fullAnswerText = ""
                            triggerPipeline(block)
                        },
                        onVisibilityChange = { newVisibility ->
                            val currentReport = DeepSearchReport(
                                id = block.id,
                                query = block.query,
                                reportContent = block.fullAnswerText.ifBlank { block.displayedAnswerText },
                                sources = block.allSortedSources.map {
                                    DeepSearchSource(
                                        citationId = it.citationId,
                                        title = it.title,
                                        domain = it.domain,
                                        url = it.url,
                                        snippet = it.snippet
                                    )
                                },
                                detectedIntent = block.detectedIntent,
                                timestamp = System.currentTimeMillis(),
                                isPublic = newVisibility,
                                userId = com.example.data.repository.AuthRepository.currentUserId
                            )
                            coroutineScope.launch {
                                historyRepo.saveReport(currentReport)
                            }
                        },
                        onShareClick = {
                            shareModalBlock = block
                            showShareModal = true
                        }
                    )
                }
            }

            // ── Conditional Fixed Bottom Follow-Up Bar (Hidden During Loading/Streaming) ──
            AnimatedVisibility(
                visible = !isAnyLoading && threadBlocks.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = followUpInput,
                            onValueChange = { followUpInput = it },
                            placeholder = {
                                Text(
                                    text = if (isBn) "ফলো-আপ প্রশ্ন লিখুন…" else "Ask a follow-up question…",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SlatePrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                focusedContainerColor = MaterialTheme.colorScheme.background,
                                unfocusedContainerColor = MaterialTheme.colorScheme.background
                            ),
                            singleLine = true
                        )

                        Surface(
                            onClick = {
                                if (followUpInput.isNotBlank()) {
                                    val text = followUpInput.trim()
                                    followUpInput = ""
                                    val newBlock = DeepSearchThreadBlock(
                                        id = java.util.UUID.randomUUID().toString(),
                                        query = text
                                    )
                                    threadBlocks.add(newBlock)
                                    coroutineScope.launch {
                                        delay(100)
                                        listState.animateScrollToItem(threadBlocks.lastIndex)
                                        triggerPipeline(newBlock)
                                    }
                                }
                            },
                            shape = CircleShape,
                            color = SlatePrimary,
                            modifier = Modifier.size(44.dp),
                            enabled = followUpInput.isNotBlank()
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (isBn) "পাঠান" else "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── "View All References" Stunning Bottom Sheet Drawer ──────────────────
        if (showSourcesSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSourcesSheet = false },
                sheetState = bottomSheetState,
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(44.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    )
                }
            ) {
                val filteredSources = sheetSources.filter {
                    referenceSearchQuery.isBlank() ||
                            it.title.contains(referenceSearchQuery, ignoreCase = true) ||
                            it.domain.contains(referenceSearchQuery, ignoreCase = true) ||
                            it.snippet.contains(referenceSearchQuery, ignoreCase = true)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    // Header Bar with Title, Count badge, and Copy All action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = sheetTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                ),
                                color = SlatePrimary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val textToCopy = sheetSources.joinToString("\n\n") {
                                    "[${it.citationId}] ${it.title}\nDomain: ${it.domain}\nURL: ${it.url}"
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("References", textToCopy))
                                Toast.makeText(context, if (isBn) "সকল তথ্যসূত্র কপি করা হয়েছে" else "Copied all references", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy All",
                                    tint = SlatePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = { showSourcesSheet = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = if (isBn) "বন্ধ করুন" else "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Search References Input Filter Field
                    OutlinedTextField(
                        value = referenceSearchQuery,
                        onValueChange = { referenceSearchQuery = it },
                        placeholder = {
                            Text(
                                text = if (isBn) "তথ্যসূত্র অনুসন্ধান করুন…" else "Search references…",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = SlatePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (referenceSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { referenceSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SlatePrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp
                    )

                    // Stunning Source Cards in scrollable column with Number Badges + Original Favicons
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxHeight(0.78f)
                    ) {
                        itemsIndexed(filteredSources) { _, source ->
                            Surface(
                                onClick = {
                                    val intent = CustomTabsIntent.Builder().build()
                                    intent.launchUrl(context, Uri.parse(source.url))
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 3.dp,
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Number Badge [Citation ID]
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                brush = Brush.linearGradient(
                                                    colors = listOf(SlatePrimary, SlatePrimary.copy(alpha = 0.8f))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${source.citationId}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp
                                            ),
                                            color = Color.White
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        // Header Row: Original Domain Favicon + Domain Pill
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data("https://www.google.com/s2/favicons?domain=${source.domain}&sz=64")
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = source.domain,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape),
                                                error = painterResource(id = R.drawable.ic_launcher_foreground)
                                            )
                                            Text(
                                                text = source.domain,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                ),
                                                color = SlatePrimary
                                            )
                                        }

                                        Text(
                                            text = source.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                lineHeight = 19.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (source.snippet.isNotBlank()) {
                                            Text(
                                                text = source.snippet,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open Link",
                                        tint = SlatePrimary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Floating Share Action Button (FAB) ──────────────────────────────────
        val shareableBlock = threadBlocks.lastOrNull { it.displayedAnswerText.isNotBlank() }
        if (!isAnyLoading && shareableBlock != null) {
            FloatingActionButton(
                onClick = {
                    shareModalBlock = shareableBlock
                    showShareModal = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 76.dp),
                containerColor = SlatePrimary,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_share_custom),
                    contentDescription = "Share Report",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // ── Glassmorphic Share Modal Popup ──────────────────────────────────────
        if (showShareModal && shareModalBlock != null) {
            val blockToShare = shareModalBlock!!
            val reportUrl = "https://khoj-ai.bd/factcheck-detail?id=${blockToShare.id}&query=${Uri.encode(blockToShare.query)}"
            val sourcesCount = if (blockToShare.allSortedSources.isNotEmpty()) blockToShare.allSortedSources.size else blockToShare.totalSourceCount
            val shareText = if (isBn) {
                "🔍 খোঁজ এআই ডিপ সার্চ গবেষণা প্রতিবেদন:\n📌 বিষয়: ${blockToShare.query}\n📚 যাচাইকৃত তথ্যসূত্র: ${sourcesCount}টি\n\nসম্পূর্ণ রিপোর্ট পড়ুন:\n$reportUrl"
            } else {
                "🔍 Khoj AI Deep Search Research Report:\n📌 Topic: ${blockToShare.query}\n📚 Verified Sources: ${sourcesCount}\n\nRead full report:\n$reportUrl"
            }

            Dialog(
                onDismissRequest = { showShareModal = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showShareModal = false },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .clickable(enabled = false) {},
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_share_custom),
                                        contentDescription = null,
                                        tint = SlatePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (isBn) "গবেষণা প্রতিবেদন শেয়ার করুন" else "Share Research Report",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = fontToUse,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { showShareModal = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Query preview bubble
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = blockToShare.query,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isBn) "$sourcesCount টি তথ্যসূত্র বিশ্লেষণ করা হয়েছে" else "Analyzed from $sourcesCount sources",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = SlatePrimary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Messenger
                                ShareSocialIconButton(
                                    iconRes = R.drawable.ic_facebook_custom,
                                    onClick = {
                                        shareToApp(context, "com.facebook.orca", shareText, reportUrl)
                                        showShareModal = false
                                    }
                                )
                                // WhatsApp
                                ShareSocialIconButton(
                                    iconRes = R.drawable.ic_whatsapp_custom,
                                    onClick = {
                                        shareToApp(context, "com.whatsapp", shareText, reportUrl)
                                        showShareModal = false
                                    }
                                )
                                // Instagram
                                ShareSocialIconButton(
                                    iconRes = R.drawable.ic_instagram_custom,
                                    onClick = {
                                        shareToApp(context, "com.instagram.android", shareText, reportUrl)
                                        showShareModal = false
                                    }
                                )
                                // X (Twitter)
                                ShareSocialIconButton(
                                    iconRes = R.drawable.ic_x_custom,
                                    onClick = {
                                        shareToApp(context, "com.twitter.android", shareText, reportUrl)
                                        showShareModal = false
                                    }
                                )
                                // LinkedIn
                                ShareSocialIconButton(
                                    iconRes = R.drawable.ic_linkedin_custom,
                                    onClick = {
                                        shareToApp(context, "com.linkedin.android", shareText, reportUrl)
                                        showShareModal = false
                                    }
                                )
                                // Copy Link
                                ShareSocialIconButton(
                                    iconRes = R.drawable.ic_copy_custom,
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Deep Search Report Link", reportUrl)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(
                                            context,
                                            if (isBn) "লিংক কপি করা হয়েছে!" else "Link copied to clipboard!",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        showShareModal = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Thread Item Composable ───────────────────────────────────────────────────

@Composable
private fun DeepSearchBlockItem(
    block: DeepSearchThreadBlock,
    isBn: Boolean,
    fontToUse: FontFamily = FontFamily.Default,
    onSourceClick: (String) -> Unit,
    onViewAllSources: (List<DeepSearchSourceItem>, String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onRetryClick: () -> Unit,
    onVisibilityChange: (Boolean) -> Unit = {},
    onShareClick: () -> Unit = {}
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Query Header
        Text(
            text = block.query,
            fontFamily = fontToUse,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                lineHeight = 25.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        // Status Text & Progress Line during Loading
        if (block.isLoading) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SlatePrimary.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = SlatePrimary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = block.statusText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = SlatePrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp)),
                        color = SlatePrimary,
                        trackColor = SlatePrimary.copy(alpha = 0.15f)
                    )
                }
            }
        }

        // Sources Strip with Citation Badges & Favicons (Top Sorted Sources when ready, else Live Discovery)
        val displaySources = if (block.allSortedSources.isNotEmpty()) block.allSortedSources.take(15) else block.liveSources
        if (displaySources.isNotEmpty()) {
            val totalSourcesCount = if (block.allSortedSources.isNotEmpty()) block.allSortedSources.size else block.liveSources.size
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBn) "তথ্যসূত্র ($totalSourcesCount)" else "Sources ($totalSourcesCount)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val allSourcesList = if (block.allSortedSources.isNotEmpty()) block.allSortedSources else block.liveSources
                    if (allSourcesList.isNotEmpty()) {
                        Text(
                            text = if (isBn) "সকল তথ্যসূত্র (${allSourcesList.size}টি) ➔" else "View All (${allSourcesList.size}) ➔",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = SlatePrimary,
                            modifier = Modifier.clickable {
                                onViewAllSources(
                                    allSourcesList,
                                    if (isBn) "সকল তথ্যসূত্র (${allSourcesList.size}টি)" else "All References (${allSourcesList.size})"
                                )
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displaySources.forEach { source ->
                        Surface(
                            onClick = { onSourceClick(source.url) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            shadowElevation = 1.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (source.citationId > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                brush = Brush.linearGradient(
                                                    colors = listOf(SlatePrimary, SlatePrimary.copy(alpha = 0.85f))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${source.citationId}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }

                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data("https://www.google.com/s2/favicons?domain=${source.domain}&sz=64")
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = source.domain,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape),
                                    error = painterResource(id = R.drawable.ic_launcher_foreground)
                                )
                                Column {
                                    Text(
                                        text = source.domain,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = SlatePrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = source.title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(120.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Error State Display
        if (block.isError) {
            val isNetErr = !NetworkErrorHelper.isDeviceOnline(context) || NetworkErrorHelper.isNetworkException(Exception(block.errorMessage))
            if (isNetErr) {
                OfflineStateCard(
                    isBn = isBn,
                    fontToUse = fontToUse,
                    featureName = if (isBn) "ডিপ সার্চ" else "DeepSearch",
                    compact = true,
                    onRetry = onRetryClick
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = block.errorMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = onRetryClick) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isBn) "পুনরায় চেষ্টা করুন" else "Retry")
                        }
                    }
                }
            }
        }

        // Answer Body Display with Full Markdown Rendering
        if (block.displayedAnswerText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                DeepSearchMarkdownRenderer(
                    markdownText = block.displayedAnswerText,
                    fontToUse = fontToUse,
                    onCitationClick = { citationId ->
                        val source = block.allSortedSources.find { it.citationId == citationId }
                        if (source != null) {
                            val intent = CustomTabsIntent.Builder().build()
                            intent.launchUrl(context, Uri.parse(source.url))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Public / Private Report Visibility Selector
            if (!block.isLoading && !block.isError) {
                val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color(0xFF14171C) else Color(0xFFF6F8FA),
                    border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (block.isPublic) Icons.Default.Language else Icons.Default.Lock,
                                contentDescription = null,
                                tint = SlatePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isBn) "আপনার রিপোর্ট-টি কি সবার জন্য উন্মুক্ত রাখবেন?" else "Keep this report public for everyone in feed?",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = if (block.isPublic) {
                                if (isBn) "উন্মুক্ত রয়েছে: এটি খোঁজের পাবলিক ফিড এবং আপনার প্রোফাইল হিস্ট্রি উভয়েই দেখা যাবে।"
                                else "Public: Visible in Khoj public feed and your profile history."
                            } else {
                                if (isBn) "ব্যক্তিগত রয়েছে: এটি পাবলিক ফিডে থাকবে না, কেবল আপনার প্রোফাইল হিস্ট্রিতে সংরক্ষিত থাকবে।"
                                else "Private: Hidden from public feed, saved only in your personal history."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            // "হ্যাঁ" (Public) Option
                            Surface(
                                onClick = {
                                    if (!block.isPublic) {
                                        block.isPublic = true
                                        onVisibilityChange(true)
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = if (block.isPublic) SlatePrimary else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (block.isPublic) SlatePrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (block.isPublic) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isBn) "হ্যাঁ (উন্মুক্ত)" else "Yes (Public)",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (block.isPublic) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        ),
                                        color = if (block.isPublic) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // "না" (Private) Option
                            Surface(
                                onClick = {
                                    if (block.isPublic) {
                                        block.isPublic = false
                                        onVisibilityChange(false)
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = if (!block.isPublic) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (!block.isPublic) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!block.isPublic) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isBn) "না (ব্যক্তিগত)" else "No (Private)",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (!block.isPublic) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        ),
                                        color = if (!block.isPublic) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Direct Share Button inside the visibility card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = onShareClick,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.45f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_share_custom),
                                    contentDescription = null,
                                    tint = SlatePrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isBn) "রিপোর্ট শেয়ার করুন" else "Share Report",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = SlatePrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
