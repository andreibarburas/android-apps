/*
 * Blik — a Nextcloud screenshot manager for Android
 * Copyright (C) 2026 andrei BARBURAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version. See <https://www.gnu.org/licenses/>.
 */

package com.brbrs.blik.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import coil.ImageLoader
import com.brbrs.blik.auth.AuthViewModel
import com.brbrs.blik.network.WebDavClient
import com.brbrs.blik.ui.screens.applock.AppLockScreen
import com.brbrs.blik.ui.screens.detail.DetailScreen
import com.brbrs.blik.ui.screens.gallery.GalleryScreen
import com.brbrs.blik.ui.screens.login.LoginScreen
import com.brbrs.blik.ui.screens.nextcloud.NextcloudScreen
import com.brbrs.blik.ui.screens.settings.SettingsScreen
import com.brbrs.blik.ui.theme.CyanPrimary
import com.brbrs.blik.ui.theme.LocalIsDark
import com.brbrs.blik.ui.theme.NavyMid
import com.brbrs.blik.ui.theme.LightSurface

// Avoids URL-encoding content:// URIs in nav routes
private var pendingDetailPath: String? = null

object Routes {
    const val APP_LOCK  = "app_lock"
    const val LOGIN     = "login"
    const val MAIN      = "main"       // hosts the bottom nav scaffold
    const val GALLERY   = "gallery"
    const val NEXTCLOUD = "nextcloud"
    const val DETAIL    = "detail"
    const val SETTINGS  = "settings"
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.GALLERY,   "Library",   Icons.Outlined.PhotoLibrary),
    BottomNavItem(Routes.NEXTCLOUD, "Nextcloud", Icons.Outlined.Cloud),
)

@Composable
fun BlikNavHost(webDavClient: WebDavClient, imageLoader: ImageLoader) {
    val rootNav = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()
    val authState by authVm.authState.collectAsState()

    val startDest = when {
        !authState.isLoggedIn      -> Routes.LOGIN
        authState.biometricEnabled -> Routes.APP_LOCK
        else                       -> Routes.MAIN
    }

    NavHost(navController = rootNav, startDestination = startDest) {

        composable(Routes.APP_LOCK) {
            AppLockScreen(onUnlocked = {
                rootNav.navigate(Routes.MAIN) { popUpTo(Routes.APP_LOCK) { inclusive = true } }
            })
        }

        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                rootNav.navigate(Routes.MAIN) { popUpTo(Routes.LOGIN) { inclusive = true } }
            })
        }

        // ── Main scaffold with bottom nav ─────────────────────────────────────
        composable(Routes.MAIN) {
            MainScaffold(
                onOpenDetail = { path ->
                    pendingDetailPath = path
                    rootNav.navigate(Routes.DETAIL)
                },
                onSettings   = { rootNav.navigate(Routes.SETTINGS) },
                webDavClient = webDavClient,
                imageLoader  = imageLoader,
            )
        }

        composable(Routes.DETAIL) {
            val path = pendingDetailPath
            if (path == null) rootNav.popBackStack()
            else DetailScreen(localPath = path, onBack = { rootNav.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack       = { rootNav.popBackStack() },
                onLoggedOut  = {
                    rootNav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
                webDavClient = webDavClient,
            )
        }
    }
}

@Composable
private fun MainScaffold(
    onOpenDetail: (String) -> Unit,
    onSettings: () -> Unit,
    webDavClient: WebDavClient,
    imageLoader: ImageLoader,
) {
    val bottomNav = rememberNavController()
    val isDark    = LocalIsDark.current

    val currentEntry by bottomNav.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = if (isDark) NavyMid else LightSurface,
                tonalElevation = 0.dp,
            ) {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected      = currentRoute == item.route,
                        onClick       = {
                            if (currentRoute != item.route) {
                                bottomNav.navigate(item.route) {
                                    popUpTo(bottomNav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        },
                        icon  = { Icon(item.icon, item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = CyanPrimary,
                            selectedTextColor   = CyanPrimary,
                            indicatorColor      = CyanPrimary.copy(alpha = 0.12f),
                            unselectedIconColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF64748B)
                                                 else androidx.compose.ui.graphics.Color(0xFF94A3B8),
                            unselectedTextColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF64748B)
                                                 else androidx.compose.ui.graphics.Color(0xFF94A3B8),
                        ),
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = bottomNav,
            startDestination = Routes.GALLERY,
            // Only consume the bottom inset (the nav bar height).
            // Each screen handles its own top status-bar padding via statusBarsPadding().
            modifier         = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(Routes.GALLERY) {
                GalleryScreen(
                    onOpenDetail = onOpenDetail,
                    onSettings   = onSettings,
                )
            }
            composable(Routes.NEXTCLOUD) {
                NextcloudScreen(imageLoader = imageLoader)
            }
        }
    }
}
