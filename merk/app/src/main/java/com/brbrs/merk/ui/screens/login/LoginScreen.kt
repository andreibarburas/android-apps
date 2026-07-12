package com.brbrs.merk.ui.screens.login

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.merk.ui.components.MerkLogo
import com.brbrs.merk.ui.theme.*

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    vm: LoginViewModel = hiltViewModel(),
) {
    val state   = vm.uiState.collectAsState().value
    val context = LocalContext.current
    val isDark  = LocalIsDark.current

    val bgBrush = if (isDark)
        Brush.verticalGradient(listOf(NavyDeep, NavyMid, NavyDeep))
    else
        Brush.verticalGradient(listOf(LightBg, LightSurface, LightBg))

    LaunchedEffect(state.loginUrl) {
        state.loginUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
    }
    LaunchedEffect(state.loggedIn) { if (state.loggedIn) onLoggedIn() }

    Box(
        modifier = Modifier.fillMaxSize().background(bgBrush),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .glassCard(
                        cornerRadius = 24.dp, bgAlpha = 0.08f,
                        tint = if (isDark) androidx.compose.ui.graphics.Color.White
                               else androidx.compose.ui.graphics.Color.Black,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Bookmarks, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp))
            }

            // Logo + subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MerkLogo(modifier = Modifier.height(48.dp))
                Text(
                    "Enter your Nextcloud server address",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // URL field
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = vm::onServerUrlChanged,
                label = { Text("Nextcloud URL") },
                placeholder = { Text("https://cloud.yourdomain.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction    = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { vm.startLoginFlow() }),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
                    focusedTextColor        = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor      = MaterialTheme.colorScheme.onBackground,
                    cursorColor             = MaterialTheme.colorScheme.primary,
                    focusedLabelColor       = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ),
            )

            // Error
            AnimatedVisibility(state.error != null) {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center)
                }
            }

            // Connect button
            Button(
                onClick  = vm::startLoginFlow,
                enabled  = state.serverUrl.isNotBlank() && !state.isLoading && !state.isPolling,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (state.isLoading || state.isPolling) {
                    CircularProgressIndicator(
                        color       = MaterialTheme.colorScheme.onPrimary,
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Connect to Nextcloud", style = MaterialTheme.typography.titleMedium)
                }
            }

            AnimatedVisibility(state.isPolling) {
                Text(
                    "Approve the connection in your browser, then return here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // ── Nextcloud server notice ───────────────────────────────────────
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Nextcloud server required",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    "Merk is a client for Nextcloud Bookmarks. It requires a Nextcloud server to function. " +
                    "You can self-host one or sign up with a Nextcloud hosting partner.",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://nextcloud.com/sign-up/"))
                        )
                    }
                ) {
                    Text(
                        "Find a Nextcloud hosting partner \u2192",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
