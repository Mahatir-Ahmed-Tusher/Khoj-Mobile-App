package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.UserAvatarManager
import com.example.data.model.SupabaseUser
import com.example.ui.theme.SlatePrimary

/**
 * Full-page sliding overlay menu component.
 * Extracted from MainAppScreen to isolate navigation drawer UI, reduce file size,
 * and provide polished headers, custom item drawables, and elevation shadows.
 */
@Composable
fun FullPageMenuOverlay(
    visible: Boolean,
    isBn: Boolean,
    currentUser: SupabaseUser?,
    isLoggedIn: Boolean,
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    onOpenUrl: (String) -> Unit,
    onLogoutClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(220)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(180)),
        modifier = modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Top Header Row: Back button on the left of title (close cross removed)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isBn) "ফিরে যান" else "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = if (isBn) "মেনু" else "Menu",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            lineHeight = 26.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Profile / Guest Card at Top
                if (isLoggedIn && currentUser != null) {
                    val userDisplayName = currentUser.displayName?.trim()?.ifBlank { null }
                        ?: currentUser.email?.substringBefore("@")
                        ?: if (isBn) "ব্যবহারকারী" else "User"

                    Surface(
                        onClick = {
                            onDismiss()
                            onNavigate(AppScreen.PROFILE)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.5.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(SlatePrimary.copy(alpha = 0.15f))
                                    .border(1.2.dp, SlatePrimary.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(UserAvatarManager.getEffectiveAvatarUrl(LocalContext.current, currentUser))
                                        .decoderFactory(SvgDecoder.Factory())
                                        .crossfade(true)
                                        .placeholder(R.drawable.khoj_logo)
                                        .error(R.drawable.khoj_logo)
                                        .fallback(R.drawable.khoj_logo)
                                        .build(),
                                    contentDescription = "Profile Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userDisplayName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(1.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isBn) "প্রোফাইল দেখুন" else "View your profile",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            )
                                        ),
                                        color = SlatePrimary
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = SlatePrimary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Guest Account Card
                    Surface(
                        onClick = {
                            onDismiss()
                            onNavigate(AppScreen.LOGIN)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.5.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(SlatePrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = SlatePrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isBn) "লগইন করুন" else "Log In",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        lineHeight = 19.sp,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = if (isBn) "সকল সুবিধা পেতে সাইন ইন করুন" else "Sign in to access all features",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.5.sp,
                                        lineHeight = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = SlatePrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.8.dp)

                // Scrollable Menu List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp)
                ) {
                    // Activity History
                    DrawerMenuItem(
                        icon = Icons.Outlined.History,
                        label = if (isBn) "একটিভিটির ইতিহাস" else "Activity History",
                        selected = currentScreen == AppScreen.HISTORY,
                        onClick = {
                            onDismiss()
                            onNavigate(AppScreen.HISTORY)
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        thickness = 0.8.dp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Section 1: Features
                    Text(
                        text = if (isBn) "ফিচারসমূহ" else "Features",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = SlatePrimary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 0.5.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )

                    DrawerMenuItem(
                        drawableRes = R.drawable.e_library,
                        label = if (isBn) "ই-গ্রন্থ সম্ভার" else "E-Library",
                        subtitle = if (isBn) "তথ্য যাচাইয়ের বই ও রিসোর্স" else "Fact-checking books & resources",
                        selected = currentScreen == AppScreen.E_LIBRARY,
                        onClick = {
                            onDismiss()
                            onNavigate(AppScreen.E_LIBRARY)
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        thickness = 0.8.dp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Section 2: Info & Resources
                    Text(
                        text = if (isBn) "আমাদের সম্পর্কে" else "About Khoj",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = SlatePrimary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 0.5.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )

                    DrawerMenuItem(
                        drawableRes = R.drawable.about_khoj,
                        label = if (isBn) "খোঁজ সম্পর্কে" else "About Us",
                        selected = currentScreen == AppScreen.ABOUT,
                        onClick = {
                            onDismiss()
                            onNavigate(AppScreen.ABOUT)
                        }
                    )

                    DrawerMenuItem(
                        drawableRes = R.drawable.faq_ic,
                        label = if (isBn) "সচরাচর জিজ্ঞাসা" else "FAQ",
                        selected = currentScreen == AppScreen.FAQ,
                        onClick = {
                            onDismiss()
                            onNavigate(AppScreen.FAQ)
                        }
                    )

                    DrawerMenuItem(
                        drawableRes = R.drawable.write,
                        label = if (isBn) "কীভাবে লিখবেন" else "Write Us",
                        selected = currentScreen == AppScreen.WRITE_US,
                        onClick = {
                            onDismiss()
                            onNavigate(AppScreen.WRITE_US)
                        }
                    )

                    DrawerMenuItem(
                        drawableRes = R.drawable.funding,
                        label = if (isBn) "অর্থায়ন ও স্বচ্ছতা" else "Funding & Transparency",
                        selected = currentScreen == AppScreen.FUNDING_TRANSPARENCY,
                        onClick = {
                            onDismiss()
                            onNavigate(AppScreen.FUNDING_TRANSPARENCY)
                        }
                    )

                    DrawerMenuItem(
                        drawableRes = R.drawable.settings_ic,
                        label = if (isBn) "সেটিংস" else "Settings",
                        subtitle = if (isBn) "ভাষা ও থিম পরিবর্তন" else "Language, theme & preferences",
                        selected = currentScreen == AppScreen.SETTINGS,
                        onClick = {
                            onDismiss()
                            onNavigate(AppScreen.SETTINGS)
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        thickness = 0.8.dp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Section 3: Policies & External
                    Text(
                        text = if (isBn) "নীতিমালা ও অন্যান্য" else "Policies & Legal",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = SlatePrimary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 0.5.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )

                    DrawerMenuItem(
                        drawableRes = R.drawable.privacy_policy,
                        label = if (isBn) "গোপনীয়তা নীতি" else "Privacy Policy",
                        isExternal = true,
                        selected = false,
                        onClick = {
                            onDismiss()
                            onOpenUrl("https://khoj-ai.bd/privacy-policy")
                        }
                    )

                    DrawerMenuItem(
                        drawableRes = R.drawable.terms_and_conditions_ic,
                        label = if (isBn) "শর্তাবলী" else "Terms of Service",
                        isExternal = true,
                        selected = false,
                        onClick = {
                            onDismiss()
                            onOpenUrl("https://khoj-ai.bd/terms")
                        }
                    )

                    DrawerMenuItem(
                        drawableRes = R.drawable.api_icon,
                        label = if (isBn) "এপিআই" else "API Docs",
                        isExternal = true,
                        selected = false,
                        onClick = {
                            onDismiss()
                            onOpenUrl("https://khoj-ai.bd/api-docs")
                        }
                    )

                    // Bottom Action Section (Log Out / Log In + Version)
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isLoggedIn && currentUser != null) {
                            // Log Out Option
                            Surface(
                                onClick = {
                                    onDismiss()
                                    onLogoutClick()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.logout_icon),
                                        contentDescription = "Log Out",
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = if (isBn) "লগআউট" else "Log Out",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            )
                                        ),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            // Log In Option if Guest
                            Surface(
                                onClick = {
                                    onDismiss()
                                    onNavigate(AppScreen.LOGIN)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = SlatePrimary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.30f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Log In",
                                        tint = SlatePrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = if (isBn) "লগইন করুন" else "Log In",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            )
                                        ),
                                        color = SlatePrimary
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (isBn) "খোঁজ সংস্করণ ২.০.১" else "Khoj Version 2.0.1",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            ),
                            modifier = Modifier.padding(top = 2.dp, bottom = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Menu item row displaying label, optional subtitle, external link icon or navigation arrow.
 * Icons are rendered cleanly without container backgrounds or borders.
 */
@Composable
fun DrawerMenuItem(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    drawableRes: Int? = null,
    selected: Boolean = false,
    subtitle: String? = null,
    isExternal: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SlatePrimary.copy(alpha = 0.08f) else Color.Transparent,
        contentColor = if (selected) SlatePrimary else MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon rendered directly with no background or border
            if (drawableRes != null) {
                Image(
                    painter = painterResource(id = drawableRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        )
                    ),
                    color = if (selected) SlatePrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isExternal) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(13.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (selected) SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
