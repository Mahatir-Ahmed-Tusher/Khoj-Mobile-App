package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.api.WebSearchResult
import com.example.data.model.WebSearchHistoryEntity
import com.example.ui.components.OfflineStateCard
import com.example.ui.viewmodel.WebSearchCategory
import com.example.ui.viewmodel.WebSearchViewModel
import com.example.utils.NetworkErrorHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSearchScreen(
    initialQuery: String,
    isBn: Boolean,
    fontToUse: FontFamily,
    onBack: () -> Unit,
    viewModel: WebSearchViewModel = viewModel()
) {
    val currentQuery by viewModel.currentQuery.collectAsStateWithLifecycle()
    val activeCategory by viewModel.activeCategory.collectAsStateWithLifecycle()
    val categoryStates by viewModel.categoryStates.collectAsStateWithLifecycle()
    val aiOverview by viewModel.aiOverview.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var searchInput by remember { mutableStateOf(initialQuery) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && currentQuery != initialQuery) {
            searchInput = initialQuery
            viewModel.initiateSearch(initialQuery)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            WebSearchDrawerContent(
                viewModel = viewModel,
                isBn = isBn,
                fontToUse = fontToUse,
                onClose = { scope.launch { drawerState.close() } },
                onHistoryItemClick = { query ->
                    searchInput = query
                    viewModel.initiateSearch(query)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchInput,
                                onValueChange = { searchInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .padding(end = 8.dp),
                                placeholder = {
                                    Text(
                                        if (isBn) "ওয়েবে খুঁজুন..." else "Search the web...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = fontToUse
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.TravelExplore,
                                        contentDescription = "Web Search",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchInput.isNotEmpty()) {
                                        IconButton(onClick = { searchInput = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                        viewModel.initiateSearch(searchInput)
                                    }
                                ),
                                shape = RoundedCornerShape(26.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontToUse)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                    
                    // Category Tabs
                    ScrollableTabRow(
                        selectedTabIndex = WebSearchCategory.values().indexOf(activeCategory),
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.background,
                        divider = {},
                        indicator = { tabPositions ->
                            if (activeCategory.ordinal < tabPositions.size) {
                                TabRowDefaults.Indicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeCategory.ordinal]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    ) {
                        WebSearchCategory.values().forEach { category ->
                            val isSelected = activeCategory == category
                            Tab(
                                selected = isSelected,
                                onClick = { viewModel.setCategory(category) },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = getCategoryIcon(category),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = getCategoryName(category, isBn),
                                            fontFamily = fontToUse,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val currentState = categoryStates[activeCategory]
                val isDeviceOnline = NetworkErrorHelper.isDeviceOnline(LocalContext.current)
                
                if (currentState == null || (currentState.results.isEmpty() && currentState.isLoading)) {
                    WebSearchSkeleton(activeCategory)
                } else if (currentState.results.isEmpty() && !currentState.isLoading) {
                    if (!isDeviceOnline || currentState.hasError) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OfflineStateCard(
                                isBn = isBn,
                                fontToUse = fontToUse,
                                featureName = if (isBn) "ওয়েব সার্চ" else "Web Search",
                                onRetry = { viewModel.retryActiveCategory() },
                                onBack = onBack
                            )
                        }
                    } else {
                        EmptyStateView(isBn, fontToUse)
                    }
                } else {
                    when (activeCategory) {
                        WebSearchCategory.IMAGES -> ImageResultsGrid(currentState.results, currentState.isLoading, { viewModel.loadNextPage() }, isBn, fontToUse)
                        WebSearchCategory.MAPS -> MapResultsList(currentState.results, currentState.isLoading, { viewModel.loadNextPage() }, isBn, fontToUse)
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (activeCategory == WebSearchCategory.GENERAL) {
                                    item {
                                        AiOverviewCard(
                                            overview = aiOverview,
                                            isLoading = isAiLoading,
                                            isBn = isBn,
                                            fontToUse = fontToUse
                                        )
                                    }
                                }
                                
                                items(
                                    items = currentState.results,
                                    key = { it.url + "_" + it.title }
                                ) { result ->
                                    WebSearchResultItem(result, fontToUse)
                                }
                                
                                if (currentState.isLoading && currentState.results.isNotEmpty()) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                } else if (!currentState.hasReachedEnd && currentState.results.isNotEmpty()) {
                                    item {
                                        LaunchedEffect(Unit) {
                                            viewModel.loadNextPage()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiOverviewCard(overview: String?, isLoading: Boolean, isBn: Boolean, fontToUse: FontFamily) {
    if (!isLoading && overview.isNullOrBlank()) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TravelExplore, contentDescription = "AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBn) "এআই সারসংক্ষেপ" else "AI Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontToUse,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isLoading) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.2f)))
                    Box(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.2f)))
                    Box(modifier = Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.2f)))
                }
            } else {
                com.example.ui.components.MarkdownText(
                    text = overview ?: "",
                    fontToUse = fontToUse
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isBn) "এআই দ্বারা তৈরি সারসংক্ষেপ। তথ্যের সত্যতা যাচাইয়ের জন্য উল্লেখিত উৎসগুলো চেক করুন।" else "AI-generated summary. Verify important information using the cited sources.",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = fontToUse,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun WebSearchResultItem(result: WebSearchResult, fontToUse: FontFamily) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.url))
                context.startActivity(intent)
            }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = result.url.substringBefore("://").let { result.url.substringAfter("://").substringBefore("/") },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            fontFamily = fontToUse,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = result.snippet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = fontToUse,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun ImageResultsGrid(results: List<WebSearchResult>, isLoading: Boolean, onLoadMore: () -> Unit, isBn: Boolean, fontToUse: FontFamily) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = results,
            key = { (it.imageUrl ?: it.url) + "_" + it.title }
        ) { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable {
                        if (result.source != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.source)))
                        } else {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url)))
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(result.imageUrl ?: result.url)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = result.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = result.title,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = fontToUse
                        )
                    }
                }
            }
        }
        
        if (isLoading && results.isNotEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        } else if (results.isNotEmpty()) {
            item {
                LaunchedEffect(Unit) { onLoadMore() }
            }
        }
    }
}

@Composable
fun MapResultsList(results: List<WebSearchResult>, isLoading: Boolean, onLoadMore: () -> Unit, isBn: Boolean, fontToUse: FontFamily) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = results,
            key = { it.url + "_" + it.title }
        ) { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url)))
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp).padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = result.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = fontToUse
                        )
                        if (result.snippet.isNotBlank()) {
                            Text(
                                text = result.snippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = fontToUse,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        
        if (isLoading && results.isNotEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        } else if (results.isNotEmpty()) {
            item {
                LaunchedEffect(Unit) { onLoadMore() }
            }
        }
    }
}

@Composable
fun WebSearchSkeleton(category: WebSearchCategory) {
    if (category == WebSearchCategory.IMAGES) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(12) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Gray.copy(alpha = 0.2f))
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(5) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.2f)))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.9f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.2f)))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.2f)))
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.2f)))
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(isBn: Boolean, fontToUse: FontFamily) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isBn) "কোনো ফলাফল পাওয়া যায়নি" else "No results found",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = fontToUse,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isBn) "অন্য কিছু লিখে চেষ্টা করুন" else "Try searching for something else",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = fontToUse,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun WebSearchDrawerContent(
    viewModel: WebSearchViewModel,
    isBn: Boolean,
    fontToUse: FontFamily,
    onClose: () -> Unit,
    onHistoryItemClick: (String) -> Unit
) {
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()
    
    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.85f),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isBn) "সার্চ হিস্ট্রি" else "Search History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontToUse,
                    modifier = Modifier.weight(1f)
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearAllHistory() }) {
                        Text(if (isBn) "মুছুন" else "Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            Divider()
            
            if (history.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isBn) "কোনো হিস্ট্রি নেই" else "No search history",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = fontToUse,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(
                        items = history,
                        key = { it.query }
                    ) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onHistoryItemClick(item.query) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = item.query,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = fontToUse,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.deleteHistoryItem(item.query) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getCategoryIcon(category: WebSearchCategory): ImageVector {
    return when (category) {
        WebSearchCategory.GENERAL -> Icons.Outlined.Language
        WebSearchCategory.IMAGES -> Icons.Outlined.Photo
        WebSearchCategory.NEWS -> Icons.Outlined.Newspaper
        WebSearchCategory.VIDEOS -> Icons.Outlined.OndemandVideo
        WebSearchCategory.MAPS -> Icons.Outlined.Map
    }
}

private fun getCategoryName(category: WebSearchCategory, isBn: Boolean): String {
    return when (category) {
        WebSearchCategory.GENERAL -> if (isBn) "সাধারণ" else "General"
        WebSearchCategory.IMAGES -> if (isBn) "ছবি" else "Images"
        WebSearchCategory.NEWS -> if (isBn) "খবর" else "News"
        WebSearchCategory.VIDEOS -> if (isBn) "ভিডিও" else "Videos"
        WebSearchCategory.MAPS -> if (isBn) "ম্যাপ" else "Maps"
    }
}
