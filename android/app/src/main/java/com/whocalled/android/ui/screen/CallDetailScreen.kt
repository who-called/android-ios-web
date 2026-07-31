package com.whocalled.android.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.whocalled.android.BuildConfig
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
import com.whocalled.android.util.CallDirection
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class NumberAction { CALL, SMS }

@Composable
fun CallDetailScreen(
    viewModel: MainViewModel,
    callId: Long? = null,
    phone: String? = null,
    onBack: () -> Unit,
) {
    val detail by viewModel.detail.collectAsState()
    val lookup by viewModel.lookup.collectAsState()
    val lookupLoading by viewModel.lookupLoading.collectAsState()
    val arcepMatch by viewModel.arcepMatch.collectAsState()
    val report by viewModel.report.collectAsState()
    val rule by viewModel.detailRule.collectAsState()
    val detailPhone by viewModel.detailPhone.collectAsState()
    val myReports by viewModel.myReports.collectAsState()
    val allCallEvents by viewModel.callEvents.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<NumberAction?>(null) }
    var showAllHistory by remember { mutableStateOf(false) }

    val call = if (callId != null) detail?.takeIf { it.id == callId } else null
    val displayedPhone = phone?.takeIf { it.isNotBlank() }
        ?: call?.phone
        ?: detailPhone.takeIf { callId == null }

    // The backend is the presentation source of truth; the local ARCEP match is
    // retained as an offline fallback.
    val source = lookup?.source ?: if (arcepMatch != null) "arcep" else "none"
    val isArcep = source == "arcep" || source == "mixed" || arcepMatch != null
    val hasCommunityData = source == "community" || source == "mixed" ||
        (lookup?.reportCountSpam ?: 0) > 0 ||
        (lookup?.reportCountLegit ?: 0) > 0
    val myVote = lookup?.userVote ?: myReports.firstOrNull { it.phone == displayedPhone }?.vote
    val status = when {
        call?.action == "blocked" -> "block"
        call?.action == "warned" -> "warn"
        else -> lookup?.status ?: "unknown"
    }
    val statusColor = when (status) {
        "block" -> WCColor.Coral
        "warn" -> WCColor.Amber
        "allow" -> WCColor.Emerald
        else -> WCColor.Blue
    }
    val recentHistory = remember(allCallEvents, displayedPhone) {
        val cutoff = System.currentTimeMillis() - 30L * 86_400_000L
        allCallEvents
            .filter { it.phone == displayedPhone && it.timestamp >= cutoff }
            .sortedByDescending { it.timestamp }
    }
    val callsLast7Days = recentHistory.count {
        it.timestamp >= System.currentTimeMillis() - 7L * 86_400_000L
    }

    fun launchNumberAction(action: NumberAction) {
        val target = displayedPhone ?: return
        val intent = when (action) {
            NumberAction.CALL -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$target"))
            NumberAction.SMS -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+$target"))
        }
        runCatching { context.startActivity(intent) }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (status == "block") "Numéro indésirable" else "Numéro suspect") },
            text = {
                Text(
                    if (action == NumberAction.CALL)
                        "Ce numéro présente un risque. Voulez-vous vraiment ouvrir le composeur ?"
                    else
                        "Ce numéro présente un risque. Voulez-vous vraiment préparer un SMS ?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    launchNumberAction(action)
                }) { Text("Continuer") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("Annuler") }
            },
        )
    }

    LaunchedEffect(callId, phone) {
        if (callId != null) {
            viewModel.loadCallDetail(callId)
        } else if (phone != null) {
            viewModel.loadNumberDetail(phone)
        }
        viewModel.clearReportState() // no stale banner from a previous action
    }

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

        if (displayedPhone == null) {
            item { Text("Chargement…") }
            return@LazyColumn
        }

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
                        score = lookup?.spamScore ?: call?.spamScore ?: 0,
                        diameter = 64.dp,
                        stroke = 6.dp,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("+$displayedPhone", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBadge(
                                label = when (status) {
                                    "block" -> "Indésirable"
                                    "warn" -> "Suspect"
                                    "allow" -> "Plutôt légitime"
                                    else -> "Données insuffisantes"
                                },
                                color = statusColor,
                                icon = when (status) {
                                    "block" -> Icons.Rounded.Block
                                    "warn" -> Icons.Rounded.WarningAmber
                                    "allow" -> Icons.Rounded.CheckCircle
                                    else -> Icons.Rounded.Search
                                },
                            )
                            if (isArcep || hasCommunityData) {
                                SourceBadge(source, isArcep, hasCommunityData)
                            }
                        }
                        Text(
                            verdictExplanation(status, lookup?.confidenceLevel ?: "none"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            BorderedCard {
                Text("Contacter ce numéro", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (status == "block" || status == "warn") {
                                pendingAction = NumberAction.CALL
                            } else {
                                launchNumberAction(NumberAction.CALL)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Appeler")
                    }
                    OutlinedButton(
                        onClick = {
                            if (status == "block" || status == "warn") {
                                pendingAction = NumberAction.SMS
                            } else {
                                launchNumberAction(NumberAction.SMS)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Message, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("SMS")
                    }
                }
            }
        }

        // Source explanation
        item {
            BorderedCard {
                Text(
                    when {
                        source == "mixed" -> "ARCEP et avis de la communauté"
                        isArcep -> "Plage officielle ARCEP"
                        hasCommunityData -> "Avis de la communauté"
                        else -> "Aucune donnée communautaire"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (source == "mixed")
                        "Ce numéro correspond à une plage ARCEP et possède aussi des avis communautaires. Le verdict tient compte des deux."
                    else if (isArcep)
                        "Ce numéro correspond à un préfixe de la liste officielle des démarcheurs (ARCEP / opérateurs)" +
                            (arcepMatch?.pattern?.let { " : $it" } ?: "") +
                            ". Cette source est distincte des avis communautaires."
                    else if (hasCommunityData)
                        "Le verdict combine les avis récents, leur volume et la réputation des contributeurs."
                    else
                        "L’absence d’avis ne signifie pas que ce numéro est fiable. Restez prudent avant de rappeler.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (isArcep) {
                    // Play "misleading claims" policy: government info must link
                    // to its official source, with a non-affiliation disclaimer.
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.ARCEP_SOURCE_URL)),
                            )
                        },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                        Text("Source officielle : plan de numérotation (arcep.fr)")
                    }
                    Text(
                        "Who Called est une application indépendante, non affiliée à l’ARCEP ni à aucune entité gouvernementale.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Compact details — type + date grouped in a single card.
        call?.let { filteredCall ->
            item {
                BorderedCard {
                    DetailLine("Type d’appel", ReportCategory.fromApi(lookup?.category ?: filteredCall.category)?.label ?: "Inconnu")
                    DetailLine(
                        "Filtré le",
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.FRANCE)
                            .format(Date(filteredCall.timestamp)),
                    )
                }
            }
        }

        if (recentHistory.isNotEmpty()) {
            item {
                BorderedCard {
                    Text("Vos appels avec ce numéro", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${recentHistory.size} appel${if (recentHistory.size > 1) "s" else ""} sur 30 jours" +
                            " · $callsLast7Days sur 7 jours",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                    (if (showAllHistory) recentHistory else recentHistory.take(3)).forEach { event ->
                        DetailLine(
                            callHistoryLabel(event.direction, event.durationSeconds),
                            com.whocalled.android.util.RelativeTime.format(event.timestamp),
                        )
                    }
                    if (recentHistory.size > 3) {
                        TextButton(onClick = { showAllHistory = !showAllHistory }) {
                            Text(if (showAllHistory) "Réduire" else "Voir les ${recentHistory.size} appels")
                        }
                    }
                }
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

        item {
            BorderedCard {
                Text("Votre avis", fontWeight = FontWeight.SemiBold)
                Text(
                    "Votre vote améliore l’évaluation communautaire. Il ne bloque pas automatiquement le numéro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = myVote == "spam",
                        onClick = {
                            viewModel.submitReport(
                                displayedPhone,
                                isSpam = true,
                                ReportCategory.OTHER.api,
                            )
                        },
                        enabled = report !is LoadState.Loading,
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) {
                        Text("Indésirable")
                    }
                    SegmentedButton(
                        selected = myVote == "legit",
                        onClick = { viewModel.submitReport(displayedPhone, isSpam = false) },
                        enabled = report !is LoadState.Loading,
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) {
                        Text("Légitime")
                    }
                }
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
                effectivelyBlocked = rule == "block" || (rule == null && status == "block"),
                onBlock = { viewModel.block(displayedPhone) },
                onUnblock = { viewModel.unblock(displayedPhone) },
            )
        }
        item {
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString("+$displayedPhone")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Copier le numéro")
            }
        }
    }
}

/** Personal device rule, intentionally separate from the community vote. */
@Composable
fun BlockToggle(
    rule: String?,
    effectivelyBlocked: Boolean,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
) {
    val isAllowed = rule == "allow"
    BorderedCard(
        accent = when {
            isAllowed -> WCColor.Emerald
            effectivelyBlocked -> WCColor.Coral
            else -> null
        },
    ) {
        Text("Sur mon téléphone", fontWeight = FontWeight.SemiBold)
        Text(
            when {
                isAllowed -> "Toujours autorisé : ce numéro sonnera normalement."
                effectivelyBlocked -> "Ce numéro est actuellement bloqué sur cet appareil."
                else -> "Aucune règle personnelle pour ce numéro."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        when {
            isAllowed -> {
                OutlinedButton(onClick = onBlock, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Bloquer sur mon téléphone")
                }
            }
            effectivelyBlocked -> {
                OutlinedButton(onClick = onUnblock, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Toujours autoriser")
                }
            }
            else -> {
                OutlinedButton(onClick = onBlock, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Bloquer sur mon téléphone")
                }
            }
        }
    }
}

@Composable
fun SourceBadge(source: String, isArcep: Boolean, hasCommunityData: Boolean) {
    when {
        source == "mixed" -> StatusBadge("ARCEP + communauté", WCColor.Indigo, Icons.Rounded.Gavel)
        isArcep -> StatusBadge("ARCEP", WCColor.Indigo, Icons.Rounded.Gavel)
        hasCommunityData -> StatusBadge("Communauté", WCColor.Slate, Icons.Rounded.Group)
        else -> StatusBadge("Aucune donnée", WCColor.Blue, Icons.Rounded.Search)
    }
}

@Composable
fun StatsCard(stats: LookupResponse) {
    val confidenceLevel = stats.confidenceLevel ?: "none"
    BorderedCard {
        Text("Avis de la communauté", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Indésirable", stats.reportCountSpam.toString(), WCColor.Coral)
            Stat("Légitime", stats.reportCountLegit.toString(), WCColor.Emerald)
        }
        Text(
            "Ces nombres sont les avis bruts. Le verdict pondère aussi leur récence et la réputation des contributeurs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Text(
            "Fiabilité de l’évaluation",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            confidenceTitle(confidenceLevel),
            color = when (confidenceLevel) {
                "high", "official" -> WCColor.Emerald
                "medium" -> WCColor.Amber
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            confidenceExplanation(confidenceLevel, stats.confidence),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
        val frequency = stats.frequency
        if (frequency.last1y > 0) {
            Text("Activité des avis", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("24 h", frequency.last24h.toString(), MaterialTheme.colorScheme.primary)
                Stat("7 j", frequency.last7d.toString(), MaterialTheme.colorScheme.primary)
                Stat("30 j", frequency.last30d.toString(), MaterialTheme.colorScheme.primary)
                Stat("1 an", frequency.last1y.toString(), MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun verdictExplanation(status: String, confidenceLevel: String): String = when (status) {
    "block" -> if (confidenceLevel == "high") "Forte convergence vers un appel indésirable."
        else "Évalué comme indésirable, avec encore peu de recul."
    "warn" -> "Des avis négatifs existent, mais le verdict reste à confirmer."
    "allow" -> if (confidenceLevel == "high") "Les avis convergent vers un numéro légitime."
        else "Tendance plutôt légitime, avec encore peu d’avis."
    else -> "Pas assez d’éléments pour évaluer ce numéro."
}

private fun confidenceTitle(level: String): String = when (level) {
    "official" -> "Source officielle"
    "high" -> "Élevée"
    "medium" -> "Moyenne"
    "low" -> "Faible"
    else -> "Non évaluée"
}

private fun confidenceExplanation(level: String, confidence: Int?): String = when (level) {
    "official" -> "Le statut provient d’une plage officielle, pas d’un calcul communautaire."
    "high" -> "Le volume pondéré d’avis est suffisant pour une évaluation solide (${confidence ?: 100} %)."
    "medium" -> "Plusieurs avis existent, mais davantage de recul est utile (${confidence ?: 0} %)."
    "low" -> "Trop peu d’avis pondérés pour conclure avec assurance (${confidence ?: 0} %)."
    else -> "Aucun avis pondéré exploitable pour le moment."
}

private fun callHistoryLabel(direction: CallDirection, durationSeconds: Long): String {
    val directionLabel = when (direction) {
        CallDirection.INCOMING -> "Entrant"
        CallDirection.MISSED -> "Manqué"
        CallDirection.OUTGOING -> "Sortant"
        CallDirection.REJECTED -> "Refusé"
        CallDirection.BLOCKED -> "Bloqué"
    }
    if (durationSeconds <= 0) return directionLabel
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return "$directionLabel · ${minutes}m ${seconds}s"
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
