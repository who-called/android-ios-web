import Foundation

/// Expands wildcard patterns (e.g. "33899######") into individual E.164 numbers
/// for the Call Directory (iOS can't match wildcards at call time, so the list
/// must be pre-expanded — like Saracroche does).
///
/// Each trailing '#' multiplies the count by 10, so a 6-'#' pattern = 1,000,000
/// numbers. We cap total expansion to protect Call Directory performance.
enum PatternExpander {
  /// Hard cap on generated numbers across all patterns (CallKit perf safety).
  static let maxExpandedNumbers = 1_000_000

  /// Number of numbers a single pattern would generate, or nil if malformed.
  static func count(for pattern: String) -> Int? {
    guard pattern.allSatisfy({ $0.isNumber || $0 == "#" }) else { return nil }
    let hashes = pattern.filter { $0 == "#" }.count
    guard hashes > 0 else { return 1 }
    return Int(pow(10.0, Double(hashes)))
  }

  /// Expand one pattern into Int64 numbers. Returns [] if malformed or too large.
  static func expand(_ pattern: String, limit: Int) -> [Int64] {
    guard let total = count(for: pattern), total <= limit else { return [] }

    // Fixed prefix = pattern up to the first '#'. Wildcards are trailing only.
    guard let firstHash = pattern.firstIndex(of: "#") else {
      return Int64(pattern).map { [$0] } ?? []
    }
    let prefix = String(pattern[..<firstHash])
    let hashes = pattern.distance(from: firstHash, to: pattern.endIndex)

    guard let base = Int64(prefix) else { return [] }
    let span = Int(pow(10.0, Double(hashes)))
    let start = base * Int64(span)
    return (0..<span).map { start + Int64($0) }
  }

  /// Expand a set of patterns into sorted unique Int64 numbers, globally capped.
  static func expandAll(_ patterns: [(pattern: String, status: String)], cap: Int = maxExpandedNumbers)
    -> [(value: Int64, status: String)]
  {
    var out: [(Int64, String)] = []
    var budget = cap
    // Smallest patterns first so we don't blow the budget on one huge range.
    let sorted = patterns.sorted { (count(for: $0.pattern) ?? .max) < (count(for: $1.pattern) ?? .max) }
    for p in sorted {
      let nums = expand(p.pattern, limit: budget)
      if nums.isEmpty { continue }
      budget -= nums.count
      for n in nums { out.append((n, p.status)) }
      if budget <= 0 { break }
    }
    return out.sorted { $0.0 < $1.0 }
  }
}
