package com.whocalled.android.ui.screen

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneLocked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whocalled.android.ui.theme.WCColor
import kotlinx.coroutines.delay
import java.text.NumberFormat

private enum class SetupStep {
    SCREENING,
    NOTIFICATIONS,
    CALL_LOG,
    DONE,
}

/**
 * First-launch shield activation tunnel. Skip is always allowed — the app stays
 * usable without any grant; incomplete steps surface later as a Home nudge.
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
        SetupStep.DONE -> true
    }

    var stepIndex by remember {
        mutableIntStateOf(steps.indexOfFirst { !isGranted(it) }.let { if (it < 0) steps.lastIndex else it })
    }
    var celebrating by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val grantedCount = steps.count { isGranted(it) }
    val progress by animateFloatAsState(
        targetValue = grantedCount / steps.size.toFloat(),
        label = "shield-progress",
    )

    val current = steps.getOrElse(stepIndex) { SetupStep.DONE }

    // Auto-advance when the user grants the current step via the system UI.
    LaunchedEffect(screeningGranted, notificationsGranted, callLogGranted, stepIndex, celebrating) {
        if (finished || celebrating || current == SetupStep.DONE) return@LaunchedEffect
        if (!isGranted(current)) return@LaunchedEffect
        celebrating = true
        delay(650)
        celebrating = false
        val next = (stepIndex + 1 until steps.size).firstOrNull { !isGranted(steps[it]) }
        if (next == null) {
            finished = true
            delay(700)
            onFinished()
        } else {
            stepIndex = next
        }
    }

    fun skipCurrent() {
        val next = stepIndex + 1
        if (next >= steps.size) onFinished() else stepIndex = next
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Bouclier $grantedCount/${steps.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (grantedCount == steps.size) WCColor.Emerald else WCColor.Amber,
            )
            TextButton(onClick = onFinished) {
                Text("Passer")
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = WCColor.Emerald,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        )

        Spacer(Modifier.height(28.dp))

        if (finished || (grantedCount == steps.size && celebrating)) {
            CompletionPane(coveredNumbers = coveredNumbers)
        } else {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                },
                label = "setup-step",
                modifier = Modifier.weight(1f),
            ) { step ->
                StepPane(
                    step = step,
                    celebrating = celebrating && isGranted(step),
                    coveredNumbers = coveredNumbers,
                    onActivate = {
                        when (step) {
                            SetupStep.SCREENING -> onRequestScreening()
                            SetupStep.NOTIFICATIONS -> onRequestNotifications()
                            SetupStep.CALL_LOG -> onRequestCallLog()
                            SetupStep.DONE -> onFinished()
                        }
                    },
                    onSkip = ::skipCurrent,
                )
            }
        }

        if (!finished) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Réglages modifiables à tout moment. Sans autorisation, jeux et recherche restent disponibles.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun CompletionPane(coveredNumbers: Long?) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = WCColor.Emerald,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Vous êtes protégé", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            coveredNumbers?.takeIf { it > 0 }?.let {
                "${NumberFormat.getInstance().format(it)} numéros prêts à filtrer"
            } ?: "Votre bouclier Who Called est prêt",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
        )
    }
}

@Composable
private fun StepPane(
    step: SetupStep,
    celebrating: Boolean,
    coveredNumbers: Long?,
    onActivate: () -> Unit,
    onSkip: () -> Unit,
) {
    val copy = stepCopy(step, coveredNumbers)
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.15f))
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    if (celebrating) WCColor.Emerald.copy(alpha = 0.15f)
                    else copy.accent.copy(alpha = 0.12f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (celebrating) Icons.Rounded.CheckCircle else copy.icon,
                contentDescription = null,
                tint = if (celebrating) WCColor.Emerald else copy.accent,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            if (celebrating) "C’est activé" else copy.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            if (celebrating) copy.successBody else copy.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp),
        )
        Spacer(Modifier.weight(0.35f))
        if (!celebrating) {
            Button(
                onClick = onActivate,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(copy.cta, fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(if (step == SetupStep.SCREENING) "Continuer sans bloquer" else "Plus tard")
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private data class StepCopy(
    val icon: ImageVector,
    val accent: androidx.compose.ui.graphics.Color,
    val title: String,
    val body: String,
    val successBody: String,
    val cta: String,
)

private fun stepCopy(step: SetupStep, coveredNumbers: Long?): StepCopy = when (step) {
    SetupStep.SCREENING -> StepCopy(
        icon = Icons.Rounded.PhoneLocked,
        accent = WCColor.Coral,
        title = "Activez votre bouclier",
        body = "Who Called ne peut rien bloquer tant que le filtre d’appels système n’est pas activé. " +
            "3 étapes · environ 20 secondes · révocable à tout moment.",
        successBody = coveredNumbers?.takeIf { it > 0 }?.let {
            "${NumberFormat.getInstance().format(it)} numéros prêts à filtrer sur votre téléphone."
        } ?: "Le filtre d’appels est prêt. Encore deux étapes pour une protection complète.",
        cta = "Activer la protection",
    )
    SetupStep.NOTIFICATIONS -> StepCopy(
        icon = Icons.Rounded.NotificationsActive,
        accent = WCColor.Amber,
        title = "Restez informé",
        body = "Sans notifications, la protection reste silencieuse : pas d’alerte sur un appel suspect, " +
            "ni de confirmation quand un numéro est bloqué.",
        successBody = "Vous serez prévenu des appels suspects et des numéros bloqués.",
        cta = "Activer les alertes",
    )
    SetupStep.CALL_LOG -> StepCopy(
        icon = Icons.Rounded.History,
        accent = WCColor.Blue,
        title = "Voir qui a appelé",
        body = "Pour afficher vos appels récents et signaler un numéro en un geste. " +
            "L’historique reste sur votre téléphone — rien n’est envoyé.",
        successBody = "Votre journal d’appels est prêt dans l’onglet Appels.",
        cta = "Afficher mon historique",
    )
    SetupStep.DONE -> StepCopy(
        icon = Icons.Outlined.Shield,
        accent = WCColor.Emerald,
        title = "Vous êtes protégé",
        body = "",
        successBody = "",
        cta = "Commencer",
    )
}
