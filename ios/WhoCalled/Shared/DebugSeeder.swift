import Foundation

/// Seeds the App Group store with mock data in DEBUG builds so the UI is
/// populated without a server. No-op in release.
enum DebugSeeder {
  static func seedIfNeeded() {
    #if DEBUG
      guard SharedStore.readScoredNumbers().numbers.isEmpty else { return }

      let numbers = [
        // ARCEP example (official list) — to see the difference vs community.
        ScoredNumber(phone: "33899123456", status: "block", spamScore: 100, category: "telemarketing", source: "arcep"),
        ScoredNumber(phone: "33162987654", status: "block", spamScore: 92, category: "robocall", source: "community"),
        ScoredNumber(phone: "33970650000", status: "warn", spamScore: 68, category: "scam", source: "community"),
        ScoredNumber(phone: "33812345678", status: "warn", spamScore: 74, category: "telemarketing", source: "community"),
      ].sorted { ($0.phoneInt64 ?? 0) < ($1.phoneInt64 ?? 0) }

      SharedStore.writeScoredNumbers(
        ScoredNumbersFile(serverTime: ISO8601DateFormatter().string(from: Date()), numbers: numbers))

      MyReportsStore.upsert(
        MyReport(phone: "33899123456", vote: "spam", category: "telemarketing", updatedAt: Date(), syncState: "synced"))
      MyReportsStore.upsert(
        MyReport(phone: "33600112233", vote: "spam", category: nil, updatedAt: Date(), syncState: "failed"))
    #endif
  }
}
