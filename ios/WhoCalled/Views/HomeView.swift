import SwiftUI

struct HomeView: View {
  @EnvironmentObject private var viewModel: MainViewModel
  @State private var showSmsSetup = false
  @State private var showCountryPicker = false
  @State private var community: CommunityResponseDTO?

  private var gameSubtitle: String {
    let s = GameStore.state()
    let playedToday = s.lastPlayedDay == GameStore.epochDay()
    if playedToday {
      let left = max(0, GameStore.maxAttempts - s.attemptsToday)
      return "Meilleur : \(s.bestScoreToday) pts" + (left > 0 ? " · \(left) essai\(left > 1 ? "s" : "") restant\(left > 1 ? "s" : "")" : " · practice")
    }
    return "Bloque un max de spams. 3 essais classés/jour."
  }

  var body: some View {
    NavigationStack {
      ScrollToTopScreen {
        GradientHeader(
          title: "Who Called",
          subtitle: viewModel.isExtensionEnabled ? "Vous êtes protégé" : "Protection à activer")
          .padding([.horizontal, .top])
      } content: {
        VStack(alignment: .leading, spacing: 16) {
          if !viewModel.countryConfirmed {
            countryBanner
          }
          if viewModel.isExtensionEnabled {
            protectionCard
          } else {
            BorderedCard(accent: WhoCalledColors.coral) {
              VStack(alignment: .leading, spacing: 8) {
                Text("Le bloqueur n’est pas activé").font(.headline)
                Text("Activez Who Called dans Réglages → Téléphone → Blocage d’appels et identification.")
                  .font(.subheadline)
                Button {
                  viewModel.openExtensionSettings()
                } label: {
                  Label("Ouvrir les réglages", systemImage: "power")
                }
                .buttonStyle(.borderedProminent)
                .tint(WhoCalledColors.indigo)
              }
            }
          }

          // Community shield — compact shared counter (the collective mission).
          if let c = community {
            BorderedCard(accent: WhoCalledColors.blue) {
              VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 12) {
                  Text("🛡️").font(.title3)
                  VStack(alignment: .leading, spacing: 1) {
                    Text("Bouclier communautaire").fontWeight(.semibold).foregroundStyle(.primary)
                    Text("\(c.total) signalements ensemble · +\(c.week) cette semaine")
                      .font(.caption).foregroundStyle(WhoCalledColors.muted)
                  }
                }
                ProgressView(value: min(1, Double(c.week) / Double(max(c.goal, 1)))).tint(WhoCalledColors.blue)
              }
            }
          }

          // Jeux — daily games hub (retention + shareable streak).
          Button {
            viewModel.selectedTab = .games
          } label: {
            BorderedCard(accent: WhoCalledColors.blue) {
              HStack(spacing: 12) {
                Text("🎮").font(.title3)
                VStack(alignment: .leading, spacing: 1) {
                  Text("Jeux — défis du jour & classements").fontWeight(.semibold).foregroundStyle(.primary)
                  Text(gameSubtitle).font(.caption).foregroundStyle(WhoCalledColors.muted)
                }
                Spacer()
                Text(GameStore.state("defense").streak > 0 ? "\(GameStore.state("defense").streak)🔥" : "Jouer")
                  .font(.caption.bold()).foregroundStyle(WhoCalledColors.blue)
              }
            }
          }
          .buttonStyle(.plain)

          // SMS shield entry — green, distinct from the call card.
          Button {
            showSmsSetup = true
          } label: {
            BorderedCard(accent: WhoCalledColors.emerald) {
              HStack(spacing: 12) {
                Image(systemName: "message.fill")
                  .font(.title3).foregroundStyle(WhoCalledColors.emerald)
                VStack(alignment: .leading, spacing: 1) {
                  Text("Filtre SMS").fontWeight(.semibold).foregroundStyle(.primary)
                  Text("Masquez les SMS indésirables")
                    .font(.caption).foregroundStyle(WhoCalledColors.muted)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(WhoCalledColors.muted)
              }
            }
          }
          .buttonStyle(.plain)

          Button {
            viewModel.syncNow()
          } label: {
            Label(viewModel.isSyncing ? "Mise à jour…" : "Mettre à jour la liste",
              systemImage: "arrow.clockwise")
              .frame(maxWidth: .infinity)
          }
          .buttonStyle(.bordered)
          .disabled(viewModel.isSyncing)

          // Live progress: animated bar + climbing counter while syncing.
          if viewModel.isSyncing {
            ProgressView()
              .progressViewStyle(.linear)
              .tint(WhoCalledColors.blue)
            Text(
              viewModel.syncProgress > 0
                ? "\(Self.grouped(viewModel.syncProgress)) numéros récupérés…"
                : "Connexion au serveur…"
            )
            .font(.caption).foregroundStyle(.secondary)
          }

          if let banner = viewModel.syncBanner {
            FeedbackBanner(kind: banner.kind, message: banner.message)
          }
        }
        .padding()
      }
      .sheet(isPresented: $showSmsSetup) { SmsFilterSetupView() }
      .navigationTitle("Who Called")
      .navigationBarTitleDisplayMode(.inline)
      .onAppear {
        viewModel.refresh()
        viewModel.loadStats()
      }
      .task { if community == nil { community = try? await WhoCalledAPI().community() } }
    }
  }

  /// "Bloqueur actif et à jour" card (à la Saracroche, in our emerald): green
  /// shield headline, DB coverage figure, auto-update freshness, and a tappable
  /// First-launch country confirmation (non-blocking). Detected silently; the
  /// user can confirm (✕) or change it (Modifier → picker) once.
  private var countryBanner: some View {
    let c = Countries.byDial(viewModel.countryDial)
    return BorderedCard(accent: WhoCalledColors.blue) {
      HStack(spacing: 10) {
        Image(systemName: "globe").foregroundColor(WhoCalledColors.blue)
        VStack(alignment: .leading, spacing: 2) {
          Text("Numéros surveillés : \(c.flag) \(c.name)").font(.subheadline.weight(.semibold))
          Text("Détecté automatiquement. Touchez « Modifier » pour changer.")
            .font(.caption).foregroundColor(.secondary)
        }
        Spacer(minLength: 4)
        Button("Modifier") { showCountryPicker = true }.font(.subheadline.weight(.semibold))
        Button { viewModel.confirmCountry() } label: {
          Image(systemName: "xmark").foregroundColor(.secondary)
        }
      }
    }
    .confirmationDialog("Pays surveillé", isPresented: $showCountryPicker, titleVisibility: .visible) {
      ForEach(Countries.list) { c in
        Button("\(c.flag)  \(c.name)") {
          viewModel.setCountry(c.dial)
          viewModel.confirmCountry()
        }
      }
      Button("Annuler", role: .cancel) {}
    }
  }

  /// "Numéros surveillés" row (iOS has no per-call journal, so we link to the
  /// watched-numbers list — what would be blocked/warned).
  private var protectionCard: some View {
    VStack(alignment: .leading, spacing: 0) {
      HStack(spacing: 10) {
        Image(systemName: "checkmark.shield.fill")
          .font(.title3).foregroundStyle(WhoCalledColors.emerald)
        Text("Bloqueur actif et à jour").font(.headline)
      }

      Divider().padding(.vertical, 14)

      if let stats = viewModel.stats {
        HStack {
          Image(systemName: "cylinder.split.1x2.fill")
            .foregroundStyle(WhoCalledColors.emerald)
          Text("Numéros couverts")
          Spacer()
          Text(Self.grouped(stats.coveredNumbers ?? 0)).fontWeight(.bold)
        }
        .padding(.vertical, 4)
        VStack(alignment: .leading, spacing: 2) {
          Text(Self.freshnessLabel(stats.lastUpdate))
          // Per-country breakdown (matches the device's scope).
          if let cn = stats.countryNumbers {
            let c = Countries.byDial(stats.country ?? "")
            let arcep = stats.countryArcepPatternCount.map { " · \(Self.grouped($0)) préfixes ARCEP" } ?? ""
            Text("\(c.flag) \(c.name) : \(Self.grouped(cn)) numéros\(arcep)")
          }
        }
        .font(.caption).foregroundStyle(WhoCalledColors.nightBlue)
        .padding(.bottom, 6)
      }

      NavigationLink {
        WatchedNumbersView()
      } label: {
        countRow(
          icon: "nosign", color: WhoCalledColors.coral,
          label: "Numéros bloqués",
          subtitle: "Rejetés automatiquement · voir la liste",
          count: viewModel.blockedNumbersCount)
      }
      .buttonStyle(.plain)

      NavigationLink {
        WatchedNumbersView()
      } label: {
        countRow(
          icon: "exclamationmark.triangle.fill", color: WhoCalledColors.amber,
          label: "Numéros suspects",
          subtitle: "Étiquetés à l’appel · voir la liste",
          count: viewModel.warnNumbersCount)
      }
      .buttonStyle(.plain)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(18)
    .background(WhoCalledColors.emerald.opacity(0.10))
    .overlay(
      RoundedRectangle(cornerRadius: 16)
        .stroke(WhoCalledColors.emerald.opacity(0.35), lineWidth: 1))
    .clipShape(RoundedRectangle(cornerRadius: 16))
  }

  private func countRow(icon: String, color: Color, label: String, subtitle: String, count: Int) -> some View {
    HStack {
      Image(systemName: icon).foregroundStyle(color)
      VStack(alignment: .leading, spacing: 1) {
        Text(label).fontWeight(.semibold).foregroundStyle(.primary)
        Text(count == 0 ? "Aucun pour l’instant" : subtitle)
          .font(.caption).foregroundStyle(WhoCalledColors.muted)
      }
      Spacer()
      Text("\(count)").font(.title3.bold()).foregroundStyle(color)
      Image(systemName: "chevron.right").foregroundStyle(WhoCalledColors.muted)
    }
    .padding(.vertical, 6)
  }

  private static func grouped(_ n: Int) -> String {
    let f = NumberFormatter()
    f.numberStyle = .decimal
    f.locale = Locale(identifier: "fr_FR")
    return f.string(from: NSNumber(value: n)) ?? "\(n)"
  }

  private static func freshnessLabel(_ iso: String?) -> String {
    guard let iso else { return "Mise à jour automatique" }
    let parser = ISO8601DateFormatter()
    parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let date = parser.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
    guard let date else { return "Mise à jour automatique" }
    let days = Int(Date().timeIntervalSince(date) / 86_400)
    switch days {
    case ..<1: return "Mise à jour automatique · à jour aujourd’hui"
    case 1: return "Mise à jour automatique · hier"
    case 2..<30: return "Mise à jour automatique · il y a \(days) j"
    default:
      let df = DateFormatter()
      df.locale = Locale(identifier: "fr_FR")
      df.dateStyle = .medium
      return "Mise à jour automatique · le \(df.string(from: date))"
    }
  }


}
