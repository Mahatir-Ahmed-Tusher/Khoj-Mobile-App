package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AnekBanglaFamily
import com.example.ui.theme.SlatePrimary
import com.example.ui.theme.TiroBanglaFamily
import com.example.ui.viewmodel.FactCheckViewModel

@Composable
fun SettingsAppearanceScreen(
    viewModel: FactCheckViewModel,
    onBack: () -> Unit
) {
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
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
            titleBn = getString("appearance", true),
            titleEn = getString("appearance", false),
            subtitleBn = "অ্যাপ্লিকেশনের লুক ও ফিল পরিবর্তন করুন",
            subtitleEn = "Change the look and feel of the app",
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
            // Theme Option Preference Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.DarkMode,
                            contentDescription = "Theme",
                            tint = SlatePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = getString("theme_pref", isBn),
                            fontFamily = fontToUse,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = getString("theme_desc", isBn),
                        fontFamily = fontToUse,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "system" to if (isBn) "সিস্টেম" else "System",
                            "light" to if (isBn) "লাইট" else "Light",
                            "dark" to if (isBn) "ডার্ক" else "Dark"
                        ).forEach { (code, name) ->
                            val selected = themePreference == code
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setThemePreference(code) },
                                label = { Text(name, fontFamily = fontToUse, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SlatePrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Bengali Font Choice Card (only shown when language is Bengali)
            if (isBn) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.FontDownload,
                                contentDescription = "Bengali Font",
                                tint = SlatePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = getString("bengali_font", isBn),
                                fontFamily = fontToUse,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = getString("bengali_font_desc", isBn),
                            fontFamily = fontToUse,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "tiro" to "Tiro Bangla",
                                "anek" to "Anek Bangla"
                            ).forEach { (code, name) ->
                                val selected = bengaliFontPreference == code
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setBengaliFontPreference(code) },
                                    label = { Text(name, fontFamily = fontToUse, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SlatePrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
