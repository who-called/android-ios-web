import CallKit
import Foundation

@MainActor
final class MainViewModel: ObservableObject {
  @Published var isExtensionEnabled = false
  @Published var blockedNumbersCount = 0
  @Published var warnNumbersCount = 0
  @Published var isSyncing = false
  @Published var syncProgress = 0  // live count of numbers fetched during a sync
  @Published var syncBanner: (kind: BannerKind, message: String)?

  // Scored numbers (acts as iOS "history": what we'd block/warn)
  @Published var scoredNumbers: [ScoredNumber] = []

  // My reports
  @Published var myReports: [MyReport] = []

  // Settings
  @Published var warnEnabled: Bool = SharedStore.warnEnabled {
    didSet { SharedStore.warnEnabled = warnEnabled }
  }
  @Published var blockThreshold: Double = Double(SharedStore.blockThreshold) {
    didSet { SharedStore.blockThreshold = Int(blockThreshold) }
  }
  /// Daily game reminder (opt-in). Toggling on requests notification permission
  /// and schedules it; off cancels it. If permission is denied we revert to off.
  @Published var gameReminderEnabled: Bool = SharedStore.gameReminderEnabled {
    didSet {
      guard gameReminderEnabled != oldValue else { return }
      SharedStore.gameReminderEnabled = gameReminderEnabled
      if gameReminderEnabled {
        Task {
          let ok = await GameReminders.enable()
          if !ok { gameReminderEnabled = false }
        }
      } else {
        GameReminders.disable()
      }
    }
  }

  // Report form
  @Published var reportPhone = ""
  @Published var reportIsSpam = true
  @Published var reportCategory: ReportCategory = .telemarketing
  @Published var reportSubmitting = false
  @Published var reportBanner: (kind: BannerKind, message: String)?

  // Detail lookup
  @Published var lookup: LookupResponseDTO?
  @Published var lookupLoading = false

  // Programmatic tab selection (so a shared number can jump to Report).
  @Published var selectedTab: RootTab = .home

  // Reassurance stats (Home banner) + Settings API "mode test".
  @Published var stats: StatsResponseDTO?
  @Published var apiTest: ApiTestState = .idle

  private let listService = ListService()
  private let reportService = ReportService()
  private let api = WhoCalledAPI()

  init() {
    // A daily game-reminder tap deep-links to the Games hub.
    NotificationCenter.default.addObserver(forName: .wcOpenGames, object: nil, queue: .main) { [weak self] _ in
      Task { @MainActor in self?.selectedTab = .games }
    }
  }

  func loadLookup(phone: String) {
    Task {
      lookup = nil
      lookupLoading = true
      lookup = try? await api.lookup(phone)
      lookupLoading = false
    }
  }

  /// Pre-fill the Report tab with a number (from a shared text)
  /// and switch to it.
  func startReport(phone: String) {
    reportPhone = phone
    reportIsSpam = true
    reportBanner = nil
    selectedTab = .report
  }

  /// Load public reassurance stats (best-effort; banner hidden if unavailable).
  func loadStats() {
    Task { stats = try? await api.stats(country: SharedStore.countryDial) }
  }

  /// Settings "mode test": probe API connectivity, surface latency or failure.
  func runApiTest() {
    if case .testing = apiTest { return }  // ignore rapid repeat double-taps
    Task {
      apiTest = .testing
      do {
        let ms = try await api.ping()
        apiTest = .ok(latencyMs: ms)
      } catch {
        apiTest = .failed(reason: Self.describe(error))
      }
    }
  }

  private static func describe(_ error: Error) -> String {
    switch error {
    case APIError.http(let code): return "HTTP \(code)"
    case APIError.invalidURL: return "URL invalide"
    case APIError.decoding: return "Réponse invalide"
    default: return (error as NSError).localizedDescription
    }
  }

  // Privacy (RGPD)
  @Published var privacyBanner: (kind: BannerKind, message: String)?

  func eraseMyData() {
    Task {
      privacyBanner = nil
      do {
        try await api.eraseDevice(SharedStore.deviceId())
        MyReportsStore.clear()  // server wiped — mirror locally
        refresh()
        privacyBanner = (.success, "Vos signalements ont été supprimés.")
      } catch {
        privacyBanner = (.error, "Échec de la suppression. Réessayez.")
      }
    }
  }

  func refresh() {
    let numbers = SharedStore.readScoredNumbers().numbers
    scoredNumbers = numbers
    blockedNumbersCount = numbers.filter { $0.status == "block" }.count
    warnNumbersCount = numbers.filter { $0.status == "warn" }.count
    myReports = MyReportsStore.all()
    checkExtensionStatus()
  }

  func checkExtensionStatus() {
    CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(
      withIdentifier: AppConstants.callDirectoryExtensionId
    ) { [weak self] status, _ in
      Task { @MainActor in self?.isExtensionEnabled = (status == .enabled) }
    }
  }

  func openExtensionSettings() {
    CXCallDirectoryManager.sharedInstance.openSettings { _ in }
  }

  func syncNow() {
    Task {
      isSyncing = true
      syncProgress = 0
      syncBanner = nil
      do {
        try await listService.update { count in Task { @MainActor in self.syncProgress = count } }
        await reportService.retryPending()  // network is up — re-push unsent reports too
        refresh()
        syncBanner = (.success, "Liste à jour ✓")
      } catch {
        syncBanner = (.error, "Échec de la mise à jour. Réessayez.")
      }
      isSyncing = false
    }
  }

  /// Watched-country scope (E.164 dial code; "" = worldwide).
  @Published var countryDial: String = SharedStore.countryDial

  /// First-launch country confirmation banner (non-blocking). Shown until confirmed.
  @Published var countryConfirmed: Bool = SharedStore.countryConfirmed

  func confirmCountry() {
    countryConfirmed = true
    SharedStore.countryConfirmed = true
  }

  /// Change the scope: wipe + re-sync the new country, then refresh the UI.
  func setCountry(_ dial: String) {
    countryDial = dial
    Task {
      isSyncing = true
      syncProgress = 0
      syncBanner = nil
      do {
        try await listService.setCountry(dial) { count in Task { @MainActor in self.syncProgress = count } }
        refresh()
        syncBanner = (.success, "Liste à jour ✓")
      } catch {
        syncBanner = (.error, "Échec de la mise à jour. Réessayez.")
      }
      isSyncing = false
    }
  }

  private var lastReportedPhone: String?

  func submitReport() {
    Task {
      reportSubmitting = true
      reportBanner = nil
      let submittedPhone = PhoneNormalizer.normalize(reportPhone)
      let isRepeat = reportPhone.trimmingCharacters(in: .whitespaces) == lastReportedPhone
      lastReportedPhone = reportPhone.trimmingCharacters(in: .whitespaces)
      do {
        try await reportService.report(
          rawPhone: reportPhone, isSpam: reportIsSpam,
          category: reportIsSpam ? reportCategory : nil)
        reportBanner = isRepeat
          ? (.success, "C'est bien noté 😊 Pas besoin d'insister — un signalement suffit !")
          : (.success, "Merci ! Signalement enregistré 🛡️")
        if let submittedPhone { loadLookup(phone: submittedPhone) }
        reportPhone = ""
      } catch ReportError.invalidPhone {
        reportBanner = (.error, "Numéro invalide — vérifiez le format.")
      } catch APIError.http(429) {
        reportBanner = (.error, "Limite quotidienne de signalements atteinte — votre avis sera renvoyé automatiquement.")
      } catch {
        reportBanner = (.error, "Enregistré localement. Envoi au serveur échoué.")
      }
      refresh()
      reportSubmitting = false
    }
  }

  func deleteReport(phone: String) {
    Task {
      await reportService.delete(phone: phone)
      refresh()
    }
  }

  /// Flip a report spam ↔ legit, keeping its original category when it goes
  /// back to spam — without touching the Signaler form.
  func flipReport(_ report: MyReport) {
    Task {
      let toSpam = report.vote != "spam"
      // No fallback to .other: a nil category lets the service keep whatever
      // reason the row already holds (and the server accepts a missing one).
      try? await reportService.report(
        rawPhone: report.phone, isSpam: toSpam,
        category: toSpam ? ReportCategory.from(report.category) : nil)
      refresh()
    }
  }

  /// Re-push one unsent report (the "Renvoyer" action in Mes signalements).
  func retryReport(_ report: MyReport) {
    Task {
      await reportService.retry(report)
      refresh()
    }
  }

  /// Best-effort re-push of every unsent report (app start / back to foreground).
  func retryPendingReports() {
    Task {
      await reportService.retryPending()
      refresh()
    }
  }
}

/// Result of the Settings API connectivity probe.
enum ApiTestState {
  case idle
  case testing
  case ok(latencyMs: Int)
  case failed(reason: String)
}
