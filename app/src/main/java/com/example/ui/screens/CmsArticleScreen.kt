package com.example.ui.screens

import android.content.Intent
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.example.data.model.CmsArticle
import kotlinx.coroutines.delay
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CmsArticleScreen(
    article: CmsArticle,
    isBn: Boolean,
    bengaliFont: String = "tiro",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var fullArticle by remember(article.id) { mutableStateOf(article) }
    var isLoading by remember(article.id) { mutableStateOf(article.content.isBlank()) }

    LaunchedEffect(article.id) {
        if (article.content.isBlank()) {
            isLoading = true
            val loaded = com.example.data.repository.FactCheckRepository.getFullCmsArticle(article)
            fullArticle = loaded
            isLoading = false
        } else {
            fullArticle = article
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Crossfade(
            targetState = isLoading,
            animationSpec = tween(400),
            modifier = Modifier.fillMaxSize()
        ) { loading ->
            if (loading) {
                CmsArticleSkeleton(padding = PaddingValues(0.dp))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header Image Container with Floating Overlays
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    ) {
                        AsyncImage(
                            model = fullArticle.thumbnailUrl,
                            contentDescription = fullArticle.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                        )

                        // Floating Back Button over Thumbnail
                        FilledTonalIconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(start = 14.dp, top = 14.dp)
                                .size(40.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.45f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isBn) "ফিরে যান" else "Back",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Floating Share Button over Thumbnail
                        FilledTonalIconButton(
                            onClick = {
                                val shareType = when {
                                    fullArticle.isMythbusting -> if (isBn) "গুজবখন্ডন রিপোর্ট" else "mythbusting report"
                                    fullArticle.isMuktijoddha -> if (isBn) "মুক্তিযুদ্ধ কর্নার রিপোর্ট" else "Liberation War Corner report"
                                    else -> if (isBn) "ফ্যাক্টচেক" else "fact-check"
                                }
                                val sharePath = when {
                                    fullArticle.isMythbusting -> "mythbusting"
                                    fullArticle.isMuktijoddha -> "mukti-corner"
                                    else -> "factchecks"
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, fullArticle.title)
                                    putExtra(Intent.EXTRA_TEXT, "Check out this $shareType: https://khoj-ai.bd/$sharePath/${fullArticle.slug}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, if (isBn) "শেয়ার করুন" else "Share via"))
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(end = 14.dp, top = 14.dp)
                                .size(40.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.45f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = if (isBn) "শেয়ার করুন" else "Share",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Fact-Check / Mythbusting / Muktijoddha Badge Chip
                        val isMythbusting = fullArticle.isMythbusting
                        val isMuktijoddha = fullArticle.isMuktijoddha
                        val badgeColor = when {
                            isMythbusting -> Color(0xFF16A34A)
                            isMuktijoddha -> Color(0xFF15803D)
                            else -> Color(0xFF0284C7)
                        }
                        Surface(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isMuktijoddha) {
                                    Image(
                                        painter = painterResource(id = com.example.R.drawable.mukti_icon),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                badgeColor, 
                                                shape = CircleShape
                                            )
                                    )
                                }
                                @Suppress("DEPRECATION")
                                Text(
                                    text = when {
                                        isMythbusting -> if (isBn) "গুজবখন্ডন রিপোর্ট" else "Mythbusting Report"
                                        isMuktijoddha -> if (isBn) "মুক্তিযুদ্ধ কর্নার" else "Liberation War Corner"
                                        else -> if (isBn) "ফ্যাক্টচেক রিপোর্ট" else "Fact-Check Report"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
                                        )
                                    ),
                                    color = badgeColor
                                )
                            }
                        }

                        Text(
                            text = fullArticle.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 30.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        // Meta row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = fullArticle.author,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = fullArticle.publishedAt.take(10),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

                        // HTML Content Rendering with Image Support
                        com.example.ui.components.CmsHtmlContentRenderer(
                            htmlContent = fullArticle.content.ifBlank { fullArticle.excerpt ?: "" },
                            isBn = isBn,
                            bengaliFont = bengaliFont,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

                        // Bottom Share Card
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    @Suppress("DEPRECATION")
                                    Text(
                                        text = when {
                                            isMythbusting -> if (isBn) "গুজবখন্ডন রিপোর্টটি শেয়ার করুন" else "Share Mythbusting Report"
                                            isMuktijoddha -> if (isBn) "মুক্তিযুদ্ধ কর্নার রিপোর্টটি শেয়ার করুন" else "Share Liberation War Corner Report"
                                            else -> if (isBn) "ফ্যাক্টচেক রিপোর্টটি শেয়ার করুন" else "Share Fact-Check Report"
                                        },
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true),
                                            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
                                            )
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    @Suppress("DEPRECATION")
                                    Text(
                                        text = when {
                                            isMuktijoddha -> if (isBn) "মুক্তিযুদ্ধের সঠিক ইতিহাস ছড়িয়ে দিন" else "Spread the true history of the Liberation War"
                                            else -> if (isBn) "ভুয়া তথ্য প্রতিরোধে সাহায্য করুন" else "Help prevent misinformation spread"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true),
                                            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
                                            )
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        val shareType = when {
                                            isMythbusting -> if (isBn) "গুজবখন্ডন রিপোর্ট" else "mythbusting report"
                                            isMuktijoddha -> if (isBn) "মুক্তিযুদ্ধ কর্নার রিপোর্ট" else "Liberation War Corner report"
                                            else -> if (isBn) "ফ্যাক্টচেক" else "fact-check"
                                        }
                                        val sharePath = when {
                                            isMythbusting -> "mythbusting"
                                            isMuktijoddha -> "mukti-corner"
                                            else -> "factchecks"
                                        }
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, fullArticle.title)
                                            putExtra(Intent.EXTRA_TEXT, "Check out this $shareType: https://khoj-ai.bd/$sharePath/${fullArticle.slug}")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, if (isBn) "শেয়ার করুন" else "Share via"))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    @Suppress("DEPRECATION")
                                    Text(
                                        text = if (isBn) "শেয়ার" else "Share",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CmsArticleSkeleton(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // Header image placeholder with rounded bottom corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                .cmsShimmerEffect()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Badge chip skeleton
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .cmsShimmerEffect()
            )

            // Title skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .cmsShimmerEffect()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .cmsShimmerEffect()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .cmsShimmerEffect()
            )

            // Meta skeleton
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .cmsShimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .cmsShimmerEffect()
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

            // Content body skeleton blocks
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .cmsShimmerEffect()
            )
            Spacer(modifier = Modifier.height(2.dp))

            repeat(4) { idx ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (idx == 3) 0.75f else 1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .cmsShimmerEffect()
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .cmsShimmerEffect()
            )
            Spacer(modifier = Modifier.height(2.dp))

            repeat(4) { idx ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (idx == 3) 0.65f else 1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .cmsShimmerEffect()
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

            // Bottom Share card skeleton
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .cmsShimmerEffect()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .cmsShimmerEffect()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .cmsShimmerEffect()
                    )
                }
            }
        }
    }
}

