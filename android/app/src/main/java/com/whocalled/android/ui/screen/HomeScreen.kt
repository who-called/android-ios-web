package com.whocalled.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Layers
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.whocalled.android.R
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
import com.whocalled.android.network.CommunityResponse
import com.whocalled.android.network.StatsResponse
import com.whocalled.android.util.CallEvent
import com.whocalled.android.util.CallEventAction
import com.whocalled.android.util.RecentCallPrompt
import com.whocalled.android.util.RelativeTime
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Everything the Home screen renders, as plain data. [HomeScreen] fills it from
 * the ViewModel; screenshot tests and previews fill it with fixtures.
 */
data class HomeUiState(
    val isScreeningRoleHeld: Boolean,
    val notificationsGranted: Boolean = true,
    /** Default true so store screenshots stay on the quiet/protected layout. */
    val overlayGranted: Boolean = true,
    val warnEnabled: Boolean = true,
    val blocked: Int = 0,
    val warned: Int = 0,
    val sync: LoadState = LoadState.Idle,
    val syncProgress: Int = 0,
    val stats: StatsResponse? = null,
    val recentCall: RecentCallPrompt? = null,
    val community: CommunityResponse? = null,
    val smsOn: Boolean = false,
    val playedToday: Boolean = false,
    val streak: Int = 0,
    val bestToday: Int = 0,
    val countryDial: String = "33",
    val showCountryBanner: Boolean = false,
    /** Fixed clock for "il y a 17 h"; null = wall clock (ticks every minute). */
    val now: Long? = null,
)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    isScreeningRoleHeld: Boolean,
    notificationsGranted: Boolean = true,
    overlayGranted: Boolean = true,
    onRequestRole: () -> Unit,
    onRequestNotifications: () -> Unit = {},
    onRequestOverlay: () -> Unit = {},
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
    val smsOn by viewModel.smsBlockingEnabled.collectAsState()

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
    var countryDial by remember { mutableStateOf("") }
    var warnEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        countryDial = Preferences.countryDial(context)
        showCountryBanner = !Preferences.isCountryConfirmed(context)
        warnEnabled = Preferences.isWarnEnabled(context)
    }
    fun confirmCountry() {
        showCountryBanner = false
        scope.launch { Preferences.setCountryConfirmed(context, true) }
    }

    HomeContent(
        state = HomeUiState(
            isScreeningRoleHeld = isScreeningRoleHeld,
            notificationsGranted = notificationsGranted,
            overlayGranted = overlayGranted,
            warnEnabled = warnEnabled,
            blocked = blocked,
            warned = warned,
            sync = sync,
            syncProgress = syncProgress,
            stats = stats,
            recentCall = recentCall,
            community = community,
            smsOn = smsOn,
            playedToday = playedToday,
            streak = gameState?.streak ?: 0,
            bestToday = gameState?.bestScoreToday ?: 0,
            countryDial = countryDial,
            showCountryBanner = showCountryBanner,
        ),
        onRequestRole = onRequestRole,
        onRequestNotifications = onRequestNotifications,
        onRequestOverlay = onRequestOverlay,
        onSyncNow = onSyncNow,
        onSeeAllHistory = onSeeAllHistory,
        onRecentCallOpen = { call ->
            viewModel.markRecentCallHandled(call)
            onRecentCallClick(call)
        },
        onRecentCallDismiss = { call -> viewModel.markRecentCallHandled(call) },
        onCountrySelected = { dial ->
            countryDial = dial
            viewModel.setCountry(dial)
            confirmCountry()
        },
        onCountryConfirmed = { confirmCountry() },
        onSmsClick = onSmsClick,
        onPlayGame = onPlayGame,
        onOpenLeaderboard = onOpenLeaderboard,
    )
}

/** Stateless Home: renders [state], reports intents through callbacks. */
@Composable
fun HomeContent(
    state: HomeUiState,
    onRequestRole: () -> Unit = {},
    onRequestNotifications: () -> Unit = {},
    onRequestOverlay: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onSeeAllHistory: () -> Unit = {},
    onRecentCallOpen: (CallEvent) -> Unit = {},
    onRecentCallDismiss: (CallEvent) -> Unit = {},
    onCountrySelected: (String) -> Unit = {},
    onCountryConfirmed: () -> Unit = {},
    onSmsClick: () -> Unit = {},
    onPlayGame: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
) {
    val isScreeningRoleHeld = state.isScreeningRoleHeld
    var showCountryDialog by remember { mutableStateOf(false) }
    val numberFormat = rememberNumberFormat()

    if (showCountryDialog) {
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            title = { Text(stringResource(R.string.home_country_dialog_title)) },
            text = {
                Column {
                    Countries.list.forEach { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                showCountryDialog = false
                                onCountrySelected(c.dial)
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.flag, Modifier.padding(end = 10.dp))
                            Text(c.name, Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCountryDialog = false }) { Text(stringResource(R.string.common_close)) }
            },
        )
    }

    ScrollableScreen(
        header = {
            GradientHeader(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(
                    if (isScreeningRoleHeld) R.string.home_protected else R.string.home_protection_to_enable,
                ),
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
        val prompt = state.recentCall
        if (prompt != null) {
            item(key = "recent-call-${prompt.call.key}") {
                RecentCallCard(
                    prompt = prompt,
                    fixedNow = state.now,
                    onOpen = { onRecentCallOpen(prompt.call) },
                    onDismiss = { onRecentCallDismiss(prompt.call) },
                    onSeeAll = onSeeAllHistory,
                )
            }
        } else if (isScreeningRoleHeld) {
            // Same slot, calm mode: the protection tells its story even when
            // there is nothing to triage.
            item(key = "recent-call-quiet") { RecentCallQuietRow() }
        }

        // Primary action first: update the list (visible without scrolling).
        item {
            val sync = state.sync
            OutlinedButton(
                onClick = onSyncNow,
                enabled = sync !is LoadState.Loading,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                if (sync is LoadState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.home_sync_updating), modifier = Modifier.padding(start = 8.dp))
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.home_sync_button))
                }
            }
            if (sync is LoadState.Loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Text(
                    if (state.syncProgress > 0)
                        stringResource(R.string.home_sync_progress, numberFormat.format(state.syncProgress))
                    else stringResource(R.string.home_sync_connecting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item {
            val (kind, msg) = when (val s = state.sync) {
                is LoadState.Error -> BannerKind.Error to s.message
                is LoadState.Success -> BannerKind.Success to s.message
                else -> BannerKind.Info to null
            }
            AnimatedBanner(kind, msg, Modifier.padding(horizontal = 16.dp))
        }
        item {
            if (state.showCountryBanner) {
                val c = Countries.byDial(state.countryDial)
                BorderedCard(
                    Modifier.padding(horizontal = 16.dp),
                    accent = MaterialTheme.colorScheme.primary,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(
                                stringResource(R.string.home_country_watched, c.flag, c.name),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.home_country_detected),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showCountryDialog = true }) { Text(stringResource(R.string.common_edit)) }
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.common_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onCountryConfirmed() }.padding(4.dp),
                        )
                    }
                }
            }
        }
        item {
            val shieldSteps = listOf(isScreeningRoleHeld, state.notificationsGranted)
            val shieldDone = shieldSteps.count { it }
            val shieldTotal = shieldSteps.size

            if (shieldDone < shieldTotal) {
                BorderedCard(
                    Modifier.padding(horizontal = 16.dp),
                    accent = if (isScreeningRoleHeld) WCColor.Amber else WCColor.Coral,
                ) {
                    Text(
                        stringResource(
                            if (isScreeningRoleHeld) R.string.home_shield_partial else R.string.home_shield_off,
                            shieldDone,
                            shieldTotal,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(
                            if (!isScreeningRoleHeld) R.string.home_shield_enable_filter
                            else R.string.home_shield_enable_notifs,
                        ),
                        Modifier.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = if (!isScreeningRoleHeld) onRequestRole else onRequestNotifications,
                    ) {
                        Icon(
                            Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            stringResource(
                                if (!isScreeningRoleHeld) R.string.home_enable_blocker else R.string.home_enable_alerts,
                            ),
                        )
                    }
                }
            }
            if (isScreeningRoleHeld) {
                // Saracroche-style "active and up to date" card, but in our emerald.
                ProtectionCard(
                    blocked = state.blocked,
                    warned = state.warned,
                    stats = state.stats,
                    now = state.now ?: System.currentTimeMillis(),
                    onSeeBlocked = onSeeAllHistory,
                )
            }
        }

        if (isScreeningRoleHeld && state.warnEnabled && !state.overlayGranted) {
            item {
                BorderedCard(
                    Modifier.padding(horizontal = 16.dp),
                    accent = WCColor.Amber,
                ) {
                    Text(
                        stringResource(R.string.home_overlay_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.home_overlay_body),
                        Modifier.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onRequestOverlay) {
                        Icon(
                            Icons.Rounded.Layers,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(R.string.home_overlay_cta))
                    }
                }
            }
        }

        // Community shield — compact shared counter (the collective mission).
        item {
            state.community?.let { c -> CommunityShieldCard(c) }
        }

        // SMS shield entry — green, distinct from the call card, tappable.
        item {
            val smsOn = state.smsOn
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
                        Text(stringResource(R.string.home_sms_title), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(if (smsOn) R.string.home_sms_on else R.string.home_sms_off),
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
                playedToday = state.playedToday,
                streak = state.streak,
                bestToday = state.bestToday,
                onPlay = onPlayGame,
                onLeaderboard = onOpenLeaderboard,
            )
        }
    }
}

/** Locale-aware grouping ("28 873 272" in fr, "28,873,272" in en). */
@Composable
private fun rememberNumberFormat(): NumberFormat {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return remember(locale) { NumberFormat.getInstance(locale) }
}

@Composable
private fun RecentCallCard(
    prompt: RecentCallPrompt,
    fixedNow: Long?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSeeAll: () -> Unit,
) {
    val call = prompt.call
    // Ticks every minute so "il y a 5 min" follows the clock during a session.
    val tickingNow by androidx.compose.runtime.produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            value = System.currentTimeMillis()
        }
    }
    val now = fixedNow ?: tickingNow
    val (title, message, color) = when (call.action) {
        CallEventAction.BLOCKED -> Triple(
            stringResource(R.string.home_recent_blocked_title),
            stringResource(R.string.home_recent_blocked_msg),
            WCColor.Coral,
        )
        CallEventAction.WARNED -> Triple(
            stringResource(R.string.home_recent_warned_title),
            stringResource(R.string.home_recent_warned_msg),
            WCColor.Amber,
        )
        CallEventAction.REPORTED_SPAM -> Triple(
            stringResource(R.string.home_recent_reported_title),
            stringResource(R.string.home_recent_reported_msg),
            WCColor.Coral,
        )
        CallEventAction.LEGITIMATE -> Triple(
            stringResource(R.string.home_recent_legit_title),
            stringResource(R.string.home_recent_legit_msg),
            WCColor.Emerald,
        )
        CallEventAction.UNKNOWN -> Triple(
            stringResource(R.string.home_recent_unknown_title),
            stringResource(R.string.home_recent_unknown_msg),
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
                    "${RelativeTime.format(LocalContext.current.resources, call.timestamp, now)} · $message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
            )
        }
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text(stringResource(R.string.home_recent_see_number))
        }
        if (prompt.others > 0) {
            // Dismissing the headline must not bury the rest of the backlog.
            TextButton(onClick = onSeeAll, modifier = Modifier.fillMaxWidth()) {
                Text(pluralStringResource(R.plurals.home_recent_others, prompt.others, prompt.others))
            }
        }
    }
}

/** Calm replacement for the recent-call slot when there is nothing to triage. */
@Composable
private fun RecentCallQuietRow() {
    BorderedCard(
        Modifier.padding(horizontal = 16.dp),
        accent = WCColor.Emerald,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = WCColor.Emerald,
                modifier = Modifier.size(22.dp),
            )
            Text(
                stringResource(R.string.home_quiet),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 10.dp),
            )
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
    now: Long,
    onSeeBlocked: () -> Unit,
) {
    val nf = rememberNumberFormat()
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
                stringResource(R.string.home_blocker_active),
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
                label = stringResource(R.string.home_covered_numbers),
                value = nf.format(s.coveredNumbers),
                subtitle = relativeUpdate(s.lastUpdate, now)?.let { stringResource(R.string.home_auto_update, it) },
            )
            // Per-country breakdown (matches the device's scope).
            s.countryNumbers?.let { cn ->
                val c = Countries.byDial(s.country ?: "")
                val arcep = s.countryArcepPatternCount
                    ?.let { stringResource(R.string.home_arcep_prefixes, nf.format(it)) } ?: ""
                Text(
                    stringResource(R.string.home_country_breakdown, c.flag, c.name, nf.format(cn), arcep),
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
            label = stringResource(R.string.home_blocked_calls),
            count = blocked,
            subtitle = stringResource(if (blocked == 0) R.string.home_none_yet else R.string.home_blocked_detail),
            onClick = onSeeBlocked,
        )
        CountRow(
            icon = Icons.Rounded.WarningAmber,
            color = WCColor.Amber,
            label = stringResource(R.string.home_suspect_calls),
            count = warned,
            subtitle = stringResource(if (warned == 0) R.string.home_none_yet else R.string.home_suspect_detail),
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

/** "à jour aujourd'hui / mise à jour il y a 3 j / MAJ le 12 juin" from an ISO-8601 date. */
@Composable
private fun relativeUpdate(iso: String?, now: Long): String? {
    if (iso.isNullOrBlank()) return null
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val time = runCatching { parser.parse(iso)?.time }.getOrNull() ?: return null
    val days = ((now - time) / 86_400_000L).toInt()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return when {
        days <= 0 -> stringResource(R.string.home_update_today)
        days == 1 -> stringResource(R.string.home_update_yesterday)
        days < 30 -> stringResource(R.string.home_update_days_ago, days)
        else -> stringResource(
            R.string.home_update_on,
            DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(time)),
        )
    }
}

/** Home entry for the daily games — a retention hook with a shareable streak. */
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
                    stringResource(R.string.home_games_row, extra),
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
private fun CommunityShieldCard(c: CommunityResponse) {
    val fmt = rememberNumberFormat()
    val progress = if (c.goal > 0) (c.week.toFloat() / c.goal).coerceIn(0f, 1f) else 0f
    BorderedCard(Modifier.padding(horizontal = 16.dp), accent = WCColor.Blue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🛡️")
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(stringResource(R.string.home_community_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.home_community_line, fmt.format(c.total), fmt.format(c.week)),
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
