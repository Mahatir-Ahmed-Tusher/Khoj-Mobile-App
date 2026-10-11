package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AnekBanglaFamily
import com.example.ui.theme.SlatePrimary
import com.example.ui.theme.TiroBanglaFamily
import com.example.ui.viewmodel.FactCheckViewModel

@Composable
fun SettingsMainScreen(
    viewModel: FactCheckViewModel,
    onNavigateToLanguage: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToWidgetSettings: () -> Unit,
    onNavigateToControls: () -> Unit,
    onNavigateToHelpCenter: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
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
            titleBn = getString("settings", true),
            titleEn = getString("settings", false),
            subtitleBn = "আপনার পছন্দমতো অ্যাপ কাস্টমাইজ করুন",
            subtitleEn = "Customize the app to your preferences",
            isBn = isBn,
            fontToUse = fontToUse,
            showGearIcon = true,
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Language
            SettingsItemCard(
                icon = Icons.Default.Language,
                title = getString("language", isBn),
                subtitle = if (isBn) "অ্যাপ ও এআই এর ভাষা পরিবর্তন করুন" else "Change App and AI language",
                fontToUse = fontToUse,
                onClick = onNavigateToLanguage
            )

            // Appearance
            SettingsItemCard(
                icon = Icons.Default.Palette,
                title = getString("appearance", isBn),
                subtitle = if (isBn) "থিম এবং ফন্ট কাস্টমাইজ করুন" else "Customize theme and fonts",
                fontToUse = fontToUse,
                onClick = onNavigateToAppearance
            )

            // Widget Settings
            SettingsItemCard(
                icon = Icons.Default.Widgets,
                title = getString("widget_settings", isBn),
                subtitle = if (isBn) "হোম স্ক্রিন উইজেট কনফিগার করুন" else "Configure the Home Screen widget",
                fontToUse = fontToUse,
                onClick = onNavigateToWidgetSettings
            )

            // Controls
            SettingsItemCard(
                icon = Icons.Default.Tune,
                title = getString("controls", isBn),
                subtitle = if (isBn) "সার্চ ইঞ্জিন এবং অন্যান্য কন্ট্রোল" else "Search engines and other controls",
                fontToUse = fontToUse,
                onClick = onNavigateToControls
            )

            // Help Center
            SettingsItemCard(
                icon = Icons.Default.HelpOutline,
                title = getString("help_center", isBn),
                subtitle = if (isBn) "অ্যাপ ব্যবহারের নির্দেশিকা এবং প্রশ্নোত্তর" else "Guides and FAQs on using the app",
                fontToUse = fontToUse,
                onClick = onNavigateToHelpCenter
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reset Settings button
            OutlinedButton(
                onClick = {
                    viewModel.setResponseLanguage(com.example.data.preferences.ResponseLanguage.AUTO)
                    viewModel.setThemePreference("system")
                    Toast.makeText(context, if (isBn) "ডিফল্ট সেটিংস সফলভাবে রিসেট করা হয়েছে" else "Default settings restored successfully", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = getString("reset_settings_title", isBn),
                    fontFamily = fontToUse,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SettingsItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    fontToUse: androidx.compose.ui.text.font.FontFamily,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(SlatePrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = SlatePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        fontFamily = fontToUse,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontFamily = fontToUse,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
