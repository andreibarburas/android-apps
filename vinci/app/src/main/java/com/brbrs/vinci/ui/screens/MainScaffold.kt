package com.brbrs.vinci.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brbrs.vinci.ui.theme.CyanPrimary
import com.brbrs.vinci.ui.theme.LocalIsDark
import com.brbrs.vinci.ui.theme.NavyMid
import com.brbrs.vinci.ui.theme.LightSurface

private enum class MainTab(val label: String) {
    Home("Home"),
    Interactions("Interactions"),
    Contacts("Contacts"),
}

@Composable
fun MainScaffold(
    onContactClick: (Long) -> Unit,
    onLogCall: (Long, String) -> Unit,
    onEditInteraction: (Long) -> Unit,
    onSettings: () -> Unit,
    onAddContact: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    val isDark = LocalIsDark.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Active tab content
        when (selectedTab) {
            MainTab.Home -> HomeScreen(
                onContactClick    = onContactClick,
                onLogCall         = onLogCall,
                onEditInteraction = onEditInteraction,
                onSettings        = onSettings,
            )
            MainTab.Interactions -> InteractionsScreen(
                onEditInteraction = onEditInteraction,
            )
            MainTab.Contacts -> ContactsTabScreen(
                onContactClick = onContactClick,
                onLogCall      = onLogCall,
                onAddContact   = onAddContact,
            )
        }

        // Bottom navigation bar
        NavigationBar(
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            containerColor = if (isDark) NavyMid else LightSurface,
            tonalElevation = 0.dp,
        ) {
            NavigationBarItem(
                selected = selectedTab == MainTab.Home,
                onClick  = { selectedTab = MainTab.Home },
                icon = {
                    Icon(
                        if (selectedTab == MainTab.Home) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = "Home",
                    )
                },
                label = { Text("Home") },
                colors = navColors(),
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.Interactions,
                onClick  = { selectedTab = MainTab.Interactions },
                icon = {
                    Icon(
                        if (selectedTab == MainTab.Interactions) Icons.Filled.EventNote else Icons.Outlined.EventNote,
                        contentDescription = "Interactions",
                    )
                },
                label = { Text("Interactions") },
                colors = navColors(),
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.Contacts,
                onClick  = { selectedTab = MainTab.Contacts },
                icon = {
                    Icon(
                        if (selectedTab == MainTab.Contacts) Icons.Filled.Person else Icons.Outlined.Person,
                        contentDescription = "Contacts",
                    )
                },
                label = { Text("Contacts") },
                colors = navColors(),
            )
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor   = CyanPrimary,
    selectedTextColor   = CyanPrimary,
    indicatorColor      = CyanPrimary.copy(alpha = 0.15f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
