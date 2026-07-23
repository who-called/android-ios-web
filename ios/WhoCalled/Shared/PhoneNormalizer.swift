import Foundation

/// Normalizes phone numbers to E.164 digits without '+' (e.g. "33612345678").
/// FR-first; mirrors the backend and the Android client so all agree.
enum PhoneNormalizer {
  static func normalize(
    _ raw: String?,
    defaultCountryPrefix: String = AppConstants.defaultCountryPrefix
  ) -> String? {
    guard let raw, !raw.isEmpty else { return nil }

    var s = raw.filter { $0.isNumber || $0 == "+" }

    if s.hasPrefix("+") {
      s = String(s.dropFirst())
    } else if s.hasPrefix("00") {
      s = String(s.dropFirst(2))
    } else if s.hasPrefix("0") {
      s = defaultCountryPrefix + s.dropFirst()
    }

    let isValid = s.range(of: "^[0-9]{6,15}$", options: .regularExpression) != nil
    return isValid ? s : nil
  }

  /// Best-effort extraction of a phone number from arbitrary shared text (e.g. an
  /// SMS body). Returns the raw matched candidate (not normalized) so the report
  /// screen can display it; nil if nothing phone-like is found. Picks the
  /// candidate with the most digits to avoid grabbing short codes.
  static func extractFromText(_ text: String?) -> String? {
    guard let text, !text.isEmpty else { return nil }
    let pattern = "\\+?[0-9][0-9\\s().\\-]{5,18}[0-9]"
    guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
    let range = NSRange(text.startIndex..<text.endIndex, in: text)
    var best: String?
    var bestDigits = -1
    regex.enumerateMatches(in: text, range: range) { match, _, _ in
      guard let match, let r = Range(match.range, in: text) else { return }
      let candidate = String(text[r]).trimmingCharacters(in: .whitespaces)
      guard normalize(candidate) != nil else { return }
      let digits = candidate.filter { $0.isNumber }.count
      if digits > bestDigits {
        bestDigits = digits
        best = candidate
      }
    }
    return best
  }
}
