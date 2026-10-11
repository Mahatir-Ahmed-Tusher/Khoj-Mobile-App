package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.NewsArticle
import com.example.data.repository.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class NewsUiState {
    object Loading : NewsUiState()
    data class Success(val articles: List<NewsArticle>) : NewsUiState()
    data class Error(val message: String) : NewsUiState()
}

class NewsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadNews()
    }

    fun loadNews() {
        viewModelScope.launch {
            _uiState.value = NewsUiState.Loading
            val result = NewsRepository.getNews()
            result.fold(
                onSuccess = { articles ->
                    _uiState.value = NewsUiState.Success(articles)
                },
                onFailure = { e ->
                    _uiState.value = NewsUiState.Error(
                        e.message ?: "Failed to load news"
                    )
                }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = NewsRepository.getNews()
            result.fold(
                onSuccess = { articles ->
                    _uiState.value = NewsUiState.Success(articles)
                },
                onFailure = { e ->
                    // On pull-to-refresh failure, keep existing content if available
                    if (_uiState.value !is NewsUiState.Success) {
                        _uiState.value = NewsUiState.Error(
                            e.message ?: "Failed to refresh news"
                        )
                    }
                }
            )
            _isRefreshing.value = false
        }
    }
}
