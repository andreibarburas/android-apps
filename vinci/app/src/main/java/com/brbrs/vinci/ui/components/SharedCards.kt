package com.brbrs.vinci.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.brbrs.vinci.data.CallLogEntity
import com.brbrs.vinci.data.ContactEntity
import com.brbrs.vinci.ui.theme.*
import com.brbrs.vinci.util.InteractionParticipant
import com.brbrs.vinci.util.parseParticipants
import java.text.SimpleDateFormat
import java.util.*

// ── Top bar icon button ───────────────────────────────────────────────────────

@Composable
fun TopBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector       = icon,
            contentDescription= contentDescription,
            tint              = if (active) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier          = Modifier.size(20.dp),
        )
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(text: String, badge: Int?, color: Color) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.7f),
        )
        if (badge != null && badge > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(color.copy(alpha = 0.15f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(badge.toString(), style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

// ── Show more button ──────────────────────────────────────────────────────────

@Composable
fun ShowMoreButton(
    expanded: Boolean,
    count: Int,
    label: String,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            null,
            tint = CyanPrimary.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (expanded) "Show less" else "Show all $count $label",
            color = CyanPrimary.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

// ── Starred grid cell ─────────────────────────────────────────────────────────

@Composable
fun StarredGridCell(
    contact: ContactEntity,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .then(
                if (isDark)
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(PurpleGlow, Color.Transparent)
                            )
                        )
                        .border(1.dp, CyanPrimary.copy(alpha = 0.20f), RoundedCornerShape(18.dp))
                else
                    Modifier
                        .shadow(5.dp, RoundedCornerShape(18.dp),
                            ambientColor = Color(0x3A9C27B0), spotColor = Color(0x1E9C27B0))
                        .clip(RoundedCornerShape(18.dp))
                        .background(LightSurface2)
                        .border(1.5.dp, PurpleLight.copy(alpha = 0.32f), RoundedCornerShape(18.dp))
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            ContactAvatar(photoUri = contact.photoUri, displayName = contact.displayName,
                size = 50, shape = CircleShape)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isDark) NavyMid else LightSurface)
                    .border(1.dp, AmberWarn.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Star, null, tint = AmberWarn, modifier = Modifier.size(9.dp))
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            contact.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (contact.organization.isNotBlank()) {
            Text(
                contact.organization,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Contact list card ─────────────────────────────────────────────────────────

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ContactListCard(
    contact: ContactEntity,
    isDark: Boolean,
    accentColor: Color?,
    tag: String?,
    onClick: () -> Unit,
    onLogCall: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .fillMaxWidth()
            .then(
                if (isDark)
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.055f))
                        .border(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.09f),
                            RoundedCornerShape(16.dp),
                        )
                else
                    Modifier
                        .shadow(2.dp, RoundedCornerShape(16.dp),
                            ambientColor = Color(0x289C27B0), spotColor = Color(0x149C27B0))
                        .clip(RoundedCornerShape(16.dp))
                        .background(LightSurface)
                        .border(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else LightBorderMed,
                            RoundedCornerShape(16.dp),
                        )
            )
            .combinedClickable(
                onClick = { if (selectionMode) onLongClick() else onClick() },
                onLongClick = onLongClick,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            ContactAvatar(photoUri = contact.photoUri, displayName = contact.displayName,
                size = 42, shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildString {
                    if (contact.organization.isNotBlank()) append(contact.organization)
                    if (contact.lastCallTimestamp > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(relativeTime(contact.lastCallTimestamp))
                    }
                }
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (tag != null && accentColor != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(tag, style = MaterialTheme.typography.labelSmall, color = accentColor)
                    }
                }
                IconButton(onClick = onLogCall, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.EditNote, "Log interaction",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

// ── All-contacts grid cell ────────────────────────────────────────────────────

@Composable
fun ContactGridCell(
    contact: ContactEntity,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .then(
                if (isDark)
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.055f))
                        .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
                else
                    Modifier
                        .shadow(2.dp, RoundedCornerShape(16.dp),
                            ambientColor = Color(0x229C27B0), spotColor = Color(0x0E9C27B0))
                        .clip(RoundedCornerShape(16.dp))
                        .background(LightSurface)
                        .border(1.dp, LightBorderMed, RoundedCornerShape(16.dp))
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
    ) {
        ContactAvatar(photoUri = contact.photoUri, displayName = contact.displayName,
            size = 56, shape = CircleShape)
        Spacer(Modifier.height(8.dp))
        Text(contact.displayName, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (contact.organization.isNotBlank()) {
            Text(contact.organization, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        if (contact.followUpDue > 0 && contact.followUpDue <= System.currentTimeMillis()) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(AmberWarn.copy(alpha = 0.15f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text("Follow up", style = MaterialTheme.typography.labelSmall, color = AmberWarn)
            }
        }
    }
}

// ── Recent / chronological interaction card ──────────────────────────────────

@Composable
fun RecentInteractionCard(
    log: CallLogEntity,
    isDark: Boolean,
    showDate: Boolean = false,
    onClick: () -> Unit,
) {
    // Check if the interaction type is a social media entry (e.g. "Social Media:linkedin")
    val socialPlatform = if (log.interactionType.startsWith("Social Media:")) {
        val key = log.interactionType.removePrefix("Social Media:")
        com.brbrs.vinci.ui.components.SOCIAL_PLATFORMS.firstOrNull { it.key == key && it.drawableRes != 0 }
    } else null

    val participantCount = parseParticipants(log.participants).size

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .fillMaxWidth()
            .then(
                if (isDark)
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.055f))
                        .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
                else
                    Modifier
                        .shadow(2.dp, RoundedCornerShape(16.dp),
                            ambientColor = Color(0x289C27B0), spotColor = Color(0x149C27B0))
                        .clip(RoundedCornerShape(16.dp))
                        .background(LightSurface)
                        .border(1.dp, LightBorderMed, RoundedCornerShape(16.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyanPrimary.copy(alpha = if (isDark) 0.15f else 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                if (socialPlatform != null) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = socialPlatform.drawableRes),
                        contentDescription = socialPlatform.label,
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    val typeIcon = when {
                        participantCount > 0    -> Icons.Outlined.Groups
                        log.interactionType == "Meeting" -> Icons.Outlined.Groups
                        log.interactionType == "Email"   -> Icons.Outlined.Email
                        log.interactionType == "Message" -> Icons.Outlined.Message
                        else      -> Icons.Outlined.Call
                    }
                    Icon(typeIcon, null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                }
                if (participantCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(AmberWarn)
                            .border(1.dp, if (isDark) NavyMid else LightSurface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "+$participantCount",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)),
                            color = NavyDeep,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(log.contactName, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        val displayType = if (log.interactionType.startsWith("Social Media:"))
                            socialPlatform?.label ?: "Social Media"
                        else log.interactionType
                        append(displayType)
                        if (log.reason.isNotBlank()) append(" · ${log.reason}")
                        if (participantCount > 0) append(" · +$participantCount ${if (participantCount == 1) "person" else "people"}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (showDate) SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(log.callTimestamp))
                    else relativeTime(log.callTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Edit", style = MaterialTheme.typography.labelSmall,
                    color = CyanPrimary.copy(alpha = 0.7f))
            }
        }
    }
}

// ── Shared avatar ─────────────────────────────────────────────────────────────

@Composable
fun ContactAvatar(
    photoUri: String,
    displayName: String,
    size: Int,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    val initials = displayName.split(" ")
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    if (photoUri.isNotBlank()) {
        AsyncImage(
            model = Uri.parse(photoUri), contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size.dp).clip(shape),
            error    = painterResource(id = android.R.drawable.ic_menu_gallery),
            fallback = painterResource(id = android.R.drawable.ic_menu_gallery),
        )
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(CyanPrimary.copy(alpha = 0.25f), CyanPrimary.copy(alpha = 0.12f))
                    )
                )
                .border(1.dp, CyanPrimary.copy(alpha = 0.20f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials,
                style = if (size >= 56) MaterialTheme.typography.titleLarge
                        else if (size >= 40) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.labelLarge,
                color = CyanPrimary,
            )
        }
    }
}

// ── Unknown-number call card (Home "Recent calls") ────────────────────────────

@Composable
fun UnknownCallCard(
    phoneNumber: String,
    timestamp: Long,
    isOutgoing: Boolean,
    durationSeconds: Int,
    isDark: Boolean,
    onLogCall: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .fillMaxWidth()
            .then(
                if (isDark)
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.055f))
                        .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
                else
                    Modifier
                        .shadow(2.dp, RoundedCornerShape(16.dp),
                            ambientColor = Color(0x289C27B0), spotColor = Color(0x149C27B0))
                        .clip(RoundedCornerShape(16.dp))
                        .background(LightSurface)
                        .border(1.dp, LightBorderMed, RoundedCornerShape(16.dp))
            )
            .clickable(onClick = onLogCall)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PersonOff, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(phoneNumber, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val durStr = if (durationSeconds >= 60) "${durationSeconds / 60}m ${durationSeconds % 60}s" else "${durationSeconds}s"
                Text(
                    "${if (isOutgoing) "Outgoing" else "Incoming"} \u00b7 $durStr \u00b7 ${relativeTime(timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onLogCall, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.EditNote, "Log interaction",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(17.dp))
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium,
        color = CyanPrimary.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

fun relativeTime(epochMs: Long): String {
    val diff  = System.currentTimeMillis() - epochMs
    val hours = diff / (1000 * 60 * 60)
    val days  = hours / 24
    return when {
        hours < 1  -> "just now"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "yesterday"
        days < 7   -> "${days}d ago"
        else       -> SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(epochMs))
    }
}

// ── Participants picker (Log / Edit interaction — add extra people, e.g. group calls) ─────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParticipantsPicker(
    participants: List<InteractionParticipant>,
    query: String,
    searchResults: List<ContactEntity>,
    isDark: Boolean,
    photosByUid: Map<String, String> = emptyMap(),
    onQueryChanged: (String) -> Unit,
    onAddContact: (ContactEntity) -> Unit,
    onAddFreeText: (String) -> Unit,
    onRemove: (InteractionParticipant) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (participants.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                participants.forEach { participant ->
                    ParticipantChip(
                        participant = participant,
                        photoUri    = photosByUid[participant.uid].orEmpty(),
                        isDark      = isDark,
                        onRemove    = { onRemove(participant) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = { Text("Search contacts or type a name...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Outlined.PersonAdd, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor     = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor   = MaterialTheme.colorScheme.onBackground,
                cursorColor          = MaterialTheme.colorScheme.primary,
            ),
        )

        if (query.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .vinciCard(isDark = isDark)
                    .padding(vertical = 4.dp),
            ) {
                searchResults.forEach { contact ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddContact(contact) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        ContactAvatar(photoUri = contact.photoUri, displayName = contact.displayName, size = 28, shape = CircleShape)
                        Spacer(Modifier.width(10.dp))
                        Text(contact.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddFreeText(query) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Add \"$query\" as new", style = MaterialTheme.typography.bodyMedium, color = CyanPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * A single added participant, styled like [StarredGridCell] — avatar with name below —
 * but with a small circular "remove" badge peeking out of the avatar's corner instead of a star,
 * so it reads as "tap to un-star this person from the interaction" rather than a plain delete button.
 */
@Composable
private fun ParticipantChip(
    participant: InteractionParticipant,
    photoUri: String,
    isDark: Boolean,
    onRemove: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            ContactAvatar(photoUri = photoUri, displayName = participant.name, size = 52, shape = CircleShape)
            Box(
                modifier = Modifier
                    .offset(x = 5.dp, y = (-5).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isDark) NavyMid else LightSurface)
                    .border(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove ${participant.name}",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            participant.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
