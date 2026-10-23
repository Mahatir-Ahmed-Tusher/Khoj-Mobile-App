package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.CanonicalClaimItem
import com.example.data.model.DistrictHotspotItem
import com.example.data.model.MediaSpreadItem
import com.example.data.model.NarrativeItem
import com.example.data.model.RumorMeterData
import com.example.data.model.TopicItem
import com.example.ui.components.SpeedometerGaugeArc
import com.example.ui.theme.SlatePrimary
import com.example.ui.viewmodel.RumorMeterUiState
import com.example.ui.viewmodel.RumorMeterViewModel
import com.example.ui.viewmodel.TrendingRumoursState
import com.example.ui.viewmodel.TrendingRumoursViewModel
import com.example.utils.BengaliNumberUtils

/**
 * Dedicated, full-screen Khoj Rumour Meter observatory.
 * Integrates directly with the Cloudflare Worker API.
 * Adheres strictly to Bengali typography guidelines and zero-unnecessary-padding layout rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RumorMeterScreen(
    isBn: Boolean,
    onBack: () -> Unit,
    viewModel: RumorMeterViewModel = viewModel(),
    trendingViewModel: TrendingRumoursViewModel = viewModel()
) {
    var showHotspotsOverlay by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showHotspotsOverlay) {
        showHotspotsOverlay = false
    }

    BackHandler(enabled = !showHotspotsOverlay, onBack = onBack)

    LaunchedEffect(isBn) {
        trendingViewModel.onLanguageChanged(isBn)
        trendingViewModel.autoSyncIfNeeded()
    }

    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val trendingState by trendingViewModel.uiState.collectAsState()
    val claims by viewModel.claims.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val context = LocalContext.current

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    // Pulsing live badge animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans_screen")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha_screen"
    )

    // Rotating refresh animation
    val refreshRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_spin"
    )

    // Root container with edge-to-edge statusBarsPadding and no redundant insets
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        if (showHotspotsOverlay) {
            val currentHotspots = when (val s = uiState) {
                is RumorMeterUiState.Success -> s.hotspots
                is RumorMeterUiState.Error -> s.fallbackData?.let { emptyList() } ?: emptyList()
                else -> emptyList()
            }
            DistrictHotspotsFullScreenView(
                hotspots = currentHotspots,
                isBn = isBn,
                isDark = isDark,
                onClose = { showHotspotsOverlay = false }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {

            // ── Top App Bar ───────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isBn) "ফিরে যান" else "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = getString("rumor_meter_title", isBn),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.None
                                    ),
                                    lineHeight = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Live Pill Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = pulseAlpha))
                                )
                                Text(
                                    text = getString("rumor_meter_live", isBn),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    color = Color(0xFF10B981)
                                )
                            }
                        }

                        val lastUpdatedText = when (val s = uiState) {
                            is RumorMeterUiState.Success -> {
                                val minutes = ((System.currentTimeMillis() - s.lastSyncTimestamp) / 60000L).coerceAtLeast(0)
                                if (minutes == 0L) {
                                    if (isBn) "এইমাত্র আপডেট করা হয়েছে" else "Updated just now"
                                } else {
                                    if (isBn) "${BengaliNumberUtils.toBengaliDigits(minutes)} মিনিট আগে আপডেট" else "Updated ${minutes}m ago"
                                }
                            }
                            else -> if (isBn) "রিয়েল-টাইম ডাটা সিঙ্ক হচ্ছে..." else "Syncing live data..."
                        }

                        Text(
                            text = lastUpdatedText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                        )
                    }

                    // Refresh Button
                    IconButton(onClick = { viewModel.refresh(showSpinner = true) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (isBn) "রিফ্রেশ" else "Refresh",
                            tint = SlatePrimary,
                            modifier = if (isRefreshing) Modifier.rotate(refreshRotation) else Modifier
                        )
                    }
                }
            }

            // ── Segmented Navigation Tabs ─────────────────────────────────
            val tabTitles = listOf(
                getString("rumor_meter_overview", isBn) to Icons.Outlined.Dashboard,
                getString("rumor_meter_taxonomy", isBn) to Icons.Outlined.Category,
                (if (isBn) "ট্রেন্ডিং রিউমার" else "Trending Rumours") to Icons.AutoMirrored.Filled.TrendingUp,
                getString("rumor_meter_claims", isBn) to Icons.Outlined.Policy
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = SlatePrimary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = SlatePrimary,
                        height = 2.5.dp
                    )
                },
                divider = {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                        thickness = 0.5.dp
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, (title, icon) ->
                    val isSelected = selectedTabIndex == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (isSelected) SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    color = if (isSelected) SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                                )
                            }
                        }
                    )
                }
            }

            // ── Main Body with PullToRefreshBox ───────────────────────────
            PullToRefreshBox(
                isRefreshing = isRefreshing || trendingState is TrendingRumoursState.Loading,
                onRefresh = {
                    viewModel.refresh(showSpinner = true)
                    trendingViewModel.refresh()
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when (val state = uiState) {
                    is RumorMeterUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = SlatePrimary)
                        }
                    }

                    is RumorMeterUiState.Error -> {
                        if (state.fallbackData != null) {
                            RumorMeterContent(
                                isBn = isBn,
                                isDark = isDark,
                                meterData = state.fallbackData,
                                trends = com.example.data.model.RumorTrendsData(),
                                narratives = emptyList(),
                                hotspots = emptyList(),
                                mediaSpread = emptyList(),
                                claims = claims,
                                trendingState = trendingState,
                                selectedTab = selectedTabIndex,
                                searchQuery = searchQuery,
                                activeFilter = activeFilter,
                                onSearchChanged = { viewModel.onSearchQueryChanged(it) },
                                onFilterChanged = { viewModel.onFilterSelected(it) },
                                onNarrativeClicked = { narrative ->
                                    selectedTabIndex = 3 // Switch to Claims tab
                                    viewModel.onSearchQueryChanged(narrative)
                                },
                                onClaimClicked = { url ->
                                    if (!url.isNullOrBlank()) {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        } catch (_: Throwable) {}
                                    }
                                },
                                onOpenHotspots = { showHotspotsOverlay = true },
                                onRefreshTrending = { trendingViewModel.refresh() }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            )
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    is RumorMeterUiState.Success -> {
                        RumorMeterContent(
                            isBn = isBn,
                            isDark = isDark,
                            meterData = state.meterData,
                            trends = state.trendsData,
                            narratives = state.narratives,
                            hotspots = state.hotspots,
                            mediaSpread = state.mediaSpread,
                            claims = claims,
                            trendingState = trendingState,
                            selectedTab = selectedTabIndex,
                            searchQuery = searchQuery,
                            activeFilter = activeFilter,
                            onSearchChanged = { viewModel.onSearchQueryChanged(it) },
                            onFilterChanged = { viewModel.onFilterSelected(it) },
                            onNarrativeClicked = { narrative ->
                                selectedTabIndex = 3 // Switch to Claims tab
                                viewModel.onSearchQueryChanged(narrative)
                            },
                            onClaimClicked = { url ->
                                if (!url.isNullOrBlank()) {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Throwable) {}
                                }
                            },
                            onOpenHotspots = { showHotspotsOverlay = true },
                            onRefreshTrending = { trendingViewModel.refresh() }
                        )
                    }
                }
            }
        }
    }
}
}

/**
 * Tab-switched body content for the observatory.
 */
@Composable
private fun RumorMeterContent(
    isBn: Boolean,
    isDark: Boolean,
    meterData: RumorMeterData,
    trends: com.example.data.model.RumorTrendsData,
    narratives: List<NarrativeItem>,
    hotspots: List<DistrictHotspotItem>,
    mediaSpread: List<MediaSpreadItem>,
    claims: List<CanonicalClaimItem>,
    trendingState: TrendingRumoursState,
    selectedTab: Int,
    searchQuery: String,
    activeFilter: String,
    onSearchChanged: (String) -> Unit,
    onFilterChanged: (String) -> Unit,
    onNarrativeClicked: (String) -> Unit,
    onClaimClicked: (String?) -> Unit,
    onOpenHotspots: () -> Unit,
    onRefreshTrending: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (selectedTab) {
            0 -> {
                // ── Tab 0: Overview ─────────────────────────────────────────
                item {
                    HeroGaugeCard(meterData = meterData, isBn = isBn, isDark = isDark)
                }

                item {
                    CountsQuadGrid(counts = meterData.counts, isBn = isBn, isDark = isDark)
                }

                if (narratives.isNotEmpty()) {
                    item {
                        NarrativesSection(
                            narratives = narratives,
                            isBn = isBn,
                            isDark = isDark,
                            onNarrativeClicked = onNarrativeClicked
                        )
                    }
                }

                if (mediaSpread.isNotEmpty()) {
                    item {
                        MediaSpreadSection(mediaSpread = mediaSpread, isBn = isBn, isDark = isDark)
                    }
                }

                item {
                    RumorMeterAttributionFooter(isBn = isBn, isDark = isDark)
                }
            }

            1 -> {
                // ── Tab 1: Taxonomy & Formats ────────────────────────────────
                item {
                    TaxonomyTopicsSection(topics = trends.topics, isBn = isBn, isDark = isDark)
                }

                item {
                    FormatsAndTechniquesSection(
                        formats = trends.formats,
                        techniques = trends.techniques,
                        isBn = isBn,
                        isDark = isDark
                    )
                }

                item {
                    RumorMeterAttributionFooter(isBn = isBn, isDark = isDark)
                }
            }

            2 -> {
                // ── Tab 2: Trending Rumours ──────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = SlatePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (isBn) "সোশ্যাল মিডিয়ায় শীর্ষ ভাইরাল গুজব" else "Top Viral Trending Rumours",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.None
                                    ),
                                    lineHeight = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isBn) "রিয়েল-টাইম এআই ভেরিফিকেশন ও ফ্যাক্টচেক ট্র্যাকার" else "Real-time AI verification & fact-check tracker",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.None
                                    )
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                when (val ts = trendingState) {
                    is TrendingRumoursState.Loading -> {
                        items(6) {
                            TrendingSkeletonLandscapeCard(isDark = isDark)
                        }
                    }

                    is TrendingRumoursState.Success -> {
                        itemsIndexed(
                            items = ts.rumours,
                            key = { _, rumour -> rumour.url }
                        ) { index, rumour ->
                            TrendingRumourLandscapeCard(
                                rumour = rumour,
                                isBn = isBn,
                                isDark = isDark,
                                cardWidth = null,
                                index = index,
                                onClick = {
                                    try {
                                        val intent = CustomTabsIntent.Builder().build()
                                        intent.launchUrl(context, Uri.parse(rumour.url))
                                    } catch (_: Throwable) {
                                        onClaimClicked(rumour.url)
                                    }
                                }
                            )
                        }
                    }

                    is TrendingRumoursState.Error -> {
                        if (!ts.cachedRumours.isNullOrEmpty()) {
                            itemsIndexed(
                                items = ts.cachedRumours,
                                key = { _, rumour -> rumour.url }
                            ) { index, rumour ->
                                TrendingRumourLandscapeCard(
                                    rumour = rumour,
                                    isBn = isBn,
                                    isDark = isDark,
                                    cardWidth = null,
                                    index = index,
                                    onClick = {
                                        try {
                                            val intent = CustomTabsIntent.Builder().build()
                                            intent.launchUrl(context, Uri.parse(rumour.url))
                                        } catch (_: Throwable) {
                                            onClaimClicked(rumour.url)
                                        }
                                    }
                                )
                            }
                        } else {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = ts.message,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Button(
                                            onClick = onRefreshTrending,
                                            colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary)
                                        ) {
                                            Text(if (isBn) "পুনরায় চেষ্টা করুন" else "Retry")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    RumorMeterAttributionFooter(isBn = isBn, isDark = isDark)
                }
            }

            3 -> {
                // ── Tab 3: Canonical Claims Feed ─────────────────────────────
                item {
                    ClaimsFilterBar(
                        searchQuery = searchQuery,
                        activeFilter = activeFilter,
                        isBn = isBn,
                        isDark = isDark,
                        onSearchChanged = onSearchChanged,
                        onFilterChanged = onFilterChanged
                    )
                }

                if (claims.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isBn) "কোনো সংশ্লিষ্ট দাবি পাওয়া যায়নি।" else "No matching claims found.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.None
                                    )
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    items(claims) { claim ->
                        ClaimItemCard(
                            claim = claim,
                            isBn = isBn,
                            isDark = isDark,
                            onFactCheckClick = onClaimClicked
                        )
                    }
                }

                // Banner Button to Open District Hotspots
                item {
                    DistrictHotspotsBannerButton(
                        isBn = isBn,
                        isDark = isDark,
                        hotspotCount = hotspots.size,
                        onClick = onOpenHotspots
                    )
                }

                item {
                    RumorMeterAttributionFooter(isBn = isBn, isDark = isDark)
                }
            }
        }

        // Bottom space so content doesn't hit edge
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Dedicated District Hotspots Full View with Cross (Close) Icon.
 */
@Composable
fun DistrictHotspotsFullScreenView(
    hotspots: List<DistrictHotspotItem>,
    isBn: Boolean,
    isDark: Boolean,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar with Close/Cross Icon
        Surface(
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = SlatePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (isBn) "৬৪ জেলা মিসইনফরমেশন উপদ্রব তালিকা" else "64 District Misinformation Hotspots",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                ),
                                lineHeight = 21.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isBn) "উপদ্রুত অঞ্চল অনুযায়ী গুজব শনাক্তকরণ রেকর্ড" else "Rumor prevalence by district",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                }

                // Cross Icon Button to Quit District Hotspots at any time
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = if (isBn) "বন্ধ করুন" else "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
            thickness = 0.5.dp
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(hotspots) { hotspot ->
                HotspotRowCard(hotspot = hotspot, isBn = isBn, isDark = isDark)
            }

            item {
                RumorMeterAttributionFooter(isBn = isBn, isDark = isDark)
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * Banner Button placed at the bottom of the Canonical Claims tab
 * to open the District Hotspots view.
 */
@Composable
fun DistrictHotspotsBannerButton(
    isBn: Boolean,
    isDark: Boolean,
    hotspotCount: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (isDark) Color(0xFF1E3A5F) else Color(0xFFBAE6FD),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        color = if (isDark) Color(0xFF0C1929) else Color(0xFFF0F9FF)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SlatePrimary.copy(alpha = if (isDark) 0.22f else 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = SlatePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isBn) "৬৪ জেলা মিসইনফরমেশন উপদ্রব তালিকা" else "64 District Misinformation Hotspots",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            ),
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isBn) "কোন জেলায় কতটি গুজব ছড়ানো হয়েছে তার পূর্ণাঙ্গ বিশ্লেষণ" else "Full regional breakdown of rumor density by district",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlatePrimary)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isBn) "দেখুন" else "View",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = Color.White
                    )
                )
            }
        }
    }
}

/**
 * Attribution & public data disclaimer footnote displayed at the bottom of every tab.
 */
@Composable
fun RumorMeterAttributionFooter(
    isBn: Boolean,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) Color(0xFF0F172A).copy(alpha = 0.6f) else Color(0xFFF1F5F9).copy(alpha = 0.9f))
                .border(
                    1.dp,
                    if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (isBn) {
                    "স্বীকৃতি ও তথ্যসূত্র: এই প্ল্যাটফর্মের সকল তথ্য বাংলাদেশের নির্ভরযোগ্য ফ্যাক্টচেকিং প্রতিষ্ঠানসমূহের উন্মুক্ত ও প্রকাশিত আর্কাইভ থেকে সংগৃহীত। নিজস্ব কোনো তথ্য সংযোজন করা হয়নি।"
                } else {
                    "Data Attribution: All analytics and records are aggregated from publicly available archives of accredited Bangladeshi fact-checking organizations. No proprietary data is independently generated."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    )
                ),
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
            )
        }
    }
}

/**
 * Large Hero Speedometer Card showing National Rumor Intensity.
 */
@Composable
fun HeroGaugeCard(
    meterData: RumorMeterData,
    isBn: Boolean,
    isDark: Boolean
) {
    val score = meterData.meter.score.coerceIn(0, 100)
    val velocity = meterData.meter.dailyVelocity

    val threatLevel = when {
        score > 75 -> "HIGH"
        score > 40 -> "MODERATE"
        else -> "NORMAL"
    }

    val statusBadgeText = when (threatLevel) {
        "HIGH" -> getString("rumor_meter_high", isBn)
        "MODERATE" -> getString("rumor_meter_moderate", isBn)
        else -> getString("rumor_meter_normal", isBn)
    }

    val statusSubtitle = when (threatLevel) {
        "HIGH" -> getString("rumor_meter_status_high", isBn)
        "MODERATE" -> getString("rumor_meter_status_moderate", isBn)
        else -> getString("rumor_meter_status_low", isBn)
    }

    val statusColor = when (threatLevel) {
        "HIGH" -> Color(0xFFEF4444)
        "MODERATE" -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val cardBackground = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF0B1626), Color(0xFF070F1C)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFF0FDF4), Color(0xFFF8FAFC)))
    }

    val cardBorder = if (isDark) Color(0xFF17384E).copy(alpha = 0.85f) else Color(0xFFCBD5E1).copy(alpha = 0.9f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, cardBorder, RoundedCornerShape(20.dp)),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBackground)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Speedometer Arc
            Box(
                modifier = Modifier
                    .size(width = 220.dp, height = 150.dp),
                contentAlignment = Alignment.Center
            ) {
                SpeedometerGaugeArc(
                    score = score,
                    isDark = isDark,
                    modifier = Modifier.matchParentSize()
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 28.dp)
                ) {
                    Text(
                        text = BengaliNumberUtils.formatNumber(score, isBn),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 38.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            ),
                            lineHeight = 44.sp
                        ),
                        color = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
                    )

                    Text(
                        text = if (isBn) "জাতীয় সূচক /১০০" else "National Index /100",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.60f)
                    )
                }
            }

            // Status Pill + Subtitle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(statusColor.copy(alpha = if (isDark) 0.16f else 0.12f))
                    .border(1.dp, statusColor.copy(alpha = 0.60f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = statusBadgeText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        )
                    ),
                    color = statusColor
                )
            }

            Text(
                text = "$statusSubtitle • ${if (isBn) "দৈনিক গতিবেগ (গত ৭ দিন)" else "Daily Velocity (last 7 days)"}: ${BengaliNumberUtils.formatNumber(velocity, isBn, decimals = 1)} ${getString("rumor_meter_per_day", isBn)}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    )
                ),
                color = if (isDark) Color.White.copy(alpha = 0.75f) else Color(0xFF475569)
            )
        }
    }
}

/**
 * 4-Quad Grid for Article & Rumor Counters.
 */
@Composable
fun CountsQuadGrid(
    counts: com.example.data.model.MeterCounts,
    isBn: Boolean,
    isDark: Boolean
) {
    val items = listOf(
        (if (isBn) "আজকে শনাক্ত" else "Today") to counts.today,
        (if (isBn) "বিগত ৭ দিন" else "Last 7 Days") to counts.last7Days,
        (if (isBn) "বিগত ৩০ দিন" else "Last 30 Days") to counts.last30Days,
        (if (isBn) "সর্বমোট আর্কাইভ" else "Total Tracked") to counts.totalArticles
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.take(2).forEach { (label, value) ->
            Box(modifier = Modifier.weight(1f)) {
                StatCounterCard(label = label, value = value, isBn = isBn, isDark = isDark)
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.drop(2).forEach { (label, value) ->
            Box(modifier = Modifier.weight(1f)) {
                StatCounterCard(label = label, value = value, isBn = isBn, isDark = isDark)
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // 4 High-Value Intelligence Badges
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IntelligenceChip(
            title = if (isBn) "ইউনিক ক্লেইম" else "Unique Claims",
            count = counts.uniqueClaims,
            isBn = isBn,
            isDark = isDark,
            tint = Color(0xFF0284C7)
        )
        IntelligenceChip(
            title = getString("rumor_meter_zombie_chip", isBn),
            count = counts.zombieClaims,
            isBn = isBn,
            isDark = isDark,
            tint = Color(0xFFEF4444)
        )
        IntelligenceChip(
            title = getString("rumor_meter_cross_border_chip", isBn),
            count = counts.crossBorderClaims,
            isBn = isBn,
            isDark = isDark,
            tint = Color(0xFFF59E0B)
        )
        IntelligenceChip(
            title = getString("rumor_meter_gender_chip", isBn),
            count = counts.genderTargetedClaims,
            isBn = isBn,
            isDark = isDark,
            tint = Color(0xFF8B5CF6)
        )
    }
}

@Composable
fun StatCounterCard(
    label: String,
    value: Int,
    isBn: Boolean,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                RoundedCornerShape(14.dp)
            ),
        color = if (isDark) Color(0xFF0F172A).copy(alpha = 0.6f) else Color(0xFFFFFFFF)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    )
                ),
                color = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF64748B)
            )

            Text(
                text = "${BengaliNumberUtils.formatNumber(value, isBn)} ${if (isBn) "টি" else ""}",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    ),
                    lineHeight = 24.sp
                ),
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )
        }
    }
}

@Composable
fun IntelligenceChip(
    title: String,
    count: Int,
    isBn: Boolean,
    isDark: Boolean,
    tint: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = if (isDark) 0.15f else 0.10f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            color = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1E293B)
        )
        Text(
            text = BengaliNumberUtils.formatNumber(count, isBn),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            color = tint
        )
    }
}

/**
 * Stopword-Cleaned 2-Word Bigram Trending Narratives.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NarrativesSection(
    narratives: List<NarrativeItem>,
    isBn: Boolean,
    isDark: Boolean,
    onNarrativeClicked: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (isBn) "শীর্ষ ট্রেন্ডিং ন্যারেটিভ (সংবাদ ও সোশ্যাল মিডিয়া)" else "Trending Misinformation Narratives",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                ),
                lineHeight = 20.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            narratives.forEach { item ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.7f) else Color(0xFFF1F5F9))
                        .border(
                            1.dp,
                            if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onNarrativeClicked(item.narrative) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "# ${item.narrative}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = SlatePrimary
                    )
                    Text(
                        text = "(${BengaliNumberUtils.formatNumber(item.count, isBn)})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Mainstream Media Spread Distribution Tracker.
 */
@Composable
fun MediaSpreadSection(
    mediaSpread: List<MediaSpreadItem>,
    isBn: Boolean,
    isDark: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (isBn) "মূলধারার মিডিয়া সম্পৃক্ততা ট্র্যাকার" else "Mainstream Media Amplification Tracker",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                ),
                lineHeight = 20.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        val maxSpread = mediaSpread.maxOfOrNull { it.spreadCount.toIntOrNull() ?: 1 } ?: 1

        mediaSpread.take(6).forEach { item ->
            val count = item.spreadCount.toIntOrNull() ?: 0
            val fraction = (count.toFloat() / maxSpread.toFloat()).coerceIn(0.05f, 1f)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.mediaName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${BengaliNumberUtils.formatNumber(count, isBn)} ${if (isBn) "বার" else "times"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = Color(0xFFF97316)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFF97316))
                    )
                }
            }
        }
    }
}

/**
 * 17-Taxonomy Category Topic Breakdown.
 */
@Composable
fun TaxonomyTopicsSection(
    topics: List<TopicItem>,
    isBn: Boolean,
    isDark: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (isBn) "প্রমিত ১৭-ট্যাক্সোনমি ক্যাটাগরি শেয়ার" else "17-Taxonomy Category Distribution",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                ),
                lineHeight = 22.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        topics.forEach { topic ->
            val percentageFloat = (topic.percentage.toFloatOrNull() ?: 0f) / 100f
            val localizedName = formatTopicName(topic.primaryTopic, isBn)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = localizedName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "${BengaliNumberUtils.toBengaliDigits(topic.percentage)}% (${BengaliNumberUtils.formatNumber(topic.count.toIntOrNull() ?: 0, isBn)})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = SlatePrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percentageFloat.coerceIn(0.03f, 1f))
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SlatePrimary)
                    )
                }
            }
        }
    }
}

/**
 * Formats and Techniques Breakdown.
 */
@Composable
fun FormatsAndTechniquesSection(
    formats: List<com.example.data.model.FormatItem>,
    techniques: List<com.example.data.model.TechniqueItem>,
    isBn: Boolean,
    isDark: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (isBn) "গুজব তৈরির মাধ্যম ও কৌশল (Formats & Techniques)" else "Deception Formats & Techniques",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                ),
                lineHeight = 22.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Formats column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC))
                    .border(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isBn) "মাধ্যম (Format)" else "Format",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary,
                        fontSize = 12.sp
                    )
                )
                formats.take(5).forEach { f ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatFormatName(f.format, isBn),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)
                        )
                        Text(
                            text = BengaliNumberUtils.formatNumber(f.count.toIntOrNull() ?: 0, isBn),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Techniques column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC))
                    .border(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isBn) "কৌশল (Technique)" else "Technique",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp
                    )
                )
                techniques.take(5).forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTechniqueName(t.technique, isBn),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)
                        )
                        Text(
                            text = BengaliNumberUtils.formatNumber(t.count.toIntOrNull() ?: 0, isBn),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HotspotsHeader(isBn: Boolean) {
    Text(
        text = if (isBn) "৬৪ জেলা মিসইনফরমেশন উপদ্রব তালিকা (Hotspots)" else "64 District Misinformation Hotspots",
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None
            ),
            lineHeight = 22.sp
        ),
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun HotspotRowCard(
    hotspot: DistrictHotspotItem,
    isBn: Boolean,
    isDark: Boolean
) {
    val count = hotspot.rumorCount.toIntOrNull() ?: 0
    val displayName = if (isBn && hotspot.nameBn.isNotBlank()) hotspot.nameBn else hotspot.nameEn

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
        color = if (isDark) Color(0xFF0B1320) else Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = SlatePrimary,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${if (isBn) "বিভাগ:" else "Division:"} ${hotspot.division}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }

            Text(
                text = "${BengaliNumberUtils.formatNumber(count, isBn)} ${if (isBn) "টি গুজব" else "rumors"}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    )
                ),
                color = if (count > 5) Color(0xFFEF4444) else SlatePrimary
            )
        }
    }
}

/**
 * Filter and Search Bar for Canonical Claims Feed.
 */
@Composable
fun ClaimsFilterBar(
    searchQuery: String,
    activeFilter: String,
    isBn: Boolean,
    isDark: Boolean,
    onSearchChanged: (String) -> Unit,
    onFilterChanged: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = getString("rumor_meter_search_hint", isBn),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        )
                    )
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
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SlatePrimary,
                unfocusedBorderColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
            ),
            singleLine = true
        )

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "ALL" to (if (isBn) "সকল দাবি" else "All Claims"),
                "ZOMBIE" to getString("rumor_meter_zombie_chip", isBn),
                "CROSS_BORDER" to getString("rumor_meter_cross_border_chip", isBn),
                "GENDER_TARGETED" to getString("rumor_meter_gender_chip", isBn)
            )

            filters.forEach { (key, title) ->
                val isSelected = activeFilter == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) SlatePrimary else (if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)))
                        .clickable { onFilterChanged(key) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Individual Canonical Claim Item Card with Outlet References.
 */
@Composable
fun ClaimItemCard(
    claim: CanonicalClaimItem,
    isBn: Boolean,
    isDark: Boolean,
    onFactCheckClick: (String?) -> Unit
) {
    val displayClaim = if (isBn && !claim.canonicalClaimBn.isNullOrBlank()) {
        claim.canonicalClaimBn
    } else {
        claim.canonicalClaim
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
        color = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color.White
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (claim.primaryTopic != null) {
                        Text(
                            text = formatTopicName(claim.primaryTopic, isBn),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp
                            ),
                            color = SlatePrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SlatePrimary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (claim.isZombie) {
                        Text(
                            text = getString("rumor_meter_zombie_chip", isBn),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp
                            ),
                            color = Color(0xFFEF4444),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "${BengaliNumberUtils.formatNumber(claim.articleCount, isBn)} ${if (isBn) "টি ফ্যাক্টচেক" else "checks"}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                    )
                )
            }

            // Claim statement
            Text(
                text = displayClaim,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    ),
                    lineHeight = 19.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Fact Checking Outlets links
            if (claim.factChecks.isNotEmpty()) {
                HorizontalDivider(
                    color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                    thickness = 0.5.dp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBn) "যাচাইকারী:" else "Fact-checked by:",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )

                    claim.factChecks.forEach { fc ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                .clickable { onFactCheckClick(fc.url) }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = fc.author ?: "Fact-Checker",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.5.sp
                                ),
                                color = SlatePrimary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = SlatePrimary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTopicName(topic: String, isBn: Boolean): String {
    return when (topic.lowercase().trim()) {
        "politics" -> if (isBn) "রাজনীতি" else "Politics"
        "communal_religious", "religion" -> if (isBn) "সাম্প্রদায়িক ও ধর্মীয়" else "Communal & Religion"
        "health_medical", "health" -> if (isBn) "স্বাস্থ্য ও চিকিৎসা" else "Health & Medical"
        "education" -> if (isBn) "শিক্ষা ও পরীক্ষা" else "Education"
        "disaster_accident" -> if (isBn) "দুর্যোগ ও দুর্ঘটনা" else "Disaster & Accident"
        "fraud_scam" -> if (isBn) "প্রতারণা ও সাইবার অপরাধ" else "Fraud & Cybercrime"
        "entertainment" -> if (isBn) "বিনোদন ও সেলিব্রিটি" else "Entertainment"
        "sports" -> if (isBn) "খেলাধুলা" else "Sports"
        "international" -> if (isBn) "আন্তর্জাতিক বিষয়াবলি" else "International"
        "human_rights" -> if (isBn) "মানবাধিকার ও বিচার" else "Human Rights & Law"
        "gender_defamation" -> if (isBn) "লিঙ্গ ও চরিত্রহনন" else "Gender & Defamation"
        "science_tech" -> if (isBn) "বিজ্ঞান ও প্রযুক্তি" else "Science & Technology"
        "nature_environment", "environment_nature" -> if (isBn) "পরিবেশ ও প্রকৃতি" else "Environment & Nature"
        "economy_finance" -> if (isBn) "অর্থনীতি ও বাণিজ্য" else "Economy & Finance"
        "security_defense" -> if (isBn) "নিরাপত্তা ও প্রতিরক্ষা" else "Security & Defense"
        "social_customs" -> if (isBn) "সামাজিক প্রথা ও সংস্কৃতি" else "Social & Culture"
        else -> if (isBn) "অন্যান্য" else "Other"
    }
}

private fun formatFormatName(format: String, isBn: Boolean): String {
    return when (format.lowercase().trim()) {
        "photocard" -> if (isBn) "ফটোকার্ড" else "Photocard"
        "video" -> if (isBn) "ভিডিও" else "Video"
        "text_claim" -> if (isBn) "টেক্সট পোস্ট" else "Text Post"
        "audio" -> if (isBn) "অডিও" else "Audio"
        "document" -> if (isBn) "নথি/দলিল" else "Document"
        else -> format
    }
}

private fun formatTechniqueName(technique: String, isBn: Boolean): String {
    return when (technique.lowercase().trim()) {
        "fabricated_quote" -> if (isBn) "বানোয়াট উক্তি" else "Fabricated Quote"
        "ai_generated" -> if (isBn) "AI জেনারেটেড" else "AI-Generated"
        "unaltered_context" -> if (isBn) "বিভ্রান্তিকর প্রসঙ্গ" else "Misattributed Context"
        "deepfake" -> if (isBn) "ডিপফেক" else "Deepfake"
        "manipulated_media" -> if (isBn) "এডিটেড কন্টেন্ট" else "Altered Media"
        else -> technique.replace('_', ' ')
    }
}
