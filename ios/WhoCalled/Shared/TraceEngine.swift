import Foundation
import SwiftUI

/// TRACE — daily deduction puzzle engine (pure logic, no view deps; the view
/// drives redraws). A "Star Battle"/Queens variant reskinned as a spam hunt:
///
///   • one fraudster per row, per column and per area-code region
///   • two fraudsters never touch (diagonals included)
///   • tap a cell to cycle: empty → SAFE (✓) → FRAUDSTER (😈) → empty
///
/// A RANKED run is the day's 5-grid SPRINT (sizes 5→9) with 3 SHARED lives: three
/// wrong placements across the whole run and it's over. Score banks per solved
/// grid (base×size − time − hints − errors + solve bonus); `solvedCount` is the
/// leaderboard "waves". Grids come from the server; the engine just plays them.
/// Mirrors the Android `TraceEngine`.
final class TraceEngine {
  static let maxLives = 3
  static let hintsPerGrid = 3
  static let startScoreBase = 1000  // × (size − 4)
  static let hintCost = 150
  static let errorCost = 200
  static let timeDecay = 5  // points lost per second on the current grid
  static let solveBonus = 250
  static let placeBonus = 100  // per correctly placed fraudster (first time per cell)

  /// Vivid, maximally-separated region colour for `index` among `count`.
  /// Algorithm: OKLCH with even hue spacing (biggest minimum hue gap) PLUS
  /// alternating lightness, so even neighbouring regions differ in BOTH hue
  /// and luminance — unambiguous, and readable for colour-blind players.
  /// Deterministic → identical on every device, so the daily puzzle looks the
  /// same for everyone (fair, cross-platform).
  static func regionColor(_ index: Int, _ count: Int) -> Color {
    let n = max(1, count)
    let i = ((index % n) + n) % n
    let h = Double(i) * 2.0 * .pi / Double(n)  // radians, evenly spaced around the wheel
    let L = (i % 2 == 0) ? 0.66 : 0.78  // alternate luminance
    let C = 0.16
    let a = C * cos(h)
    let b = C * sin(h)
    // OKLCH → OKLab → linear LMS (cube) → linear sRGB → sRGB gamma encode.
    let l1 = L + 0.3963377774*a + 0.2158037573*b
    let m1 = L - 0.1055613458*a - 0.0638541728*b
    let s1 = L - 0.0894841775*a - 1.2914855480*b
    let lr = l1*l1*l1
    let mr = m1*m1*m1
    let sr = s1*s1*s1
    let r = 4.0767416621*lr - 3.3077115913*mr + 0.2309699292*sr
    let g = -1.2684380046*lr + 2.6097574011*mr - 0.3413193965*sr
    let bl = -0.0041960863*lr - 0.7034186147*mr + 1.7076147010*sr
    func enc(_ x: Double) -> Double {
      if x <= 0.0031308 { return 12.92 * x }
      return 1.055 * pow(x, 1.0/2.4) - 0.055
    }
    return Color(red: max(0, min(1, enc(r))),
                 green: max(0, min(1, enc(g))),
                 blue: max(0, min(1, enc(bl))))
  }

  /// Region colours are generated algorithmically (see `regionColor`) so the
  /// palette is always maximally distinct for the grid's region count — no
  /// near-duplicate pinks like a fixed list would produce for some counts.

  /// Cosmetic fraudster per grid (emoji + name).
  static let villains: [(emoji: String, name: String)] = [
    ("😈", "Diablotin"), ("👿", "Démon"), ("🦹", "Super-vilain"), ("🥷", "Ninja"),
    ("🤖", "Robot spam"), ("👾", "Alien"), ("🃏", "Arnaqueur"), ("🕵️", "Usurpateur"),
  ]
  // Brand palette (shared feel with DEFENSE).
  static let cy = Color(red: 0.169, green: 0.878, blue: 0.776)
  static let ink = Color(red: 0.918, green: 0.941, blue: 1.0)
  static let ok = Color(red: 0.133, green: 0.773, blue: 0.369)
  static let spam = Color(red: 1.0, green: 0.365, blue: 0.424)

  // config
  private var grids: [TraceGrid] = []
  private(set) var ranked = true

  // run state
  private(set) var started = false
  private(set) var runOver = false
  private(set) var runWon = false
  private(set) var gridIndex = 0
  private(set) var lives = maxLives
  private(set) var solvedCount = 0
  private var banked = 0

  // current grid state
  private(set) var cells: [[Int]] = []  // 0 empty, 1 safe(✓), 2 spam(😈)
  private(set) var hints = hintsPerGrid
  private(set) var hintsUsed = 0
  private(set) var errors = 0
  private(set) var elapsed = 0
  private(set) var gridWon = false
  private(set) var revealSolution = false
  private(set) var revealed: Set<Int> = []
  private(set) var villainIdx = 0
  private(set) var goodPlaced = 0  // correctly placed fraudsters (bonus points)
  private var credited: Set<Int> = []  // cells already credited (no re-placement farming)

  // transient effect marker — the view animates on each new eventSeq.
  private(set) var lastEventType = 0  // 0 none · 1 good · 2 error · 3 hint
  private(set) var lastEventCell = -1  // key(r,c)
  private(set) var lastEventPoints = 0  // score delta to show in the feedback toast
  private(set) var eventSeq = 0

  // leaderboard position after submission (shown in the game-over overlay)
  var rank: GameScoreResponseDTO?

  var gridCount: Int { grids.count }
  var n: Int { gridIndex < grids.count ? grids[gridIndex].n : 5 }
  var region: [[Int]] { grids[gridIndex].region }
  /// Number of distinct area-code regions in the current grid (palette sizing).
  var regionCount: Int {
    guard gridIndex < grids.count else { return 5 }
    let maxId = grids[gridIndex].region.flatMap { $0 }.max() ?? 0
    return max(maxId + 1, 1)
  }
  var solution: [Int] { grids[gridIndex].solution }
  var villain: (emoji: String, name: String) { TraceEngine.villains[villainIdx] }
  var active: Bool { started && !runOver && !gridWon }
  func key(_ r: Int, _ c: Int) -> Int { r * 100 + c }

  func setPuzzles(_ g: [TraceGrid], ranked: Bool) {
    grids = g
    self.ranked = ranked
    gridIndex = 0
    // Init a fresh grid so the board can render behind the intro overlay
    // (taps are gated by `active`, false until start()).
    if !g.isEmpty { loadGrid() }
  }

  func start() {
    guard !grids.isEmpty else { return }
    started = true; runOver = false; runWon = false
    gridIndex = 0; lives = TraceEngine.maxLives; solvedCount = 0; banked = 0
    rank = nil
    loadGrid()
  }

  private func loadGrid() {
    let g = grids[gridIndex]
    cells = Array(repeating: Array(repeating: 0, count: g.n), count: g.n)
    hints = TraceEngine.hintsPerGrid; hintsUsed = 0; errors = 0; elapsed = 0
    goodPlaced = 0; credited.removeAll()
    gridWon = false; revealSolution = false; revealed.removeAll()
    villainIdx = (gridIndex * 2 + 1) % TraceEngine.villains.count
  }

  /// One-second timer (called by the view while `active`) — drives score decay.
  func tick() { if active { elapsed += 1 } }

  /// Live score for the CURRENT grid, clamped to ≥ 0.
  func liveScore() -> Int {
    let base = TraceEngine.startScoreBase * (n - 4)
    return max(0, base + goodPlaced * TraceEngine.placeBonus - elapsed * TraceEngine.timeDecay - hintsUsed * TraceEngine.hintCost - errors * TraceEngine.errorCost)
  }

  /// Total shown in the HUD: banked grids + the current grid's live score.
  func displayScore() -> Int { banked + (gridWon ? 0 : liveScore()) }

  /// Final score to submit to the leaderboard.
  func finalScore() -> Int { banked }

  /// Cells currently in a rule conflict (for red highlighting).
  func errorCells() -> Set<Int> {
    let nn = n
    var rowC = Array(repeating: 0, count: nn)
    var colC = Array(repeating: 0, count: nn)
    var regC = Array(repeating: 0, count: nn)
    for r in 0..<nn { for c in 0..<nn where cells[r][c] == 2 { rowC[r] += 1; colC[c] += 1; regC[region[r][c]] += 1 } }
    var out: Set<Int> = []
    for r in 0..<nn {
      for c in 0..<nn where cells[r][c] == 2 {
        if solution[r] != c { out.insert(key(r, c)); continue }
        if rowC[r] > 1 || colC[c] > 1 || regC[region[r][c]] > 1 { out.insert(key(r, c)); continue }
        var adj = false
        for dr in -1...1 { for dc in -1...1 where !(dr == 0 && dc == 0) {
          let nr = r + dr, ncc = c + dc
          if nr >= 0, nr < nn, ncc >= 0, ncc < nn, cells[nr][ncc] == 2 { adj = true }
        } }
        if adj { out.insert(key(r, c)) }
      }
    }
    return out
  }

  private func isSolved() -> Bool {
    let nn = n
    var total = 0
    var rowC = Array(repeating: 0, count: nn)
    var colC = Array(repeating: 0, count: nn)
    var regC = Array(repeating: 0, count: nn)
    var spamCells: [(Int, Int)] = []
    for r in 0..<nn { for c in 0..<nn where cells[r][c] == 2 { total += 1; rowC[r] += 1; colC[c] += 1; regC[region[r][c]] += 1; spamCells.append((r, c)) } }
    if total != nn { return false }
    if rowC.contains(where: { $0 != 1 }) || colC.contains(where: { $0 != 1 }) || regC.contains(where: { $0 != 1 }) { return false }
    for (r, c) in spamCells { for dr in -1...1 { for dc in -1...1 where !(dr == 0 && dc == 0) {
      let nr = r + dr, ncc = c + dc
      if nr >= 0, nr < nn, ncc >= 0, ncc < nn, cells[nr][ncc] == 2 { return false }
    } } }
    return true
  }

  /// Tap a cell: empty → SAFE(✓) → FRAUDSTER(😈) → empty.
  func cycle(_ r: Int, _ c: Int) {
    guard active else { return }
    // A correctly placed fraudster is frozen: no accidental erase, no farming.
    if cells[r][c] == 2, solution[r] == c { return }
    let v = (cells[r][c] + 1) % 3
    cells[r][c] = v
    if v == 2 {
      if solution[r] != c {
        errors += 1; lives -= 1
        lastEventType = 2; lastEventCell = key(r, c); lastEventPoints = -TraceEngine.errorCost; eventSeq += 1
        if lives <= 0 { endRun(reveal: true); return }
      } else {
        let k = key(r, c)
        if credited.contains(k) {
          lastEventPoints = 0
        } else {
          credited.insert(k); goodPlaced += 1
          lastEventPoints = TraceEngine.placeBonus
        }
        lastEventType = 1; lastEventCell = k; eventSeq += 1
      }
    }
    if isSolved() { solveGrid() }
  }

  /// Swipe-paint: mark an EMPTY cell safe (✗) without cycling — used when the
  /// player drags across a row/column to lay several crosses in one gesture.
  /// Never touches placed marks, so a swipe can't cost points or lives.
  func paintSafe(_ r: Int, _ c: Int) {
    guard active, cells[r][c] == 0 else { return }
    cells[r][c] = 1
  }

  /// Reveal a real, not-yet-placed fraudster. Costs points, never a life.
  func useHint() {
    guard active, hints > 0 else { return }
    for r in 0..<n where cells[r][solution[r]] != 2 {
      let c = solution[r]
      cells[r][c] = 2
      revealed.insert(key(r, c))
      hints -= 1; hintsUsed += 1
      lastEventType = 3; lastEventCell = key(r, c); eventSeq += 1
      if isSolved() { solveGrid() }
      return
    }
  }

  private func solveGrid() {
    gridWon = true
    solvedCount += 1
    banked += liveScore() + TraceEngine.solveBonus
  }

  /// Advance after the win pause; finish the run when the last grid is cleared.
  func nextGrid() {
    guard gridWon, !runOver else { return }
    if gridIndex + 1 < grids.count {
      gridIndex += 1
      loadGrid()
    } else {
      runWon = true
      endRun(reveal: false)
    }
  }

  private func endRun(reveal: Bool) {
    runOver = true
    revealSolution = reveal
  }
}
