import SwiftUI

/// Card with a subtle hairline border and no heavy shadow — clean, not flat-gray.
struct BorderedCard<Content: View>: View {
  var accent: Color? = nil
  @ViewBuilder var content: () -> Content

  var body: some View {
    content()
      .padding(16)
      .frame(maxWidth: .infinity, alignment: .leading)
      .background(Color(.systemBackground))
      .overlay(
        RoundedRectangle(cornerRadius: 12)
          .stroke((accent ?? WhoCalledColors.border).opacity(accent == nil ? 1 : 0.35), lineWidth: 1)
      )
      .clipShape(RoundedRectangle(cornerRadius: 12))
  }
}

enum BannerKind {
  case error, success, info

  var tint: Color {
    switch self {
    case .error: return WhoCalledColors.coral
    case .success: return WhoCalledColors.emerald
    case .info: return WhoCalledColors.indigo
    }
  }
  var icon: String {
    switch self {
    case .error: return "exclamationmark.triangle.fill"
    case .success: return "checkmark.circle.fill"
    case .info: return "info.circle.fill"
    }
  }
}

/// Bordered, lightly tinted feedback row with an SF Symbol — replaces bare red text.
struct FeedbackBanner: View {
  let kind: BannerKind
  let message: String

  var body: some View {
    HStack(spacing: 10) {
      Image(systemName: kind.icon).foregroundStyle(kind.tint)
      Text(message).font(.subheadline)
      Spacer(minLength: 0)
    }
    .padding(.horizontal, 12)
    .padding(.vertical, 10)
    .background(kind.tint.opacity(0.06))
    .overlay(
      RoundedRectangle(cornerRadius: 10).stroke(kind.tint.opacity(0.30), lineWidth: 1)
    )
    .clipShape(RoundedRectangle(cornerRadius: 10))
  }
}

/// Status pill (Bloqué / Alerté / Sûr) — tinted + bordered, no shadow.
struct StatusBadge: View {
  let label: String
  let color: Color
  let systemImage: String

  var body: some View {
    HStack(spacing: 4) {
      Image(systemName: systemImage).font(.caption)
      Text(label).font(.caption.weight(.semibold))
    }
    .foregroundStyle(color)
    .padding(.horizontal, 10)
    .padding(.vertical, 5)
    .background(color.opacity(0.08))
    .overlay(Capsule().stroke(color.opacity(0.35), lineWidth: 1))
    .clipShape(Capsule())
  }
}

/// Risk gauge: arc follows the internal score; center shows Faible / Modéré / Élevé.
struct ScoreGauge: View {
  let score: Int
  var status: String = "unknown"
  var diameter: CGFloat = 132

  private var color: Color {
    switch status {
    case "block": return WhoCalledColors.coral
    case "warn": return WhoCalledColors.amber
    case "allow": return WhoCalledColors.emerald
    default: return WhoCalledColors.blue
    }
  }

  private var riskLabel: String {
    switch status {
    case "block": "Élevé"
    case "warn": "Modéré"
    case "allow": "Faible"
    default: "—"
    }
  }

  var body: some View {
    ZStack {
      Circle()
        .trim(from: 0, to: 0.75)
        .stroke(Color.gray.opacity(0.15), style: StrokeStyle(lineWidth: 12, lineCap: .round))
        .rotationEffect(.degrees(135))
      Circle()
        .trim(from: 0, to: 0.75 * CGFloat(min(max(score, 0), 100)) / 100)
        .stroke(color, style: StrokeStyle(lineWidth: 12, lineCap: .round))
        .rotationEffect(.degrees(135))
        .animation(.easeOut(duration: 0.9), value: score)
      VStack(spacing: 1) {
        Text(riskLabel)
          .font(.system(size: diameter * 0.16, weight: .bold))
          .foregroundStyle(color)
          .minimumScaleFactor(0.7)
          .lineLimit(1)
        Text("Risque")
          .font(.system(size: diameter * 0.1, weight: .medium))
          .foregroundStyle(WhoCalledColors.muted)
      }
    }
    .frame(width: diameter, height: diameter)
  }
}

/// Friendly empty-state placeholder. The glyph sits in a large soft tinted disc
/// (with a hairline ring) so an empty list reads as intentional and calm — never
/// broken or blank. An optional `action` slot offers the obvious next step.
struct EmptyState<Action: View>: View {
  let systemImage: String
  let title: String
  var subtitle: String? = nil
  var accent: Color = WhoCalledColors.indigo
  @ViewBuilder var action: () -> Action

  var body: some View {
    VStack(spacing: 10) {
      ZStack {
        Circle()
          .fill(accent.opacity(0.08))
          .frame(width: 88, height: 88)
        Circle()
          .stroke(accent.opacity(0.25), lineWidth: 1)
          .frame(width: 88, height: 88)
        Image(systemName: systemImage)
          .font(.system(size: 36))
          .foregroundStyle(accent.opacity(0.85))
      }
      Text(title).font(.headline).multilineTextAlignment(.center)
      if let subtitle {
        Text(subtitle)
          .font(.subheadline)
          .foregroundStyle(WhoCalledColors.muted)
          .multilineTextAlignment(.center)
      }
      action().padding(.top, 4)
    }
    .frame(maxWidth: .infinity)
    .padding(.horizontal, 24)
    .padding(.vertical, 36)
  }
}

extension EmptyState where Action == EmptyView {
  /// Convenience init for empty states without an action button.
  init(systemImage: String, title: String, subtitle: String? = nil,
       accent: Color = WhoCalledColors.indigo) {
    self.init(systemImage: systemImage, title: title, subtitle: subtitle,
              accent: accent, action: { EmptyView() })
  }
}

/// Brand lockup: shield mark in a blue disc + the "Who Called" wordmark. Used on
/// loading/branded screens so the identity is the name, not just a shield glyph.
struct BrandLogo: View {
  var horizontal = true
  var onDark = false

  private var mark: some View {
    ZStack {
      RoundedRectangle(cornerRadius: horizontal ? 12 : 18)
        .fill(onDark ? Color.white.opacity(0.16) : WhoCalledColors.blue)
        .frame(width: horizontal ? 44 : 64, height: horizontal ? 44 : 64)
      Image(systemName: "checkmark.shield.fill")
        .font(.system(size: horizontal ? 24 : 34))
        .foregroundStyle(.white)
    }
  }

  private var wordmark: some View {
    Text("Who Called")
      .font(.title2.bold())
      .foregroundStyle(onDark ? Color.white : WhoCalledColors.nightBlue)
  }

  var body: some View {
    if horizontal {
      HStack(spacing: 12) { mark; wordmark }
    } else {
      VStack(spacing: 12) { mark; wordmark }
    }
  }
}

/// Full-screen branded loader: the Who Called lockup over a spinner.
struct BrandedLoader: View {
  var body: some View {
    VStack(spacing: 20) {
      Spacer()
      BrandLogo(horizontal: false)
      ProgressView().tint(WhoCalledColors.blue)
      Spacer()
    }
    .frame(maxWidth: .infinity)
    .padding(32)
  }
}

/// Vivid blue → navy gradient header band.
struct GradientHeader: View {
  let title: String
  var subtitle: String? = nil

  var body: some View {
    VStack(alignment: .leading, spacing: 4) {
      Text(title).font(.title2.bold()).foregroundStyle(.white)
      if let subtitle {
        Text(subtitle).font(.subheadline).foregroundStyle(.white.opacity(0.85))
      }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(20)
    .background(
      // Gentle same-family gradient (vivid blue → slightly darker blue),
      // never fading toward navy/grey.
      LinearGradient(
        colors: [WhoCalledColors.blueDark, WhoCalledColors.blue],
        startPoint: .topLeading, endPoint: .bottomTrailing)
    )
    .clipShape(RoundedRectangle(cornerRadius: 18))
  }
}
