package com.whocalled.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Flag
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
import com.whocalled.android.ui.LoadState
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.components.AnimatedBanner
import com.whocalled.android.ui.components.BannerKind
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.components.ScoreGauge
import com.whocalled.android.ui.components.StatusBadge
import com.whocalled.android.ui.theme.WCColor

/**
 * Phone-based number detail (opened from the recent-calls / my-reports lists).
 * Reuses the lookup + block toggle; no call-log entry required.
 */
@Composable
fun NumberLookupScreen(
    viewModel: MainViewModel,
    phone: String,
    onBack: () -> Unit,
) {
    LaunchedEffect(phone) {
        viewModel.loadNumberDetail(phone)
        viewModel.clearReportState()
    }
    val lookup by viewModel.lookup.collectAsState()
    val loading by viewModel.lookupLoading.collectAsState()
    val arcep by viewModel.arcepMatch.collectAsState()
    val rule by viewModel.detailRule.collectAsState()
    val report by viewModel.report.collectAsState()
    val clipboard = LocalClipboardManager.current

    val isArcep = lookup?.source == "arcep" || arcep != null
    val status = lookup?.status ?: "unknown"
    val statusColor = when (status) {
        "block" -> WCColor.Coral
        "warn" -> WCColor.Amber
        else -> WCColor.Emerald
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour") }
                Text("Détail du numéro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        item {
            BorderedCard(accent = statusColor) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ScoreGauge(score = lookup?.spamScore ?: 0, diameter = 64.dp, stroke = 6.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("+$phone", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        SourceBadge(isArcep)
                        Text("Indice de spam", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (loading) {
            item {
                BorderedCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                        Text("Chargement des statistiques…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            lookup?.let { stats -> item { StatsCard(stats) } }
        }

        // Block / unblock — reflects current state (see BlockToggle).
        item {
            BlockToggle(
                rule = rule,
                effectivelyBlocked = rule == "block" || (rule == null && (isArcep || status == "block")),
                onBlock = { viewModel.block(phone) },
                onUnblock = { viewModel.unblock(phone) },
            )
        }

        item {
            Button(
                onClick = { viewModel.submitReport(phone, isSpam = true, ReportCategory.OTHER.api) },
                enabled = report !is LoadState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Signaler comme indésirable")
            }
        }
        item {
            val (kind, msg) = when (val r = report) {
                is LoadState.Success -> BannerKind.Success to r.message
                is LoadState.Error -> BannerKind.Error to r.message
                else -> BannerKind.Info to null
            }
            AnimatedBanner(kind, msg)
        }
        item {
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString("+$phone")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Copier le numéro")
            }
        }
    }
}
