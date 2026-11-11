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
import androidx.compose.material.icons.rounded.Gavel
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.whocalled.android.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.whocalled.android.BuildConfig
import com.whocalled.android.data.Preferences
import com.whocalled.android.service.NotificationHelper
import com.whocalled.android.service.WarnOverlay
import com.whocalled.android.ui.ApiTestState
import com.whocalled.android.ui.LoadState
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.components.BorderedCard
import com.whocalled.android.ui.components.GradientHeader
import com.whocalled.android.ui.components.ScrollableScreen
import com.whocalled.android.ui.theme.WCColor
import com.whocalled.android.util.OverlayPermission
import com.whocalled.android.worker.GameReminderWorker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val privacy by viewModel.privacy.collectAsState()
    var showEraseDialog by remember { mutableStateOf(false) }

    var filtering by remember { mutableStateOf(true) }
    var warn by remember { mutableStateOf(true) }
    var overlayGranted by remember { mutableStateOf(false) }
    var blockedCallNotif by remember { mutableStateOf(true) }
    var funNotifs by remember { mutableStateOf(false) }
    var funCustomTitles by remember { mutableStateOf(setOf<String>()) }
    var newFunTitle by remember { mutableStateOf("") }
    var gameReminder by remember { mutableStateOf(false) }
    // Anti-spam pause state of the reminder — surfaced so "toggle ON but
    // silent" is never a mystery (the historic invisible-mute bug).
    var reminderIgnored by remember { mutableStateOf(0) }
    var reminderSnoozedUntil by remember { mutableStateOf(0L) }
    var threshold by remember { mutableFloatStateOf(85f) }
    var countryDial by remember { mutableStateOf("") }
    var showCountryDialog by remember { mutableStateOf(false) }

    if (showCountryDialog) {
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            title = { Text(stringResource(R.string.country_dialog_title)) },
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
                            Text(stringResource(c.nameRes), modifier = Modifier.weight(1f))
                            if (c.dial == countryDial) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCountryDialog = false }) { Text(stringResource(R.string.common_close)) } },
        )
    }

    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = { showEraseDialog = false },
            title = { Text(stringResource(R.string.settings_erase_dialog_title)) },
            text = { Text(stringResource(R.string.settings_erase_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showEraseDialog = false
                    viewModel.eraseMyData()
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showEraseDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    // Android 13+ requires the runtime POST_NOTIFICATIONS permission — without it
    // the daily reminder would be silently dropped. Asked when the toggle goes on.
    fun enableGameReminder() {
        gameReminder = true
        scope.launch {
            Preferences.setGameReminderEnabled(context, true)
            // Re-enabling by hand also lifts the ignored-reminders pause.
            Preferences.resetReminderIgnored(context)
            reminderIgnored = 0
            reminderSnoozedUntil = 0L
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
        overlayGranted = OverlayPermission.isGranted(context)
        blockedCallNotif = Preferences.blockedCallNotification(context)
        funNotifs = Preferences.funNotifications(context)
        funCustomTitles = Preferences.funCustomTitles(context)
        notifsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        gameReminder = Preferences.isGameReminderEnabled(context)
        reminderIgnored = Preferences.reminderIgnoredCount(context)
        reminderSnoozedUntil = Preferences.reminderSnoozedUntilDay(context)
        threshold = Preferences.blockThreshold(context).toFloat()
        countryDial = Preferences.countryDial(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = OverlayPermission.isGranted(context)
                notifsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun email() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${BuildConfig.CONTACT_EMAIL}")
            putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.settings_contact_subject))
        }
        context.startActivity(intent)
    }

    val apiTest by viewModel.apiTest.collectAsState()

    ScrollableScreen(header = { GradientHeader(title = stringResource(R.string.settings_title)) }) {
        // Filtering
        item {
            SectionTitle(stringResource(R.string.settings_section_filtering))
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                ToggleRow(
                    title = stringResource(R.string.settings_block_title),
                    description = stringResource(R.string.settings_block_desc),
                    checked = filtering,
                ) {
                    filtering = it
                    scope.launch { Preferences.setFilteringEnabled(context, it) }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ToggleRow(
                    title = stringResource(R.string.settings_warn_title),
                    description = stringResource(R.string.settings_warn_desc),
                    checked = warn,
                ) {
                    warn = it
                    scope.launch { Preferences.setWarnEnabled(context, it) }
                    if (it) askNotifPermissionIfNeeded()
                }
                if (warn) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    ToggleRow(
                        title = stringResource(R.string.settings_overlay_title),
                        description = stringResource(R.string.settings_overlay_desc),
                        checked = overlayGranted,
                    ) {
                        OverlayPermission.openSettings(context)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ToggleRow(
                    title = stringResource(R.string.settings_blocked_notif_title),
                    description = stringResource(R.string.settings_blocked_notif_desc),
                    checked = blockedCallNotif,
                ) {
                    blockedCallNotif = it
                    scope.launch { Preferences.setBlockedCallNotification(context, it) }
                    if (it) askNotifPermissionIfNeeded()
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ToggleRow(
                    title = stringResource(R.string.settings_fun_title),
                    description = stringResource(R.string.settings_fun_desc),
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
                        Text(stringResource(R.string.settings_fun_messages_title), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.settings_fun_messages_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )

                        // Built-in defaults (UI language + EN) — always active, read-only.
                        Text(
                            stringResource(R.string.settings_fun_defaults),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        stringArrayResource(R.array.fun_blocked_default_titles).forEach { t ->
                            Text(
                                "•  $t",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }

                        // User's own phrases — editable / removable.
                        Text(
                            stringResource(R.string.settings_fun_yours),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        if (funCustomTitles.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_fun_none),
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
                                        contentDescription = stringResource(R.string.settings_fun_edit),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    funCustomTitles = funCustomTitles - t
                                    scope.launch { Preferences.removeFunCustomTitle(context, t) }
                                }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.settings_fun_delete),
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
                                placeholder = { Text(stringResource(R.string.settings_fun_placeholder)) },
                                supportingText = { Text(stringResource(R.string.settings_fun_counter, newFunTitle.length, Preferences.MAX_FUN_TITLE_LEN)) },
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
                            ) { Text(stringResource(R.string.settings_fun_add)) }
                        }
                        if (funCustomTitles.isNotEmpty()) {
                            TextButton(onClick = {
                                funCustomTitles = emptySet()
                                newFunTitle = ""
                                scope.launch { Preferences.clearFunCustomTitles(context) }
                            }) {
                                Text(stringResource(R.string.settings_fun_reset))
                            }
                        }
                    }
                }
            }
        }

        // Jeux — daily reminder (opt-in, and we respect the choice: off cancels it).
        item {
            SectionTitle(stringResource(R.string.settings_section_games))
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                ToggleRow(
                    title = stringResource(R.string.settings_reminder_title),
                    description = stringResource(
                        R.string.settings_reminder_desc,
                        Preferences.REMINDER_IGNORED_LIMIT,
                        Preferences.REMINDER_SNOOZE_DAYS,
                    ),
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
                // The pause used to be invisible (toggle ON, zero reminders) —
                // show it, with a one-tap way out.
                if (gameReminder && reminderIgnored >= Preferences.REMINDER_IGNORED_LIMIT) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(stringResource(R.string.settings_reminder_paused_title), fontWeight = FontWeight.SemiBold)
                            val until = reminderSnoozedUntil
                            Text(
                                if (until > 0L) {
                                    val date = java.time.LocalDate.ofEpochDay(until).format(
                                        java.time.format.DateTimeFormatter.ofPattern("d MMMM", com.whocalled.android.util.UiLocale.of(context)),
                                    )
                                    stringResource(R.string.settings_reminder_paused_until, date)
                                } else {
                                    stringResource(R.string.settings_reminder_paused_soon)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            scope.launch { Preferences.resetReminderIgnored(context) }
                            reminderIgnored = 0
                            reminderSnoozedUntil = 0L
                            GameReminderWorker.schedule(context)
                        }) { Text(stringResource(R.string.settings_reminder_resume)) }
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
                        Text(stringResource(R.string.country_dialog_title), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.settings_country_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    val c = com.whocalled.android.data.Countries.byDial(countryDial)
                    Text("${c.flag}  ${stringResource(c.nameRes)}", fontWeight = FontWeight.Medium)
                }
            }
        }

        // Threshold
        item {
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.settings_threshold, threshold.toInt()), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.settings_threshold_desc),
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
            SectionTitle(stringResource(R.string.settings_section_info))
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                // Each link is shown only when its URL/value is configured (empty → hidden).
                LinkRow(stringResource(R.string.settings_link_privacy), Icons.Rounded.Lock, BuildConfig.PRIVACY_URL, ::open)
                LinkRow(stringResource(R.string.settings_link_legal), Icons.Rounded.Description, BuildConfig.POLICY_URL, ::open)
                LinkRow(stringResource(R.string.settings_link_site), Icons.Rounded.Language, BuildConfig.SITE_URL, ::open)
                LinkRow(stringResource(R.string.settings_link_arcep), Icons.Rounded.Gavel, BuildConfig.ARCEP_SOURCE_URL, ::open)
                LinkRow(stringResource(R.string.settings_link_rate), Icons.Rounded.StarBorder, BuildConfig.RATE_URL, ::open)
                LinkRow(stringResource(R.string.settings_link_source), Icons.Rounded.Code, BuildConfig.REPO_URL, ::open)
                if (BuildConfig.CONTACT_EMAIL.isNotBlank()) {
                    LinkRow(stringResource(R.string.settings_link_contact), Icons.Rounded.MailOutline) { email() }
                }
            }
        }

        // Data / RGPD
        item {
            SectionTitle(stringResource(R.string.settings_section_data))
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.settings_erase_title), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.settings_erase_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                TextButton(onClick = { showEraseDialog = true }) {
                    Text(stringResource(R.string.settings_erase_button), color = MaterialTheme.colorScheme.error)
                }
                when (val p = privacy) {
                    is LoadState.Loading -> Text(stringResource(R.string.settings_erasing), style = MaterialTheme.typography.bodySmall)
                    is LoadState.Error -> Text(p.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    is LoadState.Success -> p.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    else -> {}
                }
            }
        }

        // Diagnostic — explicit, always-visible API connectivity test (no need to
        // scroll to the bottom; the version double-tap remains as a shortcut).
        item {
            SectionTitle(stringResource(R.string.settings_section_diagnostic))
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.settings_api_title), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.settings_api_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = { viewModel.runApiTest() },
                    enabled = apiTest !is ApiTestState.Testing,
                ) {
                    Text(stringResource(if (apiTest is ApiTestState.Testing) R.string.settings_api_testing else R.string.settings_api_test))
                }
                val (label, color) = when (val s = apiTest) {
                    is ApiTestState.Idle -> null to MaterialTheme.colorScheme.onSurfaceVariant
                    is ApiTestState.Testing -> stringResource(R.string.common_connecting) to MaterialTheme.colorScheme.onSurfaceVariant
                    is ApiTestState.Ok -> stringResource(R.string.settings_api_ok, s.latencyMs) to WCColor.Emerald
                    is ApiTestState.Failed -> stringResource(R.string.settings_api_failed, s.reason) to WCColor.Coral
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
                Text(stringResource(R.string.settings_free_title), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.settings_free_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Non-affiliation disclaimer (Play "misleading claims" policy: apps
        // sharing government info must clearly state they are not official).
        item {
            BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.settings_independent_title), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.settings_independent_body),
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
                    is ApiTestState.Idle -> stringResource(R.string.settings_version_hint) to MaterialTheme.colorScheme.onSurfaceVariant
                    is ApiTestState.Testing -> stringResource(R.string.settings_version_testing) to MaterialTheme.colorScheme.onSurfaceVariant
                    is ApiTestState.Ok -> stringResource(R.string.settings_api_ok, s.latencyMs) to WCColor.Emerald
                    is ApiTestState.Failed -> stringResource(R.string.settings_api_failed, s.reason) to WCColor.Coral
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
                SectionTitle(stringResource(R.string.debug_title))
                BorderedCard(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(
                            R.string.debug_permission,
                            stringResource(if (notifsEnabled) R.string.debug_permission_granted else R.string.debug_permission_denied),
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = if (notifsEnabled) WCColor.Emerald else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(if (overlayGranted) R.string.debug_overlay_granted else R.string.debug_overlay_denied),
                        fontWeight = FontWeight.SemiBold,
                        color = if (overlayGranted) WCColor.Emerald else WCColor.Amber,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (!notifsEnabled) {
                        Row {
                            TextButton(onClick = { askNotifPermissionIfNeeded() }) { Text(stringResource(R.string.debug_request)) }
                            TextButton(onClick = { openSystemNotificationSettings() }) { Text(stringResource(R.string.debug_system_settings)) }
                        }
                    }
                    if (!overlayGranted) {
                        TextButton(onClick = { OverlayPermission.openSettings(context) }) {
                            Text(stringResource(R.string.debug_overlay_allow))
                        }
                    }
                    Text(
                        stringResource(R.string.debug_simulate_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    androidx.compose.material3.Button(onClick = {
                        NotificationHelper.showWarning(context, "+33612345678", 72, "telemarketing", null)
                    }) { Text(stringResource(R.string.debug_simulate)) }
                    Text(
                        stringResource(R.string.debug_other_tests),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    androidx.compose.material3.OutlinedButton(onClick = {
                        NotificationHelper.showBlockedCall(context, "+33612345678", null, funMode = false)
                    }) { Text(stringResource(R.string.debug_blocked_standard)) }
                    androidx.compose.material3.OutlinedButton(onClick = {
                        NotificationHelper.showBlockedCall(context, "+33612345678", null, funMode = true, customTitles = funCustomTitles)
                    }) { Text(stringResource(R.string.debug_blocked_fun)) }
                    androidx.compose.material3.OutlinedButton(onClick = {
                        WarnOverlay.show(context, "+33612345678", 72, "telemarketing")
                    }) { Text(stringResource(R.string.debug_overlay_only)) }
                    androidx.compose.material3.OutlinedButton(onClick = {
                        NotificationHelper.showBlockedSms(context, "36777")
                    }) { Text(stringResource(R.string.debug_sms)) }
                    androidx.compose.material3.OutlinedButton(onClick = {
                        NotificationHelper.showGameReminder(context)
                    }) { Text(stringResource(R.string.debug_reminder)) }
                    Text(
                        stringResource(R.string.debug_hide_hint),
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
