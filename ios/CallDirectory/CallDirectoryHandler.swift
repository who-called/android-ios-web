import CallKit
import Foundation
import OSLog

/// who-called Call Directory extension.
///
/// iOS consults a pre-loaded list (it does NOT wake this extension per call).
/// We read the scored numbers prepared by the main app (JSON in the App Group)
/// and register:
///   - status == "block" → blocking entry (call silently rejected)
///   - status == "warn"  → identification entry with a label (number rings, label shown)
///
/// CallKit requires all entries to be added in **ascending numerical order**.
class CallDirectoryHandler: CXCallDirectoryProvider {
  private let logger = Logger(subsystem: "com.whocalled.app", category: "CallDirectory")

  override func beginRequest(with context: CXCallDirectoryExtensionContext) {
    context.delegate = self
    do {
      // Always do a full rebuild from the prepared file (simple + correct).
      try fullUpdate(context)
      context.completeRequest()
    } catch {
      logger.error("Request failed: \(error.localizedDescription)")
      context.cancelRequest(withError: error)
    }
  }

  private func fullUpdate(_ context: CXCallDirectoryExtensionContext) throws {
    let file = SharedStore.readScoredNumbers()
    let warnEnabled = SharedStore.warnEnabled
    let blockThreshold = SharedStore.blockThreshold

    // Defensive: ensure ascending order even if the file wasn't pre-sorted.
    let entries = file.numbers
      .compactMap { number -> (Int64, ScoredNumber)? in
        guard let value = number.phoneInt64 else { return nil }
        return (value, number)
      }
      .sorted { $0.0 < $1.0 }

    for (value, number) in entries {
      let isBlock = number.status == "block" || number.spamScore >= blockThreshold
      if isBlock {
        context.addBlockingEntry(withNextSequentialPhoneNumber: value)
      } else if warnEnabled && number.status == "warn" {
        let label = "⚠️ who-called · spam \(number.spamScore)%"
        context.addIdentificationEntry(withNextSequentialPhoneNumber: value, label: label)
      }
    }

    logger.info("Loaded \(entries.count) entries into Call Directory")
  }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
  func requestFailed(
    for extensionContext: CXCallDirectoryExtensionContext, withError error: Error
  ) {
    logger.error("Extension request failed: \(error.localizedDescription)")
  }
}
