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
    onRequestCallLogPermission: () -> Unit,
    onOpenNumber: (String) -> Unit = {},
) {
    var phone by remember { mutableStateOf("") }
    var isSpam by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf(ReportCategory.TELEMARKETING) }
    val report by viewModel.report.collectAsState()
    val systemCalls by viewModel.systemCalls.collectAsState()
    val hasPermission by viewModel.callLogPermission.collectAsState()
    val sharedPhone by viewModel.sharedPhone.collectAsState()

    val myReports by viewModel.myReports.collectAsState()
    val reportedPhones = remember(myReports) { myReports.map { it.phone }.toSet() }

    // Default: hide known contacts so only unidentified numbers (the ones worth
    // reporting) are shown. Anonymous/withheld calls (no number) are always dropped.
    var hideContacts by remember { mutableStateOf(true) }
    val identifiableCalls = remember(systemCalls) {
        systemCalls.filter { !it.rawNumber.isBlank() && it.normalizedPhone != null }
    }
    val displayedCalls = remember(identifiableCalls, hideContacts) {
        if (hideContacts) identifiableCalls.filter { !it.isContact } else identifiableCalls
    }
    val hiddenContactsCount = remember(identifiableCalls) { identifiableCalls.count { it.isContact } }

    // Re-check the permission whenever this screen is shown (covers the grant flow).
    LaunchedEffect(Unit) { viewModel.refreshCallLogPermission() }

    // Pre-fill from the share sheet / trending tap (one-shot, then cleared).
    LaunchedEffect(sharedPhone) {
        viewModel.consumeSharedPhone()?.let { phone = it }
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

        item {
            Text(
                "Depuis vos appels récents",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Filter: hide contacts by default (only unidentified numbers shown).
        if (hasPermission && hiddenContactsCount > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (hideContacts) "Mes contacts masqués ($hiddenContactsCount)" else "Mes contacts affichés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { hideContacts = !hideContacts }) {
                        Text(if (hideContacts) "Tout afficher" else "Masquer les contacts")
                    }
                }
            }
        }

        if (!hasPermission) {
            item {
                BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                    Text("Autorisez l’accès au journal d’appels pour signaler en un geste.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRequestCallLogPermission) { Text("Autoriser") }
                }
            }
        } else if (displayedCalls.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = if (hiddenContactsCount > 0) "Aucun numéro inconnu" else "Aucun appel récent",
                    subtitle = if (hiddenContactsCount > 0)
                        "Tous vos appels récents proviennent de contacts connus. Touchez « Tout afficher » pour les voir."
                    else "Vos appels récents s’afficheront ici pour les signaler en un geste.",
                )
            }
        } else {
            items(displayedCalls) { call ->
                val reported = call.normalizedPhone in reportedPhones
                BorderedCard(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .clickable { call.normalizedPhone?.let(onOpenNumber) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            // Show the contact name when known, the number underneath.
                            Text(call.contactName ?: call.rawNumber, fontWeight = FontWeight.Bold)
                            Text(
                                (if (call.contactName != null) "${call.rawNumber} · " else "") +
                                    com.whocalled.android.util.RelativeTime.format(call.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = "Voir le détail",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (reported) {
                        // Reactive: after a tap, the report lands in myReports and the
                        // row flips to this confirmation — clear feedback, no re-tap.
                        Row(
                            Modifier.padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WCColor.Emerald, modifier = Modifier.size(18.dp))
                            Text(
                                "  Déjà signalé — merci 😊",
                                color = WCColor.Emerald,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.submitReport(call.rawNumber, isSpam = true, ReportCategory.OTHER.api) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Text("  Indésirable")
                            }
                            OutlinedButton(
                                onClick = { viewModel.submitReport(call.rawNumber, isSpam = false) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WCColor.Emerald, modifier = Modifier.size(18.dp))
                                Text("  Légitime")
                            }
                        }
                    }
                }
            }
        }

        item { Text("", modifier = Modifier.padding(8.dp)) }
    }
}
