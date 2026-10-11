package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.R
import com.example.ui.theme.SlatePrimary
import com.example.utils.NetworkErrorHelper

/**
 * Returns a remembered shuffled Lottie animation resource id (R.raw.no_internet or R.raw.no_connection).
 */
@Composable
fun rememberShuffledOfflineAnimation(seed: Any? = null): Int {
    return remember(seed) {
        NetworkErrorHelper.getShuffledOfflineAnimation(seed)
    }
}

/**
 * Inline card that displays a shuffled Lottie animation with friendly offline copy
 * and retry/back action buttons.
 */
@Composable
fun OfflineStateCard(
    modifier: Modifier = Modifier,
    isBn: Boolean = true,
    fontToUse: FontFamily = FontFamily.Default,
    featureName: String? = null,
    title: String? = null,
    description: String? = null,
    animationRes: Int = rememberShuffledOfflineAnimation(),
    animationSize: Dp = 150.dp,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    retryButtonText: String? = null,
    compact: Boolean = false
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animationRes))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    val finalTitle = title ?: NetworkErrorHelper.getOfflineTitle(isBn)
    val finalDesc = description ?: NetworkErrorHelper.getOfflineDescription(isBn, featureName)
    val finalRetryText = retryButtonText ?: if (isBn) "পুনরায় চেষ্টা করুন" else "Retry"
    val backText = if (isBn) "ফিরে যান" else "Go Back"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Lottie Animation Container
            Box(
                modifier = Modifier
                    .size(if (compact) 100.dp else animationSize)
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (composition != null) {
                    LottieAnimation(
                        composition = composition,
                        progress = { progress },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.WifiOff,
                        contentDescription = "Offline",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Title
            Text(
                text = finalTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = fontToUse,
                    fontSize = if (compact) 15.sp else 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Description
            Text(
                text = finalDesc,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = fontToUse,
                    fontSize = if (compact) 12.sp else 13.sp,
                    lineHeight = if (compact) 16.sp else 19.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            if (onRetry != null || onBack != null) {
                Spacer(modifier = Modifier.height(if (compact) 12.dp else 18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = backText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = fontToUse
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (onRetry != null) {
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SlatePrimary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = finalRetryText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = fontToUse
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modal Dialog displaying the shuffled offline Lottie state.
 */
@Composable
fun OfflineStateDialog(
    onDismissRequest: () -> Unit,
    isBn: Boolean = true,
    fontToUse: FontFamily = FontFamily.Default,
    featureName: String? = null,
    title: String? = null,
    description: String? = null,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val animRes = rememberShuffledOfflineAnimation(featureName ?: System.currentTimeMillis())

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            OfflineStateCard(
                isBn = isBn,
                fontToUse = fontToUse,
                featureName = featureName,
                title = title,
                description = description,
                animationRes = animRes,
                onRetry = onRetry,
                onBack = onBack ?: onDismissRequest
            )
        }
    }
}

/**
 * Full-screen offline view suitable for top-level screens or root containers.
 */
@Composable
fun OfflineFullScreenView(
    modifier: Modifier = Modifier,
    isBn: Boolean = true,
    fontToUse: FontFamily = FontFamily.Default,
    featureName: String? = null,
    title: String? = null,
    description: String? = null,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val animRes = rememberShuffledOfflineAnimation(featureName ?: "fullscreen")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        OfflineStateCard(
            modifier = Modifier.fillMaxWidth(0.92f),
            isBn = isBn,
            fontToUse = fontToUse,
            featureName = featureName,
            title = title,
            description = description,
            animationRes = animRes,
            animationSize = 180.dp,
            onRetry = onRetry,
            onBack = onBack
        )
    }
}
