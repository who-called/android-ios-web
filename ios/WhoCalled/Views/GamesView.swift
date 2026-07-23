import SwiftUI

/// Games hub — the "Jeux" tab. Lists every mini-game with its own best/streak and
/// routes to the game + its dedicated leaderboard. Room for more games later.
struct GamesView: View {
  @State private var showDefense = false
  @State private var showTrace = false
  @State private var showTracePractice = false
  @State private var streakGame: GameID?
  @State private var refresh = 0

  private struct GameID: Identifiable { var id: String }

  var body: some View {
    let _ = refresh
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 8) {
          gameCard(
            emoji: "🛡️", title: "DEFENSE", accent: WhoCalledColors.coral, game: "defense",
            tagline: "Arcade — détruis les appels spam.",
            onPlay: { showDefense = true })
          gameCard(
            emoji: "🕵️", title: "TRACE", accent: WhoCalledColors.blue, game: "trace",
            tagline: "Puzzle — démasque le fraudeur caché.",
            onPlay: { showTrace = true })
          // Practice lives outside the cards so both games present identically.
          Button { showTracePractice = true } label: {
            Text("🎯 Entraînement libre TRACE — non classé").font(.caption.weight(.semibold))
          }
          .buttonStyle(.plain).foregroundStyle(WhoCalledColors.blue).padding(.horizontal, 6).padding(.top, 2)
          Text("Nouveau défi chaque jour · 3 parties classées / jour · 100 % anonyme.")
            .font(.caption).foregroundStyle(WhoCalledColors.muted).padding(.horizontal, 6).padding(.top, 4)
        }
        .padding()
      }
      .navigationTitle("Jeux")
      .fullScreenCover(isPresented: $showDefense, onDismiss: { refresh += 1 }) { DefenseGameView() }
      .fullScreenCover(isPresented: $showTrace, onDismiss: { refresh += 1 }) { TraceGameView(ranked: true) }
      .fullScreenCover(isPresented: $showTracePractice) { TraceGameView(ranked: false) }
      .sheet(item: $streakGame) { g in StreakView(game: g.id) }
    }
  }

  private func gameCard(emoji: String, title: String, accent: Color, game: String, tagline: String, onPlay: @escaping () -> Void) -> some View {
    let s = GameStore.state(game)
    let playedToday = s.lastPlayedDay == GameStore.epochDay()
    let attemptsLeft = max(0, GameStore.maxAttempts - (playedToday ? s.attemptsToday : 0))
    return BorderedCard(accent: accent) {
      VStack(alignment: .leading, spacing: 10) {
        HStack(spacing: 12) {
          Text(emoji).font(.title)
          VStack(alignment: .leading, spacing: 1) {
            HStack(spacing: 8) {
              Text(title).font(.headline)
              // Tappable daily-streak chip → the "Série quotidienne" screen.
              Button { streakGame = GameID(id: game) } label: {
                Text("☀️ \(s.streak)").font(.caption.bold()).foregroundStyle(WhoCalledColors.amber)
                  .padding(.horizontal, 8).padding(.vertical, 3)
                  .background(Capsule().fill(WhoCalledColors.amber.opacity(0.14)))
              }.buttonStyle(.plain)
            }
            Text(tagline).font(.caption).foregroundStyle(WhoCalledColors.muted)
          }
        }
        // Leaderboard link removed here — it lives inside the game screen itself.
        Text(s.bestScoreAllTime > 0 ? "Record : \(s.bestScoreAllTime) pts" : "Pas encore joué")
          .font(.caption.weight(.semibold))
        Button(action: onPlay) {
          Text(!playedToday ? "Jouer le défi du jour" : (attemptsLeft > 0 ? "Jouer (\(attemptsLeft) essai\(attemptsLeft > 1 ? "s" : "") classé\(attemptsLeft > 1 ? "s" : ""))" : "Rejouer (entraînement)"))
            .fontWeight(.bold).frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent).tint(accent)
      }
    }
    // The whole card opens the game — not just the "Jouer" button.
    .contentShape(Rectangle())
    .onTapGesture(perform: onPlay)
  }
}
