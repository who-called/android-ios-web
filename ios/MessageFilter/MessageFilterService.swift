import Foundation
import IdentityLookup

/// Offline matching for the SMS shield. Reads the same scored-numbers JSON the
/// main app writes into the App Group container (the one the Call Directory
/// extension uses), and filters a sender whose number is `block`.
///
/// Pure/offline: no network, no message content inspected — only the sender.
struct MessageFilterService {

  func action(for sender: String) -> ILMessageFilterAction {
    guard let normalized = normalize(sender) else { return .none }
    return blockedNumbers().contains(normalized) ? .junk : .none
  }

  // MARK: - Shared data

  /// Set of E.164-without-'+' numbers currently flagged as block.
  private func blockedNumbers() -> Set<String> {
    guard
      let container = FileManager.default.containerURL(
        forSecurityApplicationGroupIdentifier: AppGroup.identifier),
      case let url = container.appendingPathComponent(AppGroup.scoredNumbersFileName),
      FileManager.default.fileExists(atPath: url.path),
      let data = try? Data(contentsOf: url),
      let file = try? JSONDecoder().decode(SharedScoredNumbersFile.self, from: data)
    else { return [] }

    let threshold = UserDefaults(suiteName: AppGroup.identifier)?
      .object(forKey: AppGroup.blockThresholdKey) as? Int ?? 85

    return Set(
      file.numbers
        .filter { $0.status == "block" || $0.spamScore >= threshold }
        .map { $0.phone })
  }

  // MARK: - Normalisation (mirrors the app / backend: E.164 digits, no '+')

  private func normalize(_ raw: String, defaultCountryPrefix: String = "33") -> String? {
    var s = raw.filter { $0.isNumber || $0 == "+" }
    if s.hasPrefix("+") {
      s.removeFirst()
    } else if s.hasPrefix("00") {
      s.removeFirst(2)
    } else if s.hasPrefix("0") {
      s = defaultCountryPrefix + s.dropFirst()
    }
    let digits = s.filter { $0.isNumber }
    return (6...15).contains(digits.count) ? digits : nil
  }
}

// Minimal local copies so the extension stays lightweight and self-contained.
// (Add the real shared files to this target's membership if you prefer reuse.)

private enum AppGroup {
  static let identifier = "group.com.whocalled.app"
  static let scoredNumbersFileName = "scored_numbers.json"
  static let blockThresholdKey = "block_threshold"
}

private struct SharedScoredNumber: Codable {
  let phone: String
  let status: String
  let spamScore: Int
}

private struct SharedScoredNumbersFile: Codable {
  let numbers: [SharedScoredNumber]
}
