package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SupabaseNotification
import com.example.data.repository.AuthRepository
import com.example.data.repository.NotificationRepository
import com.example.data.repository.NotificationTarget
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationViewModel : ViewModel() {

    private val _notifications = MutableStateFlow<List<SupabaseNotification>>(emptyList())
    val notifications: StateFlow<List<SupabaseNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isResolvingTarget = MutableStateFlow(false)
    val isResolvingTarget: StateFlow<Boolean> = _isResolvingTarget.asStateFlow()

    private var lastRefreshTime: Long = 0L
    private var activeUserId: String? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        viewModelScope.launch {
            try {
                AuthRepository.sessionFlow.collect { status ->
                    if (status is SessionStatus.Authenticated) {
                        val userId = AuthRepository.currentUserId
                        if (userId != null && userId != activeUserId) {
                            activeUserId = userId
                            load(forceRefresh = true)
                        } else if (userId != null && _notifications.value.isEmpty()) {
                            load(forceRefresh = false)
                        }
                    } else if (status is SessionStatus.NotAuthenticated) {
                        activeUserId = null
                        _notifications.value = emptyList()
                        _unreadCount.value = 0
                        NotificationRepository.invalidateCache()
                    }
                }
            } catch (e: Throwable) {
                Log.e("NotificationViewModel", "Session flow collection error: ${e.message}", e)
            }
        }
    }

    fun load(forceRefresh: Boolean = false) {
        val userId = AuthRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = NotificationRepository.fetchNotifications(userId, forceRefresh = forceRefresh)
                _notifications.value = list
                val count = NotificationRepository.getUnreadCount(userId, forceRefresh = forceRefresh)
                _unreadCount.value = count
            } catch (e: Throwable) {
                Log.e("NotificationViewModel", "Failed to load notifications: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < com.example.data.local.CachePolicy.DEBOUNCE_WINDOW_MS) {
            return
        }
        lastRefreshTime = now
        load(forceRefresh = true)
    }

    fun markRead(notification: SupabaseNotification) {
        val userId = AuthRepository.currentUserId ?: return
        viewModelScope.launch {
            // Optimistic local update
            _notifications.value = _notifications.value.map { n ->
                if (n.id == notification.id) n.copy(isRead = true) else n
            }
            _unreadCount.value = (_unreadCount.value - 1).coerceAtLeast(0)

            NotificationRepository.markAsRead(
                notificationId = notification.id,
                userId = userId,
                isBroadcast = notification.isBroadcast
            )
        }
    }

    fun markRead(id: String, isBroadcast: Boolean = false) {
        val userId = AuthRepository.currentUserId ?: return
        viewModelScope.launch {
            _notifications.value = _notifications.value.map { n ->
                if (n.id == id) n.copy(isRead = true) else n
            }
            _unreadCount.value = (_unreadCount.value - 1).coerceAtLeast(0)

            NotificationRepository.markAsRead(
                notificationId = id,
                userId = userId,
                isBroadcast = isBroadcast
            )
        }
    }

    fun markAllRead() {
        val userId = AuthRepository.currentUserId ?: return
        viewModelScope.launch {
            // Optimistic local update
            _notifications.value = _notifications.value.map { it.copy(isRead = true) }
            _unreadCount.value = 0

            NotificationRepository.markAllRead(userId)
        }
    }

    fun resolveTarget(notification: SupabaseNotification, onResolved: (NotificationTarget) -> Unit) {
        // Mark read when tapped
        if (!notification.isRead) {
            markRead(notification)
        }

        viewModelScope.launch {
            _isResolvingTarget.value = true
            try {
                val target = NotificationRepository.resolveNotificationTarget(
                    postId = notification.postId,
                    type = notification.type
                )
                onResolved(target)
            } catch (e: Throwable) {
                Log.e("NotificationViewModel", "Failed to resolve notification target: ${e.message}", e)
                onResolved(NotificationTarget.Unknown)
            } finally {
                _isResolvingTarget.value = false
            }
        }
    }
}
