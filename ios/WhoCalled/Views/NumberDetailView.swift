import SwiftUI

struct NumberDetailView: View {
  @EnvironmentObject private var viewModel: MainViewModel
  @Environment(\.openURL) private var openURL
  @State private var pendingContactURL: URL?
  @State private var showRiskWarning = false
  let number: ScoredNumber

  private var status: String { viewModel.lookup?.status ?? number.status }
  private var statusColor: Color {
    switch status {
    case "block": WhoCalledColors.coral
    case "warn": WhoCalledColors.amber
    case "allow": WhoCalledColors.emerald
    default: WhoCalledColors.blue
    }
  }
  private var source: String { viewModel.lookup?.source ?? number.source }
  private var isArcep: Bool { source == "arcep" || source == "mixed" || number.source == "arcep" }
  private var hasCommunityData: Bool {
    source == "community" || source == "mixed" ||
      (viewModel.lookup?.reportCountSpam ?? 0) > 0 ||
      (viewModel.lookup?.reportCountLegit ?? 0) > 0
  }
  private var myVote: String? {
    viewModel.lookup?.userVote ??
      viewModel.myReports.first(where: { $0.phone == number.phone })?.vote
  }
  private var verdictLabel: String {
    switch status {
    case "block": "Indésirable"
    case "warn": "Suspect"
    case "allow": "Plutôt légitime"
    default: "Données insuffisantes"
    }
  }
  private var statusImage: String {
    switch status {
    case "block": "nosign"
    case "warn": "exclamationmark.triangle.fill"
    case "allow": "checkmark.circle.fill"
    default: "questionmark.circle"
    }
  }
  private var verdictExplanation: String {
    let confidence = viewModel.lookup?.confidenceLevel ?? "none"
    switch status {
    case "block":
      confidence == "high"
        ? "Forte convergence vers un appel indésirable."
        : "Évalué comme indésirable, avec encore peu de recul."
    case "warn": "Des avis négatifs existent, mais le verdict reste à confirmer."
    case "allow":
      confidence == "high"
        ? "Les avis convergent vers un numéro légitime."
        : "Tendance plutôt légitime, avec encore peu d’avis."
    default: "Pas assez d’éléments pour évaluer ce numéro."
    }
  }
  private var sourceTitle: String {
    switch source {
    case "mixed": "ARCEP et avis de la communauté"
    case "arcep": "Plage officielle ARCEP"
    case "community": "Avis de la communauté"
    default: "Aucune donnée communautaire"
    }
  }
  private var sourceExplanation: String {
    switch source {
    case "mixed":
      "Ce numéro correspond à une plage ARCEP et possède aussi des avis communautaires. Le verdict tient compte des deux."
    case "arcep":
      "Ce numéro correspond à une plage officielle de démarchage. Cette source est distincte des avis communautaires."
    case "community":
      "Le verdict combine les avis récents, leur volume et la réputation des contributeurs."
    default:
      "L’absence d’avis ne signifie pas que ce numéro est fiable. Restez prudent avant de rappeler."
    }
  }

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
                  label: verdictLabel, color: statusColor,
                  systemImage: statusImage)
                if isArcep || hasCommunityData { sourceBadge }
              }
              Text(verdictExplanation).font(.caption).foregroundStyle(WhoCalledColors.muted)
            }
            Spacer(minLength: 0)
          }
          .frame(maxWidth: .infinity)
        }

        BorderedCard {
          VStack(alignment: .leading, spacing: 8) {
            Text("Contacter ce numéro").font(.subheadline.weight(.semibold))
            HStack(spacing: 8) {
              Button {
                requestContact(scheme: "tel")
              } label: {
                Label("Appeler", systemImage: "phone.fill").frame(maxWidth: .infinity)
              }
              .buttonStyle(.bordered)

              Button {
                requestContact(scheme: "sms")
              } label: {
                Label("SMS", systemImage: "message.fill").frame(maxWidth: .infinity)
              }
              .buttonStyle(.bordered)
            }
          }
        }

        // Source explanation
        BorderedCard {
          VStack(alignment: .leading, spacing: 2) {
            Text(sourceTitle)
              .font(.subheadline.weight(.semibold))
            Text(sourceExplanation)
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

        BorderedCard {
          VStack(alignment: .leading, spacing: 10) {
            Text("Votre avis").font(.subheadline.weight(.semibold))
            Text("Votre vote améliore l’évaluation communautaire. Il ne bloque pas automatiquement le numéro.")
              .font(.footnote).foregroundStyle(WhoCalledColors.muted)
            HStack(spacing: 8) {
              voteButton(label: "Indésirable", image: "nosign", vote: "spam", color: WhoCalledColors.coral) {
                submitVote(isSpam: true)
              }
              voteButton(label: "Légitime", image: "checkmark.circle.fill", vote: "legit", color: WhoCalledColors.emerald) {
                submitVote(isSpam: false)
              }
            }
          }
        }

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
    .alert("Numéro à risque", isPresented: $showRiskWarning) {
      Button("Annuler", role: .cancel) { pendingContactURL = nil }
      Button("Continuer") {
        if let pendingContactURL { openURL(pendingContactURL) }
        pendingContactURL = nil
      }
    } message: {
      Text("Ce numéro est signalé comme indésirable ou suspect. Voulez-vous continuer ?")
    }
    .onAppear {
      viewModel.reportBanner = nil // no stale banner from a previous action
      viewModel.loadLookup(phone: number.phone)
    }
  }

  private func requestContact(scheme: String) {
    guard let url = URL(string: "\(scheme):+\(number.phone)") else { return }
    if status == "block" || status == "warn" {
      pendingContactURL = url
      showRiskWarning = true
    } else {
      openURL(url)
    }
  }

  @ViewBuilder
  private var sourceBadge: some View {
    if source == "mixed" {
      StatusBadge(label: "ARCEP + avis", color: WhoCalledColors.nightBlue, systemImage: "checkmark.seal.fill")
    } else if isArcep {
      StatusBadge(label: "ARCEP", color: WhoCalledColors.nightBlue, systemImage: "checkmark.seal.fill")
    } else if hasCommunityData {
      StatusBadge(label: "Communauté", color: WhoCalledColors.muted, systemImage: "person.3.fill")
    } else {
      StatusBadge(label: "Aucune donnée", color: WhoCalledColors.blue, systemImage: "questionmark.circle")
    }
  }

  private func statsCard(_ stats: LookupResponseDTO) -> some View {
    BorderedCard {
      VStack(alignment: .leading, spacing: 10) {
        Text("Avis de la communauté").font(.subheadline.weight(.semibold))
        HStack {
          stat("Indésirable", "\(stats.reportCountSpam ?? 0)", WhoCalledColors.coral)
          Spacer()
          stat("Légitime", "\(stats.reportCountLegit ?? 0)", WhoCalledColors.emerald)
        }
        Text("Ces nombres sont les avis bruts. Le verdict pondère aussi leur récence et la réputation des contributeurs.")
          .font(.footnote).foregroundStyle(WhoCalledColors.muted)

        Text("Fiabilité de l’évaluation").font(.subheadline.weight(.semibold)).padding(.top, 4)
        Text(confidenceTitle(stats.confidenceLevel ?? "none"))
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(confidenceColor(stats.confidenceLevel ?? "none"))
        Text(confidenceExplanation(stats.confidenceLevel ?? "none", stats.confidence))
          .font(.footnote).foregroundStyle(WhoCalledColors.muted)
        // Community "why did it call?" headline — only when we have a known reason.
        if let top = stats.topReason, top.category != "unknown", top.count > 0 {
          let label = ReportCategory.from(top.category)?.label ?? top.category
          Text("Le plus souvent : \(label) (\(top.share) %)")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(WhoCalledColors.coral)
            .padding(.top, 4)
        }
        if (stats.frequency?.last1y ?? 0) > 0 {
          Text("Activité des avis").font(.subheadline.weight(.semibold)).padding(.top, 6)
          HStack {
            stat("24 h", "\(stats.frequency?.last24h ?? 0)", WhoCalledColors.indigo)
            Spacer()
            stat("7 j", "\(stats.frequency?.last7d ?? 0)", WhoCalledColors.indigo)
            Spacer()
            stat("30 j", "\(stats.frequency?.last30d ?? 0)", WhoCalledColors.indigo)
            Spacer()
            stat("1 an", "\(stats.frequency?.last1y ?? 0)", WhoCalledColors.indigo)
          }
        }
        if let last = parseDate(stats.lastReportedAt) {
          Text("Dernier signalement : \(frenchDate(last))")
            .font(.footnote).foregroundStyle(WhoCalledColors.muted).padding(.top, 4)
        }
      }
    }
  }

  private func submitVote(isSpam: Bool) {
    viewModel.reportPhone = "+\(number.phone)"
    viewModel.reportIsSpam = isSpam
    if isSpam { viewModel.reportCategory = .other }
    viewModel.submitReport()
  }

  private func voteButton(
    label: String,
    image: String,
    vote: String,
    color: Color,
    action: @escaping () -> Void
  ) -> some View {
    let selected = myVote == vote
    return Button(action: action) {
      Label(label, systemImage: image)
        .font(.subheadline.weight(.semibold))
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .foregroundStyle(selected ? Color.white : color)
        .background(
          RoundedRectangle(cornerRadius: 10)
            .fill(selected ? color : color.opacity(0.08))
        )
        .overlay(
          RoundedRectangle(cornerRadius: 10)
            .stroke(color.opacity(0.35), lineWidth: 1)
        )
    }
    .buttonStyle(.plain)
    .disabled(viewModel.reportSubmitting)
  }

  private func confidenceTitle(_ level: String) -> String {
    switch level {
    case "official": "Source officielle"
    case "high": "Élevée"
    case "medium": "Moyenne"
    case "low": "Faible"
    default: "Non évaluée"
    }
  }

  private func confidenceColor(_ level: String) -> Color {
    switch level {
    case "official", "high": WhoCalledColors.emerald
    case "medium": WhoCalledColors.amber
    default: WhoCalledColors.muted
    }
  }

  private func confidenceExplanation(_ level: String, _ confidence: Int?) -> String {
    switch level {
    case "official":
      "Le statut provient d’une plage officielle, pas d’un calcul communautaire."
    case "high":
      "Le volume pondéré d’avis est suffisant pour une évaluation solide (\(confidence ?? 100) %)."
    case "medium":
      "Plusieurs avis existent, mais davantage de recul est utile (\(confidence ?? 0) %)."
    case "low":
      "Trop peu d’avis pondérés pour conclure avec assurance (\(confidence ?? 0) %)."
    default:
      "Aucun avis pondéré exploitable pour le moment."
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
