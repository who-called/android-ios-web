package com.whocalled.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.whocalled.android.ui.components.StatusBadge
import com.whocalled.android.ui.theme.WCColor
import com.whocalled.android.util.CallHistoryItem

/**
 * Full filtered-call history, grouped into time sections (Aujourd'hui / Hier /
 * Cette semaine / Ce mois / Plus ancien). Same-number-same-day repeats collapse
 * into one row with an attempt count, so even 45 blocked calls stay readable.
 * Unlike Saracroche, we expose the history directly and let the user copy a
 * number (long-press) — our differentiator.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onCallClick: (Long) -> Unit,
) {
    val sections by viewModel.groupedHistory.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    "Historique des appels filtrés",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (sections.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Shield,
                    title = "Aucun appel filtré",
                    subtitle = "Les appels indésirables bloqués ou signalés apparaîtront ici, regroupés par date.",
                    accent = WCColor.Emerald,
                )
            }
            return@LazyColumn
        }

        item {
            Text(
                "Astuce : appui long sur un numéro pour le copier.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        sections.forEach { section ->
            item(key = "h-${section.title}") {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }
            items(
                count = section.items.size,
                key = { section.items[it].representativeId },
            ) { idx ->
                val item = section.items[idx]
                HistoryRow(
                    item = item,
                    onClick = { onCallClick(item.representativeId) },
                    onLongClick = {
                        clipboard.setText(AnnotatedString("+${item.phone}"))
                        Toast.makeText(context, "Numéro copié", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    item: CallHistoryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val blocked = item.action == "blocked"
    val color = if (blocked) WCColor.Coral else WCColor.Amber
    BorderedCard(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("+${item.phone}", fontWeight = FontWeight.Bold)
                val time = com.whocalled.android.util.RelativeTime.format(item.timestamp)
                val suffix = if (item.attempts > 1) " · ${item.attempts} tentatives" else ""
                Text(
                    time + suffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(
                label = if (blocked) "Bloqué" else "Alerté",
                color = color,
                icon = if (blocked) Icons.Rounded.Block else Icons.Rounded.WarningAmber,
                modifier = Modifier.padding(end = 4.dp),
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
