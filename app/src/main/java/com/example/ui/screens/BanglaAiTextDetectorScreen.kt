package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.R
import com.example.data.api.BanglaAiTextDetectionState
import com.example.data.model.BanglaTextDetectResponse
import com.example.data.model.BanglaWindowResult
import com.example.ui.theme.SlatePrimary
import com.example.ui.viewmodel.BanglaAiTextDetectorViewModel
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// BANGLA AI TEXT DETECTOR SCREEN
// Fully bilingual (Bengali & English), sleek mobile UX matching web reference.
// ─────────────────────────────────────────────────────────────────────────────

private fun formatNumberBn(value: Any?, isBn: Boolean): String {
    if (value == null) return ""
    val str = value.toString()
    if (!isBn) return str
    val bnDigits = listOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    return str.map { ch ->
        if (ch in '0'..'9') bnDigits[ch - '0'] else ch
    }.joinToString("")
}

private fun getWindowHeatmapColor(prob: Float): Color = when {
    prob >= 0.75f -> Color(0xFFEA580C) // Strong AI
    prob >= 0.50f -> Color(0xFFFB923C) // Leaning AI
    prob >= 0.30f -> Color(0xFF2DD4BF) // Leaning Human
    else          -> Color(0xFF0D9488) // Strong Human
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanglaAiTextDetectorScreen(
    onNavigateBack: () -> Unit,
    isBn: Boolean = true,
    fontFamily: FontFamily = FontFamily.Default,
    viewModel: BanglaAiTextDetectorViewModel = viewModel()
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val text by viewModel.text.collectAsStateWithLifecycle()
    val operatingMode by viewModel.operatingMode.collectAsStateWithLifecycle()
    val detectionState by viewModel.detectionState.collectAsStateWithLifecycle()

    val wordCount = viewModel.wordCount
    val charCount = viewModel.charCount
    val isShortText = viewModel.isShortText
    val isLongText = viewModel.isLongText
    val isLoading = detectionState is BanglaAiTextDetectionState.Loading

    var selectedWindowId by remember { mutableStateOf<Int?>(null) }
    var isModeDropdownOpen by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isModeDropdownOpen) 180f else 0f,
        label = "chevron_rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isBn) "AI লেখা যাচাই" else "AI Text Verify",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = fontFamily,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    if (text.isNotEmpty() || detectionState !is BanglaAiTextDetectionState.Idle) {
                        IconButton(onClick = { viewModel.clearAll() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = if (isBn) "মুছুন" else "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 1. Detection Mode Dropdown Card (Matches Screenshot) ────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = { isModeDropdownOpen = !isModeDropdownOpen },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color(0xFF1E232A) else Color.White,
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDark) Color(0xFF10283C) else Color(0xFFEBF6FD),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Scale,
                                        contentDescription = null,
                                        tint = SlatePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (isBn) "ডিটেকশন মোড" else "Detection Mode",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = fontFamily
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (operatingMode == "balanced") {
                                            if (isBn) "ব্যালেন্সড" else "Balanced"
                                        } else {
                                            if (isBn) "হাই একিউরেসি" else "High Precision"
                                        },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = fontFamily,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isDark) Color(0xFF263238) else Color(0xFFF1F5F9)
                                    ) {
                                        Text(
                                            text = if (operatingMode == "balanced") {
                                                if (isBn) "ডিফল্ট (τ = ০.৫০)" else "Default (τ = 0.50)"
                                            } else {
                                                if (isBn) "হার্ড (τ = ০.৬৫)" else "Strict (τ = 0.65)"
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = fontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation)
                        )
                    }
                }

                DropdownMenu(
                    expanded = isModeDropdownOpen,
                    onDismissRequest = { isModeDropdownOpen = false },
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .background(if (isDark) Color(0xFF1E232A) else Color.White)
                ) {
                    // Option 1: Balanced (Default)
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isBn) "ব্যালেন্সড" else "Balanced",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = fontFamily
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SlatePrimary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = if (isBn) "ডিফল্ট (τ = ০.৫০)" else "Default (τ = 0.50)",
                                            fontSize = 9.sp,
                                            color = SlatePrimary,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isBn) "দৈনন্দিন ও সাধারণ ব্যবহারের জন্য উপযুক্ত সুষম বিশ্লেষণ।"
                                    else "Balanced sensitivity for general editorial & daily writing.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = fontFamily
                                )
                            }
                        },
                        onClick = {
                            viewModel.onModeChange("balanced")
                            isModeDropdownOpen = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Scale,
                                contentDescription = null,
                                tint = if (operatingMode == "balanced") SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = if (operatingMode == "balanced") {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = SlatePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )

                    // Option 2: High Precision
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isBn) "হাই একিউরেসি" else "High Precision",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = fontFamily
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SlatePrimary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = if (isBn) "হার্ড (τ = ০.৬৫)" else "Strict (τ = 0.65)",
                                            fontSize = 9.sp,
                                            color = SlatePrimary,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isBn) "একদম নিশ্চিত না হয়ে কোনো সিদ্ধান্তে আসবে না (আইনি/প্রাতিষ্ঠানিক)।"
                                    else "Demands stronger evidence before flagging AI (academic/legal).",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = fontFamily
                                )
                            }
                        },
                        onClick = {
                            viewModel.onModeChange("high_precision")
                            isModeDropdownOpen = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = if (operatingMode == "high_precision") SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = if (operatingMode == "high_precision") {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = SlatePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }

            // ── 2. Text Input Card (Matches Screenshot Unified Style) ───────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E232A) else Color.White
                ),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
            ) {
                Column {
                    // Header Bar inside Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDark) Color(0xFF161B22) else Color(0xFFF8FAFC))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = SlatePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isBn) "বিশ্লেষণের জন্য বাংলা টেক্সট" else "Bengali Text to Analyze",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = fontFamily,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (text.isNotEmpty() && !isLoading) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { viewModel.onTextChange("") }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isBn) "মুছুন" else "Clear",
                                        fontSize = 11.sp,
                                        fontFamily = fontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = if (isBn) {
                                    "${formatNumberBn(wordCount, true)}টি শব্দ • ${formatNumberBn(charCount, true)}টি বর্ণ"
                                } else {
                                    "$wordCount words • $charCount chars"
                                },
                                fontSize = 10.5.sp,
                                fontFamily = fontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            )
                        }
                    }

                    // Borderless Text Area
                    TextField(
                        value = text,
                        onValueChange = { viewModel.onTextChange(it) },
                        placeholder = {
                            Text(
                                text = if (isBn) "যেকোনো বাংলা লেখা যাচাই করতে এখানে লিখুন বা পেস্ট করুন..."
                                else "Paste or type any Bengali text here to analyze...",
                                fontSize = 12.5.sp,
                                fontFamily = fontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 135.dp, max = 260.dp),
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            lineHeight = 21.sp,
                            fontFamily = fontFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )

                    // Context Warnings / Info Banners
                    if (isShortText) {
                        Surface(
                            color = if (isDark) Color(0xFF3B2B11) else Color(0xFFFFFBEB),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (isBn) "লেখাটা একটু ছোট (< ২৫ শব্দ): ছোট লেখার চেয়ে বড় লেখায় এআই ডিটেকশন অধিকতর নির্ভরযোগ্য হয়।"
                                    else "Brief text (< 25 words): Shorter passages contain fewer contextual markers.",
                                    fontSize = 10.sp,
                                    fontFamily = fontFamily,
                                    color = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                                )
                            }
                        }
                    }

                    if (isLongText) {
                        Surface(
                            color = if (isDark) Color(0xFF0C2A40) else Color(0xFFEFF6FF),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (isBn) "লেখাটা বড় (> ১২০ শব্দ): বাক্যভিত্তিক বহু-উইন্ডো বিভাজন স্বয়ংক্রিয়ভাবে সক্রিয় হবে।"
                                    else "Long document (> 120 words): Sentence-snapped multi-window inspection enabled.",
                                    fontSize = 10.sp,
                                    fontFamily = fontFamily,
                                    color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1E40AF)
                                )
                            }
                        }
                    }

                    // Card Footer with Bottom-Right Pill Action Button (Matches Screenshot)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDark) Color(0xFF161B22) else Color(0xFFFAFAFA))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.analyze() },
                            enabled = text.isNotBlank() && !isLoading,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBn) "লেখাটি যাচাই করুন" else "Analyze Text",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily
                            )
                        }
                    }
                }
            }

            // ── 3. Dynamic State Views (Loading / Error / Success) ───────────────
            when (val state = detectionState) {
                is BanglaAiTextDetectionState.Loading -> {
                    // Small Center Lottie Loading Animation as Requested
                    LottieLoadingCard(
                        isLongText = isLongText,
                        onCancel = { viewModel.cancelAnalysis() },
                        isBn = isBn,
                        fontFamily = fontFamily,
                        isDark = isDark
                    )
                }

                is BanglaAiTextDetectionState.Error -> {
                    ErrorView(
                        errorMessage = state.message,
                        onRetry = { viewModel.analyze() },
                        isBn = isBn,
                        fontFamily = fontFamily,
                        isDark = isDark
                    )
                }

                is BanglaAiTextDetectionState.Success -> {
                    SuccessResultView(
                        response = state.response,
                        selectedWindowId = selectedWindowId,
                        onSelectWindow = { windowId ->
                            selectedWindowId = if (selectedWindowId == windowId) null else windowId
                        },
                        onCopyReport = {
                            val copyText = buildString {
                                appendLine(if (isBn) "[খোঁজ AI লেখা যাচাই রিপোর্ট]" else "[Khoj AI Text Verify Report]")
                                appendLine("${if (isBn) "ফলাফল" else "Verdict"}: ${if (state.response.verdict == "AI-generated") (if (isBn) "এআই-দ্বারা তৈরি" else "AI-Generated") else (if (isBn) "মানুষের রচিত" else "Human-Written")}")
                                appendLine("${if (isBn) "এআই সম্ভাবনা" else "AI Probability"}: ${(state.response.aiProbability * 100).roundToInt()}%")
                                appendLine("${if (isBn) "মানব সম্ভাবনা" else "Human Probability"}: ${(state.response.humanProbability * 100).roundToInt()}%")
                                appendLine("${if (isBn) "নির্ভরযোগ্যতা" else "Confidence"}: ${state.response.confidenceLevel}")
                                appendLine("${if (isBn) "মোড" else "Mode"}: ${state.response.operatingMode} (τ = ${state.response.appliedThreshold})")
                                appendLine("${if (isBn) "শব্দসংখ্যা" else "Word Count"}: ${state.response.wordCount}")
                                appendLine("${if (isBn) "ইঞ্জিন" else "Engine"}: ${state.response.analysisEngine}")
                            }
                            clipboardManager.setText(AnnotatedString(copyText))
                            Toast.makeText(
                                context,
                                if (isBn) "রিপোর্ট কপি করা হয়েছে" else "Report copied to clipboard",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onReset = { viewModel.clearAll() },
                        isBn = isBn,
                        fontFamily = fontFamily,
                        isDark = isDark
                    )
                }

                is BanglaAiTextDetectionState.Idle -> {
                    // Educational card removed as requested
                }
            }

            // ── 4. Bottom Academic Disclaimer in Very Small Font ─────────────────
            Text(
                text = if (isBn) {
                    "পৃথিবীর কোনো মডেলই শতভাগ নিশ্চিতভাবে বলতে পারে না লেখা AI নাকি মানুষের। আমাদের মডেলও সম্ভাবনার ভিত্তিতে কাজ করে। তাই চূড়ান্ত সিদ্ধান্তের আগে লেখার প্রেক্ষাপট আর বিষয়বস্তু বিবেচনায় আনা উচিত।"
                } else {
                    "No model in the world can definitively determine whether text is AI-generated or human-written. Our model operates on probabilistic inference. Context and content should always be considered before reaching a final conclusion."
                },
                fontSize = 10.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontFamily = fontFamily,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOTTIE LOADING CARD (Disappears when finished)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LottieLoadingCard(
    isLongText: Boolean,
    onCancel: () -> Unit,
    isBn: Boolean,
    fontFamily: FontFamily,
    isDark: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1E232A) else Color(0xFFF8FAFC)
        ),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(R.raw.ai_detection_loading)
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

            Text(
                text = if (isBn) "বিশ্লেষণ চলছে..." else "Analyzing...",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (isLongText) {
                    if (isBn) "দীর্ঘ লেখার প্রতিটি উইন্ডো বিশ্লেষণ করা হচ্ছে..."
                    else "Analyzing multi-window document segments..."
                } else {
                    if (isBn) "ভাষার প্যাটার্ন ও কৃত্রিমতার লক্ষণ যাচাই করা হচ্ছে..."
                    else "Evaluating text patterns and statistical indicators..."
                },
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center
            )

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = if (isBn) "বাতিল" else "Cancel",
                    fontSize = 11.sp,
                    fontFamily = fontFamily
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ERROR VIEW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ErrorView(
    errorMessage: String,
    onRetry: () -> Unit,
    isBn: Boolean,
    fontFamily: FontFamily,
    isDark: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF381515) else Color(0xFFFEF2F2)
        ),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF7F1D1D) else Color(0xFFFECACA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(30.dp)
            )

            Text(
                text = if (isBn) "বিশ্লেষণে ত্রুটি ঘটেছে" else "Analysis Error",
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                fontFamily = fontFamily,
                color = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B)
            )

            Text(
                text = errorMessage,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = if (isDark) Color(0xFFF87171) else Color(0xFFB91C1C)
            )

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBn) "পুনরায় চেষ্টা করুন" else "Try Again",
                    fontSize = 11.5.sp,
                    fontFamily = fontFamily
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SUCCESS RESULT VIEW (Scaled down, compact fonts)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SuccessResultView(
    response: BanglaTextDetectResponse,
    selectedWindowId: Int?,
    onSelectWindow: (Int) -> Unit,
    onCopyReport: () -> Unit,
    onReset: () -> Unit,
    isBn: Boolean,
    fontFamily: FontFamily,
    isDark: Boolean
) {
    val isAi = response.verdict == "AI-generated"
    val aiPercent = (response.aiProbability * 100).roundToInt()
    val humanPercent = (response.humanProbability * 100).roundToInt()
    val isMultiWindow = response.analysisEngine == "long_doc_multi_window"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── 1. Primary Verdict Banner ────────────────────────────────────────
        val verdictBg = if (isAi) {
            if (isDark) Color(0xFF2D1600) else Color(0xFFFFF7ED)
        } else {
            if (isDark) Color(0xFF042F2E) else Color(0xFFF0FDFA)
        }
        val verdictBorder = if (isAi) {
            if (isDark) Color(0xFF7C2D12) else Color(0xFFFED7AA)
        } else {
            if (isDark) Color(0xFF115E59) else Color(0xFF99F6E4)
        }
        val badgeColor = if (isAi) Color(0xFFEA580C) else Color(0xFF0D9488)

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
                                imageVector = if (isAi) Icons.Default.SmartToy else Icons.Default.Person,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = fontFamily
                        )
                        Text(
                            text = if (isAi) {
                                if (isBn) "এআই-দ্বারা তৈরি" else "AI-Generated"
                            } else {
                                if (isBn) "মানুষের রচিত" else "Human-Written"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = fontFamily,
                            color = if (isAi) Color(0xFFEA580C) else Color(0xFF0D9488)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onCopyReport,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onReset,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ── 2. Probability Gauge Card ────────────────────────────────────────
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
                            fontFamily = fontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ConfidenceBadge(
                            confidence = response.confidenceLevel,
                            isBn = isBn,
                            fontFamily = fontFamily,
                            isDark = isDark
                        )
                    }

                    Text(
                        text = "Calibrated T ≈ 1.361",
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
                                    .weight(response.aiProbability.coerceAtLeast(0.01f))
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
                                    .weight(response.humanProbability.coerceAtLeast(0.01f))
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = fontFamily
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
                                text = if (isBn) "মানব সম্ভাবনা" else "Human Probability",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = fontFamily
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

        // ── 3. Technical Diagnostics Grid (2x2) ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiagnosticCard(
                modifier = Modifier.weight(1f),
                title = if (isBn) "ডিটেকশন মোড" else "Operating Mode",
                primaryValue = if (response.operatingMode == "balanced") {
                    if (isBn) "ব্যালেন্সড" else "Balanced"
                } else {
                    if (isBn) "হাই একিউরেসি" else "High Precision"
                },
                secondaryValue = "τ = ${formatNumberBn(response.appliedThreshold, isBn)}",
                icon = Icons.Default.Scale,
                fontFamily = fontFamily,
                isDark = isDark
            )

            DiagnosticCard(
                modifier = Modifier.weight(1f),
                title = if (isBn) "নথির আয়তন" else "Analyzed Volume",
                primaryValue = if (isBn) "${formatNumberBn(response.wordCount, true)}টি শব্দ" else "${response.wordCount} words",
                secondaryValue = if (isBn) "${formatNumberBn(response.characterCount, true)}টি বর্ণ" else "${response.characterCount} chars",
                icon = Icons.Default.Layers,
                fontFamily = fontFamily,
                isDark = isDark
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiagnosticCard(
                modifier = Modifier.weight(1f),
                title = if (isBn) "প্রক্রিয়াকরণ সময়" else "Processing Time",
                primaryValue = if (isBn) "${formatNumberBn(response.processingTimeMs.roundToInt(), true)} মি.সে." else "${response.processingTimeMs.roundToInt()} ms",
                secondaryValue = "Google Cloud Run",
                icon = Icons.Default.Speed,
                fontFamily = fontFamily,
                isDark = isDark
            )

            DiagnosticCard(
                modifier = Modifier.weight(1f),
                title = if (isBn) "বিশ্লেষণ প্রযুক্তি" else "Engine",
                primaryValue = if (isMultiWindow) {
                    if (isBn) "বহু-উইন্ডো বিভাজন" else "Multi-Window"
                } else {
                    if (isBn) "একক উইন্ডো" else "Single Window"
                },
                secondaryValue = if (isMultiWindow) {
                    if (isBn) "বাক্য বিভাজন বিশ্লেষণ" else "Sentence Sliding Window"
                } else {
                    if (isBn) "স্ট্যান্ডার্ড বিশ্লেষণ" else "Standard Window"
                },
                icon = Icons.Default.AutoAwesome,
                fontFamily = fontFamily,
                isDark = isDark
            )
        }

        // ── 4. Multi-Window Document Breakdown ───────────────────────────────
        if (isMultiWindow && !response.independentWindows.isNullOrEmpty()) {
            MultiWindowBreakdownSection(
                windows = response.independentWindows,
                consensusFraction = response.aiConsensusFraction,
                disagreementMad = response.disagreementMad,
                selectedWindowId = selectedWindowId,
                onSelectWindow = onSelectWindow,
                isBn = isBn,
                fontFamily = fontFamily,
                isDark = isDark
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MULTI-WINDOW BREAKDOWN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MultiWindowBreakdownSection(
    windows: List<BanglaWindowResult>,
    consensusFraction: Float?,
    disagreementMad: Float?,
    selectedWindowId: Int?,
    onSelectWindow: (Int) -> Unit,
    isBn: Boolean,
    fontFamily: FontFamily,
    isDark: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF161B22) else Color(0xFFF8FAFC)
        ),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF30363D) else Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isBn) "নথির অংশভিত্তিক উইন্ডো বিশ্লেষণ" else "Document Multi-Window Breakdown",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    fontFamily = fontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isBn) "দীর্ঘ লেখাটিকে স্বাধীন উইন্ডোতে বিভক্ত করে স্থানীয় কৃত্রিমতা মানচিত্র তৈরি করা হয়েছে।"
                    else "The long document was partitioned into sentence windows to analyze localized AI content.",
                    fontSize = 10.sp,
                    fontFamily = fontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Aggregate Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF1E232A) else Color.White,
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SlatePrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SlatePrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (isBn) "উইন্ডো ঐকমত্য" else "Consensus",
                                fontSize = 9.5.sp,
                                fontFamily = fontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (consensusFraction != null) {
                                    "${formatNumberBn((consensusFraction * 100).roundToInt(), isBn)}%"
                                } else "N/A",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF1E232A) else Color.White,
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF64748B).copy(alpha = 0.15f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Scale,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (isBn) "উইন্ডো পার্থক্য (MAD)" else "Disagreement MAD",
                                fontSize = 9.5.sp,
                                fontFamily = fontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (disagreementMad != null) {
                                    formatNumberBn(String.format("%.4f", disagreementMad), isBn)
                                } else "N/A",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Localization Heatmap
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBn) "কৃত্রিমতার অনুভূমিক মানচিত্র" else "Localization Heatmap",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = fontFamily,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isBn) "ট্যাপ করে উদ্ধৃতি দেখুন" else "Tap segment to inspect",
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val totalWords = windows.sumOf { it.wordCount }.coerceAtLeast(1).toFloat()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isDark) Color(0xFF0F141C) else Color(0xFFE2E8F0))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    windows.forEach { win ->
                        val weight = (win.wordCount / totalWords).coerceAtLeast(0.08f)
                        val isSelected = selectedWindowId == win.windowId
                        val segColor = getWindowHeatmapColor(win.aiProbability)

                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(segColor)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, Color.White, RoundedCornerShape(5.dp))
                                    } else Modifier
                                )
                                .clickable { onSelectWindow(win.windowId) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#${formatNumberBn(win.windowId, isBn)}",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0D9488))
                        )
                        Text(
                            text = if (isBn) "মানব-সম্ভাব্য (<৫০%)" else "Human Leaning (<50%)",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEA580C))
                        )
                        Text(
                            text = if (isBn) "এআই-সম্ভাব্য (≥৫০%)" else "AI Leaning (≥50%)",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Window Cards List
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                windows.forEach { win ->
                    val isSelected = selectedWindowId == win.windowId
                    val isWinAi = win.verdict == "AI-generated"
                    val winAiPercent = (win.aiProbability * 100).roundToInt()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E232A) else Color.White
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) SlatePrimary else if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                        )
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectWindow(win.windowId) }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9),
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = formatNumberBn(win.windowId, isBn),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isWinAi) {
                                            Color(0xFFEA580C).copy(alpha = 0.15f)
                                        } else {
                                            Color(0xFF0D9488).copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Text(
                                            text = if (isWinAi) {
                                                if (isBn) "এআই" else "AI"
                                            } else {
                                                if (isBn) "মানব" else "Human"
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = fontFamily,
                                            color = if (isWinAi) Color(0xFFEA580C) else Color(0xFF0D9488),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }

                                    Text(
                                        text = if (isBn) "${formatNumberBn(win.wordCount, true)}টি শব্দ" else "${win.wordCount} words",
                                        fontSize = 10.sp,
                                        fontFamily = fontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${formatNumberBn(winAiPercent, isBn)}% AI",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = getWindowHeatmapColor(win.aiProbability)
                                    )
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (isSelected) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (isDark) Color(0xFF14181F) else Color(0xFFFAFAFA),
                                    shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = if (isBn) "উইন্ডোর উদ্ধৃতি:" else "Window Excerpt:",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = fontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "\"${win.textExcerpt}\"",
                                            fontSize = 11.sp,
                                            fontStyle = FontStyle.Italic,
                                            lineHeight = 17.sp,
                                            fontFamily = fontFamily,
                                            color = MaterialTheme.colorScheme.onSurface
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
}

// ─────────────────────────────────────────────────────────────────────────────
// DIAGNOSTIC CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DiagnosticCard(
    modifier: Modifier = Modifier,
    title: String,
    primaryValue: String,
    secondaryValue: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    fontFamily: FontFamily,
    isDark: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SlatePrimary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontFamily = fontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = primaryValue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = secondaryValue,
                fontSize = 9.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CONFIDENCE BADGE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfidenceBadge(
    confidence: String,
    isBn: Boolean,
    fontFamily: FontFamily,
    isDark: Boolean
) {
    val (label, bg, textColor) = when (confidence.lowercase()) {
        "very_high" -> Triple(
            if (isBn) "অত্যন্ত উচ্চ" else "Very High",
            if (isDark) Color(0xFF052E16) else Color(0xFFF0FDF4),
            Color(0xFF16A34A)
        )
        "high" -> Triple(
            if (isBn) "উচ্চ" else "High",
            if (isDark) Color(0xFF0C2A40) else Color(0xFFF0F9FF),
            Color(0xFF0284C7)
        )
        "medium" -> Triple(
            if (isBn) "মাঝারি" else "Medium",
            if (isDark) Color(0xFF3B2B11) else Color(0xFFFFFBEB),
            Color(0xFFD97706)
        )
        else -> Triple(
            if (isBn) "নিম্ন" else "Low",
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
            fontFamily = fontFamily,
            color = textColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
        )
    }
}
