package com.brbrs.merk.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AuthState(
    val isLoggedIn: Boolean = false,
    val biometricEnabled: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {

    val authState = combine(
        authManager.credentials,
        authManager.biometricEnabled,
    ) { creds, bio ->
        AuthState(isLoggedIn = creds != null, biometricEnabled = bio)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AuthState())
}
