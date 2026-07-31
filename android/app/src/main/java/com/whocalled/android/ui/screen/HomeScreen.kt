package com.whocalled.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.whocalled.android.data.Countries
import com.whocalled.android.data.Preferences
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whocalled.android.ui.LoadState
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.components.AnimatedBanner
import com.whocalled.android.ui.components.BannerKind
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.components.GradientHeader
import com.whocalled.android.ui.components.ScrollableScreen
import com.whocalled.android.ui.theme.WCColor
import com.whocalled.android.network.StatsResponse
import com.whocalled.android.util.CallEvent
import com.whocalled.android.util.CallEventAction
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    isScreeningRoleHeld: Boolean,
    notificationsGranted: Boolean = true,
    onRequestRole: () -> Unit,
    onRequestNotifications: () -> Unit = {},
    onRequestCallLogPermission: () -> Unit = {},
    onSyncNow: () -> Unit,
    onSeeAllHistory: () -> Unit,
    onRecentCallClick: (CallEvent) -> Unit,
    onSmsClick: () -> Unit,
    onPlayGame: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
) {
    val blocked by viewModel.blockedCount.collectAsState()
    val warned by viewModel.warnedCount.collectAsState()
    val sync by viewModel.sync.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val recentCall by viewModel.recentCallPrompt.collectAsState()
    val callLogGranted by viewModel.callLogPermission.collectAsState()

    val gameState by viewModel.gameState.collectAsState()
    val playedToday = gameState?.lastPlayedDay == com.whocalled.android.game.GameDay.epochDay()
    val community by viewModel.community.collectAsState()

    // Fetch reassurance stats + auto-sync the list on first launch (no tap needed).
    LaunchedEffect(Unit) {
        viewModel.loadStats()
        viewModel.autoSyncIfFirstTime()
        viewModel.refreshGameState()
        viewModel.loadCommunity()
    }

    // First-launch country confirmation (non-blocking banner). Detected silently;
    // shown once until the user confirms or changes it.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCountryBanner by remember { mutableStateOf(false) }
    var showCountryDialog by remember { mutableStateOf(false) }
    var countryDial by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        countryDial = Preferences.countryDial(context)
        showCountryBanner = !Preferences.isCountryConfirmed(context)
    }
    fun confirmCountry() {
        showCountryBanner = false
        scope.launch { Preferences.setCountryConfirmed(context, true) }
    }

    if (showCountryDialog) {
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            title = { Text("Pays surveillé") },
            text = {
                Column {
                    Countries.list.forEach { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                countryDial = c.dial
                                showCountryDialog = false
                                viewModel.setCountry(c.dial)
                                confirmCountry()
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.flag, Modifier.padding(end = 10.dp))
                            Text(c.name, Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCountryDialog = false }) { Text("Fermer") } },
        )
    }

    ScrollableScreen(
        header = {
            GradientHeader(
                title = "Who Called",
                subtitle = if (isScreeningRoleHeld) "Vous êtes protégé" else "Protection à activer",
                trailing = {
                    Icon(
                        if (isScreeningRoleHeld) Icons.Rounded.VerifiedUser else Icons.Outlined.Shield,
                        contentDescription = null,
                        // Amber outline when inactive (draws attention to the action),
                        // white when protection is on.
                        tint = if (isScreeningRoleHeld) androidx.compose.ui.graphics.Color.White else WCColor.Amber,
                        modifier = Modifier.size(26.dp),
                    )
                },
            )
        },
    ) {
        recentCall?.let { call ->
            item(key = "recent-call-${call.key}") {
                RecentCallCard(
                    call = call,
                    onOpen = {
                        viewModel.markRecentCallHandled(call)
                        onRecentCallClick(call)
                    },
                    onDismiss = { viewModel.markRecentCallHandled(call) },
                )
            }
        }

        // Primary action first: update the list (visible without scrolling).
        item {
            OutlinedButton(
                onClick = onSyncNow,
                enabled = sync !is LoadState.Loading,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                if (sync is LoadState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Mise à jour…", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Mettre à jour la liste")
                }
            }
            if (sync is LoadState.Loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Text(
                    if (syncProgress > 0)
                        "${NumberFormat.getInstance().format(syncProgress)} numéros récupérés…"
                    else "Connexion au serveur…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item {
            val (kind, msg) = when (val s = sync) {
                is LoadState.Error -> BannerKind.Error to s.message
                is LoadState.Success -> BannerKind.Success to s.message
                else -> BannerKind.Info to null
            }
            AnimatedBanner(kind, msg, Modifier.padding(horizontal = 16.dp))
        }
        item {
            if (showCountryBanner) {
                val c = Countries.byDial(countryDial)
                BorderedCard(
                    Modifier.padding(horizontal = 16.dp),
                    accent = MaterialTheme.colorScheme.primary,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text("Numéros surveillés : ${c.flag} ${c.name}", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Détecté automatiquement. Touchez « Modifier » pour changer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showCountryDialog = true }) { Text("Modifier") }
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Fermer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { confirmCountry() }.padding(4.dp),
                        )
                    }
                }
            }
        }
        item {
            val shieldSteps = listOf(isScreeningRoleHeld, notificationsGranted, callLogGranted)
            val shieldDone = shieldSteps.count { it }
            val shieldTotal = shieldSteps.size

            if (shieldDone < shieldTotal) {
                BorderedCard(
                    Modifier.padding(horizontal = 16.dp),
                    accent = if (isScreeningRoleHeld) WCColor.Amber else WCColor.Coral,
                ) {
                    Text(
                        if (isScreeningRoleHeld) "Protection partielle · $shieldDone/$shieldTotal"
                        else "Bouclier non activé · $shieldDone/$shieldTotal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            !isScreeningRoleHeld ->
                                "Activez le filtre d’appels pour bloquer les indésirables."
                            !notificationsGranted ->
                                "Les alertes sont coupées : activez les notifications."
                            else ->
                                "Autorisez l’historique pour voir qui a appelé."
                        },
                        Modifier.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = when {
                            !isScreeningRoleHeld -> onRequestRole
                            !notificationsGranted -> onRequestNotifications
                            else -> onRequestCallLogPermission
                        },
                    ) {
                        Icon(
                            Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            when {
                                !isScreeningRoleHeld -> "Activer le bloqueur"
                                !notificationsGranted -> "Activer les alertes"
                                else -> "Afficher mon historique"
                            },
                        )
                    }
                }
            }
            if (isScreeningRoleHeld) {
                // Saracroche-style "active and up to date" card, but in our emerald.
                ProtectionCard(
                    blocked = blocked,
                    warned = warned,
                    stats = stats,
                    onSeeBlocked = onSeeAllHistory,
                )
            }
        }

        // Community shield — compact shared counter (the collective mission).
        item {
            community?.let { c -> CommunityShieldCard(c) }
        }

        // SMS shield entry — green, distinct from the call card, tappable.
        item {
            val smsOn by viewModel.smsBlockingEnabled.collectAsState()
            BorderedCard(
                Modifier
                    .padding(horizontal = 16.dp)
                    .clickable(onClick = onSmsClick),
                accent = if (smsOn) WCColor.Emerald else null,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Message,
                        contentDescription = null,
                        tint = if (smsOn) WCColor.Emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Bouclier SMS", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (smsOn) "Actif · les SMS indésirables sont masqués"
                            else "Masquez les notifications des SMS indésirables",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (smsOn) WCColor.Emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Bonus (discreet): the daily game lives mainly in the "Jeu" tab.
        item {
            DailyChallengeCard(
                playedToday = playedToday,
                streak = gameState?.streak ?: 0,
                bestToday = gameState?.bestScoreToday ?: 0,
                onPlay = onPlayGame,
                onLeaderboard = onOpenLeaderboard,
            )
        }
    }
}

@Composable
private fun RecentCallCard(
    call: CallEvent,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, message, color) = when (call.action) {
        CallEventAction.BLOCKED -> Triple(
            "Appel indésirable bloqué",
            "Who Called a raccroché avant qu’il ne sonne.",
            WCColor.Coral,
        )
        CallEventAction.WARNED -> Triple(
            "Appel suspect détecté",
            "L’appel a sonné avec une alerte.",
            WCColor.Amber,
        )
        CallEventAction.REPORTED_SPAM -> Triple(
            "Numéro signalé indésirable",
            "Vous aviez déjà marqué ce numéro comme indésirable.",
            WCColor.Coral,
        )
        CallEventAction.LEGITIMATE -> Triple(
            "Numéro marqué légitime",
            "Vous aviez indiqué que ce numéro est légitime.",
            WCColor.Emerald,
        )
        CallEventAction.CONTACT -> Triple(
            "Appel d’un contact",
            "Ce numéro figure dans vos contacts.",
            WCColor.Slate,
        )
        CallEventAction.UNKNOWN -> Triple(
            "Un numéro inconnu vous a appelé",
            "Vérifiez ce numéro avant de rappeler.",
            WCColor.Blue,
        )
    }

    BorderedCard(
        Modifier.padding(horizontal = 16.dp),
        accent = color,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.PhoneInTalk,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    "+${call.phone}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "${com.whocalled.android.util.RelativeTime.format(call.timestamp)} · $message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Fermer",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
            )
        }
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text("Voir le numéro")
        }
    }
}

/**
 * "Bloqueur actif et à jour" card (à la Saracroche, in our emerald). A green
 * shield headline, the DB coverage figure, an auto-update freshness line, and a
 * tappable "Appels bloqués" row whose count opens the full blocked list (loaded
 * only on tap — the list is never shown upfront on Home).
 */
@Composable
private fun ProtectionCard(
    blocked: Int,
    warned: Int,
    stats: StatsResponse?,
    onSeeBlocked: () -> Unit,
) {
    val nf = remember { NumberFormat.getInstance(Locale.FRANCE) }
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(WCColor.Emerald.copy(alpha = 0.10f))
            .border(
                androidx.compose.foundation.BorderStroke(1.dp, WCColor.Emerald.copy(alpha = 0.35f)),
                androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            )
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = WCColor.Emerald,
                modifier = Modifier.size(34.dp),
            )
            Text(
                "Bloqueur actif et à jour",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = WCColor.Emerald.copy(alpha = 0.20f))

        // DB coverage (reassurance) — shown when /stats responded. The freshness
        // rides as a subtitle on the coverage row (one line instead of two).
        stats?.let { s ->
            StatRow(
                icon = Icons.Rounded.Storage,
                label = "Numéros couverts",
                value = nf.format(s.coveredNumbers),
                subtitle = relativeUpdate(s.lastUpdate)?.let { "Mise à jour auto · $it" },
            )
            // Per-country breakdown (matches the device's scope).
            s.countryNumbers?.let { cn ->
                val c = Countries.byDial(s.country ?: "")
                val arcep = s.countryArcepPatternCount?.let { " · ${nf.format(it)} préfixes ARCEP" } ?: ""
                Text(
                    "${c.flag} ${c.name} : ${nf.format(cn)} numéros$arcep",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 30.dp, bottom = 6.dp),
                )
            }
        }

        // Blocked vs suspected (warned) — two distinct counts; tapping either
        // opens the full filtered history (loaded on tap).
        CountRow(
            icon = Icons.Rounded.Block,
            color = WCColor.Coral,
            label = "Appels bloqués",
            count = blocked,
            subtitle = if (blocked == 0) "Aucun pour l’instant" else "Rejetés sans sonner · voir le détail",
            onClick = onSeeBlocked,
        )
        CountRow(
            icon = Icons.Rounded.WarningAmber,
            color = WCColor.Amber,
            label = "Appels suspects",
            count = warned,
            subtitle = if (warned == 0) "Aucun pour l’instant" else "Ont sonné, vous avez été prévenu · voir le détail",
            onClick = onSeeBlocked,
        )
    }
}

@Composable
private fun CountRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    label: String,
    count: Int,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "$count",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, tint = WCColor.Emerald, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(label)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(value, fontWeight = FontWeight.Bold)
    }
}

/** "dernière MAJ aujourd'hui / il y a 3 j / le 12 juin" from an ISO-8601 date. */
private fun relativeUpdate(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val time = runCatching { parser.parse(iso)?.time }.getOrNull() ?: return null
    val days = ((System.currentTimeMillis() - time) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "à jour aujourd’hui"
        days == 1 -> "mise à jour hier"
        days < 30 -> "mise à jour il y a $days j"
        else -> "MAJ le " + DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.FRANCE).format(Date(time))
    }
}

/** Home entry for the daily DEFENSE game — a retention hook with a shareable streak. */
@Composable
private fun DailyChallengeCard(
    playedToday: Boolean,
    streak: Int,
    bestToday: Int,
    onPlay: () -> Unit,
    onLeaderboard: () -> Unit,
) {
    // Discreet bonus row (no coloured accent) — the game mainly lives in the "Jeu" tab.
    BorderedCard(
        Modifier.padding(horizontal = 16.dp).clickable(onClick = onPlay),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎮", style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                val extra = buildString {
                    if (streak > 0) append("  ·  $streak🔥")
                    if (playedToday && bestToday > 0) append("  ·  $bestToday pts")
                }
                Text(
                    "Jeux — défis du jour & classements$extra",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("🏆", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable(onClick = onLeaderboard).padding(6.dp))
        }
    }
}

/** Compact "community shield" — a shared counter + thin weekly-goal bar (soft, one card). */
@Composable
private fun CommunityShieldCard(c: com.whocalled.android.network.CommunityResponse) {
    val fmt = java.text.NumberFormat.getInstance()
    val progress = if (c.goal > 0) (c.week.toFloat() / c.goal).coerceIn(0f, 1f) else 0f
    BorderedCard(Modifier.padding(horizontal = 16.dp), accent = WCColor.Blue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🛡️")
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Bouclier communautaire", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${fmt.format(c.total)} signalements ensemble · +${fmt.format(c.week)} cette semaine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = WCColor.Blue,
        )
    }
}
