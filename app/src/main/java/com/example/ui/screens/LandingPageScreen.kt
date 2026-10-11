package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.R

@Immutable
data class LandingFeatureItem(
    val iconResId: Int,
    val title: String,
    val description: String
)

@Composable
fun LandingPageScreen(
    onGetStarted: () -> Unit,
    fontToUse: FontFamily,
    isDarkTheme: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val featureItems = remember {
        listOf(
            LandingFeatureItem(
                iconResId = R.drawable.shield,
                title = "ফ্যাক্টচেকিং",
                description = "ভুয়া ও বিভ্রান্তিকর তথ্য শনাক্ত করুন"
            ),
            LandingFeatureItem(
                iconResId = R.drawable.deep_search,
                title = "ডিপ রিসার্চ",
                description = "গবেষণা ও গভীর অনুসন্ধান"
            ),
            LandingFeatureItem(
                iconResId = R.drawable.ai_image,
                title = "AI সনাক্তকরণ",
                description = "এআই জেনারেশন সনাক্তকরণ"
            )
        )
    }

    val bgDrawable = if (isDarkTheme) R.drawable.landing_page_bg_dark else R.drawable.landing_page_bg_light

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFEBF3FC))
    ) {
        // Full background image artwork (Theme specific artwork containing mascot)
        Image(
            painter = painterResource(id = bgDrawable),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(600)) + slideInVertically(
                initialOffsetY = { 40 },
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenHeight = maxHeight
                val topPadding = if (screenHeight < 680.dp) 250.dp else 310.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(topPadding))

                    // Brand & Copy Section
                    BrandSection(
                        fontToUse = fontToUse,
                        isDarkTheme = isDarkTheme
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3 Feature Cards Row
                    FeatureRow(
                        features = featureItems,
                        fontToUse = fontToUse,
                        isDarkTheme = isDarkTheme
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // CTA & Footer Section
                    CTASection(
                        onGetStarted = onGetStarted,
                        fontToUse = fontToUse,
                        isDarkTheme = isDarkTheme
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun BrandSection(
    fontToUse: FontFamily,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Title - Brand Color #3B9DFF, clean horizontal single line
        Text(
            text = "খোঁজ",
            style = MaterialTheme.typography.displayMedium.copy(
                fontFamily = fontToUse,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                letterSpacing = 0.sp
            ),
            color = Color(0xFF3B9DFF),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(3.dp))

        // Tagline
        Text(
            text = "সত্যের সন্ধান, তথ্যের শুদ্ধি",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = fontToUse,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.15.sp
            ),
            color = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF2C3E50),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle / Description
        Text(
            text = "তথ্য যাচাই, গবেষণা ও ডিজিটাল লিটারেসির প্রতিটি ধাপে থাকুক খোঁজ",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = fontToUse,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 19.sp
            ),
            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF5A6B82),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun FeatureRow(
    features: List<LandingFeatureItem>,
    fontToUse: FontFamily,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        features.forEach { item ->
            FeatureCard(
                item = item,
                fontToUse = fontToUse,
                isDarkTheme = isDarkTheme,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FeatureCard(
    item: LandingFeatureItem,
    fontToUse: FontFamily,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = if (isDarkTheme) Color(0x66000000) else Color(0x2B004599),
                ambientColor = if (isDarkTheme) Color(0x40000000) else Color(0x14002B66)
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.52f),
        border = BorderStroke(
            1.2.dp,
            if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.80f) else Color.White.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = item.iconResId),
                    contentDescription = item.title,
                    modifier = Modifier.size(25.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Card Title
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = fontToUse,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF1E293B),
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Card Description
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = fontToUse,
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    lineHeight = 13.5.sp
                ),
                color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun CTASection(
    onGetStarted: () -> Unit,
    fontToUse: FontFamily,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val buttonShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main CTA Button ("সামনে যাওয়া যাক") - Styled Rounded Rectangle Button
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .height(48.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = buttonShape,
                    spotColor = Color(0x362579CF),
                    ambientColor = Color(0x142579CF)
                ),
            shape = buttonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2579CF)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "সামনে যাওয়া যাক",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = fontToUse,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Partner Logos ("অর্থায়নে") Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "অর্থায়নে",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = fontToUse,
                    fontWeight = FontWeight.Medium,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.3.sp
                ),
                color = if (isDarkTheme) Color(0xFF94A3B8).copy(alpha = 0.85f) else Color(0xFF64748B).copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.undp_logo),
                    contentDescription = "UNDP",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(20.dp)
                )
                Image(
                    painter = painterResource(id = R.drawable.ict_division_logo),
                    contentDescription = "ICT Division",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(20.dp)
                )
                Image(
                    painter = painterResource(id = R.drawable.bcc_logo),
                    contentDescription = "BCC",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(20.dp)
                )
            }
        }
    }
}

