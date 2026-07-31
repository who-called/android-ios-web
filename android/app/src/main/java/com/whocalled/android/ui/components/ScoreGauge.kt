package com.whocalled.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whocalled.android.ui.theme.WCColor

/**
 * Risk gauge: the arc follows the internal score, but the center shows a
 * human label (Faible / Modéré / Élevé) so a low score never reads as "3 spam".
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

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            drawArc(
                color = Color.Gray.copy(alpha = 0.15f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx),
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokePx, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color)),
                startAngle = 135f,
                sweepAngle = 270f * animated,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx),
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokePx, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                riskLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
                fontSize = if (riskLabel.length > 3) 12.sp else 16.sp,
                lineHeight = 14.sp,
            )
            Text(
                "Risque",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
            )
        }
    }
}
