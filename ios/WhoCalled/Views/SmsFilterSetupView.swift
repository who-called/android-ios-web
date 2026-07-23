import SwiftUI

/// "Filtre SMS" setup sheet. On iOS the SMS shield is a Message Filter extension
/// the user enables in system Settings (we can't toggle it programmatically).
/// We explain the steps and link to Settings, à la Saracroche — but offline
/// (no message content ever leaves the device).
struct SmsFilterSetupView: View {
  @Environment(\.dismiss) private var dismiss

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 20) {
          HStack(spacing: 12) {
            Image(systemName: "message.fill")
              .font(.system(size: 34)).foregroundStyle(WhoCalledColors.emerald)
            VStack(alignment: .leading) {
              Text("Filtre SMS").font(.title2.bold())
              Text("Masquez les SMS indésirables")
                .font(.subheadline).foregroundStyle(WhoCalledColors.muted)
            }
          }

          Text("Who Called peut filtrer les SMS des expéditeurs inconnus indésirables, à partir de la même liste que pour les appels. Le filtrage se fait sur l’appareil : le contenu de vos messages ne quitte jamais votre téléphone.")
            .font(.subheadline)

          VStack(alignment: .leading, spacing: 14) {
            step(1, "Ouvrez Réglages > Apps > Messages")
            step(2, "Touchez « Expéditeurs inconnus et indésirables »")
            step(3, "Activez « Filtrer les expéditeurs inconnus »")
            step(4, "Sélectionnez « Who Called » comme filtre SMS")
          }
          .padding()
          .frame(maxWidth: .infinity, alignment: .leading)
          .background(WhoCalledColors.emerald.opacity(0.10))
          .clipShape(RoundedRectangle(cornerRadius: 12))

          Button {
            if let url = URL(string: UIApplication.openSettingsURLString) {
              UIApplication.shared.open(url)
            }
          } label: {
            Label("Ouvrir les réglages", systemImage: "gear").frame(maxWidth: .infinity)
          }
          .buttonStyle(.borderedProminent)
          .tint(WhoCalledColors.emerald)

          Text("Astuce : les expéditeurs inconnus indésirables apparaissent dans l’onglet « Indésirables » de Messages, sans notification.")
            .font(.footnote).foregroundStyle(WhoCalledColors.muted)
        }
        .padding()
      }
      .navigationTitle("Filtre SMS")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .confirmationAction) {
          Button("OK") { dismiss() }
        }
      }
    }
  }

  private func step(_ n: Int, _ text: String) -> some View {
    HStack(alignment: .top, spacing: 12) {
      Text("\(n)")
        .font(.subheadline.bold()).foregroundStyle(.white)
        .frame(width: 26, height: 26)
        .background(WhoCalledColors.emerald).clipShape(Circle())
      Text(text).font(.subheadline)
      Spacer(minLength: 0)
    }
  }
}
