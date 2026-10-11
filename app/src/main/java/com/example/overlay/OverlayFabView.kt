package com.example.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.SlatePrimary
import kotlin.math.abs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.Painter
import com.example.ui.theme.SlateDarkBackground
import com.example.ui.theme.SlateSurface

/**
 * Draggable floating action button with a 4-action menu.
 *
 * Supports dynamic language (Bengali / English) and dark/light theming.
 * Uses custom drawable assets for Select/Clipboard, Screenshot, and Open Khoj,
 * while preserving the native microphone vector icon.
 */
@Composable
fun OverlayFabView(
    menuExpanded: Boolean,
    isBn: Boolean = false,
    isDark: Boolean = false,
    onCloseMenu: () -> Unit,
    onSelectAction: () -> Unit,
    onScreenshotAction: () -> Unit,
    onMicrophoneAction: () -> Unit,
    onKhojChatAction: () -> Unit,
    onSearchAction: () -> Unit
) {
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var isIdle by remember { mutableStateOf(false) }

    LaunchedEffect(lastInteractionTime, menuExpanded) {
        if (menuExpanded) {
            isIdle = false
        } else {
            isIdle = false
            kotlinx.coroutines.delay(2000)
            isIdle = true
        }
    }

    val fabAlpha by animateFloatAsState(targetValue = if (isIdle && !menuExpanded) 0.5f else 1.0f)

    // Column stacks the menu ABOVE the FAB — window WRAP_CONTENT resizes to fit both.
    Column(
        modifier = Modifier.padding(8.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        // ── 4-Action menu ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = menuExpanded,
            enter = fadeIn(tween(160)) + expandVertically(
                tween(200),
                expandFrom = Alignment.Bottom
            ),
            exit = fadeOut(tween(120)) + shrinkVertically(
                tween(160),
                shrinkTowards = Alignment.Bottom
            )
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 0.dp,
                shadowElevation = if (isDark) 4.dp else 16.dp,
                color = if (isDark) SlateDarkBackground else SlateSurface,
                border = if (isDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)) else null,
                modifier = Modifier.width(if (isBn) 210.dp else 196.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    OverlayMenuRow(
                        iconPainter = painterResource(id = R.drawable.clipboard),
                        iconSize = 16.dp,
                        label = if (isBn) "সিলেক্ট/ক্লিপবোর্ড" else "Select / Clipboard",
                        onClick = { onCloseMenu(); onSelectAction() }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        thickness = 0.5.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    OverlayMenuRow(
                        iconPainter = painterResource(id = R.drawable.screenshot),
                        iconSize = 16.dp,
                        label = if (isBn) "স্ক্রিনশট" else "Screenshot",
                        onClick = { onCloseMenu(); onScreenshotAction() }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        thickness = 0.5.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    OverlayMenuRow(
                        iconVector = Icons.Default.Mic,
                        iconSize = 20.dp,
                        label = if (isBn) "মাইক্রোফোন" else "Microphone",
                        tint = SlatePrimary,
                        onClick = { onCloseMenu(); onMicrophoneAction() }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        thickness = 0.5.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    OverlayMenuRow(
                        iconPainter = painterResource(id = R.drawable.khoj_chat_tooltip),
                        iconSize = 16.dp,
                        label = if (isBn) "খোঁজ চ্যাট" else "Khoj Chat",
                        onClick = { onCloseMenu(); onKhojChatAction() }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        thickness = 0.5.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    OverlayMenuRow(
                        iconPainter = painterResource(id = R.drawable.khoj_logo),
                        iconSize = 16.dp,
                        label = if (isBn) "অ্যাপ খুলুন" else "Open Khoj",
                        onClick = { onCloseMenu(); onSearchAction() }
                    )
                }
            }
        }

        // ── FAB button (42 dp squircle) ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(42.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .alpha(fabAlpha),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = menuExpanded,
                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(80)) },
                label = "fab_icon"
            ) { expanded ->
                if (expanded) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close menu",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.fab_view_on_other_apps),
                        contentDescription = "Open Khoj overlay",
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayMenuRow(
    iconPainter: Painter? = null,
    iconVector: ImageVector? = null,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (iconPainter != null) {
                    Image(
                        painter = iconPainter,
                        contentDescription = label,
                        modifier = Modifier.size(iconSize)
                    )
                } else if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = label,
                        tint = tint ?: MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
