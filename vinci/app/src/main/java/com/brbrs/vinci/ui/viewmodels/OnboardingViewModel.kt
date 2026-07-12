package com.brbrs.vinci.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.vinci.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** null = not yet known, true/false once read from DataStore. */
    val onboardingDone: StateFlow<Boolean?> = authRepository.onboardingDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markDone() {
        viewModelScope.launch { authRepository.setOnboardingDone() }
    }
}
