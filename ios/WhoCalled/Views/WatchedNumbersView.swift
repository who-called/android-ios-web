import SwiftUI

/// Full list of watched numbers (what we'd block/warn). iOS has no per-call
/// journal — the Call Directory extension isn't woken on each incoming call, so
/// there are no per-event timestamps to group by (Apple limitation). We instead
/// list every watched number, searchable, with long-press to copy — our
/// differentiator vs. apps that hide the list behind a counter.
struct WatchedNumbersView: View {
  @EnvironmentObject private var viewModel: MainViewModel
  @State private var query = ""

  private var filtered: [ScoredNumber] {
    let q = query.trimmingCharacters(in: .whitespaces)
    guard !q.isEmpty else { return viewModel.scoredNumbers }
    let digits = q.filter { $0.isNumber }
    return viewModel.scoredNumbers.filter {
      digits.isEmpty ? true : $0.phone.contains(digits)
    }
  }

  var body: some View {
    ScrollToTopScreen {
      VStack(alignment: .leading, spacing: 12) {
        if viewModel.scoredNumbers.isEmpty {
          EmptyState(
            systemImage: "shield.lefthalf.filled",
            title: "Aucun numéro surveillé",
            subtitle: "Lancez une mise à jour depuis l’accueil pour télécharger la liste.",
            accent: WhoCalledColors.emerald)
        } else {
          Text("\(filtered.count) numéro\(filtered.count > 1 ? "s" : "") · appui long pour copier")
            .font(.caption).foregroundStyle(WhoCalledColors.muted)
          ForEach(filtered, id: \.phone) { number in
            NavigationLink {
              NumberDetailView(number: number)
            } label: {
              row(number)
            }
            .buttonStyle(.plain)
            .contextMenu {
              Button {
                UIPasteboard.general.string = "+\(number.phone)"
              } label: {
                Label("Copier le numéro", systemImage: "doc.on.doc")
              }
            }
          }
        }
      }
      .padding()
    }
    .navigationTitle("Numéros surveillés")
    .navigationBarTitleDisplayMode(.inline)
    .searchable(text: $query, prompt: "Rechercher un numéro")
  }

  private func row(_ number: ScoredNumber) -> some View {
    let blocked = number.status == "block"
    let color = blocked ? WhoCalledColors.coral : WhoCalledColors.amber
    return BorderedCard {
      HStack {
        VStack(alignment: .leading, spacing: 2) {
          Text("+\(number.phone)").fontWeight(.bold)
          if let cat = ReportCategory.from(number.category)?.label {
            Text(cat).font(.caption).foregroundStyle(WhoCalledColors.muted)
          }
        }
        Spacer()
        StatusBadge(
          label: blocked ? "Bloqué" : "Alerté", color: color,
          systemImage: blocked ? "nosign" : "exclamationmark.triangle.fill")
        Image(systemName: "chevron.right").foregroundStyle(WhoCalledColors.muted)
      }
    }
  }
}
