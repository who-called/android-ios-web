package com.whocalled.android.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whocalled.android.BuildConfig
import com.whocalled.android.R
import com.whocalled.android.data.ReportCategory
import com.whocalled.android.network.LookupResponse
import com.whocalled.android.ui.LoadState
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.WarnAlertContext
import com.whocalled.android.ui.components.AnimatedBanner
import com.whocalled.android.ui.components.BannerKind
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.components.ExpandableSection
import com.whocalled.android.ui.components.ScoreGauge
import com.whocalled.android.ui.components.StatusBadge
import com.whocalled.android.ui.theme.WCColor
import com.whocalled.android.util.CallDirection
import com.whocalled.android.util.CallEventAction
import com.whocalled.android.util.UiLocale
import java.text.DateFormat
import java.util.Date

private enum class NumberAction { CALL, SMS }

/**
 * Number detail.
 *
 * Layout priority: the verdict, who the number is, and what the user can DO
 * about it all fit on the first screenful. Everything explanatory (how the
 * evaluation is built, the per-call history) lives in collapsed sections — still
 * one tap away, but no longer pushing the vote buttons below the fold.
 */
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
    // A caller name can be far longer than one line. It stays ellipsized so the
    // card keeps its shape, and a tap unfolds it in place — the number right
    // below keeps its own tap action (copy), so the two never compete.

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
    val isBlocked = rule == "block" || (rule == null && status == "block")
    val incomingWarn by viewModel.warnAlert.collectAsState()
    var stickyWarn by remember(callId, phone) { mutableStateOf<WarnAlertContext?>(null) }
    LaunchedEffect(incomingWarn) {
        if (incomingWarn != null) {
            stickyWarn = incomingWarn
            viewModel.clearWarnAlert()
        }
    }
    val warnEvent = remember(call, recentHistory, stickyWarn) {
        call?.takeIf { it.action == "warned" }?.let {
            WarnAlertContext(it.timestamp, it.spamScore, it.category)
        } ?: stickyWarn ?: recentHistory.firstOrNull { it.action == CallEventAction.WARNED }?.let {
            WarnAlertContext(it.timestamp, it.spamScore, it.category)
        }
    }

    fun launchNumberAction(action: NumberAction) {
        val target = displayedPhone ?: return
        val intent = when (action) {
            NumberAction.CALL -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$target"))
            NumberAction.SMS -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+$target"))
        }
        runCatching { context.startActivity(intent) }
    }

    fun confirmOrLaunch(action: NumberAction) {
        if (status == "block" || status == "warn") pendingAction = action else launchNumberAction(action)
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (status == "block") stringResource(R.string.detail_confirm_title_spam) else stringResource(R.string.detail_confirm_title_suspect)) },
            text = {
                Text(
                    if (action == NumberAction.CALL)
                        stringResource(R.string.detail_confirm_call_body)
                    else
                        stringResource(R.string.detail_confirm_sms_body),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    launchNumberAction(action)
                }) { Text(stringResource(R.string.detail_confirm_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text(stringResource(R.string.common_cancel)) }
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
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
                Text(stringResource(R.string.detail_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        if (displayedPhone == null) {
            item { Text(stringResource(R.string.common_loading)) }
            return@LazyColumn
        }

        if (warnEvent != null) {
            item {
                val categoryLabel = ReportCategory.fromApi(warnEvent.category)
                    ?.takeIf { it != ReportCategory.OTHER }?.labelRes?.let { stringResource(it) }
                BorderedCard(accent = WCColor.Amber) {
                    Text(
                        formatReceivedAt(context, warnEvent.at),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        warnScoreExplanation(warnEvent.score, categoryLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // 1) Verdict + identity, all in one card: score, name, number, badges and
        //    the raw community counts on a single line.
        item {
            BorderedCard(accent = statusColor) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ScoreGauge(
                        score = lookup?.spamScore ?: call?.spamScore ?: 0,
                        status = status,
                        diameter = 68.dp,
                        stroke = 6.dp,
                    )
                    // weight(1f) bounds the column so a long name ellipsizes instead
                    // of pushing the badges out of the card.
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            "+$displayedPhone",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                clipboard.setText(AnnotatedString("+$displayedPhone"))
                            },
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            StatusBadge(
                                label = when (status) {
                                    "block" -> stringResource(R.string.common_vote_spam)
                                    "warn" -> stringResource(R.string.detail_status_suspect)
                                    "allow" -> stringResource(R.string.detail_status_rather_legit)
                                    else -> stringResource(R.string.detail_status_little_data)
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
                    }
                }
                lookup?.let { stats ->
                    Text(
                        communitySummary(stats),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Text(
                    verdictExplanation(status, lookup?.confidenceLevel ?: "none"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (lookupLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp).size(14.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.detail_stats_loading), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // 2) Everything the user can do, on one row instead of three cards.
        item {
            BorderedCard {
                Row(Modifier.fillMaxWidth()) {
                    ActionTile(
                        Modifier.weight(1f),
                        Icons.Rounded.Phone,
                        stringResource(R.string.detail_action_call),
                    ) { confirmOrLaunch(NumberAction.CALL) }
                    ActionTile(
                        Modifier.weight(1f),
                        Icons.AutoMirrored.Rounded.Message,
                        stringResource(R.string.detail_action_sms),
                    ) { confirmOrLaunch(NumberAction.SMS) }
                    ActionTile(
                        Modifier.weight(1f),
                        if (isBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                        if (isBlocked) stringResource(R.string.report_allow) else stringResource(R.string.detail_action_block),
                        tint = if (isBlocked) WCColor.Emerald else WCColor.Coral,
                    ) {
                        if (isBlocked) viewModel.unblock(displayedPhone) else viewModel.block(displayedPhone)
                    }
                    ActionTile(
                        Modifier.weight(1f),
                        Icons.Rounded.ContentCopy,
                        stringResource(R.string.detail_action_copy),
                    ) { clipboard.setText(AnnotatedString("+$displayedPhone")) }
                }
                // Only surfaced once a personal rule actually exists — no line of
                // text just to say "nothing special here".
                rule?.let {
                    Text(
                        if (it == "allow")
                            stringResource(R.string.detail_rule_always_allowed)
                        else
                            stringResource(R.string.detail_rule_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it == "allow") WCColor.Emerald else WCColor.Coral,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // 3) The contribution ask, kept above the fold.
        item {
            BorderedCard {
                Text(stringResource(R.string.detail_your_opinion), fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    SegmentedButton(
                        selected = myVote == "spam",
                        onClick = {
                            // No category yet — the chips below refine it; a
                            // hard-coded "unknown" starved the community's
                            // "pourquoi ce numéro appelle" statistics.
                            viewModel.submitReport(displayedPhone, isSpam = true)
                        },
                        enabled = report !is LoadState.Loading,
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) {
                        Text(stringResource(R.string.common_vote_spam))
                    }
                    SegmentedButton(
                        selected = myVote == "legit",
                        onClick = { viewModel.submitReport(displayedPhone, isSpam = false) },
                        enabled = report !is LoadState.Loading,
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) {
                        Text(stringResource(R.string.common_vote_legit))
                    }
                }
                // One optional tap: why is this number unwanted? A re-vote is
                // free server-side, so the chip just refines the same report.
                if (myVote == "spam") {
                    val myCategory = myReports.firstOrNull { it.phone == displayedPhone }?.category
                    Text(
                        stringResource(R.string.detail_why_optional),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportCategory.entries.filter { it != ReportCategory.OTHER }.forEach { c ->
                            androidx.compose.material3.FilterChip(
                                selected = myCategory == c.api,
                                onClick = {
                                    viewModel.submitReport(displayedPhone, isSpam = true, c.api)
                                },
                                enabled = report !is LoadState.Loading,
                                label = { Text(stringResource(c.labelRes)) },
                            )
                        }
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

        // 4) ARCEP attribution stays expanded, never behind a tap: the Play
        //    "misleading claims" policy requires the official source link and the
        //    non-affiliation notice to be visible whenever we lean on that list.
        if (isArcep) {
            item {
                BorderedCard(accent = WCColor.Indigo) {
                    Text(
                        arcepMatch?.pattern?.let { stringResource(R.string.detail_arcep_range_pattern, it) }
                            ?: stringResource(R.string.detail_arcep_range),
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.ARCEP_SOURCE_URL)),
                            )
                        },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp).size(16.dp),
                        )
                        Text(stringResource(R.string.detail_arcep_official_source))
                    }
                    Text(
                        stringResource(R.string.detail_arcep_not_affiliated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 5) The reasoning behind the verdict — useful, but not what the user
        //    opened the screen for.
        item {
            ExpandableSection(
                title = stringResource(R.string.detail_why_evaluation),
                subtitle = sourceHeadline(source, isArcep, hasCommunityData),
            ) {
                Text(
                    sourceExplanation(source, isArcep, hasCommunityData, arcepMatch?.pattern),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lookup?.let { stats -> EvaluationDetail(stats) }
            }
        }

        // 6) Local call history + the filtered-call specifics.
        if (recentHistory.isNotEmpty() || call != null) {
            item {
                ExpandableSection(
                    title = stringResource(R.string.detail_your_calls),
                    subtitle = if (recentHistory.isEmpty()) null else
                        stringResource(R.string.detail_your_calls_subtitle, recentHistory.size, callsLast7Days),
                ) {
                    call?.let { filteredCall ->
                        DetailLine(
                            stringResource(R.string.report_call_type),
                            stringResource(
                                ReportCategory.fromApi(lookup?.category ?: filteredCall.category)?.labelRes
                                    ?: R.string.category_unknown,
                            ),
                        )
                        DetailLine(
                            if (filteredCall.action == "allowed") stringResource(R.string.detail_received_on) else stringResource(R.string.detail_filtered_on),
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, UiLocale.of(context))
                                .format(Date(filteredCall.timestamp)),
                        )
                    }
                    recentHistory.forEach { event ->
                        DetailLine(
                            callHistoryLabel(event.direction),
                            com.whocalled.android.util.RelativeTime.format(context, event.timestamp),
                        )
                    }
                }
            }
        }
    }
}

/** One compact tap target in the actions row (icon over a short label). */
@Composable
private fun ActionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun SourceBadge(source: String, isArcep: Boolean, hasCommunityData: Boolean) {
    when {
        source == "mixed" -> StatusBadge(stringResource(R.string.detail_badge_mixed), WCColor.Indigo, Icons.Rounded.Gavel)
        isArcep -> StatusBadge(stringResource(R.string.detail_badge_arcep), WCColor.Indigo, Icons.Rounded.Gavel)
        hasCommunityData -> StatusBadge(stringResource(R.string.detail_badge_community), WCColor.Slate, Icons.Rounded.Group)
        else -> StatusBadge(stringResource(R.string.detail_badge_none), WCColor.Blue, Icons.Rounded.Search)
    }
}

/**
 * The raw community counts and the reliability of the verdict, condensed into one
 * readable line — this used to be a card with two big numbers and a paragraph.
 */
@Composable
private fun communitySummary(stats: LookupResponse): AnnotatedString {
    val spam = pluralStringResource(R.plurals.detail_spam_count, stats.reportCountSpam, stats.reportCountSpam)
    val legit = pluralStringResource(R.plurals.detail_legit_count, stats.reportCountLegit, stats.reportCountLegit)
    val confidence = confidenceSummary(stats.confidenceLevel ?: "none")
    return buildAnnotatedString {
        appendCount(spam, stats.reportCountSpam, WCColor.Coral)
        append("  ·  ")
        appendCount(legit, stats.reportCountLegit, WCColor.Emerald)
        append("  ·  ")
        append(confidence)
    }
}

/** Appends a resolved "N label" string, keeping only the number N coloured and bold. */
private fun AnnotatedString.Builder.appendCount(text: String, count: Int, color: Color) {
    val start = length
    append(text)
    val number = count.toString()
    val at = text.indexOf(number)
    if (at >= 0) {
        addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), start + at, start + at + number.length)
    }
}

/** Everything that was spread over the old stats card, now inside the fold. */
@Composable
private fun EvaluationDetail(stats: LookupResponse) {
    val level = stats.confidenceLevel ?: "none"
    Text(
        confidenceExplanation(level, stats.confidence),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        stringResource(R.string.detail_raw_counts_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )

    // Community "why did it call?" headline — only when we have a known reason.
    stats.topReason
        ?.takeIf { it.category != "unknown" && it.count > 0 }
        ?.let { top ->
            val label = ReportCategory.fromApi(top.category)?.labelRes?.let { stringResource(it) } ?: top.category
            Text(
                stringResource(R.string.detail_stats_top_reason, label, top.share),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = WCColor.Coral,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

    val frequency = stats.frequency
    if (frequency.last1y > 0) {
        Text(stringResource(R.string.detail_reviews_activity), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.detail_stats_24h), frequency.last24h.toString(), MaterialTheme.colorScheme.primary)
            Stat(stringResource(R.string.detail_stats_7d), frequency.last7d.toString(), MaterialTheme.colorScheme.primary)
            Stat(stringResource(R.string.detail_stats_30d), frequency.last30d.toString(), MaterialTheme.colorScheme.primary)
            Stat(stringResource(R.string.detail_stats_1y), frequency.last1y.toString(), MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun sourceHeadline(source: String, isArcep: Boolean, hasCommunityData: Boolean): String = when {
    source == "mixed" -> stringResource(R.string.detail_headline_mixed)
    isArcep -> stringResource(R.string.detail_arcep_range)
    hasCommunityData -> stringResource(R.string.detail_headline_community)
    else -> stringResource(R.string.detail_headline_none)
}

@Composable
private fun sourceExplanation(
    source: String,
    isArcep: Boolean,
    hasCommunityData: Boolean,
    arcepPattern: String?,
): String = when {
    source == "mixed" ->
        stringResource(R.string.detail_explanation_mixed)
    isArcep ->
        arcepPattern?.let { stringResource(R.string.detail_explanation_arcep_pattern, it) }
            ?: stringResource(R.string.detail_explanation_arcep)
    hasCommunityData ->
        stringResource(R.string.detail_explanation_community)
    else ->
        stringResource(R.string.detail_explanation_none)
}

private fun formatReceivedAt(context: android.content.Context, timestamp: Long): String {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT, UiLocale.of(context)).format(Date(timestamp))
    val then = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = java.util.Calendar.getInstance()
    val sameDay = now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR) &&
        now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    return if (sameDay) {
        context.getString(R.string.detail_received_today, time)
    } else {
        val date = DateFormat.getDateInstance(DateFormat.SHORT, UiLocale.of(context)).format(Date(timestamp))
        context.getString(R.string.detail_received_on_date, date, time)
    }
}

@Composable
private fun warnScoreExplanation(score: Int, categoryLabel: String?): String =
    categoryLabel?.let { stringResource(R.string.detail_warn_score_explanation_category, score, it) }
        ?: stringResource(R.string.detail_warn_score_explanation, score)

@Composable
private fun verdictExplanation(status: String, confidenceLevel: String): String = when (status) {
    "block" -> if (confidenceLevel == "high") stringResource(R.string.detail_verdict_block_high)
        else stringResource(R.string.detail_verdict_block)
    "warn" -> stringResource(R.string.detail_verdict_warn)
    "allow" -> if (confidenceLevel == "high") stringResource(R.string.detail_verdict_allow_high)
        else stringResource(R.string.detail_verdict_allow)
    else -> stringResource(R.string.detail_verdict_unknown)
}

/** Inline form of the reliability label, for the one-line summary. */
@Composable
private fun confidenceSummary(level: String): String = when (level) {
    "official" -> stringResource(R.string.detail_confidence_official)
    "high" -> stringResource(R.string.detail_confidence_high)
    "medium" -> stringResource(R.string.detail_confidence_medium)
    "low" -> stringResource(R.string.detail_confidence_low)
    else -> stringResource(R.string.detail_confidence_none)
}

@Composable
private fun confidenceExplanation(level: String, confidence: Int?): String = when (level) {
    "official" -> stringResource(R.string.detail_confidence_official_body)
    "high" -> stringResource(R.string.detail_confidence_high_body, confidence ?: 100)
    "medium" -> stringResource(R.string.detail_confidence_medium_body, confidence ?: 0)
    "low" -> stringResource(R.string.detail_confidence_low_body, confidence ?: 0)
    else -> stringResource(R.string.detail_confidence_none_body)
}

@Composable
private fun callHistoryLabel(direction: CallDirection): String = when (direction) {
    CallDirection.INCOMING -> stringResource(R.string.calls_direction_incoming)
    CallDirection.BLOCKED -> stringResource(R.string.common_status_blocked)
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A label/value row without its own card — for grouping several in one card. */
@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
