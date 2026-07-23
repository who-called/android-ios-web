import SwiftUI

/// Anonymous DEFENSE leaderboard — Day / Week / All-time, your position, and your
/// own history. Never blank: falls back to your local scores when the server has
/// no data yet (or is unreachable).
struct LeaderboardView: View {
  @Environment(\.dismiss) private var dismiss
  var game: String = "defense"
  private let today = GameStore.epochDay()
  private let periods: [(key: String, label: String)] = [("day", "Aujourd'hui"), ("week", "Semaine"), ("all", "Tout temps")]
  @State private var period = "day"
  @State private var board: LeaderboardResponseDTO?
  @State private var history: [HistoryEntryDTO] = []
  @State private var localHist: [GameStore.LocalDay] = []
  @State private var loading = false

  private func dayLabel(_ day: Int) -> String {
    switch today - day { case 0: return "Aujourd'hui"; case 1: return "Hier"; default: return "il y a \(today - day) j" }
  }
  private func medal(_ rank: Int) -> String { rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : "#\(rank)" }
  private var gameName: String { game == "trace" ? "TRACE" : "DEFENSE" }
  private func unit(_ waves: Int) -> String { game == "trace" ? "\(waves) grille\(waves > 1 ? "s" : "")" : "vague \(waves)" }

  private var localBest: GameStore.LocalDay? {
    switch period {
    case "day": return localHist.first { $0.day == today }
    case "week": return localHist.filter { $0.day > today - 7 }.max { $0.score < $1.score }
    default: return localHist.max { $0.score < $1.score }
    }
  }

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 14) {
          Picker("Période", selection: $period) {
            ForEach(periods, id: \.key) { Text($0.label).tag($0.key) }
          }.pickerStyle(.segmented).padding(.horizontal)

          let players = board?.players ?? 0
          BorderedCard(accent: WhoCalledColors.blue) {
            VStack(alignment: .leading, spacing: 2) {
              Text(periods.first { $0.key == period }?.label ?? "").fontWeight(.bold)
              Text(players > 0
                ? "\(players) joueur\(players > 1 ? "s" : "") · meilleur \(board?.bestScore ?? 0) · médiane \(board?.medianScore ?? 0)"
                : (localBest != nil ? "1 joueur (toi) · meilleur \(localBest!.score)" : "Sois le premier à marquer un score ! 🚀"))
                .font(.caption).foregroundStyle(.secondary)
            }
          }

          BorderedCard(accent: WhoCalledColors.emerald) {
            VStack(alignment: .leading, spacing: 2) {
              HStack {
                Text("Ton classement").fontWeight(.bold).foregroundStyle(WhoCalledColors.emerald)
                Spacer()
                Text(board?.you != nil ? "#\(board!.you!.rank)/\(players)" : (localBest != nil ? "#1/1" : "—"))
                  .fontWeight(.bold).foregroundStyle(WhoCalledColors.emerald)
              }
              if let you = board?.you {
                Text("Top \(you.topPercent)% · \(you.score) pts (\(unit(you.waves)))")
              } else if let lb = localBest {
                Text("\(lb.score) pts (\(unit(lb.waves)))")
              } else {
                Text("Pas encore joué — lance une partie !").foregroundStyle(.secondary)
              }
            }
          }

          Text("Top").fontWeight(.bold).padding(.horizontal)
          if let top = board?.top, !top.isEmpty {
            ForEach(top) { e in
              HStack {
                Text(medal(e.rank)).fontWeight(.bold).frame(width: 40, alignment: .leading)
                Text("\(e.score) pts").fontWeight(.semibold); Spacer()
                Text(unit(e.waves)).font(.caption).foregroundStyle(.secondary)
              }.padding(.horizontal).padding(.vertical, 6)
            }
          } else if let lb = localBest {
            HStack { Text("🥇").fontWeight(.bold).frame(width: 40, alignment: .leading)
              Text("\(lb.score) pts").fontWeight(.semibold); Spacer()
              Text("toi").font(.caption).foregroundStyle(.secondary) }.padding(.horizontal).padding(.vertical, 6)
            Text("Ton score rejoindra le classement à la synchro.").font(.caption).foregroundStyle(.secondary).padding(.horizontal)
          } else {
            Text("Aucun score. À toi de jouer !").font(.caption).foregroundStyle(.secondary).padding(.horizontal)
          }

          Text("Ton historique").fontWeight(.bold).padding(.horizontal).padding(.top, 6)
          if !history.isEmpty {
            ForEach(history) { h in
              HStack { Text(dayLabel(h.day)); Spacer(); Text("\(h.score) pts").fontWeight(.semibold)
                Text("#\(h.rank)/\(h.players) · top \(h.topPercent)%").font(.caption).foregroundStyle(.secondary)
              }.padding(.horizontal).padding(.vertical, 5)
            }
          } else if !localHist.isEmpty {
            ForEach(localHist, id: \.day) { h in
              HStack { Text(dayLabel(h.day)); Spacer(); Text("\(h.score) pts").fontWeight(.semibold)
                Text(unit(h.waves)).font(.caption).foregroundStyle(.secondary)
              }.padding(.horizontal).padding(.vertical, 5)
            }
          } else {
            Text("Joue une partie pour démarrer ton historique.").font(.caption).foregroundStyle(.secondary).padding(.horizontal)
          }

          if loading && board == nil { ProgressView().frame(maxWidth: .infinity).padding() }
        }.padding(.vertical)
      }
      .navigationTitle("Classement \(gameName)")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } } }
      .task(id: period) { await loadBoard() }
      .task { localHist = GameStore.localHistory(game); history = (try? await WhoCalledAPI().gameHistory(game: game))?.entries ?? [] }
    }
  }

  private func loadBoard() async {
    loading = true; board = try? await WhoCalledAPI().gameLeaderboard(period: period, day: today, game: game); loading = false
  }
}
