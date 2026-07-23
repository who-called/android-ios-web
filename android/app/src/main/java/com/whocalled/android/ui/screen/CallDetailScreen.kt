package com.whocalled.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whocalled.android.data.ReportCategory
import com.whocalled.android.network.LookupResponse
import com.whocalled.android.ui.LoadState
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.components.AnimatedBanner
import com.whocalled.android.ui.components.BannerKind
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.components.ScoreGauge
import com.whocalled.android.ui.components.StatusBadge
import com.whocalled.android.ui.theme.WCColor
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallDetailScreen(
    viewModel: MainViewModel,
    callId: Long,
    onBack: () -> Unit,
) {
    val detail by viewModel.detail.collectAsState()
    val lookup by viewModel.lookup.collectAsState()
    val lookupLoading by viewModel.lookupLoading.collectAsState()
    val arcepMatch by viewModel.arcepMatch.collectAsState()
    val report by viewModel.report.collectAsState()
    val rule by viewModel.detailRule.collectAsState()
    val clipboard = LocalClipboardManager.current

    // ARCEP if the server says so OR the number matches a local official pattern.
    val isArcep = lookup?.source == "arcep" || arcepMatch != null

    LaunchedEffect(callId) {
        viewModel.loadCallDetail(callId)
        viewModel.clearReportState() // no stale banner from a previous action
    }

    val call = detail

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        // The bottom tab bar is hidden on this drill-down screen; a small inset
        // keeps the last button clear of the gesture/navigation area.
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour") }
                Text("Détail du numéro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        if (call == null) {
            item { Text("Chargement…") }
            return@LazyColumn
        }

        val blocked = call.action == "blocked"
        val statusColor = if (blocked) WCColor.Coral else WCColor.Amber

        item {
            // Compact header: the gauge sits beside the number/badges (not stacked
            // below), so the action buttons stay above the fold without scrolling.
            BorderedCard(accent = statusColor) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ScoreGauge(
                        score = lookup?.spamScore ?: call.spamScore,
                        diameter = 64.dp,
                        stroke = 6.dp,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("+${call.phone}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBadge(
                                label = if (blocked) "Bloqué" else "Alerté",
                                color = statusColor,
                                icon = if (blocked) Icons.Rounded.Block else Icons.Rounded.WarningAmber,
                            )
                            SourceBadge(isArcep)
                        }
                        Text("Indice de spam", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Source explanation
        item {
            BorderedCard {
                Text(
                    if (isArcep) "Liste officielle ARCEP" else "Signalé par la communauté",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (isArcep)
                        "Ce numéro correspond à un préfixe de la liste officielle des démarcheurs (ARCEP / opérateurs)" +
                            (arcepMatch?.pattern?.let { " : $it" } ?: "") +
                            ". Il est bloqué indépendamment des signalements."
                    else
                        "Ce numéro est évalué à partir des signalements de la communauté.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // Compact details — type + date grouped in a single card.
        item {
            BorderedCard {
                DetailLine("Type d’appel", ReportCategory.fromApi(lookup?.category ?: call.category)?.label ?: "Inconnu")
                DetailLine(
                    "Filtré le",
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.FRANCE)
                        .format(Date(call.timestamp)),
                )
            }
        }

        // Community stats (only meaningful when not ARCEP-only)
        if (lookupLoading) {
            item {
                BorderedCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                        Text("Chargement des statistiques…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            lookup?.let { stats -> item { StatsCard(stats) } }
        }

        // Actions
        item {
            Button(
                onClick = { viewModel.submitReport(call.phone, isSpam = true, ReportCategory.OTHER.api) },
                enabled = report !is LoadState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (report is LoadState.Loading) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                }
                Text("Confirmer comme indésirable")
            }
        }
        // Feedback so tapping the button never feels like a no-op.
        item {
            val (kind, msg) = when (val r = report) {
                is LoadState.Success -> BannerKind.Success to r.message
                is LoadState.Error -> BannerKind.Error to r.message
                else -> BannerKind.Info to null
            }
            AnimatedBanner(kind, msg)
        }
        item {
            BlockToggle(
                rule = rule,
                effectivelyBlocked = rule == "block" || (rule == null && (isArcep || (lookup?.status == "block") || blocked)),
                onBlock = { viewModel.block(call.phone) },
                onUnblock = { viewModel.unblock(call.phone) },
            )
        }
        item {
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString("+${call.phone}")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Copier le numéro")
            }
        }
    }
}

/**
 * Block/unblock toggle whose action + look reflect the number's current state:
 *   - allow rule set  → shown as "débloqué", offers to re-block
 *   - blocked (rule/list/ARCEP) → offers to unblock (the active case)
 *   - neutral → offers to block
 */
@Composable
fun BlockToggle(
    rule: String?,
    effectivelyBlocked: Boolean,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
) {
    when {
        rule == "allow" -> {
            BorderedCard(accent = WCColor.Emerald) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LockOpen, contentDescription = null, tint = WCColor.Emerald)
                    Text(
                        "  Débloqué — ce numéro sonnera normalement.",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedButton(onClick = onBlock, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Re-bloquer ce numéro")
                }
            }
        }
        effectivelyBlocked -> {
            OutlinedButton(onClick = onUnblock, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Débloquer ce numéro")
            }
        }
        else -> {
            OutlinedButton(onClick = onBlock, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Bloquer ce numéro")
            }
        }
    }
}

@Composable
fun SourceBadge(isArcep: Boolean) {
    if (isArcep) {
        StatusBadge("ARCEP", WCColor.Indigo, Icons.Rounded.Gavel)
    } else {
        StatusBadge("Communauté", WCColor.Slate, Icons.Rounded.Group)
    }
}

@Composable
fun StatsCard(stats: LookupResponse) {
    BorderedCard {
        Text("Signalements", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Indésirable", stats.reportCountSpam.toString(), WCColor.Coral)
            Stat("Légitime", stats.reportCountLegit.toString(), WCColor.Emerald)
        }
        // Community "why did it call?" headline — only when we have a known reason.
        stats.topReason
            ?.takeIf { it.category != "unknown" && it.count > 0 }
            ?.let { top ->
                val label = ReportCategory.fromApi(top.category)?.label ?: top.category
                Text(
                    "Le plus souvent : $label (${top.share} %)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = WCColor.Coral,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        Text("Fréquence des signalements", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("24 h", stats.frequency.last24h.toString(), MaterialTheme.colorScheme.primary)
            Stat("7 j", stats.frequency.last7d.toString(), MaterialTheme.colorScheme.primary)
            Stat("30 j", stats.frequency.last30d.toString(), MaterialTheme.colorScheme.primary)
            Stat("1 an", stats.frequency.last1y.toString(), MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    BorderedCard {
        DetailLine(label, value)
    }
}

/** A label/value row without its own card — for grouping several in one card. */
@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
