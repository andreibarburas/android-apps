package com.brbrs.runa

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brbrs.runa.auth.AuthRepository
import com.brbrs.runa.data.repository.SyncRepository
import com.brbrs.runa.ui.navigation.RunaRoute
import com.brbrs.runa.ui.screens.HomeScreen
import com.brbrs.runa.ui.screens.applock.AppLockScreen
import com.brbrs.runa.ui.screens.detail.EntryDetailScreen
import com.brbrs.runa.ui.screens.login.LoginScreen
import com.brbrs.runa.ui.screens.settings.SettingsScreen
import com.brbrs.runa.ui.screens.storagechoice.StorageChoiceScreen
import com.brbrs.runa.ui.screens.write.EditEntryScreen
import com.brbrs.runa.ui.theme.DisplayPreferencesRepository
import com.brbrs.runa.ui.theme.RunaTheme
import com.brbrs.runa.ui.theme.ThemeMode
import com.brbrs.runa.ui.theme.textSizeMultiplier
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Extract any shared image URIs from the launching intent
        val sharedUris = extractSharedImageUris(intent)

        setContent {
            val rootViewModel: RootViewModel = hiltViewModel()
            rootViewModel.syncOnOpen()
            val state by rootViewModel.uiState.collectAsState()

            val themeMode = when (state.themeMode) {
                "light" -> ThemeMode.LIGHT
                "dark"  -> ThemeMode.DARK
                else    -> ThemeMode.SYSTEM
            }

            RunaTheme(
                themeMode     = themeMode,
                textScale     = textSizeMultiplier(state.textSize),
                useCustomFont = state.useCustomFont,
            ) {
                if (state.isReady) {
                    RunaNavHost(sharedUris = sharedUris)
                }
            }
        }
    }
}

/**
 * Extracts image URI(s) from ACTION_SEND / ACTION_SEND_MULTIPLE intents.
 * Returns an empty list for normal launches.
 */
private fun extractSharedImageUris(intent: Intent?): List<Uri> {
    if (intent == null) return emptyList()
    return when (intent.action) {
        Intent.ACTION_SEND -> {
            val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
            listOfNotNull(uri)
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            val uris = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
            uris?.filterIsInstance<Uri>() ?: emptyList()
        }
        else -> emptyList()
    }
}

@androidx.compose.runtime.Composable
private fun RunaNavHost(sharedUris: List<Uri> = emptyList()) {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = RunaRoute.AppLock.route,
        modifier         = Modifier.fillMaxSize(),
    ) {

        composable(RunaRoute.AppLock.route) {
            AppLockScreen(
                onUnlocked       = {
                    navController.navigate(RunaRoute.Home.route) {
                        popUpTo(RunaRoute.AppLock.route) { inclusive = true }
                    }
                },
                onNoLock         = {
                    navController.navigate(RunaRoute.StorageChoice.route) {
                        popUpTo(RunaRoute.AppLock.route) { inclusive = true }
                    }
                },
                onLoggedInNoLock = {
                    navController.navigate(RunaRoute.Home.route) {
                        popUpTo(RunaRoute.AppLock.route) { inclusive = true }
                    }
                },
            )
        }

        composable(RunaRoute.StorageChoice.route) {
            StorageChoiceScreen(
                onConnectNextcloud = { navController.navigate(RunaRoute.Login.route) },
                onUseLocally = {
                    navController.navigate(RunaRoute.Home.route) {
                        popUpTo(RunaRoute.StorageChoice.route) { inclusive = true }
                    }
                },
            )
        }

        composable(RunaRoute.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(RunaRoute.Home.route) {
                        popUpTo(RunaRoute.StorageChoice.route) { inclusive = true }
                    }
                },
            )
        }

        composable(RunaRoute.Home.route) {
            HomeScreen(
                onEntryClick = { id -> navController.navigate(RunaRoute.EntryDetail.createRoute(id)) },
                onSettings   = { navController.navigate(RunaRoute.Settings.route) },
                sharedUris   = sharedUris,
            )
        }

        composable(
            route     = RunaRoute.EntryDetail.route,
            arguments = listOf(navArgument(RunaRoute.ENTRY_ID_ARG) { type = NavType.StringType }),
        ) { backStack ->
            val entryId = backStack.arguments?.getString(RunaRoute.ENTRY_ID_ARG) ?: return@composable
            EntryDetailScreen(
                entryId = entryId,
                onBack  = { navController.popBackStack() },
                onEdit  = { id -> navController.navigate(RunaRoute.EditEntry.createRoute(id)) },
            )
        }

        composable(
            route     = RunaRoute.EditEntry.route,
            arguments = listOf(navArgument(RunaRoute.ENTRY_ID_ARG) { type = NavType.StringType }),
        ) { backStack ->
            val entryId = backStack.arguments?.getString(RunaRoute.ENTRY_ID_ARG) ?: return@composable
            EditEntryScreen(
                entryId = entryId,
                onBack  = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(RunaRoute.Settings.route) {
            SettingsScreen(
                onBack             = { navController.popBackStack() },
                onSignedOut        = { navController.popBackStack() },
                onConnectNextcloud = { navController.navigate(RunaRoute.LoginFromSettings.route) },
            )
        }

        composable(RunaRoute.LoginFromSettings.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.popBackStack(RunaRoute.Settings.route, inclusive = false)
                },
            )
        }
    }
}

// ── Root state ────────────────────────────────────────────────────────────────

data class RootUiState(
    val themeMode: String      = "system",
    val textSize: String       = "default",
    val useCustomFont: Boolean = false,
    val isReady: Boolean       = false,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    displayPrefs: DisplayPreferencesRepository,
    authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    val uiState = combine(
        displayPrefs.preferences,
        authRepository.storageMode,
    ) { display, _ ->
        RootUiState(
            themeMode     = display.themeMode,
            textSize      = display.textSize,
            useCustomFont = display.useCustomFont,
            isReady       = true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootUiState())

    /** Triggered once from MainActivity.onCreate — silently syncs if connected. */
    fun syncOnOpen() {
        viewModelScope.launch { syncRepository.sync() }
    }
}
