package com.brbrs.qarib

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.brbrs.qarib.auth.AuthRepository
import com.brbrs.qarib.data.sync.SyncScheduler
import com.brbrs.qarib.ui.navigation.QaribRoute
import com.brbrs.qarib.ui.screens.add.AddPlaceScreen
import com.brbrs.qarib.ui.screens.applock.AppLockScreen
import com.brbrs.qarib.ui.screens.edit.EditPlaceScreen
import com.brbrs.qarib.ui.screens.list.PlacesScreen
import com.brbrs.qarib.ui.screens.login.LoginScreen
import com.brbrs.qarib.ui.screens.permissions.PermissionsScreen
import com.brbrs.qarib.ui.screens.settings.SettingsScreen
import com.brbrs.qarib.ui.theme.DisplayPreferencesRepository
import com.brbrs.qarib.ui.theme.QaribTheme
import com.brbrs.qarib.ui.theme.ThemeMode
import com.brbrs.qarib.ui.theme.textSizeMultiplier
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// BiometricPrompt requires a FragmentActivity, not a plain ComponentActivity.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Silent background sync whenever the app returns to the
        // foreground — picks up changes made on other devices while this
        // one was backgrounded, without any visible loading state.
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                syncScheduler.syncNow()
            }
        })

        setContent {
            val rootViewModel: RootViewModel = hiltViewModel()
            val state by rootViewModel.uiState.collectAsState()

            val themeMode = when (state.themeMode) {
                "light" -> ThemeMode.LIGHT
                "dark"  -> ThemeMode.DARK
                else    -> ThemeMode.SYSTEM
            }
            val textScale = textSizeMultiplier(state.textSize)

            QaribTheme(themeMode = themeMode, textScale = textScale, useCustomFont = state.useCustomFont) {
                if (state.isReady) {
                    QaribNavHost()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun QaribNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = QaribRoute.AppLock.route,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(QaribRoute.AppLock.route) {
            AppLockScreen(
                onUnlocked = {
                    navController.navigate(QaribRoute.Permissions.route) {
                        popUpTo(QaribRoute.AppLock.route) { inclusive = true }
                    }
                },
                onNoLock = {
                    navController.navigate(QaribRoute.Login.route) {
                        popUpTo(QaribRoute.AppLock.route) { inclusive = true }
                    }
                },
                onLoggedInNoLock = {
                    navController.navigate(QaribRoute.Permissions.route) {
                        popUpTo(QaribRoute.AppLock.route) { inclusive = true }
                    }
                }
            )
        }
        composable(QaribRoute.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(QaribRoute.Permissions.route) {
                        popUpTo(QaribRoute.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(QaribRoute.Permissions.route) {
            PermissionsScreen(
                onAllGranted = {
                    navController.navigate(QaribRoute.Places.route) {
                        popUpTo(QaribRoute.Permissions.route) { inclusive = true }
                    }
                }
            )
        }
        composable(QaribRoute.Places.route) {
            PlacesScreen(
                onAddPlace = { query -> navController.navigate(QaribRoute.AddPlace.createRoute(query)) },
                onEditPlace = { placeId -> navController.navigate(QaribRoute.EditPlace.createRoute(placeId)) },
                onSettings = { navController.navigate(QaribRoute.Settings.route) }
            )
        }
        composable(
            route = QaribRoute.AddPlace.route,
            arguments = listOf(androidx.navigation.navArgument(QaribRoute.INITIAL_QUERY_ARG) {
                type = androidx.navigation.NavType.StringType
                defaultValue = ""
            })
        ) {
            AddPlaceScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(
            route = QaribRoute.EditPlace.route,
            arguments = listOf(androidx.navigation.navArgument(QaribRoute.PLACE_ID_ARG) {
                type = androidx.navigation.NavType.StringType
            })
        ) {
            EditPlaceScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(QaribRoute.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(QaribRoute.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

data class RootUiState(
    val themeMode: String = "system",
    val textSize: String = "default",
    val useCustomFont: Boolean = false,
    val isReady: Boolean = false
)

@HiltViewModel
class RootViewModel @Inject constructor(
    displayPrefs: DisplayPreferencesRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val uiState = combine(
        displayPrefs.preferences,
        authRepository.session
    ) { display, _ ->
        RootUiState(
            themeMode     = display.themeMode,
            textSize      = display.textSize,
            useCustomFont = display.useCustomFont,
            isReady       = true
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootUiState())
}
