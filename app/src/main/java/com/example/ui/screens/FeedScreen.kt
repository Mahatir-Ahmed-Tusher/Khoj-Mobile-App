package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.composed
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontStyle
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.model.CmsArticle
import com.example.data.model.FeedItem
import com.example.data.model.SupabaseComment
import com.example.data.model.SupabaseReport
import com.example.data.model.FactCheckReport
import com.example.data.model.DeepSearchReport
import com.example.data.model.isDeepSearch
import com.example.data.model.toDeepSearchReport
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.example.ui.components.MarkdownText
import com.example.ui.theme.ColorFalse
import com.example.ui.theme.ColorTrue
import com.example.ui.theme.ColorUnverifiable
import com.example.ui.theme.SlatePrimary
import com.example.ui.viewmodel.FeedUiState
import com.example.ui.viewmodel.FeedViewModel

private val TRUE_DRAWABLES = intArrayOf(R.drawable.true_1, R.drawable.true_2, R.drawable.true_3)
private val FALSE_DRAWABLES = intArrayOf(R.drawable.false_1, R.drawable.false_2, R.drawable.false_3)
private val UNVERIFIABLE_DRAWABLES = intArrayOf(R.drawable.unverifiable_1, R.drawable.unverifiable_2, R.drawable.unverifiable_3)
private val DEEPSEARCH_DRAWABLES = intArrayOf(R.drawable.deepsearch_1, R.drawable.deepsearch_2, R.drawable.deepsearch_3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    feedViewModel: FeedViewModel = viewModel(),
    isBn: Boolean,
    fontToUse: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onSelectReport: (FactCheckReport) -> Unit,
    onSelectDeepSearchReport: (DeepSearchReport) -> Unit = {},
    onSelectCmsArticle: (CmsArticle) -> Unit = {},
    onSelectBlogArticle: (CmsArticle) -> Unit = {},
    onNavigateToNews: () -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val uiState by feedViewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by feedViewModel.isRefreshing.collectAsStateWithLifecycle()
    val hasNewPosts by feedViewModel.hasNewPosts.collectAsStateWithLifecycle()
    val comments by feedViewModel.comments.collectAsStateWithLifecycle()
    val commentsLoading by feedViewModel.commentsLoading.collectAsStateWithLifecycle()
    val isLoadingMore by feedViewModel.isLoadingMore.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstIndex ->
                feedViewModel.isScrolledDown = firstIndex > 0
            }
    }

    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null) {
                    val totalItems = listState.layoutInfo.totalItemsCount
                    // Trigger loadMore if we are within 3 items from the bottom
                    if (totalItems > 0 && lastIndex >= totalItems - 3) {
                        feedViewModel.loadMore()
                    }
                }
            }
    }

    // Map each report ID to a selected stable, non-repeating thumbnail resource ID (Zero Reflection)
    val reportThumbnails = remember(uiState) {
        if (uiState !is FeedUiState.Success) return@remember emptyMap<String, Int>()
        val successState = uiState as FeedUiState.Success
        val lastIndices = mutableMapOf<String, Int>() // category -> last used index

        successState.items.filterIsInstance<FeedItem.ReportItem>().associate { item ->
            if (item.report.isDeepSearch()) {
                val hash = kotlin.math.abs(item.report.id.hashCode())
                val resId = DEEPSEARCH_DRAWABLES[hash % DEEPSEARCH_DRAWABLES.size]
                item.report.id to resId
            } else {
                val category = when (item.report.verdict.lowercase()) {
                    "true" -> "true"
                    "false" -> "false"
                    else -> "unverifiable"
                }

                val pool = when (category) {
                    "true" -> TRUE_DRAWABLES
                    "false" -> FALSE_DRAWABLES
                    else -> UNVERIFIABLE_DRAWABLES
                }

                val lastIdx = lastIndices[category] ?: -1
                val nextIdx = if (lastIdx == -1 || pool.size <= 1) {
                    (item.report.id.hashCode() and 0x7FFFFFFF) % pool.size
                } else {
                    val possibleIndices = (0 until pool.size).filter { it != lastIdx }
                    val choice = (item.report.id.hashCode() and 0x7FFFFFFF) % possibleIndices.size
                    possibleIndices[choice]
                }
                lastIndices[category] = nextIdx
                item.report.id to pool[nextIdx]
            }
        }
    }

    var selectedPostId by remember { mutableStateOf<String?>(null) }
    var showCommentSheet by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<String?>(null) }
    var sharingReport by remember { mutableStateOf<FactCheckReport?>(null) }
    var showShareModal by remember { mutableStateOf(false) }

    if (showCommentSheet && selectedPostId != null) {
        CommentBottomSheet(
            postId = selectedPostId!!,
            comments = comments,
            isLoading = commentsLoading,
            commentText = commentText,
            replyToId = replyToId,
            onCommentChange = { commentText = it },
            onSend = {
                feedViewModel.postComment(selectedPostId!!, commentText, replyToId)
                commentText = ""
                replyToId = null
            },
            onReply = { replyToId = it },
            onDismiss = {
                showCommentSheet = false
                selectedPostId = null
                commentText = ""
                replyToId = null
            }
        )
    }

    val searchQueryText by feedViewModel.searchQuery.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(modifier = Modifier.fillMaxSize().background(if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF0F2F5))) {
        // Floating "New Posts" banner when new updates arrive while scrolled down
        androidx.compose.animation.AnimatedVisibility(
            visible = hasNewPosts,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
                .zIndex(10f)
        ) {
            Surface(
                shape = CircleShape,
                color = SlatePrimary,
                contentColor = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier.clickable {
                    feedViewModel.applyNewPosts()
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Text(
                        text = if (isBn) "নতুন পোস্ট এসেছে ↑" else "New posts available ↑",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontToUse,
                        color = Color.White
                    )
                }
            }
        }

        when (val state = uiState) {
            is FeedUiState.Loading -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "feed_header") {
                        FeedHeader(
                            isBn = isBn,
                            isDark = isDark,
                            fontToUse = fontToUse,
                            searchQueryText = searchQueryText,
                            onSearchQueryChange = { feedViewModel.setSearchQuery(it) },
                            onNavigateBack = onNavigateBack
                        )
                    }
                    items(3) { FeedSkeletonItem(isDark = isDark) }
                }
            }
            is FeedUiState.Error -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "feed_header") {
                        FeedHeader(
                            isBn = isBn,
                            isDark = isDark,
                            fontToUse = fontToUse,
                            searchQueryText = searchQueryText,
                            onSearchQueryChange = { feedViewModel.setSearchQuery(it) },
                            onNavigateBack = onNavigateBack
                        )
                    }
                    item(key = "error_content") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.WifiOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Failed to load feed", fontFamily = fontToUse, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { feedViewModel.loadFeed(force = true) }) { Text("Retry", fontFamily = fontToUse) }
                            }
                        }
                    }
                }
            }
            is FeedUiState.Success -> {
                if (state.items.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "feed_header") {
                            FeedHeader(
                                isBn = isBn,
                                isDark = isDark,
                                fontToUse = fontToUse,
                                searchQueryText = searchQueryText,
                                onSearchQueryChange = { feedViewModel.setSearchQuery(it) },
                                onNavigateBack = onNavigateBack
                            )
                        }
                        item(key = "empty_content") {
                            Box(
                                modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.List, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("No posts yet. Be the first to fact-check!", fontFamily = fontToUse, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { feedViewModel.loadFeed(force = true) },
                        state = pullToRefreshState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullToRefreshState,
                                isRefreshing = isRefreshing,
                                modifier = Modifier.align(Alignment.TopCenter),
                                color = SlatePrimary
                            )
                        }
                    ) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item(key = "feed_header") {
                                FeedHeader(
                                    isBn = isBn,
                                    isDark = isDark,
                                    fontToUse = fontToUse,
                                    searchQueryText = searchQueryText,
                                    onSearchQueryChange = { feedViewModel.setSearchQuery(it) },
                                    onNavigateBack = onNavigateBack
                                )
                            }
                            
                            items(
                                items = state.items,
                                key = { item ->
                                    when (item) {
                                        is FeedItem.ReportItem -> "r_${item.report.id}"
                                        is FeedItem.ArticleItem -> "a_${item.article.slug}"
                                        is FeedItem.NewsItem -> "n_${item.news.link}"
                                        is FeedItem.MoreNewsButtonItem -> "btn_more_news"
                                    }
                                },
                                contentType = { item -> item::class.java }
                            ) { item ->
                                when (item) {
                                    is FeedItem.ReportItem -> {
                                        if (item.report.isDeepSearch()) {
                                            DeepSearchFeedCard(
                                                item = item,
                                                isBn = isBn,
                                                fontToUse = fontToUse,
                                                thumbnailResId = reportThumbnails[item.report.id] ?: R.drawable.deepsearch_1,
                                                onLike = { feedViewModel.toggleLike(item.report.id) },
                                                onComment = {
                                                    selectedPostId = item.report.id
                                                    feedViewModel.loadComments(item.report.id)
                                                    showCommentSheet = true
                                                },
                                                onShare = {
                                                    sharingReport = item.report.toFactCheckReport()
                                                    showShareModal = true
                                                },
                                                onSelectDeepSearchReport = onSelectDeepSearchReport
                                            )
                                        } else {
                                            ReportFeedCard(
                                                item = item,
                                                isBn = isBn,
                                                fontToUse = fontToUse,
                                                thumbnailResId = reportThumbnails[item.report.id] ?: 0,
                                                onLike = { feedViewModel.toggleLike(item.report.id) },
                                                onComment = {
                                                    selectedPostId = item.report.id
                                                    feedViewModel.loadComments(item.report.id)
                                                    showCommentSheet = true
                                                },
                                                onShare = {
                                                    sharingReport = item.report.toFactCheckReport()
                                                    showShareModal = true
                                                },
                                                onSelectReport = onSelectReport
                                            )
                                        }
                                    }
                                    is FeedItem.ArticleItem -> ArticleFeedCard(
                                        item = item,
                                        isBn = isBn,
                                        fontToUse = fontToUse,
                                        onLike = { feedViewModel.toggleLike(item.article.slug) },
                                        onComment = {
                                            selectedPostId = item.article.slug
                                            feedViewModel.loadComments(item.article.slug)
                                            showCommentSheet = true
                                        },
                                        onSelectCmsArticle = onSelectCmsArticle,
                                        onSelectBlogArticle = onSelectBlogArticle
                                    )
                                    is FeedItem.NewsItem -> NewsArticleCard(
                                        article = item.news,
                                        isBn = isBn,
                                        fontToUse = fontToUse,
                                        isDark = isDark,
                                        onMoreNewsClick = onNavigateToNews
                                    )
                                    is FeedItem.MoreNewsButtonItem -> MoreNewsButtonCard(
                                        isBn = isBn,
                                        fontToUse = fontToUse,
                                        onClick = onNavigateToNews
                                    )
                                }
                            }

                            if (isLoadingMore) {
                                item(key = "loading_more_spinner") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = SlatePrimary,
                                            strokeWidth = 3.dp
                                        )
                                    }
                                }
                            } else if (!state.hasMore) {
                                item(key = "end_of_feed") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isBn) "আপনি ফিডের শেষে পৌঁছেছেন" else "You've reached the end of the feed",
                                            fontFamily = fontToUse,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showShareModal && sharingReport != null) {
        val report = sharingReport!!
        val reportUrl = "https://khoj-ai.bd/factcheck-detail?id=${report.id}"
        val verdictText = when (report.verdict.lowercase()) {
            "true" -> if (isBn) "সত্য" else "True"
            "false" -> if (isBn) "মিথ্যা" else "False"
            "misleading" -> if (isBn) "বিভ্রান্তিকর" else "Misleading"
            "context_dependent" -> if (isBn) "প্রসঙ্গ নির্ভর" else "Context Dependent"
            else -> if (isBn) "অযাচাইকৃত" else "Unverified"
        }
        val shareText = if (isBn) {
            "খোঁজ এআই দ্বারা যাচাইকৃত রিপোর্ট:\nদাবি: ${report.claimText}\nফলাফল: ${verdictText}\nবিস্তারিত দেখুন: $reportUrl"
        } else {
            "Factchecked Report by Khoj AI:\nClaim: ${report.claimText}\nVerdict: ${report.verdict}\nView details: $reportUrl"
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
                        .fillMaxWidth(0.9f)
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
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = if (isBn) "এই রিপোর্ট শেয়ার করুন" else "Share this report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontToUse,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShareSocialIconButton(
                                iconRes = R.drawable.ic_facebook_custom,
                                onClick = {
                                    shareToApp(context, "com.facebook.orca", shareText, reportUrl)
                                    showShareModal = false
                                }
                            )
                            ShareSocialIconButton(
                                iconRes = R.drawable.ic_whatsapp_custom,
                                onClick = {
                                    shareToApp(context, "com.whatsapp", shareText, reportUrl)
                                    showShareModal = false
                                }
                            )
                            ShareSocialIconButton(
                                iconRes = R.drawable.ic_instagram_custom,
                                onClick = {
                                    shareToApp(context, "com.instagram.android", shareText, reportUrl)
                                    showShareModal = false
                                }
                            )
                            ShareSocialIconButton(
                                iconRes = R.drawable.ic_x_custom,
                                onClick = {
                                    shareToApp(context, "com.twitter.android", shareText, reportUrl)
                                    showShareModal = false
                                }
                            )
                            ShareSocialIconButton(
                                iconRes = R.drawable.ic_linkedin_custom,
                                onClick = {
                                    shareToApp(context, "com.linkedin.android", shareText, reportUrl)
                                    showShareModal = false
                                }
                            )
                            ShareSocialIconButton(
                                iconRes = R.drawable.ic_copy_custom,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Fact Check Report Link", reportUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(
                                        context,
                                        if (isBn) "লিংক কপি করা হয়েছে!" else "Link copied to clipboard!",
                                        Toast.LENGTH_SHORT
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

// ── Deep Search Feed Card ──────────────────────────────────────────────────

@Composable
fun DeepSearchFeedCard(
    item: FeedItem.ReportItem,
    isBn: Boolean,
    fontToUse: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    thumbnailResId: Int,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onSelectDeepSearchReport: (DeepSearchReport) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val deepSearchReport = remember(item.report) { item.report.toDeepSearchReport(isFeedPreview = true) }
    val sourceCount = deepSearchReport.sources.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectDeepSearchReport(deepSearchReport) },
        shape = androidx.compose.ui.graphics.RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.background else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Author row
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(
                        painter = painterResource(R.drawable.khoj_ai_bot),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isBn) "খোঁজ এআই" else "Khoj AI",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Box(
                            modifier = Modifier
                                .background(SlatePrimary.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .border(BorderStroke(0.5.dp, SlatePrimary.copy(alpha = 0.4f)), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isBn) "খোঁজ AI Generated" else "Khoj AI Generated",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlatePrimary
                            )
                        }
                        Text(
                            text = formatRelativeTime(item.report.createdAt),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Deep Search Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SlatePrimary.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, SlatePrimary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = SlatePrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isBn) "ডিপসার্চ" else "DeepSearch",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlatePrimary
                            )
                        }
                    }
                }

                // Question / Query (Semi-bold, 15-16.sp, scannable)
                Text(
                    text = deepSearchReport.query.ifBlank { item.report.originalQuery },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Preview Text & Read More
                val content = deepSearchReport.reportContent.ifBlank { item.report.reportContent ?: "" }
                if (content.isNotBlank()) {
                    val previewText = remember(content) {
                        val cleanText = content.lines()
                            .filter { !it.trim().startsWith("#") }
                            .joinToString(" ")
                            .trim()
                        if (cleanText.length > 160) cleanText.take(160) + "..." else cleanText
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = previewText,
                            fontFamily = fontToUse,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBn) "আরও পড়ুন ➔" else "Read More ➔",
                                fontFamily = fontToUse,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = SlatePrimary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )

                            if (sourceCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = if (isBn) "${sourceCount}টি তথ্যসূত্র" else "$sourceCount sources",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Thumbnail banner (touches side corners edge-to-edge)
            if (thumbnailResId != 0) {
                Image(
                    painter = painterResource(id = thumbnailResId),
                    contentDescription = "Deep Search Report Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
            }

            // Action row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LikeButton(isLiked = item.isLikedByMe, count = item.likeCount, onClick = onLike)
                    Spacer(Modifier.width(8.dp))
                    CommentButton(count = item.commentCount, onClick = onComment)
                    Spacer(Modifier.width(8.dp))
                    ShareButton(onClick = onShare)
                }
            }
        }
    }
}

// ── Report Feed Card ──────────────────────────────────────────────────────────

@Composable
fun ReportFeedCard(
    item: FeedItem.ReportItem,
    isBn: Boolean,
    fontToUse: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    thumbnailResId: Int,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onSelectReport: (FactCheckReport) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectReport(item.report.toFactCheckReport()) },
        shape = androidx.compose.ui.graphics.RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.background else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Author row
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(
                        painter = painterResource(R.drawable.khoj_ai_bot),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isBn) "খোঁজ এআই" else "Khoj AI",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0284C7).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .border(BorderStroke(0.5.dp, Color(0xFF0284C7).copy(alpha = 0.4f)), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isBn) "ব্যবহারকারীদের যাচাইকৃত" else "Factchecked by the Users",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7)
                            )
                        }
                        Text(
                            text = formatRelativeTime(item.report.createdAt),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    VerdictBadge(verdict = item.report.verdict)
                }

                // Claim text
                Text(
                    text = item.report.originalQuery,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Report Preview (Markdown rendered)
                val content = item.report.reportContent
                if (!content.isNullOrBlank()) {
                    val previewText = remember(content) {
                        if (content.length > 150) {
                            content.take(150) + "..."
                        } else {
                            content
                        }
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MarkdownText(
                            text = previewText,
                            fontToUse = fontToUse,
                            textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontSize = 13,
                            lineHeight = 20
                        )
                        
                        Text(
                            text = if (item.report.language == "bn") "আরও পড়ুন" else "Read More",
                            fontFamily = fontToUse,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = SlatePrimary,
                            modifier = Modifier
                                .clickable {
                                    onSelectReport(item.report.toFactCheckReport())
                                }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }

            // Thumbnail banner (touches side corners edge-to-edge)
            if (thumbnailResId != 0) {
                Image(
                    painter = painterResource(id = thumbnailResId),
                    contentDescription = "Report Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
            }

            // Confidence bar & Action row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.report.confidenceScore > 0) {
                    val color = verdictColor(item.report.verdict)
                    LinearProgressIndicator(
                        progress = { item.report.confidenceScore.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = color,
                        trackColor = color.copy(alpha = 0.15f)
                    )
                    Text(
                        text = "${(item.report.confidenceScore * 100).toInt()}% confidence",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LikeButton(isLiked = item.isLikedByMe, count = item.likeCount, onClick = onLike)
                    Spacer(Modifier.width(8.dp))
                    CommentButton(count = item.commentCount, onClick = onComment)
                    Spacer(Modifier.width(8.dp))
                    ShareButton(onClick = onShare)
                }
            }
        }
    }
}

// ── Article Feed Card ─────────────────────────────────────────────────────────

@Composable
fun ArticleFeedCard(
    item: FeedItem.ArticleItem,
    isBn: Boolean = false,
    fontToUse: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onSelectCmsArticle: (CmsArticle) -> Unit = {},
    onSelectBlogArticle: (CmsArticle) -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val onClickArticle = {
        val cmsArticle = item.article.toCmsArticle()
        if (item.article.type.equals("blog", ignoreCase = true)) {
            onSelectBlogArticle(cmsArticle)
        } else {
            onSelectCmsArticle(cmsArticle)
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickArticle() },
        shape = androidx.compose.ui.graphics.RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF242526) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!item.article.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.article.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(item.article.type.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
                    )
                    item.article.verdict?.let { VerdictBadge(verdict = it) }
                }
                Text(
                    text = item.article.title,
                    fontFamily = fontToUse,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
                item.article.excerpt?.let {
                    Text(text = it, fontFamily = fontToUse, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = if (isBn) "আরও পড়ুন ➔" else "Read More ➔",
                    fontFamily = fontToUse,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = SlatePrimary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Text(
                    text = "By ${item.article.author} · ${formatRelativeTime(item.article.publishedAt)}",
                    fontFamily = fontToUse,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LikeButton(isLiked = item.isLikedByMe, count = item.likeCount, onClick = onLike)
                    Spacer(Modifier.width(4.dp))
                    CommentButton(count = item.commentCount, onClick = onComment)
                }
            }
        }
    }
}

// ── Shared Components ─────────────────────────────────────────────────────────

@Composable
fun LikeButton(isLiked: Boolean, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable { onClick() }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = "Like",
            tint = if (isLiked) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(count.toString(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CommentButton(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable { onClick() }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comment", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(count.toString(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun VerdictBadge(verdict: String) {
    val (label, fg) = when (verdict.lowercase()) {
        "true" -> Pair("✓ TRUE", Color(0xFF16A34A))
        "false" -> Pair("✗ FALSE", Color(0xFFDC2626))
        else -> Pair("⚠ MISLEADING", Color(0xFFD97706))
    }
    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
}

// ── Comment Bottom Sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentBottomSheet(
    postId: String,
    comments: List<SupabaseComment>,
    isLoading: Boolean,
    commentText: String,
    replyToId: String? = null,
    onCommentChange: (String) -> Unit,
    onSend: () -> Unit,
    onReply: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Comments (${comments.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else if (comments.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("No comments yet. Be the first!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(
                        items = comments,
                        key = { it.id },
                        contentType = { "comment" }
                    ) { comment ->
                        CommentItem(comment = comment, onReply = { onReply(comment.id) })
                    }
                }
            }
            HorizontalDivider()
            
            // Replying to banner
            if (replyToId != null) {
                val targetComment = comments.find { it.id == replyToId }
                val targetAuthor = targetComment?.userId?.take(8) ?: "User"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(SlatePrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Replying to @$targetAuthor",
                        fontSize = 12.sp,
                        color = SlatePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = { onReply(null) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel reply",
                            tint = SlatePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Input
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = onCommentChange,
                    placeholder = { 
                        Text(
                            text = if (replyToId != null) "Write a reply..." else "Write a comment...", 
                            fontSize = 14.sp
                        ) 
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
                IconButton(
                    onClick = onSend,
                    enabled = commentText.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = SlatePrimary)
                }
            }
        }
    }
}

@Composable
fun CommentItem(comment: SupabaseComment, onReply: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = (16 + comment.depth * 20).dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 2.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(comment.userId?.take(8) ?: "User", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(comment.commentText, fontSize = 14.sp)
            Row {
                TextButton(onClick = onReply, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
                    Text("Reply", fontSize = 12.sp, color = SlatePrimary)
                }
                Text(
                    formatRelativeTime(comment.createdAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun verdictColor(verdict: String): Color = when (verdict.lowercase()) {
    "true" -> ColorTrue
    "false" -> ColorFalse
    "misleading" -> ColorUnverifiable
    else -> Color(0xFF94A3B8)
}

fun formatRelativeTime(isoString: String): String {
    return try {
        val cleanString = isoString.replace("Z", "+0000")
        val formatPattern = if (cleanString.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSSZ" else "yyyy-MM-dd'T'HH:mm:ssZ"
        val sdf = java.text.SimpleDateFormat(formatPattern, java.util.Locale.US)
        val date = sdf.parse(cleanString)
        if (date != null) {
            val diffMs = System.currentTimeMillis() - date.time
            val diffSeconds = diffMs / 1000
            when {
                diffSeconds < 60 -> "just now"
                diffSeconds < 3600 -> "${diffSeconds / 60}m ago"
                diffSeconds < 86400 -> "${diffSeconds / 3600}h ago"
                diffSeconds < 604800 -> "${diffSeconds / 86400}d ago"
                else -> {
                    val outputSdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                    outputSdf.format(date)
                }
            }
        } else {
            isoString.take(10)
        }
    } catch (e: Throwable) { isoString.take(10) }
}

private fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shimmerColors = if (isDark) {
        listOf(
            Color(0xFF1E293B),
            Color(0xFF334155),
            Color(0xFF1E293B)
        )
    } else {
        listOf(
            Color(0xFFE2E8F0),
            Color(0xFFF1F5F9),
            Color(0xFFE2E8F0)
        )
    }

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim.value - 300f, y = translateAnim.value - 300f),
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    background(brush)
}

@Composable
fun FeedSkeletonItem(isDark: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.ui.graphics.RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF242526) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Author row skeleton
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .shimmerEffect()
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerEffect()
                        )
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerEffect()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .shimmerEffect()
                    )
                }

                // Claim text skeleton
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                }
            }

            // Image skeleton (touches side corners edge-to-edge)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .shimmerEffect()
            )

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Action row skeleton
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .shimmerEffect()
                    )
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}

private fun SupabaseReport.toFactCheckReport(): FactCheckReport {
    val sourcesArr = org.json.JSONArray()
    this.sources?.forEach { element ->
        try {
            val obj = element.jsonObject
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
            val snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull ?: ""
            val score = obj["credibilityScore"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val tier = if (score != null) {
                if (score >= 0.8) 1 else if (score >= 0.6) 2 else 3
            } else {
                obj["tier"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 3
            }
            sourcesArr.put(org.json.JSONObject().apply {
                put("title", title)
                put("url", url)
                put("snippet", snippet)
                put("domain", try { java.net.URI(url).host ?: "" } catch(e: Exception) { "" })
                put("credibilityTier", tier)
                put("publishDate", "")
            })
        } catch (e: Exception) {
            // ignore malformed elements
        }
    }
    return FactCheckReport(
        id = this.id,
        claimText = this.processedQuery ?: this.originalQuery,
        normalizedClaim = this.processedQuery ?: this.originalQuery,
        verdict = this.verdict,
        confidence = (this.confidenceScore * 100).toInt(),
        summaryBn = this.reportContent ?: "",
        summaryEn = this.reportContent ?: "",
        reasoningBn = this.reportContent ?: "",
        reasoningEn = this.reportContent ?: "",
        markdownReport = this.reportContent, // Populate so getEffectiveMarkdown() renders it directly
        sourcesJson = sourcesArr.toString(),
        contradictingSourcesJson = "[]",
        isPublic = this.isPublic ?: true,
        userId = this.userId,
        timestamp = try {
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
    )
}


@Composable
fun MoreNewsButtonCard(isBn: Boolean, fontToUse: androidx.compose.ui.text.font.FontFamily, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = com.example.ui.theme.SlatePrimary,
            contentColor = androidx.compose.ui.graphics.Color.White
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            androidx.compose.material3.Text(
                text = if (isBn) "আরও খবর" else "More News",
                fontFamily = fontToUse,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 16.sp
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.ArrowForward,
                contentDescription = null
            )
        }
    }
}

@Composable
fun ShareButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Share",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun FeedHeader(
    isBn: Boolean,
    isDark: Boolean,
    fontToUse: androidx.compose.ui.text.font.FontFamily,
    searchQueryText: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        if (!isDark) {
            Image(
                painter = painterResource(id = R.drawable.landing_page_bg_light),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.08f
            )
        }

        // overlay gradient to blend with the list page background
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(Color(0xFF0F172A).copy(alpha = 0.2f), Color(0xFF0F172A))
                        } else {
                            listOf(Color.White.copy(alpha = 0.2f), Color.White)
                        }
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (onNavigateBack != null) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Text(
                    text = if (isBn) "ফিড" else "Feed",
                    fontFamily = fontToUse,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.5.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            OutlinedTextField(
                value = searchQueryText,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = if (isBn) "ফিড অনুসন্ধান করুন..." else "Search feed...",
                        fontFamily = fontToUse,
                        fontSize = 13.5.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                    )
                },
                trailingIcon = {
                    if (searchQueryText.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = if (isDark) Color.White else Color.Black,
                    unfocusedTextColor = if (isDark) Color.White else Color.Black,
                    focusedContainerColor = if (isDark) Color.Black else Color.White,
                    unfocusedContainerColor = if (isDark) Color.Black else Color.White,
                    focusedBorderColor = SlatePrimary,
                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f),
                    cursorColor = SlatePrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
        }
    }
}

