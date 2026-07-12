package com.brbrs.vinci.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brbrs.vinci.ui.screens.*
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Onboarding      : Screen("onboarding")
    object AppLock         : Screen("app_lock")
    object Login           : Screen("login")
    object FolderPicker    : Screen("folder_picker")
    object Restore         : Screen("restore")
    object Permissions     : Screen("permissions")
    object Main            : Screen("main")
    object Settings        : Screen("settings")
    object ContactDetail   : Screen("contact_detail/{contactId}") {
        fun route(contactId: Long) = "contact_detail/$contactId"
    }
    object CallLog : Screen("call_log/{contactId}?phone={phone}&prefillTimestamp={prefillTimestamp}&prefillType={prefillType}") {
        fun route(
            contactId: Long,
            phone: String = "",
            prefillTimestamp: Long = 0L,
            prefillType: String = "Call",
        ) = "call_log/$contactId" +
            "?phone=${URLEncoder.encode(phone, "UTF-8")}" +
            "&prefillTimestamp=$prefillTimestamp" +
            "&prefillType=${URLEncoder.encode(prefillType, "UTF-8")}"
    }
    object EditInteraction : Screen("edit_interaction/{logId}") {
        fun route(logId: Long) = "edit_interaction/$logId"
    }
    object EditContact : Screen("edit_contact/{contactId}") {
        fun route(contactId: Long) = "edit_contact/$contactId"
    }
}

@Composable
fun VinciNavGraph(
    pendingContactId: Long? = null,
    pendingPhone: String = "",
    onPendingConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    // When a call notification is tapped, navigate to the log screen once the nav graph is ready.
    // contactId == -1 with a phone number means "unknown number" -- CallLogScreen handles
    // this as a no-contact interaction log.
    LaunchedEffect(pendingContactId, pendingPhone) {
        val contactId = pendingContactId ?: -1L
        if (pendingPhone.isNotBlank() || contactId >= 0) {
            navController.navigate(Screen.CallLog.route(contactId, pendingPhone, 0L, "Call"))
            onPendingConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Screen.Onboarding.route) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onContinue = { navController.navigate(Screen.AppLock.route) { popUpTo(Screen.Onboarding.route) { inclusive = true } } }
            )
        }

        composable(Screen.AppLock.route) {
            AppLockScreen(
                onUnlocked       = { navController.navigate(Screen.Main.route) { popUpTo(Screen.AppLock.route) { inclusive = true } } },
                onNoLock         = { navController.navigate(Screen.Login.route) { popUpTo(Screen.AppLock.route) { inclusive = true } } },
                onLoggedInNoLock = { navController.navigate(Screen.Main.route) { popUpTo(Screen.AppLock.route) { inclusive = true } } },
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Screen.FolderPicker.route) { popUpTo(Screen.Login.route) { inclusive = true } } }
            )
        }

        // After folder picker, show restore screen so returning users get their data back
        composable(Screen.FolderPicker.route) {
            FolderPickerScreen(
                onFolderConfirmed = { navController.navigate(Screen.Restore.route) { popUpTo(Screen.FolderPicker.route) { inclusive = true } } }
            )
        }

        // Restore screen — user can restore from Nextcloud or start fresh
        composable(Screen.Restore.route) {
            RestoreScreen(
                onDone = { navController.navigate(Screen.Permissions.route) { popUpTo(Screen.Restore.route) { inclusive = true } } }
            )
        }

        composable(Screen.Permissions.route) {
            PermissionsScreen(
                onAllGranted = { navController.navigate(Screen.Main.route) { popUpTo(Screen.Permissions.route) { inclusive = true } } }
            )
        }

        composable(Screen.Main.route) {
            MainScaffold(
                onContactClick    = { navController.navigate(Screen.ContactDetail.route(it)) },
                onLogCall         = { contactId, phone -> navController.navigate(Screen.CallLog.route(contactId, phone)) },
                onEditInteraction = { logId -> navController.navigate(Screen.EditInteraction.route(logId)) },
                onSettings        = { navController.navigate(Screen.Settings.route) },
                onAddContact      = { navController.navigate(Screen.EditContact.route(-1L)) },
            )
        }

        composable(
            route = Screen.ContactDetail.route,
            arguments = listOf(navArgument("contactId") { type = NavType.LongType }),
        ) { backStack ->
            val contactId = backStack.arguments?.getLong("contactId") ?: return@composable
            ContactDetailScreen(
                contactId        = contactId,
                onBack           = { navController.popBackStack() },
                onLogInteraction = { id, phone, ts, type -> navController.navigate(Screen.CallLog.route(id, phone, ts, type)) },
                onEditInteraction= { logId -> navController.navigate(Screen.EditInteraction.route(logId)) },
                onEditContact    = { id -> navController.navigate(Screen.EditContact.route(id)) },
            )
        }

        composable(
            route = Screen.CallLog.route,
            arguments = listOf(
                navArgument("contactId")        { type = NavType.LongType },
                navArgument("phone")            { type = NavType.StringType; defaultValue = "" },
                navArgument("prefillTimestamp") { type = NavType.LongType;   defaultValue = 0L },
                navArgument("prefillType")      { type = NavType.StringType; defaultValue = "Call" },
            ),
        ) { backStack ->
            val contactId        = backStack.arguments?.getLong("contactId") ?: return@composable
            val phone            = URLDecoder.decode(backStack.arguments?.getString("phone") ?: "", "UTF-8")
            val prefillTimestamp = backStack.arguments?.getLong("prefillTimestamp") ?: 0L
            val prefillType      = URLDecoder.decode(backStack.arguments?.getString("prefillType") ?: "Call", "UTF-8")
            CallLogScreen(
                contactId        = contactId,
                phone            = phone,
                prefillTimestamp = prefillTimestamp,
                prefillType      = prefillType,
                onSaved          = { navController.popBackStack() },
                onBack           = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.EditInteraction.route,
            arguments = listOf(navArgument("logId") { type = NavType.LongType }),
        ) { backStack ->
            val logId = backStack.arguments?.getLong("logId") ?: return@composable
            EditInteractionScreen(
                logId     = logId,
                onSaved   = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
                onBack    = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.EditContact.route,
            arguments = listOf(navArgument("contactId") { type = NavType.LongType }),
        ) { backStack ->
            val contactId = backStack.arguments?.getLong("contactId") ?: return@composable
            EditContactScreen(
                contactId = contactId,
                onSaved   = { navController.popBackStack() },
                onBack    = { navController.popBackStack() },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack      = { navController.popBackStack() },
                onLoggedOut = { navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } } },
            )
        }
    }
}
