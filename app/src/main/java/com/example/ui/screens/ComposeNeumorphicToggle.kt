package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ComposeNeumorphicToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Position animation (-1f when off, 1f when on)
    val alignBias by animateFloatAsState(
        targetValue = if (checked) 1f else -1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "toggle_bias"
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.run { (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5 }

    val trackBg = if (isDark) Color(0xFF1E293B) else Color(0xFFECF0F3)
    val indicatorBg = if (checked) primaryColor else (if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))

    val shadowLight = if (isDark) Color(0x1AFFFFFF) else Color(0xFFFFFFFF)
    val shadowDark = if (isDark) Color(0x66000000) else Color(0xFFD1D9E6)

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .width(56.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackBg)
            .then(
                if (enabled && onCheckedChange != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onCheckedChange(!checked) }
                } else Modifier
            )
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Sliding indicator
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.48f)
                .align(BiasAlignment(horizontalBias = alignBias, verticalBias = 0f))
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(12.dp),
                    spotColor = shadowDark,
                    ambientColor = shadowLight
                )
                .clip(RoundedCornerShape(12.dp))
                .background(indicatorBg)
        )
    }
}
