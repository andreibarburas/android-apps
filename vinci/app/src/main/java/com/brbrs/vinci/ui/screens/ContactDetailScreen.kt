package com.brbrs.vinci.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.brbrs.vinci.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.vinci.data.CallLogEntity
import com.brbrs.vinci.data.ContactEntity
import com.brbrs.vinci.ui.components.SocialIconBadge
import com.brbrs.vinci.ui.components.platformForKey
import com.brbrs.vinci.ui.components.platformForUrl
import com.brbrs.vinci.ui.components.ContactAvatar
import com.brbrs.vinci.util.isWhatsAppInstalled
import com.brbrs.vinci.util.isSignalInstalled
import com.brbrs.vinci.util.openWhatsAppChat
import com.brbrs.vinci.util.openSignalChat
import com.brbrs.vinci.ui.theme.*
import com.brbrs.vinci.ui.viewmodels.ContactDetailViewModel
import com.brbrs.vinci.ui.viewmodels.RecentInteraction
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

private val TS_FORMAT = SimpleDateFormat("yyyy/MM/dd | HH:mm", Locale.getDefault())
private fun fmtTs(epochMs: Long) = TS_FORMAT.format(Date(epochMs))
private fun fmtDuration(sec: Int): String {
    val m = sec / 60; val s = sec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@Composable
fun ContactDetailScreen(
    contactId: Long,
    onBack: () -> Unit,
    onLogInteraction: (Long, String, Long, String) -> Unit,
    onEditInteraction: (Long) -> Unit,
    onEditContact: (Long) -> Unit,
    viewModel: ContactDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark  = LocalIsDark.current
    val contact = uiState.contact
    val exportFile by viewModel.exportFile.collectAsState()
    val localContext = LocalContext.current

    LaunchedEffect(exportFile) {
        exportFile?.let { file ->
            localContext.startActivity(Intent.createChooser(viewModel.shareIntentFor(file), "Share interaction history"))
            viewModel.clearExportFile()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark)
                    Brush.verticalGradient(0f to NavyDeep, 0.4f to NavyMid, 1f to NavyDeep)
                else
                    Brush.verticalGradient(0f to LightBg, 0.4f to Color(0xFFEDE6F8), 1f to LightSurface2)
            ),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 40.dp),
            modifier = Modifier.fillMaxSize(),
        ) {

            // Back row
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.statusBarsPadding().padding(start = 8.dp, top = 8.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                    Text("Contacts", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    contact?.let { c ->
                        IconButton(onClick = { viewModel.exportContactHistory() }) {
                            Icon(Icons.Outlined.Share, "Export history", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { onEditContact(c.id) }) {
                            Icon(Icons.Outlined.Edit, "Edit contact", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (contact == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CyanPrimary)
                    }
                }
                return@LazyColumn
            }

            // Hero
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                ) {
                    ContactAvatar(
                        photoUri    = contact.photoUri,
                        displayName = contact.displayName,
                        size        = 96,
                        shape       = CircleShape,
                    )
                    Spacer(Modifier.height(14.dp))
                    var showStarDialog by remember { mutableStateOf(false) }
                    if (showStarDialog) {
                        AlertDialog(
                            onDismissRequest = { showStarDialog = false },
                            title = {
                                Text(if (contact.isStarred) "Remove from starred?" else "Star this contact?")
                            },
                            text = {
                                Text(
                                    if (contact.isStarred)
                                        "${contact.displayName} will be removed from your starred contacts."
                                    else
                                        "${contact.displayName} will be pinned to the top of your contacts list.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.toggleStarred()
                                    showStarDialog = false
                                }) {
                                    Text(
                                        if (contact.isStarred) "Remove" else "Star",
                                        color = if (contact.isStarred) MaterialTheme.colorScheme.error else AmberWarn,
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showStarDialog = false }) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            contact.displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { showStarDialog = true },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = if (contact.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (contact.isStarred) "Unstar" else "Star",
                                tint = if (contact.isStarred) AmberWarn else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    val role = listOf(contact.jobTitle, contact.organization).filter { it.isNotBlank() }.joinToString(" · ")
                    if (role.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(role, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Quick actions
            item {
                val context = LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickActionButton(Icons.Outlined.Call, "Call", true, Modifier.weight(1f)) {
                        if (contact.phoneNumber.isNotBlank())
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}")))
                    }
                    QuickActionButton(Icons.Outlined.EditNote, "Log", false, Modifier.weight(1f)) {
                        onLogInteraction(contact.id, contact.phoneNumber, 0L, "Call")
                    }
                    if (contact.email.isNotBlank()) {
                        QuickActionButton(Icons.Outlined.Email, "Email", false, Modifier.weight(1f)) {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${contact.email}")))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Phone numbers — all of them
            item {
                val phones = parseJsonList(contact.allPhones, "type", "number")
                val context = LocalContext.current
                val showWhatsApp = remember { isWhatsAppInstalled(context) }
                val showSignal = remember { isSignalInstalled(context) }
                if (phones.isNotEmpty()) {
                    DetailSection(title = "Phone", isDark = isDark) {
                        phones.forEachIndexed { i, (type, number) ->
                            if (i > 0) RowDivider()
                            InfoRow(
                                icon     = Icons.Outlined.Phone,
                                label    = number,
                                sublabel = type,
                                onClick  = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) },
                                trailing = {
                                    if (showWhatsApp) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.si_whatsapp),
                                            contentDescription = "WhatsApp",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { openWhatsAppChat(context, number, uiState.defaultCountryCode) },
                                        )
                                    }
                                    if (showSignal) {
                                        Spacer(Modifier.width(14.dp))
                                        Icon(
                                            painter = painterResource(id = R.drawable.si_signal),
                                            contentDescription = "Signal",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { openSignalChat(context, number, uiState.defaultCountryCode) },
                                        )
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            // Email addresses — all of them
            item {
                val emails = parseJsonList(contact.allEmails, "type", "address")
                val context = LocalContext.current
                if (emails.isNotEmpty()) {
                    DetailSection(title = "Email", isDark = isDark) {
                        emails.forEachIndexed { i, (type, address) ->
                            if (i > 0) RowDivider()
                            InfoRow(
                                icon     = Icons.Outlined.Email,
                                label    = address,
                                sublabel = type,
                                onClick  = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address"))) },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            // Address
            if (contact.address.isNotBlank()) {
                item {
                    val context = LocalContext.current
                    DetailSection(title = "Address", isDark = isDark) {
                        InfoRow(
                            icon     = Icons.Outlined.LocationOn,
                            label    = contact.address,
                            sublabel = "",
                            onClick  = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(contact.address)}")))
                            },
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            // Birthday
            if (contact.birthday.isNotBlank()) {
                item {
                    DetailSection(title = "Birthday", isDark = isDark) {
                        InfoRow(icon = Icons.Outlined.Cake, label = formatBirthday(contact.birthday), sublabel = "", onClick = null)
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            // Notes
            if (contact.notes.isNotBlank()) {
                item {
                    DetailSection(title = "Notes", isDark = isDark) {
                        Text(
                            contact.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            // Social / website links
            val socialLinksJson = uiState.remoteSocialLinks
            if (!socialLinksJson.isNullOrBlank() && socialLinksJson != "[]") {
                item {
                    val context = LocalContext.current
                    val links   = parseSocialLinks(socialLinksJson)
                    if (links.isNotEmpty()) {
                        DetailSection(title = "Links", isDark = isDark) {
                            links.forEachIndexed { i, (platform, url, label) ->
                                if (i > 0) RowDivider()
                                SocialLinkRow(platform = platform, url = url, customLabel = label) {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (e: Exception) { }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }

            // ── Call history (system call log) ─────────────────────────────────
            if (uiState.recentInteractions.isNotEmpty()) {
                val visibleInteractions = if (uiState.showAllInteractions)
                    uiState.recentInteractions
                else
                    uiState.recentInteractions.take(5)

                item {
                    DetailSection(title = "Call history · ${uiState.recentInteractions.size}", isDark = isDark) {
                        visibleInteractions.forEachIndexed { i, interaction ->
                            if (i > 0) RowDivider()
                            InteractionRow(
                                interaction = interaction,
                                onClick = {
                                    if (interaction.matchedLogId != null) {
                                        onEditInteraction(interaction.matchedLogId)
                                    } else {
                                        contact?.let {
                                            onLogInteraction(it.id, it.phoneNumber, interaction.timestampMs, "Call")
                                        }
                                    }
                                },
                            )
                        }
                        if (uiState.recentInteractions.size > 5) {
                            RowDivider()
                            TextButton(
                                onClick = viewModel::toggleShowAllInteractions,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            ) {
                                Text(
                                    if (uiState.showAllInteractions) "Show less" else "Show all ${uiState.recentInteractions.size} calls",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            // ── Logged in Vinci ─────────────────────────────────────────────────
            val visibleLogs = if (uiState.showAllLogs)
                uiState.callLogs
            else
                uiState.callLogs.take(5)

            item {
                DetailSection(title = "Logged in Vinci · ${uiState.callLogs.size}", isDark = isDark) {
                    if (uiState.callLogs.isEmpty()) {
                        Text(
                            "No interactions logged yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            items(visibleLogs, key = { it.id }) { log ->
                Spacer(Modifier.height(8.dp))
                CallLogEntry(log = log, isDark = isDark, onClick = { onEditInteraction(log.id) })
            }

            if (uiState.callLogs.size > 5) {
                item {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = viewModel::toggleShowAllLogs,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (uiState.showAllLogs) "Show less" else "Show all ${uiState.callLogs.size} entries",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun InteractionRow(interaction: RecentInteraction, onClick: () -> Unit = {}) {
    val isLogged = interaction.matchedLogId != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = if (interaction.isOutgoing) Icons.Outlined.CallMade else Icons.Outlined.CallReceived,
            contentDescription = null,
            tint = if (interaction.isOutgoing) CyanPrimary else GreenOk,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                fmtTs(interaction.timestampMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (interaction.durationSeconds > 0) {
                Text(
                    fmtDuration(interaction.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (interaction.isOutgoing) "Outgoing" else "Incoming",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isLogged) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = GreenOk, modifier = Modifier.size(11.dp))
                    Text("Logged · Edit", style = MaterialTheme.typography.labelSmall, color = GreenOk)
                }
            } else {
                Text("Tap to log", style = MaterialTheme.typography.labelSmall, color = CyanPrimary.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isDark = LocalIsDark.current
    Box(
        modifier = modifier
            .height(44.dp)
            .then(
                if (isPrimary) Modifier.glassCardPrimary(cornerRadius = 13.dp)
                else Modifier.glassCard(cornerRadius = 13.dp, tint = if (isDark) Color.White else Color.Black)
            ),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(icon, null, modifier = Modifier.size(15.dp), tint = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailSection(title: String, isDark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = CyanPrimary.copy(alpha = 0.65f),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isDark)
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    else
                        Modifier
                            .shadow(3.dp, RoundedCornerShape(16.dp),
                                ambientColor = Color(0x2E9C27B0), spotColor = Color(0x149C27B0))
                            .clip(RoundedCornerShape(16.dp))
                            .background(LightSurface)
                            .border(1.dp, LightBorderMed, RoundedCornerShape(16.dp))
                ),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    sublabel: String,
    onClick: (() -> Unit)?,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (sublabel.isNotBlank()) {
                Text(sublabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SocialLinkRow(platform: String, url: String, customLabel: String = "", onClick: () -> Unit) {
    val socialPlatform = platformForKey(platform)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        SocialIconBadge(platform = socialPlatform, size = 36.dp, iconSize = 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(customLabel.ifBlank { socialPlatform.label }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onClick) {
            Text("Open", style = MaterialTheme.typography.bodyMedium, color = socialPlatform.brandColor)
        }
    }
}

@Composable
private fun CallLogEntry(log: CallLogEntity, isDark: Boolean, onClick: () -> Unit = {}) {
    val socialPlatform = if (log.interactionType.startsWith("Social Media:")) {
        val key = log.interactionType.removePrefix("Social Media:")
        com.brbrs.vinci.ui.components.SOCIAL_PLATFORMS.firstOrNull { it.key == key && it.drawableRes != 0 }
    } else null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .glassCard(cornerRadius = 14.dp, tint = if (isDark) Color.White else Color.Black)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (socialPlatform != null) {
                        Icon(
                            painter = painterResource(id = socialPlatform.drawableRes),
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        val typeIcon = when (log.interactionType) {
                            "Meeting" -> Icons.Outlined.Groups
                            "Email"   -> Icons.Outlined.Email
                            "Message" -> Icons.Outlined.Message
                            else      -> Icons.Outlined.Call
                        }
                        Icon(typeIcon, null, tint = CyanPrimary, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(log.reason, style = MaterialTheme.typography.titleMedium, color = CyanPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(fmtTs(log.callTimestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Outlined.Edit, "Edit", tint = CyanPrimary.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                }
            }
            if (log.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(log.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutcomeChip(log.outcome)
                if (log.followUpDays > 0) OutcomeChip("Follow-up set", color = AmberWarn)
            }
        }
    }
}

@Composable
private fun OutcomeChip(label: String, color: Color = CyanPrimary) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun parseJsonList(json: String, key1: String, key2: String): List<Pair<String, String>> {
    if (json.isBlank() || json == "[]") return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Pair(obj.optString(key1, ""), obj.optString(key2, ""))
        }.filter { it.second.isNotBlank() }
    } catch (e: Exception) { emptyList() }
}

// Returns list of Triple(platform, url, displayLabel)
private fun parseSocialLinks(json: String): List<Triple<String, String, String>> {
    if (json.isBlank() || json == "[]") return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj   = arr.getJSONObject(i)
            val key   = obj.optString("platform", "other")
            val url   = obj.optString("url", "")
            val label = obj.optString("label", "")  // custom label e.g. "Home"
            Triple(key, url, label)
        }.filter { it.second.isNotBlank() }
    } catch (e: Exception) { emptyList() }
}

private fun formatBirthday(raw: String): String {
    return try {
        val parts = raw.removePrefix("--").split("-")
        when (parts.size) {
            3 -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(
                Calendar.getInstance().apply { set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt()) }.time
            )
            2 -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(
                Calendar.getInstance().apply { set(Calendar.MONTH, parts[0].toInt() - 1); set(Calendar.DAY_OF_MONTH, parts[1].toInt()) }.time
            )
            else -> raw
        }
    } catch (e: Exception) { raw }
}
