package com.example.ui.screens

import android.content.Intent
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
fun BlogArticleScreen(
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
                BlogArticleSkeleton(padding = PaddingValues(0.dp))
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
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, fullArticle.title)
                                    putExtra(Intent.EXTRA_TEXT, "Read this blog post: https://khoj-ai.bd/blog/${fullArticle.slug}")
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
                        // Category Chip
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = if (isBn) "ব্লগ পোস্ট" else "Blog Post",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
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
                                    Text(
                                        text = if (isBn) "ব্লগটি ভালো লেগেছে?" else "Enjoyed this article?",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isBn) "অন্যদের সাথে শেয়ার করুন" else "Share with your network",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, fullArticle.title)
                                            putExtra(Intent.EXTRA_TEXT, "Read this blog post: https://khoj-ai.bd/blog/${fullArticle.slug}")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, if (isBn) "শেয়ার করুন" else "Share via"))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isBn) "শেয়ার" else "Share")
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
fun BlogArticleSkeleton(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // Edge-to-edge Header Thumbnail placeholder with rounded bottom corners
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
            // Category Chip skeleton
            Box(
                modifier = Modifier
                    .width(90.dp)
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
                    .fillMaxWidth(0.85f)
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

            // Content Body skeleton blocks
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

