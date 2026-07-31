package com.whocalled.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.whocalled.android.ui.screen.CallDetailScreen
import com.whocalled.android.ui.screen.CallsScreen
import com.whocalled.android.ui.screen.DefenseGameScreen
import com.whocalled.android.ui.screen.GamesScreen
import com.whocalled.android.ui.screen.HomeScreen
import com.whocalled.android.ui.screen.LeaderboardScreen
import com.whocalled.android.ui.screen.TraceGameScreen
import com.whocalled.android.ui.screen.MyReportsScreen
import com.whocalled.android.ui.screen.StreakScreen
import com.whocalled.android.ui.screen.ReportScreen
import com.whocalled.android.ui.screen.SettingsScreen
import com.whocalled.android.ui.screen.SmsSettingsScreen

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun WhoCalledApp(
    viewModel: MainViewModel,
    isScreeningRoleHeld: State<Boolean>,
    onRequestRole: () -> Unit,
    onSyncNow: () -> Unit,
    onRequestCallLogPermission: () -> Unit,
) {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab("home", "Accueil") { Icon(Icons.Rounded.Home, null) },
        Tab("calls", "Appels") { Icon(Icons.Rounded.Phone, null) },
        Tab("report", "Signaler") { Icon(Icons.Rounded.Flag, null) },
        Tab("games", "Jeux") { Text("🎮") },
        Tab("settings", "Réglages") { Icon(Icons.Rounded.Settings, null) },
    )

    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route

    // A shared number (system share sheet or a trending tap) routes to the
    // Report tab, where the screen consumes it to pre-fill the field.
    val sharedPhone by viewModel.sharedPhone.collectAsState()
    LaunchedEffect(sharedPhone) {
        if (sharedPhone != null && route != "report") {
            nav.navigate("report") {
                popUpTo("home")
                launchSingleTop = true
            }
        }
    }

    // A notification tap deep-links to a blocked/warned call's detail screen.
    val openCallId by viewModel.openCallId.collectAsState()
    LaunchedEffect(openCallId) {
        openCallId?.let { id ->
            viewModel.consumeOpenCall()
            nav.navigate("call/$id") { launchSingleTop = true }
        }
    }

    // A daily game-reminder tap deep-links to the Games hub (the "choose a game" landing).
    val openGames by viewModel.openGames.collectAsState()
    LaunchedEffect(openGames) {
        if (openGames) {
            viewModel.consumeOpenGames()
            nav.navigate("games") { popUpTo("home"); launchSingleTop = true }
        }
    }
    // Hide the bottom tab bar on drill-down screens (e.g. the call detail), so
    // they use the full height and nothing is clipped.
    val showBottomBar = route in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo("home")
                                    launchSingleTop = true
                                }
                            },
                            icon = tab.icon,
                            label = { Text(tab.label, maxLines = 1, softWrap = false) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    isScreeningRoleHeld = isScreeningRoleHeld.value,
                    onRequestRole = onRequestRole,
                    onSyncNow = onSyncNow,
                    onSeeAllHistory = { nav.navigate("calls") },
                    onRecentCallClick = { call ->
                        if (call.callLogId != null) {
                            nav.navigate("call/${call.callLogId}")
                        } else {
                            nav.navigate("number/${call.phone}")
                        }
                    },
                    onSmsClick = { nav.navigate("sms") },
                    onPlayGame = { nav.navigate("games") },
                    onOpenLeaderboard = { nav.navigate("games") },
                )
            }
            composable("calls") {
                CallsScreen(
                    viewModel = viewModel,
                    onCallClick = { call ->
                        viewModel.markRecentCallHandled(call)
                        if (call.callLogId != null) {
                            nav.navigate("call/${call.callLogId}")
                        } else {
                            nav.navigate("number/${call.phone}")
                        }
                    },
                    onRequestCallLogPermission = onRequestCallLogPermission,
                )
            }
            composable("sms") {
                SmsSettingsScreen(viewModel = viewModel, onBack = { nav.popBackStack() })
            }
            composable("report") {
                ReportScreen(
                    viewModel = viewModel,
                    onOpenCalls = { nav.navigate("calls") },
                    onOpenMyReports = { nav.navigate("myreports") },
                )
            }
            composable("myreports") {
                MyReportsScreen(
                    viewModel,
                    onBack = { nav.popBackStack() },
                    onOpenNumber = { phone -> nav.navigate("number/$phone") },
                )
            }
            composable("settings") { SettingsScreen(viewModel) }
            composable("games") {
                GamesScreen(
                    viewModel = viewModel,
                    onPlay = { g -> nav.navigate("game/$g") },
                    onPractice = { g -> nav.navigate("game/$g/practice") },
                    onStreak = { g -> nav.navigate("streak/$g") },
                )
            }
            composable("game/defense") {
                DefenseGameScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.refreshGameState("defense")
                        nav.popBackStack()
                    },
                    onLeaderboard = { nav.navigate("leaderboard/defense") },
                )
            }
            composable("game/trace") {
                TraceGameScreen(
                    viewModel = viewModel,
                    ranked = true,
                    onBack = {
                        viewModel.refreshGameState("trace")
                        viewModel.clearTracePuzzle()
                        nav.popBackStack()
                    },
                    onLeaderboard = { nav.navigate("leaderboard/trace") },
                )
            }
            composable("game/trace/practice") {
                TraceGameScreen(
                    viewModel = viewModel,
                    ranked = false,
                    onBack = {
                        viewModel.clearTracePuzzle()
                        nav.popBackStack()
                    },
                )
            }
            composable(
                "leaderboard/{game}",
                arguments = listOf(navArgument("game") { type = NavType.StringType }),
            ) { entry ->
                LeaderboardScreen(
                    viewModel = viewModel,
                    game = entry.arguments?.getString("game") ?: "defense",
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "streak/{game}",
                arguments = listOf(navArgument("game") { type = NavType.StringType }),
            ) { entry ->
                StreakScreen(
                    game = entry.arguments?.getString("game") ?: "defense",
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "call/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                CallDetailScreen(
                    viewModel = viewModel,
                    callId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "number/{phone}",
                arguments = listOf(navArgument("phone") { type = NavType.StringType }),
            ) { entry ->
                CallDetailScreen(
                    viewModel = viewModel,
                    phone = entry.arguments?.getString("phone") ?: "",
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
