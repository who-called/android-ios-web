import SwiftUI

struct NumberDetailView: View {
  @EnvironmentObject private var viewModel: MainViewModel
  let number: ScoredNumber

  private var blocked: Bool { number.status == "block" }
  private var statusColor: Color { blocked ? WhoCalledColors.coral : WhoCalledColors.amber }
  private var isArcep: Bool { viewModel.lookup?.source == "arcep" || number.source == "arcep" }

  var body: some View {
    ScrollToTopScreen {
      VStack(spacing: 16) {
        // Compact header: the gauge sits beside the number/badges (not stacked
        // below), so the action buttons stay above the fold without scrolling.
        BorderedCard(accent: statusColor) {
          HStack(spacing: 16) {
            ScoreGauge(score: viewModel.lookup?.spamScore ?? number.spamScore, diameter: 72)
            VStack(alignment: .leading, spacing: 6) {
              Text("+\(number.phone)").font(.title3.bold())
              HStack(spacing: 8) {
                StatusBadge(
                  label: blocked ? "Bloqué" : "Alerté", color: statusColor,
                  systemImage: blocked ? "nosign" : "exclamationmark.triangle.fill")
                sourceBadge
              }
              Text("Indice de spam").font(.caption).foregroundStyle(WhoCalledColors.muted)
            }
            Spacer(minLength: 0)
          }
          .frame(maxWidth: .infinity)
        }

        // Source explanation
        BorderedCard {
          VStack(alignment: .leading, spacing: 2) {
            Text(isArcep ? "Liste officielle ARCEP" : "Signalé par la communauté")
              .font(.subheadline.weight(.semibold))
            Text(isArcep
              ? "Ce numéro fait partie de la liste officielle des préfixes de démarchage (ARCEP / opérateurs). Il est bloqué indépendamment des signalements."
              : "Ce numéro est évalué à partir des signalements de la communauté.")
              .font(.footnote).foregroundStyle(WhoCalledColors.muted)
            if isArcep {
              // Store "misleading claims" policies: government info must link
              // to its official source, with a non-affiliation disclaimer.
              Button {
                if let url = URL(string: AppConstants.Links.arcepSource) {
                  UIApplication.shared.open(url)
                }
              } label: {
                Label("Source officielle : plan de numérotation (arcep.fr)", systemImage: "arrow.up.right.square")
                  .font(.footnote)
              }
              .padding(.top, 4)
              Text("Who Called est une application indépendante, non affiliée à l’ARCEP ni à aucune entité gouvernementale.")
                .font(.caption2).foregroundStyle(WhoCalledColors.muted)
                .padding(.top, 2)
            }
          }
        }

        detailRow("Type d’appel", ReportCategory.from(viewModel.lookup?.category ?? number.category)?.label ?? "Inconnu")

        if viewModel.lookupLoading {
          BorderedCard {
            HStack { ProgressView(); Text("Chargement des statistiques…").font(.footnote) }
          }
        } else if let stats = viewModel.lookup {
          statsCard(stats)
        }

        Button {
          viewModel.reportPhone = "+\(number.phone)"
          viewModel.reportIsSpam = true
          viewModel.submitReport()
        } label: {
          Label("Confirmer comme indésirable", systemImage: "flag.fill").frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .tint(WhoCalledColors.indigo)
        .disabled(viewModel.reportSubmitting)

        // Feedback so tapping the button never feels like a no-op.
        if let banner = viewModel.reportBanner {
          FeedbackBanner(kind: banner.kind, message: banner.message)
        }

        Button {
          UIPasteboard.general.string = "+\(number.phone)"
        } label: {
          Label("Copier le numéro", systemImage: "doc.on.doc").frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
      }
      .padding()
    }
    .navigationTitle("Détail")
    .navigationBarTitleDisplayMode(.inline)
    .onAppear {
      viewModel.reportBanner = nil // no stale banner from a previous action
      viewModel.loadLookup(phone: number.phone)
    }
  }

  private var sourceBadge: some View {
    isArcep
      ? StatusBadge(label: "ARCEP", color: WhoCalledColors.nightBlue, systemImage: "checkmark.seal.fill")
      : StatusBadge(label: "Communauté", color: WhoCalledColors.muted, systemImage: "person.3.fill")
  }

  private func statsCard(_ stats: LookupResponseDTO) -> some View {
    BorderedCard {
      VStack(alignment: .leading, spacing: 10) {
        Text("Signalements").font(.subheadline.weight(.semibold))
        HStack {
          stat("Indésirable", "\(stats.reportCountSpam ?? 0)", WhoCalledColors.coral)
          Spacer()
          stat("Légitime", "\(stats.reportCountLegit ?? 0)", WhoCalledColors.emerald)
        }
        // Community "why did it call?" headline — only when we have a known reason.
        if let top = stats.topReason, top.category != "unknown", top.count > 0 {
          let label = ReportCategory.from(top.category)?.label ?? top.category
          Text("Le plus souvent : \(label) (\(top.share) %)")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(WhoCalledColors.coral)
            .padding(.top, 4)
        }
        Text("Fréquence des signalements").font(.subheadline.weight(.semibold)).padding(.top, 6)
        HStack {
          stat("24 h", "\(stats.frequency?.last24h ?? 0)", WhoCalledColors.indigo)
          Spacer()
          stat("7 j", "\(stats.frequency?.last7d ?? 0)", WhoCalledColors.indigo)
          Spacer()
          stat("30 j", "\(stats.frequency?.last30d ?? 0)", WhoCalledColors.indigo)
          Spacer()
          stat("1 an", "\(stats.frequency?.last1y ?? 0)", WhoCalledColors.indigo)
        }
        if let last = parseDate(stats.lastReportedAt) {
          Text("Dernier signalement : \(frenchDate(last))")
            .font(.footnote).foregroundStyle(WhoCalledColors.muted).padding(.top, 4)
        }
      }
    }
  }

  private func stat(_ label: String, _ value: String, _ color: Color) -> some View {
    VStack {
      Text(value).font(.title3.bold()).foregroundStyle(color)
      Text(label).font(.caption).foregroundStyle(WhoCalledColors.muted)
    }
  }

  private func detailRow(_ label: String, _ value: String) -> some View {
    BorderedCard {
      HStack {
        Text(label).foregroundStyle(WhoCalledColors.muted)
        Spacer()
        Text(value).fontWeight(.semibold)
      }
    }
  }

  private func parseDate(_ s: String?) -> Date? {
    guard let s else { return nil }
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return f.date(from: s) ?? ISO8601DateFormatter().date(from: s)
  }

  private func frenchDate(_ date: Date) -> String {
    let f = DateFormatter()
    f.locale = Locale(identifier: "fr_FR")
    f.dateStyle = .long
    f.timeStyle = .short
    return f.string(from: date)
  }
}
