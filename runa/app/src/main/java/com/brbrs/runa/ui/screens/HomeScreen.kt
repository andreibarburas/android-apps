package com.brbrs.runa.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.runa.R
import androidx.compose.ui.res.painterResource
import com.brbrs.runa.ui.screens.map.MapScreen
import com.brbrs.runa.ui.screens.read.ReadScreen
import com.brbrs.runa.ui.screens.write.WriteScreen
import com.brbrs.runa.ui.theme.DisplayPreferencesRepository
import com.brbrs.runa.ui.theme.LocalIsDark
import com.brbrs.runa.ui.theme.runaBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val displayPrefs: DisplayPreferencesRepository,
) : ViewModel() {
    val themeMode = displayPrefs.preferences
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    fun toggleTheme(currentMode: String) {
        viewModelScope.launch {
            val next = when (currentMode) {
                "dark"  -> "light"
                "light" -> "dark"
                else    -> "dark"
            }
            displayPrefs.setThemeMode(next)
        }
    }
}

@Composable
fun HomeScreen(
    onEntryClick: (String) -> Unit,
    onSettings: () -> Unit,
    sharedUris: List<android.net.Uri> = emptyList(),
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val isDark    = LocalIsDark.current
    val themeMode by viewModel.themeMode.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Image(
                    painter            = painterResource(id = R.drawable.runa_wordmark),
                    contentDescription = "Runa",
                    colorFilter        = androidx.compose.ui.graphics.ColorFilter.tint(
                        if (isDark) androidx.compose.ui.graphics.Color.White
                        else MaterialTheme.colorScheme.primary
                    ),
                    modifier           = Modifier
                        .padding(start = 8.dp)
                        .height(22.dp)
                        .widthIn(max = 80.dp),
                )
                Row {
                    IconButton(onClick = { viewModel.toggleTheme(themeMode) }) {
                        Icon(
                            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = "Toggle theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    icon     = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    label    = { Text(stringResource(R.string.tab_write)) },
                    colors   = navColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    icon     = { Icon(Icons.Outlined.AutoStories, contentDescription = null) },
                    label    = { Text(stringResource(R.string.tab_read)) },
                    colors   = navColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick  = { selectedTab = 2 },
                    icon     = { Icon(Icons.Outlined.Map, contentDescription = null) },
                    label    = { Text(stringResource(R.string.tab_map)) },
                    colors   = navColors(),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .runaBackground(isDark)
                .padding(padding),
        ) {
            when (selectedTab) {
                0 -> WriteScreen(sharedUris = sharedUris)
                1 -> ReadScreen(onEntryClick = onEntryClick)
                2 -> MapScreen(onEntryClick = onEntryClick)
            }
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor   = MaterialTheme.colorScheme.primary,
    selectedTextColor   = MaterialTheme.colorScheme.primary,
    indicatorColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
