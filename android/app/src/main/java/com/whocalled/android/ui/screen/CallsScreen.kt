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
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.whocalled.android.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.components.EmptyState
import com.whocalled.android.ui.components.GradientHeader
import com.whocalled.android.ui.components.ScrollableScreen
import com.whocalled.android.ui.components.StatusDot
import com.whocalled.android.ui.theme.WCColor
import com.whocalled.android.util.CallEvent
import com.whocalled.android.util.CallEventAction
import com.whocalled.android.util.CallDirection

private enum class CallsFilter(@param:StringRes val labelRes: Int) {
    ALL(R.string.calls_filter_all),
    REVIEW(R.string.calls_filter_review),
    FILTERED(R.string.calls_filter_filtered),
}

/** Who Called's own journal of the incoming calls seen by the screening service. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallsScreen(
    viewModel: MainViewModel,
    onCallClick: (CallEvent) -> Unit,
    isScreeningRoleHeld: Boolean,
) {
    val sections by viewModel.groupedCallEvents.collectAsState()
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
                }
            }
            section.takeIf { items.isNotEmpty() }?.copy(items = items)
        }
    }

    ScrollableScreen(
        header = {
            GradientHeader(
                title = stringResource(R.string.calls_title),
                subtitle = stringResource(R.string.calls_subtitle),
            )
        },
    ) {
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CallsFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(stringResource(option.labelRes)) },
                    )
                }
            }
        }

        if (displayedSections.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.PhoneMissed,
                    title = when (filter) {
                        CallsFilter.ALL -> stringResource(R.string.report_empty_recent_title)
                        CallsFilter.REVIEW -> stringResource(R.string.calls_empty_review_title)
                        CallsFilter.FILTERED -> stringResource(R.string.history_empty_title)
                    },
                    subtitle = if (isScreeningRoleHeld)
                        stringResource(R.string.calls_empty_body_on)
                    else
                        stringResource(R.string.calls_empty_body_off),
                    accent = WCColor.Blue,
                )
            }
        } else {
            item {
                Text(
                    stringResource(R.string.calls_copy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            displayedSections.forEach { section ->
                item(key = "header-${section.titleRes}") {
                    Text(
                        stringResource(section.titleRes),
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
                            Toast.makeText(context, context.getString(R.string.history_copied), Toast.LENGTH_SHORT).show()
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
        CallEventAction.BLOCKED -> Triple(stringResource(R.string.common_status_blocked), WCColor.Coral, Icons.Rounded.Block)
        CallEventAction.WARNED -> Triple(stringResource(R.string.common_status_warned), WCColor.Amber, Icons.Rounded.WarningAmber)
        CallEventAction.REPORTED_SPAM -> Triple(stringResource(R.string.common_vote_spam), WCColor.Coral, Icons.Rounded.Block)
        CallEventAction.LEGITIMATE -> Triple(stringResource(R.string.common_vote_legit), WCColor.Emerald, Icons.Rounded.CheckCircle)
        CallEventAction.UNKNOWN -> Triple(stringResource(R.string.calls_status_unknown), WCColor.Blue, Icons.Rounded.Search)
    }
    BorderedCard(
        Modifier
            .padding(horizontal = 16.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // weight(1f) + single-line ellipsis: a long
            // "+3312… · Entrant · il y a 3 j" never wraps onto a second line.
            Column(Modifier.weight(1f)) {
                Text(
                    "+${event.phone}",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val attempts = if (event.attempts > 1) {
                    " · " + pluralStringResource(R.plurals.calls_attempts, event.attempts, event.attempts)
                } else ""
                val direction = stringResource(directionLabel(event.direction))
                val context = LocalContext.current
                Text(
                    buildString {
                        append(direction)
                        append(" · ")
                        append(com.whocalled.android.util.RelativeTime.format(context, event.timestamp))
                        append(attempts)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Icon + colour only — the wording lives on the detail screen.
            StatusDot(
                label = label,
                color = color,
                icon = icon,
                modifier = Modifier.padding(start = 8.dp, end = 2.dp),
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.home_recent_see_number),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@StringRes
private fun directionLabel(direction: CallDirection): Int = when (direction) {
    CallDirection.INCOMING -> R.string.calls_direction_incoming
    CallDirection.BLOCKED -> R.string.common_status_blocked
}
