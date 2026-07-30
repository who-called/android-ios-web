import SwiftUI

struct MyReportsView: View {
  @EnvironmentObject private var viewModel: MainViewModel
  @State private var pendingDelete: String?

  var body: some View {
    NavigationStack {
      ScrollToTopScreen {
        GradientHeader(
          title: "Mes signalements",
          subtitle: "Changez d’avis ou supprimez un signalement fait par erreur.")
          .padding([.horizontal, .top])
      } content: {
        VStack(alignment: .leading, spacing: 12) {
          if viewModel.myReports.isEmpty {
            EmptyState(
              systemImage: "flag.slash",
              title: "Aucun signalement",
              subtitle: "Vos signalements apparaîtront ici. Signalez un numéro indésirable pour protéger la communauté.")
          } else {
            ForEach(viewModel.myReports) { report in
              BorderedCard {
                HStack {
                  NavigationLink {
                    NumberDetailView(
                      number: ScoredNumber(
                        phone: report.phone,
                        status: "unknown",
                        spamScore: 0,
                        category: report.category ?? "unknown",
                        source: "none"))
                  } label: {
                    HStack {
                      VStack(alignment: .leading, spacing: 2) {
                        Text("+\(report.phone)").fontWeight(.bold)
                        Text(subtitle(report))
                          .font(.caption)
                          .foregroundStyle(report.syncState == "failed" ? WhoCalledColors.coral : WhoCalledColors.muted)
                      }
                      Spacer()
                      Image(systemName: "chevron.right").foregroundStyle(WhoCalledColors.muted)
                    }
                  }
                  .buttonStyle(.plain)
                  Button {
                    viewModel.flipReport(report)
                  } label: {
                    Image(systemName: "arrow.left.arrow.right")
                  }
                  .buttonStyle(.plain)
                  .padding(.trailing, 12)
                  Button {
                    pendingDelete = report.phone // confirm first, not a direct delete
                  } label: {
                    Image(systemName: "trash").foregroundStyle(WhoCalledColors.coral)
                  }
                  .buttonStyle(.plain)
                }
              }
            }
          }
        }
        .padding()
      }
      .navigationTitle("Mes signalements")
      .navigationBarTitleDisplayMode(.inline)
      .onAppear { viewModel.refresh() }
      .confirmationDialog(
        "Supprimer ce signalement ?",
        isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
        titleVisibility: .visible
      ) {
        Button("Supprimer", role: .destructive) {
          if let phone = pendingDelete { viewModel.deleteReport(phone: phone) }
          pendingDelete = nil
        }
        Button("Annuler", role: .cancel) { pendingDelete = nil }
      } message: {
        Text("Le signalement sera retiré (localement et de nos serveurs). Action irréversible.")
      }
    }
  }

  private func subtitle(_ r: MyReport) -> String {
    let vote = r.vote == "spam" ? "Indésirable" : "Légitime"
    let sync: String
    switch r.syncState {
    case "synced": sync = "envoyé"
    case "failed": sync = "non envoyé"
    default: sync = "en attente"
    }
    return "\(vote) · \(sync) · \(RelativeTime.format(r.updatedAt))"
  }
}
