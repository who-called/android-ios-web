import Foundation

/// A scored number synced from the who-called backend.
/// Phone is E.164 without '+' (e.g. "33899123456").
struct ScoredNumber: Codable, Equatable {
  let phone: String
  let status: String  // "block" | "warn"
  let spamScore: Int
  let category: String
  var source: String = "community"  // community | arcep

  /// CallKit requires Int64 phone numbers (with country code, no '+').
  var phoneInt64: Int64? { Int64(phone) }
}

/// Wrapper persisted to the App Group container as JSON.
struct ScoredNumbersFile: Codable {
  var serverTime: String
  var numbers: [ScoredNumber]
}
