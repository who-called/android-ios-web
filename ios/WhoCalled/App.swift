import SwiftUI
import UserNotifications

@main
struct WhoCalledApp: App {
  @StateObject private var viewModel = MainViewModel()

  var body: some Scene {
    WindowGroup {
      RootView()
        .environmentObject(viewModel)
        .task {
          // Preload the embedded warm-up (real FR spam DB) so the app works
          // offline on first launch, then fall back to mock data in debug only.
          let firstLaunch = await WarmupLoader.loadIfNeeded()
          DebugSeeder.seedIfNeeded()
          viewModel.refresh()
          // First time: fetch the live list right away — no button tap needed.
          if firstLaunch { viewModel.syncNow() }
          // Route reminder taps to the Games hub + keep the opt-in reminder scheduled.
          UNUserNotificationCenter.current().delegate = GameNotificationDelegate.shared
          if SharedStore.gameReminderEnabled { GameReminders.schedule() }
        }
    }
  }
}

/// Tabs identified so a shared number / trending tap can switch programmatically.
enum RootTab: Hashable {
  case home, report, myReports, games, settings
}

struct RootView: View {
  @EnvironmentObject private var viewModel: MainViewModel
  @State private var showSplash = true

  var body: some View {
    ZStack {
      tabs
      if showSplash {
        // Brief branded splash on launch — shield + the "Who Called" name.
        BrandedLoader()
          .background(Color(.systemBackground))
          .transition(.opacity)
          .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) {
              withAnimation(.easeOut(duration: 0.3)) { showSplash = false }
            }
          }
      }
    }
  }

  private var tabs: some View {
    TabView(selection: $viewModel.selectedTab) {
      HomeView()
        .tabItem { Label("Accueil", systemImage: "house.fill") }
        .tag(RootTab.home)
      ReportView()
        .tabItem { Label("Signaler", systemImage: "flag.fill") }
        .tag(RootTab.report)
      MyReportsView()
        .tabItem { Label("Signalements", systemImage: "list.bullet.rectangle.fill") }
        .tag(RootTab.myReports)
      GamesView()
        .tabItem { Label("Jeux", systemImage: "gamecontroller.fill") }
        .tag(RootTab.games)
      SettingsView()
        .tabItem { Label("Réglages", systemImage: "gearshape.fill") }
        .tag(RootTab.settings)
    }
    .tint(WhoCalledColors.indigo)
    .onOpenURL { url in handleSharedURL(url) }
  }

  /// Handle the custom URL the Share Extension opens (whocalled://report?phone=…),
  /// pre-filling the Report tab. No SMS permission — the user explicitly shared.
  private func handleSharedURL(_ url: URL) {
    guard url.scheme == "whocalled", url.host == "report" else { return }
    let comps = URLComponents(url: url, resolvingAgainstBaseURL: false)
    let raw = comps?.queryItems?.first(where: { $0.name == "phone" })?.value
    guard let raw, !raw.isEmpty else { return }
    viewModel.startReport(phone: raw)
  }
}

/// Who Called palette — vivid royal blue brand on clean white. Navy is kept only
/// for the header gradient's deep end. No violet.
enum WhoCalledColors {
  static let blue = Color(red: 0.145, green: 0.388, blue: 0.922)  // #2563EB vivid brand
  static let blueDark = Color(red: 0.114, green: 0.306, blue: 0.847)  // #1D4ED8 pressed
  static let blueBright = Color(red: 0.231, green: 0.510, blue: 0.965)  // #3B82F6
  static let nightBlue = Color(red: 0.082, green: 0.161, blue: 0.302)  // #15294D gradient end
  static let nightBlueDark = Color(red: 0.063, green: 0.122, blue: 0.235)  // #101F3C
  static let indigo = blue  // aliases now point to the vivid blue brand
  static let indigoDeep = blueDark
  static let violet = blue
  static let amber = Color(red: 0.96, green: 0.62, blue: 0.04)  // #F59E0B
  static let emerald = Color(red: 0.06, green: 0.73, blue: 0.51)  // #10B981
  static let coral = Color(red: 0.94, green: 0.27, blue: 0.27)  // #EF4444
  static let border = Color(red: 0.886, green: 0.910, blue: 0.961)  // #E2E8F5 hairline
  static let muted = Color(red: 0.392, green: 0.455, blue: 0.545)  // #64748B
}
