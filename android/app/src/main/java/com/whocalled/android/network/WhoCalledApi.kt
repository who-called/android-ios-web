package com.whocalled.android.network

import com.google.gson.Gson
import com.whocalled.android.BuildConfig
import com.whocalled.android.game.TracePuzzleSet
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal HTTP client for the who-called API using HttpURLConnection + Gson.
 * No third-party HTTP dependency — keeps the app light (like Saracroche's approach).
 */
class WhoCalledApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val gson: Gson = Gson(),
) {

    /**
     * Connectivity probe used by the Settings "mode test" (double-tap version).
     * Hits the root /health endpoint (note: it lives at the host root, NOT under
     * /api/v1) and returns the round-trip latency in ms. Throws on any failure.
     */
    fun ping(): Long {
        // baseUrl is ".../api/v1" → strip the API prefix to reach the host /health.
        val healthUrl = baseUrl.replace(Regex("/api/v\\d+/?$"), "") + "/health"
        val start = System.currentTimeMillis()
        request(URL(healthUrl), "GET", null, Map::class.java)
        return System.currentTimeMillis() - start
    }

    /** Shared community-shield figures (total + this week + soft weekly goal). */
    fun community(): CommunityResponse {
        val url = URL("$baseUrl/stats/community")
        return request(url, "GET", null, CommunityResponse::class.java)
    }

    /** Public reassurance stats. `country` (dial code) adds that country's counts. */
    fun stats(country: String? = null): StatsResponse {
        val countryParam = country?.takeIf { it.isNotBlank() }
            ?.let { "?country=${URLEncoder.encode(it, "UTF-8")}" } ?: ""
        val url = URL("$baseUrl/stats$countryParam")
        return request(url, "GET", null, StatsResponse::class.java)
    }

    /**
     * Short-lived access token gating GET /lists (the download URL is
     * deliberately "provisoire" — dead a few minutes later if replayed).
     */
    fun listsAccess(): ListAccessResponse {
        val url = URL("$baseUrl/lists/access")
        return request(url, "GET", null, ListAccessResponse::class.java)
    }

    /**
     * Delta sync. `since` = ISO-8601; null on first launch (warm-up = full list).
     * `country` = E.164 dial code (e.g. "33") to scope the list; null/blank = worldwide.
     * `token` = short-lived token from [listsAccess].
     */
    fun fetchList(since: String?, country: String? = null, limit: Int = 5000, token: String): ListResponse {
        val sinceParam = since?.let { "&since=${URLEncoder.encode(it, "UTF-8")}" } ?: ""
        val countryParam = country?.takeIf { it.isNotBlank() }
            ?.let { "&country=${URLEncoder.encode(it, "UTF-8")}" } ?: ""
        val tokenParam = "&token=${URLEncoder.encode(token, "UTF-8")}"
        val url = URL("$baseUrl/lists?limit=$limit$sinceParam$countryParam$tokenParam")
        return request(url, "GET", null, ListResponse::class.java)
    }

    /** Live lookup for a single number. */
    fun lookup(phone: String): LookupResponse {
        val url = URL("$baseUrl/lookup/${URLEncoder.encode(phone, "UTF-8")}")
        return request(url, "GET", null, LookupResponse::class.java)
    }

    /** "Numéros qui montent" — most-reported numbers ramping up recently. */
    fun trending(windowDays: Int = 7, limit: Int = 25): TrendingResponse {
        val url = URL("$baseUrl/trending?window=$windowDays&limit=$limit")
        return request(url, "GET", null, TrendingResponse::class.java)
    }

    /** Submit an anonymous report. */
    fun report(body: ReportRequest) {
        val url = URL("$baseUrl/reports")
        request(url, "POST", gson.toJson(body), Map::class.java)
    }

    /** Submit a daily-game score for `game` → returns today's leaderboard position. */
    fun submitGameScore(deviceId: String, score: Int, waves: Int, day: Long, game: String = "defense"): GameScoreResponse {
        val url = URL("$baseUrl/game/score")
        val body = GameScoreRequest(deviceId, score, waves, day, game)
        return request(url, "POST", gson.toJson(body), GameScoreResponse::class.java)
    }

    /** Anonymous leaderboard for a game + period (day|week|all) + your position. */
    fun gameLeaderboard(period: String, day: Long, deviceId: String, game: String = "defense"): LeaderboardResponse {
        val url = URL("$baseUrl/game/leaderboard?period=$period&day=$day&game=$game&deviceId=${URLEncoder.encode(deviceId, "UTF-8")}")
        return request(url, "GET", null, LeaderboardResponse::class.java)
    }

    /** This device's recent daily results for a game, with per-day rank. */
    fun gameHistory(deviceId: String, days: Int = 14, game: String = "defense"): HistoryResponse {
        val url = URL("$baseUrl/game/history?deviceId=${URLEncoder.encode(deviceId, "UTF-8")}&days=$days&game=$game")
        return request(url, "GET", null, HistoryResponse::class.java)
    }

    /** The day's TRACE sprint (5 grids), identical for every player. */
    fun tracePuzzle(day: Long): TracePuzzleSet {
        val url = URL("$baseUrl/game/puzzle?game=trace&day=$day")
        return request(url, "GET", null, TracePuzzleSet::class.java)
    }

    /** A single unranked TRACE training grid of the requested size. */
    fun traceTrainingPuzzle(size: Int, seed: Long): TracePuzzleSet {
        val url = URL("$baseUrl/game/puzzle?game=trace&mode=training&size=$size&seed=$seed")
        return request(url, "GET", null, TracePuzzleSet::class.java)
    }

    /** RGPD: erase all reports made from this device. */
    fun eraseDevice(deviceId: String) {
        val url = URL("$baseUrl/privacy/device/${URLEncoder.encode(deviceId, "UTF-8")}")
        request(url, "DELETE", null, Map::class.java)
    }

    /** RGPD: request erasure of a specific number from the community database. */
    fun eraseNumber(phone: String) {
        val url = URL("$baseUrl/privacy/number/${URLEncoder.encode(phone, "UTF-8")}")
        request(url, "DELETE", null, Map::class.java)
    }

    private fun <T> request(url: URL, method: String, body: String?, type: Class<T>): T {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw ApiException(code, text)
            }
            return gson.fromJson(text, type)
        } finally {
            conn.disconnect()
        }
    }
}

class ApiException(val code: Int, message: String) : Exception("HTTP $code: $message")
