package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Premium Instagram / Facebook style confirmation modal dialog for destructive item deletion.
 * Follows strict Bengali typography and platform padding rules.
 */
@Suppress("DEPRECATION")
@Composable
fun DeleteConfirmationDialog(
    isOpen: Boolean,
    isDeleting: Boolean = false,
    title: String? = null,
    message: String? = null,
    isBn: Boolean = true,
    fontFamily: FontFamily? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val defaultTitle = if (isBn) "রিপোর্ট মুছে ফেলবেন?" else "Delete Report?"
    val defaultMessage = if (isBn) {
        "এই রিপোর্টটি মুছে ফেললে তা আর পুনরুদ্ধার করা সম্ভব হবে না। আপনি কি নিশ্চিতভাবে মুছে ফেলতে চান?"
    } else {
        "Once deleted, this report cannot be recovered. Are you sure you want to permanently delete it?"
    }

    val dialogTitle = title ?: defaultTitle
    val dialogMessage = message ?: defaultMessage

    val dialogBackground = if (isDark) Color(0xFF1E293B) else Color.White
    val iconBadgeBackground = if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.35f) else Color(0xFFFEE2E2)
    val destructiveColor = Color(0xFFDC2626)

    val titleStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    )

    val messageStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    )

    val buttonTextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    )

    Dialog(
        onDismissRequest = {
            if (!isDeleting) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isDeleting,
            dismissOnClickOutside = !isDeleting,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = dialogBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning icon badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(iconBadgeBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = "Delete Warning",
                        tint = destructiveColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = dialogTitle,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Description
                Text(
                    text = dialogMessage,
                    style = messageStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons: Cancel & Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel Button
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isDeleting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isDark) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFF1F5F9),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = null
                    ) {
                        Text(
                            text = if (isBn) "বাতিল" else "Cancel",
                            style = buttonTextStyle
                        )
                    }

                    // Delete Button
                    Button(
                        onClick = onConfirm,
                        enabled = !isDeleting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = destructiveColor,
                            contentColor = Color.White,
                            disabledContainerColor = destructiveColor.copy(alpha = 0.5f)
                        )
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isBn) "মুছে ফেলুন" else "Delete",
                                style = buttonTextStyle,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
