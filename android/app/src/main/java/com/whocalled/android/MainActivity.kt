package com.whocalled.android

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    private val notificationsGranted = mutableStateOf(false)

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
        notificationsGranted.value = granted
        // The daily game reminder is on by default but stays dormant until the
        // permission exists — arm it as soon as we actually can notify.
        if (granted) {
            com.whocalled.android.worker.GameReminderWorker.schedule(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge + WindowInsets.safeDrawing (e.g. setup Scaffold) is the
        // supported pattern on targetSdk 35+ so content never sits under the
        // status/nav bars or camera cutout.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        refreshRoleState()
        refreshNotificationState()
        handleShareIntent(intent)
        handleOpenCallIntent(intent)
        handleOpenGamesIntent(intent)
        handleOpenPhoneIntent(intent)

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
                        notificationsGranted = notificationsGranted,
                        onRequestRole = ::requestScreeningRole,
                        onRequestNotifications = ::requestNotificationPermission,
                        onSyncNow = viewModel::syncNow,
                        onRequestCallLogPermission = {
                            callLogLauncher.launch(android.Manifest.permission.READ_CALL_LOG)
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRoleState()
        refreshNotificationState()
        viewModel.refreshCallLogPermission()
        viewModel.refreshSmsState()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app seals the session: the Home card shown during it
        // becomes "seen" and stops headlining old news on the next open.
        viewModel.commitRecentCallSeen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        handleOpenCallIntent(intent)
        handleOpenGamesIntent(intent)
        handleOpenPhoneIntent(intent)
    }

    /** A notification tap carries a number → open that number's page. */
    private fun handleOpenPhoneIntent(intent: Intent?) {
        val phone = intent?.getStringExtra(
            com.whocalled.android.service.NotificationHelper.EXTRA_OPEN_PHONE,
        )
        if (!phone.isNullOrBlank()) viewModel.requestOpenPhone(phone)
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
    }

    private fun refreshNotificationState() {
        notificationsGranted.value = if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationsGranted.value = true
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
