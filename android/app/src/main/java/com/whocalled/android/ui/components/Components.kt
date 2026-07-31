package com.whocalled.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whocalled.android.ui.theme.WCColor
import kotlinx.coroutines.launch

/** Card with a subtle hairline border and no shadow — clean, not heavy. */
@Composable
fun BorderedCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(1.dp, accent?.copy(alpha = 0.35f) ?: MaterialTheme.colorScheme.outline),
                RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
        content = content,
    )
}

enum class BannerKind { Error, Success, Info }

/**
 * Feedback banner — a bordered, lightly tinted row with an icon. No gray fill,
 * no shadow: structure comes from a hairline border and a colored left rail.
 */
@Composable
fun FeedbackBanner(kind: BannerKind, message: String, modifier: Modifier = Modifier) {
    val (tint, icon: ImageVector) = when (kind) {
        BannerKind.Error -> WCColor.Coral to Icons.Rounded.ErrorOutline
        BannerKind.Success -> WCColor.Emerald to Icons.Rounded.CheckCircle
        BannerKind.Info -> WCColor.Indigo to Icons.Rounded.Info
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.06f))
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.30f)), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AnimatedBanner(kind: BannerKind, message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        FeedbackBanner(kind, message.orEmpty(), modifier)
    }
}

/** Status pill (Bloqué / Alerté / Sûr) — tinted, bordered, no shadow. */
@Composable
fun StatusBadge(label: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Icon-only variant of [StatusBadge] — same tinted disc, no label. Used in dense
 * lists (the calls history) where a worded pill ("Non évalué", "Indésirable")
 * would eat the row's width and push the number onto a second line. The label
 * still ships as the content description, so the status stays available to
 * TalkBack and reads out exactly like the full badge.
 */
@Composable
fun StatusDot(label: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(17.dp))
    }
}

/** Vivid blue → navy gradient header band; modest rounding at the bottom. */
@Composable
fun GradientHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(WCColor.BlueDark, WCColor.Blue),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                }
            }
            if (trailing != null) trailing()
        }
    }
}

/**
 * Friendly empty-state placeholder. Rather than a bare icon, the glyph sits in a
 * large soft tinted disc (with a hairline ring) so an empty list reads as
 * intentional and calm — never broken or blank. Used wherever a list can
 * legitimately be empty (no calls, no reports, no trends yet). An optional
 * [action] slot lets the screen offer the obvious next step (e.g. "Mettre à jour").
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = WCColor.Indigo,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.08f))
                .border(BorderStroke(1.dp, accent.copy(alpha = 0.25f)), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent.copy(alpha = 0.85f),
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        if (action != null) {
            Box(Modifier.padding(top = 4.dp)) { action() }
        }
    }
}

/**
 * Brand lockup: shield mark in a blue disc + the "Who Called" wordmark. Used on
 * loading/branded screens so the identity is the name, not just a shield glyph.
 */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    horizontal: Boolean = true,
    onDark: Boolean = false,
) {
    val markBg = if (onDark) Color.White.copy(alpha = 0.16f) else WCColor.Blue
    val markTint = Color.White
    val textColor = if (onDark) Color.White else WCColor.NightBlue
    val mark = @Composable {
        Box(
            Modifier
                .size(if (horizontal) 44.dp else 64.dp)
                .clip(RoundedCornerShape(if (horizontal) 12.dp else 18.dp))
                .background(markBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = markTint,
                modifier = Modifier.size(if (horizontal) 26.dp else 38.dp),
            )
        }
    }
    val wordmark = @Composable {
        Text(
            "Who Called",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
    if (horizontal) {
        Row(
            modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) { mark(); wordmark() }
    } else {
        Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { mark(); wordmark() }
    }
}

/** Full-screen branded loader: the Who Called lockup over a spinner. */
@Composable
fun BrandedLoader(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.weight(1f))
        BrandLogo(horizontal = false)
        androidx.compose.material3.CircularProgressIndicator(
            color = WCColor.Blue,
            strokeWidth = 3.dp,
        )
        Box(Modifier.weight(1f))
    }
}

/**
 * A screen whose [header] is pinned at the top — exactly as it looks at rest,
 * with no shrinking or transformation — while only the list below it scrolls.
 * A floating "remonter" button appears once the user scrolls down and animates
 * the list back to the top on tap (the requested UX hint).
 */
@Composable
fun ScrollableScreen(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Fixed header — outside the scroll, always shown as-is.
            header()
            // Only this list scrolls, underneath the pinned header.
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }

        // Floating scroll-to-top button (the UX hint to get back up).
        AnimatedVisibility(
            visible = scrolled,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                containerColor = WCColor.Indigo,
                contentColor = Color.White,
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Remonter en haut")
            }
        }
    }
}
