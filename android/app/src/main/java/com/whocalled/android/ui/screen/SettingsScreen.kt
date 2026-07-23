package com.whocalled.android.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.app.NotificationManagerCompat
import com.whocalled.android.BuildConfig
import com.whocalled.android.data.Preferences
import com.whocalled.android.service.NotificationHelper
import com.whocalled.android.ui.ApiTestState
import com.whocalled.android.ui.LoadState
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.components.GradientHeader
import com.whocalled.android.ui.components.ScrollableScreen
import com.whocalled.android.ui.theme.WCColor
import com.whocalled.android.worker.GameReminderWorker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privacy by viewModel.privacy.collectAsState()
    var showEraseDialog by remember { mutableStateOf(false) }

    var filtering by remember { mutableStateOf(true) }
    var warn by remember { mutableStateOf(true) }
    var blockedCallNotif by remember { mutableStateOf(true) }
    var funNotifs by remember { mutableStateOf(false) }
    var funCustomTitles by remember { mutableStateOf(setOf<String>()) }
    var newFunTitle by remember { mutableStateOf("") }
    var gameReminder by remember { mutableStateOf(false) }
    var threshold by remember { mutableFloatStateOf(85f) }
    var countryDial by remember { mutableStateOf("") }
    var showCountryDialog by remember { mutableStateOf(false) }

    if (showCountryDialog) {
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            title = { Text("Pays surveillé") },
            text = {
                Column {
                    com.whocalled.android.data.Countries.list.forEach { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                countryDial = c.dial
                                showCountryDialog = false
                                viewModel.setCountry(c.dial)
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.flag, modifier = Modifier.padding(end = 10.dp))
                            Text(c.name, modifier = Modifier.weight(1f))
                            if (c.dial == countryDial) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCountryDialog = false }) { Text("Fermer") } },
        )
    }

    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = { showEraseDialog = false },
            title = { Text("Supprimer mes données ?") },
            text = { Text("Tous les signalements envoyés depuis cet appareil seront définitivement supprimés de nos serveurs. Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = {
                    showEraseDialog = false
                    viewModel.eraseMyData()
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showEraseDialog = false }) { Text("Annuler") }
            },
        )
    }

    // Android 13+ requires the runtime POST_NOTIFICATIONS permission — without it
    // the daily reminder would be silently dropped. Asked when the toggle goes on.
    fun enableGameReminder() {
        gameReminder = true
        scope.launch {
            Preferences.setGameReminderEnabled(context, true)
            // Re-enabling by hand also un-mutes the ignored-reminders kill-switch.
            Preferences.resetReminderIgnored(context)
        }
        GameReminderWorker.schedule(context)
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enableGameReminder()
        } else {
            gameReminder = false
            scope.launch { Preferences.setGameReminderEnabled(context, false) }
        }
    }

    // Generic (fire-and-forget) notification-permission request, used when a
    // notification feature is enabled and by the hidden debug card. The toggle
    // stays on either way — without the permission, notifications just stay
    // silent, so the debug card surfaces the state explicitly.
    var notifsEnabled by remember { mutableStateOf(true) }
    val plainNotifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notifsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled() }

    fun askNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            plainNotifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openSystemNotificationSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }

    var showNotifDebug by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        filtering = Preferences.isFilteringEnabled(context)
        warn = Preferences.isWarnEnabled(context)
        blockedCallNotif = Preferences.blockedCallNotification(context)
        funNotifs = Preferences.funNotifications(context)
        funCustomTitles = Preferences.funCustomTitles(context)
        notifsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        gameReminder = Preferences.isGameReminderEnabled(context)
        threshold = Preferences.blockThreshold(context).toFloat()
        countryDial = Preferences.countryDial(context)
    }

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun email() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${BuildConfig.CONTACT_EMAIL}")
            putExtra(Intent.EXTRA_SUBJECT, "Who Called — contact")
        }
        context.startActivity(intent)
    }

    val apiTest by viewModel.apiTest.collectAsState()

    ScrollableScreen(header = { GradientHeader(title = "Réglages") }) {
        // Filtering
        item {
            SectionTitle("Filtrage")
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                ToggleRow(
                    title = "Bloquer les appels indésirables",
                    description = "Les numéros au score de spam élevé sont rejetés automatiquement : votre téléphone ne sonne pas.",
                    checked = filtering,
                ) {
                    filtering = it
                    scope.launch { Preferences.setFilteringEnabled(context, it) }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ToggleRow(
                    title = "M’alerter en cas de doute",
                    description = "Pour les numéros au score moyen (suspects sans certitude), le téléphone sonne normalement mais une notification vous prévient. Vous décidez de répondre ou non.",
                    checked = warn,
                ) {
                    warn = it
                    scope.launch { Preferences.setWarnEnabled(context, it) }
                    if (it) askNotifPermissionIfNeeded()
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ToggleRow(
                    title = "M’avertir d’un appel bloqué",
                    description = "Affiche une notification discrète après chaque appel indésirable bloqué.",
                    checked = blockedCallNotif,
                ) {
                    blockedCallNotif = it
                    scope.launch { Preferences.setBlockedCallNotification(context, it) }
                    if (it) askNotifPermissionIfNeeded()
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ToggleRow(
                    title = "Notifications droles 😜",
                    description = "Des messages fun quand un spam est bloqué (« Spam K.O. ! »). Désactivé par défaut.",
                    checked = funNotifs,
                ) {
                    funNotifs = it
                    scope.launch { Preferences.setFunNotifications(context, it) }
                }
                // Full message list — the built-in FR/EN defaults always stay in
                // rotation and are shown read-only; the user's own phrases join
                // them and can be edited / removed. "Réinitialiser" wipes the
                // customs → back to defaults only.
                if (funNotifs) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("Les messages 😜", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Voici tous les messages qui passent en rotation quand un spam est bloqué. Ajoutez les vôtres, modifiez-les ou revenez aux messages par défaut.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )

                        // Built-in defaults (FR + EN) — always active, read-only.
                        Text(
                            "Messages par défaut",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        NotificationHelper.defaultFunBlockedTitles.forEach { t ->
                            Text(
                                "•  $t",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }

                        // User's own phrases — editable / removable.
                        Text(
                            "Vos messages ✍️",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        if (funCustomTitles.isEmpty()) {
                            Text(
                                "Aucun message perso pour l'instant — écrivez le vôtre ci-dessous.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        funCustomTitles.forEach { t ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(t, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = {
                                    // Modifier : recharge le message dans le champ
                                    // pour l'éditer puis le ré-ajouter.
                                    newFunTitle = t
                                    funCustomTitles = funCustomTitles - t
                                    scope.launch { Preferences.removeFunCustomTitle(context, t) }
                                }) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = "Modifier ce message",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    funCustomTitles = funCustomTitles - t
                                    scope.launch { Preferences.removeFunCustomTitle(context, t) }
                                }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Supprimer ce message",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = newFunTitle,
                                onValueChange = { newFunTitle = it.take(Preferences.MAX_FUN_TITLE_LEN) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Ex : 🎣 Encore un phishing à la ligne !") },
                                supportingText = { Text("${newFunTitle.length}/${Preferences.MAX_FUN_TITLE_LEN} · emojis bienvenus 😄") },
                                singleLine = true,
                            )
                            TextButton(
                                onClick = {
                                    val t = newFunTitle.trim()
                                    if (t.isNotEmpty()) {
                                        funCustomTitles = funCustomTitles + t
                                        newFunTitle = ""
                                        scope.launch { Preferences.addFunCustomTitle(context, t) }
                                    }
                                },
                                enabled = newFunTitle.isNotBlank(),
                            ) { Text("Ajouter") }
                        }
                        if (funCustomTitles.isNotEmpty()) {
                            TextButton(onClick = {
                                funCustomTitles = emptySet()
                                newFunTitle = ""
                                scope.launch { Preferences.clearFunCustomTitles(context) }
                            }) {
                                Text("Réinitialiser (revenir aux messages par défaut)")
                            }
                        }
                    }
                }
            }
        }

        // Jeux — daily reminder (opt-in, and we respect the choice: off cancels it).
        item {
            SectionTitle("Jeux")
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                ToggleRow(
                    title = "Rappel quotidien pour jouer 🎮",
                    description = "Un rappel bienveillant entre 18h30 et 20h30, jamais si vous avez déjà joué. Il se met en pause tout seul si vous l'ignorez plusieurs jours de suite.",
                    checked = gameReminder,
                ) { on ->
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            enableGameReminder()
                        }
                    } else {
                        gameReminder = false
                        scope.launch { Preferences.setGameReminderEnabled(context, false) }
                        GameReminderWorker.cancel(context)
                    }
                }
            }
        }

        // Country scope
        item {
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showCountryDialog = true }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Pays surveillé", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Ne télécharger que les numéros de ce pays (cache plus léger). Détecté automatiquement, modifiable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    val c = com.whocalled.android.data.Countries.byDial(countryDial)
                    Text("${c.flag}  ${c.name}", fontWeight = FontWeight.Medium)
                }
            }
        }

        // Threshold
        item {
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                Text("Sensibilité du blocage : ${threshold.toInt()} %", fontWeight = FontWeight.SemiBold)
                Text(
                    "À partir de ce score de spam, un numéro est bloqué. En dessous, il déclenche seulement une alerte (si l’option ci-dessus est activée). Plus le seuil est bas, plus on bloque — au risque de quelques faux positifs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    onValueChangeFinished = {
                        scope.launch { Preferences.setBlockThreshold(context, threshold.toInt()) }
                    },
                    valueRange = 50f..100f,
                )
            }
        }

        // Legal & links
        item {
            SectionTitle("Informations")
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                // Each link is shown only when its URL/value is configured (empty → hidden).
                LinkRow("Confidentialité", Icons.Rounded.Lock, BuildConfig.PRIVACY_URL, ::open)
                LinkRow("Mentions légales", Icons.Rounded.Description, BuildConfig.POLICY_URL, ::open)
                LinkRow("Site officiel", Icons.Rounded.Language, BuildConfig.SITE_URL, ::open)
                LinkRow("Noter l’application", Icons.Rounded.StarBorder, BuildConfig.RATE_URL, ::open)
                LinkRow("Code source (open source)", Icons.Rounded.Code, BuildConfig.REPO_URL, ::open)
                if (BuildConfig.CONTACT_EMAIL.isNotBlank()) {
                    LinkRow("Nous contacter", Icons.Rounded.MailOutline) { email() }
                }
            }
        }

        // Data / RGPD
        item {
            SectionTitle("Mes données")
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                Text("Supprimer mes signalements", fontWeight = FontWeight.SemiBold)
                Text(
                    "Efface définitivement tous les signalements envoyés depuis cet appareil. Conforme à votre droit à l’effacement (RGPD).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                TextButton(onClick = { showEraseDialog = true }) {
                    Text("Supprimer mes données", color = MaterialTheme.colorScheme.error)
                }
                when (val p = privacy) {
                    is LoadState.Loading -> Text("Suppression…", style = MaterialTheme.typography.bodySmall)
                    is LoadState.Error -> Text(p.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    is LoadState.Success -> p.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    else -> {}
                }
            }
        }

        // Diagnostic — explicit, always-visible API connectivity test (no need to
        // scroll to the bottom; the version double-tap remains as a shortcut).
        item {
            SectionTitle("Diagnostic")
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                Text("Connexion à l’API", fontWeight = FontWeight.SemiBold)
                Text(
                    "Vérifiez que l’application joint bien le serveur Who Called.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = { viewModel.runApiTest() },
                    enabled = apiTest !is ApiTestState.Testing,
                ) {
                    Text(if (apiTest is ApiTestState.Testing) "Test en cours…" else "Tester la connexion")
                }
                val (label, color) = when (val s = apiTest) {
                    is ApiTestState.Idle -> null to MaterialTheme.colorScheme.onSurfaceVariant
                    is ApiTestState.Testing -> "Connexion au serveur…" to MaterialTheme.colorScheme.onSurfaceVariant
                    is ApiTestState.Ok -> "API OK ✓ (${s.latencyMs} ms)" to WCColor.Emerald
                    is ApiTestState.Failed -> "API injoignable ✗ — ${s.reason}" to WCColor.Coral
                }
                if (label != null) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        // Free & open source note
        item {
            BorderedCard(Modifier.padding(horizontal = 16.dp), accent = MaterialTheme.colorScheme.primary) {
                Text("Gratuit et open source", fontWeight = FontWeight.SemiBold)
                Text(
                    "Who Called est 100 % gratuit et son code est ouvert. 100 % anonyme — aucun compte, aucune donnée personnelle. Numéros au format international.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Version — double-tap also runs the API test (shortcut for the section
        // above); LONG-PRESS reveals the hidden notification debug card.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { viewModel.runApiTest() },
                            onLongPress = {
                                showNotifDebug = !showNotifDebug
                                if (showNotifDebug) {
                                    notifsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                                }
                            },
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Who Called v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                val (label, color) = when (val s = apiTest) {
                    is ApiTestState.Idle -> "Double-tap pour tester l’API" to MaterialTheme.colorScheme.onSurfaceVariant
                    is ApiTestState.Testing -> "Test de l’API…" to MaterialTheme.colorScheme.onSurfaceVariant
                    is ApiTestState.Ok -> "API OK ✓ (${s.latencyMs} ms)" to WCColor.Emerald
                    is ApiTestState.Failed -> "API injoignable ✗ — ${s.reason}" to WCColor.Coral
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // HIDDEN notification debug card (long-press the version to reveal).
        // Fires each notification type immediately so the whole pipeline —
        // permission, channel, heads-up — can be verified on-device.
        if (showNotifDebug) {
            item {
                SectionTitle("Debug notifications")
                BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Autorisation notifications : " +
                            if (notifsEnabled) "accordée ✓" else "REFUSÉE ✗ — rien ne s'affichera",
                        fontWeight = FontWeight.SemiBold,
                        color = if (notifsEnabled) WCColor.Emerald else MaterialTheme.colorScheme.error,
                    )
                    if (!notifsEnabled) {
                        Row {
                            TextButton(onClick = { askNotifPermissionIfNeeded() }) { Text("Demander") }
                            TextButton(onClick = { openSystemNotificationSettings() }) { Text("Réglages système") }
                        }
                    }
                    Text(
                        "Envoyer un test maintenant :",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    androidx.compose.material3.OutlinedButton(onClick = {
                        NotificationHelper.showBlockedCall(context, "+33612345678", null, funMode = false)
                    }) { Text("Appel bloqué (standard)") }
                    androidx.compose.material3.OutlinedButton(onClick = {
                        NotificationHelper.showBlockedCall(context, "+33612345678", null, funMode = true, customTitles = funCustomTitles)
                    }) { Text("Appel bloqué (fun)") }
                    androidx.compose.material3.OutlinedButton(onClick = {
                        NotificationHelper.showWarning(context, "+33612345678", 72, null)
                    }) { Text("Alerte WARN (score 72)") }
                    androidx.compose.material3.OutlinedButton(onClick = {
                        NotificationHelper.showBlockedSms(context, "36777")
                    }) { Text("SMS indésirable masqué") }
                    androidx.compose.material3.OutlinedButton(onClick = {
                        NotificationHelper.showGameReminder(context)
                    }) { Text("Rappel jeu quotidien") }
                    Text(
                        "Long-press sur la version pour masquer ce panneau.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
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
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Link row bound to a URL — renders nothing when the URL is blank. */
@Composable
private fun LinkRow(label: String, icon: ImageVector, url: String, open: (String) -> Unit) {
    if (url.isBlank()) return
    LinkRow(label, icon) { open(url) }
}

@Composable
private fun LinkRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(label, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
