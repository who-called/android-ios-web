package com.whocalled.android.ui.screen

import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whocalled.android.BuildConfig
import com.whocalled.android.data.Preferences
import com.whocalled.android.game.DefenseEngine
import com.whocalled.android.game.GameDay
import com.whocalled.android.ui.MainViewModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun col(l: Long) = Color(l.toInt())
private fun col(i: Int) = Color(i)

@Composable
fun DefenseGameScreen(viewModel: MainViewModel, onBack: () -> Unit, onLeaderboard: () -> Unit = {}) {
    val context = LocalContext.current
    val engine = remember { DefenseEngine() }
    val rank by viewModel.gameRank.collectAsState()

    // The engine runs in a fixed 390-wide logical space (matching the design); we
    // scale rendering + input so it fills the real (high-density) screen.
    var vscale by remember { mutableFloatStateOf(1f) }
    var tick by remember { mutableLongStateOf(0L) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var waveUi by remember { mutableIntStateOf(1) }
    var shieldUi by remember { mutableFloatStateOf(1f) }
    var chargeUi by remember { mutableFloatStateOf(0f) }
    var itemUi by remember { mutableStateOf<String?>(null) }
    var comboUi by remember { mutableIntStateOf(0) }
    var runningUi by remember { mutableStateOf(false) }
    var overUi by remember { mutableStateOf(false) }
    var showQuitConfirm by remember { mutableStateOf(false) }

    // Game loop.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (last != 0L && engine.running && !engine.over) {
                    val dt = ((t - last) / 1e9f).coerceAtMost(0.05f)
                    engine.update(dt)
                }
                last = t; tick = t
                scoreUi = engine.score; waveUi = engine.wave
                shieldUi = (engine.shield / engine.shieldMax).coerceIn(0f, 1f)
                chargeUi = (engine.charge.toFloat() / engine.chargeFull).coerceIn(0f, 1f)
                itemUi = engine.item
                comboUi = engine.combo; runningUi = engine.running; overUi = engine.over
            }
        }
    }
    // Record + submit once when a run ends.
    LaunchedEffect(overUi) {
        if (overUi) {
            val ranked = Preferences.recordGame(context, "defense", GameDay.epochDay(), engine.score, engine.wave)
            viewModel.refreshGameState("defense")
            if (ranked) viewModel.submitGameScore("defense", engine.score, engine.wave)
        }
    }

    val phonePaint = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER } }
    val textPaint = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) } }

    // System back during an active run asks for confirmation; otherwise it exits.
    BackHandler(enabled = runningUi && !overUi && !showQuitConfirm) { showQuitConfirm = true }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(col(0xFF0A1326), col(0xFF0A1120))),
        ),
    ) {
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged {
                    val sc = if (it.width > 0) it.width / 390f else 1f
                    vscale = sc
                    engine.setSize(390f, it.height / sc)
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull()
                            if (ch != null) { engine.pointer(ch.position.x / vscale); engine.firing = ch.pressed }
                        }
                    }
                },
        ) {
            @Suppress("UNUSED_EXPRESSION") tick // subscribe to frames
            if (engine.w <= 0f) return@Canvas
            scale(vscale, vscale, pivot = Offset.Zero) { drawGame(engine, phonePaint, textPaint) }
        }

        // HUD ----------------------------------------------------------
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (runningUi && !overUi) showQuitConfirm = true else onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour", tint = col(DefenseEngine.INK)) }
            Spacer(Modifier.weight(1f))
            HudChip("SCORE", scoreUi.toString())
            Spacer(Modifier.size(8.dp))
            HudChip("WAVE", waveUi.toString())
            Spacer(Modifier.size(8.dp))
            HudChip("🛡", "${(shieldUi * 100).toInt()}%", if (shieldUi < 0.34f) col(DefenseEngine.SPAM) else col(DefenseEngine.CY))
        }
        if (comboUi >= 3 && runningUi && !overUi) {
            Text(
                "×$comboUi" + if (comboUi >= 8) " 🔥" else "",
                color = col(DefenseEngine.AMBER),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
            )
        }

        // Mario-Kart-style item slot: fills as you destroy the bad calls, then you
        // TAP to activate the item (never automatic). Circular gauge = very visible.
        val ready = itemUi != null
        Column(
            Modifier.align(Alignment.BottomStart).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (ready) {
                Text("OBJET PRÊT — touche", color = col(DefenseEngine.AMBER), fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                Spacer(Modifier.size(4.dp))
            }
            Box(
                Modifier.size(66.dp).clip(CircleShape)
                    .background(if (ready) col(DefenseEngine.AMBER).copy(alpha = 0.20f) else col(0xFF0E1730))
                    .clickable(enabled = ready && runningUi && !overUi) { engine.useItem() },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                    val sw = 6f
                    drawArc(col(0x33FFFFFF), -90f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                    drawArc(
                        if (ready) col(DefenseEngine.AMBER) else col(DefenseEngine.CY),
                        -90f, 360f * chargeUi, false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(sw),
                    )
                }
                Text(
                    if (ready) DefenseEngine.itemGlyph(itemUi!!) else "${(chargeUi * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    color = col(DefenseEngine.INK),
                    style = if (ready) androidx.compose.material3.MaterialTheme.typography.titleLarge else androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
        }

        // Intro overlay (shared landing layout with TRACE: title · desc · Commencer · 🏆).
        if (!runningUi && !overUi) {
            Overlay {
                Text("DEFENSE", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = col(DefenseEngine.INK))
                Spacer(Modifier.height(10.dp))
                Text(
                    "Maintiens appuyé pour tirer, glisse pour viser.\nDétruis les indésirables — ils remplissent ta jauge d'OBJET (à activer toi-même !).\nLes appels verts sont bienveillants ; attrape les 🎁 pour un objet immédiat.",
                    color = col(DefenseEngine.INK).copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
                Spacer(Modifier.height(22.dp))
                Button(onClick = { engine.start() }) { Text("Commencer ▸") }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onLeaderboard) { Text("🏆 Classement") }
            }
        }
        // Game-over overlay.
        if (overUi) {
            Overlay {
                Text("Bouclier tombé", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = col(DefenseEngine.INK))
                Spacer(Modifier.height(6.dp))
                Text("$scoreUi", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = col(DefenseEngine.CY))
                Text("Vague $waveUi · ${engine.blocked} spams bloqués", color = col(DefenseEngine.INK).copy(alpha = 0.7f))
                rank?.let { r ->
                    Spacer(Modifier.height(12.dp))
                    Text("Classement du jour · top ${r.topPercent}%", color = col(DefenseEngine.INK), fontWeight = FontWeight.Bold)
                    Text("${r.players} joueur${if (r.players > 1) "s" else ""} · meilleur ${r.bestScore}", color = col(DefenseEngine.INK).copy(alpha = 0.7f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        val text = "who-called DEFENSE · $scoreUi pts · Vague $waveUi 🛡️\nBloque les spams toi aussi 👉 ${BuildConfig.SITE_URL}"
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Partager"))
                    }) { Text("Partager") }
                    Button(onClick = { engine.start() }) { Text("Rejouer") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onLeaderboard) { Text("🏆 Classement") }
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
                text = { Text("Tu perdras ta progression de la vague en cours.") },
            )
        }
    }
}

@Composable
private fun HudChip(label: String, value: String, valueColor: Color = col(DefenseEngine.INK)) {
    Column(
        Modifier.clip(RoundedCornerShape(11.dp)).background(col(0xFF0E1730)).padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = col(DefenseEngine.INK).copy(alpha = 0.55f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Overlay(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(col(0xFF0A1122).copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

// ---------------- rendering ----------------
private fun shieldPath(cx: Float, cy: Float, size: Float): Path {
    val s = size / 24f
    fun px(x: Float) = cx + (x - 12f) * s
    fun py(y: Float) = cy + (y - 12.25f) * s
    return Path().apply {
        moveTo(px(12f), py(1.5f)); lineTo(px(21f), py(4.6f)); lineTo(px(21f), py(11.5f))
        cubicTo(px(21f), py(16.6f), px(17f), py(21f), px(12f), py(23f))
        cubicTo(px(7f), py(21f), px(3f), py(16.6f), px(3f), py(11.5f))
        lineTo(px(3f), py(4.6f)); close()
    }
}

private fun DrawScope.drawGame(e: DefenseEngine, phonePaint: Paint, textPaint: Paint) {
    val sx = if (e.shake > 0) (Math.random().toFloat() - 0.5f) * e.shake else 0f
    val sy = if (e.shake > 0) (Math.random().toFloat() - 0.5f) * e.shake else 0f
    translate(sx, sy) {
        // starfield
        for (s in e.stars) {
            val a = 0.25f + s.z * 0.5f
            drawRect(if (s.z > 0.7f) col(0xFF9FD0FF).copy(alpha = a) else col(0xFF4A5F8A).copy(alpha = a),
                topLeft = Offset(s.x, s.y), size = Size(1.6f, 1.6f + s.z * 2.4f))
        }
        // ambient tint
        val inten = min(1f, e.combo / 12f * 0.6f + (e.wave - 1) / 10f * 0.5f)
        val pulse = 0.06f + 0.05f * inten * (0.6f + 0.4f * sin(e.elapsed * 3f))
        val amb = when { e.slow > 0 -> col(DefenseEngine.BL); inten > 0.55f -> col(0xFFFF785A); else -> col(DefenseEngine.CY) }
        drawRect(Brush.radialGradient(listOf(amb.copy(alpha = pulse), Color.Transparent),
            center = Offset(e.w / 2, e.h * 0.32f), radius = e.h * 0.8f), size = Size(e.w, e.h))

        // background fake call (cosmetic destabiliser)
        if (e.fakeA > 0) {
            val a = min(0.28f, e.fakeA * 0.28f); val rp = (1 - e.fakeA) * min(e.w, e.h) * 0.9f
            drawCircle(col(DefenseEngine.AMBER).copy(alpha = a), rp, Offset(e.w / 2, e.h * 0.42f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            drawCircle(col(DefenseEngine.AMBER).copy(alpha = a), rp * 0.6f, Offset(e.w / 2, e.h * 0.42f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            drawContext.canvas.nativeCanvas.apply {
                textPaint.color = DefenseEngine.AMBER.toInt(); textPaint.alpha = (a * 255).toInt(); textPaint.textSize = 12f
                drawText("📞  INCOMING…", e.w / 2, e.h * 0.16f, textPaint)
                textPaint.textSize = 15f; drawText(e.fakeDigits, e.w / 2, e.h * 0.16f + 20f, textPaint); textPaint.alpha = 255
            }
        }
        // super-shield brand splash
        if (e.nova > 0) {
            val p = e.nova; val cy = e.h * 0.46f; val size = min(e.w, e.h) * 0.68f * (1.12f - p * 0.14f)
            drawPath(shieldPath(e.w / 2, cy, size), Brush.verticalGradient(listOf(col(DefenseEngine.BL), col(DefenseEngine.CY)),
                startY = cy - size / 2, endY = cy + size / 2), alpha = min(0.55f, p * 0.6f))
            drawContext.canvas.nativeCanvas.apply {
                textPaint.color = 0xFF04122C.toInt(); textPaint.alpha = (min(0.92f, p * 1.1f) * 255).toInt(); textPaint.textSize = size * 0.135f
                drawText("WHO", e.w / 2, cy - size * 0.09f, textPaint); drawText("CALLED", e.w / 2, cy + size * 0.055f, textPaint); textPaint.alpha = 255
            }
        }

        // defense line + hp fill
        val ly = e.lineY(); val hp = (e.shield / e.shieldMax).coerceIn(0f, 1f)
        val lineC = if (e.lineFlash > 0) col(DefenseEngine.SPAM).copy(alpha = 0.6f + 0.4f * e.lineFlash) else col(DefenseEngine.CY).copy(alpha = 0.32f + 0.4f * hp)
        drawLine(lineC, Offset(6f, ly), Offset(e.w - 6f, ly), strokeWidth = 3f)
        drawRect((if (e.lineFlash > 0) col(DefenseEngine.SPAM) else col(DefenseEngine.CY)).copy(alpha = 0.04f + 0.10f * hp),
            topLeft = Offset(6f, ly + 2f), size = Size((e.w - 12f) * hp, e.h - ly - 4f))

        // rings
        for (r in e.rings) {
            val a = 1 - r.age / r.life
            drawCircle(col(r.color).copy(alpha = a * 0.8f), r.r, Offset(r.x, r.y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
        }
        // bullets
        val bc = if (e.rapid > 0) col(0xFFFFE08A) else col(DefenseEngine.SPARK)
        for (b in e.bullets) drawRect(bc, topLeft = Offset(b.x - 1.6f, b.y - 8f), size = Size(3.2f, 12f))
        // reds
        for (en in e.reds) {
            val boss = en.type == "boss"
            val inner = col(0xFFFF9AA4)
            val outer = if (boss) col(0xFF8A1D5A) else if (en.type == "armored") col(0xFFB31F2C) else col(0xFFE0323F)
            drawCircle(Brush.radialGradient(listOf(inner, outer), center = Offset(en.x, en.y), radius = en.r), en.r, Offset(en.x, en.y))
            if (boss) {
                val frac = en.hp.toFloat() / en.maxHp.coerceAtLeast(1)
                val rr = en.r + 6f; val tl = Offset(en.x - rr, en.y - rr); val sz = Size(rr * 2, rr * 2)
                drawArc(col(0x33FFFFFF), -90f, 360f, false, topLeft = tl, size = sz, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                drawArc(col(DefenseEngine.AMBER), -90f, 360f * frac, false, topLeft = tl, size = sz, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }
            drawContext.canvas.nativeCanvas.apply { phonePaint.textSize = en.r * if (boss) 1.05f else 1.25f; drawText(en.glyph, en.x, en.y + en.r * 0.36f, phonePaint) }
        }
        // green benevolent calls (harmless); 🎁 specials get an amber halo
        for (fr in e.friends) {
            drawCircle(Brush.radialGradient(listOf(col(0xFF84F2C6), col(0xFF1FBD83)), center = Offset(fr.x, fr.y), radius = fr.r), fr.r, Offset(fr.x, fr.y))
            if (fr.special) {
                drawCircle(col(DefenseEngine.AMBER), fr.r + 5f, Offset(fr.x, fr.y), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }
            drawContext.canvas.nativeCanvas.apply { phonePaint.textSize = fr.r * 1.15f; drawText(fr.glyph, fr.x, fr.y + fr.r * 0.4f, phonePaint) }
        }
        // ship + muzzle flash (while holding fire)
        if (e.running && !e.over) {
            if (e.muzzle > 0f) {
                drawCircle(col(0xFFFFE08A).copy(alpha = (e.muzzle / 0.07f).coerceIn(0f, 1f) * 0.85f), 8f, Offset(e.shipX, e.shipY - 20f))
            }
            drawPath(shieldPath(e.shipX, e.shipY, e.shipW),
                Brush.verticalGradient(listOf(col(DefenseEngine.BL), if (e.rapid > 0) col(0xFFFFE08A) else col(DefenseEngine.CY)),
                    startY = e.shipY - e.shipW / 2, endY = e.shipY + e.shipW / 2))
        }
        // particles
        for (pt in e.parts) { val a = 1 - pt.age / pt.life; drawCircle(col(pt.color).copy(alpha = a), 3f, Offset(pt.x, pt.y)) }
        // score pops
        for (po in e.pops) {
            val a = 1 - po.age / po.life
            drawContext.canvas.nativeCanvas.apply { textPaint.color = po.color.toInt(); textPaint.alpha = (a * 255).toInt()
                textPaint.textSize = if (po.text.first() == 'C' || po.text.first() == 'S') 20f else 13f
                drawText(po.text, po.x, po.y, textPaint); textPaint.alpha = 255 }
        }
    }
    // low-shield vignette (screen space)
    val hp = (e.shield / e.shieldMax).coerceIn(0f, 1f)
    if (hp < 0.34f) {
        val v = (0.34f - hp) / 0.34f * (0.5f + 0.5f * sin(e.elapsed * 6f))
        drawRect(Brush.radialGradient(listOf(Color.Transparent, col(0xFFFF3C50).copy(alpha = 0.28f * v)),
            center = Offset(e.w / 2, e.h / 2), radius = e.h * 0.75f), size = Size(e.w, e.h))
    }
    if (e.flash > 0) drawRect(Color.White.copy(alpha = e.flash * 0.22f), size = Size(e.w, e.h))
}
