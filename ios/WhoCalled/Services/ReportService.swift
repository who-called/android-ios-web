import Foundation

/// Submits anonymous reports and keeps a local copy the user can review.
struct ReportService {
  private let api: WhoCalledAPI

  init(api: WhoCalledAPI = WhoCalledAPI()) {
    self.api = api
  }

  /// Report a number. Saved locally first (so the user always sees it), then
  /// pushed to the API best-effort. Throws only on invalid input.
  func report(rawPhone: String, isSpam: Bool, category: ReportCategory? = nil) async throws {
    guard let phone = PhoneNormalizer.normalize(rawPhone) else {
      throw APIError.invalidURL
    }
    let vote = isSpam ? "spam" : "legit"

    // Persist locally (pending).
    MyReportsStore.upsert(
      MyReport(phone: phone, vote: vote, category: category?.rawValue, updatedAt: Date(), syncState: "pending"))

    do {
      try await api.report(
        ReportRequestDTO(
          phone: phone, deviceId: SharedStore.deviceId(),
          vote: vote, category: category?.rawValue, locale: "fr"))
      MyReportsStore.upsert(
        MyReport(phone: phone, vote: vote, category: category?.rawValue, updatedAt: Date(), syncState: "synced"))
    } catch {
      MyReportsStore.upsert(
        MyReport(phone: phone, vote: vote, category: category?.rawValue, updatedAt: Date(), syncState: "failed"))
      throw error
    }
  }

  /// Delete one of the user's reports + send a corrective "legit" vote.
  func delete(phone: String) async {
    MyReportsStore.delete(phone: phone)
    _ = try? await api.report(
      ReportRequestDTO(
        phone: phone, deviceId: SharedStore.deviceId(),
        vote: "legit", category: nil, locale: "fr"))
  }
}
