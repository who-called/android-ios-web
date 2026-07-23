package com.whocalled.android.game

/**
 * One TRACE grid, served by the backend so every player faces the SAME puzzle
 * (fair leaderboard, no cross-platform RNG drift).
 *
 *  - [region] `[r][c]` = area-code id (0..n-1) → the coloured zones.
 *  - [solution] `[r]` = column of the fraudster in row r (the unique answer).
 */
data class TraceGrid(
    val n: Int = 0,
    val region: List<List<Int>> = emptyList(),
    val solution: List<Int> = emptyList(),
)

/** GET /game/puzzle response: the day's sprint (5 grids) or a training grid. */
data class TracePuzzleSet(
    val day: Long = 0,
    val game: String = "trace",
    val mode: String? = null,
    val grids: List<TraceGrid> = emptyList(),
)
