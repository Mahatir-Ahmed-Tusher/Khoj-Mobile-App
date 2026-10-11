package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.screens.AppScreen
import com.example.ui.screens.getString

@Composable
fun LiquidGlassBottomNav(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    onActionClick: () -> Unit,
    isBn: Boolean,
    fontToUse: FontFamily,
    unreadNotificationCount: Int = 0,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Facebook-style flat color scheme
    val navBgColor = if (darkTheme) {
        MaterialTheme.colorScheme.background // Match app background in dark UI
    } else {
        Color(0xFFFFFFFF) // Facebook light theme white surface
    }

    val topBorderColor = if (darkTheme) {
        Color(0xFF393A3B) // Subtle dark top divider line
    } else {
        Color(0xFFE4E6EB) // Subtle light top divider line
    }

    val activeColor = if (darkTheme) {
        Color(0xFF2D88FF) // Facebook dark active blue accent
    } else {
        Color(0xFF1877F2) // Facebook classic primary blue
    }

    val inactiveColor = if (darkTheme) {
        Color(0xFFB0B3B8) // Facebook dark inactive gray
    } else {
        Color(0xFF65676B) // Facebook light inactive gray
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = navBgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Top thin divider line across the navbar
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = topBorderColor
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Item 1: HOME / REPORT
                val homeSelected = currentScreen == AppScreen.HOME || currentScreen == AppScreen.REPORT
                FacebookNavItem(
                    selected = homeSelected,
                    onClick = {
                        if (currentScreen == AppScreen.REPORT) {
                            onNavigate(AppScreen.REPORT)
                        } else {
                            onNavigate(AppScreen.HOME)
                        }
                    },
                    painter = painterResource(id = if (homeSelected) R.drawable.ic_nav_home_filled else R.drawable.ic_nav_home_outline),
                    label = if (isBn) "হোম" else "Home",
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    fontToUse = fontToUse,
                    testTag = "nav_item_check",
                    modifier = Modifier.weight(1f)
                )

                // Item 2: NOTIFICATIONS
                val notifSelected = currentScreen == AppScreen.NOTIFICATIONS
                FacebookNavItem(
                    selected = notifSelected,
                    onClick = { onNavigate(AppScreen.NOTIFICATIONS) },
                    painter = rememberVectorPainter(if (notifSelected) Icons.Filled.Notifications else Icons.Outlined.Notifications),
                    label = if (isBn) "নোটিফিকেশন" else "Notifications",
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    fontToUse = fontToUse,
                    badgeCount = unreadNotificationCount,
                    testTag = "nav_item_notifications",
                    modifier = Modifier.weight(1f)
                )

                // Item 3: FEED
                val feedSelected = currentScreen == AppScreen.FEED
                FacebookNavItem(
                    selected = feedSelected,
                    onClick = { onNavigate(AppScreen.FEED) },
                    painter = painterResource(id = if (feedSelected) R.drawable.ic_nav_feed_filled else R.drawable.ic_nav_feed_outline),
                    label = if (isBn) "ফিড" else "Feed",
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    fontToUse = fontToUse,
                    testTag = "nav_item_feed",
                    modifier = Modifier.weight(1f)
                )

                // Item 4: SETTINGS
                val settingsSelected = currentScreen == AppScreen.SETTINGS
                FacebookNavItem(
                    selected = settingsSelected,
                    onClick = { onNavigate(AppScreen.SETTINGS) },
                    painter = painterResource(id = if (settingsSelected) R.drawable.ic_nav_settings_filled else R.drawable.ic_nav_settings_outline),
                    label = getString("settings", isBn),
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    fontToUse = fontToUse,
                    testTag = "nav_item_settings",
                    modifier = Modifier.weight(1f)
                )

                // Item 5: PROFILE
                val profileSelected = currentScreen == AppScreen.PROFILE
                FacebookNavItem(
                    selected = profileSelected,
                    onClick = { onNavigate(AppScreen.PROFILE) },
                    painter = rememberVectorPainter(if (profileSelected) Icons.Filled.Person else Icons.Outlined.Person),
                    label = if (isBn) "প্রোফাইল" else "Profile",
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    fontToUse = fontToUse,
                    testTag = "nav_item_profile",
                    avatarUrl = avatarUrl,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun FacebookNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    painter: Painter,
    label: String,
    activeColor: Color,
    inactiveColor: Color,
    fontToUse: FontFamily,
    testTag: String,
    badgeCount: Int = 0,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 150),
        label = "nav_item_color"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .testTag(testTag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Facebook Top Active Blue Indicator Line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    color = if (selected) activeColor else Color.Transparent,
                    shape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(25.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) activeColor else inactiveColor.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                )
            } else {
                BadgedBox(
                    badge = {
                        if (badgeCount > 0) {
                            Badge(
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        painter = painter,
                        contentDescription = label,
                        tint = animatedColor,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                fontFamily = fontToUse,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 10.sp,
                color = animatedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LiquidGlassNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    painter: Painter,
    label: String,
    activeColor: Color,
    inactiveColor: Color,
    textColor: Color,
    indicatorBgColor: Color,
    fontToUse: FontFamily,
    testTag: String,
    badgeCount: Int = 0,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier
) {
    FacebookNavItem(
        selected = selected,
        onClick = onClick,
        painter = painter,
        label = label,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        fontToUse = fontToUse,
        testTag = testTag,
        badgeCount = badgeCount,
        avatarUrl = avatarUrl,
        modifier = modifier
    )
}

