import SwiftUI

struct ReportView: View {
  @EnvironmentObject private var viewModel: MainViewModel

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 16) {
          GradientHeader(
            title: "Signaler un numéro",
            subtitle: "Anonyme — aucun compte, aucune donnée personnelle.")

          BorderedCard {
            VStack(alignment: .leading, spacing: 12) {
              TextField("+33 6 12 34 56 78", text: $viewModel.reportPhone)
                .keyboardType(.phonePad)
                .textContentType(.telephoneNumber)
                .textFieldStyle(.roundedBorder)

              Picker("Type", selection: $viewModel.reportIsSpam) {
                Text("Indésirable").tag(true)
                Text("Légitime").tag(false)
              }
              .pickerStyle(.segmented)

              if viewModel.reportIsSpam {
                Text("Type d’appel").font(.subheadline.weight(.semibold))
                Picker("Catégorie", selection: $viewModel.reportCategory) {
                  ForEach(ReportCategory.allCases) { c in
                    Text(c.label).tag(c)
                  }
                }
                .pickerStyle(.menu)
                .tint(WhoCalledColors.indigo)
              }

              Button {
                viewModel.submitReport()
              } label: {
                if viewModel.reportSubmitting {
                  ProgressView().frame(maxWidth: .infinity)
                } else {
                  Text("Envoyer le signalement").frame(maxWidth: .infinity)
                }
              }
              .buttonStyle(.borderedProminent)
              .tint(WhoCalledColors.indigo)
              .disabled(viewModel.reportPhone.isEmpty || viewModel.reportSubmitting)
            }
          }

          if let banner = viewModel.reportBanner {
            FeedbackBanner(kind: banner.kind, message: banner.message)
          }
        }
        .padding()
      }
      .navigationTitle("Signaler")
      .navigationBarTitleDisplayMode(.inline)
    }
  }
}
