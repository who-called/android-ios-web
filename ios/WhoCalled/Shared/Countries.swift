import Foundation

/// A selectable country scope for the spam list. `dial` "" = worldwide ("Tous").
struct CountryOption: Identifiable, Hashable {
  let code: String
  let name: String
  let dial: String
  let flag: String
  var id: String { code }
}

/// Curated country scopes (FR-first) + "Tous". `dial` is sent to
/// GET /lists?country=…; "" means worldwide. Mirrors the Android list.
enum Countries {
  static let all = CountryOption(code: "ALL", name: "Tous les pays", dial: "", flag: "🌍")

  static let list: [CountryOption] = [
    all,
    CountryOption(code: "FR", name: "France", dial: "33", flag: "🇫🇷"),
    CountryOption(code: "BE", name: "Belgique", dial: "32", flag: "🇧🇪"),
    CountryOption(code: "CH", name: "Suisse", dial: "41", flag: "🇨🇭"),
    CountryOption(code: "LU", name: "Luxembourg", dial: "352", flag: "🇱🇺"),
    CountryOption(code: "ES", name: "Espagne", dial: "34", flag: "🇪🇸"),
    CountryOption(code: "IT", name: "Italie", dial: "39", flag: "🇮🇹"),
    CountryOption(code: "DE", name: "Allemagne", dial: "49", flag: "🇩🇪"),
    CountryOption(code: "GB", name: "Royaume-Uni", dial: "44", flag: "🇬🇧"),
    CountryOption(code: "PT", name: "Portugal", dial: "351", flag: "🇵🇹"),
    CountryOption(code: "US", name: "États-Unis", dial: "1", flag: "🇺🇸"),
    CountryOption(code: "CA", name: "Canada", dial: "1", flag: "🇨🇦"),
    CountryOption(code: "MA", name: "Maroc", dial: "212", flag: "🇲🇦"),
    CountryOption(code: "DZ", name: "Algérie", dial: "213", flag: "🇩🇿"),
    CountryOption(code: "TN", name: "Tunisie", dial: "216", flag: "🇹🇳"),
  ]

  static func byDial(_ dial: String) -> CountryOption {
    list.first { $0.dial == dial } ?? all
  }

  /// Best-effort default from the device region (no permission). iOS's CTCarrier
  /// ISO is deprecated/empty, so we use the locale region. Falls back to "Tous".
  static func detectDefault() -> CountryOption {
    let iso: String?
    if #available(iOS 16, *) {
      iso = Locale.current.region?.identifier
    } else {
      iso = Locale.current.regionCode
    }
    guard let code = iso?.uppercased() else { return all }
    return list.first { $0.code == code } ?? all
  }
}
