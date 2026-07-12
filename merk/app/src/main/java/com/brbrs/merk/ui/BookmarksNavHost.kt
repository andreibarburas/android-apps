package com.brbrs.merk.ui

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.*
import androidx.navigation.compose.*
import com.brbrs.merk.auth.AuthViewModel
import com.brbrs.merk.tasks.TasksPreference
import com.brbrs.merk.ui.screens.applock.AppLockScreen
import com.brbrs.merk.ui.screens.detail.BookmarkDetailScreen
import com.brbrs.merk.ui.screens.detail.BookmarkDetailViewModel
import com.brbrs.merk.ui.screens.edit.EditBookmarkScreen
import com.brbrs.merk.ui.screens.list.BookmarkListScreen
import com.brbrs.merk.ui.screens.login.LoginScreen
import com.brbrs.merk.ui.screens.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(tasksPref: TasksPreference) : ViewModel() {
    val tasksEnabled = tasksPref.enabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}

object Routes {
    const val APP_LOCK = "app_lock"
    const val LOGIN    = "login"
    const val LIST     = "list"
    const val EDIT     = "edit/{bookmarkId}"
    const val ADD      = "edit/-1"
    const val DETAIL   = "detail/{bookmarkId}"
    const val SETTINGS = "settings"

    fun edit(id: Long)   = "edit/$id"
    fun detail(id: Long) = "detail/$id"
}

@Composable
fun BookmarksNavHost(
    sharedUrl:   String? = null,
    sharedTitle: String? = null,
) {
    val navController   = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()
    val tasksVm: TasksViewModel = hiltViewModel()
    val authState    by authVm.authState.collectAsState()
    val tasksEnabled by tasksVm.tasksEnabled.collectAsState()

    val startDest = when {
        !authState.isLoggedIn      -> Routes.LOGIN
        authState.biometricEnabled -> Routes.APP_LOCK
        else                       -> Routes.LIST
    }

    LaunchedEffect(sharedUrl, authState.isLoggedIn) {
        if (sharedUrl != null && authState.isLoggedIn && !authState.biometricEnabled) {
            navController.navigate(Routes.ADD)
        }
    }

    NavHost(navController = navController, startDestination = startDest) {

        composable(Routes.APP_LOCK) {
            AppLockScreen(
                onUnlocked = {
                    val dest = if (sharedUrl != null) Routes.ADD else Routes.LIST
                    navController.navigate(dest) { popUpTo(Routes.APP_LOCK) { inclusive = true } }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.LIST) { popUpTo(Routes.LOGIN) { inclusive = true } }
                }
            )
        }

        composable(Routes.LIST) {
            BookmarkListScreen(
                onAdd      = { navController.navigate(Routes.ADD) },
                onEdit     = { id -> navController.navigate(Routes.edit(id)) },
                onOpen     = { id -> navController.navigate(Routes.detail(id)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route     = Routes.DETAIL,
            arguments = listOf(navArgument("bookmarkId") { type = NavType.LongType }),
        ) { back ->
            val id = back.arguments?.getLong("bookmarkId") ?: return@composable
            val vm: BookmarkDetailViewModel = hiltViewModel()
            val state by vm.uiState.collectAsState()
            LaunchedEffect(id) { vm.load(id) }
            state.bookmark?.let { bm ->
                BookmarkDetailScreen(
                    url          = bm.url,
                    title        = bm.title,
                    tasksEnabled = tasksEnabled,
                    onBack       = { navController.popBackStack() },
                    onEdit       = {
                        navController.navigate(Routes.edit(id)) {
                            popUpTo(Routes.detail(id)) { inclusive = true }
                        }
                    },
                )
            }
        }

        composable(
            route     = Routes.EDIT,
            arguments = listOf(navArgument("bookmarkId") { type = NavType.LongType }),
        ) { back ->
            val bookmarkId = back.arguments?.getLong("bookmarkId") ?: -1L
            EditBookmarkScreen(
                prefillUrl   = if (bookmarkId == -1L) sharedUrl else null,
                prefillTitle = if (bookmarkId == -1L) sharedTitle else null,
                tasksEnabled = tasksEnabled,
                onSaved      = { navController.popBackStack() },
                onDismiss    = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack      = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
            )
        }
    }
}
