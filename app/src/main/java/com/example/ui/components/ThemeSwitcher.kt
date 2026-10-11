package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom animated ThemeSwitcher toggle button adapted for Khoj Factchecker.
 *
 * Implements smooth sliding thumb animation with cutout icon contrast
 * using Khoj's brand color palette (Cerulean Blue & Radiant Sky Accent).
 */
@Composable
fun ThemeSwitcher(
    darkTheme: Boolean = false,
    size: Dp = 24.dp,
    iconSize: Dp = (size * 0.42f),
    padding: Dp = (size * 0.14f),
    borderWidth: Dp = 0.5.dp,
    parentShape: Shape = CircleShape,
    toggleShape: Shape = CircleShape,
    animationSpec: AnimationSpec<Dp> = tween(durationMillis = 300),
    onClick: () -> Unit
) {
    val offset by animateDpAsState(
        targetValue = if (darkTheme) 0.dp else size,
        animationSpec = animationSpec,
        label = "ThemeSwitcherOffsetAnimation"
    )

    // Track stays the same colour as the background of the app in both themes
    val trackColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background,
        animationSpec = tween(durationMillis = 300),
        label = "ThemeTrackColor"
    )

    // Match the exact colour of the language toggle (MaterialTheme.colorScheme.onBackground)
    // Dark mode: Color(0xFFF1F5F9) [Clean White]
    // Light mode: Color(0xFF0F172A) [Near Black / Slate]
    val languageToggleColor = MaterialTheme.colorScheme.onBackground

    val thumbColor by animateColorAsState(
        targetValue = languageToggleColor,
        animationSpec = tween(durationMillis = 300),
        label = "ThemeThumbColor"
    )

    val borderColor by animateColorAsState(
        targetValue = languageToggleColor,
        animationSpec = tween(durationMillis = 300),
        label = "ThemeBorderColor"
    )

    val moonIconTint by animateColorAsState(
        targetValue = if (darkTheme) MaterialTheme.colorScheme.background else languageToggleColor,
        animationSpec = tween(durationMillis = 300),
        label = "ThemeMoonTint"
    )

    val sunIconTint by animateColorAsState(
        targetValue = if (darkTheme) languageToggleColor else MaterialTheme.colorScheme.background,
        animationSpec = tween(durationMillis = 300),
        label = "ThemeSunTint"
    )

    Box(
        modifier = Modifier
            .width(size * 2)
            .height(size)
            .clip(shape = parentShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .background(trackColor)
    ) {
        // Sliding Thumb
        Box(
            modifier = Modifier
                .size(size)
                .offset(x = offset)
                .padding(all = padding)
                .clip(shape = toggleShape)
                .background(thumbColor)
        )

        // Icons & Border Row Layered Above Thumb
        Row(
            modifier = Modifier
                .border(
                    border = BorderStroke(
                        width = borderWidth,
                        color = borderColor
                    ),
                    shape = parentShape
                )
        ) {
            // Moon Icon Box (Left - Dark Theme)
            Box(
                modifier = Modifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(iconSize),
                    imageVector = Icons.Default.Nightlight,
                    contentDescription = "Dark Theme",
                    tint = moonIconTint
                )
            }

            // Sun Icon Box (Right - Light Theme)
            Box(
                modifier = Modifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(iconSize),
                    imageVector = Icons.Default.LightMode,
                    contentDescription = "Light Theme",
                    tint = sunIconTint
                )
            }
        }
    }
}
