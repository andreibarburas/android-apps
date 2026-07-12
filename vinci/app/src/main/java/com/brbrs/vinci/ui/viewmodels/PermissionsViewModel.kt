package com.brbrs.vinci.ui.viewmodels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PermissionItem(
    val permission: String,
    val title: String,
    val rationale: String,
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
                    permission  = Manifest.permission.READ_CONTACTS,
                    title       = "Read contacts",
                    rationale   = "Required to show your contacts and match calls to people in your address book.",
                    isEssential = true,
                    isGranted   = isGranted(Manifest.permission.READ_CONTACTS),
                )
            )
            add(
                PermissionItem(
                    permission  = Manifest.permission.WRITE_CONTACTS,
                    title       = "Edit contacts",
                    rationale   = "Required to edit contact details directly from Vinci. Changes sync back to Nextcloud via DAVx5.",
                    isEssential = true,
                    isGranted   = isGranted(Manifest.permission.WRITE_CONTACTS),
                )
            )
            add(
                PermissionItem(
                    permission  = Manifest.permission.READ_CALL_LOG,
                    title       = "Call log",
                    rationale   = "Needed to detect when a call ends and prompt you to log it automatically.",
                    isEssential = true,
                    isGranted   = isGranted(Manifest.permission.READ_CALL_LOG),
                )
            )
            add(
                PermissionItem(
                    permission  = Manifest.permission.READ_PHONE_STATE,
                    title       = "Phone state",
                    rationale   = "Needed to detect incoming and outgoing calls in the background.",
                    isEssential = true,
                    isGranted   = isGranted(Manifest.permission.READ_PHONE_STATE),
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionItem(
                        permission  = Manifest.permission.POST_NOTIFICATIONS,
                        title       = "Notifications",
                        rationale   = "Used to show a prompt after a call ends so you never forget to log it.",
                        isEssential = false,
                        isGranted   = isGranted(Manifest.permission.POST_NOTIFICATIONS),
                    )
                )
            }
        }

        _uiState.update {
            it.copy(
                permissions          = items,
                allEssentialGranted  = items.filter { p -> p.isEssential }.all { p -> p.isGranted },
            )
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
