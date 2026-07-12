package com.brbrs.nota.ui

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brbrs.nota.ui.screens.*
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    object AppLock    : Screen("app_lock")
    object Login      : Screen("login")
    object NotesList  : Screen("notes_list")
    object NoteEditor : Screen("note_editor/{noteId}?category={category}&sharedText={sharedText}&sharedImageUri={sharedImageUri}") {
        fun route(
            noteId: Long,
            category: String = "",
            sharedText: String = "",
            sharedImageUri: String = "",
        ) = "note_editor/$noteId" +
            "?category=${URLEncoder.encode(category, "UTF-8")}" +
            "&sharedText=${URLEncoder.encode(sharedText, "UTF-8")}" +
            "&sharedImageUri=${URLEncoder.encode(sharedImageUri, "UTF-8")}"
    }
    object Settings : Screen("settings")
}

@Composable
fun NotaNavGraph(
    sharedText: String? = null,
    sharedImageUri: String? = null,
) {
    val navController = rememberNavController()

    fun navigateToEditor() {
        navController.navigate(
            Screen.NoteEditor.route(
                noteId         = -1L,
                sharedText     = sharedText ?: "",
                sharedImageUri = sharedImageUri ?: "",
            )
        )
    }

    NavHost(navController = navController, startDestination = Screen.AppLock.route) {

        composable(Screen.AppLock.route) {
            val hasShared = sharedText != null || sharedImageUri != null
            AppLockScreen(
                onUnlocked = {
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(Screen.AppLock.route) { inclusive = true }
                    }
                    if (hasShared) navigateToEditor()
                },
                onNoLock = {
                    if (hasShared) {
                        navController.navigate(Screen.NotesList.route) {
                            popUpTo(Screen.AppLock.route) { inclusive = true }
                        }
                        navigateToEditor()
                    } else {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.AppLock.route) { inclusive = true }
                        }
                    }
                },
                onLoggedInNoLock = {
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(Screen.AppLock.route) { inclusive = true }
                    }
                    if (hasShared) navigateToEditor()
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.NotesList.route) {
            NotesListScreen(
                onNoteClick = { noteId ->
                    navController.navigate(Screen.NoteEditor.route(noteId))
                },
                onNewNote = { category ->
                    navController.navigate(Screen.NoteEditor.route(-1L, category))
                },
                onSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.NoteEditor.route,
            arguments = listOf(
                navArgument("noteId")         { type = NavType.LongType },
                navArgument("category")       { type = NavType.StringType; defaultValue = "" },
                navArgument("sharedText")     { type = NavType.StringType; defaultValue = "" },
                navArgument("sharedImageUri") { type = NavType.StringType; defaultValue = "" },
            )
        ) { backStack ->
            val noteId        = backStack.arguments?.getLong("noteId") ?: -1L
            val category      = URLDecoder.decode(backStack.arguments?.getString("category")       ?: "", "UTF-8")
            val shared        = URLDecoder.decode(backStack.arguments?.getString("sharedText")     ?: "", "UTF-8")
            val sharedImgUri  = URLDecoder.decode(backStack.arguments?.getString("sharedImageUri") ?: "", "UTF-8")
            NoteEditorScreen(
                noteId          = noteId,
                initialCategory = category,
                sharedText      = shared,
                sharedImageUri  = sharedImgUri,
                onBack          = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
