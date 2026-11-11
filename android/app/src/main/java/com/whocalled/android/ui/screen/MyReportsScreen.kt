package com.whocalled.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.whocalled.android.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.components.EmptyState
import com.whocalled.android.ui.components.GradientHeader
import com.whocalled.android.ui.components.ScrollableScreen

@Composable
fun MyReportsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenNumber: (String) -> Unit = {},
) {
    val reports by viewModel.myReports.collectAsState()
    val reportState by viewModel.report.collectAsState()
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    // A stale success/error from another screen must not greet the user here.
    LaunchedEffect(Unit) { viewModel.clearReportState() }

    pendingDelete?.let { phone ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.myreports_delete_title)) },
            text = { Text(stringResource(R.string.myreports_delete_body, phone)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReport(phone)
                    pendingDelete = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    ScrollableScreen(
        header = {
            GradientHeader(
                title = stringResource(R.string.myreports_title),
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White,
                        )
                    }
                },
            )
        },
    ) {
        item {
            Text(
                stringResource(R.string.myreports_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        // Feedback for flips/retries done from this screen — silence looked
        // like success even when the push failed.
        item {
            val (kind, msg) = when (val s = reportState) {
                is com.whocalled.android.ui.LoadState.Error ->
                    com.whocalled.android.ui.components.BannerKind.Error to s.message
                is com.whocalled.android.ui.LoadState.Success ->
                    com.whocalled.android.ui.components.BannerKind.Success to s.message
                else -> com.whocalled.android.ui.components.BannerKind.Info to null
            }
            com.whocalled.android.ui.components.AnimatedBanner(kind, msg, Modifier.padding(horizontal = 16.dp))
        }

        if (reports.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Flag,
                    title = stringResource(R.string.myreports_empty_title),
                    subtitle = stringResource(R.string.myreports_empty_body),
                )
            }
        } else {
            items(reports, key = { it.phone }) { report ->
                BorderedCard(Modifier.padding(horizontal = 16.dp).clickable { onOpenNumber(report.phone) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("+${report.phone}", fontWeight = FontWeight.Bold)
                            val vote = stringResource(
                                if (report.vote == "spam") R.string.common_vote_spam else R.string.common_vote_legit,
                            )
                            val sync = stringResource(
                                when (report.syncState) {
                                    "synced" -> R.string.myreports_sync_sent
                                    "failed" -> R.string.myreports_sync_failed
                                    else -> R.string.myreports_sync_pending
                                },
                            )
                            Text(
                                "$vote · $sync · ${
                                    com.whocalled.android.util.RelativeTime.format(LocalContext.current, report.updatedAt)
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (report.syncState == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (report.syncState == "failed") {
                                TextButton(onClick = { viewModel.retryPending() }) {
                                    Text(stringResource(R.string.myreports_resend_now))
                                }
                            }
                        }
                        // Change one's mind: flip the vote (the repository
                        // keeps the original category across flips).
                        IconButton(onClick = {
                            viewModel.submitReport(
                                report.phone,
                                isSpam = report.vote != "spam",
                                category = report.category,
                            )
                        }) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = stringResource(R.string.myreports_change_mind))
                        }
                        // Delete a mistaken report — via confirmation, not a direct tap.
                        IconButton(onClick = { pendingDelete = report.phone }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
