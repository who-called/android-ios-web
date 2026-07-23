import Foundation

/// Persistence for the daily games (streak / ranked attempts / best score), keyed
/// PER GAME so DEFENSE and TRACE never collide. Mirrors Android `Preferences`:
/// the streak is day-based (bumps on the first play of a new day, breaks only by
/// skipping a day) and the first `maxAttempts` runs of a day are RANKED.
///
/// "defense" uses the original un-suffixed keys for backward compatibility with
/// existing installs; new games get a `game_<id>_…` prefix.
enum GameStore {
  static let maxAttempts = 3

  private static func key(_ game: String, _ field: String) -> String {
    game == "defense" ? "game_\(field)" : "game_\(game)_\(field)"
  }

  struct LocalDay { let day: Int; let score: Int; let waves: Int }

  /// Local per-day best history (desc by day) — leaderboard offline fallback.
  static func localHistory(_ game: String = "defense") -> [LocalDay] {
    let raw = SharedStore.defaults()?.stringArray(forKey: key(game, "history_v1")) ?? []
    return raw.compactMap { s -> LocalDay? in
      let p = s.split(separator: "|")
      guard p.count >= 2, let d = Int(p[0]), let sc = Int(p[1]) else { return nil }
      return LocalDay(day: d, score: sc, waves: p.count > 2 ? (Int(p[2]) ?? 0) : 0)
    }.sorted { $0.day > $1.day }
  }

  static func epochDay(_ date: Date = Date()) -> Int {
    Int(floor(date.timeIntervalSince1970 / 86_400))
  }

  struct State {
    var lastPlayedDay: Int
    var streak: Int
    var lastScore: Int
    var lastWave: Int
    var bestScoreToday: Int
    var bestScoreAllTime: Int
    var attemptsToday: Int
    var bestStreak: Int
  }

  static func state(_ game: String = "defense") -> State {
    let d = SharedStore.defaults()
    return State(
      lastPlayedDay: d?.integer(forKey: key(game, "last_day")) ?? -1,
      streak: d?.integer(forKey: key(game, "streak")) ?? 0,
      lastScore: d?.integer(forKey: key(game, "last_score")) ?? 0,
      lastWave: d?.integer(forKey: key(game, "last_wave")) ?? 0,
      bestScoreToday: d?.integer(forKey: key(game, "best_today")) ?? 0,
      bestScoreAllTime: d?.integer(forKey: key(game, "best_alltime")) ?? 0,
      attemptsToday: d?.integer(forKey: key(game, "attempts_today")) ?? 0,
      bestStreak: d?.integer(forKey: key(game, "best_streak")) ?? 0)
  }

  /// Record a finished run for `game` on `today`. Returns whether it was RANKED.
  @discardableResult
  static func record(game: String, today: Int, score: Int, waves: Int) -> Bool {
    let d = SharedStore.defaults()
    let prevDay = d?.integer(forKey: key(game, "last_day")) ?? -1
    let prevStreak = d?.integer(forKey: key(game, "streak")) ?? 0
    let firstToday = prevDay != today
    let attempts = firstToday ? 0 : (d?.integer(forKey: key(game, "attempts_today")) ?? 0)
    let ranked = attempts < maxAttempts
    let newStreak = firstToday ? (prevDay == today - 1 ? prevStreak + 1 : 1) : prevStreak
    let newBestStreak = max(d?.integer(forKey: key(game, "best_streak")) ?? 0, newStreak)
    let prevBestToday = firstToday ? 0 : (d?.integer(forKey: key(game, "best_today")) ?? 0)
    let bestToday = max(prevBestToday, score)
    // Update local per-day history (best of the day, last 30).
    let prefix = "\(today)|"
    var hist: [String] = (d?.stringArray(forKey: key(game, "history_v1")) ?? []).filter { !$0.hasPrefix(prefix) }
    hist.append("\(today)|\(bestToday)|\(waves)")
    func dayOf(_ s: String) -> Int { Int(s.split(separator: "|").first.map(String.init) ?? "") ?? 0 }
    hist.sort { dayOf($0) > dayOf($1) }
    if hist.count > 30 { hist = Array(hist.prefix(30)) }
    d?.set(today, forKey: key(game, "last_day"))
    d?.set(newStreak, forKey: key(game, "streak"))
    d?.set(newBestStreak, forKey: key(game, "best_streak"))
    d?.set(ranked ? attempts + 1 : attempts, forKey: key(game, "attempts_today"))
    d?.set(score, forKey: key(game, "last_score"))
    d?.set(waves, forKey: key(game, "last_wave"))
    d?.set(bestToday, forKey: key(game, "best_today"))
    d?.set(max(d?.integer(forKey: key(game, "best_alltime")) ?? 0, score), forKey: key(game, "best_alltime"))
    d?.set(hist, forKey: key(game, "history_v1"))
    return ranked
  }

  /// True if any game was already played today (so the reminder can stay quiet).
  static func playedAnyGameToday(_ today: Int = epochDay()) -> Bool {
    state("defense").lastPlayedDay == today || state("trace").lastPlayedDay == today
  }
}
