package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Slide-up Chat Actions Drawer triggered by the bottom-left '+' button.
 * Matches Claude/ChatGPT mobile action sheet aesthetics:
 * - Dual top tiles for Camera and Gallery (no "Photos" headers).
 * - Vertical action list: Upload files, Deep Research (with minimal toggle),
 *   AI Text Detector (with minimal toggle), AI Image Detector.
 * - Dark theme background: #212121; Light theme background: Color.White with tonalElevation = 0.dp.
 */
@Composable
fun KhojChatActionsDrawer(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onUploadFiles: () -> Unit,
    isDeepSearchActive: Boolean,
    onToggleDeepSearch: (Boolean) -> Unit,
    isAiTextDetectionActive: Boolean,
    onToggleAiTextDetection: (Boolean) -> Unit,
    isAiImageDetectionActive: Boolean,
    onToggleAiImageDetection: (Boolean) -> Unit,
    onAiImageDetectorClick: () -> Unit = {},
    isBn: Boolean,
    fontToUse: FontFamily,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val drawerBgColor = if (isDark) Color(0xFF212121) else Color.White
    val borderColor = if (isDark) Color(0xFF333333) else Color(0xFFE2E8F0).copy(alpha = 0.8f)
    val cardBgColor = if (isDark) Color(0xFF2C2D32) else Color(0xFFF1F3F5)
    val cardBorderColor = if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = drawerBgColor,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Drag handle pill
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 14.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isDark) Color(0xFF555555) else Color(0xFFCBD5E1))
                    .align(Alignment.CenterHorizontally)
            )

            // Top Action Grid: Camera and Gallery side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Camera tile
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTakePhoto(); onDismiss() },
                    shape = RoundedCornerShape(16.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = if (isBn) "ক্যামেরা" else "Camera",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Gallery tile
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPickGallery(); onDismiss() },
                    shape = RoundedCornerShape(16.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = if (isBn) "গ্যালারি" else "Gallery",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Upload files
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onUploadFiles(); onDismiss() }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = if (isBn) "ফাইল আপলোড করুন" else "Upload files",
                    fontFamily = fontToUse,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
                    )
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // 2. Deep Research + Toggle switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleDeepSearch(!isDeepSearchActive) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TravelExplore,
                    contentDescription = null,
                    tint = if (isDeepSearchActive) Color(0xFF0284C7) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = if (isBn) "ডিপ রিসার্চ" else "Deep Research",
                    fontFamily = fontToUse,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
                    )
                )
                Switch(
                    checked = isDeepSearchActive,
                    onCheckedChange = { onToggleDeepSearch(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF0284C7),
                        uncheckedThumbColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575),
                        uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB),
                        uncheckedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.scale(0.85f)
                )
            }

            // 3. AI Text Detector + Toggle switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleAiTextDetection(!isAiTextDetectionActive) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = if (isAiTextDetectionActive) Color(0xFF0284C7) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = if (isBn) "AI লেখা যাচাই" else "AI Text Detector",
                    fontFamily = fontToUse,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
                    )
                )
                Switch(
                    checked = isAiTextDetectionActive,
                    onCheckedChange = { onToggleAiTextDetection(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF0284C7),
                        uncheckedThumbColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575),
                        uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB),
                        uncheckedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.scale(0.85f)
                )
            }

            // 4. AI Image Detector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleAiImageDetection(!isAiImageDetectionActive) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CenterFocusWeak,
                    contentDescription = null,
                    tint = if (isAiImageDetectionActive) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = if (isBn) "AI ইমেজ ডিটেক্টর" else "AI Image Detector",
                    fontFamily = fontToUse,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
                    )
                )
                Switch(
                    checked = isAiImageDetectionActive,
                    onCheckedChange = { onToggleAiImageDetection(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF8B5CF6),
                        uncheckedThumbColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575),
                        uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB),
                        uncheckedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.scale(0.85f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
