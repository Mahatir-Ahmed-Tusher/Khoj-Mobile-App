package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.data.api.FactCheckEngine
import com.example.data.model.FactCheckReport
import com.example.data.model.FactCheckSource
import com.example.data.repository.AuthRepository
import com.example.R
import com.example.ui.theme.*
import com.example.ui.viewmodel.FactCheckViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReportScreen(
    report: FactCheckReport,
    viewModel: FactCheckViewModel,
    onPrivacyChanged: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val bengaliFontPreference by viewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    val fontToUse = if (isBn) {
        if (bengaliFontPreference == "anek") AnekBanglaFamily else TiroBanglaFamily
    } else {
        FontFamily.Default
    }

    val titleText = getString("report_title", isBn)
    val claimLabel = getString("claim_label", isBn)
    val confidenceLabel = getString("confidence_label", isBn)
    val sourcesLabel = getString("sources_label", isBn)
    val contradictingLabel = getString("contradicting_label", isBn)

    val verdictText = when (report.verdict.lowercase()) {
        "true" -> getString("true", isBn)
        "false" -> getString("false", isBn)
        else -> getString("misleading", isBn)
    }

    // Extract sources
    val engine = remember { FactCheckEngine(context) }
    val sources = remember(report) { engine.deserializeSources(report.sourcesJson) }
    val contradictions = remember(report) { engine.deserializeContradictions(report.contradictingSourcesJson) }

    var showShareModal by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Original Claim Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = claimLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = report.claimText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 3-way Verdict Badge Card
            val verdictColor = when (report.verdict.lowercase()) {
                "true" -> Color(0xFF16A34A)
                "false" -> Color(0xFFDC2626)
                else -> Color(0xFFD97706)
            }

            val lottieRes = when (report.verdict.lowercase()) {
                "true" -> R.raw.anim_true
                "false" -> R.raw.anim_false
                "misleading" -> R.raw.anim_misleading
                else -> R.raw.anim_misleading
            }
            
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(lottieRes))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(verdictColor.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        LottieAnimation(
                            composition = composition,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Column {
                        Text(
                            text = getString("khoj_verdict", isBn),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = verdictText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = verdictColor
                        )
                    }
                }
            }

            // Confidence card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = confidenceLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${report.confidence}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = verdictColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = report.confidence / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = verdictColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                }
            }

            // Privacy Switch Card (only if the user is the owner of the report)
            val currentUserId = AuthRepository.currentUserId
            val isOwner = !currentUserId.isNullOrEmpty() && report.userId == currentUserId

            if (isOwner) {
                var isPublicStatus by remember(report.id) { mutableStateOf(report.isPublic) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Globe or Lock icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isPublicStatus) Color(0xFFE2F6EA) else Color(0xFFFEF3C7),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPublicStatus) Icons.Default.Language else Icons.Default.Lock,
                                    contentDescription = "Privacy Icon",
                                    tint = if (isPublicStatus) Color(0xFF16A34A) else Color(0xFFD97706),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (isPublicStatus) {
                                        if (isBn) "পাবলিক রিপোর্ট" else "Public Report"
                                    } else {
                                        if (isBn) "প্রাইভেট রিপোর্ট" else "Private Report"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isPublicStatus) {
                                        if (isBn) "সবাই দেখতে পারবে" else "Everyone can see"
                                    } else {
                                        if (isBn) "শুধু আপনি দেখতে পারবেন" else "Only you can see"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isPublicStatus,
                            onCheckedChange = { checked ->
                                isPublicStatus = checked
                                viewModel.updateReportPrivacy(report.id, checked) {
                                    onPrivacyChanged()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981), // Emerald green matching screenshot
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }

            // ── Unified Markdown Report Body ────────────────────────────────────────
            // This is the single source of truth for the report body across all entry points:
            // new result, history, feed. Uses getEffectiveMarkdown() which handles both new
            // (markdownReport) and legacy (summaryBn + reasoningBn) reports gracefully.
            UnifiedFactCheckReportView(
                report = report,
                sources = sources,
                fontToUse = fontToUse,
                modifier = Modifier.fillMaxWidth()
            )

            // Contradicting evidence section (Transparent design)
            if (contradictions.isNotEmpty()) {
                val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ColorFalseContainer.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Contradict", tint = if (isDarkTheme) Color(0xFFFF5252) else ColorFalse)
                            Text(
                                text = contradictingLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFFFF5252) else OnColorFalse
                            )
                        }

                        contradictions.forEach { contra ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = contra.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDarkTheme) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                                if (contra.url.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(contra.url))
                                            context.startActivity(intent)
                                        },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Link, contentDescription = "Link", modifier = Modifier.size(14.dp), tint = SlatePrimary)
                                        Text(
                                            text = contra.title.ifEmpty { getString("open_contra", isBn) },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlatePrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Clickable Citations & Sources list
            Text(
                text = sourcesLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (sources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getString("no_links", isBn),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                sources.forEachIndexed { idx, source ->
                    SourceCardItem(source = source, index = idx + 1, isBn = isBn)
                }
            }

            // Re-check button
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary)
            ) {
                Icon(Icons.Default.Autorenew, contentDescription = "Retry")
                Spacer(modifier = Modifier.width(8.dp))
                Text(getString("verify_new", isBn))
            }
        }

        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val coroutineScope = rememberCoroutineScope()
        
        val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
        val offsetY = remember { androidx.compose.animation.core.Animatable(with(density) { 100.dp.toPx() }) }

        // Share FAB
        FloatingActionButton(
            onClick = { showShareModal = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
                                val fabAndPadding = with(density) { (56.dp + 32.dp).toPx() } 
                                val threshold = -(screenWidthPx - fabAndPadding) / 2f
                                
                                val targetX = if (offsetX.value > threshold) 0f else -(screenWidthPx - fabAndPadding)
                                offsetX.animateTo(targetX, androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
                            }
                        }
                    ) { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                        change.consume()
                        coroutineScope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    }
                },
            containerColor = SlatePrimary,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_share_custom),
                contentDescription = "Share",
                modifier = Modifier.size(24.dp)
            )
        }

        // Glassmorphic Share Modal Popup
        if (showShareModal) {
            val reportUrl = "https://khoj-ai.bd/factcheck-detail?id=${report.id}"
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
                                // FB Messenger
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
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Fact Check Report Link", reportUrl)
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
}

@Composable
fun SourceCardItem(source: FactCheckSource, index: Int, isBn: Boolean) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (source.url.isNotEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast
                            .makeText(context, if (isBn) "লিঙ্ক খোলা সম্ভব হচ্ছে না" else "Cannot open link", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(SlateSecondary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = index.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateSecondary
                        )
                    }
                    val faviconUrl = if (source.domain.isNotEmpty()) {
                        "https://www.google.com/s2/favicons?sz=64&domain=${source.domain}"
                    } else {
                        "https://www.google.com/s2/favicons?sz=64&domain_url=${source.url}"
                    }
                    AsyncImage(
                        model = faviconUrl,
                        contentDescription = "Favicon",
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = source.title.ifEmpty { getString("authoritative_source", isBn) },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Tier badge
                val tierText = if (isBn) "ধাপ ${source.credibilityTier}" else "Tier ${source.credibilityTier}"
                val tierColor = when (source.credibilityTier) {
                    1 -> ColorTrue
                    2 -> SlateTertiary
                    3 -> SlateSecondary
                    else -> Color.Gray
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tierColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = tierText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = tierColor)
                }
            }

            Text(
                text = source.snippet.ifEmpty { getString("source_details", isBn) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = source.domain.ifEmpty { "web-source" },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlatePrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        Icons.Default.Launch,
                        contentDescription = "Open",
                        modifier = Modifier.size(12.dp),
                        tint = SlatePrimary
                    )
                    Text(
                        text = getString("open_link", isBn),
                        fontSize = 10.sp,
                        color = SlatePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
