package com.example.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.AnekBanglaFamily
import com.example.ui.theme.SlatePrimary
import com.example.ui.theme.TiroBanglaFamily
import com.example.ui.viewmodel.FactCheckViewModel
import com.example.widget.KhojSearchWidgetProvider

@Composable
fun WidgetSettingsScreen(
    viewModel: FactCheckViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (themePreference) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark
    }

    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val isPinningSupported = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported
    }

    var showManualGuide by remember { mutableStateOf(!isPinningSupported) }

    val bengaliFontPreference by viewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    val fontToUse = if (isBn) {
        if (bengaliFontPreference == "anek") AnekBanglaFamily else TiroBanglaFamily
    } else {
        androidx.compose.ui.text.font.FontFamily.Default
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            titleBn = "উইজেট সেটিংস",
            titleEn = "Widget Settings",
            subtitleBn = "হোম স্ক্রিনে খোঁজ সার্চ বার যুক্ত করুন",
            subtitleEn = "Add Khoj Search Bar to your Home Screen",
            isBn = isBn,
            fontToUse = fontToUse,
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Widget Preview Card
            WidgetPreviewCard(isBn = isBn, isDarkTheme = isDarkTheme)

            // Pin to Home Screen Action Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isPinningSupported) Color(0xFF10B981).copy(alpha = 0.15f) else SlatePrimary.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPinningSupported) Icons.Default.CheckCircle else Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = if (isPinningSupported) Color(0xFF10B981) else SlatePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = if (isPinningSupported) {
                                    if (isBn) "ওয়ান-ট্যাপ ইনস্টলেশন সমর্থিত" else "1-Tap Installation Supported"
                                } else {
                                    if (isBn) "হোম স্ক্রিন উইজেট সেটআপ" else "Home Screen Widget Setup"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isPinningSupported) {
                                    if (isBn) "সরাসরি আপনার হোম স্ক্রিনে উইজেটটি পিন করুন" else "Pin the search widget directly to your launcher"
                                } else {
                                    if (isBn) "ম্যানুয়াল ড্র্যাগ-অ্যান্ড-ড্রপ পদ্ধতি ব্যবহার করুন" else "Use manual drag-and-drop from widget menu"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isPinningSupported) {
                        Button(
                            onClick = {
                                try {
                                    val provider = ComponentName(context, KhojSearchWidgetProvider::class.java)
                                    val success = appWidgetManager.requestPinAppWidget(provider, null, null)
                                    if (success) {
                                        Toast.makeText(
                                            context,
                                            if (isBn) "হোম স্ক্রিন পিনিং কনফার্মেশন প্রম্পট পাঠানো হয়েছে" else "Widget pinning prompt sent to your home screen",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            if (isBn) "লঞ্চার রিকোয়েস্ট গ্রহণ করেনি। ম্যানুয়াল গাইড অনুসরণ করুন।" else "Launcher could not accept request. Follow manual steps.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        showManualGuide = true
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        if (isBn) "একটি ত্রুটি ঘটেছে: ${e.localizedMessage}" else "Error: ${e.localizedMessage}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showManualGuide = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SlatePrimary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBn) "হোম স্ক্রিনে উইজেট যুক্ত করুন" else "Add Widget to Home Screen",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Toggle for manual guide
                    TextButton(
                        onClick = { showManualGuide = !showManualGuide },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = if (showManualGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showManualGuide) {
                                if (isBn) "ম্যানুয়াল গাইড লুকান" else "Hide Manual Setup Guide"
                            } else {
                                if (isBn) "ম্যানুয়াল সেটআপ নির্দেশিকা দেখুন" else "View Manual Setup Guide"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Step-by-Step Manual Guide
            AnimatedVisibility(visible = showManualGuide) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                tint = SlatePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isBn) "ম্যানুয়াল সেটআপ পদ্ধতি" else "Manual Setup Instructions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val steps = if (isBn) {
                            listOf(
                                "১. আপনার মোবাইলের হোম স্ক্রিনের যেকোনো খালি জায়গায় চেপে ধরে রাখুন (Long Press)।",
                                "২. নিচে প্রদর্শিত মেনু থেকে 'Widgets' (উইজেটস) অপশনে ট্যাপ করুন।",
                                "৩. উইজেটের তালিকা স্ক্রোল করে 'Khoj' (খোঁজ) অ্যাপটি খুঁজে বের করুন।",
                                "৪. 'খোঁজ সার্চ বার' উইজেটটি চেপে ধরে আপনার পছন্দমতো স্থানে বসিয়ে দিন।"
                            )
                        } else {
                            listOf(
                                "1. Touch and hold (long-press) any empty space on your Android Home Screen.",
                                "2. Tap on 'Widgets' from the pop-up menu at the bottom.",
                                "3. Scroll through the widget list to locate 'Khoj'.",
                                "4. Touch and hold the 'Khoj Search Bar' widget and drag it to your desired screen position."
                            )
                        }

                        steps.forEach { step ->
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            // Feature Highlights & Capabilities
            Text(
                text = if (isBn) "উইজেটের সুবিধাসমূহ" else "Widget Features",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )

            WidgetFeatureItem(
                icon = Icons.Outlined.Shield,
                iconTint = Color(0xFF0284C7),
                title = if (isBn) "ফ্যাক্ট-চেক মোড (Fact Check)" else "Fact Check Mode",
                description = if (isBn) "যেকোনো খবর, সোশ্যাল মিডিয়া পোস্ট বা ভাইরাল দাবির সত্যতা নিমেষেই যাচাই করুন।" else "Instantly verify news claims, viral social media posts, and rumours."
            )

            WidgetFeatureItem(
                icon = Icons.Outlined.Public,
                iconTint = Color(0xFF10B981),
                title = if (isBn) "ওয়েব সার্চ মোড (Web Search)" else "Web Search Mode",
                description = if (isBn) "বিশ্বস্ত উৎস ও ডেটাবেজ থেকে তথ্য ও ওয়েবসাইটের তথ্য সরাসরি খুঁজে নিন।" else "Search authoritative sources, news archives, and web references directly."
            )

            WidgetFeatureItem(
                icon = Icons.Outlined.Bolt,
                iconTint = Color(0xFFF59E0B),
                title = if (isBn) "ডিপ সার্চ / রিসার্চ মোড (Deep Search)" else "Deep Search / Research Mode",
                description = if (isBn) "এআই-চালিত গভীর অনুসন্ধান ও মাল্টি-সোর্স অ্যানালাইসিস।" else "Multi-source deep AI analysis and extensive intelligence gathering."
            )

            WidgetFeatureItem(
                icon = Icons.Outlined.Mic,
                iconTint = Color(0xFFEF4444),
                title = if (isBn) "ভয়েস ও ইমেজ শর্টকাট (Voice & Image)" else "Voice & Image Shortcuts",
                description = if (isBn) "মাইক বা ক্যামেরা আইকনে ট্যাপ করে কথা বলে বা ছবি স্ক্যান করে সার্চ করুন।" else "Tap the mic or camera icons to search by voice or visual photo scan."
            )

            WidgetFeatureItem(
                icon = Icons.Outlined.AutoAwesome,
                iconTint = Color(0xFF8B5CF6),
                title = if (isBn) "স্মার্ট ডেটা রিকভারি (Deferred Auth)" else "Smart Deferred Auth",
                description = if (isBn) "লগইন করা না থাকলেও উইজেট থেকে কোয়েরি দিয়ে লগইন সম্পন্ন করার পর স্বয়ংক্রিয়ভাবে সার্চ রান হবে।" else "Search seamlessly even before login; your search executes immediately after sign-in."
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WidgetPreviewCard(isBn: Boolean, isDarkTheme: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme) Color(0xFF18181B) else Color(0xFFF1F5F9)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isBn) "উইজেট লাইভ প্রিভিউ (৪x১)" else "Widget Live Preview (4x1)",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlatePrimary,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SlatePrimary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = if (isBn) "হোম স্ক্রিন" else "Home Screen",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = SlatePrimary
                    )
                }
            }

            // Visual replica of the Android Search Widget
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (isDarkTheme) Color(0xFF1E1E24) else Color.White,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search Bar Pill
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDarkTheme) Color(0xFF282830) else Color(0xFFF8FAFC),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color(0xFFE2E8F0)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.khoj_logo),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBn) "যেকোনো দাবি বা বিষয় অনুসন্ধান..." else "Search or verify any claim...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_widget_mic),
                                contentDescription = "Voice",
                                tint = SlatePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_widget_camera),
                                contentDescription = "Camera",
                                tint = SlatePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Mode Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WidgetPreviewChip(
                            icon = R.drawable.ic_widget_shield,
                            text = if (isBn) "ফ্যাক্ট-চেক" else "Fact Check",
                            tint = Color(0xFF0284C7),
                            isDark = isDarkTheme,
                            modifier = Modifier.weight(1f)
                        )
                        WidgetPreviewChip(
                            icon = R.drawable.ic_widget_globe,
                            text = if (isBn) "ওয়েব" else "Web",
                            tint = Color(0xFF10B981),
                            isDark = isDarkTheme,
                            modifier = Modifier.weight(1f)
                        )
                        WidgetPreviewChip(
                            icon = R.drawable.ic_widget_bolt,
                            text = if (isBn) "রিসার্চ" else "Research",
                            tint = Color(0xFFF59E0B),
                            isDark = isDarkTheme,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewChip(
    icon: Int,
    text: String,
    tint: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) Color(0xFF282830) else Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WidgetFeatureItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
