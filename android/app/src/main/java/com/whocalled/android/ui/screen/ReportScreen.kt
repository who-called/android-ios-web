package com.whocalled.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whocalled.android.data.ReportCategory
import com.whocalled.android.ui.LoadState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.OutlinedButton
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.theme.WCColor
import com.whocalled.android.ui.components.AnimatedBanner
import com.whocalled.android.ui.components.BannerKind
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.components.EmptyState
import com.whocalled.android.ui.components.GradientHeader

@Composable
fun ReportScreen(
    viewModel: MainViewModel,
    onOpenCalls: () -> Unit,
    onOpenMyReports: () -> Unit = {},
) {
    var phone by remember { mutableStateOf("") }
    var isSpam by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf(ReportCategory.TELEMARKETING) }
    val report by viewModel.report.collectAsState()
    val sharedPhone by viewModel.sharedPhone.collectAsState()

    // Pre-fill from the share sheet / trending tap (one-shot, then cleared).
    LaunchedEffect(sharedPhone) {
        viewModel.consumeSharedPhone()?.let { phone = it }
    }

    // A success banner from a vote made elsewhere (number detail) must not
    // greet the user under an empty form as if a send just happened.
    LaunchedEffect(Unit) { viewModel.clearReportState() }

    // After a successful send from THIS screen (Loading seen here first),
    // clear the field for the next report — stale successes don't count.
    var sendInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(report) {
        when {
            report is LoadState.Loading -> sendInFlight = true
            sendInFlight && report is LoadState.Success -> {
                phone = ""
                sendInFlight = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GradientHeader(
                title = "Signaler un numéro",
                subtitle = "Anonyme — aucun compte, aucune donnée personnelle.",
            )
        }

        item {
            OutlinedButton(
                onClick = onOpenMyReports,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ListAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Voir mes signalements")
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenCalls,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Choisir dans mes appels récents")
            }
        }

        item {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Numéro (ex. +33 6 12 34 56 78)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
            )
        }

        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                SegmentedButton(
                    selected = isSpam,
                    onClick = { isSpam = true },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Indésirable") }
                SegmentedButton(
                    selected = !isSpam,
                    onClick = { isSpam = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Légitime") }
            }
        }

        // Category chips — only relevant when reporting as spam.
        if (isSpam) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Type d’appel", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportCategory.entries.forEach { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(c.label) },
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    viewModel.submitReport(phone, isSpam, if (isSpam) category.api else null)
                },
                enabled = phone.isNotBlank() && report !is LoadState.Loading,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                if (report is LoadState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Envoyer le signalement")
                }
            }
        }

        item {
            val msg = report
            AnimatedBanner(
                kind = if (msg is LoadState.Error) BannerKind.Error else BannerKind.Success,
                message = when (msg) {
                    is LoadState.Error -> msg.message
                    is LoadState.Success -> msg.message
                    else -> null
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item { Text("", modifier = Modifier.padding(8.dp)) }
    }
}
