import SwiftUI

/// Per-game daily streak ("Série quotidienne") — a big sun, the current + best
/// streak, and a 7-day activity strip. Mirrors the Android `StreakScreen`.
struct StreakView: View {
  @Environment(\.dismiss) private var dismiss
  let game: String
  private let today = GameStore.epochDay()
  @State private var state: GameStore.State?
  @State private var playedDays: Set<Int> = []
  @State private var pulse = false
  @State private var numAppeared = false

  private var gameName: String { game == "trace" ? "TRACE" : "DEFENSE" }
  private var streak: Int { state?.streak ?? 0 }
  private var best: Int { state?.bestStreak ?? 0 }
  private var playedToday: Bool { state?.lastPlayedDay == today }

  private func weekdayFr(_ epochDay: Int) -> String {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = TimeZone(identifier: "UTC") ?? .current
    let date = Date(timeIntervalSince1970: Double(epochDay) * 86_400)
    let wd = cal.component(.weekday, from: date) // 1=Sun … 7=Sat
    return ["DIM", "LUN", "MAR", "MER", "JEU", "VEN", "SAM"][max(0, min(6, wd - 1))]
  }

  var body: some View {
    VStack(spacing: 0) {
      HStack {
        Button { dismiss() } label: { Image(systemName: "chevron.left").font(.title3) }.foregroundStyle(.primary)
        VStack(alignment: .leading, spacing: 1) {
          Text("Série quotidienne").font(.title3.bold())
          Text(gameName).font(.caption.weight(.semibold)).foregroundStyle(WhoCalledColors.amber)
        }
        Spacer()
      }
      .padding()

      Spacer().frame(height: 20)
      Text("☀️").font(.system(size: 100))
        .scaleEffect(pulse ? (playedToday ? 1.12 : 1.06) : 1.0)
        .animation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true), value: pulse)

      Spacer().frame(height: 12)
      Text("\(streak)").font(.system(size: 64, weight: .black)).foregroundStyle(WhoCalledColors.amber)
        .scaleEffect(numAppeared ? 1 : 0.4)
        .animation(.spring(response: 0.45, dampingFraction: playedToday ? 0.4 : 0.6), value: numAppeared)
      Text("Série actuelle").font(.title3.bold()).foregroundStyle(WhoCalledColors.amber)

      Spacer().frame(height: 10)
      Text("Meilleure série : \(best)")
        .font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
        .padding(.horizontal, 16).padding(.vertical, 8)
        .background(Capsule().fill(Color(.secondarySystemBackground)))

      Spacer().frame(height: 28)
      HStack(spacing: 6) {
        ForEach((today - 6)...today, id: \.self) { d in
          VStack(spacing: 6) {
            Text(weekdayFr(d)).font(.caption2.weight(.bold))
              .foregroundStyle(d == today ? WhoCalledColors.amber : .secondary)
            ZStack {
              Circle()
                .fill(playedDays.contains(d) ? WhoCalledColors.amber : Color(.secondarySystemBackground))
                .frame(width: 38, height: 38)
              if playedDays.contains(d) {
                Text("✓").foregroundStyle(.white).fontWeight(.bold)
              } else if d == today {
                Text("·").foregroundStyle(WhoCalledColors.amber).fontWeight(.bold)
              }
            }
          }
          .frame(maxWidth: .infinity)
        }
      }
      .padding(.horizontal, 16)

      Spacer().frame(height: 24)
      Text(playedToday ? "Bien joué — série tenue aujourd'hui ! 🔥" : "Joue aujourd'hui pour continuer ta série ☀️")
        .fontWeight(.semibold).multilineTextAlignment(.center)
        .foregroundStyle(playedToday ? WhoCalledColors.emerald : .primary)
        .padding(.horizontal, 24)
      Text(streak >= 7 ? "Palier 7 jours atteint ! 🎁" : "Prochain palier : 7 jours 🎁")
        .font(.caption).foregroundStyle(.secondary).padding(.top, 6)

      Spacer()
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(
      LinearGradient(colors: [WhoCalledColors.amber.opacity(0.20), Color(.systemBackground)], startPoint: .top, endPoint: .center)
        .ignoresSafeArea())
    .onAppear {
      state = GameStore.state(game)
      playedDays = Set(GameStore.localHistory(game).map { $0.day })
      pulse = true
      numAppeared = true
    }
  }
}
