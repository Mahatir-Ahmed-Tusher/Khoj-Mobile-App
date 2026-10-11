package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.provider.HelpContentProvider
import com.example.ui.theme.AnekBanglaFamily
import com.example.ui.theme.SlatePrimary
import com.example.ui.theme.TiroBanglaFamily
import com.example.ui.components.MarkdownText
import com.example.ui.viewmodel.FactCheckViewModel

@Composable
fun HelpArticleScreen(
    articleId: String,
    viewModel: FactCheckViewModel,
    onBack: () -> Unit
) {
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val bengaliFontPreference by viewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    val fontToUse = if (isBn) {
        if (bengaliFontPreference == "anek") AnekBanglaFamily else TiroBanglaFamily
    } else {
        androidx.compose.ui.text.font.FontFamily.Default
    }
    
    val article = remember(articleId) {
        HelpContentProvider.getArticles().find { it.id == articleId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            titleBn = getString("help_center", true),
            titleEn = getString("help_center", false),
            subtitleBn = article?.titleBn ?: "",
            subtitleEn = article?.titleEn ?: "",
            isBn = isBn,
            fontToUse = fontToUse,
            onBack = onBack
        )

        if (article == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = getString("no_articles_found", isBn),
                    fontFamily = fontToUse,
                    fontSize = 13.sp
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (isBn) article.titleBn else article.titleEn,
                    fontFamily = fontToUse,
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = SlatePrimary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                MarkdownText(
                    text = if (isBn) article.contentBn else article.contentEn,
                    fontToUse = fontToUse,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14,
                    lineHeight = 22
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
