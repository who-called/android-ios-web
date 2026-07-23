import Foundation

/// Predefined report categories (no free text → safe for RGPD, no moderation).
enum ReportCategory: String, CaseIterable, Identifiable {
  case telemarketing
  case scam
  case robocall
  case silent
  case debt
  case survey
  case other = "unknown"

  var id: String { rawValue }

  var label: String {
    switch self {
    case .telemarketing: return "Démarchage"
    case .scam: return "Arnaque"
    case .robocall: return "Appel automatisé"
    case .silent: return "Appel silencieux"
    case .debt: return "Recouvrement"
    case .survey: return "Sondage"
    case .other: return "Autre"
    }
  }

  static func from(_ api: String?) -> ReportCategory? {
    guard let api else { return nil }
    return ReportCategory(rawValue: api)
  }
}
