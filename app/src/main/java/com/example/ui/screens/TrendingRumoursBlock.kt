package com.example.ui.screens

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.TrendingRumourUi
import com.example.ui.theme.SlatePrimary
import com.example.ui.viewmodel.TrendingRumoursState
import com.example.ui.viewmodel.TrendingRumoursViewModel
import kotlin.math.abs

@Composable
fun TrendingRumoursBlock(
    isBn: Boolean,
    onNavigateToSeeAll: () -> Unit = {},
    viewModel: TrendingRumoursViewModel = viewModel()
) {
    LaunchedEffect(isBn) {
        viewModel.onLanguageChanged(isBn)
    }

    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDark = (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val lazyListState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // ── Header Row: Title | Refresh | "See All ->" ────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.trending_ic),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isBn) "ট্রেন্ডিং রিউমার" else "Trending Rumours",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { viewModel.refresh() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = if (isBn) "রিফ্রেশ" else "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onNavigateToSeeAll() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isBn) "সব দেখুন" else "See All",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = SlatePrimary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = SlatePrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Horizontal Scrollable Row (LazyRow with Snap Fling) ───────────
        when (val s = state) {
            is TrendingRumoursState.Loading -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(4) {
                        TrendingSkeletonLandscapeCard(isDark = isDark)
                    }
                }
            }

            is TrendingRumoursState.Success -> {
                LazyRow(
                    state = lazyListState,
                    flingBehavior = snapFlingBehavior,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = s.rumours,
                        key = { _, rumour -> rumour.url }
                    ) { index, rumour ->
                        TrendingRumourLandscapeCard(
                            rumour = rumour,
                            isBn = isBn,
                            isDark = isDark,
                            cardWidth = 280.dp,
                            index = index,
                            onClick = {
                                val intent = CustomTabsIntent.Builder().build()
                                intent.launchUrl(context, Uri.parse(rumour.url))
                            }
                        )
                    }
                }
            }

            is TrendingRumoursState.Error -> {
                if (!s.cachedRumours.isNullOrEmpty()) {
                    LazyRow(
                        state = lazyListState,
                        flingBehavior = snapFlingBehavior,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = s.cachedRumours,
                            key = { _, rumour -> rumour.url }
                        ) { index, rumour ->
                            TrendingRumourLandscapeCard(
                                rumour = rumour,
                                isBn = isBn,
                                isDark = isDark,
                                cardWidth = 280.dp,
                                index = index,
                                onClick = {
                                    val intent = CustomTabsIntent.Builder().build()
                                    intent.launchUrl(context, Uri.parse(rumour.url))
                                }
                            )
                        }
                    }
                } else {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/** Individual Neumorphic Landscape Card for Trending Rumours */
@Composable
fun TrendingRumourLandscapeCard(
    rumour: TrendingRumourUi,
    isBn: Boolean,
    isDark: Boolean,
    cardWidth: androidx.compose.ui.unit.Dp? = 280.dp,
    index: Int = 0,
    onClick: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val shadowLight = if (isDark) Color(0x12FFFFFF) else Color(0xFFFFFFFF)
    val shadowDark = if (isDark) Color(0x55000000) else Color(0xFFD1D9E6)

    val modifierWidth = if (cardWidth != null) Modifier.width(cardWidth) else Modifier.fillMaxWidth()

    Box(
        modifier = modifierWidth
            .height(105.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(8.dp),
                spotColor = shadowDark,
                ambientColor = shadowLight
            )
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Left: Thumbnail (~35% width, ~100dp width) ─────────────────
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .clip(
                        RoundedCornerShape(
                            topStart = 8.dp,
                            bottomStart = 8.dp,
                            topEnd = 0.dp,
                            bottomEnd = 0.dp
                        )
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                if (!rumour.thumbnail.isNullOrBlank()) {
                    val context = LocalContext.current
                    val imageRequest = remember(rumour.thumbnail) {
                        coil.request.ImageRequest.Builder(context)
                            .data(rumour.thumbnail)
                            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                            .apply {
                                if (rumour.thumbnail.contains("fact-watch.org")) {
                                    setHeader("Referer", "https://www.fact-watch.org/")
                                }
                            }
                            .crossfade(200)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = rumour.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback placeholder using hero section background drawables or shuffled fallback drawables
                    val fallbackDrawables = remember {
                        listOf(
                            R.drawable.bg_image_hero_section_dark_theme,
                            R.drawable.bg_image_hero_section_white_theme,
                            R.drawable.false_1,
                            R.drawable.false_2,
                            R.drawable.cover_1
                        )
                    }
                    val chosenRes = fallbackDrawables[abs(rumour.title.hashCode() + index) % fallbackDrawables.size]
                    Image(
                        painter = painterResource(id = chosenRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // ── Right: Title & Metadata Stack ──────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Title (max 2 lines, bold, ellipsized)
                @Suppress("DEPRECATION")
                Text(
                    text = rumour.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        )
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Metadata line: DD-MM-YYYY • Source Name
                val formattedDate = formatTrendingDate(rumour.timestamp, isBn)
                val formattedSource = formatTrendingSource(rumour.source, isBn)
                val metaText = buildString {
                    if (formattedDate.isNotBlank()) append(formattedDate)
                    if (formattedDate.isNotBlank() && formattedSource.isNotBlank()) {
                        append("  •  ")
                    }
                    if (formattedSource.isNotBlank()) append(formattedSource)
                }

                if (metaText.isNotBlank()) {
                    @Suppress("DEPRECATION")
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

fun formatTrendingDate(raw: String?, isBn: Boolean): String {
    if (raw.isNullOrBlank()) return ""
    val trimmed = raw.trim()
    val isoMatch = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(trimmed)
    val formatted = if (isoMatch != null) {
        val (year, month, day) = isoMatch.destructured
        "$day-$month-$year"
    } else {
        trimmed.take(10)
    }

    return if (isBn) {
        formatted.map { ch ->
            when (ch) {
                '0' -> '০'
                '1' -> '১'
                '2' -> '২'
                '3' -> '৩'
                '4' -> '৪'
                '5' -> '৫'
                '6' -> '৬'
                '7' -> '৭'
                '8' -> '৮'
                '9' -> '৯'
                else -> ch
            }
        }.joinToString("")
    } else {
        formatted
    }
}

fun formatTrendingSource(rawSource: String, isBn: Boolean): String {
    val s = rawSource.trim()
    val lower = s.lowercase()
    return if (isBn) {
        when {
            lower.contains("fact") && lower.contains("watch") || lower.contains("ফ্যাক্ট") -> "ফ্যাক্টওয়াচ"
            lower.contains("dissent") || lower.contains("ডিসেন্ট") -> "দ্যা ডিসেন্ট"
            lower.contains("rumor") || lower.contains("rumour") || lower.contains("রিউমার") -> "রিউমার স্ক্যানার"
            lower.contains("factcheck") -> "ফ্যাক্টচেক.ওআরজি"
            lower.contains("snopes") -> "স্নোপস"
            lower.contains("politifact") -> "পলিটিফ্যাক্ট"
            else -> s
        }
    } else {
        when {
            s.contains("ফ্যাক্টওয়াচ") -> "Fact Watch"
            s.contains("ডিসেন্ট") -> "The Dissent"
            s.contains("রিউমার") -> "Rumor Scanner"
            lower.contains("rumour scanner") || lower.contains("rumor scanner") -> "Rumor Scanner"
            lower.contains("fact-watch") || lower.contains("fact watch") -> "Fact Watch"
            lower.contains("the dissent") || lower == "dissent" -> "The Dissent"
            else -> s
        }
    }
}

@Composable
fun TrendingSkeletonLandscapeCard(isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "trendingSkeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = if (isDark) 0.05f else 0.07f,
        targetValue = if (isDark) 0.16f else 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    val shimmerColor = if (isDark) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha)

    Box(
        modifier = Modifier
            .width(280.dp)
            .height(105.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .background(shimmerColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .background(shimmerColor, RoundedCornerShape(4.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .background(shimmerColor, RoundedCornerShape(4.dp))
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(11.dp)
                        .background(shimmerColor, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}
