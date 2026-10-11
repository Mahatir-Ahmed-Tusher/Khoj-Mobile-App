package com.example.overlay

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.BanglaAiTextDetectionState
import com.example.ui.screens.ErrorView
import com.example.ui.screens.LottieLoadingCard
import com.example.ui.screens.SuccessResultView
import com.example.ui.theme.SlatePrimary
import kotlin.math.roundToInt

/**
 * Bottom drawer for displaying Bangla AI Text Detection directly over other apps.
 * Matches the UI, animations, and diagnostics of BanglaAiTextDetectorScreen.
 */
@Composable
fun OverlayAiTextDrawer(
    state: BanglaAiTextDetectionState,
    inputText: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    isBn: Boolean,
    fontFamily: FontFamily,
    isDark: Boolean
) {
    val drawerShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedWindowId by remember { mutableStateOf<Int?>(null) }

    val wordCount = remember(inputText) {
        inputText.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    }
    val isLongText = wordCount > 120

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim background - tap outside to dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        )

        // Drawer card anchored at bottom
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.80f)
                .align(Alignment.BottomCenter)
                .clip(drawerShape),
            shape = drawerShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag handle bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEA580C).copy(alpha = 0.12f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFEA580C),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Text(
                            text = if (isBn) "AI লেখা যাচাই" else "AI Text Detector",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = fontFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SlatePrimary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = if (isBn) "ক্লিপবোর্ড" else "Clipboard",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlatePrimary,
                                fontFamily = fontFamily,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Scrollable content body
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AnimatedContent(
                        targetState = state,
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
                        label = "ai_drawer_body"
                    ) { currentState ->
                        when (currentState) {
                            is BanglaAiTextDetectionState.Loading -> {
                                LottieLoadingCard(
                                    isLongText = isLongText,
                                    onCancel = onCancel,
                                    isBn = isBn,
                                    fontFamily = fontFamily,
                                    isDark = isDark
                                )
                            }

                            is BanglaAiTextDetectionState.Error -> {
                                ErrorView(
                                    errorMessage = currentState.message,
                                    onRetry = onRetry,
                                    isBn = isBn,
                                    fontFamily = fontFamily,
                                    isDark = isDark
                                )
                            }

                            is BanglaAiTextDetectionState.Success -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SuccessResultView(
                                        response = currentState.response,
                                        selectedWindowId = selectedWindowId,
                                        onSelectWindow = { windowId ->
                                            selectedWindowId = if (selectedWindowId == windowId) null else windowId
                                        },
                                        onCopyReport = {
                                            val copyText = buildString {
                                                appendLine(if (isBn) "[খোঁজ AI লেখা যাচাই রিপোর্ট]" else "[Khoj AI Text Verify Report]")
                                                appendLine("${if (isBn) "ফলাফল" else "Verdict"}: ${if (currentState.response.verdict == "AI-generated") (if (isBn) "এআই-দ্বারা তৈরি" else "AI-Generated") else (if (isBn) "মানুষের রচিত" else "Human-Written")}")
                                                appendLine("${if (isBn) "এআই সম্ভাবনা" else "AI Probability"}: ${(currentState.response.aiProbability * 100).roundToInt()}%")
                                                appendLine("${if (isBn) "মানব সম্ভাবনা" else "Human Probability"}: ${(currentState.response.humanProbability * 100).roundToInt()}%")
                                                appendLine("${if (isBn) "নির্ভরযোগ্যতা" else "Confidence"}: ${currentState.response.confidenceLevel}")
                                                appendLine("${if (isBn) "মোড" else "Mode"}: ${currentState.response.operatingMode} (τ = ${currentState.response.appliedThreshold})")
                                                appendLine("${if (isBn) "শব্দসংখ্যা" else "Word Count"}: ${currentState.response.wordCount}")
                                                appendLine("${if (isBn) "ইঞ্জিন" else "Engine"}: ${currentState.response.analysisEngine}")
                                            }
                                            clipboardManager.setText(AnnotatedString(copyText))
                                            Toast.makeText(
                                                context,
                                                if (isBn) "রিপোর্ট কপি করা হয়েছে" else "Report copied to clipboard",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onReset = onRetry,
                                        isBn = isBn,
                                        fontFamily = fontFamily,
                                        isDark = isDark
                                    )

                                    // Academic Disclaimer in drawer
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
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            is BanglaAiTextDetectionState.Idle -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isBn) "কোনো ফলাফল নেই" else "No results",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        fontFamily = fontFamily
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
