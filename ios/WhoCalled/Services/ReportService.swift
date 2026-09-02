import Foundation

/// Report submission failed before reaching the network (nothing was saved).
enum ReportError: Error {
  case invalidPhone
}

/// The device language ("fr", "en"…), so server-side provenance stats aren't
/// all "fr". Mirrors the Android client; iOS 15-compatible API.
private var deviceLanguage: String {
  let tag = Locale.preferredLanguages.first ?? ""
  let code = tag.split(separator: "-").first.map(String.init) ?? ""
  return code.isEmpty ? "fr" : code
}

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
      throw ReportError.invalidPhone
    }
    let vote = isSpam ? "spam" : "legit"
    // Keep the original reason across vote flips (spam → legit → spam) so the
    // "pourquoi" survives a change of mind; a "legit" vote carries no category
    // on the wire, only in the local row.
    let previous = MyReportsStore.all().first { $0.phone == phone }
    let keptCategory = category?.rawValue ?? previous?.category
    let sentCategory = isSpam ? keptCategory : nil

    // Persist locally (pending).
    MyReportsStore.upsert(
      MyReport(phone: phone, vote: vote, category: keptCategory, updatedAt: Date(), syncState: "pending"))

    do {
      try await api.report(
        ReportRequestDTO(
          phone: phone, deviceId: SharedStore.deviceId(),
          vote: vote, category: sentCategory, locale: deviceLanguage))
      MyReportsStore.upsert(
        MyReport(phone: phone, vote: vote, category: keptCategory, updatedAt: Date(), syncState: "synced"))
    } catch {
      MyReportsStore.upsert(
        MyReport(phone: phone, vote: vote, category: keptCategory, updatedAt: Date(), syncState: "failed"))
      throw error
    }
  }

  /// Re-push one report that never reached the server. Updates the existing
  /// row's syncState in place — never inserts a duplicate.
  func retry(_ report: MyReport) async {
    do {
      try await api.report(
        ReportRequestDTO(
          phone: report.phone, deviceId: SharedStore.deviceId(),
          vote: report.vote, category: report.vote == "spam" ? report.category : nil,
          locale: deviceLanguage))
      MyReportsStore.upsert(
        MyReport(phone: report.phone, vote: report.vote, category: report.category, updatedAt: Date(), syncState: "synced"))
    } catch {
      MyReportsStore.upsert(
        MyReport(phone: report.phone, vote: report.vote, category: report.category, updatedAt: report.updatedAt, syncState: "failed"))
    }
  }

  /// Best-effort re-push of every pending/failed report (app start, post-sync).
  func retryPending() async {
    for report in MyReportsStore.all() where report.syncState != "synced" {
      await retry(report)
    }
  }

  /// Delete one of the user's reports. If a SPAM vote had actually reached the
  /// server, also send a corrective "legit" vote so the crowd score isn't stuck
  /// on a mistake. An unsynced or already-legit report gets no corrective — it
  /// would create a vote out of thin air (same rule as the Android client).
  func delete(phone: String) async {
    let existing = MyReportsStore.all().first { $0.phone == phone }
    MyReportsStore.delete(phone: phone)
    guard existing?.vote == "spam", existing?.syncState == "synced" else { return }
    _ = try? await api.report(
      ReportRequestDTO(
        phone: phone, deviceId: SharedStore.deviceId(),
        vote: "legit", category: nil, locale: deviceLanguage))
  }
}
