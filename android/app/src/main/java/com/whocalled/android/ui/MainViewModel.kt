package com.whocalled.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whocalled.android.data.CallLogEntity
import com.whocalled.android.data.MyReportEntity
import com.whocalled.android.data.WhoCalledDatabase
import com.whocalled.android.data.WhoCalledRepository
import com.whocalled.android.util.CallLogReader
import com.whocalled.android.util.PhoneCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Generic async UI state for loaders + error handling. */
sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data class Error(val message: String) : LoadState
    data class Success(val message: String? = null) : LoadState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WhoCalledRepository(app)
    private val db = WhoCalledDatabase.get(app)

    // Home stats + history
    val blockedCount: StateFlow<Int> =
        db.callLogDao().observeBlockedCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val warnedCount: StateFlow<Int> =
        db.callLogDao().observeWarnedCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val recentCalls = db.callLogDao().observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Full history grouped into time sections (Aujourd'hui / Hier / …) with
    // same-number-same-day repeats collapsed — for the dedicated history screen.
    val groupedHistory: StateFlow<List<com.whocalled.android.util.CallHistorySection>> =
        db.callLogDao().observeRecent()
            .map { com.whocalled.android.util.groupCallHistory(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // SMS shield state (mirrors DataStore; refreshed on resume / after toggling).
    private val _smsBlockingEnabled = MutableStateFlow(false)
    val smsBlockingEnabled: StateFlow<Boolean> = _smsBlockingEnabled

    private val _blockedSmsNotification = MutableStateFlow(true)
    val blockedSmsNotification: StateFlow<Boolean> = _blockedSmsNotification

    private val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted

    fun refreshSmsState() {
        viewModelScope.launch {
            _smsBlockingEnabled.value = com.whocalled.android.data.Preferences.isSmsBlockingEnabled(getApplication())
            _blockedSmsNotification.value = com.whocalled.android.data.Preferences.blockedSmsNotification(getApplication())
            _notificationAccessGranted.value =
                com.whocalled.android.util.NotificationAccess.isGranted(getApplication())
        }
    }

    fun setSmsBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            com.whocalled.android.data.Preferences.setSmsBlockingEnabled(getApplication(), enabled)
            _smsBlockingEnabled.value = enabled
        }
    }

    fun setBlockedSmsNotification(enabled: Boolean) {
        viewModelScope.launch {
            com.whocalled.android.data.Preferences.setBlockedSmsNotification(getApplication(), enabled)
            _blockedSmsNotification.value = enabled
        }
    }

    // My reports
    val myReports: StateFlow<List<MyReportEntity>> =
        repo.observeMyReports()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // System call log (for "report from recents")
    private val _systemCalls = MutableStateFlow<List<PhoneCall>>(emptyList())
    val systemCalls: StateFlow<List<PhoneCall>> = _systemCalls

    // Observable permission state so the UI recomposes after the grant.
    private val _callLogPermission = MutableStateFlow(false)
    val callLogPermission: StateFlow<Boolean> = _callLogPermission

    // Async states
    private val _sync = MutableStateFlow<LoadState>(LoadState.Idle)
    val sync: StateFlow<LoadState> = _sync

    /** Live count of numbers fetched during the current sync (for the loader). */
    private val _syncProgress = MutableStateFlow(0)
    val syncProgress: StateFlow<Int> = _syncProgress

    private val _report = MutableStateFlow<LoadState>(LoadState.Idle)
    val report: StateFlow<LoadState> = _report

    // Reassurance stats for the Home banner (best-effort; null hides the banner).
    private val _stats = MutableStateFlow<com.whocalled.android.network.StatsResponse?>(null)
    val stats: StateFlow<com.whocalled.android.network.StatsResponse?> = _stats

    fun loadStats() {
        viewModelScope.launch {
            repo.fetchStats().onSuccess { _stats.value = it }
        }
    }

    // Daily game state for the currently-viewed game (Home/hub card + streak).
    private val _gameState = MutableStateFlow<com.whocalled.android.data.Preferences.GameState?>(null)
    val gameState: StateFlow<com.whocalled.android.data.Preferences.GameState?> = _gameState

    fun refreshGameState(game: String = "defense") {
        viewModelScope.launch {
            _gameState.value = com.whocalled.android.data.Preferences.gameState(getApplication(), game)
        }
    }

    // Daily leaderboard position for the just-finished game (null until submitted).
    private val _gameRank = MutableStateFlow<com.whocalled.android.network.GameScoreResponse?>(null)
    val gameRank: StateFlow<com.whocalled.android.network.GameScoreResponse?> = _gameRank

    fun resetGameRank() { _gameRank.value = null }

    fun submitGameScore(game: String, score: Int, waves: Int) {
        viewModelScope.launch {
            _gameRank.value = null
            repo.submitGameScore(game, score, waves).onSuccess { _gameRank.value = it }
        }
    }

    // TRACE puzzle-of-the-day (or a training grid): fetched from the server so all
    // players face the same grids. null while loading; _traceError on failure.
    private val _tracePuzzle = MutableStateFlow<com.whocalled.android.game.TracePuzzleSet?>(null)
    val tracePuzzle: StateFlow<com.whocalled.android.game.TracePuzzleSet?> = _tracePuzzle
    private val _traceLoading = MutableStateFlow(false)
    val traceLoading: StateFlow<Boolean> = _traceLoading
    private val _traceError = MutableStateFlow(false)
    val traceError: StateFlow<Boolean> = _traceError

    fun loadTraceDaily() {
        viewModelScope.launch {
            _traceLoading.value = true; _traceError.value = false; _tracePuzzle.value = null
            repo.fetchTracePuzzle(com.whocalled.android.game.GameDay.epochDay()).fold(
                onSuccess = { _tracePuzzle.value = it },
                onFailure = { _traceError.value = true },
            )
            _traceLoading.value = false
        }
    }

    fun loadTraceTraining(size: Int) {
        viewModelScope.launch {
            _traceLoading.value = true; _traceError.value = false; _tracePuzzle.value = null
            repo.fetchTraceTraining(size, System.currentTimeMillis()).fold(
                onSuccess = { _tracePuzzle.value = it },
                onFailure = { _traceError.value = true },
            )
            _traceLoading.value = false
        }
    }

    fun clearTracePuzzle() { _tracePuzzle.value = null; _traceError.value = false }

    // Leaderboard screen state (selected game+day board + this device's history).
    private val _leaderboard = MutableStateFlow<com.whocalled.android.network.LeaderboardResponse?>(null)
    val leaderboard: StateFlow<com.whocalled.android.network.LeaderboardResponse?> = _leaderboard
    private val _history = MutableStateFlow<List<com.whocalled.android.network.HistoryEntry>>(emptyList())
    val history: StateFlow<List<com.whocalled.android.network.HistoryEntry>> = _history
    private val _localHistory = MutableStateFlow<List<com.whocalled.android.data.Preferences.LocalDay>>(emptyList())
    val localHistory: StateFlow<List<com.whocalled.android.data.Preferences.LocalDay>> = _localHistory
    private val _leaderboardLoading = MutableStateFlow(false)
    val leaderboardLoading: StateFlow<Boolean> = _leaderboardLoading

    fun loadLeaderboard(game: String, period: String, day: Long) {
        viewModelScope.launch {
            _leaderboardLoading.value = true
            _leaderboard.value = null
            repo.gameLeaderboard(game, period, day).onSuccess { _leaderboard.value = it }
            _leaderboardLoading.value = false
        }
    }

    fun loadHistory(game: String) {
        viewModelScope.launch {
            _localHistory.value = repo.localGameHistory(game) // always available (offline fallback)
            repo.gameHistory(game).onSuccess { _history.value = it.entries }
        }
    }

    // Community shield (shared counter shown compactly on Home).
    private val _community = MutableStateFlow<com.whocalled.android.network.CommunityResponse?>(null)
    val community: StateFlow<com.whocalled.android.network.CommunityResponse?> = _community

    fun loadCommunity() {
        viewModelScope.launch { repo.community().onSuccess { _community.value = it } }
    }

    /** Re-reads the permission state and loads calls if granted. Call on resume + after grant. */
    fun refreshCallLogPermission() {
        val granted = CallLogReader.hasPermission(getApplication())
        _callLogPermission.value = granted
        if (granted) loadSystemCalls()
    }

    fun loadSystemCalls() {
        viewModelScope.launch {
            _systemCalls.value = withContext(Dispatchers.IO) {
                CallLogReader.recentCalls(getApplication())
            }
        }
    }

    /**
     * First-launch auto-sync: if the list has never been synced from the server
     * (only the embedded warm-up is present), fetch it now — no button tap
     * needed. Idempotent: once synced, lastSync != 0 so this no-ops.
     */
    fun autoSyncIfFirstTime() {
        viewModelScope.launch {
            if (_sync.value is LoadState.Loading) return@launch
            if (com.whocalled.android.data.Preferences.lastSyncAt(getApplication()) == 0L) syncNow()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _sync.value = LoadState.Loading
            _syncProgress.value = 0
            repo.syncList { _syncProgress.value = it }.fold(
                onSuccess = {
                    _sync.value = LoadState.Success(
                        if (it == 0) "Déjà à jour ✓ Aucun nouveau numéro."
                        else "Mise à jour terminée 📥 $it numéro(s) actualisé(s).",
                    )
                },
                onFailure = {
                    _sync.value = LoadState.Error("Mise à jour impossible. Vérifiez votre connexion et réessayez.")
                },
            )
        }
    }

    /** Change the watched-country scope: wipe + re-sync the new scope. */
    fun setCountry(dial: String) {
        viewModelScope.launch {
            _sync.value = LoadState.Loading
            _syncProgress.value = 0
            repo.setCountry(dial)
            repo.syncList { _syncProgress.value = it }.fold(
                onSuccess = {
                    _sync.value = LoadState.Success(
                        if (it == 0) "Déjà à jour ✓ Aucun nouveau numéro."
                        else "Mise à jour terminée 📥 $it numéro(s) actualisé(s).",
                    )
                },
                onFailure = {
                    _sync.value = LoadState.Error("Mise à jour impossible. Vérifiez votre connexion et réessayez.")
                },
            )
        }
    }

    private var lastReportedPhone: String? = null

    fun submitReport(phone: String, isSpam: Boolean, category: String? = null) {
        viewModelScope.launch {
            _report.value = LoadState.Loading
            val isRepeat = phone.trim() == lastReportedPhone
            lastReportedPhone = phone.trim()
            repo.submitReport(phone, isSpam, category).fold(
                onSuccess = {
                    _report.value = LoadState.Success(
                        if (isRepeat) "C'est bien noté 😊 Pas besoin d'insister — un signalement suffit !"
                        else "Merci ! Signalement enregistré 🛡️",
                    )
                },
                onFailure = {
                    // Stored locally even if the network failed → make that clear.
                    _report.value = LoadState.Error(
                        "Enregistré localement. Envoi au serveur échoué : ${it.message}",
                    )
                },
            )
        }
    }

    fun deleteReport(phone: String) {
        viewModelScope.launch {
            repo.deleteMyReport(phone)
        }
    }

    fun retryPending() {
        viewModelScope.launch { repo.retryPendingReports() }
    }

    fun clearReportState() {
        _report.value = LoadState.Idle
    }

    // Privacy (RGPD)
    private val _privacy = MutableStateFlow<LoadState>(LoadState.Idle)
    val privacy: StateFlow<LoadState> = _privacy

    fun eraseMyData() {
        viewModelScope.launch {
            _privacy.value = LoadState.Loading
            repo.eraseMyData().fold(
                onSuccess = { _privacy.value = LoadState.Success("Vos signalements ont été supprimés.") },
                onFailure = { _privacy.value = LoadState.Error("Échec de la suppression. Réessayez.") },
            )
        }
    }

    fun eraseNumber(phone: String) {
        viewModelScope.launch {
            _privacy.value = LoadState.Loading
            repo.eraseNumber(phone).fold(
                onSuccess = { _privacy.value = LoadState.Success("Demande de suppression envoyée.") },
                onFailure = { _privacy.value = LoadState.Error("Numéro invalide ou échec.") },
            )
        }
    }

    // Call detail
    private val _detail = MutableStateFlow<CallLogEntity?>(null)
    val detail: StateFlow<CallLogEntity?> = _detail

    private val _lookup = MutableStateFlow<com.whocalled.android.network.LookupResponse?>(null)
    val lookup: StateFlow<com.whocalled.android.network.LookupResponse?> = _lookup

    private val _lookupLoading = MutableStateFlow(false)
    val lookupLoading: StateFlow<Boolean> = _lookupLoading

    // Local ARCEP pattern match for the detail (works offline; source of truth for "ARCEP" badge).
    private val _arcepMatch = MutableStateFlow<com.whocalled.android.data.PatternEntity?>(null)
    val arcepMatch: StateFlow<com.whocalled.android.data.PatternEntity?> = _arcepMatch

    // The number shown on the detail screen (set for both call-log and phone-based entry).
    private val _detailPhone = MutableStateFlow<String?>(null)
    val detailPhone: StateFlow<String?> = _detailPhone

    // User rule for the detail number: "block" | "allow" | null. Drives the
    // block/unblock toggle's active/inactive state.
    private val _detailRule = MutableStateFlow<String?>(null)
    val detailRule: StateFlow<String?> = _detailRule

    fun loadCallDetail(id: Long) {
        viewModelScope.launch {
            val entry = db.callLogDao().findById(id)
            _detail.value = entry
            entry?.let { prepareDetail(it.phone) }
        }
    }

    /** Phone-based detail (from a recent call / a report — no call-log entry). */
    fun loadNumberDetail(phone: String) {
        viewModelScope.launch {
            _detail.value = null
            prepareDetail(phone)
        }
    }

    private fun prepareDetail(phone: String) {
        _detailPhone.value = phone
        // Local pattern match (ARCEP official ranges) — independent of network.
        _arcepMatch.value = com.whocalled.android.util.PatternMatcher
            .firstMatch(phone, db.patternDao().all())
        _detailRule.value = db.userRuleDao().findByPhone(phone)?.kind
        fetchLookup(phone) // live stats, best-effort
    }

    private fun fetchLookup(phone: String) {
        viewModelScope.launch {
            _lookup.value = null
            _lookupLoading.value = true
            repo.lookup(phone).onSuccess { _lookup.value = it }
            _lookupLoading.value = false
        }
    }

    /** Unblock a number from its detail screen (adds a local allow rule). */
    fun unblock(phone: String) {
        viewModelScope.launch {
            repo.allow(phone)
            _detailRule.value = "allow"
        }
    }

    /** (Re-)block a number from its detail screen (adds a local block rule). */
    fun block(phone: String) {
        viewModelScope.launch {
            repo.block(phone)
            _detailRule.value = "block"
        }
    }


    /**
     * Pre-fill the report screen from a shared number/text (Android share sheet).
     * Stored as a one-shot so the Report tab can consume + clear it.
     */
    private val _sharedPhone = MutableStateFlow<String?>(null)
    val sharedPhone: StateFlow<String?> = _sharedPhone

    fun setSharedPhone(raw: String?) {
        _sharedPhone.value = raw
    }

    fun consumeSharedPhone(): String? {
        val v = _sharedPhone.value
        _sharedPhone.value = null
        return v
    }

    /**
     * Deep link from a notification: a call-log id to open on the detail screen.
     * One-shot — the nav layer consumes it once and clears it.
     */
    private val _openCallId = MutableStateFlow<Long?>(null)
    val openCallId: StateFlow<Long?> = _openCallId

    fun requestOpenCall(id: Long) {
        _openCallId.value = id
    }

    fun consumeOpenCall(): Long? {
        val v = _openCallId.value
        _openCallId.value = null
        return v
    }

    /** One-shot deep link from the daily reminder → open the Games hub. */
    private val _openGames = MutableStateFlow(false)
    val openGames: StateFlow<Boolean> = _openGames

    fun requestOpenGames() { _openGames.value = true }
    fun consumeOpenGames() { _openGames.value = false }

    // Diagnostics: API "mode test" (double-tap on the version row in Settings).
    private val _apiTest = MutableStateFlow<ApiTestState>(ApiTestState.Idle)
    val apiTest: StateFlow<ApiTestState> = _apiTest

    fun runApiTest() {
        // Avoid stacking probes if the user double-taps repeatedly.
        if (_apiTest.value is ApiTestState.Testing) return
        viewModelScope.launch {
            _apiTest.value = ApiTestState.Testing
            repo.pingApi().fold(
                onSuccess = { _apiTest.value = ApiTestState.Ok(it) },
                onFailure = { _apiTest.value = ApiTestState.Failed(it.message ?: "Injoignable.") },
            )
        }
    }

    fun clearApiTest() {
        _apiTest.value = ApiTestState.Idle
    }
}

/** Result of the Settings API connectivity probe. */
sealed interface ApiTestState {
    data object Idle : ApiTestState
    data object Testing : ApiTestState
    data class Ok(val latencyMs: Long) : ApiTestState
    data class Failed(val reason: String) : ApiTestState
}
