package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.OfflineStateCard
import com.example.ui.components.rememberShuffledOfflineAnimation
import com.example.utils.NetworkErrorHelper
import kotlin.random.Random

@Composable
fun NoInternetScreen(
    modifier: Modifier = Modifier,
    isBn: Boolean = true,
    fontToUse: FontFamily = FontFamily.Default,
    featureName: String? = null,
    title: String? = null,
    description: String? = null,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val animRes = rememberShuffledOfflineAnimation(featureName ?: "NoInternetScreen")

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
