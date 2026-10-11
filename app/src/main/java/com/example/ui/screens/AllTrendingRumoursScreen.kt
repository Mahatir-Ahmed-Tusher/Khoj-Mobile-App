package com.example.ui.screens

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.SlatePrimary
import com.example.ui.viewmodel.TrendingRumoursState
import com.example.ui.viewmodel.TrendingRumoursViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTrendingRumoursScreen(
    isBn: Boolean,
    onBack: () -> Unit,
    viewModel: TrendingRumoursViewModel = viewModel()
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(isBn) {
        viewModel.onLanguageChanged(isBn)
    }

    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDark = (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val isRefreshing = state is TrendingRumoursState.Loading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
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
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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

                    Text(
                        text = if (isBn) "ট্রেন্ডিং রিউমার" else "Trending Rumours",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (isBn) "রিফ্রেশ" else "Refresh",
                            tint = SlatePrimary
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 0.5.dp
            )

            // ── Body Content with PullToRefreshBox ─────────────────────────
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                when (val s = state) {
                    is TrendingRumoursState.Loading -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(8) {
                                TrendingSkeletonLandscapeCard(isDark = isDark)
                            }
                        }
                    }

                    is TrendingRumoursState.Success -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = s.rumours,
                                key = { _, rumour -> rumour.url }
                            ) { index, rumour ->
                                TrendingRumourLandscapeCard(
                                    rumour = rumour,
                                    isBn = isBn,
                                    isDark = isDark,
                                    cardWidth = null, // Full width for vertical list
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
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    items = s.cachedRumours,
                                    key = { _, rumour -> rumour.url }
                                ) { index, rumour ->
                                    TrendingRumourLandscapeCard(
                                        rumour = rumour,
                                        isBn = isBn,
                                        isDark = isDark,
                                        cardWidth = null,
                                        index = index,
                                        onClick = {
                                            val intent = CustomTabsIntent.Builder().build()
                                            intent.launchUrl(context, Uri.parse(rumour.url))
                                        }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = s.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.size(12.dp))
                                    Button(
                                        onClick = { viewModel.refresh() },
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
        }
    }
}
