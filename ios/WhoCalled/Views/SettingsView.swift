import SwiftUI
import UserNotifications

struct SettingsView: View {
  @EnvironmentObject private var viewModel: MainViewModel
  @State private var showEraseAlert = false
  @State private var showNotifDebug = false
  @State private var notifAuthLabel = ""

  private var version: String {
    let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1.0"
    let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
    return "v\(v) (\(b))"
  }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          Toggle("M’alerter en cas de doute", isOn: $viewModel.warnEnabled)
        } header: {
          Text("Filtrage")
        } footer: {
          Text("Pour les numéros suspects sans certitude (score moyen), le téléphone sonne normalement mais affiche une étiquette d’avertissement sous le numéro. Vous décidez de répondre ou non. Les numéros au score élevé, eux, sont bloqués automatiquement.")
        }

        Section {
          Slider(value: $viewModel.blockThreshold, in: 50...100, step: 5)
        } header: {
          Text("Sensibilité du blocage : \(Int(viewModel.blockThreshold)) %")
        } footer: {
          Text("À partir de ce score de spam, un numéro est bloqué. En dessous, il est seulement étiqueté (si l’option ci-dessus est activée). Plus le seuil est bas, plus on bloque — au risque de quelques faux positifs.")
        }

        Section {
          Picker(
            "Pays surveillé",
            selection: Binding(
              get: { viewModel.countryDial },
              set: { viewModel.setCountry($0) })
          ) {
            ForEach(Countries.list) { c in
              Text("\(c.flag)  \(c.name)").tag(c.dial)
            }
          }
        } footer: {
          Text("Ne précharger que les numéros de ce pays (cache plus léger). Détecté automatiquement selon votre région, modifiable. « Tous » télécharge le monde entier.")
        }

        Section {
          Toggle("Rappel quotidien pour jouer 🎮", isOn: $viewModel.gameReminderEnabled)
        } header: {
          Text("Jeux")
        } footer: {
          Text("Un rappel bienveillant, une fois par jour (le soir), pour venir relever le défi et grimper au classement. Désactivé par défaut — coupez-le à tout moment.")
        }

        Section("Informations") {
          // Each link is shown only when its URL/value is configured (empty → hidden).
          link("Confidentialité", "lock.fill", AppConstants.Links.privacy)
          link("Mentions légales", "doc.text.fill", AppConstants.Links.policy)
          link("Site officiel", "safari.fill", AppConstants.Links.site)
          link("Source des préfixes ARCEP (arcep.fr)", "building.columns.fill", AppConstants.Links.arcepSource)
          link("Noter l’application", "star.fill", AppConstants.Links.rate)
          link("Code source (open source)", "chevron.left.forwardslash.chevron.right", AppConstants.Links.repo)
          if !AppConstants.Links.contactEmail.isEmpty {
            Button {
              if let url = URL(string: "mailto:\(AppConstants.Links.contactEmail)") {
                UIApplication.shared.open(url)
              }
            } label: {
              Label("Nous contacter", systemImage: "envelope.fill")
            }
          }
        }

        Section {
          Button(role: .destructive) {
            showEraseAlert = true
          } label: {
            Label("Supprimer mes données", systemImage: "trash")
          }
          if let banner = viewModel.privacyBanner {
            Text(banner.message)
              .font(.footnote)
              .foregroundStyle(banner.kind == .error ? WhoCalledColors.coral : WhoCalledColors.emerald)
          }
        } header: {
          Text("Mes données")
        } footer: {
          Text("Efface définitivement tous les signalements envoyés depuis cet appareil. Conforme à votre droit à l’effacement (RGPD).")
        }

        Section {
          VStack(alignment: .leading, spacing: 4) {
            Text("Gratuit et open source").font(.subheadline.weight(.semibold))
            Text("Who Called est 100 % gratuit et son code est ouvert. 100 % anonyme — aucun compte, aucune donnée personnelle. Numéros au format international.")
              .font(.footnote).foregroundStyle(WhoCalledColors.muted)
          }
        }

        // Non-affiliation disclaimer (store policies: apps sharing government
        // info must clearly state they are not official).
        Section {
          VStack(alignment: .leading, spacing: 4) {
            Text("Application indépendante").font(.subheadline.weight(.semibold))
            Text("Who Called n’est pas une application officielle : elle n’est pas affiliée à l’ARCEP ni à aucune entité gouvernementale, et ne représente aucun organisme public. Les préfixes de démarchage utilisés proviennent du plan national de numérotation publié par l’ARCEP (lien « Source des préfixes ARCEP » ci-dessus).")
              .font(.footnote).foregroundStyle(WhoCalledColors.muted)
          }
        }

        // HIDDEN notification debug section — triple-tap the version to reveal.
        // Verifies the whole pipeline: permission → banner → tap routing.
        if showNotifDebug {
          Section("Debug notifications") {
            Text(notifAuthLabel).font(.footnote)
            Button("Tester une notification (5 s)") {
              Task {
                let center = UNUserNotificationCenter.current()
                let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
                if granted { GameReminders.scheduleTest(inSeconds: 5) }
                await refreshNotifAuth()
              }
            }
            Button("Ouvrir les réglages notifications") {
              if let url = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(url)
              }
            }
          } footer: {
            Text("Envoyez le test puis quittez l'app : la bannière doit arriver ~5 s plus tard. Triple-tap sur la version pour masquer ce panneau.")
          }
        }
      }
      .navigationTitle("Réglages")
      .alert("Supprimer mes données ?", isPresented: $showEraseAlert) {
        Button("Annuler", role: .cancel) {}
        Button("Supprimer", role: .destructive) { viewModel.eraseMyData() }
      } message: {
        Text("Tous les signalements envoyés depuis cet appareil seront définitivement supprimés de nos serveurs. Cette action est irréversible.")
      }
      .safeAreaInset(edge: .bottom) {
        // Double-tap the version → hidden API connectivity test.
        // Triple-tap → hidden notification debug section.
        VStack(spacing: 2) {
          Text("Who Called \(version)")
            .font(.footnote).foregroundStyle(WhoCalledColors.muted)
          Text(apiTestLabel)
            .font(.caption2)
            .foregroundStyle(apiTestColor)
        }
        .frame(maxWidth: .infinity)
        .padding(8)
        .contentShape(Rectangle())
        .onTapGesture(count: 3) {
          showNotifDebug.toggle()
          if showNotifDebug { Task { await refreshNotifAuth() } }
        }
        .onTapGesture(count: 2) { viewModel.runApiTest() }
      }
    }
  }

  /// Live notification authorization summary for the hidden debug section.
  private func refreshNotifAuth() async {
    let s = await UNUserNotificationCenter.current().notificationSettings()
    let auth = switch s.authorizationStatus {
    case .authorized: "autorisée ✓"
    case .denied: "REFUSÉE ✗ — rien ne s'affichera"
    case .notDetermined: "pas encore demandée"
    case .provisional: "provisoire"
    case .ephemeral: "éphémère"
    @unknown default: "inconnue"
    }
    notifAuthLabel = "Autorisation : \(auth) · alertes \(s.alertSetting == .enabled ? "✓" : "✗") · son \(s.soundSetting == .enabled ? "✓" : "✗")"
  }

  private var apiTestLabel: String {
    switch viewModel.apiTest {
    case .idle: return "Double-tap pour tester l’API"
    case .testing: return "Test de l’API…"
    case .ok(let ms): return "API OK ✓ (\(ms) ms)"
    case .failed(let reason): return "API injoignable ✗ — \(reason)"
    }
  }

  private var apiTestColor: Color {
    switch viewModel.apiTest {
    case .ok: return WhoCalledColors.emerald
    case .failed: return WhoCalledColors.coral
    default: return WhoCalledColors.muted
    }
  }

  /// Renders a link row only when the URL is non-empty; otherwise nothing.
  @ViewBuilder
  private func link(_ title: String, _ symbol: String, _ urlString: String) -> some View {
    if !urlString.isEmpty {
      Button {
        if let url = URL(string: urlString) { UIApplication.shared.open(url) }
      } label: {
        Label(title, systemImage: symbol)
      }
    }
  }
}
