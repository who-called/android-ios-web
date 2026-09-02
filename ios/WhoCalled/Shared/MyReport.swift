import Foundation

/// A report the user made (kept locally so they can review / change / delete it).
struct MyReport: Codable, Identifiable, Equatable {
  let phone: String  // E.164 without '+'
  var vote: String  // spam | legit
  var category: String?
  var updatedAt: Date
  var syncState: String  // pending | synced | failed

  var id: String { phone }
}

/// Persists the user's own reports in the App Group UserDefaults.
enum MyReportsStore {
  private static let key = "my_reports"

  static func all() -> [MyReport] {
    guard let data = SharedStore.defaults()?.data(forKey: key),
      let list = try? JSONDecoder().decode([MyReport].self, from: data)
    else { return [] }
    return list.sorted { $0.updatedAt > $1.updatedAt }
  }

  static func upsert(_ report: MyReport) {
    var list = all().filter { $0.phone != report.phone }
    list.append(report)
    save(list)
  }

  static func delete(phone: String) {
    save(all().filter { $0.phone != phone })
  }

  /// Wipe everything (RGPD erase — the server copy is gone too).
  static func clear() {
    save([])
  }

  private static func save(_ list: [MyReport]) {
    if let data = try? JSONEncoder().encode(list) {
      SharedStore.defaults()?.set(data, forKey: key)
    }
  }
}
