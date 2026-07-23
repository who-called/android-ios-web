import CallKit
import Foundation
import OSLog

/// Preloads the embedded warm-up list (bundled `warmup_ios.json`) into the App
/// Group store on first launch, so iOS blocks/labels known FR spam immediately —
/// offline, before any network sync. No-op once the store is populated. After
/// this, ListService deltas keep the same single file up to date.
enum WarmupLoader {
  private static let logger = Logger(subsystem: "com.whocalled.app", category: "WarmupLoader")

  /// Returns true if the warm-up was just loaded (i.e. this is the first launch)
  /// so the caller can trigger an immediate first-time sync.
  @discardableResult
  static func loadIfNeeded() async -> Bool {
    guard SharedStore.readScoredNumbers().numbers.isEmpty else { return false }
    guard let url = Bundle.main.url(forResource: "warmup_ios", withExtension: "json") else {
      logger.notice("no warm-up asset bundled (warmup_ios.json)")
      return false
    }
    do {
      let data = try Data(contentsOf: url)
      let file = try JSONDecoder().decode(ScoredNumbersFile.self, from: data)
      guard !file.numbers.isEmpty else { return false }
      SharedStore.writeScoredNumbers(file)
      // Seed the sync cursor to the warm-up's serverTime so the next sync only
      // pulls deltas since the embedded baseline (not a full re-download).
      if let t = ISO8601DateFormatter().date(from: file.serverTime) {
        SharedStore.lastSyncAt = t
      }
      try await reloadExtension()
      logger.info("Warm-up loaded \(file.numbers.count) numbers")
      return true
    } catch {
      logger.error("warm-up load failed: \(error.localizedDescription)")
      return false
    }
  }

  private static func reloadExtension() async throws {
    try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
      CXCallDirectoryManager.sharedInstance.reloadExtension(
        withIdentifier: AppConstants.callDirectoryExtensionId
      ) { error in
        if let error { cont.resume(throwing: error) } else { cont.resume() }
      }
    }
  }
}
