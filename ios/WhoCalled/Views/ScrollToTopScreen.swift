import SwiftUI

/// A screen with an optional pinned `header` shown as-is at the top (no shrinking
/// or transformation) while only the content below scrolls, plus a floating
/// "remonter" button that appears once the user scrolls down — consistent with
/// the Android screens.
struct ScrollToTopScreen<Header: View, Content: View>: View {
  @ViewBuilder var header: () -> Header
  @ViewBuilder var content: () -> Content

  @State private var scrolledDown = false
  private let topID = "scroll_top_anchor"

  var body: some View {
    VStack(spacing: 0) {
      // Fixed header — outside the scroll, always shown as-is.
      header()
      ScrollViewReader { proxy in
        ScrollView {
          // Invisible anchor + offset reader at the very top of the content.
          Color.clear
            .frame(height: 0)
            .id(topID)
            .background(
              GeometryReader { geo in
                Color.clear.preference(
                  key: ScrollOffsetKey.self,
                  value: geo.frame(in: .named("scrollToTop")).minY)
              }
            )
          content()
        }
        .coordinateSpace(name: "scrollToTop")
        .onPreferenceChange(ScrollOffsetKey.self) { minY in
          // minY goes negative as we scroll down; show the button past a threshold.
          let down = minY < -240
          if down != scrolledDown {
            withAnimation(.easeInOut(duration: 0.2)) { scrolledDown = down }
          }
        }
        .overlay(alignment: .bottomTrailing) {
          if scrolledDown {
            Button {
              withAnimation { proxy.scrollTo(topID, anchor: .top) }
            } label: {
              Image(systemName: "chevron.up")
                .font(.headline)
                .foregroundStyle(.white)
                .padding(14)
                .background(WhoCalledColors.indigo)
                .clipShape(Circle())
                .shadow(radius: 4, y: 2)
            }
            .padding(20)
            .accessibilityLabel("Remonter en haut")
            .transition(.scale.combined(with: .opacity))
          }
        }
      }
    }
  }
}

extension ScrollToTopScreen where Header == EmptyView {
  /// Convenience init for screens without a pinned header.
  init(@ViewBuilder content: @escaping () -> Content) {
    self.init(header: { EmptyView() }, content: content)
  }
}

private struct ScrollOffsetKey: PreferenceKey {
  static var defaultValue: CGFloat = 0
  static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
    value = nextValue()
  }
}
