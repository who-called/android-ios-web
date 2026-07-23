import CallKit
import Foundation
import OSLog

/// Downloads the scored-number list, stores it in the App Group container,
/// and reloads the Call Directory extension so iOS picks up the changes.
///
/// iOS limitation (vs Android): the extension is NOT woken per-call. We push a
/// prepared list ahead of time. WARN numbers become `identify` entries (a label
/// shown under the number) since iOS cannot fire a custom in-call alert.
final class ListService {
  private let api: WhoCalledAPI
  private let logger = Logger(subsystem: "com.whocalled.app", category: "ListService")

  init(api: WhoCalledAPI = WhoCalledAPI()) {
    self.api = api
  }

  /// Full (or delta-merged) refresh of the local list, then reload the extension.
  /// `onProgress(count)` fires after each page so the UI can show live progress
  /// (no upfront total — the loop is cursor-paginated).
  func update(onProgress: ((Int) -> Void)? = nil) async throws {
    let iso = ISO8601DateFormatter()
    iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

    let since = SharedStore.lastSyncAt.map { iso.string(from: $0) }
    let country = SharedStore.countryDial // "" = worldwide

    var merged = since == nil ? [:] : currentNumbersByPhone()
    var patterns: [(pattern: String, status: String)] = []
    var cursor = since
    var fetched = 0
    // Short-lived access token (see /lists/access). Refreshed once on 401 —
    // a long paginated sync can outlive the token's TTL.
    var token = try await api.listsAccess()

    repeat {
      let page: ListResponseDTO
      do {
        page = try await api.fetchList(since: cursor, country: country, token: token)
      } catch APIError.http(401) {
        token = try await api.listsAccess()
        page = try await api.fetchList(since: cursor, country: country, token: token)
      }
      for dto in page.numbers {
        merged[dto.phone] = ScoredNumber(
          phone: dto.phone, status: dto.status,
          spamScore: dto.spamScore, category: dto.category,
          source: dto.source ?? "community")
      }
      for p in page.patterns ?? [] {
        patterns.append((p.pattern, p.status))
      }
      // Drop numbers that healed/were removed upstream so the single local
      // cache stays in sync with the consolidated server DB.
      for phone in page.deleted ?? [] {
        merged.removeValue(forKey: phone)
      }
      fetched += page.numbers.count
      onProgress?(fetched)
      cursor = page.next
    } while cursor != nil

    // Expand ARCEP/operator wildcard patterns into individual numbers — iOS can't
    // match wildcards at call time, so the Call Directory needs explicit numbers.
    for entry in PatternExpander.expandAll(patterns) {
      let phone = String(entry.value)
      // Don't override a community/exact entry already present.
      if merged[phone] == nil {
        merged[phone] = ScoredNumber(
          phone: phone, status: entry.status, spamScore: entry.status == "block" ? 100 : 70,
          category: "telemarketing", source: "arcep")
      }
    }

    // CallKit requires entries sorted ascending by number — sort here once.
    let sorted = merged.values
      .filter { $0.phoneInt64 != nil }
      .sorted { ($0.phoneInt64 ?? 0) < ($1.phoneInt64 ?? 0) }

    SharedStore.writeScoredNumbers(
      ScoredNumbersFile(serverTime: ISO8601DateFormatter().string(from: Date()), numbers: sorted))
    SharedStore.lastSyncAt = Date()

    try await reloadExtension()
    logger.info("List updated with \(sorted.count) numbers")
  }

  /// Change the country scope: persist it, wipe the now-out-of-scope cache and
  /// reset the cursor, then re-sync the new scope fully (and reload the extension).
  func setCountry(_ dial: String, onProgress: ((Int) -> Void)? = nil) async throws {
    SharedStore.countryDial = dial
    SharedStore.writeScoredNumbers(ScoredNumbersFile(serverTime: "", numbers: []))
    SharedStore.lastSyncAt = nil
    try await update(onProgress: onProgress)
  }

  private func currentNumbersByPhone() -> [String: ScoredNumber] {
    Dictionary(uniqueKeysWithValues: SharedStore.readScoredNumbers().numbers.map { ($0.phone, $0) })
  }

  private func reloadExtension() async throws {
    try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
      CXCallDirectoryManager.sharedInstance.reloadExtension(
        withIdentifier: AppConstants.callDirectoryExtensionId
      ) { error in
        if let error { cont.resume(throwing: error) } else { cont.resume() }
      }
    }
  }
}
