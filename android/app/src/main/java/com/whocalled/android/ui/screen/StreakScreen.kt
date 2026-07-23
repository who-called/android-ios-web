package com.whocalled.android.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whocalled.android.data.Preferences
import com.whocalled.android.game.GameDay
import com.whocalled.android.ui.theme.WCColor
import java.time.DayOfWeek
import java.time.LocalDate

private fun gameName(game: String) = if (game == "trace") "TRACE" else "DEFENSE"
private fun weekdayFr(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "LUN"; DayOfWeek.TUESDAY -> "MAR"; DayOfWeek.WEDNESDAY -> "MER"
    DayOfWeek.THURSDAY -> "JEU"; DayOfWeek.FRIDAY -> "VEN"; DayOfWeek.SATURDAY -> "SAM"
    DayOfWeek.SUNDAY -> "DIM"
}

/**
 * Per-game daily streak ("Série quotidienne") — a big sun, the current + best
 * streak, and a 7-day activity strip. The streak is proper to each game and bumps
 * once on the first play of a new day (see [Preferences.recordGame]).
 */
@Composable
fun StreakScreen(game: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val today = remember { GameDay.epochDay() }
    var state by remember { mutableStateOf<Preferences.GameState?>(null) }
    var playedDays by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(game) {
        state = Preferences.gameState(context, game)
        playedDays = Preferences.localHistory(context, game).map { it.day }.toSet()
    }
    val streak = state?.streak ?: 0
    val best = state?.bestStreak ?: 0
    val playedToday = state?.lastPlayedDay == today

    // Ambient sun pulse, brighter when the streak is kept today.
    val infinite = rememberInfiniteTransition(label = "sun")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (playedToday) 1.12f else 1.05f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "pulse",
    )
    // The streak number pops in on entry (bigger celebration when kept today).
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(state) { if (state != null) appeared = true }
    val numScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.4f,
        animationSpec = spring(dampingRatio = if (playedToday) 0.4f else Spring.DampingRatioMediumBouncy),
        label = "num",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(WCColor.Amber.copy(alpha = 0.20f), MaterialTheme.colorScheme.background)))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Column(Modifier.weight(1f)) {
                Text("Série quotidienne", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(gameName(game), style = MaterialTheme.typography.bodySmall, color = WCColor.Amber, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("☀️", fontSize = 110.sp, modifier = Modifier.scale(pulse))
        Spacer(Modifier.height(12.dp))
        Text("$streak", fontSize = 64.sp, fontWeight = FontWeight.Black, color = WCColor.Amber, modifier = Modifier.scale(numScale))
        Text("Série actuelle", color = WCColor.Amber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(10.dp))
        Text(
            "Meilleure série : $best",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Spacer(Modifier.height(28.dp))
        // 7-day activity strip (last 7 days ending today).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in (today - 6)..today) {
                val date = LocalDate.ofEpochDay(d)
                val played = playedDays.contains(d)
                val isToday = d == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        weekdayFr(date),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = if (isToday) WCColor.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (played) WCColor.Amber else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (played) Text("✓", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                        else if (isToday) Text("·", color = WCColor.Amber, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            if (playedToday) "Bien joué — série tenue aujourd'hui ! 🔥"
            else "Joue aujourd'hui pour continuer ta série ☀️",
            textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
            color = if (playedToday) WCColor.Emerald else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (streak >= 7) "Palier 7 jours atteint ! 🎁" else "Prochain palier : 7 jours 🎁",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
