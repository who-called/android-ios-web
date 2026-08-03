package com.whocalled.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whocalled.android.ui.theme.WCColor

/**
 * Risk gauge: the arc follows the internal score; the center shows a human
 * label (Faible / Modéré / Élevé). The label is laid out inside the arc hole
 * and scaled from [diameter] so the compact detail variant never overflows.
 */
@Composable
fun ScoreGauge(
    score: Int,
    status: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 96.dp,
    stroke: Dp = 9.dp,
) {
    val animated by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(900),
        label = "score",
    )
    val color = when (status) {
        "block" -> WCColor.Coral
        "warn" -> WCColor.Amber
        "allow" -> WCColor.Emerald
        else -> WCColor.Blue
    }
    val riskLabel = when (status) {
        "block" -> "Élevé"
        "warn" -> "Modéré"
        "allow" -> "Faible"
        else -> "—"
    }

    // Keep text inside the hole: diameter − stroke on each side − a little air.
    val holePad = stroke + 3.dp
    val hole = (diameter - holePad * 2).coerceAtLeast(18.dp)
    val (labelSize, captionSize, showCaption) = gaugeType(diameter, riskLabel)

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - strokePx,
                size.height - strokePx,
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = Color.Gray.copy(alpha = 0.15f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokePx, cap = StrokeCap.Round),
            )
            // Solid color (not sweepGradient): a sweep brush is absolute to the
            // canvas and looks wrong at low/mid fill angles.
            if (animated > 0f) {
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            Modifier.size(hole),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                riskLabel,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
                fontSize = labelSize,
                lineHeight = labelSize * 1.05f,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            if (showCaption) {
                Text(
                    "Risque",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = captionSize,
                    lineHeight = captionSize,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Pick type that fits the hole for the longest label ("Modéré"). */
private fun gaugeType(diameter: Dp, label: String): Triple<TextUnit, TextUnit, Boolean> {
    val long = label.length >= 5
    return when {
        diameter < 56.dp -> Triple(7.sp, 6.sp, false)
        diameter < 64.dp -> Triple(if (long) 8.sp else 9.sp, 7.sp, true)
        diameter < 72.dp -> Triple(if (long) 9.sp else 10.sp, 7.sp, true)
        diameter < 88.dp -> Triple(if (long) 11.sp else 12.sp, 8.sp, true)
        else -> Triple(14.sp, 9.sp, true)
    }
}
