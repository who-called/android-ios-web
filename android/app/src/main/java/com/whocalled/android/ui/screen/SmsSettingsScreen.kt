package com.whocalled.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.components.GradientHeader
import com.whocalled.android.ui.components.ScrollableScreen
import com.whocalled.android.ui.theme.WCColor
import com.whocalled.android.util.NotificationAccess

/**
 * SMS shield settings. Honest about the Android limit: we can't truly block an
 * SMS without being the default SMS app, so we hide the *notification* of an
 * unwanted SMS — the message stays in the SMS app, marked unread. Requires
 * notification access.
 */
@Composable
fun SmsSettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val smsOn by viewModel.smsBlockingEnabled.collectAsState()
    val notify by viewModel.blockedSmsNotification.collectAsState()
    val accessGranted by viewModel.notificationAccessGranted.collectAsState()

    ScrollableScreen(
        header = {
            GradientHeader(
                title = "Bouclier SMS",
                subtitle = "Masquez les SMS indésirables",
                leading = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour",
                            tint = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                },
            )
        },
    ) {
        // Notification access gate.
        if (!accessGranted) {
            item {
                BorderedCard(Modifier.padding(horizontal = 16.dp), accent = WCColor.Coral) {
                    Text("Accès aux notifications requis", fontWeight = FontWeight.Bold)
                    Text(
                        "Pour masquer les SMS indésirables, autorisez Who Called à accéder aux notifications. " +
                            "Nous ne lisons jamais le contenu de vos messages : nous masquons seulement la notification " +
                            "d’un expéditeur indésirable. Le SMS reste dans votre application de messagerie.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Button(onClick = { NotificationAccess.openSettings(context) }) {
                        Text("Activer l’accès aux notifications")
                    }
                }
            }
        }

        item {
            BorderedCard(Modifier.padding(horizontal = 16.dp), accent = if (smsOn) WCColor.Emerald else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Message,
                        contentDescription = null,
                        tint = if (smsOn) WCColor.Emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        "Bouclier SMS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                ToggleRow(
                    title = "Masquer les notifications de SMS indésirables",
                    description = "Le message reste dans votre application SMS, marqué comme non lu. Aucun message n’est lu ni supprimé.",
                    checked = smsOn,
                    enabled = accessGranted,
                ) { viewModel.setSmsBlockingEnabled(it) }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ToggleRow(
                    title = "M’avertir d’un SMS masqué",
                    description = "Affiche une notification discrète quand un SMS indésirable est masqué.",
                    checked = notify,
                    enabled = smsOn,
                ) { viewModel.setBlockedSmsNotification(it) }
            }
        }

        item {
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                Text("Comment ça marche", fontWeight = FontWeight.SemiBold)
                Text(
                    "Android ne permet pas de bloquer un SMS sans devenir votre application de messagerie par défaut. " +
                        "Who Called reste un filtre : nous masquons seulement la notification des expéditeurs au score de spam élevé, " +
                        "à partir de la même liste que pour les appels.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
