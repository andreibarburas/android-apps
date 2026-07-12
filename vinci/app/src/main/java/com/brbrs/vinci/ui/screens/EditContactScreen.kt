package com.brbrs.vinci.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.vinci.ui.components.*
import com.brbrs.vinci.ui.components.ContactAvatar
import com.brbrs.vinci.ui.theme.*
import com.brbrs.vinci.ui.viewmodels.*
import java.util.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.ui.graphics.ColorFilter

@Composable
fun EditContactScreen(
    contactId: Long,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditContactViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark  = LocalIsDark.current
    val context = LocalContext.current

    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.setPhoto(it) } }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDark) Brush.verticalGradient(listOf(NavyDeep, NavyMid))
                    else Brush.verticalGradient(listOf(LightBg, LightSurface2))
                )
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.statusBarsPadding().padding(start = 8.dp, top = 8.dp, end = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                }
                Text(if (uiState.isNewContact) "New contact" else "Edit contact",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            // Avatar preview -- tappable to change photo, shown for both new and existing contacts
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    val previewUri = uiState.photoUri.ifBlank { uiState.contact?.photoUri ?: "" }
                    val previewName = if (uiState.displayName.isNotBlank()) uiState.displayName
                        else "${uiState.firstName} ${uiState.lastName}".trim().ifBlank { "?" }
                    ContactAvatar(
                        photoUri    = previewUri,
                        displayName = previewName,
                        size        = 84,
                        shape       = CircleShape,
                        modifier    = Modifier.clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary)
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.CameraAlt, "Change photo",
                            tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Name
                EditSection(title = "Name", isDark = isDark) {
                    EditRow {
                        VinciTextField(
                            value         = uiState.firstName,
                            onValueChange = viewModel::onFirstNameChanged,
                            label         = "First name",
                            modifier      = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        VinciTextField(
                            value         = uiState.lastName,
                            onValueChange = viewModel::onLastNameChanged,
                            label         = "Last name",
                            modifier      = Modifier.weight(1f),
                        )
                    }
                }

                // Organization
                EditSection(title = "Work", isDark = isDark) {
                    VinciTextField(uiState.organization, viewModel::onOrganizationChanged, "Organization")
                    Spacer(Modifier.height(10.dp))
                    VinciTextField(uiState.jobTitle, viewModel::onJobTitleChanged, "Job title")
                }

                // Phone numbers
                EditSection(title = "Phone", isDark = isDark) {
                    uiState.phones.forEachIndexed { i, phone ->
                        PhoneRow(
                            phone    = phone,
                            onUpdate = { viewModel.updatePhone(i, it) },
                            onRemove = { viewModel.removePhone(i) },
                        )
                        if (i < uiState.phones.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    AddRowButton("Add phone") { viewModel.addPhone() }
                }

                // Email addresses
                EditSection(title = "Email", isDark = isDark) {
                    uiState.emails.forEachIndexed { i, email ->
                        EmailRow(
                            email    = email,
                            onUpdate = { viewModel.updateEmail(i, it) },
                            onRemove = { viewModel.removeEmail(i) },
                        )
                        if (i < uiState.emails.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    AddRowButton("Add email") { viewModel.addEmail() }
                }

                // Address
                EditSection(title = "Address", isDark = isDark) {
                    VinciTextField(uiState.address, viewModel::onAddressChanged, "Formatted address", minLines = 2)
                }

                // Birthday
                EditSection(title = "Birthday", isDark = isDark) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VinciTextField(
                            value         = uiState.birthday,
                            onValueChange = viewModel::onBirthdayChanged,
                            label         = "YYYY-MM-DD",
                            modifier      = Modifier.weight(1f),
                            keyboardType  = KeyboardType.Number,
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(context, { _, y, m, d ->
                                viewModel.onBirthdayChanged("%04d-%02d-%02d".format(y, m + 1, d))
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        }) {
                            Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Notes
                EditSection(title = "Notes", isDark = isDark) {
                    VinciTextField(uiState.notes, viewModel::onNotesChanged, "Notes", minLines = 3)
                }

                // Social links
                EditSection(title = "Social networks", isDark = isDark) {
                    uiState.socialLinks.forEachIndexed { i, social ->
                        SocialRow(
                            social   = social,
                            onUpdate = { viewModel.updateSocial(i, it) },
                            onRemove = { viewModel.removeSocial(i) },
                        )
                        if (i < uiState.socialLinks.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    AddRowButton("Add social network") { viewModel.addSocial() }
                }

                // Custom fields
                EditSection(title = "Custom fields", isDark = isDark) {
                    if (uiState.customFields.isEmpty()) {
                        Text(
                            "Add any field not covered above. Synced to Nextcloud via DAVx5.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    uiState.customFields.forEachIndexed { i, field ->
                        CustomFieldRow(
                            field    = field,
                            onUpdate = { viewModel.updateCustomField(i, it) },
                            onRemove = { viewModel.removeCustomField(i) },
                        )
                        if (i < uiState.customFields.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    AddRowButton("Add custom field") { viewModel.addCustomField() }
                }

                // Error
                if (uiState.error != null) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }

                // Save
                Button(
                    onClick   = viewModel::save,
                    enabled   = !uiState.isSaving,
                    modifier  = Modifier.fillMaxWidth().height(52.dp),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(color = NavyDeep, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save contact", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun EditSection(title: String, isDark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = CyanPrimary.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun EditRow(content: @Composable RowScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
fun VinciTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val isDark = LocalIsDark.current
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        modifier      = modifier,
        minLines      = minLines,
        shape         = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
            focusedTextColor        = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor      = MaterialTheme.colorScheme.onBackground,
            cursorColor             = MaterialTheme.colorScheme.primary,
            focusedLabelColor       = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    )
}

@Composable
private fun PhoneRow(phone: PhoneEntry, onUpdate: (PhoneEntry) -> Unit, onRemove: () -> Unit) {
    var typeExpanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(96.dp)) {
            OutlinedButton(
                onClick = { typeExpanded = true },
                shape   = RoundedCornerShape(12.dp),
                modifier= Modifier.fillMaxWidth().height(56.dp),
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) { Text(phone.type, style = MaterialTheme.typography.bodySmall) }
            DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                PHONE_TYPES.forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = { onUpdate(phone.copy(type = t)); typeExpanded = false })
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        VinciTextField(phone.number, { onUpdate(phone.copy(number = it)) }, "Phone number",
            modifier = Modifier.weight(1f), keyboardType = KeyboardType.Phone)
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.RemoveCircleOutline, "Remove", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EmailRow(email: EmailEntry, onUpdate: (EmailEntry) -> Unit, onRemove: () -> Unit) {
    var typeExpanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(80.dp)) {
            OutlinedButton(
                onClick  = { typeExpanded = true },
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) { Text(email.type, style = MaterialTheme.typography.bodySmall) }
            DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                EMAIL_TYPES.forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = { onUpdate(email.copy(type = t)); typeExpanded = false })
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        VinciTextField(email.address, { onUpdate(email.copy(address = it)) }, "Email address",
            modifier = Modifier.weight(1f), keyboardType = KeyboardType.Email)
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.RemoveCircleOutline, "Remove", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SocialRow(social: SocialEntry, onUpdate: (SocialEntry) -> Unit, onRemove: () -> Unit) {
    var platformExpanded by remember { mutableStateOf(false) }
    val currentPlatform = SOCIAL_PLATFORMS.firstOrNull { it.key == social.platform } ?: SOCIAL_PLATFORMS.last()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(currentPlatform.brandColor.copy(alpha = 0.12f))
                    .clickable { platformExpanded = true },
                contentAlignment = Alignment.Center,
            ) {
                SocialIconBadge(platform = currentPlatform, size = 40.dp, iconSize = 20.dp)
            }
            DropdownMenu(expanded = platformExpanded, onDismissRequest = { platformExpanded = false }) {
                SOCIAL_PLATFORMS.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SocialIconBadge(platform = p, size = 24.dp, iconSize = 12.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(p.label)
                            }
                        },
                        onClick = {
                            onUpdate(social.copy(platform = p.key))
                            platformExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        VinciTextField(social.url, { onUpdate(social.copy(platform = platformForUrl(it).key, url = it)) },
            "${currentPlatform.label} URL", modifier = Modifier.weight(1f), keyboardType = KeyboardType.Uri)
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.RemoveCircleOutline, "Remove", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CustomFieldRow(field: CustomField, onUpdate: (CustomField) -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VinciTextField(field.label, { onUpdate(field.copy(label = it)) }, "Label", modifier = Modifier.weight(0.4f))
        Spacer(Modifier.width(8.dp))
        VinciTextField(field.value, { onUpdate(field.copy(value = it)) }, "Value", modifier = Modifier.weight(0.6f))
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.RemoveCircleOutline, "Remove", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AddRowButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.AddCircleOutline, null, modifier = Modifier.size(16.dp), tint = CyanPrimary)
        Spacer(Modifier.width(6.dp))
        Text(label, color = CyanPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}
