package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SlatePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepSearchOptionsDrawer(
    isFactCheckEnabled: Boolean,
    onFactCheckToggled: () -> Unit,
    isWebSearchEnabled: Boolean,
    onWebSearchToggled: (Boolean) -> Unit,
    isDeepSearchEnabled: Boolean,
    onDeepSearchToggled: (Boolean) -> Unit,
    isAiTextDetectionEnabled: Boolean,
    onAiTextDetectionToggled: (Boolean) -> Unit,
    isBn: Boolean,
    fontToUse: FontFamily,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val sheetBgColor = if (isDark) Color(0xFF1E1E22) else Color.White

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBgColor,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF4A4A52) else Color(0xFFCBD5E1))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row: Reduced size Title & Clean Borderless Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                @Suppress("DEPRECATION")
                Text(
                    text = if (isBn) "আরও খোঁজের অপশন" else "More Search Options",
                    fontFamily = fontToUse,
                    style = TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        )
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }

            // Option 1: Fact-Check (Default Toggled)
            SearchOptionRow(
                title = if (isBn) "ফ্যাক্টচেক" else "Fact Check",
                icon = Icons.Default.VerifiedUser,
                iconColor = Color(0xFF10B981), // Emerald Green
                checked = isFactCheckEnabled,
                onCheckedChange = { onFactCheckToggled() },
                fontToUse = fontToUse,
                isDark = isDark
            )

            // Option 2: Live Web Search
            SearchOptionRow(
                title = if (isBn) "ওয়েব সার্চ" else "Web Search",
                icon = Icons.Default.TravelExplore,
                iconColor = Color(0xFF0284C7), // Sky Blue
                checked = isWebSearchEnabled,
                onCheckedChange = onWebSearchToggled,
                fontToUse = fontToUse,
                isDark = isDark
            )

            // Option 3: Deep Search / Research
            SearchOptionRow(
                title = if (isBn) "ডিপ সার্চ" else "Deep Search",
                icon = Icons.Default.AutoAwesome,
                iconColor = SlatePrimary, // Indigo
                checked = isDeepSearchEnabled,
                onCheckedChange = onDeepSearchToggled,
                fontToUse = fontToUse,
                isDark = isDark
            )

            // Option 4: AI Text Detection
            SearchOptionRow(
                title = if (isBn) "AI লেখা যাচাই" else "AI Text Detector",
                icon = Icons.Outlined.Description,
                iconColor = Color(0xFFF59E0B), // Amber
                checked = isAiTextDetectionEnabled,
                onCheckedChange = onAiTextDetectionToggled,
                fontToUse = fontToUse,
                isDark = isDark
            )
        }
    }
}

@Composable
private fun SearchOptionRow(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    fontToUse: FontFamily,
    isDark: Boolean
) {
    val animatedBgColor by animateColorAsState(
        targetValue = if (checked) iconColor.copy(alpha = if (isDark) 0.12f else 0.08f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "row_bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(animatedBgColor)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Clean vector icon directly without borders or box containers
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) iconColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(22.dp)
        )

        // Title with full Indic typography support
        @Suppress("DEPRECATION")
        Text(
            text = title,
            fontFamily = fontToUse,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium,
                platformStyle = PlatformTextStyle(includeFontPadding = true),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        // Scaled and styled Switch matching KhojChatActionsDrawer
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = iconColor,
                uncheckedThumbColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575),
                uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB),
                uncheckedBorderColor = Color.Transparent
            ),
            modifier = Modifier.scale(0.85f)
        )
    }
}
