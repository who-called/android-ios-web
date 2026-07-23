import Foundation

/// Human-friendly French relative time ("à l'instant", "il y a 5 min",
/// "il y a 2 h", "hier", "il y a 3 j"), falling back to an absolute FR date for
/// anything older than a week. Centralised so every view shows dates the same
/// way — and never in the device's English locale.
enum RelativeTime {
  static func format(_ date: Date, now: Date = Date()) -> String {
    let diff = now.timeIntervalSince(date)
    if diff < 0 { return "à l’instant" }
    let minutes = Int(diff / 60)
    let hours = Int(diff / 3600)
    let days = Int(diff / 86_400)
    switch true {
    case minutes < 1: return "à l’instant"
    case minutes < 60: return "il y a \(minutes) min"
    case hours < 24: return "il y a \(hours) h"
    case days == 1: return "hier"
    case days < 7: return "il y a \(days) j"
    default:
      let f = DateFormatter()
      f.locale = Locale(identifier: "fr_FR")
      f.dateStyle = .medium
      return f.string(from: date)
    }
  }
}
