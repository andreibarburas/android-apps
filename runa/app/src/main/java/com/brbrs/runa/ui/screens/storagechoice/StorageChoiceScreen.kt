package com.brbrs.runa.ui.screens.storagechoice

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.runa.R
import com.brbrs.runa.auth.AuthRepository
import com.brbrs.runa.ui.theme.LocalIsDark
import com.brbrs.runa.ui.theme.glassCard
import com.brbrs.runa.ui.theme.glassCardPrimary
import com.brbrs.runa.ui.theme.runaBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageChoiceViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    fun chooseLocal() {
        viewModelScope.launch { authRepository.setLocalMode() }
    }
}

@Composable
fun StorageChoiceScreen(
    onConnectNextcloud: () -> Unit,
    onUseLocally: () -> Unit,
    viewModel: StorageChoiceViewModel = hiltViewModel(),
) {
    val isDark = LocalIsDark.current

    Box(
        modifier = Modifier.fillMaxSize().runaBackground(isDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Image(
                painter            = painterResource(id = R.drawable.runa_wordmark),
                contentDescription = "Runa",
                colorFilter        = androidx.compose.ui.graphics.ColorFilter.tint(
                    if (isDark) androidx.compose.ui.graphics.Color.White
                    else MaterialTheme.colorScheme.primary
                ),
                modifier           = Modifier
                    .height(72.dp)
                    .widthIn(max = 240.dp),
            )

            Spacer(Modifier.height(4.dp))

            Text(
                stringResource(R.string.storage_choice_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Text(
                stringResource(R.string.storage_choice_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            StorageOptionCard(
                icon        = Icons.Outlined.Cloud,
                title       = stringResource(R.string.storage_choice_nextcloud_title),
                body        = stringResource(R.string.storage_choice_nextcloud_body),
                elevated    = true,
                onClick     = onConnectNextcloud,
                buttonLabel = "Connect →",
                isPrimary   = true,
            )

            StorageOptionCard(
                icon        = Icons.Outlined.PhoneAndroid,
                title       = stringResource(R.string.storage_choice_local_title),
                body        = stringResource(R.string.storage_choice_local_body),
                elevated    = false,
                onClick     = { viewModel.chooseLocal(); onUseLocally() },
                buttonLabel = stringResource(R.string.storage_choice_local_title),
                isPrimary   = false,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 12.dp)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.storage_choice_privacy_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StorageOptionCard(
    icon: ImageVector,
    title: String,
    body: String,
    elevated: Boolean,
    isPrimary: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    val modifier = if (elevated)
        Modifier.fillMaxWidth().glassCardPrimary(cornerRadius = 20.dp)
    else
        Modifier.fillMaxWidth().glassCard(cornerRadius = 20.dp)

    Box(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            if (isPrimary) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(buttonLabel, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(buttonLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
