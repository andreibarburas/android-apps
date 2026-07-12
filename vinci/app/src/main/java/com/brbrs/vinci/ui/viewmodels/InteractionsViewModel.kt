package com.brbrs.vinci.ui.viewmodels

import com.brbrs.vinci.data.CallLogDao
import com.brbrs.vinci.data.CallLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class InteractionsUiState(
    // Groups of (month label, logs) ordered most-recent month first
    val groups: List<Pair<String, List<CallLogEntity>>> = emptyList(),
    val total: Int = 0,
)

@HiltViewModel
class InteractionsViewModel @Inject constructor(
    private val callLogDao: CallLogDao,
) : ViewModel() {

    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    val uiState: StateFlow<InteractionsUiState> = callLogDao.getAllLogs().map { logs ->
        val groups = logs
            .groupBy { monthFormat.format(Date(it.callTimestamp)) }
            .toList() // preserves encounter order; logs are already DESC by timestamp so months come most-recent-first
        InteractionsUiState(groups = groups, total = logs.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InteractionsUiState())
}
