import Foundation
import OSLog
import Security

/// Shared storage between the main app and the Call Directory extension.
///
/// The scored-number list is written as a JSON file into the App Group container
/// (the extension reads it on `beginRequest`). Small settings live in the shared
/// UserDefaults suite. This is the app's local "cache".
enum SharedStore {
  private static let logger = Logger(subsystem: "com.whocalled.app", category: "SharedStore")

  static func defaults() -> UserDefaults? {
    UserDefaults(suiteName: AppConstants.appGroupIdentifier)
  }

  private static func containerURL() -> URL? {
    FileManager.default.containerURL(
      forSecurityApplicationGroupIdentifier: AppConstants.appGroupIdentifier)
  }

  private static func fileURL() -> URL? {
    containerURL()?.appendingPathComponent(AppConstants.scoredNumbersFileName)
  }

  // MARK: - Scored numbers file

  static func readScoredNumbers() -> ScoredNumbersFile {
    guard let url = fileURL(), FileManager.default.fileExists(atPath: url.path) else {
      return ScoredNumbersFile(serverTime: "", numbers: [])
    }
    do {
      let data = try Data(contentsOf: url)
      return try JSONDecoder().decode(ScoredNumbersFile.self, from: data)
    } catch {
      logger.error("Failed to read scored numbers: \(error.localizedDescription)")
      return ScoredNumbersFile(serverTime: "", numbers: [])
    }
  }

  static func writeScoredNumbers(_ file: ScoredNumbersFile) {
    guard let url = fileURL() else { return }
    do {
      let data = try JSONEncoder().encode(file)
      try data.write(to: url, options: .atomic)
    } catch {
      logger.error("Failed to write scored numbers: \(error.localizedDescription)")
    }
  }

  // MARK: - Anonymous device id

  /// Anonymous Keychain id. Keychain survives a normal reinstall, so the backend
  /// replaces this device's previous vote instead of counting it twice.
  static func deviceId() -> String {
    let service = "com.whocalled.app.anonymous-device"
    let account = "report-voter"
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: account,
      kSecReturnData as String: true,
      kSecMatchLimit as String: kSecMatchLimitOne,
    ]
    var result: CFTypeRef?
    if SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
       let data = result as? Data,
       let existing = String(data: data, encoding: .utf8)
    {
      return existing
    }

    let d = defaults()
    let id = d?.string(forKey: AppConstants.Keys.deviceId) ?? "ios-\(UUID().uuidString)"
    let add: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: account,
      kSecValueData as String: Data(id.utf8),
      kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
    ]
    SecItemAdd(add as CFDictionary, nil)
    d?.set(id, forKey: AppConstants.Keys.deviceId)
    return id
  }

  // MARK: - Settings

  static var blockThreshold: Int {
    get { defaults()?.object(forKey: AppConstants.Keys.blockThreshold) as? Int ?? AppConstants.defaultBlockThreshold }
    set { defaults()?.set(newValue, forKey: AppConstants.Keys.blockThreshold) }
  }

  static var warnEnabled: Bool {
    get { defaults()?.object(forKey: AppConstants.Keys.warnEnabled) as? Bool ?? true }
    set { defaults()?.set(newValue, forKey: AppConstants.Keys.warnEnabled) }
  }

  static var lastSyncAt: Date? {
    get { defaults()?.object(forKey: AppConstants.Keys.lastSyncAt) as? Date }
    set { defaults()?.set(newValue, forKey: AppConstants.Keys.lastSyncAt) }
  }

  /// Country scope (E.164 dial code) for the list sync; "" = worldwide.
  /// Unset → detect from the device region so a new install scopes locally.
  static var countryDial: String {
    get { defaults()?.string(forKey: AppConstants.Keys.countryDial) ?? Countries.detectDefault().dial }
    set { defaults()?.set(newValue, forKey: AppConstants.Keys.countryDial) }
  }

  /// Whether the user has seen/confirmed the auto-detected country (1st-launch banner).
  static var countryConfirmed: Bool {
    get { defaults()?.bool(forKey: AppConstants.Keys.countryConfirmed) ?? false }
    set { defaults()?.set(newValue, forKey: AppConstants.Keys.countryConfirmed) }
  }

  /// Daily "come back and play" game reminder (opt-in, default off).
  static var gameReminderEnabled: Bool {
    get { defaults()?.bool(forKey: AppConstants.Keys.gameReminderEnabled) ?? false }
    set { defaults()?.set(newValue, forKey: AppConstants.Keys.gameReminderEnabled) }
  }

  /// Whether the TRACE interactive tutorial was completed once (first launch only).
  static var traceTutorialSeen: Bool {
    get { defaults()?.bool(forKey: AppConstants.Keys.traceTutorialSeen) ?? false }
    set { defaults()?.set(newValue, forKey: AppConstants.Keys.traceTutorialSeen) }
  }
}
