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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import java.text.NumberFormat

private enum class SetupStep {
    SCREENING,
    NOTIFICATIONS,
}

/**
 * First-launch shield activation tunnel.
 *
 * Steps are always sequential (1 → 2). Already-granted permissions are not
 * skipped silently: the step still appears with a "Continuer" CTA. Fresh grants
 * via the system dialog auto-advance shortly after.
 */
@Composable
fun ProtectionSetupScreen(
    screeningGranted: Boolean,
    notificationsGranted: Boolean,
    coveredNumbers: Long?,
    onRequestScreening: () -> Unit,
    onRequestNotifications: () -> Unit,
    onFinished: () -> Unit,
) {
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
        }
    }

    fun isGranted(step: SetupStep): Boolean = when (step) {
        SetupStep.SCREENING -> screeningGranted
        SetupStep.NOTIFICATIONS -> notificationsGranted || !needsNotifications
    }

    fun bravoFor(step: SetupStep): String = when (step) {
        SetupStep.SCREENING -> coveredNumbers?.takeIf { it > 0 }?.let {
            "✓ Filtre activé · ${NumberFormat.getInstance().format(it)} numéros prêts"
        } ?: "✓ Filtre d’appels activé"
        SetupStep.NOTIFICATIONS -> "✓ Alertes activées"
    }

    // Always start at step 0 so notifications is never jumped over.
    var stepIndex by remember { mutableIntStateOf(0) }
    var justUnlocked by remember { mutableStateOf<String?>(null) }
    var completing by remember { mutableStateOf(false) }
    // Snapshot of grant state when entering a step — distinguishes "already OK"
    // (show Continuer) from "just granted via system dialog" (auto-advance).
    // Updated synchronously in goNext to avoid a frame where a pre-granted
    // notifications step would auto-skip.
    var grantedOnEnter by remember { mutableStateOf(isGranted(steps[0])) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (steps.all { isGranted(it) }) onFinished()
    }

    fun goNext(banner: String?) {
        if (banner != null) justUnlocked = banner
        val next = stepIndex + 1
        if (next >= steps.size) {
            completing = true
            scope.launch {
                delay(900)
                onFinished()
            }
        } else {
            grantedOnEnter = isGranted(steps[next])
            stepIndex = next
        }
    }

    // Fresh grant on the current step → brief confirmation, then next step.
    LaunchedEffect(screeningGranted, notificationsGranted, stepIndex) {
        if (completing) return@LaunchedEffect
        val current = steps.getOrNull(stepIndex) ?: return@LaunchedEffect
        if (!isGranted(current)) return@LaunchedEffect
        if (grantedOnEnter) return@LaunchedEffect // already OK on entry — wait for Continuer
        val banner = bravoFor(current)
        justUnlocked = banner
        delay(500)
        goNext(banner)
    }

    val current = steps.getOrElse(stepIndex) { SetupStep.SCREENING }
    val currentGranted = isGranted(current)
    val displayStep = (stepIndex + 1).coerceAtMost(steps.size)
    val stepFraction = displayStep / steps.size.toFloat()
    val progress by animateFloatAsState(
        targetValue = if (completing) 1f else stepFraction,
        label = "shield-progress",
    )
    val copy = stepCopy(current)

    fun skipCurrent() {
        justUnlocked = null
        goNext(null)
    }

    fun onPrimary() {
        if (currentGranted) {
            goNext(bravoFor(current))
            return
        }
        when (current) {
            SetupStep.SCREENING -> onRequestScreening()
            SetupStep.NOTIFICATIONS -> onRequestNotifications()
        }
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
                        AdaptiveStepBody(
                            step = step,
                            alreadyGranted = isGranted(step),
                        )
                    }
                }
            }

            if (!completing) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, top = 4.dp),
                ) {
                    Button(
                        onClick = ::onPrimary,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentGranted) WCColor.Emerald else WCColor.Blue,
                            contentColor = WCColor.Cloud,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            if (currentGranted) "Continuer" else copy.cta,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
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

@Composable
private fun AdaptiveStepBody(
    step: SetupStep,
    alreadyGranted: Boolean,
) {
    val copy = stepCopy(step)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 360.dp
        val iconSize = if (compact) 64.dp else 88.dp
        val glyphSize = if (compact) 32.dp else 44.dp
        val titleSize = if (compact) 20.sp else 24.sp
        val bodySize = if (compact) 14.sp else 16.sp

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
                    .background(
                        (if (alreadyGranted) WCColor.Emerald else copy.accent).copy(alpha = 0.12f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (alreadyGranted) Icons.Rounded.CheckCircle else copy.icon,
                    contentDescription = null,
                    tint = if (alreadyGranted) WCColor.Emerald else copy.accent,
                    modifier = Modifier.size(glyphSize),
                )
            }
            Spacer(Modifier.height(if (compact) 14.dp else 22.dp))
            Text(
                if (alreadyGranted) {
                    when (step) {
                        SetupStep.SCREENING -> "Protection déjà activée"
                        SetupStep.NOTIFICATIONS -> "Alertes déjà activées"
                                        }
                } else {
                    copy.title
                },
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                color = WCColor.Ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (alreadyGranted) {
                    "Cette autorisation est déjà en place. Continuez pour l’étape suivante."
                } else {
                    copy.body
                },
                fontSize = bodySize,
                color = WCColor.Muted,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
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
}
