package com.brbrs.qarib.ui.screens.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.brbrs.qarib.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PermissionItem(
    val permission: String,
    val titleRes: Int,
    val rationaleRes: Int,
    val isEssential: Boolean,
    val isGranted: Boolean = false,
)

data class PermissionsUiState(
    val permissions: List<PermissionItem> = emptyList(),
    val allEssentialGranted: Boolean = false,
)

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        val items = buildList {
            add(
                PermissionItem(
                    permission = Manifest.permission.ACCESS_FINE_LOCATION,
                    titleRes = R.string.permission_location_title,
                    rationaleRes = R.string.permission_location_rationale,
                    isEssential = true,
                    isGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(
                    PermissionItem(
                        permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        titleRes = R.string.permission_background_location_title,
                        rationaleRes = R.string.permission_background_location_rationale,
                        isEssential = true,
                        isGranted = isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    )
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionItem(
                        permission = Manifest.permission.POST_NOTIFICATIONS,
                        titleRes = R.string.permission_notifications_title,
                        rationaleRes = R.string.permission_notifications_rationale,
                        isEssential = false,
                        isGranted = isGranted(Manifest.permission.POST_NOTIFICATIONS),
                    )
                )
            }
        }

        _uiState.update {
            it.copy(
                permissions = items,
                allEssentialGranted = items.filter { p -> p.isEssential }.all { p -> p.isGranted },
            )
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
