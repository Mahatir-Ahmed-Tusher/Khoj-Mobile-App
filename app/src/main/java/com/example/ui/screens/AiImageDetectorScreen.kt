package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.R
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import com.example.data.api.AiImageDetectionProgress
import com.example.data.model.ConsensusLevel
import com.example.data.model.EnsembleResult
import com.example.data.model.EngineResult
import com.example.data.model.EngineVerdict
import com.example.data.model.ImageVerdict
import com.example.ui.theme.ColorFalse
import com.example.ui.theme.ColorFalseContainer
import com.example.ui.theme.ColorTrue
import com.example.ui.theme.ColorTrueContainer
import com.example.ui.theme.ColorUnverifiable
import com.example.ui.theme.ColorUnverifiableContainer
import com.example.ui.theme.OnColorFalse
import com.example.ui.theme.OnColorTrue
import com.example.ui.theme.OnColorUnverifiable
import com.example.ui.theme.SlatePrimary
import com.example.ui.components.OfflineStateCard
import com.example.utils.NetworkErrorHelper
import com.example.ui.viewmodel.AiImageDetectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiImageDetectorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettingsControls: () -> Unit,
    isBn: Boolean,
    viewModel: AiImageDetectionViewModel = viewModel()
) {
    val detectionState  by viewModel.detectionState.collectAsStateWithLifecycle()
    val selectedImageUri by viewModel.selectedImageUri.collectAsStateWithLifecycle()
    
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Gallery picker — system photo picker, no CAMERA permission needed
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setSelectedImage(it) }
    }

    val isAnalyzing = detectionState is AiImageDetectionProgress.PreparingImage ||
            detectionState is AiImageDetectionProgress.RunningEngines

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isBn) "AI ছবি যাচাই" else "AI Image Detector",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = CircleShape
                            )
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = SlatePrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Image Picker Card ────────────────────────────────────────────
            ImagePickerCard(
                selectedUri  = selectedImageUri,
                isDark       = isDark,
                isAnalyzing  = isAnalyzing,
                isBn         = isBn,
                onTap        = { launcher.launch("image/*") }
            )

            // ── Gallery / Photo button row ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick  = { launcher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.6f))
                ) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = SlatePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isBn) "গ্যালারি" else "Gallery",
                        color = SlatePrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                OutlinedButton(
                    onClick  = { launcher.launch("image/*") }, // gallery only per spec
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isBn) "ছবি তুলুন" else "Camera",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            // ── State-driven content ──────────────────────────────────────────
            when (val state = detectionState) {
                is AiImageDetectionProgress.Idle -> {}
                is AiImageDetectionProgress.PreparingImage -> {
                    LoadingCard(
                        message = if (isBn) "ছবি প্রস্তুত হচ্ছে…" else "Preparing image…",
                        subMessage = if (isBn) "বিশ্লেষণের জন্য ছবি প্রস্তুত করা হচ্ছে" else "Preparing image for analysis",
                        isDark = isDark
                    )
                }
                is AiImageDetectionProgress.RunningEngines -> {
                    RunningEnginesCard(
                        isDark = isDark,
                        isBn = isBn
                    )
                }
                is AiImageDetectionProgress.Success -> {
                    ResultCard(
                        result   = state.result,
                        isDark   = isDark,
                        isBn     = isBn,
                        onReset  = { viewModel.clearImage() }
                    )
                }
                is AiImageDetectionProgress.Error -> {
                    ErrorCard(
                        message = state.message,
                        isDark  = isDark,
                        isBn    = isBn,
                        onRetry = {
                            selectedImageUri?.let { viewModel.analyzeImage(it) }
                        }
                    )
                }
            }

            // ── Analyze Button ────────────────────────────────────────────────
            val canAnalyze = selectedImageUri != null && !isAnalyzing &&
                    detectionState !is AiImageDetectionProgress.Success

            Button(
                onClick  = { selectedImageUri?.let { viewModel.analyzeImage(it) } },
                enabled  = canAnalyze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = SlatePrimary,
                    contentColor           = Color.White,
                    disabledContainerColor = SlatePrimary.copy(alpha = 0.35f),
                    disabledContentColor   = Color.White.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isBn) "ছবি বিশ্লেষণ করুন" else "Analyze Image",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IMAGE PICKER CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ImagePickerCard(
    selectedUri: Uri?,
    isDark: Boolean,
    isAnalyzing: Boolean,
    isBn: Boolean,
    onTap: () -> Unit
) {
    val borderColor = SlatePrimary.copy(alpha = if (isDark) 0.5f else 0.45f)
    val bgColor     = if (isDark) Color(0xFF0A0A0A) else Color(0xFFF3F7FF)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(enabled = !isAnalyzing) { onTap() },
        contentAlignment = Alignment.Center
    ) {
        // Dashed border drawn via Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val dashLength  = 10.dp.toPx()
            val gapLength   = 6.dp.toPx()
            drawRoundRect(
                color  = borderColor,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size   = Size(size.width - strokeWidth, size.height - strokeWidth),
                cornerRadius = CornerRadius(16.dp.toPx()),
                style  = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(dashLength, gapLength), 0f
                    )
                )
            )
        }

        if (selectedUri != null) {
            AsyncImage(
                model             = selectedUri,
                contentDescription = "Selected image",
                contentScale      = ContentScale.Crop,
                modifier          = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(SlatePrimary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = SlatePrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Text(
                    text = if (isBn) "ছবি বাছাই করুন" else "Select Image",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isBn) "JPG, PNG বা WEBP  •  সর্বোচ্চ ১০ MB" else "JPG, PNG or WEBP  •  Max 10 MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}




// ─────────────────────────────────────────────────────────────────────────────
// LOADING CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingCard(message: String, subMessage: String, isDark: Boolean) {
    Surface(
        shape  = RoundedCornerShape(14.dp),
        color  = if (isDark) Color(0xFF111111) else Color(0xFFF8F9FA),
        border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                color = SlatePrimary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = subMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RUNNING ENGINES CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RunningEnginesCard(
    isDark: Boolean,
    isBn: Boolean
) {
    Surface(
        shape  = RoundedCornerShape(14.dp),
        color  = if (isDark) Color(0xFF111111) else Color(0xFFF8F9FA),
        border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isBn) "বিশ্লেষণ চলছে…" else "Analysis in progress…",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(R.raw.loading)
            )
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = LottieConstants.IterateForever
            )

            Box(
                modifier = Modifier.size(90.dp),
                contentAlignment = Alignment.Center
            ) {
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RESULT CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultCard(
    result: EnsembleResult,
    isDark: Boolean,
    isBn: Boolean,
    onReset: () -> Unit
) {
    val allFailedNetwork = result.successfulEngines == 0 &&
            result.engines.isNotEmpty() &&
            result.engines.all { it.verdict == EngineVerdict.ERROR && NetworkErrorHelper.isNetworkException(Exception(it.error)) }

    if (allFailedNetwork) {
        OfflineStateCard(
            isBn = isBn,
            featureName = if (isBn) "AI ছবি যাচাই" else "AI Image Detector",
            onRetry = onReset,
            retryButtonText = if (isBn) "পুনরায় যাচাই করুন" else "Try Again"
        )
        return
    }

    val isAi = result.verdict == ImageVerdict.AI
    val isHuman = result.verdict == ImageVerdict.HUMAN
    val aiPercent = result.aiScore.roundToInt()
    val humanPercent = result.humanScore.roundToInt()

    // Verdict-specific card theme
    val verdictBg = when {
        isAi -> if (isDark) Color(0xFF2D1600) else Color(0xFFFFF7ED)
        isHuman -> if (isDark) Color(0xFF042F2E) else Color(0xFFF0FDFA)
        else -> if (isDark) Color(0xFF2D2500) else Color(0xFFFEFCE8)
    }
    val verdictBorder = when {
        isAi -> if (isDark) Color(0xFF7C2D12) else Color(0xFFFED7AA)
        isHuman -> if (isDark) Color(0xFF115E59) else Color(0xFF99F6E4)
        else -> if (isDark) Color(0xFF713F12) else Color(0xFFFEF08A)
    }
    val badgeColor = when {
        isAi -> Color(0xFFEA580C)
        isHuman -> Color(0xFF0D9488)
        else -> Color(0xFFCA8A04)
    }
    val verdictTitle = when {
        isAi -> if (isBn) "এআই জেনারেটেড" else "AI-Generated"
        isHuman -> if (isBn) "বাস্তব ছবি" else "Real Image"
        else -> if (isBn) "অনিশ্চিত" else "Uncertain"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── 1. Primary Verdict Card (Adapted from AI Text Detector) ──────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = verdictBg),
            border = BorderStroke(1.dp, verdictBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeColor,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isAi -> Icons.Default.SmartToy
                                    isHuman -> Icons.Default.Person
                                    else -> Icons.Default.HelpOutline
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (isBn) "চূড়ান্ত সিদ্ধান্ত" else "Final Verdict",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = verdictTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeColor
                        )
                    }
                }

                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ── 2. Probability & Confidence Gauge Card ───────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isBn) "নির্ভরযোগ্যতা:" else "Confidence:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ConfidenceBadge(
                            confidencePercent = result.confidence,
                            consensusLevel = result.consensusLevel,
                            isBn = isBn,
                            isDark = isDark
                        )
                    }

                    Text(
                        text = if (isBn) "বিশ্লেষণ সময়: ${formatNumberBn(result.analysisTimeMs, isBn)} ms" else "Analysis: ${result.analysisTimeMs} ms",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }

                // Dual Color Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (aiPercent > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(result.aiScore.coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(Color(0xFFEA580C)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (aiPercent >= 18) {
                                    Text(
                                        text = "${formatNumberBn(aiPercent, isBn)}%",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        if (humanPercent > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(result.humanScore.coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(Color(0xFF0D9488)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (humanPercent >= 18) {
                                    Text(
                                        text = "${formatNumberBn(humanPercent, isBn)}%",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Numbers row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFEA580C).copy(alpha = 0.15f),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = Color(0xFFEA580C),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (isBn) "এআই সম্ভাবনা" else "AI Probability",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${formatNumberBn(aiPercent, isBn)}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEA580C)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0D9488).copy(alpha = 0.15f),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF0D9488),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isBn) "বাস্তব সম্ভাবনা" else "Real Probability",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${formatNumberBn(humanPercent, isBn)}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D9488)
                            )
                        }
                    }
                }
            }
        }

        // ── 3. Reset Button ───────────────────────────────────────────────────
        OutlinedButton(
            onClick  = onReset,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            border   = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = SlatePrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (isBn) "আরেকটি ছবি যাচাই করুন" else "Check Another Image", color = SlatePrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ConfidenceBadge(
    confidencePercent: Float,
    consensusLevel: ConsensusLevel,
    isBn: Boolean,
    isDark: Boolean
) {
    val (label, bg, textColor) = when {
        confidencePercent >= 85f || consensusLevel == ConsensusLevel.STRONG -> Triple(
            if (isBn) "অত্যন্ত উচ্চ" else "Very High",
            if (isDark) Color(0xFF052E16) else Color(0xFFF0FDF4),
            Color(0xFF16A34A)
        )
        confidencePercent >= 70f || consensusLevel == ConsensusLevel.MODERATE -> Triple(
            if (isBn) "উচ্চ" else "High",
            if (isDark) Color(0xFF0C2A40) else Color(0xFFF0F9FF),
            Color(0xFF0284C7)
        )
        confidencePercent >= 50f || consensusLevel == ConsensusLevel.WEAK -> Triple(
            if (isBn) "মাঝারি" else "Medium",
            if (isDark) Color(0xFF3B2B11) else Color(0xFFFFFBEB),
            Color(0xFFD97706)
        )
        else -> Triple(
            if (isBn) "কম" else "Low",
            if (isDark) Color(0xFF381515) else Color(0xFFFEF2F2),
            Color(0xFFDC2626)
        )
    }

    Surface(
        shape = RoundedCornerShape(5.dp),
        color = bg,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
        )
    }
}

private fun formatNumberBn(value: Any?, isBn: Boolean): String {
    if (value == null) return ""
    val str = value.toString()
    if (!isBn) return str
    val bnDigits = listOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    return str.map { ch ->
        if (ch in '0'..'9') bnDigits[ch - '0'] else ch
    }.joinToString("")
}

// ─────────────────────────────────────────────────────────────────────────────
// ERROR CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorCard(
    message: String,
    isDark: Boolean,
    isBn: Boolean,
    onRetry: () -> Unit
) {
    val isNetworkErr = NetworkErrorHelper.isNetworkException(Exception(message))
    if (isNetworkErr) {
        OfflineStateCard(
            isBn = isBn,
            featureName = if (isBn) "AI ছবি যাচাই" else "AI Image Detector",
            onRetry = onRetry
        )
    } else {
        Surface(
            shape  = RoundedCornerShape(14.dp),
            color  = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = if (isBn) "বিশ্লেষণে সমস্যা হয়েছে" else "Analysis Failed",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRetry,
                    shape  = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isBn) "আবার চেষ্টা করুন" else "Retry", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
