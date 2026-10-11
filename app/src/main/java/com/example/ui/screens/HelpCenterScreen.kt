package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HelpArticle
import com.example.data.provider.HelpContentProvider
import com.example.ui.theme.AnekBanglaFamily
import com.example.ui.theme.SlatePrimary
import com.example.ui.theme.TiroBanglaFamily
import com.example.ui.viewmodel.FactCheckViewModel

@Composable
fun HelpCenterScreen(
    viewModel: FactCheckViewModel,
    onNavigateToArticle: (String) -> Unit,
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
    
    var searchQuery by remember { mutableStateOf("") }
    
    val allArticles = remember { HelpContentProvider.getArticles() }
    val filteredArticles = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allArticles
        } else {
            val query = searchQuery.lowercase()
            allArticles.filter { article ->
                val titleMatch = if (isBn) article.titleBn.lowercase().contains(query) else article.titleEn.lowercase().contains(query)
                val contentMatch = if (isBn) article.contentBn.lowercase().contains(query) else article.contentEn.lowercase().contains(query)
                val tagMatch = article.tags.any { it.lowercase().contains(query) }
                titleMatch || contentMatch || tagMatch
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            titleBn = getString("help_center", true),
            titleEn = getString("help_center", false),
            subtitleBn = getString("how_can_we_help", true),
            subtitleEn = getString("how_can_we_help", false),
            isBn = isBn,
            fontToUse = fontToUse,
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(getString("search_help", isBn), fontFamily = fontToUse, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = LocalTextStyle.current.copy(fontFamily = fontToUse, fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SlatePrimary,
                    cursorColor = SlatePrimary
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (filteredArticles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getString("no_articles_found", isBn),
                        fontFamily = fontToUse,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredArticles) { article ->
                        HelpArticleCard(article = article, isBn = isBn, fontToUse = fontToUse, onClick = { onNavigateToArticle(article.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun HelpArticleCard(article: HelpArticle, isBn: Boolean, fontToUse: androidx.compose.ui.text.font.FontFamily, onClick: () -> Unit) {
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
                        imageVector = Icons.Default.Article,
                        contentDescription = "Article",
                        tint = SlatePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = if (isBn) article.titleBn else article.titleEn,
                    fontFamily = fontToUse,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Read",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
