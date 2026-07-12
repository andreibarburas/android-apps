package com.brbrs.merk.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.merk.data.local.BookmarkEntity
import com.brbrs.merk.data.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(val bookmark: BookmarkEntity? = null)

@HiltViewModel
class BookmarkDetailViewModel @Inject constructor(
    private val repo: BookmarkRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState = _uiState.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch {
            _uiState.value = DetailUiState(bookmark = repo.getById(id))
        }
    }
}
