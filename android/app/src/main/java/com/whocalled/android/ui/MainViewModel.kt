package com.whocalled.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whocalled.android.data.CallLogEntity
import com.whocalled.android.data.MyReportEntity
import com.whocalled.android.data.RepoError
import com.whocalled.android.data.WhoCalledDatabase
import com.whocalled.android.data.WhoCalledRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val filteredCalls = db.callLogDao().observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Home protection stats.
    val blockedCount: StateFlow<Int> =
        db.callLogDao().observeBlockedCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val warnedCount: StateFlow<Int> =
        db.callLogDao().observeWarnedCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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

    /** Our own journal of screened calls (blocked, warned, allowed) + personal votes. */
    val callEvents: StateFlow<List<com.whocalled.android.util.CallEvent>> =
        combine(filteredCalls, myReports) { journal, reports ->
            com.whocalled.android.util.buildCallEvents(journal, reports)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groupedCallEvents: StateFlow<List<com.whocalled.android.util.CallHistorySection>> =
        callEvents
            .map { com.whocalled.android.util.groupCallHistory(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _recentCallHandledAt = MutableStateFlow<Long?>(null)
    private val _recentCallSeenAt = MutableStateFlow<Long?>(null)

    // Minute tick so the 24 h / freshness windows keep moving while the app
    // stays foregrounded (the flows would otherwise only re-emit on new data).
    private val minuteTick = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(60_000L)
        }
    }

    /**
     * The newest unhandled call headlines Home for up to 24 hours — unless a
     * previous session already showed it (then only while < 1 h old). Nullable
     * timestamps prevent a brief stale prompt while DataStore loads.
     */
    val recentCallPrompt: StateFlow<com.whocalled.android.util.RecentCallPrompt?> =
        combine(callEvents, _recentCallHandledAt, _recentCallSeenAt, minuteTick) { calls, handledAt, seenAt, _ ->
            if (handledAt == null || seenAt == null) {
                null
            } else {
                com.whocalled.android.util.buildRecentCallPrompt(calls, handledAt, seenAt)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            _recentCallHandledAt.value =
                com.whocalled.android.data.Preferences.recentCallHandledAt(getApplication())
            _recentCallSeenAt.value =
                com.whocalled.android.data.Preferences.recentCallSeenAt(getApplication())
        }
        // Push any report that failed to sync (offline at the time) — the
        // local-first design exists exactly for this, but nothing called it.
        viewModelScope.launch { repo.retryPendingReports() }
    }

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

    fun markRecentCallHandled(call: com.whocalled.android.util.CallEvent) {
        markRecentCallHandledAt(call.timestamp)
    }

    /**
     * Called when the app leaves the foreground: whatever the Home card was
     * showing counts as "seen but not acted on" — next session it only
     * re-headlines while still fresh (the call stays in the Calls tab).
     */
    fun commitRecentCallSeen() {
        val shown = recentCallPrompt.value?.call ?: return
        val seenAt = maxOf(_recentCallSeenAt.value ?: 0L, shown.timestamp)
        _recentCallSeenAt.value = seenAt
        viewModelScope.launch {
            com.whocalled.android.data.Preferences.setRecentCallSeenAt(getApplication(), seenAt)
        }
    }

    private fun markRecentCallHandledAt(timestamp: Long) {
        val handledAt = maxOf(_recentCallHandledAt.value ?: 0L, timestamp)
        _recentCallHandledAt.value = handledAt
        viewModelScope.launch {
            com.whocalled.android.data.Preferences.setRecentCallHandledAt(
                getApplication(),
                handledAt,
            )
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
                onFailure = { _sync.value = LoadState.Error(syncErrorMessage(it)) },
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
                onFailure = { _sync.value = LoadState.Error(syncErrorMessage(it)) },
            )
        }
    }

    // Last (normalized phone, vote, category) sent, so a change of mind or a
    // category refinement is acknowledged instead of "pas besoin d'insister".
    private var lastReport: Triple<String, String, String?>? = null

    /**
     * Why the sync failed, in the user's terms. Blaming the connection for a 429
     * sent people chasing a network problem that wasn't there — the API test in
     * Réglages hits /health, which is never rate-limited, so it stayed green
     * while the list endpoint was refusing requests.
     */
    private fun syncErrorMessage(error: Throwable): String = when {
        error is RepoError.Server && error.code == 429 ->
            "Trop de mises à jour depuis ce réseau. Patientez quelques minutes puis réessayez."
        error is RepoError.Server ->
            "Le serveur a refusé la mise à jour (erreur ${error.code}). Réessayez plus tard."
        error is RepoError.Network ->
            "Mise à jour impossible. Vérifiez votre connexion et réessayez."
        else ->
            "Mise à jour impossible. Réessayez dans un instant."
    }

    fun submitReport(phone: String, isSpam: Boolean, category: String? = null) {
        viewModelScope.launch {
            _report.value = LoadState.Loading
            val normalized = com.whocalled.android.util.PhoneNormalizer.normalize(phone)
            val vote = if (isSpam) "spam" else "legit"
            val prev = lastReport?.takeIf { normalized != null && it.first == normalized }
            val isRepeat = prev != null && prev.second == vote && prev.third == category
            val isFlip = prev != null && prev.second != vote
            val isRefine = prev != null && prev.second == vote && prev.third != category
            lastReport = normalized?.let { Triple(it, vote, category) }
            repo.submitReport(phone, isSpam, category).fold(
                onSuccess = {
                    _report.value = LoadState.Success(
                        when {
                            isRepeat -> "C'est bien noté 😊 Pas besoin d'insister — un signalement suffit !"
                            isFlip -> "Avis mis à jour ✓"
                            isRefine -> "Merci, précision enregistrée ✓"
                            else -> "Merci ! Signalement enregistré 🛡️"
                        },
                    )
                    if (normalized != null && normalized == _detailPhone.value) {
                        fetchLookup(normalized)
                    }
                },
                onFailure = { _report.value = LoadState.Error(reportErrorMessage(it)) },
            )
        }
    }

    /**
     * Report failures in the user's terms. "Enregistré localement" is only
     * true for network/server failures (the row was saved before the push) —
     * an invalid number saves NOTHING and must say so.
     */
    private fun reportErrorMessage(error: Throwable): String = when {
        error is RepoError.InvalidPhone ->
            "Numéro invalide — vérifiez le format."
        error is RepoError.Server && error.code == 429 ->
            "Limite quotidienne de signalements atteinte. Votre avis est gardé sur l'appareil et sera renvoyé automatiquement."
        else ->
            "Enregistré localement. Envoi au serveur échoué : ${error.message}"
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
        resetDetailState()
        viewModelScope.launch {
            val entry = db.callLogDao().findById(id)
            _detail.value = entry
            entry?.let {
                markRecentCallHandledAt(it.timestamp)
                prepareDetail(it.phone)
            }
        }
    }

    /** Phone-based detail (from a recent call / a report — no call-log entry). */
    fun loadNumberDetail(phone: String) {
        resetDetailState()
        viewModelScope.launch {
            prepareDetail(phone)
        }
    }

    private fun resetDetailState() {
        _detail.value = null
        _detailPhone.value = null
        _lookup.value = null
        _lookupLoading.value = false
        _arcepMatch.value = null
        _detailRule.value = null
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

    /** One-shot deep link from a notification → a number's page (e.g. SMS sender). */
    private val _openPhone = MutableStateFlow<String?>(null)
    val openPhone: StateFlow<String?> = _openPhone

    fun requestOpenPhone(phone: String) { _openPhone.value = phone }

    fun consumeOpenPhone(): String? {
        val v = _openPhone.value
        _openPhone.value = null
        return v
    }

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
