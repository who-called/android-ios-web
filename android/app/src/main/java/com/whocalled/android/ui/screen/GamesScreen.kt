package com.whocalled.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whocalled.android.data.Preferences
import com.whocalled.android.game.GameDay
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.components.GradientHeader
import com.whocalled.android.ui.components.ScrollableScreen
import com.whocalled.android.ui.theme.WCColor

/**
 * Games hub — the "Jeux" tab. Lists every mini-game with its own best/streak and
 * routes to the game + its dedicated leaderboard. Room for more games later.
 */
@Composable
fun GamesScreen(
    viewModel: MainViewModel,
    onPlay: (String) -> Unit,
    onPractice: (String) -> Unit = {},
    onStreak: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val today = remember { GameDay.epochDay() }
    var defense by remember { mutableStateOf<Preferences.GameState?>(null) }
    var trace by remember { mutableStateOf<Preferences.GameState?>(null) }
    LaunchedEffect(Unit) {
        defense = Preferences.gameState(context, "defense")
        trace = Preferences.gameState(context, "trace")
    }

    ScrollableScreen(header = { GradientHeader(title = "Jeux", subtitle = "Un défi par jour, ton classement") }) {
        item {
            GameCard(
                emoji = "🛡️",
                title = "DEFENSE",
                tagline = "Arcade — détruis les appels spam.",
                accent = WCColor.Coral,
                state = defense,
                today = today,
                onPlay = { onPlay("defense") },
                onStreak = { onStreak("defense") },
            )
        }
        item {
            GameCard(
                emoji = "🕵️",
                title = "TRACE",
                tagline = "Puzzle — démasque le fraudeur caché.",
                accent = WCColor.Blue,
                state = trace,
                today = today,
                onPlay = { onPlay("trace") },
                onStreak = { onStreak("trace") },
            )
        }
        item {
            // Practice lives outside the cards so both games present identically.
            Text(
                "🎯 Entraînement libre TRACE — non classé",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = WCColor.Blue,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onPractice("trace") }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        item {
            Text(
                "Nouveau défi chaque jour · 3 parties classées / jour · 100 % anonyme.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun GameCard(
    emoji: String,
    title: String,
    tagline: String,
    accent: androidx.compose.ui.graphics.Color,
    state: Preferences.GameState?,
    today: Long,
    onPlay: () -> Unit,
    onStreak: () -> Unit,
) {
    val playedToday = state?.lastPlayedDay == today
    val attemptsLeft = Preferences.MAX_ATTEMPTS - (if (playedToday) (state?.attemptsToday ?: 0) else 0)
    // The whole card opens the game — not just the "Jouer" button. The leaderboard
    // link was removed here; it lives inside the game screen itself.
    BorderedCard(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPlay),
        accent = accent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, modifier = Modifier.size(34.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(8.dp))
                    // Tappable daily-streak chip → the "Série quotidienne" screen.
                    Text(
                        "☀️ ${state?.streak ?: 0}",
                        color = WCColor.Amber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(WCColor.Amber.copy(alpha = 0.14f))
                            .clickable(onClick = onStreak)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(10.dp))
        val best = state?.bestScoreAllTime ?: 0
        Text(
            if (best > 0) "Record : $best pts" else "Pas encore joué",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(8.dp))
        Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
            Text(
                when {
                    !playedToday -> "Jouer le défi du jour ▸"
                    attemptsLeft > 0 -> "Jouer ($attemptsLeft essai${if (attemptsLeft > 1) "s" else ""} classé${if (attemptsLeft > 1) "s" else ""} restant${if (attemptsLeft > 1) "s" else ""})"
                    else -> "Rejouer (entraînement)"
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
