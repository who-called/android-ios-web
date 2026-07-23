package com.whocalled.android.ui.screen

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whocalled.android.BuildConfig
import com.whocalled.android.data.Preferences
import com.whocalled.android.game.GameDay
import com.whocalled.android.game.TraceEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun col(l: Long) = Color(l.toInt())
private fun col(i: Int) = Color(i)

// ---- Crisp vector marks (no emoji → sharp at every grid size) ---------------
private fun DrawScope.drawCross(color: Color) {
    val pad = size.minDimension * 0.30f
    val sw = size.minDimension * 0.15f
    drawLine(color, Offset(pad, pad), Offset(size.width - pad, size.height - pad), sw, StrokeCap.Round)
    drawLine(color, Offset(size.width - pad, pad), Offset(pad, size.height - pad), sw, StrokeCap.Round)
}

/**
 * Soft synthesized chimes (pure sine, gentle attack + exponential decay) —
 * much smoother than the harsh ToneGenerator beeps, and still asset-free.
 */
private object TraceSound {
    private const val SR = 44_100

    // Good: soft ascending E5 → B5 chime · bad: low descending muffled "womp" ·
    // lose: gentle three-note descent (sympathetic, not dramatic) ·
    // win: sparkling ascending C5 → E5 → G5 arpeggio.
    private val good by lazy { chime(listOf(659.25 to 0.0, 987.77 to 0.06), 0.22) }
    private val bad by lazy { chime(listOf(220.0 to 0.0, 185.0 to 0.09), 0.30) }
    private val lose by lazy { chime(listOf(392.0 to 0.0, 311.1 to 0.18, 261.6 to 0.36), 0.8) }
    private val win by lazy { chime(listOf(523.25 to 0.0, 659.25 to 0.14, 783.99 to 0.28), 0.75) }

    fun play(isGood: Boolean) = playPcm(if (isGood) good else bad)
    fun playLose() = playPcm(lose)
    fun playWin() = playPcm(win)

    private fun playPcm(pcm: ShortArray) {
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) = t.release()
                override fun onPeriodicNotification(t: AudioTrack) = Unit
            })
            track.play()
        }
    }

    private fun chime(notes: List<Pair<Double, Double>>, duration: Double): ShortArray {
        val n = (SR * duration).toInt()
        val mix = FloatArray(n)
        for ((freq, offset) in notes) {
            val start = (SR * offset).toInt()
            val len = n - start
            for (i in 0 until len) {
                val t = i.toDouble() / SR
                // Smooth ~12ms cosine attack, then an exponential fade to silence.
                val attack = if (t < 0.012) 0.5 * (1 - kotlin.math.cos(Math.PI * t / 0.012)) else 1.0
                val decay = kotlin.math.exp(-5.0 * i / len)
                mix[start + i] += (kotlin.math.sin(2 * Math.PI * freq * t) * attack * decay * 0.42).toFloat()
            }
        }
        return ShortArray(n) { (mix[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
    }
}

// ---- Sound + haptic feedback on good/bad placements --------------------------
private fun playFx(view: View, good: Boolean) {
    runCatching {
        view.performHapticFeedback(
            when {
                Build.VERSION.SDK_INT >= 30 && good -> HapticFeedbackConstants.CONFIRM
                Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.REJECT
                else -> HapticFeedbackConstants.LONG_PRESS
            },
        )
    }
    TraceSound.play(good)
}

/**
 * TRACE — daily deduction puzzle. Redesigned to fit on ONE screen (no scroll):
 * a compact status line, a thin sprint bar, the grid sized to the free space, and
 * a hint button. The rules live in the intro tutorial; grid marks are drawn
 * vectors (a crisp cross + a "caught" token) so nothing depends on tiny emoji.
 */
@Composable
fun TraceGameScreen(
    viewModel: com.whocalled.android.ui.MainViewModel,
    ranked: Boolean,
    onBack: () -> Unit,
    onLeaderboard: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val engine = remember { TraceEngine() }
    val rank by viewModel.gameRank.collectAsState()
    val puzzle by viewModel.tracePuzzle.collectAsState()
    val loading by viewModel.traceLoading.collectAsState()
    val error by viewModel.traceError.collectAsState()

    var version by remember { mutableIntStateOf(0) }
    fun bump() { version++ }

    var practiceSize by remember { mutableIntStateOf(6) }
    var autoStartNext by remember { mutableStateOf(false) }

    // Interactive tutorial (auto on very first launch, replayable via ?), rules
    // modal (tap the rule strip) and the ±points feedback toast.
    var showTutorial by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var showQuitConfirm by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Triple<Int, String, Boolean>?>(null) } // (id, text, good)
    var toastSeq by remember { mutableIntStateOf(0) }
    fun showToast(text: String, good: Boolean) {
        val t = Triple(++toastSeq, text, good)
        toast = t
        scope.launch { delay(1000); if (toast == t) toast = null }
    }

    LaunchedEffect(Unit) {
        if (!Preferences.isTraceTutorialSeen(context)) showTutorial = true
        viewModel.resetGameRank()
        if (ranked) viewModel.loadTraceDaily() else viewModel.loadTraceTraining(practiceSize)
    }
    LaunchedEffect(puzzle) {
        val p = puzzle ?: return@LaunchedEffect
        if (p.grids.isEmpty()) return@LaunchedEffect
        engine.setPuzzles(p.grids, ranked)
        if (autoStartNext) { engine.start(); autoStartNext = false }
        bump()
    }
    LaunchedEffect(showTutorial, showRules) {
        while (true) {
            delay(1000)
            // The clock pauses while the tutorial or the rules are open.
            if (engine.active && !showTutorial && !showRules) { engine.tick(); bump() }
        }
    }
    LaunchedEffect(engine.solvedCount) {
        if (engine.solvedCount > 0 && engine.gridWon && !engine.runOver) {
            delay(1400)
            if (engine.gridWon && !engine.runOver) { engine.nextGrid(); bump() }
        }
    }
    LaunchedEffect(engine.runOver) {
        if (engine.runOver) {
            // End-of-run jingle. On a lost run the last life's "womp" just played,
            // so the lose jingle waits a beat instead of overlapping it.
            if (engine.runWon) {
                launch { TraceSound.playWin() }
            } else {
                launch { delay(550); TraceSound.playLose() }
            }
            if (ranked) {
                val waves = maxOf(1, engine.solvedCount)
                val rankedRun = Preferences.recordGame(context, "trace", GameDay.epochDay(), engine.finalScore(), waves)
                viewModel.refreshGameState("trace")
                if (rankedRun) viewModel.submitGameScore("trace", engine.finalScore(), waves)
            }
        }
    }

    // The just-lost heart pops as 💔 for a beat, then settles as 🖤.
    val heartScale = remember { Animatable(1f) }
    var prevLives by remember { mutableIntStateOf(TraceEngine.MAX_LIVES) }
    var brokenHeart by remember { mutableIntStateOf(-1) }
    LaunchedEffect(engine.lives) {
        if (engine.lives < prevLives) {
            prevLives = engine.lives
            brokenHeart = engine.lives
            heartScale.snapTo(2.1f)
            heartScale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
            delay(700)
            brokenHeart = -1
        } else {
            prevLives = engine.lives
        }
    }

    // transient cell effects
    val shake = remember { Animatable(0f) }
    var shakeCell by remember { mutableIntStateOf(-1) }
    val pop = remember { Animatable(1f) }
    var popCell by remember { mutableIntStateOf(-1) }
    val hintPulse = remember { Animatable(1f) }
    var hintCell by remember { mutableIntStateOf(-1) }

    fun onCell(r: Int, c: Int) {
        val before = engine.eventSeq
        engine.cycle(r, c)
        if (engine.eventSeq != before) {
            val cell = engine.lastEventCell
            when (engine.lastEventType) {
                2 -> {
                    shakeCell = cell
                    playFx(view, good = false)
                    showToast("−1 vie · −${kotlin.math.abs(engine.lastEventPoints)} pts", good = false)
                    scope.launch {
                        shake.snapTo(0f)
                        for (v in listOf(-6f, 6f, -5f, 5f, -3f, 3f, 0f)) shake.animateTo(v, tween(42))
                    }
                }
                1 -> {
                    popCell = cell
                    playFx(view, good = true)
                    if (engine.lastEventPoints > 0) showToast("+${engine.lastEventPoints} pts !", good = true)
                    scope.launch {
                        pop.snapTo(1f); pop.animateTo(1.22f, tween(110))
                        pop.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = Spring.StiffnessMediumLow))
                    }
                }
            }
        }
        bump()
    }
    fun onHint() {
        val before = engine.eventSeq
        engine.useHint()
        if (engine.eventSeq != before) {
            hintCell = engine.lastEventCell
            scope.launch { hintPulse.snapTo(0.4f); hintPulse.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        }
        bump()
    }

    // System back: tutorial → skip · rules → close (resume) · active game → confirm quit.
    // Composed earliest so the more-specific handlers below win when several are enabled.
    BackHandler(enabled = engine.started && !engine.runOver && !showTutorial && !showRules && !showQuitConfirm) { showQuitConfirm = true }
    BackHandler(enabled = showRules) { showRules = false }
    BackHandler(enabled = showTutorial) {
        scope.launch { Preferences.setTraceTutorialSeen(context, true) }
        showTutorial = false
    }

    val rev = version // subscribe this composable to bump()

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(col(0xFF0F2540), col(0xFF16324F), col(0xFF1B3A5C)))),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
            // Top bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (engine.started && !engine.runOver) showQuitConfirm = true else onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = col(TraceEngine.INK)) }
                Text("TRACE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (ranked) "Défi du jour" else "Entraînement",
                    color = col(TraceEngine.CY), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(col(TraceEngine.CY).copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.weight(1f))
                // Replay the interactive tutorial at any time.
                IconButton(onClick = { showTutorial = true }) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, "Tutoriel", tint = col(TraceEngine.INK).copy(alpha = 0.85f))
                }
                if (ranked) Text("🏆", fontSize = 18.sp, modifier = Modifier.clickable(onClick = onLeaderboard).padding(6.dp))
            }

            when {
                loading && puzzle == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingBlock() }
                error && puzzle == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ErrorBlock { if (ranked) viewModel.loadTraceDaily() else viewModel.loadTraceTraining(practiceSize) }
                }
                puzzle != null && engine.gridCount > 0 -> {
                    Spacer(Modifier.height(6.dp))
                    // Compact status: villain · lives · score · time (one line)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(engine.villain.first, fontSize = 18.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(engine.villain.second, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Row {
                            repeat(TraceEngine.MAX_LIVES) { i ->
                                Text(
                                    if (i < engine.lives) "❤️" else if (i == brokenHeart) "💔" else "🖤",
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .alpha(if (i < engine.lives || i == brokenHeart) 1f else 0.35f)
                                        .scale(if (i == engine.lives) heartScale.value else 1f),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("${engine.displayScore()}", color = col(TraceEngine.CY), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(String.format("%02d:%02d", engine.elapsed / 60, engine.elapsed % 60), color = col(TraceEngine.INK).copy(alpha = 0.7f), fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(6.dp))
                    // Thin sprint bar
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(engine.gridCount) { i ->
                            Box(
                                Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(
                                    when {
                                        i < engine.solvedCount -> col(TraceEngine.OK)
                                        i == engine.gridIndex && !engine.runOver -> col(TraceEngine.CY)
                                        else -> col(0x1FFFFFFF)
                                    },
                                ),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("${minOf(engine.solvedCount + (if (engine.runOver) 0 else 1), engine.gridCount)}/${engine.gridCount}", color = col(TraceEngine.INK).copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Visual rules kept in view during play — tapping them reopens the rules.
                    Spacer(Modifier.height(8.dp))
                    RuleStrip(Modifier.clickable { showRules = true })

                    // Grid fills the remaining space (square = min(w, h) → no scroll)
                    Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        TraceGrid(engine, rev, shakeCell, shake.value, popCell, pop.value, hintCell, hintPulse.value, ::onCell) { r, c ->
                            // Swipe-paint: drag across a row/column to lay several ✗.
                            if (engine.cells[r][c] == 0) { engine.paintSafe(r, c); bump() }
                        }
                        // Transient "+N pts" / "−1 vie" feedback bubble (~1s).
                        toast?.let { (id, text, good) -> FeedbackBubble(id, text, good, Modifier.align(Alignment.TopCenter).padding(top = 10.dp)) }
                    }

                    // Hint button
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = ::onHint,
                            enabled = engine.hints > 0 && engine.active,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = col(0xFFF4C430), contentColor = col(0xFF3A2E05),
                                disabledContainerColor = col(0x24FFFFFF), disabledContentColor = col(0x66FFFFFF),
                            ),
                        ) {
                            Text("💡 Indice", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.size(20.dp).clip(CircleShape).background(col(0xFF3A2E05)), contentAlignment = Alignment.Center) {
                                Text("${engine.hints}", color = col(0xFFF4C430), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (engine.hints > 0) "Place un fraudeur · −${TraceEngine.HINT_COST} pts" else "Plus d'indices",
                            color = col(TraceEngine.INK).copy(alpha = 0.6f), fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // ----- overlays (one at a time — tutorial first, then rules, then intro;
        // never stacked on each other) -----
        val showIntro = puzzle != null && engine.gridCount > 0 && !engine.started && !engine.runOver
        if (showTutorial) {
            TraceTutorialOverlay(
                villain = engine.villain.first,
                onFx = { good -> playFx(view, good) },
                onDone = {
                    scope.launch { Preferences.setTraceTutorialSeen(context, true) }
                    showTutorial = false
                },
            )
        } else if (showRules) {
            OverlayCard(onBackdropTap = { showRules = false }) {
                Text("LES 3 RÈGLES", color = col(TraceEngine.CY), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                RuleStrip()
                Spacer(Modifier.height(12.dp))
                RuleLine(engine.villain.first)
                Spacer(Modifier.height(8.dp))
                Text(
                    "1 touche = ✗ sûre · 2 touches = fraudeur · 3 touches = effacer\nGlisse le doigt sur la grille pour poser des ✗ en série",
                    color = col(TraceEngine.INK).copy(alpha = 0.65f), fontSize = 11.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = { showRules = false }, colors = ButtonDefaults.buttonColors(containerColor = col(TraceEngine.CY))) {
                    Text("Reprendre ▸", color = col(0xFF06233F), fontWeight = FontWeight.Bold)
                }
            }
        } else if (showIntro) {
            OverlayCard(onBackdropTap = onBack) {
                Text("TRACE", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (ranked) "Défi du jour · 5 grilles · 3 vies" else "Entraînement libre · non classé",
                    color = col(TraceEngine.INK).copy(alpha = 0.8f), fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text("LES 3 RÈGLES", color = col(TraceEngine.CY), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                RuleStrip()
                Spacer(Modifier.height(12.dp))
                RuleLine(engine.villain.first)
                Spacer(Modifier.height(8.dp))
                Text(
                    "❓ Revoir le tuto interactif",
                    color = col(TraceEngine.CY), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable { showTutorial = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                if (!ranked) {
                    Spacer(Modifier.height(14.dp))
                    Text("Taille : ${practiceSize}×${practiceSize}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    androidx.compose.material3.Slider(
                        value = practiceSize.toFloat(),
                        onValueChange = { practiceSize = it.toInt() },
                        onValueChangeFinished = { viewModel.loadTraceTraining(practiceSize) },
                        valueRange = 5f..9f, steps = 3,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = { viewModel.resetGameRank(); engine.start(); bump() }, colors = ButtonDefaults.buttonColors(containerColor = col(TraceEngine.CY))) {
                    Text("Commencer ▸", color = col(0xFF06233F), fontWeight = FontWeight.Bold)
                }
                if (ranked) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onLeaderboard) { Text("🏆 Classement", color = col(TraceEngine.INK)) }
                }
            }
        } else if (engine.started && engine.gridWon && !engine.runOver) {
            Overlay(dim = 0.55f) {
                Text("✓", color = col(TraceEngine.OK), fontSize = 54.sp, fontWeight = FontWeight.Bold)
                Text("Grille ${engine.gridIndex + 1} résolue !", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Score : ${engine.displayScore()}", color = col(TraceEngine.CY), fontWeight = FontWeight.SemiBold)
                if (engine.gridIndex + 1 < engine.gridCount) {
                    Spacer(Modifier.height(6.dp))
                    Text("Grille suivante…", color = col(TraceEngine.INK).copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        } else if (engine.runOver) {
            OverlayCard(onBackdropTap = onBack) {
                if (engine.runWon) {
                    BouncyEmoji("🏆", 48.sp)
                    Text("Sprint terminé !", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("Les ${engine.gridCount} grilles neutralisées 🎉", color = col(TraceEngine.INK).copy(alpha = 0.85f), textAlign = TextAlign.Center)
                } else {
                    // Sympathetic fail — encouraging, not dramatic.
                    BouncyEmoji("😅", 48.sp)
                    Text("Plus de vies…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("Pas grave — ${engine.solvedCount}/${engine.gridCount} grilles résolues !", color = col(TraceEngine.INK).copy(alpha = 0.8f))
                }
                Spacer(Modifier.height(10.dp))
                Text("${engine.finalScore()}", color = col(TraceEngine.CY), fontWeight = FontWeight.Bold, fontSize = 42.sp)
                Text("points", color = col(TraceEngine.INK).copy(alpha = 0.65f), fontSize = 13.sp)
                if (ranked) rank?.let { r ->
                    Spacer(Modifier.height(12.dp))
                    Text("Classement du jour · top ${r.topPercent}%", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${r.players} joueur${if (r.players > 1) "s" else ""} · meilleur ${r.bestScore}", color = col(TraceEngine.INK).copy(alpha = 0.7f), fontSize = 13.sp)
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (ranked) OutlinedButton(onClick = {
                        val text = "who-called TRACE · ${engine.finalScore()} pts · ${engine.solvedCount}/${engine.gridCount} grilles 🛡️\nTraque les spammeurs toi aussi 👉 ${BuildConfig.SITE_URL}"
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Partager"))
                    }) { Text("Partager") }
                    Button(
                        onClick = {
                            if (ranked) { viewModel.resetGameRank(); engine.start(); bump() }
                            else { autoStartNext = true; viewModel.loadTraceTraining(practiceSize) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = col(TraceEngine.CY)),
                    ) { Text(if (ranked) "Rejouer" else "Nouvelle grille", color = col(0xFF06233F), fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (ranked) OutlinedButton(onClick = onLeaderboard) { Text("🏆 Classement") }
                    OutlinedButton(onClick = onBack) { Text("Retour") }
                }
            }
        }

        // Quit confirmation — only shown mid-run (back arrow or system back).
        if (showQuitConfirm) {
            AlertDialog(
                onDismissRequest = { showQuitConfirm = false },
                confirmButton = { TextButton(onClick = onBack) { Text("Quitter") } },
                dismissButton = { TextButton(onClick = { showQuitConfirm = false }) { Text("Annuler") } },
                title = { Text("Quitter la partie ?") },
                text = { Text("Tu perdras ta progression du défi en cours.") },
            )
        }
    }
}

@Composable
private fun TraceGrid(
    engine: TraceEngine,
    rev: Int,
    shakeCell: Int, shake: Float,
    popCell: Int, pop: Float,
    hintCell: Int, hintPulse: Float,
    onCell: (Int, Int) -> Unit,
    onPaint: (Int, Int) -> Unit,
) {
    val n = engine.n
    val errors = remember(rev) { engine.errorCells() }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val gap = 3.dp
        val board = if (maxWidth < maxHeight) maxWidth else maxHeight
        val cell = (board - 16.dp - gap * (n - 1)) / n
        Box(
            Modifier.size(board).clip(RoundedCornerShape(16.dp)).background(col(0xFF0A1C30))
                .border(BorderStroke(1.dp, col(0x14FFFFFF)), RoundedCornerShape(16.dp)).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
                // Drag across a row/column to lay several ✗ in one gesture (empty
                // cells only, so a swipe can never place a fraudster or cost a life).
                modifier = Modifier.pointerInput(n, cell) {
                    val pitch = (cell + gap).toPx()
                    detectDragGestures { change, _ ->
                        val c = (change.position.x / pitch).toInt()
                        val r = (change.position.y / pitch).toInt()
                        if (r in 0 until n && c in 0 until n) onPaint(r, c)
                    }
                },
            ) {
                for (r in 0 until n) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (c in 0 until n) {
                            val key = engine.key(r, c)
                            val v = engine.cells[r][c]
                            val isErr = errors.contains(key)
                            val isRevealed = engine.revealed.contains(key)
                            val showSol = engine.revealSolution && engine.solution[r] == c
                            val base = col(TraceEngine.regionColor(engine.region[r][c], engine.regionCount)).copy(alpha = 0.96f)
                            val ring: Color = when {
                                isErr -> col(0xFFE23C3C); showSol -> col(0xFF22C55E)
                                isRevealed -> col(0xFF2F7FD8); else -> col(0x59FFFFFF)
                            }
                            val ringW = if (isErr || showSol || isRevealed) 3.dp else 1.dp
                            val sc = if (key == popCell) pop else if (key == hintCell) hintPulse else 1f
                            val dx = if (key == shakeCell) shake.dp else 0.dp
                            Box(
                                Modifier.size(cell).offset(x = dx).scale(sc).clip(RoundedCornerShape(7.dp))
                                    .background(base).border(BorderStroke(ringW, ring), RoundedCornerShape(7.dp))
                                    .clickable(enabled = engine.active) { onCell(r, c) },
                                contentAlignment = Alignment.Center,
                            ) {
                                // The placed mark is the grid's villain emoji — the same
                                // character shown above the board.
                                when {
                                    v == 2 -> Text(engine.villain.first, fontSize = (cell.value * 0.5f).sp)
                                    v == 1 -> Canvas(Modifier.size(cell * 0.5f)) { drawCross(Color.White.copy(alpha = 0.9f)) }
                                    showSol -> Text(engine.villain.first, fontSize = (cell.value * 0.5f).sp, modifier = Modifier.alpha(0.4f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Intro tutorial: the 3 rules as visual mini-grids (drawn marks) ----------
@Composable
private fun RuleStrip(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val neu = 0xFF294863L
        val pink = 0xFFFF6B8AL; val teal = 0xFF1FD1C0L; val amber = 0xFFFFB020L
        RuleCard(
            tints = listOf(listOf(pink, pink, teal), listOf(amber, pink, teal), listOf(amber, amber, teal)),
            marks = listOf(listOf(2, 0, 0), listOf(0, 0, 2), listOf(0, 2, 0)),
            label = "1 par couleur",
        )
        RuleCard(
            tints = List(3) { List(3) { neu } },
            marks = listOf(listOf(1, 2, 1), listOf(0, 1, 0), listOf(0, 1, 0)),
            label = "1 / ligne & colonne",
        )
        RuleCard(
            tints = List(3) { List(3) { neu } },
            marks = listOf(listOf(1, 1, 1), listOf(1, 2, 1), listOf(1, 1, 1)),
            label = "Jamais côte à côte",
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RuleCard(tints: List<List<Long>>, marks: List<List<Int>>, label: String) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(col(0x14FFFFFF)).padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0..2) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in 0..2) {
                        Box(Modifier.size(15.dp).clip(RoundedCornerShape(2.dp)).background(col(tints[r][c])), contentAlignment = Alignment.Center) {
                            when (marks[r][c]) {
                                1 -> Canvas(Modifier.size(9.dp)) { drawCross(Color.White.copy(alpha = 0.9f)) }
                                // Emoji fonts carry big ascent/descent padding — removed so the
                                // glyph isn't cropped by the tiny cell and stays optically centred.
                                2 -> Text(
                                    "😈",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        lineHeight = 10.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, color = col(TraceEngine.INK).copy(alpha = 0.85f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun RuleLine(villain: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Touche :", color = col(TraceEngine.INK).copy(alpha = 0.8f), fontSize = 12.sp)
        Box(Modifier.size(16.dp)) { Canvas(Modifier.fillMaxSize()) { drawCross(col(0xFFBFD4EE)) } }
        Text("sûre  ·", color = col(TraceEngine.INK).copy(alpha = 0.8f), fontSize = 12.sp)
        Text(villain, fontSize = 14.sp)
        Text("fraudeur", color = col(TraceEngine.INK).copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

/**
 * Transient "+N pts" / "−1 vie" capsule shown ~1s above the grid — springs in
 * with a bounce and a slight tilt, floats upward, then fades out.
 */
@Composable
private fun FeedbackBubble(key: Int, text: String, good: Boolean, modifier: Modifier = Modifier) {
    val scale = remember(key) { Animatable(0.2f) }
    val rise = remember(key) { Animatable(8f) }
    val alpha = remember(key) { Animatable(0f) }
    val tilt = remember(key) { Animatable(if (good) -8f else 8f) }
    LaunchedEffect(key) {
        launch { alpha.animateTo(1f, tween(120)); delay(650); alpha.animateTo(0f, tween(280)) }
        launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
        launch { tilt.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) }
        launch { rise.animateTo(-18f, tween(1050, easing = FastOutSlowInEasing)) }
    }
    Row(
        modifier
            .offset(y = rise.value.dp)
            .scale(scale.value)
            .rotate(tilt.value)
            .alpha(alpha.value)
            .shadow(8.dp, RoundedCornerShape(999.dp))
            .clip(RoundedCornerShape(999.dp))
            .background(if (good) col(TraceEngine.OK) else col(0xFFE23C3C))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (good) "✓" else "✗", color = Color.White, fontWeight = FontWeight.Black)
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** An emoji that pops in with a playful spring (used in the end-of-run modal). */
@Composable
private fun BouncyEmoji(emoji: String, size: androidx.compose.ui.unit.TextUnit) {
    val scale = remember { Animatable(0.2f) }
    val tilt = remember { Animatable(-18f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow)) }
        launch { tilt.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow)) }
    }
    Text(emoji, fontSize = size, modifier = Modifier.scale(scale.value).rotate(tilt.value))
}

/**
 * Interactive first-play tutorial (4×4 demo that ends as a FULL valid solve).
 * Every step requires a USER action — no auto-X, no silent advance. Wrong taps
 * only get a gentle redirect; they never cost a life. The only mistake is the
 * deliberate teaching one (red-ringed cell, step 6).
 *  0. the 3 rules — « Compris »
 *  1. double-tap the single-cell PINK fraudster (1 per colour)
 *  2. place ✗ on his ROW (3 cells) — teaches WHY we mark
 *  3. place ✗ on his COLUMN (3 cells)
 *  4. place ✗ on everything TOUCHING him (2 cells, diagonals included)
 *  5. deduce: double-tap the only free cell on row 2
 *  6. deliberate mistake on the red-ringed cell → −1 ❤️
 *  7. a 3rd tap erases it
 *  8. finish the solve — 2 remaining fraudsters + epilogue ✗ fade-in
 * Shown once, replayable via ?.
 */
@Composable
private fun TraceTutorialOverlay(
    villain: String,
    onFx: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    // True after the expected action is done: the grid freezes and the player
    // advances THEMSELVES via « Suivant » — no auto-advance, nobody loses track.
    var awaitingNext by remember { mutableStateOf(false) }
    // Demo grid marks (4×4): 0 empty · 1 ✗ · 2 fraudster. Snapshot rows → recompose.
    val demo = remember { List(4) { mutableStateListOf(0, 0, 0, 0) } }
    var lives by remember { mutableIntStateOf(3) }
    var bubble by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val shake = remember { Animatable(0f) }
    val pulse = remember { Animatable(0.35f) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            pulse.animateTo(0.35f, tween(650, easing = FastOutSlowInEasing))
        }
    }
    fun showBubble(text: String, good: Boolean) {
        val b = text to good
        bubble = b
        scope.launch { delay(1000); if (bubble == b) bubble = null }
    }

    // 4×4 demo that ends as a FULL valid solve: (0,1) (1,3) (2,0) (3,2).
    // The user places EVERY mark — no auto-X — guided by spotlight + 🖐 pointer.
    val pink = 0xFFFF6B8AL; val teal = 0xFF1FD1C0L; val amber = 0xFFFFB020L; val violet = 0xFFB98CFFL
    val tints = listOf(
        listOf(amber, pink, teal, teal),
        listOf(amber, amber, teal, teal),
        listOf(amber, violet, violet, violet),
        listOf(violet, violet, violet, violet),
    )
    val easy = 0 to 1; val emerging = 1 to 3; val wrong = 2 to 2
    // Multi-target sets for user-driven X placement (steps 2, 3, 4).
    val rowColLine = setOf(0 to 0, 0 to 2, 0 to 3) // (0,1)'s row
    val rowColCol = setOf(1 to 1, 2 to 1, 3 to 1) // (0,1)'s column
    val adjTaps = setOf(1 to 0, 1 to 2) // touching (0,1), not in row/col
    // Epilogue after the last fraudster: fade-in remaining ✗ (cosmetic closure).
    val remainingX = listOf(2 to 2, 2 to 3, 3 to 0, 3 to 1, 3 to 3)

    // Single target (steps 1,5,6,7,8) or null for multi-tap steps 2-4.
    val singleTarget: Pair<Int, Int>? = when {
        awaitingNext -> null
        step == 1 -> easy
        step == 5 -> emerging
        step == 6 || step == 7 -> wrong
        step == 8 -> when {
            demo[2][0] != 2 -> 2 to 0
            demo[3][2] != 2 -> 3 to 2
            else -> null
        }
        else -> null
    }
    // Multi-target set for X-placement steps (2,3,4): all cells still needing a tap.
    val multiTargets: Set<Pair<Int, Int>> = when {
        awaitingNext -> emptySet()
        step == 2 -> rowColLine.filter { demo[it.first][it.second] == 0 }.toSet()
        step == 3 -> rowColCol.filter { demo[it.first][it.second] == 0 }.toSet()
        step == 4 -> adjTaps.filter { demo[it.first][it.second] == 0 }.toSet()
        else -> emptySet()
    }
    val pointerTarget: Pair<Int, Int>? = singleTarget ?: multiTargets.firstOrNull()
    val spotlight: Set<Pair<Int, Int>> = when {
        awaitingNext -> emptySet()
        singleTarget != null -> setOf(singleTarget!!)
        else -> multiTargets
    }

    val firstTapHint = singleTarget?.let { demo[it.first][it.second] == 1 } == true

    val instruction = if (awaitingNext) when (step) {
        1 -> "Bien. 1 fraudeur par couleur."
        2 -> "Voila. Chaque X elimine une case.\nMoins de cases = plus facile\nde trouver le prochain."
        3 -> "1 fraudeur par ligne, 1 par colonne.\nLes X sont ta memoire : interdit."
        4 -> "Tout ce qui touche un fraudeur\nest sur : on le coche X."
        5 -> "Bien joue. Les X reduisent le champ\njusqu'a ce qu'il ne reste qu'une\ncase possible. C'est tout le principe."
        6 -> "Mal place : il touchait un fraudeur.\n-1 vie, -200 pts."
        7 -> "Efface. Le cycle :\nvide, X, fraudeur, vide."
        else -> "Termine. Chaque couleur, chaque ligne,\nchaque colonne a SON fraudeur.\nA toi de jouer !"
    } else when (step) {
        0 -> "3 regles, c'est tout :\n1 fraudeur par couleur -- 1 par ligne\net colonne -- jamais cote a cote"
        1 -> if (firstTapHint) "Encore une touche pour le placer !"
            else "La zone rose n'a qu'une case :\nle fraudeur est forcement la.\nDouble-touche pour le placer."
        2 -> "Ces cases ne pourront jamais\navoir de fraudeur. Pose un X\nsur les 3 cases eclairees."
        3 -> "1 fraudeur par ligne, 1 par colonne :\nchacun sa place. Pose un X sur\nles 3 cases eclairees."
        4 -> "Jamais cote a cote, meme\nen diagonale. Pose un X sur\nles cases eclairees."
        5 -> if (firstTapHint) "Encore une touche !"
            else "Ligne 2 : une seule case libre.\nLe fraudeur est forcement la.\nDouble-touche pour le placer."
        6 -> if (firstTapHint) "Encore une touche..."
            else "Et si on se trompe ?\nDouble-touche la case encadree\nde rouge."
        7 -> "Une 3eme touche efface.\nRetouche la case."
        8 -> if (demo[2][0] != 2) {
                if (demo[2][0] == 1) "Encore une touche !"
                else "Plus que 2 fraudeurs.\nLigne 3 : une seule case libre.\nDouble-touche-la."
            } else {
                if (demo[3][2] == 1) "Encore une touche !"
                else "La derniere : ligne 4 !\nDouble-touche-la."
            }
        else -> ""
    }
    var hintBubble by remember { mutableStateOf<String?>(null) }

    // Back navigation: restore the demo exactly as it was at the start of `s`.
    fun goTo(s: Int) {
        step = s
        awaitingNext = false
        bubble = null
        hintBubble = null
        for (r in 0..3) for (c in 0..3) demo[r][c] = 0
        lives = 3
        if (s >= 2) demo[0][1] = 2
        if (s >= 3) rowColLine.forEach { (r, c) -> demo[r][c] = 1 }
        if (s >= 4) rowColCol.forEach { (r, c) -> demo[r][c] = 1 }
        if (s >= 5) adjTaps.forEach { (r, c) -> demo[r][c] = 1 }
        if (s >= 6) demo[1][3] = 2
        if (s >= 7) { demo[2][2] = 2; lives = 2 }
        if (s >= 8) { demo[2][2] = 0; lives = 2 }
    }

    fun next() { if (!awaitingNext) return; awaitingNext = false; step++ }

    fun tap(r: Int, c: Int) {
        if (awaitingNext || step == 0) return
        // Multi-target X-placement steps (2,3,4): tap highlighted cell → ✗.
        if (step in 2..4) {
            if (!multiTargets.contains(r to c)) {
                hintBubble = "Touche une case qui brille"
                scope.launch { delay(1200); hintBubble = null }
                return
            }
            demo[r][c] = 1
            if (multiTargets.size <= 1) awaitingNext = true
            return
        }
        // Single-target steps: only the spotlight cell reacts.
        if (singleTarget == null || (r to c) != singleTarget) {
            hintBubble = "Touche la case qui brille"
            scope.launch { delay(1200); hintBubble = null }
            return
        }
        when (step) {
            1, 5, 8 -> when (demo[r][c]) {
                0 -> demo[r][c] = 1
                1 -> {
                    demo[r][c] = 2; onFx(true)
                    showBubble("+${TraceEngine.PLACE_BONUS} pts !", true)
                    if (step == 8 && demo[3][2] == 2) scope.launch {
                        delay(200)
                        for ((sr, sc) in remainingX) { demo[sr][sc] = 1; delay(120) }
                        delay(200); awaitingNext = true
                    } else awaitingNext = true
                }
            }
            6 -> when (demo[r][c]) {
                0 -> demo[r][c] = 1
                1 -> {
                    demo[r][c] = 2; lives = 2; onFx(false)
                    showBubble("−1 vie · −${TraceEngine.ERROR_COST} pts", false)
                    scope.launch {
                        shake.snapTo(0f)
                        for (v in listOf(-6f, 6f, -5f, 5f, -3f, 3f, 0f)) shake.animateTo(v, tween(42))
                    }; awaitingNext = true
                }
            }
            7 -> { demo[r][c] = 0; showBubble("Efface", true); awaitingNext = true }
        }
    }

    OverlayCard(onBackdropTap = onDone) {
        Text("TUTO — $villain Comment jouer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        // Lives mirror the real HUD so the −1 vie step reads instantly.
        Row {
            repeat(3) { i ->
                Text(if (i < lives) "❤️" else "💔", fontSize = 15.sp, modifier = Modifier.alpha(if (i < lives) 1f else 0.75f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            instruction,
            color = col(TraceEngine.INK), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            textAlign = TextAlign.Center, lineHeight = 19.sp,
        )
        Spacer(Modifier.height(14.dp))
        Box(contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).background(col(0xFF0A1C30)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (r in 0..3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (c in 0..3) {
                            val cellPos = r to c
                            val isTarget = spotlight.contains(cellPos)
                            val dimmed = spotlight.isNotEmpty() && !isTarget
                            val isWrongTarget = (step == 5 || step == 6) && isTarget
                            val ring = when {
                                isWrongTarget -> col(0xFFE23C3C)
                                isTarget -> col(0xFFFF2E6B) // « rose vif » = tap here
                                else -> col(0x59FFFFFF)
                            }
                            val dx = if (cellPos == wrong) shake.value.dp else 0.dp
                            Box(contentAlignment = Alignment.Center) {
                                // Bright pulsing halo behind the target cell.
                                if (isTarget) {
                                    Box(
                                        Modifier
                                            .size(56.dp)
                                            .scale(1f + pulse.value * 0.10f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(ring.copy(alpha = 0.25f + pulse.value * 0.35f)),
                                    )
                                }
                                Box(
                                    Modifier.size(46.dp).offset(x = dx)
                                        .scale(if (isTarget) 1f + pulse.value * 0.06f else 1f)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(col(tints[r][c]).copy(alpha = 1f))
                                        .border(
                                            BorderStroke(if (isTarget) 3.dp else 1.dp, ring.copy(alpha = if (isTarget) 0.65f + pulse.value * 0.35f else 1f)),
                                            RoundedCornerShape(7.dp),
                                        )
                                        .clickable { tap(r, c) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when (demo[r][c]) {
                                        1 -> Canvas(Modifier.size(22.dp)) { drawCross(Color.White.copy(alpha = 0.9f)) }
                                        2 -> Text(villain, fontSize = 23.sp)
                                        else -> {}
                                    }
                                    // Spotlight: everything NOT targeted sinks under a strong dim.
                                    if (dimmed) {
                                        Box(Modifier.matchParentSize().clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(alpha = 0.55f)))
                                    }
                                }
                                // Big 👆 tap pointer bouncing on the target cell.
                                if (isTarget) {
                                    Text(
                                        "👆",
                                        fontSize = 26.sp,
                                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 11.dp).scale(1f + pulse.value * 0.12f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            bubble?.let { (text, good) -> FeedbackBubble(text.hashCode(), text, good, Modifier.offset(y = (-10).dp)) }
            hintBubble?.let { FeedbackBubble(it.hashCode(), it, true, Modifier.offset(y = (-10).dp)) }
        }
        Spacer(Modifier.height(12.dp))
        // Step dots — tap a past dot (or ‹) to replay from there.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                color = col(TraceEngine.INK).copy(alpha = if (step > 0) 0.7f else 0.15f),
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = step > 0) { goTo(step - 1) }.padding(horizontal = 4.dp),
            )
            repeat(9) { i ->
                Box(
                    Modifier.size(16.dp).clickable(enabled = i < step) { goTo(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (i <= step) col(TraceEngine.CY) else col(0x33FFFFFF)))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            // Step 0 = the 3 rules, no action — a single « Compris » to start.
            step == 0 -> {
                Button(onClick = { step = 1 }, colors = ButtonDefaults.buttonColors(containerColor = col(TraceEngine.CY))) {
                    Text("Compris ▸", color = col(0xFF06233F), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Passer",
                    color = col(TraceEngine.INK).copy(alpha = 0.6f), fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onDone).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            // Final recap → straight to the game.
            awaitingNext && step >= 8 -> {
                Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = col(TraceEngine.CY))) {
                    Text("C'est parti ▸", color = col(0xFF06233F), fontWeight = FontWeight.Bold)
                }
            }
            // Action done → the player reads the recap and advances THEMSELVES.
            awaitingNext -> {
                Button(onClick = { next() }, colors = ButtonDefaults.buttonColors(containerColor = col(TraceEngine.CY))) {
                    Text("Suivant ▸", color = col(0xFF06233F), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Passer",
                    color = col(TraceEngine.INK).copy(alpha = 0.6f), fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onDone).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            else -> {
                Text(
                    "Passer",
                    color = col(TraceEngine.INK).copy(alpha = 0.6f), fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onDone).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = col(TraceEngine.CY))
        Spacer(Modifier.height(14.dp))
        Text("Préparation du défi…", color = col(TraceEngine.INK).copy(alpha = 0.7f))
    }
}

@Composable
private fun ErrorBlock(onRetry: () -> Unit) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Impossible de charger le défi", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Vérifie ta connexion et réessaie.", color = col(TraceEngine.INK).copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = col(TraceEngine.CY))) {
            Text("Réessayer", color = col(0xFF06233F), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Overlay(dim: Float = 0.92f, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(col(0xFF06233F).copy(alpha = dim)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content, modifier = Modifier.padding(24.dp))
    }
}

/**
 * A distinct floating modal: soft dim behind, a bordered card with a real drop
 * shadow that springs in — reads as a proper modal, not a flat overlay.
 * `onBackdropTap` (when set) fires on a tap in the dim area outside the card,
 * so a stray tap never leaves the player stuck behind the modal.
 */
@Composable
private fun OverlayCard(
    onBackdropTap: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val scale = remember { Animatable(0.86f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
        launch { alpha.animateTo(1f, tween(160)) }
    }
    // No-ripple clickables: the backdrop closes, the card absorbs (no dismiss).
    val backdropSource = remember { MutableInteractionSource() }
    val cardSource = remember { MutableInteractionSource() }
    val backdrop = if (onBackdropTap != null) Modifier.clickable(interactionSource = backdropSource, indication = null) { onBackdropTap() } else Modifier
    val absorb = if (onBackdropTap != null) Modifier.clickable(interactionSource = cardSource, indication = null) { } else Modifier
    Box(
        Modifier.fillMaxSize().then(backdrop).background(Color.Black.copy(alpha = 0.55f * alpha.value)).padding(horizontal = 20.dp, vertical = 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .then(absorb)
                .widthIn(max = 400.dp)
                .scale(scale.value)
                .alpha(alpha.value)
                .shadow(28.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(col(0xFF123A5E), col(0xFF0C2540))))
                .border(BorderStroke(1.dp, col(0x807DD3FC)), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
