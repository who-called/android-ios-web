package com.whocalled.android

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.whocalled.android.ui.MainViewModel
import com.whocalled.android.ui.WhoCalledApp
import com.whocalled.android.ui.theme.WhoCalledTheme
import com.whocalled.android.util.PhoneNormalizer
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val isScreeningRole = mutableStateOf(false)

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshRoleState() }

    private val callLogLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshCallLogPermission() }

    // POST_NOTIFICATIONS is a runtime permission on Android 13+ — without it,
    // EVERY notification (blocked call, WARN, reminder) is silently dropped.
    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // The daily game reminder is on by default but stays dormant until the
        // permission exists — arm it as soon as we actually can notify.
        if (granted) {
            com.whocalled.android.worker.GameReminderWorker.schedule(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshRoleState()
        handleShareIntent(intent)
        handleOpenCallIntent(intent)
        handleOpenGamesIntent(intent)

        setContent {
            WhoCalledTheme {
                // Brief branded splash on launch so the first thing the user sees is
                // the shield + the "Who Called" name, not just an icon.
                val showSplash = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1100)
                    showSplash.value = false
                }
                if (showSplash.value) {
                    com.whocalled.android.ui.components.BrandedLoader()
                } else {
                    WhoCalledApp(
                        viewModel = viewModel,
                        isScreeningRoleHeld = isScreeningRole,
                        onRequestRole = ::requestScreeningRole,
                        onSyncNow = viewModel::syncNow,
                        onRequestCallLogPermission = {
                            callLogLauncher.launch(android.Manifest.permission.READ_CALL_LOG)
                        },
                    )
                    NotificationPrePrompt()
                }
            }
        }
    }

    /**
     * One-time, in-app notification pre-prompt (Android 13+). Framed around what
     * the user installed the app for — protection: alerts on suspicious calls,
     * blocked-call notices… and the daily challenge reminder rides along. Shown
     * once, BEFORE the system dialog, so a "no" here costs none of the ~2 system
     * prompts Android allows.
     */
    @androidx.compose.runtime.Composable
    private fun NotificationPrePrompt() {
        if (Build.VERSION.SDK_INT < 33) return
        val show = androidx.compose.runtime.remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            show.value = !granted && !com.whocalled.android.data.Preferences.isNotifPromptSeen(this@MainActivity)
        }
        if (!show.value) return

        val dismiss: (Boolean) -> Unit = { accepted ->
            show.value = false
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.whocalled.android.data.Preferences.setNotifPromptSeen(this@MainActivity)
            }
            if (accepted) {
                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { dismiss(false) },
            icon = { androidx.compose.material3.Text("🛡️", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium) },
            title = { androidx.compose.material3.Text("Restez protégé") },
            text = {
                androidx.compose.material3.Text(
                    "Who Called vous alerte quand un appel suspect sonne, vous prévient " +
                        "des appels et SMS bloqués, et vous rappelle le défi du jour. " +
                        "Sans les notifications, la protection reste silencieuse.",
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(onClick = { dismiss(true) }) {
                    androidx.compose.material3.Text("Activer les notifications")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { dismiss(false) }) {
                    androidx.compose.material3.Text("Plus tard")
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        refreshRoleState()
        viewModel.refreshCallLogPermission()
        viewModel.refreshSmsState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        handleOpenCallIntent(intent)
        handleOpenGamesIntent(intent)
    }

    /** A notification tap carries a call-log id → open that call's detail. */
    private fun handleOpenCallIntent(intent: Intent?) {
        val id = intent?.getLongExtra(
            com.whocalled.android.service.NotificationHelper.EXTRA_OPEN_CALL_ID,
            -1L,
        ) ?: -1L
        if (id > 0L) viewModel.requestOpenCall(id)
    }

    /** The daily game-reminder tap → open the Games hub. */
    private fun handleOpenGamesIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(
                com.whocalled.android.service.NotificationHelper.EXTRA_OPEN_GAMES, false,
            ) == true
        ) {
            // Tapping the reminder proves it's welcome — reset the kill-switch.
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.whocalled.android.data.Preferences.resetReminderIgnored(this@MainActivity)
            }
            viewModel.requestOpenGames()
        }
    }

    /**
     * Handle a shared number/SMS (ACTION_SEND, text/plain) from the system share
     * sheet: extract a phone-like candidate and hand it to the report flow.
     */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val phone = PhoneNormalizer.extractFromText(shared) ?: shared.trim()
        viewModel.setSharedPhone(phone)
    }

    private fun roleManager(): RoleManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java)
        } else {
            null
        }

    private fun refreshRoleState() {
        val rm = roleManager()
        isScreeningRole.value =
            rm?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        requestNotifPermissionIfNeeded()
    }

    /**
     * Ask for POST_NOTIFICATIONS in context: right when the user commits to call
     * protection (screening role held). No-op below Android 13 or once decided —
     * the system itself caps how often the dialog can appear.
     */
    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            isScreeningRole.value &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestScreeningRole() {
        val rm = roleManager() ?: return
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        ) {
            val intent: Intent = rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            roleLauncher.launch(intent)
        }
    }
}
