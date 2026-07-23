package com.whocalled.android.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * TRACE — daily deduction puzzle engine (pure Kotlin, no Android/Compose deps so
 * it can be unit-tested and rendered by any surface). A "Star Battle"/Queens
 * variant reskinned as a spam hunt:
 *
 *   • one fraudster per row, per column and per area-code region
 *   • two fraudsters never touch (diagonals included)
 *   • tap a cell to cycle: empty → SAFE (✓) → FRAUDSTER (😈) → empty
 *
 * A RANKED run is the day's 5-grid SPRINT (sizes 5→9) with 3 SHARED lives: three
 * wrong placements across the whole run and it's over. Score banks per solved
 * grid (base×size − time − hints − errors + solve bonus); `gridsSolved` is the
 * leaderboard "waves". Grids come from the server; the engine just plays them.
 */
class TraceEngine {
    companion object {
        const val MAX_LIVES = 3
        const val HINTS_PER_GRID = 3
        const val START_SCORE_BASE = 1000 // × (size − 4)
        const val HINT_COST = 150
        const val ERROR_COST = 200
        const val TIME_DECAY = 5 // points lost per second on the current grid
        const val SOLVE_BONUS = 250
        const val PLACE_BONUS = 100 // per correctly placed fraudster (first time per cell)

        /**
         * Vivid, maximally-separated region colour for [index] among [count].
         * Algorithm: OKLCH with even hue spacing (biggest minimum hue gap) PLUS
         * alternating lightness, so even neighbouring regions differ in BOTH hue
         * and luminance — unambiguous, and readable for colour-blind players.
         * Deterministic → identical on every device, so the daily puzzle looks
         * the same for everyone (fair, cross-platform).
         */
        fun regionColor(index: Int, count: Int): Long {
            val n = if (count < 1) 1 else count
            val i = ((index % n) + n) % n
            val h = i * 2.0 * PI / n // radians, evenly spaced around the wheel
            val l = if (i % 2 == 0) 0.66 else 0.78 // alternate luminance
            val c = 0.16
            val a = c * cos(h)
            val b = c * sin(h)
            // OKLCH → OKLab → linear LMS (cube) → linear sRGB → sRGB gamma encode.
            val l1 = l + 0.3963377774 * a + 0.2158037573 * b
            val m1 = l - 0.1055613458 * a - 0.0638541728 * b
            val s1 = l - 0.0894841775 * a - 1.2914855480 * b
            val lr = l1 * l1 * l1
            val mr = m1 * m1 * m1
            val sr = s1 * s1 * s1
            val r = 4.0767416621 * lr - 3.3077115913 * mr + 0.2309699292 * sr
            val g = -1.2684380046 * lr + 2.6097574011 * mr - 0.3413193965 * sr
            val bb = -0.0041960863 * lr - 0.7034186147 * mr + 1.7076147010 * sr
            fun enc(x: Double): Double =
                if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1.0 / 2.4) - 0.055
            val rr = (enc(r).coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
            val gg = (enc(g).coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
            val bl = (enc(bb).coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
            return 0xFF000000L or (rr.toLong() shl 16) or (gg.toLong() shl 8) or bl.toLong()
        }

        // Region colours are generated algorithmically (see [regionColor]) so the
        // palette is always maximally distinct for the grid's region count — no
        // near-duplicate pinks like a fixed list would produce for some counts.
        // Cosmetic fraudster per grid (emoji + name), tuned per grid index.
        val VILLAINS = listOf(
            "😈" to "Diablotin", "👿" to "Démon", "🦹" to "Super-vilain", "🥷" to "Ninja",
            "🤖" to "Robot spam", "👾" to "Alien", "🃏" to "Arnaqueur", "🕵️" to "Usurpateur",
        )
        // Brand palette (shared with DEFENSE for a consistent look).
        const val CY = 0xFF2BE0C6L
        const val BL = 0xFF4C9BFFL
        const val SPAM = 0xFFFF5D6CL
        const val SAFE = 0xFF37E29AL
        const val AMBER = 0xFFFFC24BL
        const val INK = 0xFFEAF0FFL
        const val OK = 0xFF22C55EL
    }

    // config -----------------------------------------------------------------
    private var grids: List<TraceGrid> = emptyList()
    var ranked: Boolean = true; private set

    // run state --------------------------------------------------------------
    var started = false; private set
    var runOver = false; private set
    var runWon = false; private set // completed all grids
    var gridIndex = 0; private set
    var lives = MAX_LIVES; private set
    var solvedCount = 0; private set // grids fully solved this run
    private var banked = 0 // score locked in from solved grids

    // current grid state -----------------------------------------------------
    var cells: Array<IntArray> = arrayOf(); private set // 0 empty, 1 safe(✓), 2 spam(😈)
    var hints = HINTS_PER_GRID; private set
    var hintsUsed = 0; private set
    var errors = 0; private set
    var elapsed = 0; private set // seconds on the current grid
    var gridWon = false; private set // current grid solved (brief pause before next)
    var revealSolution = false; private set // reveal answer after a life-losing end
    val revealed = HashSet<Int>() // cells auto-placed by a hint (key = r*100+c)
    var villainIdx = 0; private set
    var goodPlaced = 0; private set // correctly placed fraudsters (bonus points)
    private val credited = HashSet<Int>() // cells already credited (no re-placement farming)

    // transient effect marker — the UI animates on each new [eventSeq].
    var lastEventType = 0; private set // 0 none · 1 good · 2 error · 3 hint
    var lastEventCell = -1; private set // key(r,c)
    var lastEventPoints = 0; private set // score delta to show in the feedback toast
    var eventSeq = 0; private set // bumps on every good/error/hint (animation key)

    // derived views ----------------------------------------------------------
    val gridCount get() = grids.size
    val n get() = grids.getOrNull(gridIndex)?.n ?: 5
    val region get() = grids[gridIndex].region
    /** Number of distinct area-code regions in the current grid (palette sizing). */
    val regionCount: Int
        get() {
            val g = grids.getOrNull(gridIndex) ?: return 5
            val maxId = g.region.maxOfOrNull { row -> row.maxOrNull() ?: -1 } ?: return 5
            return (maxId + 1).coerceAtLeast(1)
        }
    val solution get() = grids[gridIndex].solution
    val villain get() = VILLAINS[villainIdx]
    val active get() = started && !runOver && !gridWon
    fun key(r: Int, c: Int) = r * 100 + c

    fun setPuzzles(g: List<TraceGrid>, ranked: Boolean) {
        grids = g
        this.ranked = ranked
        gridIndex = 0
        // Initialise a fresh grid so the board can render behind the intro overlay
        // (taps are gated by `active`, which stays false until start()).
        if (g.isNotEmpty()) loadGrid()
    }

    fun start() {
        if (grids.isEmpty()) return
        started = true; runOver = false; runWon = false
        gridIndex = 0; lives = MAX_LIVES; solvedCount = 0; banked = 0
        loadGrid()
    }

    private fun loadGrid() {
        val g = grids[gridIndex]
        cells = Array(g.n) { IntArray(g.n) }
        hints = HINTS_PER_GRID; hintsUsed = 0; errors = 0; elapsed = 0
        goodPlaced = 0; credited.clear()
        gridWon = false; revealSolution = false; revealed.clear()
        villainIdx = (gridIndex * 2 + 1) % VILLAINS.size
    }

    /** One-second timer (called by the UI while [active]) — drives score decay. */
    fun tick() { if (active) elapsed++ }

    /** Live score for the CURRENT grid, clamped to ≥ 0. */
    fun liveScore(): Int {
        val base = START_SCORE_BASE * (n - 4)
        return maxOf(0, base + goodPlaced * PLACE_BONUS - elapsed * TIME_DECAY - hintsUsed * HINT_COST - errors * ERROR_COST)
    }

    /** Total shown in the HUD: banked grids + the current grid's live score. */
    fun displayScore(): Int = banked + if (gridWon) 0 else liveScore()

    /** Final score to submit to the leaderboard. */
    fun finalScore(): Int = banked

    /** Cells currently in a rule conflict (for red highlighting). */
    fun errorCells(): Set<Int> {
        val nn = n
        val rowC = IntArray(nn); val colC = IntArray(nn); val regC = IntArray(nn)
        val spam = ArrayList<Int>()
        for (r in 0 until nn) for (c in 0 until nn) if (cells[r][c] == 2) {
            rowC[r]++; colC[c]++; regC[region[r][c]]++; spam.add(key(r, c))
        }
        val out = HashSet<Int>()
        for (r in 0 until nn) for (c in 0 until nn) {
            if (cells[r][c] != 2) continue
            if (solution[r] != c) { out.add(key(r, c)); continue }
            if (rowC[r] > 1 || colC[c] > 1 || regC[region[r][c]] > 1) { out.add(key(r, c)); continue }
            adj@ for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = r + dr; val ncc = c + dc
                if (nr in 0 until nn && ncc in 0 until nn && cells[nr][ncc] == 2) { out.add(key(r, c)); break@adj }
            }
        }
        return out
    }

    private fun isSolved(): Boolean {
        val nn = n
        var total = 0
        val rowC = IntArray(nn); val colC = IntArray(nn); val regC = IntArray(nn)
        val spam = ArrayList<Pair<Int, Int>>()
        for (r in 0 until nn) for (c in 0 until nn) if (cells[r][c] == 2) {
            total++; rowC[r]++; colC[c]++; regC[region[r][c]]++; spam.add(r to c)
        }
        if (total != nn) return false
        if (rowC.any { it != 1 } || colC.any { it != 1 } || regC.any { it != 1 }) return false
        for ((r, c) in spam) for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr; val ncc = c + dc
            if (nr in 0 until nn && ncc in 0 until nn && cells[nr][ncc] == 2) return false
        }
        return true
    }

    /** Tap a cell: empty → SAFE(✓) → FRAUDSTER(😈) → empty. */
    fun cycle(r: Int, c: Int) {
        if (!active) return
        // A correctly placed fraudster is frozen: no accidental erase, no farming.
        if (cells[r][c] == 2 && solution[r] == c) return
        val v = (cells[r][c] + 1) % 3
        cells[r][c] = v
        if (v == 2) {
            if (solution[r] != c) {
                errors++; lives--
                lastEventType = 2; lastEventCell = key(r, c); lastEventPoints = -ERROR_COST; eventSeq++
                if (lives <= 0) { endRun(reveal = true); return }
            } else {
                val k = key(r, c)
                lastEventPoints = if (credited.add(k)) { goodPlaced++; PLACE_BONUS } else 0
                lastEventType = 1; lastEventCell = k; eventSeq++
            }
        }
        if (isSolved()) solveGrid()
    }

    /**
     * Swipe-paint: mark an EMPTY cell safe (✗) without cycling — used when the
     * player drags across a row/column to lay several crosses in one gesture.
     * Never touches placed marks, so a swipe can't cost points or lives.
     */
    fun paintSafe(r: Int, c: Int) {
        if (!active || cells[r][c] != 0) return
        cells[r][c] = 1
    }

    /** Reveal a real, not-yet-placed fraudster. Costs points, never a life. */
    fun useHint() {
        if (!active || hints <= 0) return
        for (r in 0 until n) {
            if (cells[r][solution[r]] != 2) {
                val c = solution[r]
                cells[r][c] = 2
                revealed.add(key(r, c))
                hints--; hintsUsed++
                lastEventType = 3; lastEventCell = key(r, c); eventSeq++
                if (isSolved()) solveGrid()
                return
            }
        }
    }

    private fun solveGrid() {
        gridWon = true
        solvedCount++
        banked += liveScore() + SOLVE_BONUS
    }

    /**
     * Advance after the win pause: load the next grid, or finish the run when the
     * last grid was cleared. Called by the UI once the celebration has played.
     */
    fun nextGrid() {
        if (!gridWon || runOver) return
        if (gridIndex + 1 < grids.size) {
            gridIndex++
            loadGrid()
        } else {
            runWon = true
            endRun(reveal = false)
        }
    }

    private fun endRun(reveal: Boolean) {
        runOver = true
        revealSolution = reveal
    }
}
