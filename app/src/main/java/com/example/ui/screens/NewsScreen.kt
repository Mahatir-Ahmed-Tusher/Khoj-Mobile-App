package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.NewsArticle
import com.example.ui.theme.SlatePrimary
import com.example.ui.viewmodel.NewsUiState
import com.example.ui.viewmodel.NewsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    newsViewModel: NewsViewModel = viewModel(),
    isBn: Boolean,
    fontToUse: FontFamily = FontFamily.Default,
) {
    val uiState by newsViewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by newsViewModel.isRefreshing.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            is NewsUiState.Loading -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        NewsHeader(isBn = isBn, fontToUse = fontToUse, isDark = isDark)
                    }
                    items(5) {
                        NewsSkeletonCard(isDark = isDark)
                    }
                }
            }

            is NewsUiState.Error -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    NewsHeader(isBn = isBn, fontToUse = fontToUse, isDark = isDark)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(52.dp)
                            )
                            Text(
                                text = if (isBn) "সংবাদ লোড হয়নি" else "Could not load news",
                                fontFamily = fontToUse,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isBn)
                                    "সার্ভার জেগে উঠতে ~৩০ সেকেন্ড লাগতে পারে। আবার চেষ্টা করুন।"
                                else
                                    "The server may take ~30s to wake. Please retry.",
                                fontFamily = fontToUse,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Button(
                                onClick = { newsViewModel.loadNews() },
                                colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isBn) "আবার চেষ্টা করুন" else "Retry",
                                    fontFamily = fontToUse
                                )
                            }
                        }
                    }
                }
            }

            is NewsUiState.Success -> {
                if (state.articles.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        NewsHeader(isBn = isBn, fontToUse = fontToUse, isDark = isDark)
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Newspaper,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = if (isBn) "কোনো সংবাদ পাওয়া যায়নি" else "No news articles found",
                                    fontFamily = fontToUse,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { newsViewModel.refresh() },
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
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                NewsHeader(isBn = isBn, fontToUse = fontToUse, isDark = isDark)
                            }
                            items(state.articles, key = { it.link }) { article ->
                                NewsArticleCard(
                                    article = article,
                                    isBn = isBn,
                                    fontToUse = fontToUse,
                                    isDark = isDark
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun NewsHeader(isBn: Boolean, fontToUse: FontFamily, isDark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(SlatePrimary, Color(0xFF6366F1))
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Newspaper,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = if (isBn) "সর্বশেষ সংবাদ" else "Latest News",
                    fontFamily = fontToUse,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isBn) "বাংলা ও ইংরেজি সংবাদ" else "Bengali & English articles",
                    fontFamily = fontToUse,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Article Card ──────────────────────────────────────────────────────────────

@Composable
fun NewsArticleCard(
    article: NewsArticle,
    isBn: Boolean,
    fontToUse: FontFamily = FontFamily.Default,
    isDark: Boolean,
    onMoreNewsClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF0A0F1A) else Color(0xFFF0F7FF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Thumbnail image
            if (!article.imageUrl.isNullOrBlank()) {
                val imageRequest = remember(article.imageUrl) {
                    ImageRequest.Builder(context)
                        .data(article.imageUrl)
                        .crossfade(200)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                )
            }

            // Headline
            Text(
                text = article.title,
                fontFamily = fontToUse,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Description snippet (2–3 lines)
            article.description?.let { desc ->
                Text(
                    text = desc,
                    fontFamily = fontToUse,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Read More → opens link in device browser
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (onMoreNewsClick != null) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onMoreNewsClick != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlatePrimary.copy(alpha = 0.08f))
                            .clickable(onClick = onMoreNewsClick)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBn) "আরও খবর" else "More News",
                            fontFamily = fontToUse,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = SlatePrimary
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = SlatePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Text(
                    text = if (isBn) "আরও পড়ুন →" else "Read More →",
                    fontFamily = fontToUse,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = SlatePrimary,
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.link))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
        }
    }
}

// ── Skeleton Shimmer Card ─────────────────────────────────────────────────────

@Composable
private fun NewsSkeletonCard(isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF1E293B))
        } else {
            listOf(Color(0xFFE2E8F0), Color(0xFFF1F5F9), Color(0xFFE2E8F0))
        },
        start = Offset(translateAnim - 300f, translateAnim - 300f),
        end = Offset(translateAnim, translateAnim)
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF0D0D0D) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(shimmerBrush)
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.9f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
                Box(Modifier.fillMaxWidth(0.7f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
            }
            Box(Modifier.fillMaxWidth(0.85f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
        }
    }
}
