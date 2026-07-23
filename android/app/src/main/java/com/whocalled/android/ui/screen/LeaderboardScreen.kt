package com.whocalled.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whocalled.android.data.Preferences
import com.whocalled.android.game.GameDay
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.theme.WCColor

private data class Period(val key: String, val label: String)
private val PERIODS = listOf(Period("day", "Aujourd'hui"), Period("week", "Semaine"), Period("all", "Tout temps"))

private fun dayLabel(day: Long, today: Long): String = when (today - day) {
    0L -> "Aujourd'hui"; 1L -> "Hier"; else -> "il y a ${today - day} j"
}
private fun medal(rank: Int) = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#$rank" }
private fun gameName(game: String) = if (game == "trace") "TRACE" else "DEFENSE"
private fun unitLabel(game: String, waves: Int): String =
    if (game == "trace") "$waves grille${if (waves > 1) "s" else ""}" else "vague $waves"

@Composable
fun LeaderboardScreen(viewModel: MainViewModel, game: String = "defense", onBack: () -> Unit) {
    val today = remember { GameDay.epochDay() }
    var period by remember { mutableStateOf("day") }
    val board by viewModel.leaderboard.collectAsState()
    val loading by viewModel.leaderboardLoading.collectAsState()
    val history by viewModel.history.collectAsState()
    val localHistory by viewModel.localHistory.collectAsState()

    LaunchedEffect(period, game) { viewModel.loadLeaderboard(game, period, today) }
    LaunchedEffect(game) { viewModel.loadHistory(game) }

    // Local best for the selected period (offline fallback so nothing is ever blank).
    val localBest: Preferences.LocalDay? = remember(localHistory, period) {
        when (period) {
            "day" -> localHistory.firstOrNull { it.day == today }
            "week" -> localHistory.filter { it.day > today - 7 }.maxByOrNull { it.score }
            else -> localHistory.maxByOrNull { it.score }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour") }
            Text("Classement ${gameName(game)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        // Period tabs.
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PERIODS.forEach { p ->
                val sel = p.key == period
                Text(
                    p.label,
                    color = if (sel) WCColor.Blue else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                        .background(if (sel) WCColor.Blue.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                        .clickable { period = p.key }
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        val players = board?.players ?: 0
        // Summary.
        BorderedCard(Modifier.fillMaxWidth().padding(top = 4.dp), accent = WCColor.Blue) {
            Text(PERIODS.first { it.key == period }.label, fontWeight = FontWeight.Bold)
            Text(
                when {
                    players > 0 -> "${players} joueur${if (players > 1) "s" else ""} · meilleur ${board?.bestScore} · médiane ${board?.medianScore}"
                    localBest != null -> "1 joueur (toi) · meilleur ${localBest.score}"
                    else -> "Sois le premier à marquer un score ! 🚀"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Your position (server if ranked, else your local best so it's never blank).
        Spacer(Modifier.height(10.dp))
        BorderedCard(Modifier.fillMaxWidth(), accent = WCColor.Emerald) {
            val you = board?.you
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ton classement", fontWeight = FontWeight.Bold, color = WCColor.Emerald, modifier = Modifier.weight(1f))
                // rank / total — reads "1/1" when you're the only player.
                Text(
                    when {
                        you != null -> "#${you.rank}/$players"
                        localBest != null -> "#1/1"
                        else -> "—"
                    },
                    fontWeight = FontWeight.Bold, color = WCColor.Emerald, style = MaterialTheme.typography.titleMedium,
                )
            }
            when {
                you != null -> Text("Top ${you.topPercent}% · ${you.score} pts (${unitLabel(game, you.waves)})")
                localBest != null -> Text("${localBest.score} pts (${unitLabel(game, localBest.waves)})")
                else -> Text("Pas encore joué sur cette période — lance une partie !", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Top list.
        Spacer(Modifier.height(12.dp))
        Text("Top", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
        val top = board?.top ?: emptyList()
        if (top.isEmpty()) {
            // Never blank: show your own local best as the sole entry.
            if (localBest != null) {
                LeaderRow("🥇", localBest.score, localBest.waves, mine = true, game = game)
                Text("Ton score s'affichera dans le classement dès la synchro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            } else {
                Text("Aucun score pour l'instant. À toi de jouer !", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            top.forEach { e ->
                val mine = board?.you != null && e.rank == board!!.you!!.rank && e.score == board!!.you!!.score
                LeaderRow(medal(e.rank), e.score, e.waves, mine, game)
            }
        }

        // Your history — server (with rank) if available, else local.
        Spacer(Modifier.height(18.dp))
        Text("Ton historique", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
        if (history.isNotEmpty()) {
            history.forEach { h ->
                HistoryRow(dayLabel(h.day, today), h.score, "#${h.rank}/${h.players} · top ${h.topPercent}%")
            }
        } else if (localHistory.isNotEmpty()) {
            localHistory.forEach { h -> HistoryRow(dayLabel(h.day, today), h.score, unitLabel(game, h.waves)) }
        } else {
            Text("Joue une partie pour démarrer ton historique.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (loading && board == null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LeaderRow(rankLabel: String, score: Int, waves: Int, mine: Boolean, game: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (mine) WCColor.Emerald.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rankLabel, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
        Text("$score pts", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(unitLabel(game, waves), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryRow(day: String, score: Int, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(day, modifier = Modifier.weight(1f))
        Text("$score pts", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 12.dp))
        Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
