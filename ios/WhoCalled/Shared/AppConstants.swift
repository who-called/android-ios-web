import Foundation

/// Central configuration for the who-called app and its extensions.
/// Keep all shared identifiers and tunables here.
enum AppConstants {
  /// App Group shared between the main app and the Call Directory extension.
  static let appGroupIdentifier = "group.com.whocalled.app"

  /// Call Directory extension bundle identifier (used by CXCallDirectoryManager).
  static let callDirectoryExtensionId = "com.whocalled.app.calldirectory"

  /// Backend API.
  static let apiBaseURL = "https://api.who-called.com/api/v1"
  static let apiReportsURL = "\(apiBaseURL)/reports"
  static let apiListsURL = "\(apiBaseURL)/lists"
  static let apiLookupURL = "\(apiBaseURL)/lookup"
  static let apiTrendingURL = "\(apiBaseURL)/trending"
  static let apiStatsURL = "\(apiBaseURL)/stats"
  static let apiGameScoreURL = "\(apiBaseURL)/game/score"
  static let apiGamePuzzleURL = "\(apiBaseURL)/game/puzzle"
  /// Health probe lives at the host root, NOT under /api/v1.
  static let apiHealthURL = apiBaseURL.replacingOccurrences(
    of: "/api/v1", with: "/health"
  )

  /// Public links — change these in one place (our ".env"). Mirrors Android BuildConfig.
  enum Links {
    static let site = "https://www.who-called.com"
    static let privacy = "https://www.who-called.com/privacy"
    static let policy = "https://www.who-called.com/policy"
    static let repo = "https://github.com/who-called/android-ios-web"
    static let rate = "https://www.who-called.com"
    static let contactEmail = "contact@who-called.com"
  }

  /// File (in the App Group container) holding the scored numbers the extension reads.
  static let scoredNumbersFileName = "scored_numbers.json"

  /// Default country prefix (FR-first). E.164 without '+'.
  static let defaultCountryPrefix = "33"

  /// Block when spamScore >= this (user-configurable, this is the default).
  static let defaultBlockThreshold = 85

  /// Periodic sync interval (seconds) — 6h.
  static let syncInterval: TimeInterval = 6 * 60 * 60

  /// UserDefaults keys (stored in the shared suite).
  enum Keys {
    static let deviceId = "device_id"
    static let lastSyncAt = "last_sync_at"
    static let blockThreshold = "block_threshold"
    static let warnEnabled = "warn_enabled"
    static let filteringEnabled = "filtering_enabled"
    static let countryDial = "country_dial"
    static let countryConfirmed = "country_confirmed"
    static let gameReminderEnabled = "game_reminder_enabled"
    static let traceTutorialSeen = "trace_tutorial_seen"
  }
}
