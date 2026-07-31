package com.whocalled.android.network

/** POST /reports body. 100% anonymous: only an opaque deviceId, no PII. */
data class ReportRequest(
    val phone: String,
    val deviceId: String,
    val vote: String, // spam | legit
    val category: String? = null,
    val locale: String = "fr",
)

/** A scored number returned by GET /lists. */
data class ListNumber(
    val phone: String,
    val status: String, // block | warn
    val spamScore: Int,
    val category: String,
    val source: String = "community", // community | arcep
    val updatedAt: String, // ISO-8601
)

/** A wildcard pattern (ARCEP / operator ranges) returned by GET /lists. */
data class ListPattern(
    val pattern: String, // digits + trailing '#'
    val status: String, // block | warn
    val category: String,
    val source: String, // arcep | community
    val name: String? = null,
    val updatedAt: String,
)

/** GET /lists response (delta sync). */
data class ListResponse(
    val serverTime: String,
    val count: Int,
    val next: String?,
    val numbers: List<ListNumber>,
    val patterns: List<ListPattern> = emptyList(),
    // Phones to drop from the local cache (healed / deleted upstream).
    val deleted: List<String> = emptyList(),
)

/** Frequency of spam reports over rolling windows. */
data class LookupFrequency(
    val last24h: Int = 0,
    val last7d: Int = 0,
    val last30d: Int = 0,
    val last1y: Int = 0,
)

/** Dominant community reason ("why did it call?") for a number. */
data class TopReason(
    val category: String, // telemarketing | scam | robocall | unknown
    val count: Int = 0,
    val share: Int = 0, // 0..100 — share of spam reports
)

data class OfficialPattern(
    val pattern: String,
    val status: String,
    val category: String,
    val name: String? = null,
)

/** GET /lookup/:phone enriched response (detail screen). */
data class LookupResponse(
    val phone: String,
    val spamScore: Int,
    val status: String,
    val confidence: Int? = 0,
    val confidenceLevel: String? = null, // none | low | medium | high | official
    val category: String? = null,
    val source: String? = null, // none | community | arcep | mixed
    val reportCountSpam: Int = 0,
    val reportCountLegit: Int = 0,
    val userVote: String? = null,
    val frequency: LookupFrequency = LookupFrequency(),
    val topReason: TopReason? = null,
    val firstReportedAt: String? = null,
    val lastReportedAt: String? = null,
    val officialPattern: OfficialPattern? = null,
)

/** One number in the "qui montent" trending list. */
data class TrendingNumber(
    val phone: String,
    val reportCount: Int = 0,
    val last24h: Int = 0,
    val velocity: Int = 0,
    val spamScore: Int = 0,
    val status: String = "unknown",
    val category: String = "unknown",
    val source: String = "community",
    val topReason: TopReason? = null,
)

/** POST /game/score body + response (anonymous daily leaderboard, score-based). */
data class GameScoreRequest(
    val deviceId: String,
    val score: Int,
    val waves: Int,
    val day: Long,
    val game: String = "defense", // defense | trace
)

data class GameScoreResponse(
    val players: Int = 0,
    val bestScore: Int = 0,
    val medianScore: Int = 0,
    val yourScore: Int = 0,
    val rank: Int = 0,
    val topPercent: Int = 0,
)

/** GET /game/leaderboard — anonymous board for a day + your position. */
data class LeaderboardEntry(val rank: Int = 0, val score: Int = 0, val waves: Int = 0)
data class LeaderboardYou(val score: Int = 0, val waves: Int = 0, val rank: Int = 0, val topPercent: Int = 0)
data class LeaderboardResponse(
    val day: Long = 0,
    val period: String = "day",
    val players: Int = 0,
    val bestScore: Int = 0,
    val medianScore: Int = 0,
    val top: List<LeaderboardEntry> = emptyList(),
    val you: LeaderboardYou? = null,
)

/** GET /game/history — this device's recent daily results with per-day rank. */
data class HistoryEntry(
    val day: Long = 0,
    val score: Int = 0,
    val waves: Int = 0,
    val rank: Int = 0,
    val players: Int = 0,
    val topPercent: Int = 0,
)
data class HistoryResponse(val entries: List<HistoryEntry> = emptyList())

/** GET /stats/community — shared "community shield" figures. */
data class CommunityResponse(val total: Long = 0, val week: Long = 0, val goal: Long = 0)

/** GET /stats response — public reassurance figures shown on the Home banner. */
data class StatsResponse(
    val serverTime: String = "",
    val coveredNumbers: Long = 0, // big "numéros couverts" headline
    val communityCount: Long = 0, // honest count of crowdsourced numbers
    val arcepPatternCount: Long = 0, // number of ARCEP block ranges
    val lastUpdate: String? = null, // ISO-8601 of freshest data, or null
    val country: String? = null, // echoed dial code, or null if global
    val countryNumbers: Long? = null, // numbers for that country
    val countryArcepPatternCount: Long? = null, // ARCEP ranges for that country
)

/** GET /trending response. */
data class TrendingResponse(
    val serverTime: String = "",
    val window: Int = 7,
    val count: Int = 0,
    val numbers: List<TrendingNumber> = emptyList(),
)

/** GET /lists/access — short-lived token gating the list download. */
data class ListAccessResponse(
    val token: String = "",
    val expiresIn: Int = 0,
)
