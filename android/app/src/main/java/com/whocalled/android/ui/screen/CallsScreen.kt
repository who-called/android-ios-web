package com.whocalled.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PhoneMissed
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.components.EmptyState
import com.whocalled.android.ui.components.GradientHeader
import com.whocalled.android.ui.components.ScrollableScreen
import com.whocalled.android.ui.components.StatusBadge
import com.whocalled.android.ui.theme.WCColor
import com.whocalled.android.util.CallEvent
import com.whocalled.android.util.CallEventAction
import com.whocalled.android.util.CallDirection

private enum class CallsFilter(val label: String) {
    ALL("Tous"),
    REVIEW("À vérifier"),
    FILTERED("Filtrés"),
    CONTACTS("Contacts"),
}

/** Android call history enriched locally by Who Called. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallsScreen(
    viewModel: MainViewModel,
    onCallClick: (CallEvent) -> Unit,
    onRequestCallLogPermission: () -> Unit,
) {
    val sections by viewModel.groupedCallEvents.collectAsState()
    val hasPermission by viewModel.callLogPermission.collectAsState()
    var filter by remember { mutableStateOf(CallsFilter.ALL) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val displayedSections = remember(sections, filter) {
        sections.mapNotNull { section ->
            val items = section.items.filter { event ->
                when (filter) {
                    CallsFilter.ALL -> true
                    CallsFilter.REVIEW -> event.action == CallEventAction.UNKNOWN
                    CallsFilter.FILTERED -> event.action in setOf(
                        CallEventAction.BLOCKED,
                        CallEventAction.WARNED,
                        CallEventAction.REPORTED_SPAM,
                    )
                    CallsFilter.CONTACTS -> event.action == CallEventAction.CONTACT
                }
            }
            section.takeIf { items.isNotEmpty() }?.copy(items = items)
        }
    }

    ScrollableScreen(
        header = {
            GradientHeader(
                title = "Appels récents",
                subtitle = "Historique local enrichi par Who Called",
            )
        },
    ) {
        if (!hasPermission) {
            item {
                BorderedCard(
                    Modifier.padding(horizontal = 16.dp),
                    accent = WCColor.Blue,
                ) {
                    Text("Afficher votre historique d’appels", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Autorisez la lecture locale du journal pour afficher contacts, inconnus et appels sortants. Rien n’est envoyé.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TextButton(onClick = onRequestCallLogPermission) {
                        Text("Autoriser l’accès")
                    }
                }
            }
        }

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CallsFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) },
                    )
                }
            }
        }

        if (displayedSections.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.PhoneMissed,
                    title = when (filter) {
                        CallsFilter.ALL -> "Aucun appel récent"
                        CallsFilter.REVIEW -> "Aucun numéro à vérifier"
                        CallsFilter.FILTERED -> "Aucun appel filtré"
                        CallsFilter.CONTACTS -> "Aucun appel de vos contacts"
                    },
                    subtitle = if (hasPermission)
                        "Vos prochains appels apparaîtront ici."
                    else
                        "Les appels bloqués restent visibles même sans autoriser le journal système.",
                    accent = WCColor.Blue,
                )
            }
        } else {
            item {
                Text(
                    "Appui long sur un numéro pour le copier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            displayedSections.forEach { section ->
                item(key = "header-${section.title}") {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                items(
                    count = section.items.size,
                    key = { section.items[it].key },
                ) { index ->
                    val event = section.items[index]
                    CallRow(
                        event = event,
                        onClick = { onCallClick(event) },
                        onLongClick = {
                            clipboard.setText(AnnotatedString("+${event.phone}"))
                            Toast.makeText(context, "Numéro copié", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRow(
    event: CallEvent,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val (label, color, icon) = when (event.action) {
        CallEventAction.BLOCKED -> Triple("Bloqué", WCColor.Coral, Icons.Rounded.Block)
        CallEventAction.WARNED -> Triple("Alerté", WCColor.Amber, Icons.Rounded.WarningAmber)
        CallEventAction.REPORTED_SPAM -> Triple("Indésirable", WCColor.Coral, Icons.Rounded.Block)
        CallEventAction.LEGITIMATE -> Triple("Légitime", WCColor.Emerald, Icons.Rounded.CheckCircle)
        CallEventAction.CONTACT -> Triple("Contact", WCColor.Slate, Icons.Rounded.Person)
        CallEventAction.UNKNOWN -> Triple("Non évalué", WCColor.Blue, Icons.Rounded.Search)
    }
    BorderedCard(
        Modifier
            .padding(horizontal = 16.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(event.contactName ?: "+${event.phone}", fontWeight = FontWeight.Bold)
                val attempts = if (event.attempts > 1) " · ${event.attempts} appels" else ""
                Text(
                    buildString {
                        if (event.contactName != null) append("+${event.phone} · ")
                        append(directionLabel(event.direction))
                        append(" · ")
                        append(com.whocalled.android.util.RelativeTime.format(event.timestamp))
                        append(attempts)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(
                label = label,
                color = color,
                icon = icon,
                modifier = Modifier.padding(end = 4.dp),
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Voir le numéro",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun directionLabel(direction: CallDirection): String = when (direction) {
    CallDirection.INCOMING -> "Entrant"
    CallDirection.MISSED -> "Manqué"
    CallDirection.OUTGOING -> "Sortant"
    CallDirection.REJECTED -> "Refusé"
    CallDirection.BLOCKED -> "Bloqué"
}
