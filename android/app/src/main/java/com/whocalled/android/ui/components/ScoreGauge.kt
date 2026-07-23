package com.whocalled.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.whocalled.android.ui.theme.WCColor

/**
 * Signature element: a circular spam-score gauge. The arc fills proportionally
 * and shifts color with severity (emerald → amber → coral), giving the score
 * real visual weight instead of a flat percentage.
 */
@Composable
fun ScoreGauge(
    score: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 96.dp,
    stroke: Dp = 9.dp,
) {
    val animated by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(900),
        label = "score",
    )
    val color = when {
        score >= 85 -> WCColor.Coral
        score >= 60 -> WCColor.Amber
        else -> WCColor.Emerald
    }

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            // Track
            drawArc(
                color = Color.Gray.copy(alpha = 0.15f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx),
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokePx, cap = StrokeCap.Round),
            )
            // Progress
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
        Box(contentAlignment = Alignment.Center) {
            Text(
                "$score",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}
