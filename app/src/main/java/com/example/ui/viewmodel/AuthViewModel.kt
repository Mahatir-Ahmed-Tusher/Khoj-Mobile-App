package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SupabaseUser
import com.example.data.repository.AuthRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("khoj_settings", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<SupabaseUser?>(null)
    val currentUser: StateFlow<SupabaseUser?> = _currentUser.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(prefs.getBoolean("has_completed_onboarding", false))
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    val sessionStatus: StateFlow<SessionStatus> = AuthRepository.sessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionStatus.Initializing)

    val isLoggedIn: Boolean get() = AuthRepository.currentUserId != null

    init {
        loadProfile()
        viewModelScope.launch {
            try {
                AuthRepository.sessionFlow.collect { status ->
                    if (status is SessionStatus.Authenticated) {
                        setOnboardingCompleted()
                        loadProfile()
                    } else if (status is SessionStatus.NotAuthenticated) {
                        _currentUser.value = null
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("AuthViewModel", "Session status collection failed: ${e.message}")
            }
        }
    }

    fun setOnboardingCompleted() {
        if (!_hasCompletedOnboarding.value) {
            prefs.edit().putBoolean("has_completed_onboarding", true).apply()
            _hasCompletedOnboarding.value = true
        }
    }

    fun loadProfile() {
        val user = AuthRepository.getCurrentUser()
        if (user != null) {
            _currentUser.value = user
            com.example.data.local.NameTransliterationManager.precomputeAsync(getApplication(), user.displayName)
        } else {
            val userId = AuthRepository.currentUserId ?: return
            viewModelScope.launch {
                val profile = AuthRepository.getProfile(userId)
                _currentUser.value = profile
                com.example.data.local.NameTransliterationManager.precomputeAsync(getApplication(), profile?.displayName)
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = AuthRepository.signIn(email, password)
            if (result.isSuccess) {
                setOnboardingCompleted()
                loadProfile()
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Error(
                    result.exceptionOrNull()?.message ?: "Login failed. Please try again."
                )
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = AuthRepository.signInWithGoogle(idToken)
            if (result.isSuccess) {
                setOnboardingCompleted()
                loadProfile()
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Error(
                    result.exceptionOrNull()?.message ?: "Google Sign-In failed."
                )
            }
        }
    }

    fun signUp(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = AuthRepository.signUp(email, password, displayName)
            if (result.isSuccess) {
                setOnboardingCompleted()
                loadProfile()
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Error(
                    result.exceptionOrNull()?.message ?: "Registration failed. Please try again."
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            AuthRepository.signOut()
            com.example.data.repository.NotificationRepository.invalidateCache()
            _currentUser.value = null
            _authState.value = AuthState.Idle
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
