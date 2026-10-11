package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FactCheckDatabase
import com.example.data.model.FeedItem
import com.example.data.model.NewsArticle
import com.example.data.model.SupabaseComment
import com.example.data.repository.AuthRepository
import com.example.data.repository.FeedRepository
import com.example.data.repository.InteractionRepository
import com.example.data.repository.NewsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class FeedUiState {
    object Loading : FeedUiState()
    data class Success(val items: List<FeedItem>, val hasMore: Boolean = true) : FeedUiState()
    data class Error(val message: String) : FeedUiState()
}

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val feedDao = FactCheckDatabase.getDatabase(application).feedDao()

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _hasNewPosts = MutableStateFlow(false)
    val hasNewPosts: StateFlow<Boolean> = _hasNewPosts.asStateFlow()
    private var pendingNewItems: List<FeedItem>? = null
    var isScrolledDown: Boolean = false

    private val _comments = MutableStateFlow<List<SupabaseComment>>(emptyList())
    val comments: StateFlow<List<SupabaseComment>> = _comments.asStateFlow()

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // Pagination cursors
    private var reportCursor: String? = null
    private var articleCursor: String? = null

    // News caching for interleaving
    private var cachedNews = emptyList<NewsArticle>()
    private var newsIndex = 0
    private var lastFeedFetchTime: Long = 0L

    init { 
        loadCachedFeedAndSync()
    }

    private fun loadCachedFeedAndSync() {
        viewModelScope.launch {
            try {
                val cached = feedDao.getFeedList(50)
                if (cached.isNotEmpty()) {
                    val items = cached.mapNotNull { FeedRepository.entityToFeedItem(it) }
                    if (items.isNotEmpty()) {
                        _uiState.value = FeedUiState.Success(items, hasMore = true)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FeedViewModel", "Failed to load cached feed", e)
            }
            // Silent background synchronization
            fetchFeedAndSearchResults(isRefresh = true, isSilent = true)
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        fetchFeedAndSearchResults(isRefresh = true, isSilent = false)
    }

    fun loadFeed(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && _searchQuery.value.isEmpty() && (now - lastFeedFetchTime < 60_000L) && _uiState.value is FeedUiState.Success) {
            // Respect 60s TTL if we already have valid content
            return
        }
        setSearchQuery("")
    }

    fun applyNewPosts() {
        val pending = pendingNewItems ?: return
        _uiState.value = FeedUiState.Success(pending, hasMore = true)
        pendingNewItems = null
        _hasNewPosts.value = false
    }

    private fun fetchFeedAndSearchResults(isRefresh: Boolean = true, isSilent: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val query = _searchQuery.value.trim()
            
            if (isRefresh) {
                if (!isSilent) {
                    _isRefreshing.value = true
                    if (_uiState.value !is FeedUiState.Success) {
                        _uiState.value = FeedUiState.Loading
                    }
                }
                reportCursor = null
                articleCursor = null
            } else {
                _isLoadingMore.value = true
            }

            try {
                if (query.isEmpty()) {
                    // Fetch DB page and News IN PARALLEL with a short 3.5s timeout on News
                    val (result, freshNews) = kotlinx.coroutines.supervisorScope {
                        val dbDeferred = async {
                            FeedRepository.buildFeedPage(
                                currentUserId = AuthRepository.currentUserId,
                                reportCursor = reportCursor,
                                articleCursor = articleCursor
                            )
                        }

                        val newsDeferred = if (isRefresh) {
                            async {
                                withTimeoutOrNull(3500L) {
                                    runCatching { NewsRepository.getNews().getOrNull() }.getOrNull()
                                }
                            }
                        } else null

                        val res = dbDeferred.await()
                        val news = newsDeferred?.await()
                        Pair(res, news)
                    }

                    if (freshNews != null && freshNews.isNotEmpty()) {
                        cachedNews = freshNews.shuffled()
                        newsIndex = 0
                    }
                    
                    result.fold(
                        onSuccess = { (dbItems, nextCursors) ->
                            lastFeedFetchTime = System.currentTimeMillis()
                            reportCursor = nextCursors.first
                            articleCursor = nextCursors.second
                            
                            val hasMore = dbItems.isNotEmpty() 
                            
                            val processedItems = mutableListOf<FeedItem>()
                            val newsIter = cachedNews.drop(newsIndex).iterator()
                            
                            dbItems.forEachIndexed { idx, item ->
                                processedItems.add(item)
                                if ((idx + 1) % 2 == 0 && newsIter.hasNext()) {
                                    processedItems.add(FeedItem.NewsItem(newsIter.next()))
                                    newsIndex++
                                }
                            }
                            
                            if (dbItems.isEmpty() && newsIter.hasNext()) {
                                repeat(5) {
                                    if (newsIter.hasNext()) {
                                        processedItems.add(FeedItem.NewsItem(newsIter.next()))
                                        newsIndex++
                                    }
                                }
                            }

                            if (isRefresh) {
                                if (isScrolledDown && _uiState.value is FeedUiState.Success) {
                                    pendingNewItems = processedItems
                                    _hasNewPosts.value = true
                                } else {
                                    _uiState.value = FeedUiState.Success(processedItems, hasMore)
                                    _hasNewPosts.value = false
                                }

                                // Persist initial page to Room cache
                                val entities = processedItems.mapIndexedNotNull { idx, item ->
                                    val sortOrder = (processedItems.size - idx).toLong()
                                    FeedRepository.feedItemToEntity(item, sortOrder)
                                }
                                feedDao.replaceFeed(entities)
                            } else {
                                val currentItems = (_uiState.value as? FeedUiState.Success)?.items ?: emptyList()
                                val existingReportIds = currentItems.mapNotNull { (it as? FeedItem.ReportItem)?.report?.id }.toSet()
                                val existingArticleIds = currentItems.mapNotNull { (it as? FeedItem.ArticleItem)?.article?.slug }.toSet()
                                
                                val newItems = processedItems.filterNot { 
                                    when (it) {
                                        is FeedItem.ReportItem -> existingReportIds.contains(it.report.id)
                                        is FeedItem.ArticleItem -> existingArticleIds.contains(it.article.slug)
                                        else -> false
                                    }
                                }
                                val mergedList = currentItems + newItems
                                _uiState.value = FeedUiState.Success(mergedList, hasMore)

                                // Append to Room cache
                                val entities = newItems.mapIndexedNotNull { idx, item ->
                                    val sortOrder = (-(currentItems.size + idx)).toLong()
                                    FeedRepository.feedItemToEntity(item, sortOrder)
                                }
                                feedDao.insertFeedItems(entities)
                            }
                        },
                        onFailure = { e ->
                            android.util.Log.e("FeedViewModel", "Failed to load feed", e)
                            if (isRefresh && _uiState.value !is FeedUiState.Success) {
                                _uiState.value = FeedUiState.Error(e.message ?: "Failed to load feed")
                            }
                        }
                    )
                } else {
                    if (isRefresh) kotlinx.coroutines.delay(300)
                    val items = FeedRepository.searchFeedFts(AuthRepository.currentUserId, query)
                    _uiState.value = FeedUiState.Success(items, hasMore = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("FeedViewModel", "Failed to load feed outside repository", e)
                if (isRefresh && _uiState.value !is FeedUiState.Success) {
                    _uiState.value = FeedUiState.Error(e.message ?: "Failed to load feed")
                }
            } finally {
                _isLoadingMore.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState is FeedUiState.Success && currentState.hasMore && !_isLoadingMore.value && _searchQuery.value.trim().isEmpty()) {
            fetchFeedAndSearchResults(isRefresh = false)
        }
    }

    // ── Optimistic like toggle ────────────────────────────────────────────────

    fun toggleLike(postId: String) {
        val userId = AuthRepository.currentUserId ?: return
        val current = (_uiState.value as? FeedUiState.Success)?.items ?: return

        // Optimistic update
        val updated = current.map { item ->
            when {
                item is FeedItem.ReportItem && item.report.id == postId -> {
                    val nowLiked = !item.isLikedByMe
                    item.copy(
                        isLikedByMe = nowLiked,
                        likeCount = if (nowLiked) item.likeCount + 1 else item.likeCount - 1
                    )
                }
                item is FeedItem.ArticleItem && item.article.slug == postId -> {
                    val nowLiked = !item.isLikedByMe
                    item.copy(
                        isLikedByMe = nowLiked,
                        likeCount = if (nowLiked) item.likeCount + 1 else item.likeCount - 1
                    )
                }
                else -> item
            }
        }
        val hasMore = (_uiState.value as? FeedUiState.Success)?.hasMore ?: true
        _uiState.value = FeedUiState.Success(updated, hasMore)

        // Persist
        viewModelScope.launch {
            val isCurrentlyLiked = current.any { item ->
                (item is FeedItem.ReportItem && item.report.id == postId && item.isLikedByMe) ||
                (item is FeedItem.ArticleItem && item.article.slug == postId && item.isLikedByMe)
            }
            if (isCurrentlyLiked) {
                InteractionRepository.unlikePost(postId, userId)
            } else {
                InteractionRepository.likePost(postId, userId)
            }
        }
    }

    private var lastRefreshTime: Long = 0L

    fun refreshFeed() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < com.example.data.local.CachePolicy.DEBOUNCE_WINDOW_MS) {
            return
        }
        lastRefreshTime = now
        fetchFeedAndSearchResults(isRefresh = true)
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    fun loadComments(postId: String, cursor: String? = null) {
        viewModelScope.launch {
            _commentsLoading.value = true
            val fetched = InteractionRepository.fetchComments(postId, cursor = cursor, limit = 20)
            if (cursor == null) {
                _comments.value = fetched
            } else {
                val existingIds = _comments.value.map { it.id }.toSet()
                val newItems = fetched.filterNot { existingIds.contains(it.id) }
                _comments.value = _comments.value + newItems
            }
            _commentsLoading.value = false
        }
    }

    fun postComment(postId: String, text: String, parentId: String? = null, onResult: ((Boolean) -> Unit)? = null) {
        val userId = AuthRepository.currentUserId
        if (userId == null) {
            onResult?.invoke(false)
            return
        }
        if (text.isBlank()) {
            onResult?.invoke(false)
            return
        }
        viewModelScope.launch {
            val result = InteractionRepository.addComment(postId, userId, text, parentId)
            if (result.isSuccess) {
                loadComments(postId, cursor = null)
                onResult?.invoke(true)
            } else {
                onResult?.invoke(false)
            }
        }
    }
}
