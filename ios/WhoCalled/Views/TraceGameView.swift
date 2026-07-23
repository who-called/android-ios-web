import AVFoundation
import SwiftUI
import UIKit

/// The "caught fraudster" mark — the grid's villain emoji (the one shown above
/// the board), so the mark matches the character the player is hunting.
private struct VillainMark: View {
  var emoji: String
  var size: CGFloat
  var opacity: Double = 1
  var body: some View {
    Text(emoji).font(.system(size: size)).opacity(opacity)
  }
}

/// Soft synthesized chimes (pure sine, gentle attack + exponential decay) —
/// much smoother than the harsh system beeps, and still asset-free. The audio
/// session is `.ambient` so the silent switch is respected and music keeps playing.
private final class TraceSound {
  static let shared = TraceSound()
  private let engine = AVAudioEngine()
  private let player = AVAudioPlayerNode()
  private let goodBuffer: AVAudioPCMBuffer?
  private let badBuffer: AVAudioPCMBuffer?

  private let loseBuffer: AVAudioPCMBuffer?
  private let winBuffer: AVAudioPCMBuffer?

  enum Kind { case good, bad, lose, win }

  private init() {
    let format = AVAudioFormat(standardFormatWithSampleRate: 44_100, channels: 1)!
    engine.attach(player)
    engine.connect(player, to: engine.mainMixerNode, format: format)
    // Good: soft ascending E5 → B5 chime · bad: low descending muffled "womp" ·
    // lose: gentle three-note descent (sympathetic, not dramatic) ·
    // win: sparkling ascending C5 → E5 → G5 arpeggio.
    goodBuffer = TraceSound.chime([(659.25, 0), (987.77, 0.06)], duration: 0.22, format: format)
    badBuffer = TraceSound.chime([(220, 0), (185, 0.09)], duration: 0.3, format: format)
    loseBuffer = TraceSound.chime([(392, 0), (311.1, 0.18), (261.6, 0.36)], duration: 0.8, format: format)
    winBuffer = TraceSound.chime([(523.25, 0), (659.25, 0.14), (783.99, 0.28)], duration: 0.75, format: format)
    try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
  }

  func play(_ kind: Kind) {
    let buf: AVAudioPCMBuffer?
    switch kind {
    case .good: buf = goodBuffer
    case .bad: buf = badBuffer
    case .lose: buf = loseBuffer
    case .win: buf = winBuffer
    }
    guard let buf else { return }
    if !engine.isRunning { try? engine.start() }
    guard engine.isRunning else { return }
    player.stop()
    player.scheduleBuffer(buf, at: nil, options: .interrupts)
    player.play()
  }

  private static func chime(_ notes: [(freq: Double, offset: Double)], duration: Double, format: AVAudioFormat) -> AVAudioPCMBuffer? {
    let sr = format.sampleRate
    let frames = AVAudioFrameCount(sr * duration)
    guard let buf = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames),
          let data = buf.floatChannelData?[0] else { return nil }
    buf.frameLength = frames
    for i in 0..<Int(frames) { data[i] = 0 }
    for note in notes {
      let start = Int(sr * note.offset)
      let len = Int(frames) - start
      guard len > 0 else { continue }
      for i in 0..<len {
        let t = Double(i) / sr
        // Smooth ~12ms cosine attack, then an exponential fade to silence.
        let attack = t < 0.012 ? 0.5 * (1 - cos(.pi * t / 0.012)) : 1
        let decay = exp(-5 * Double(i) / Double(len))
        data[start + i] += Float(sin(2 * .pi * note.freq * t) * attack * decay * 0.42)
      }
    }
    return buf
  }
}

/// Sound + haptic feedback for good/bad placements and the end of a lost run.
private enum TraceFX {
  static func good() {
    TraceSound.shared.play(.good)
    UINotificationFeedbackGenerator().notificationOccurred(.success)
  }
  static func bad() {
    TraceSound.shared.play(.bad)
    UINotificationFeedbackGenerator().notificationOccurred(.error)
  }
  static func lose() {
    TraceSound.shared.play(.lose)
    UINotificationFeedbackGenerator().notificationOccurred(.warning)
  }
  static func win() {
    TraceSound.shared.play(.win)
    UINotificationFeedbackGenerator().notificationOccurred(.success)
  }
}

/// Transient "+N pts" / "−1 vie" feedback bubble shown ~1s above the grid.
private struct TraceToast: Equatable {
  let id: Int
  let text: String
  let good: Bool
}

/// The animated bubble itself: springs in with a bounce, floats upward, and the
/// container fades it out — much livelier than a static capsule.
private struct ToastBubbleView: View {
  let text: String
  let good: Bool
  @State private var shown = false
  @State private var rise = false

  var body: some View {
    HStack(spacing: 6) {
      Text(good ? "✓" : "✗").fontWeight(.heavy)
      Text(text).font(.subheadline.bold())
    }
    .foregroundStyle(.white)
    .padding(.horizontal, 14).padding(.vertical, 8)
    .background(Capsule().fill(good ? TraceEngine.ok : Color(red: 0.886, green: 0.235, blue: 0.235)))
    .shadow(color: .black.opacity(0.35), radius: 8, y: 3)
    .scaleEffect(shown ? 1 : 0.2)
    .rotationEffect(.degrees(shown ? 0 : (good ? -8 : 8)))
    .offset(y: rise ? -18 : 8)
    .opacity(shown ? 1 : 0)
    .onAppear {
      withAnimation(.spring(response: 0.32, dampingFraction: 0.5)) { shown = true }
      withAnimation(.easeOut(duration: 1.05)) { rise = true }
    }
  }
}

/// An emoji that pops in with a playful spring (used in the end-of-run modal).
private struct BouncyEmoji: View {
  let emoji: String
  var size: CGFloat = 44
  @State private var shown = false
  var body: some View {
    Text(emoji).font(.system(size: size))
      .scaleEffect(shown ? 1 : 0.2)
      .rotationEffect(.degrees(shown ? 0 : -18))
      .onAppear { withAnimation(.spring(response: 0.45, dampingFraction: 0.5).delay(0.08)) { shown = true } }
  }
}

/// A crisp "safe" cross.
private struct SafeCross: View {
  var size: CGFloat
  var color: Color = .white.opacity(0.9)
  var body: some View {
    Image(systemName: "xmark").font(.system(size: size, weight: .bold)).foregroundStyle(color)
  }
}

/// TRACE — daily deduction puzzle. Fits on one screen (no scroll): a compact
/// status line, a thin sprint bar, a tappable rule strip, the grid sized to the
/// free space, and a hint button. First launch shows an interactive tutorial
/// (replayable via ?); placements give ±points feedback with sound + haptics.
/// Mirrors the Android screen.
struct TraceGameView: View {
  @Environment(\.dismiss) private var dismiss
  let ranked: Bool

  @State private var engine = TraceEngine()
  @State private var version = 0
  @State private var loading = true
  @State private var loadError = false
  @State private var practiceSize = 6
  @State private var didSubmit = false
  @State private var rank: GameScoreResponseDTO?
  @State private var showLeaderboard = false

  @State private var flashCell = -1
  @State private var shakeCell = -1
  @State private var shakeToken = 0
  @State private var villainScale: CGFloat = 1
  @State private var villainAlpha: Double = 1
  @State private var heartScale: CGFloat = 1
  @State private var brokenHeart = -1  // index showing 💔 for ~1s after a lost life

  // Interactive tutorial (auto on very first launch, replayable via the ? button),
  // rules modal (tap the rule strip), and the ±points feedback toast.
  @State private var showTutorial = false
  @State private var showRules = false
  @State private var showQuitConfirm = false
  @State private var toast: TraceToast?
  @State private var toastSeq = 0

  private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

  private static let bg = LinearGradient(
    colors: [Color(red: 0.059, green: 0.145, blue: 0.251), Color(red: 0.086, green: 0.196, blue: 0.302), Color(red: 0.106, green: 0.227, blue: 0.361)],
    startPoint: .top, endPoint: .bottom)

  var body: some View {
    let _ = version
    ZStack {
      TraceGameView.bg.ignoresSafeArea()

      VStack(spacing: 6) {
        topBar
        if loading && engine.gridCount == 0 {
          Spacer(); ProgressView().tint(TraceEngine.cy); Text("Préparation du défi…").foregroundStyle(TraceEngine.ink.opacity(0.7)).padding(.top, 10); Spacer()
        } else if loadError && engine.gridCount == 0 {
          Spacer(); errorBlock; Spacer()
        } else if engine.gridCount > 0 {
          statusRow
          sprintBar
          // Rule strip stays on screen during play; tapping it reopens the rules.
          Button { showRules = true } label: { ruleStrip }.buttonStyle(.plain)
          GeometryReader { geo in
            let board = min(geo.size.width, geo.size.height)
            gridBoard(board: board)
              .frame(width: geo.size.width, height: geo.size.height, alignment: .center)
              .overlay(alignment: .top) { toastView }
          }
          hintRow
        }
      }
      .padding(.horizontal, 12).padding(.vertical, 4)

      // One overlay at a time — tutorial first (very first launch), then rules
      // via the strip / intro before the run, never stacked on each other.
      if showTutorial {
        TraceTutorialOverlay(villain: engine.villain.emoji) {
          SharedStore.traceTutorialSeen = true
          showTutorial = false
        }
      } else if showRules {
        rulesOverlay
      } else {
        if showIntro { introOverlay }
        if engine.started && engine.gridWon && !engine.runOver { celebrationOverlay }
        if engine.runOver { gameOverOverlay }
      }
    }
    .task {
      if !SharedStore.traceTutorialSeen { showTutorial = true }
      await load()
    }
    .onReceive(timer) { _ in
      // The clock pauses while the tutorial or the rules are open.
      if engine.active && !showTutorial && !showRules { engine.tick(); version += 1 }
    }
    .sheet(isPresented: $showLeaderboard) { LeaderboardView(game: "trace") }
    .confirmationDialog("Quitter la partie ?", isPresented: $showQuitConfirm, titleVisibility: .visible) {
      Button("Quitter", role: .destructive) { dismiss() }
      Button("Annuler", role: .cancel) {}
    } message: { Text("Tu perdras ta progression du défi en cours.") }
  }

  private var showIntro: Bool { engine.gridCount > 0 && !engine.started && !engine.runOver }

  private var toastView: some View {
    ZStack {
      if let t = toast {
        ToastBubbleView(text: t.text, good: t.good)
          .id(t.id)  // new toast = fresh bounce/float animation
          .transition(.opacity)
          .padding(.top, 4)
      }
    }
    .animation(.easeOut(duration: 0.25), value: toast)
  }

  /// Show the ±points bubble for ~1s (replaced immediately by a newer one).
  private func showToast(_ text: String, good: Bool) {
    toastSeq += 1
    let t = TraceToast(id: toastSeq, text: text, good: good)
    toast = t
    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { if toast == t { toast = nil } }
  }

  // MARK: - top bar / status
  private var topBar: some View {
    HStack(spacing: 8) {
      Button { if engine.started && !engine.runOver { showQuitConfirm = true } else { dismiss() } } label: { Image(systemName: "chevron.left").foregroundStyle(TraceEngine.ink) }
      Text("TRACE").font(.title3.bold()).foregroundStyle(.white)
      Text(ranked ? "Défi du jour" : "Entraînement")
        .font(.caption2.weight(.semibold)).foregroundStyle(TraceEngine.cy)
        .padding(.horizontal, 8).padding(.vertical, 3).background(Capsule().fill(TraceEngine.cy.opacity(0.16)))
      Spacer()
      // Replay the interactive tutorial at any time.
      Button { showTutorial = true } label: {
        Image(systemName: "questionmark.circle").foregroundStyle(TraceEngine.ink.opacity(0.85))
      }
      if ranked { Button { showLeaderboard = true } label: { Text("🏆") } }
    }
  }

  private var statusRow: some View {
    HStack(spacing: 5) {
      Text(engine.villain.emoji).font(.body).scaleEffect(villainScale).opacity(villainAlpha)
      Text(engine.villain.name).font(.caption.bold()).foregroundStyle(.white).opacity(villainAlpha)
      Spacer()
      HStack(spacing: 1) {
        ForEach(0..<TraceEngine.maxLives, id: \.self) { i in
          // The just-lost heart pops as 💔 for a beat, then settles as 🖤.
          Text(i < engine.lives ? "❤️" : (i == brokenHeart ? "💔" : "🖤"))
            .font(.system(size: 14))
            .opacity(i < engine.lives ? 1 : (i == brokenHeart ? 1 : 0.35))
            .scaleEffect(i == engine.lives ? heartScale : 1)
        }
      }
      .onChange(of: engine.lives) { _ in
        let idx = engine.lives
        brokenHeart = idx
        heartScale = 2.1
        withAnimation(.spring(response: 0.4, dampingFraction: 0.35)) { heartScale = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { if brokenHeart == idx { brokenHeart = -1 } }
      }
      Text("\(engine.displayScore())").font(.title3.bold()).foregroundStyle(TraceEngine.cy).padding(.leading, 10)
      Text(String(format: "%02d:%02d", engine.elapsed / 60, engine.elapsed % 60))
        .font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.7)).padding(.leading, 10)
    }
    .onChange(of: engine.villainIdx) { _ in
      villainScale = 0.3; villainAlpha = 0
      withAnimation(.spring(response: 0.4, dampingFraction: 0.5)) { villainScale = 1; villainAlpha = 1 }
    }
  }

  private var sprintBar: some View {
    HStack(spacing: 5) {
      ForEach(0..<engine.gridCount, id: \.self) { i in
        RoundedRectangle(cornerRadius: 3)
          .fill(i < engine.solvedCount ? TraceEngine.ok : (i == engine.gridIndex && !engine.runOver ? TraceEngine.cy : Color.white.opacity(0.12)))
          .frame(height: 5).frame(maxWidth: .infinity)
      }
      Text("\(min(engine.solvedCount + (engine.runOver ? 0 : 1), engine.gridCount))/\(engine.gridCount)")
        .font(.caption2.weight(.semibold)).foregroundStyle(TraceEngine.ink.opacity(0.7))
    }
  }

  // MARK: - grid
  private func gridBoard(board: CGFloat) -> some View {
    let nn = engine.n
    let gap: CGFloat = 3
    let cell = (board - 16 - gap * CGFloat(nn - 1)) / CGFloat(nn)
    let errors = engine.errorCells()
    return VStack(spacing: gap) {
      ForEach(0..<nn, id: \.self) { r in
        HStack(spacing: gap) {
          ForEach(0..<nn, id: \.self) { c in cellView(r, c, cell, errors) }
        }
      }
    }
    .padding(8)
    .frame(width: board, height: board)
    .background(RoundedRectangle(cornerRadius: 16).fill(Color(red: 0.039, green: 0.110, blue: 0.188)))
    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.08), lineWidth: 1))
    // Drag across a row/column to lay several ✗ in one gesture (empty cells only,
    // so a swipe can never place a fraudster or cost a life). Taps still cycle.
    .simultaneousGesture(
      DragGesture(minimumDistance: 12)
        .onChanged { g in
          guard engine.active else { return }
          let pitch = cell + gap
          let c = Int((g.location.x - 8) / pitch)
          let r = Int((g.location.y - 8) / pitch)
          if r >= 0, r < nn, c >= 0, c < nn, engine.cells[r][c] == 0 {
            engine.paintSafe(r, c)
            version += 1
          }
        }
    )
  }

  private func cellView(_ r: Int, _ c: Int, _ size: CGFloat, _ errors: Set<Int>) -> some View {
    let key = engine.key(r, c)
    let v = engine.cells[r][c]
    let isErr = errors.contains(key)
    let isRevealed = engine.revealed.contains(key)
    let showSol = engine.revealSolution && engine.solution[r] == c
    let base = TraceEngine.regionColor(engine.region[r][c], engine.regionCount)
    let ringColor: Color = isErr ? Color(red: 0.886, green: 0.235, blue: 0.235)
      : showSol ? TraceEngine.ok
      : isRevealed ? Color(red: 0.184, green: 0.498, blue: 0.847)
      : Color.white.opacity(0.35)
    let ringWidth: CGFloat = (isErr || showSol || isRevealed) ? 3 : 1
    return ZStack {
      RoundedRectangle(cornerRadius: 7).fill(base.opacity(0.96))
      if v == 2 {
        VillainMark(emoji: engine.villain.emoji, size: size * 0.58)
      } else if v == 1 {
        SafeCross(size: size * 0.42)
      } else if showSol {
        VillainMark(emoji: engine.villain.emoji, size: size * 0.58, opacity: 0.4)
      }
    }
    .frame(width: size, height: size)
    .overlay(RoundedRectangle(cornerRadius: 7).stroke(ringColor, lineWidth: ringWidth))
    .scaleEffect(flashCell == key ? 1.15 : 1.0)
    .modifier(Shake(animatableData: shakeCell == key ? CGFloat(shakeToken) : 0))
    .animation(.spring(response: 0.25, dampingFraction: 0.45), value: flashCell)
    .animation(.linear(duration: 0.4), value: shakeToken)
    .contentShape(Rectangle())
    .onTapGesture { if engine.active { onCell(r, c) } }
  }

  private var hintRow: some View {
    HStack(spacing: 12) {
      Button { onHint() } label: {
        HStack(spacing: 6) {
          Text("💡 Indice").fontWeight(.bold)
          Text("\(engine.hints)").font(.caption.bold())
            .frame(width: 20, height: 20).background(Circle().fill(Color(red: 0.227, green: 0.180, blue: 0.020)))
            .foregroundStyle(Color(red: 0.957, green: 0.769, blue: 0.188))
        }
        .padding(.horizontal, 16).padding(.vertical, 8)
      }
      .buttonStyle(.borderedProminent).tint(Color(red: 0.957, green: 0.769, blue: 0.188))
      .foregroundStyle(Color(red: 0.227, green: 0.180, blue: 0.020))
      .disabled(!(engine.hints > 0 && engine.active))
      Text(engine.hints > 0 ? "Place un fraudeur · −\(TraceEngine.hintCost) pts" : "Plus d'indices")
        .font(.caption2).foregroundStyle(TraceEngine.ink.opacity(0.6))
      Spacer()
    }
  }

  // MARK: - rules (intro tutorial only) — drawn mini-grids
  private var ruleStrip: some View {
    let pink = Color(red: 1.0, green: 0.420, blue: 0.541)   // vivid
    let teal = Color(red: 0.122, green: 0.820, blue: 0.753) // vivid
    let amber = Color(red: 1.0, green: 0.690, blue: 0.125)  // vivid
    let neu = Color(red: 0.161, green: 0.282, blue: 0.388)
    let neutral: [[Color]] = Array(repeating: Array(repeating: neu, count: 3), count: 3)
    return HStack(spacing: 8) {
      ruleCard(tints: [[pink, pink, teal], [amber, pink, teal], [amber, amber, teal]], marks: [[2, 0, 0], [0, 0, 2], [0, 2, 0]], label: "1 par couleur")
      ruleCard(tints: neutral, marks: [[1, 2, 1], [0, 1, 0], [0, 1, 0]], label: "1 / ligne & colonne")
      ruleCard(tints: neutral, marks: [[1, 1, 1], [1, 2, 1], [1, 1, 1]], label: "Jamais côte à côte")
    }
    .padding(.horizontal, 14)
  }

  private func ruleCard(tints: [[Color]], marks: [[Int]], label: String) -> some View {
    VStack(spacing: 5) {
      VStack(spacing: 2) {
        ForEach(0..<3, id: \.self) { r in
          HStack(spacing: 2) {
            ForEach(0..<3, id: \.self) { c in
              ZStack {
                RoundedRectangle(cornerRadius: 2).fill(tints[r][c]).frame(width: 15, height: 15)
                // Slightly larger cell so the emoji glyph isn't cropped and stays centred.
                if marks[r][c] == 1 { SafeCross(size: 9) } else if marks[r][c] == 2 { Text("😈").font(.system(size: 10)) }
              }
            }
          }
        }
      }
      Text(label).font(.system(size: 9, weight: .semibold)).foregroundStyle(TraceEngine.ink.opacity(0.85)).multilineTextAlignment(.center)
    }
    .frame(maxWidth: .infinity).padding(.vertical, 8).padding(.horizontal, 6)
    .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.08)))
  }

  private var errorBlock: some View {
    VStack(spacing: 12) {
      Text("Impossible de charger le défi").foregroundStyle(.white).fontWeight(.bold)
      Text("Vérifie ta connexion et réessaie.").foregroundStyle(TraceEngine.ink.opacity(0.7)).multilineTextAlignment(.center)
      Button { Task { await load() } } label: { Text("Réessayer").fontWeight(.bold).padding(.horizontal, 18).padding(.vertical, 10) }
        .buttonStyle(.borderedProminent).tint(TraceEngine.cy)
    }.padding(.horizontal, 24)
  }

  // MARK: - overlays
  private var introOverlay: some View {
    overlayCard(onBackdropTap: { dismiss() }) {
      Text("TRACE").font(.largeTitle.bold()).foregroundStyle(.white)
      Text(ranked ? "Défi du jour · 5 grilles · 3 vies" : "Entraînement libre · non classé")
        .font(.subheadline).foregroundStyle(TraceEngine.ink.opacity(0.8))
      Text("LES 3 RÈGLES").font(.system(size: 11, weight: .bold)).foregroundStyle(TraceEngine.cy).padding(.top, 8)
      ruleStrip
      HStack(spacing: 6) {
        Text("Touche :").font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.8))
        SafeCross(size: 12, color: Color(red: 0.75, green: 0.83, blue: 0.93))
        Text("sûre ·").font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.8))
        Text(engine.villain.emoji).font(.system(size: 14))
        Text("fraudeur").font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.8))
      }.padding(.top, 8)
      Button { showTutorial = true } label: {
        Label("Revoir le tuto interactif", systemImage: "questionmark.circle")
          .font(.caption.weight(.semibold))
      }.buttonStyle(.plain).foregroundStyle(TraceEngine.cy).padding(.top, 6)
      if !ranked {
        Text("Taille : \(practiceSize)×\(practiceSize)").fontWeight(.semibold).foregroundStyle(.white).padding(.top, 8)
        Slider(value: Binding(get: { Double(practiceSize) }, set: { practiceSize = Int($0.rounded()) }), in: 5...9, step: 1)
          .padding(.horizontal, 40).onChange(of: practiceSize) { _ in Task { await load() } }
      }
      Button { engine.start(); didSubmit = false; rank = nil; version += 1 } label: {
        Text("Commencer ▸").fontWeight(.bold).padding(.horizontal, 22).padding(.vertical, 12)
      }.buttonStyle(.borderedProminent).tint(TraceEngine.cy).padding(.top, 8)
      if ranked {
        Button { showLeaderboard = true } label: { Text("🏆 Classement").padding(.horizontal, 16).padding(.vertical, 10) }
          .buttonStyle(.bordered).tint(TraceEngine.ink)
      }
    }
  }

  /// Rules recap opened by tapping the rule strip above the grid (game paused).
  private var rulesOverlay: some View {
    overlayCard(onBackdropTap: { showRules = false }) {
      Text("LES 3 RÈGLES").font(.headline.bold()).foregroundStyle(TraceEngine.cy)
      ruleStrip
      HStack(spacing: 6) {
        Text("Touche :").font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.8))
        SafeCross(size: 12, color: Color(red: 0.75, green: 0.83, blue: 0.93))
        Text("sûre ·").font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.8))
        Text(engine.villain.emoji).font(.system(size: 14))
        Text("fraudeur").font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.8))
      }.padding(.top, 6)
      Text("1 touche = ✗ sûre · 2 touches = fraudeur · 3 touches = effacer\nGlisse le doigt sur la grille pour poser des ✗ en série")
        .font(.caption2).foregroundStyle(TraceEngine.ink.opacity(0.65)).multilineTextAlignment(.center)
      Button { showRules = false } label: {
        Text("Reprendre ▸").fontWeight(.bold).padding(.horizontal, 22).padding(.vertical, 10)
      }.buttonStyle(.borderedProminent).tint(TraceEngine.cy).padding(.top, 8)
    }
  }

  private var celebrationOverlay: some View {
    overlay(dim: 0.55) {
      Text("✓").font(.system(size: 54, weight: .bold)).foregroundStyle(TraceEngine.ok)
      Text("Grille \(engine.gridIndex + 1) résolue !").font(.title3.bold()).foregroundStyle(.white)
      Text("Score : \(engine.displayScore())").fontWeight(.semibold).foregroundStyle(TraceEngine.cy)
      if engine.gridIndex + 1 < engine.gridCount {
        Text("Grille suivante…").font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.7))
      }
    }
  }

  private var gameOverOverlay: some View {
    overlayCard(onBackdropTap: { dismiss() }) {
      if engine.runWon {
        BouncyEmoji(emoji: "🏆", size: 48)
        Text("Sprint terminé !").font(.title.bold()).foregroundStyle(.white)
        Text("Les \(engine.gridCount) grilles neutralisées 🎉").foregroundStyle(TraceEngine.ink.opacity(0.85)).multilineTextAlignment(.center)
      } else {
        // Sympathetic fail — encouraging, not dramatic.
        BouncyEmoji(emoji: "😅", size: 48)
        Text("Plus de vies…").font(.title2.bold()).foregroundStyle(.white)
        Text("Pas grave — \(engine.solvedCount)/\(engine.gridCount) grilles résolues !").foregroundStyle(TraceEngine.ink.opacity(0.8))
      }
      Text("\(engine.finalScore())").font(.system(size: 42, weight: .heavy)).foregroundStyle(TraceEngine.cy)
      Text("points").font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.65))
      if ranked, let r = rank {
        Text("Classement du jour · top \(r.topPercent)%").fontWeight(.bold).foregroundStyle(.white)
        Text("\(r.players) joueur\(r.players > 1 ? "s" : "") · meilleur \(r.bestScore)").font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.7))
      }
      HStack(spacing: 10) {
        if ranked {
          ShareLink(item: "who-called TRACE · \(engine.finalScore()) pts · \(engine.solvedCount)/\(engine.gridCount) grilles 🛡️\nTraque les spammeurs toi aussi 👉 \(AppConstants.Links.site)") {
            Text("Partager").padding(.horizontal, 16).padding(.vertical, 10)
          }.buttonStyle(.bordered).tint(TraceEngine.ink)
        }
        Button {
          if ranked { engine.start(); didSubmit = false; rank = nil; version += 1 }
          else { Task { await reloadTraining(autoStart: true) } }
        } label: {
          Text(ranked ? "Rejouer" : "Nouvelle grille").fontWeight(.bold).padding(.horizontal, 16).padding(.vertical, 10)
        }.buttonStyle(.borderedProminent).tint(TraceEngine.cy)
      }
      HStack(spacing: 10) {
        if ranked { Button { showLeaderboard = true } label: { Text("🏆 Classement") }.buttonStyle(.bordered).tint(TraceEngine.ink) }
        Button { dismiss() } label: { Text("Retour").foregroundStyle(TraceEngine.ink.opacity(0.8)) }
      }
    }
  }

  private func overlay<C: View>(dim: Double, @ViewBuilder _ content: () -> C) -> some View {
    ZStack {
      Color(red: 0.024, green: 0.137, blue: 0.247).opacity(dim).ignoresSafeArea()
      VStack(spacing: 10) { content() }.padding(24)
    }
  }

  /// A distinct floating modal: soft dim behind, a bordered card with a real
  /// drop shadow that springs in — reads as a proper modal, not a flat overlay.
  /// `onBackdropTap` (when set) fires on a tap in the dim area outside the card,
  /// so a stray tap never leaves the player stuck behind the modal.
  private func overlayCard<C: View>(onBackdropTap: (() -> Void)? = nil, @ViewBuilder _ content: () -> C) -> some View {
    ModalCard(onBackdropTap: onBackdropTap) { content() }
  }

  // MARK: - actions
  private func onCell(_ r: Int, _ c: Int) {
    let before = engine.eventSeq
    engine.cycle(r, c)
    if engine.eventSeq != before {
      let cell = engine.lastEventCell
      switch engine.lastEventType {
      case 2:
        shakeCell = cell; shakeToken += 1
        TraceFX.bad()
        showToast("−1 vie · −\(abs(engine.lastEventPoints)) pts", good: false)
      case 1:
        flash(cell)
        TraceFX.good()
        if engine.lastEventPoints > 0 { showToast("+\(engine.lastEventPoints) pts !", good: true) }
      default: break
      }
    }
    version += 1
    afterMove()
  }

  private func onHint() {
    let before = engine.eventSeq
    engine.useHint()
    if engine.eventSeq != before { flash(engine.lastEventCell) }
    version += 1
    afterMove()
  }

  private func flash(_ cell: Int) {
    flashCell = cell
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { if flashCell == cell { flashCell = -1 } }
  }

  private func afterMove() {
    if engine.gridWon && !engine.runOver {
      let solvedAtCelebration = engine.solvedCount
      DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
        if engine.gridWon && !engine.runOver && engine.solvedCount == solvedAtCelebration {
          engine.nextGrid(); version += 1
          if engine.runOver { onRunOver() }
        }
      }
    }
    if engine.runOver { onRunOver() }
  }

  private func onRunOver() {
    guard engine.runOver, !didSubmit else { return }
    didSubmit = true
    version += 1
    // End-of-run jingle. On a lost run the last life's "womp" just played on the
    // same audio player, so the lose jingle waits a beat instead of cutting it off.
    if engine.runWon {
      TraceFX.win()
    } else {
      DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) { TraceFX.lose() }
    }
    guard ranked else { return }
    let waves = max(1, engine.solvedCount)
    let isRanked = GameStore.record(game: "trace", today: GameStore.epochDay(), score: engine.finalScore(), waves: waves)
    guard isRanked else { return }
    let finalScore = engine.finalScore()
    Task {
      let r = try? await WhoCalledAPI().submitGameScore(score: finalScore, waves: waves, day: GameStore.epochDay(), game: "trace")
      await MainActor.run { rank = r }
    }
  }

  // MARK: - loading
  private func load() async {
    loading = true; loadError = false
    do {
      let set: TracePuzzleSetDTO = ranked
        ? try await WhoCalledAPI().tracePuzzle(day: GameStore.epochDay())
        : try await WhoCalledAPI().traceTraining(size: practiceSize, seed: Int(Date().timeIntervalSince1970))
      await MainActor.run { engine.setPuzzles(set.grids, ranked: ranked); loading = false; version += 1 }
    } catch { await MainActor.run { loadError = true; loading = false; version += 1 } }
  }

  private func reloadTraining(autoStart: Bool) async {
    loading = true; loadError = false
    do {
      let set = try await WhoCalledAPI().traceTraining(size: practiceSize, seed: Int(Date().timeIntervalSince1970))
      await MainActor.run {
        engine.setPuzzles(set.grids, ranked: false)
        if autoStart { engine.start(); didSubmit = false; rank = nil }
        loading = false; version += 1
      }
    } catch { await MainActor.run { loadError = true; loading = false; version += 1 } }
  }
}

/// Floating modal card: softly dimmed backdrop, drop shadow, spring entrance.
/// A tap in the dim backdrop fires `onBackdropTap` (close-on-outside-tap); a tap
/// on the card itself is absorbed so it never accidentally dismisses.
private struct ModalCard<C: View>: View {
  var onBackdropTap: (() -> Void)? = nil
  @ViewBuilder var content: () -> C
  @State private var shown = false

  var body: some View {
    ZStack {
      Color.black.opacity(0.55).ignoresSafeArea()
        .contentShape(Rectangle())
        .onTapGesture { onBackdropTap?() }
      VStack(spacing: 10) { content() }
        .padding(.horizontal, 20).padding(.vertical, 24)
        .frame(maxWidth: 400)
        .background(LinearGradient(colors: [Color(red: 0.071, green: 0.227, blue: 0.369), Color(red: 0.047, green: 0.145, blue: 0.251)], startPoint: .top, endPoint: .bottom))
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color(red: 0.49, green: 0.827, blue: 0.988).opacity(0.5), lineWidth: 1))
        .shadow(color: .black.opacity(0.5), radius: 30, y: 14)
        .scaleEffect(shown ? 1 : 0.86)
        .opacity(shown ? 1 : 0)
        .padding(.horizontal, 20)
        .contentShape(RoundedRectangle(cornerRadius: 24))
        .onTapGesture { /* absorb: a tap on the card never dismisses */ }
        .onAppear { withAnimation(.spring(response: 0.38, dampingFraction: 0.72)) { shown = true } }
    }
  }
}

/// A small horizontal shake driven by an animatable counter (used on a wrong tap).
struct Shake: GeometryEffect {
  var animatableData: CGFloat
  func effectValue(size: CGSize) -> ProjectionTransform {
    ProjectionTransform(CGAffineTransform(translationX: 6 * sin(animatableData * .pi * 5), y: 0))
  }
}

/// Interactive first-play tutorial — the 3 RULES first, then each action tied to
/// a rule, on a 4×4 demo that ends as a FULL valid solve (every colour, row and
/// column gets its fraudster — a 3×3 can't, so the demo used to end incoherent
/// with a colourless fraudster region). Spotlight on the target cell (everything
/// else dimmed) + a big 👆 pointer:
///  0. the 3 rules (no action)
///  1. 🎨 DOUBLE-TAP the single-cell PINK region → fraudster (1 per colour)
///  2. 📏 AUTO: ✗ over its row + column (1 per row/column)
///  3. 🚫 AUTO: ✗ over everything touching it (never adjacent)
///  4. a free cell EMERGES on row 2 → double-tap it (deduction!)
///  5. deliberate mistake on the red-ringed cell → −1 ❤️
///  6. a 3rd tap erases it
///  7. AUTO: finish the solve — all 4 colours get their fraudster
/// Shown once, replayable via ?.
private struct TraceTutorialOverlay: View {
  let villain: String
  let onDone: () -> Void

  @State private var step = 0
  // True after the expected action is done: the grid freezes and the player
  // advances THEMSELVES via « Suivant » — no auto-advance, nobody loses track.
  @State private var awaitingNext = false
  // Demo grid marks (4×4): 0 empty · 1 ✗ · 2 fraudster.
  @State private var demo = [[0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]]
  @State private var lives = 3
  @State private var shakeToken: CGFloat = 0
  @State private var bubble: TraceToast?
  @State private var bubbleSeq = 0
  @State private var pulse = false
  @State private var hintBubble: String?

  private let pink = Color(red: 1.0, green: 0.420, blue: 0.541)   // vivid
  private let teal = Color(red: 0.122, green: 0.820, blue: 0.753) // vivid
  private let amber = Color(red: 1.0, green: 0.690, blue: 0.125)  // vivid
  private let violet = Color(red: 0.725, green: 0.549, blue: 1.0) // vivid
  // Regions: PINK = the single-cell gimme, TEAL = where a free cell emerges on
  // row 2 (index 1), AMBER = bottom-left L, VIOLET = the rest. The layout makes
  // the full solve (0,1) (1,3) (2,0) (3,2) the ONLY possible one.
  private let easy = (r: 0, c: 1)  // single-cell PINK region → obvious fraudster
  private let emerging = (r: 1, c: 3)  // only free cell on row 2
  private let wrong = (r: 2, c: 2)  // touching the (1,3) fraudster diagonally → a real mistake
  // Multi-target sets for user-driven X placement (steps 2, 3, 4).
  private let rowColLine: Set<[Int]> = [[0, 0], [0, 2], [0, 3]]
  private let rowColCol: Set<[Int]> = [[1, 1], [2, 1], [3, 1]]
  private let adjTaps: Set<[Int]> = [[1, 0], [1, 2]]
  // Epilogue after the last fraudster: fade-in remaining ✗.
  private let remainingX: [(r: Int, c: Int)] = [(2, 2), (2, 3), (3, 0), (3, 1), (3, 3)]

  private var singleTarget: (r: Int, c: Int)? {
    if awaitingNext { return nil }
    switch step {
    case 1: return easy
    case 5: return emerging
    case 6: fallthrough
    case 7: return wrong
    case 8:
      if demo[2][0] != 2 { return (2, 0) }
      if demo[3][2] != 2 { return (3, 2) }
      return nil
    default: return nil
    }
  }
  private var multiTargets: Set<[Int]> {
    if awaitingNext { return [] }
    switch step {
    case 2: return Set(rowColLine.filter { demo[$0[0]][$0[1]] == 0 })
    case 3: return Set(rowColCol.filter { demo[$0[0]][$0[1]] == 0 })
    case 4: return Set(adjTaps.filter { demo[$0[0]][$0[1]] == 0 })
    default: return []
    }
  }
  private var spotlight: Set<[Int]> {
    if awaitingNext { return [] }
    if let st = singleTarget { return [[st.r, st.c]] }
    return multiTargets
  }
  private var pointerTarget: [Int]? { singleTarget.map { [$0.r, $0.c] } ?? multiTargets.first }
  private var hasSpotlight: Bool { !spotlight.isEmpty }
  private func isTarget(_ r: Int, _ c: Int) -> Bool { spotlight.contains([r, c]) }

  private var firstTapHint: Bool {
    guard let st = singleTarget else { return false }
    return demo[st.r][st.c] == 1
  }

  private var instruction: String {
    if awaitingNext {
      switch step {
      case 1: return "Bien. 1 fraudeur par couleur."
      case 2: return "Voila. Chaque X elimine une case.\nMoins de cases = plus facile\nde trouver le prochain."
      case 3: return "1 fraudeur par ligne, 1 par colonne.\nLes X sont ta memoire : interdit."
      case 4: return "Tout ce qui touche un fraudeur\nest sur : on le coche X."
      case 5: return "Bien joue. Les X reduisent le champ\njusqu'a ce qu'il ne reste qu'une\ncase possible. C'est tout le principe."
      case 6: return "Mal place : il touchait un fraudeur.\n-1 vie, -200 pts."
      case 7: return "Efface. Le cycle :\nvide, X, fraudeur, vide."
      default: return "Termine. Chaque couleur, chaque ligne,\nchaque colonne a SON fraudeur.\nA toi de jouer !"
      }
    }
    switch step {
    case 0: return "3 regles, c'est tout :\n1 fraudeur par couleur -- 1 par ligne\net colonne -- jamais cote a cote"
    case 1: return firstTapHint
      ? "Encore une touche pour le placer !"
      : "La zone rose n'a qu'une case :\nle fraudeur est forcement la.\nDouble-touche pour le placer."
    case 2: return "Ces cases ne pourront jamais\navoir de fraudeur. Pose un X\nsur les 3 cases eclairees."
    case 3: return "1 fraudeur par ligne, 1 par colonne :\nchacun sa place. Pose un X sur\nles 3 cases eclairees."
    case 4: return "Jamais cote a cote, meme\nen diagonale. Pose un X sur\nles cases eclairees."
    case 5: return firstTapHint ? "Encore une touche !"
      : "Ligne 2 : une seule case libre.\nLe fraudeur est forcement la.\nDouble-touche pour le placer."
    case 6: return firstTapHint ? "Encore une touche..."
      : "Et si on se trompe ?\nDouble-touche la case encadree\nde rouge."
    case 7: return "Une 3eme touche efface.\nRetouche la case."
    case 8:
      if demo[2][0] != 2 {
        return demo[2][0] == 1 ? "Encore une touche !"
          : "Plus que 2 fraudeurs.\nLigne 3 : une seule case libre.\nDouble-touche-la."
      } else {
        return demo[3][2] == 1 ? "Encore une touche !"
          : "La derniere : ligne 4 !\nDouble-touche-la."
      }
    default: return ""
    }
  }

  private func goTo(_ s: Int) {
    step = s; awaitingNext = false; bubble = nil; hintBubble = nil
    for r in 0..<4 { for c in 0..<4 { demo[r][c] = 0 } }
    lives = 3
    if s >= 2 { demo[0][1] = 2 }
    if s >= 3 { for cell in rowColLine { demo[cell[0]][cell[1]] = 1 } }
    if s >= 4 { for cell in rowColCol { demo[cell[0]][cell[1]] = 1 } }
    if s >= 5 { for cell in adjTaps { demo[cell[0]][cell[1]] = 1 } }
    if s >= 6 { demo[1][3] = 2 }
    if s >= 7 { demo[2][2] = 2; lives = 2 }
    if s >= 8 { demo[2][2] = 0; lives = 2 }
  }

  private func next() { guard awaitingNext else { return }; awaitingNext = false; step += 1 }

  private func tap(_ r: Int, _ c: Int) {
    if awaitingNext || step == 0 { return }
    // Multi-target X-placement steps (2,3,4): tap highlighted cell → ✗.
    if (2...4).contains(step) {
      guard multiTargets.contains([r, c]) else {
        hintBubble = "Touche une case qui brille"
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { if self.hintBubble == "Touche une case qui brille" { self.hintBubble = nil } }
        return
      }
      demo[r][c] = 1
      if multiTargets.count <= 1 { awaitingNext = true }
      return
    }
    // Single-target steps: only the spotlight cell reacts.
    guard let st = singleTarget, r == st.r, c == st.c else {
      hintBubble = "Touche la case qui brille"
      DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { if self.hintBubble == "Touche la case qui brille" { self.hintBubble = nil } }
      return
    }
    switch step {
    case 1, 5, 8:
      if demo[r][c] == 0 { demo[r][c] = 1 }
      else if demo[r][c] == 1 {
        demo[r][c] = 2; TraceFX.good()
        showBubble("+\(TraceEngine.placeBonus) pts !", good: true)
        if step == 8, demo[3][2] == 2 {
          var d = 0.0
          for (sr, sc) in remainingX {
            d += 0.12
            let rd = d; DispatchQueue.main.asyncAfter(deadline: .now() + rd) { self.demo[sr][sc] = 1 }
          }
          DispatchQueue.main.asyncAfter(deadline: .now() + d + 0.2) { self.awaitingNext = true }
        } else { awaitingNext = true }
      }
    case 6:
      if demo[r][c] == 0 { demo[r][c] = 1 }
      else if demo[r][c] == 1 { demo[r][c] = 2; lives = 2; shakeToken += 1; TraceFX.bad(); showBubble("−1 vie · −\(TraceEngine.errorCost) pts", good: false); awaitingNext = true }
    case 7: demo[r][c] = 0; showBubble("Efface", good: true); awaitingNext = true
    default: break
    }
  }

  var body: some View {
    ZStack {
      // Tap outside the card = skip (same as « Passer ») — never blocks.
      Color.black.opacity(0.78).ignoresSafeArea()
        .contentShape(Rectangle())
        .onTapGesture { onDone() }
      VStack(spacing: 14) {
        Text("TUTO — \(villain) Comment jouer").font(.headline.bold()).foregroundStyle(.white)
        // Lives mirror the real HUD so the −1 vie step reads instantly.
        HStack(spacing: 2) {
          ForEach(0..<3, id: \.self) { i in
            Text(i < lives ? "❤️" : "💔").font(.system(size: 16)).opacity(i < lives ? 1 : 0.75)
          }
        }
        Text(instruction)
          .font(.subheadline.weight(.semibold)).foregroundStyle(TraceEngine.ink)
          .multilineTextAlignment(.center).frame(minHeight: 58)

        demoGrid
          .overlay(alignment: .top) { bubbleView.offset(y: -18) }

        // Step dots — tap a past dot (or ‹) to replay from there.
        HStack(spacing: 8) {
          Button { if step > 0 { goTo(step - 1) } } label: {
            Image(systemName: "chevron.left")
              .font(.caption.weight(.bold))
              .foregroundStyle(TraceEngine.ink.opacity(step > 0 ? 0.7 : 0.15))
          }
          .buttonStyle(.plain)
          .disabled(step == 0)
          ForEach(0..<9, id: \.self) { i in
            Circle()
              .fill(i <= step ? TraceEngine.cy : Color.white.opacity(0.2))
              .frame(width: 8, height: 8)
              .contentShape(Circle().inset(by: -6))
              .onTapGesture { if i < step { goTo(i) } }
          }
        }
        if step == 0 {
          // The 3 rules, no action — a single « Compris » to start.
          Button { step = 1 } label: {
            Text("Compris ▸").fontWeight(.bold).padding(.horizontal, 24).padding(.vertical, 12)
          }.buttonStyle(.borderedProminent).tint(TraceEngine.cy)
          Button(action: onDone) { Text("Passer").font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.6)) }
            .buttonStyle(.plain)
        } else if awaitingNext && step >= 8 {
          // Final recap → straight to the game.
          Button(action: onDone) {
            Text("C'est parti ▸").fontWeight(.bold).padding(.horizontal, 24).padding(.vertical, 12)
          }.buttonStyle(.borderedProminent).tint(TraceEngine.cy)
        } else if awaitingNext {
          // Action done → the player reads the recap and advances THEMSELVES.
          Button(action: next) {
            Text("Suivant ▸").fontWeight(.bold).padding(.horizontal, 24).padding(.vertical, 12)
          }.buttonStyle(.borderedProminent).tint(TraceEngine.cy)
          Button(action: onDone) { Text("Passer").font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.6)) }
            .buttonStyle(.plain)
        } else {
          Button(action: onDone) { Text("Passer").font(.caption).foregroundStyle(TraceEngine.ink.opacity(0.6)) }
            .buttonStyle(.plain)
        }
      }
      .padding(.horizontal, 20).padding(.vertical, 24)
      .frame(maxWidth: 400)
      .background(LinearGradient(colors: [Color(red: 0.071, green: 0.227, blue: 0.369), Color(red: 0.047, green: 0.145, blue: 0.251)], startPoint: .top, endPoint: .bottom))
      .clipShape(RoundedRectangle(cornerRadius: 24))
      .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color(red: 0.49, green: 0.827, blue: 0.988).opacity(0.5), lineWidth: 1))
      .padding(.horizontal, 20)
      .contentShape(RoundedRectangle(cornerRadius: 24))
      .onTapGesture { /* absorb: a tap on the tutorial card never skips it */ }
    }
    .onAppear { withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) { pulse = true } }
  }

  private var demoGrid: some View {
    // Regions: PINK = the single-cell gimme, TEAL = where a free cell emerges,
    // AMBER = bottom-left L, VIOLET = the rest.
    let tints: [[Color]] = [
      [amber, pink, teal, teal],
      [amber, amber, teal, teal],
      [amber, violet, violet, violet],
      [violet, violet, violet, violet],
    ]
    let cell: CGFloat = 46
    return VStack(spacing: 3) {
      ForEach(0..<4, id: \.self) { r in
        HStack(spacing: 3) {
          ForEach(0..<4, id: \.self) { c in
            demoCell(r, c, tint: tints[r][c], size: cell)
          }
        }
      }
    }
    .padding(8)
    .background(RoundedRectangle(cornerRadius: 14).fill(Color(red: 0.039, green: 0.110, blue: 0.188)))
  }

  private func demoCell(_ r: Int, _ c: Int, tint: Color, size: CGFloat) -> some View {
    let onTarget = isTarget(r, c)
    let dimmed = hasSpotlight && !onTarget
    let isWrongTarget = (step == 5 || step == 6) && onTarget
    let ring: Color = isWrongTarget ? Color(red: 0.886, green: 0.235, blue: 0.235)
      : onTarget ? Color(red: 1.0, green: 0.180, blue: 0.420)  // « rose vif » = tap here
      : Color.white.opacity(0.35)
    return ZStack {
      // Bright pulsing halo behind the target cell.
      if onTarget {
        RoundedRectangle(cornerRadius: 12)
          .fill(ring.opacity(pulse ? 0.60 : 0.25))
          .frame(width: size + 10, height: size + 10)
          .scaleEffect(pulse ? 1.10 : 1.0)
      }
      ZStack {
        RoundedRectangle(cornerRadius: 7).fill(tint)
        if demo[r][c] == 1 {
          SafeCross(size: size * 0.42)
        } else if demo[r][c] == 2 {
          Text(villain).font(.system(size: size * 0.58))
        }
        // Spotlight: everything NOT targeted sinks under a strong dim.
        if dimmed {
          RoundedRectangle(cornerRadius: 7).fill(Color.black.opacity(0.55))
        }
      }
      .frame(width: size, height: size)
      .overlay(
        RoundedRectangle(cornerRadius: 7)
          .stroke(ring, lineWidth: onTarget ? 3 : 1)
          // Never faint: 0.65 ↔ 1.0 instead of the old 0.35 ↔ 1.0.
          .opacity(onTarget ? (pulse ? 1.0 : 0.65) : 1)
      )
      .scaleEffect(onTarget && pulse ? 1.06 : 1.0)
      .modifier(Shake(animatableData: (r == wrong.r && c == wrong.c) ? shakeToken : 0))
      .animation(.linear(duration: 0.4), value: shakeToken)
      .contentShape(Rectangle())
      .onTapGesture { tap(r, c) }
      // Big 👆 tap pointer bouncing on the target cell.
      if onTarget {
        Text("👆")
          .font(.system(size: 26))
          .offset(x: 18, y: 20)
          .scaleEffect(pulse ? 1.12 : 1.0)
      }
    }
  }

  private func showBubble(_ text: String, good: Bool) {
    bubbleSeq += 1
    let b = TraceToast(id: bubbleSeq, text: text, good: good)
    bubble = b
    DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) { if bubble == b { bubble = nil } }
  }

  private var bubbleView: some View {
    ZStack {
      if let b = bubble {
        ToastBubbleView(text: b.text, good: b.good)
          .id(b.id)
          .transition(.opacity)
      }
      if let h = hintBubble {
        Text(h).font(.caption.weight(.medium)).foregroundStyle(.white)
          .padding(.horizontal, 12).padding(.vertical, 6)
          .background(RoundedRectangle(cornerRadius: 16).fill(Color.black.opacity(0.75)))
          .transition(.opacity)
      }
    }
    .animation(.easeOut(duration: 0.25), value: bubble)
    .animation(.easeOut(duration: 0.2), value: hintBubble)
  }
}
