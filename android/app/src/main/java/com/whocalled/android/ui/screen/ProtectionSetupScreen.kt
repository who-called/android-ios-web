package com.whocalled.android.ui.screen

import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneLocked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.whocalled.android.ui.theme.WCColor
import kotlinx.coroutines.delay
import java.text.NumberFormat

private enum class SetupStep {
    SCREENING,
    NOTIFICATIONS,
    CALL_LOG,
}

/**
 * First-launch shield activation tunnel.
 *
 * Fixed (non-scroll) adaptive layout:
 * - [Scaffold] + [WindowInsets.safeDrawing]
 * - top chrome + bottom CTAs take their intrinsic height
 * - middle fills the remainder with [Modifier.weight] and scales down on short screens
 *
 * Every visible step asks for a real system permission/role. Skip is allowed.
 */
@Composable
fun ProtectionSetupScreen(
    screeningGranted: Boolean,
    notificationsGranted: Boolean,
    callLogGranted: Boolean,
    coveredNumbers: Long?,
    onRequestScreening: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestCallLog: () -> Unit,
    onFinished: () -> Unit,
) {
    // Forced light surface → dark status/nav icons while this screen is up.
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    DisposableEffect(darkTheme) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val needsNotifications = Build.VERSION.SDK_INT >= 33
    val steps = remember(needsNotifications) {
        buildList {
            add(SetupStep.SCREENING)
            if (needsNotifications) add(SetupStep.NOTIFICATIONS)
            add(SetupStep.CALL_LOG)
        }
    }

    fun isGranted(step: SetupStep): Boolean = when (step) {
        SetupStep.SCREENING -> screeningGranted
        SetupStep.NOTIFICATIONS -> notificationsGranted || !needsNotifications
        SetupStep.CALL_LOG -> callLogGranted
    }

    fun firstPending(): Int = steps.indexOfFirst { !isGranted(it) }

    var stepIndex by remember {
        mutableIntStateOf(firstPending().let { if (it < 0) 0 else it })
    }
    var justUnlocked by remember { mutableStateOf<String?>(null) }
    var completing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (firstPending() < 0) onFinished()
    }

    // On grant → next permission ask immediately (bravo as a banner, never a dead step).
    LaunchedEffect(screeningGranted, notificationsGranted, callLogGranted, stepIndex) {
        if (completing) return@LaunchedEffect
        val current = steps.getOrNull(stepIndex) ?: return@LaunchedEffect
        if (!isGranted(current)) return@LaunchedEffect

        val bravo = when (current) {
            SetupStep.SCREENING -> coveredNumbers?.takeIf { it > 0 }?.let {
                "✓ Filtre activé · ${NumberFormat.getInstance().format(it)} numéros prêts"
            } ?: "✓ Filtre d’appels activé"
            SetupStep.NOTIFICATIONS -> "✓ Alertes activées"
            SetupStep.CALL_LOG -> "✓ Historique autorisé"
        }

        val next = (stepIndex + 1 until steps.size).firstOrNull { !isGranted(steps[it]) }
        if (next == null) {
            completing = true
            justUnlocked = bravo
            delay(900)
            onFinished()
        } else {
            justUnlocked = bravo
            stepIndex = next
        }
    }

    val current = steps.getOrElse(stepIndex) { SetupStep.SCREENING }
    val displayStep = (stepIndex + 1).coerceAtMost(steps.size)
    val stepFraction = displayStep / steps.size.toFloat()
    val progress by animateFloatAsState(
        targetValue = if (completing) 1f else stepFraction,
        label = "shield-progress",
    )
    val copy = stepCopy(current)

    fun skipCurrent() {
        justUnlocked = null
        val next = stepIndex + 1
        if (next >= steps.size) onFinished() else stepIndex = next
    }

    Scaffold(
        containerColor = WCColor.Cloud,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
        ) {
            // —— Top chrome (intrinsic height) ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Étape $displayStep/${steps.size}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WCColor.Amber,
                )
                TextButton(
                    onClick = onFinished,
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Text("Passer", color = WCColor.Muted, fontWeight = FontWeight.Medium)
                }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = WCColor.Emerald,
                trackColor = WCColor.Border,
            )
            justUnlocked?.let { msg ->
                Text(
                    msg,
                    color = WCColor.Emerald,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            // —— Middle: fills leftover space, never scrolls, never covers chrome ——
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (completing) {
                    CompletionPane(coveredNumbers = coveredNumbers)
                } else {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 4 } + fadeOut())
                        },
                        label = "setup-step",
                        modifier = Modifier.fillMaxSize(),
                    ) { step ->
                        AdaptiveStepBody(step = step)
                    }
                }
            }

            // —— Bottom CTAs (intrinsic height, always above nav inset) ——
            if (!completing) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, top = 4.dp),
                ) {
                    Button(
                        onClick = {
                            when (current) {
                                SetupStep.SCREENING -> onRequestScreening()
                                SetupStep.NOTIFICATIONS -> onRequestNotifications()
                                SetupStep.CALL_LOG -> onRequestCallLog()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WCColor.Blue,
                            contentColor = WCColor.Cloud,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(copy.cta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    TextButton(
                        onClick = ::skipCurrent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (current == SetupStep.SCREENING) "Continuer sans bloquer" else "Plus tard",
                            color = WCColor.Muted,
                        )
                    }
                    Text(
                        "Modifiable dans Réglages. Sans autorisation, jeux et recherche restent disponibles.",
                        fontSize = 12.sp,
                        color = WCColor.Muted,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionPane(coveredNumbers: Long?) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = WCColor.Emerald,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Vous êtes protégé",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = WCColor.Ink,
            textAlign = TextAlign.Center,
        )
        Text(
            coveredNumbers?.takeIf { it > 0 }?.let {
                "${NumberFormat.getInstance().format(it)} numéros prêts à filtrer"
            } ?: "Votre bouclier Who Called est prêt",
            fontSize = 14.sp,
            color = WCColor.Muted,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
        )
    }
}

/**
 * Middle pane sized to the available height: smaller glyph/type on short
 * devices, more air on tall ones — no scrolling.
 */
@Composable
private fun AdaptiveStepBody(step: SetupStep) {
    val copy = stepCopy(step)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 360.dp
        val iconSize = if (compact) 64.dp else 88.dp
        val glyphSize = if (compact) 32.dp else 44.dp
        val titleSize = if (compact) 20.sp else 24.sp
        val bodySize = if (compact) 14.sp else 16.sp
        val bodyLines = if (compact) 22.sp else 22.sp

        Column(
            Modifier
                .fillMaxSize()
                .padding(vertical = if (compact) 8.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f, fill = true))
            Box(
                Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(copy.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    copy.icon,
                    contentDescription = null,
                    tint = copy.accent,
                    modifier = Modifier.size(glyphSize),
                )
            }
            Spacer(Modifier.height(if (compact) 14.dp else 22.dp))
            Text(
                copy.title,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                color = WCColor.Ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                copy.body,
                fontSize = bodySize,
                color = WCColor.Muted,
                textAlign = TextAlign.Center,
                lineHeight = bodyLines,
                maxLines = if (compact) 5 else 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (compact) 8.dp else 12.dp, start = 4.dp, end = 4.dp),
            )
            Spacer(Modifier.weight(1f, fill = true))
        }
    }
}

private data class StepCopy(
    val icon: ImageVector,
    val accent: androidx.compose.ui.graphics.Color,
    val title: String,
    val body: String,
    val cta: String,
)

private fun stepCopy(step: SetupStep): StepCopy = when (step) {
    SetupStep.SCREENING -> StepCopy(
        icon = Icons.Rounded.PhoneLocked,
        accent = WCColor.Coral,
        title = "Activez votre bouclier",
        body = "Who Called ne peut rien bloquer tant que le filtre d’appels système n’est pas activé. " +
            "Quelques secondes · révocable à tout moment.",
        cta = "Activer la protection",
    )
    SetupStep.NOTIFICATIONS -> StepCopy(
        icon = Icons.Rounded.NotificationsActive,
        accent = WCColor.Amber,
        title = "Restez informé",
        body = "Sans notifications, la protection reste silencieuse : pas d’alerte sur un appel suspect, " +
            "ni de confirmation quand un numéro est bloqué.",
        cta = "Activer les alertes",
    )
    SetupStep.CALL_LOG -> StepCopy(
        icon = Icons.Rounded.History,
        accent = WCColor.Blue,
        title = "Voir qui a appelé",
        body = "Pour afficher vos appels récents et signaler un numéro en un geste. " +
            "L’historique reste sur votre téléphone — rien n’est envoyé.",
        cta = "Afficher mon historique",
    )
}
