package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Translate
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
fun SettingsLanguageScreen(
    viewModel: FactCheckViewModel,
    onBack: () -> Unit
) {
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val responseLanguage by viewModel.responseLanguage.collectAsStateWithLifecycle()
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
            titleBn = getString("language", true),
            titleEn = getString("language", false),
            subtitleBn = "অ্যাপ ও এআই এর ভাষা পরিবর্তন করুন",
            subtitleEn = "Change App and AI language",
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
            // App UI Language Preference Card
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
                            Icons.Default.Translate,
                            contentDescription = "UI Language",
                            tint = SlatePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = getString("app_language", isBn),
                            fontFamily = fontToUse,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = getString("app_language_desc", isBn),
                        fontFamily = fontToUse,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "bn" to if (isBn) "বাংলা" else "Bengali",
                            "en" to "English"
                        ).forEach { (code, name) ->
                            val selected = renderingLanguage == code
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setRenderingLanguage(code) },
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

            // AI Response Language Preference Card
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
                            Icons.Default.Language,
                            contentDescription = "Response Language",
                            tint = SlatePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = getString("response_language", isBn),
                            fontFamily = fontToUse,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = getString("response_language_desc", isBn),
                        fontFamily = fontToUse,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            com.example.data.preferences.ResponseLanguage.AUTO to if (isBn) "স্বয়ংক্রিয়" else "Auto",
                            com.example.data.preferences.ResponseLanguage.BANGLA to if (isBn) "বাংলা" else "Bengali",
                            com.example.data.preferences.ResponseLanguage.ENGLISH to if (isBn) "ইংরেজি" else "English"
                        ).forEach { (code, name) ->
                            val selected = responseLanguage == code
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setResponseLanguage(code) },
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
