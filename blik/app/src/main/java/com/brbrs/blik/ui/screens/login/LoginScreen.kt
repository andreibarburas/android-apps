package com.brbrs.blik.ui.screens.login

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.blik.R
import com.brbrs.blik.ui.theme.*

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
        Brush.verticalGradient(listOf(LightBg, LightSurface, LightSurface2))

    LaunchedEffect(state.loginUrl) {
        state.loginUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
    }
    LaunchedEffect(state.loggedIn) { if (state.loggedIn) onLoggedIn() }

    Box(modifier = Modifier.fillMaxSize().background(bgBrush), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Handwritten logo
            Image(
                painter            = painterResource(id = R.drawable.blik_logo),
                contentDescription = "Blik",
                contentScale       = ContentScale.Fit,
                colorFilter        = if (!isDark)
                    ColorFilter.tint(Color(0xFF1E293B))
                else null,
                modifier           = Modifier
                    .fillMaxWidth(0.65f)
                    .heightIn(max = 90.dp),
            )

            Text(
                "Enter your Nextcloud server address",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            OutlinedTextField(
                value          = state.serverUrl,
                onValueChange  = vm::onServerUrlChanged,
                label          = { Text("Nextcloud URL") },
                placeholder    = { Text("https://cloud.yourdomain.com") },
                singleLine     = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction    = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { vm.startLoginFlow() }),
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(16.dp),
                colors          = OutlinedTextFieldDefaults.colors(
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

            AnimatedVisibility(state.error != null) {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }

            Button(
                onClick  = vm::startLoginFlow,
                enabled  = state.serverUrl.isNotBlank() && !state.isLoading && !state.isPolling,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = if (isDark) NavyDeep else Color.White,
                ),
            ) {
                if (state.isLoading || state.isPolling) {
                    CircularProgressIndicator(
                        color    = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Connect to Nextcloud", style = MaterialTheme.typography.titleMedium)
                }
            }

            AnimatedVisibility(state.isPolling) {
                Text(
                    "Approve the connection in your browser, then return here.",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // ── Nextcloud required notice ─────────────────────────────────────
            HorizontalDivider(
                color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                modifier  = Modifier.padding(vertical = 8.dp),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("⚠️", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Nextcloud server required",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    "Blik requires a Nextcloud server to store and manage your screenshots. " +
                    "You can self-host one or sign up with a Nextcloud hosting partner.",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://nextcloud.com/sign-up/"))
                    )
                }) {
                    Text(
                        "Find a Nextcloud hosting partner →",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
