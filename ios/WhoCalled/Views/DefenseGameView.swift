import SwiftUI

/// DEFENSE — the daily arcade game. Renders the `DefenseEngine` with a Canvas driven
/// by `TimelineView(.animation)`; drag to aim, auto-fire, tap the 🛡️ super-shield.
struct DefenseGameView: View {
  @Environment(\.dismiss) private var dismiss
  @State private var engine = DefenseEngine()
  @State private var showLeaderboard = false
  @State private var showQuitConfirm = false

  var body: some View {
    GeometryReader { geo in
      ZStack {
        LinearGradient(colors: [Color(red: 0.039, green: 0.075, blue: 0.149), Color(red: 0.039, green: 0.067, blue: 0.125)],
          startPoint: .top, endPoint: .bottom).ignoresSafeArea()

        TimelineView(.animation) { tl in
          let _ = engine.advance(to: tl.date)
          ZStack {
            Canvas { ctx, size in draw(ctx, size) }
              .contentShape(Rectangle())
              .gesture(DragGesture(minimumDistance: 0)
                .onChanged { engine.pointer($0.location.x); engine.firing = true }
                .onEnded { _ in engine.firing = false })

            // HUD
            VStack {
              HStack(spacing: 8) {
                Button { if engine.running && !engine.over { showQuitConfirm = true } else { dismiss() } } label: { Image(systemName: "chevron.left").foregroundStyle(DefenseEngine.ink) }
                Spacer()
                chip("SCORE", "\(engine.score)", DefenseEngine.ink)
                chip("WAVE", "\(engine.wave)", DefenseEngine.ink)
                chip("🛡", "\(Int((engine.shield / engine.shMax) * 100))%", (engine.shield / engine.shMax) < 0.34 ? DefenseEngine.spam : DefenseEngine.cy)
              }.padding(12)
              if engine.combo >= 3 && engine.running && !engine.over {
                Text("×\(engine.combo)\(engine.combo >= 8 ? " 🔥" : "")").bold().foregroundStyle(DefenseEngine.amber)
              }
              Spacer()
            }

            // Mario-Kart item slot: fills on kills, TAP to activate (visible gauge).
            VStack {
              Spacer()
              HStack {
                let ready = engine.item != nil
                let frac = min(1, CGFloat(engine.charge) / CGFloat(engine.chargeFull))
                VStack(spacing: 4) {
                  if ready { Text("OBJET PRÊT").font(.caption2.bold()).foregroundStyle(DefenseEngine.amber) }
                  ZStack {
                    Circle().fill(ready ? DefenseEngine.amber.opacity(0.2) : Color(red: 0.055, green: 0.09, blue: 0.188))
                    Circle().stroke(Color.white.opacity(0.2), lineWidth: 6)
                    Circle().trim(from: 0, to: frac)
                      .stroke(ready ? DefenseEngine.amber : DefenseEngine.cy, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                      .rotationEffect(.degrees(-90))
                    Text(ready ? DefenseEngine.itemGlyph(engine.item!) : "\(Int(frac * 100))%")
                      .font(ready ? .title2 : .caption.bold()).foregroundStyle(DefenseEngine.ink)
                  }
                  .frame(width: 66, height: 66)
                  .onTapGesture { engine.useItem() }
                }
                Spacer()
              }.padding(14)
            }

            if !engine.running && !engine.over { intro }
            if engine.over { gameOver }
          }
        }
      }
      .onAppear {
        engine.setSize(geo.size.width, geo.size.height)
        engine.onGameOver = { s, w in submit(s, w) }
      }
      .sheet(isPresented: $showLeaderboard) { LeaderboardView(game: "defense") }
      .confirmationDialog("Quitter la partie ?", isPresented: $showQuitConfirm, titleVisibility: .visible) {
        Button("Quitter", role: .destructive) { dismiss() }
        Button("Annuler", role: .cancel) {}
      } message: { Text("Tu perdras ta progression de la vague en cours.") }
    }
  }

  private func submit(_ score: Int, _ waves: Int) {
    let ranked = GameStore.record(game: "defense", today: GameStore.epochDay(), score: score, waves: waves)
    guard ranked else { return }
    Task { engine.rank = try? await WhoCalledAPI().submitGameScore(score: score, waves: waves, day: GameStore.epochDay()) }
  }

  private func chip(_ label: String, _ value: String, _ valueColor: Color) -> some View {
    VStack(spacing: 2) {
      Text(label).font(.system(size: 9, weight: .semibold, design: .monospaced)).foregroundStyle(DefenseEngine.ink.opacity(0.55))
      Text(value).font(.system(size: 14, weight: .bold, design: .monospaced)).foregroundStyle(valueColor)
    }.padding(.horizontal, 9).padding(.vertical, 5)
      .background(RoundedRectangle(cornerRadius: 11).fill(Color(red: 0.055, green: 0.09, blue: 0.188)))
  }

  private var intro: some View {
    overlay {
      Text("DEFENSE").font(.largeTitle.bold()).foregroundStyle(DefenseEngine.ink)
      Text("Maintiens appuyé pour tirer, glisse pour viser.\nDétruis les indésirables — ils remplissent ta jauge d'OBJET (à activer toi-même !).\nLes appels verts sont bienveillants ; attrape les 🎁 pour un objet immédiat.")
        .multilineTextAlignment(.center).foregroundStyle(DefenseEngine.ink.opacity(0.75)).padding(.horizontal, 28)
      Button { engine.start() } label: { Text("Commencer ▸").fontWeight(.bold).padding(.horizontal, 22).padding(.vertical, 12) }
        .buttonStyle(.borderedProminent).tint(DefenseEngine.cy)
      Button { showLeaderboard = true } label: { Text("🏆 Classement").padding(.horizontal, 16).padding(.vertical, 10) }
        .buttonStyle(.bordered).tint(DefenseEngine.ink)
    }
  }

  private var gameOver: some View {
    overlay {
      Text("Bouclier tombé").font(.title.bold()).foregroundStyle(DefenseEngine.ink)
      Text("\(engine.score)").font(.system(size: 44, weight: .heavy, design: .monospaced)).foregroundStyle(DefenseEngine.cy)
      Text("Vague \(engine.wave) · \(engine.blocked) spams bloqués").foregroundStyle(DefenseEngine.ink.opacity(0.7))
      if let r = engine.rank {
        Text("Classement du jour · top \(r.topPercent)%").bold().foregroundStyle(DefenseEngine.ink)
        Text("\(r.players) joueur\(r.players > 1 ? "s" : "") · meilleur \(r.bestScore)").font(.caption).foregroundStyle(DefenseEngine.ink.opacity(0.7))
      }
      HStack(spacing: 10) {
        ShareLink(item: "who-called DEFENSE · \(engine.score) pts · Vague \(engine.wave) 🛡️\nBloque les spams toi aussi 👉 \(AppConstants.Links.site)") {
          Text("Partager").padding(.horizontal, 16).padding(.vertical, 10)
        }.buttonStyle(.bordered).tint(DefenseEngine.ink)
        Button { engine.start() } label: { Text("Rejouer").fontWeight(.bold).padding(.horizontal, 16).padding(.vertical, 10) }
          .buttonStyle(.borderedProminent).tint(DefenseEngine.cy)
      }
      Button { dismiss() } label: { Text("Retour").foregroundStyle(DefenseEngine.ink.opacity(0.8)) }
    }
  }

  private func overlay<C: View>(@ViewBuilder _ content: () -> C) -> some View {
    ZStack { Color(red: 0.039, green: 0.067, blue: 0.133).opacity(0.92).ignoresSafeArea()
      VStack(spacing: 12) { content() } }
  }

  // MARK: - rendering
  private func shieldPath(_ cx: CGFloat, _ cy: CGFloat, _ size: CGFloat) -> Path {
    let s = size / 24
    func px(_ x: CGFloat) -> CGFloat { cx + (x - 12) * s }
    func py(_ y: CGFloat) -> CGFloat { cy + (y - 12.25) * s }
    var p = Path()
    p.move(to: CGPoint(x: px(12), y: py(1.5))); p.addLine(to: CGPoint(x: px(21), y: py(4.6))); p.addLine(to: CGPoint(x: px(21), y: py(11.5)))
    p.addCurve(to: CGPoint(x: px(12), y: py(23)), control1: CGPoint(x: px(21), y: py(16.6)), control2: CGPoint(x: px(17), y: py(21)))
    p.addCurve(to: CGPoint(x: px(3), y: py(11.5)), control1: CGPoint(x: px(7), y: py(21)), control2: CGPoint(x: px(3), y: py(16.6)))
    p.addLine(to: CGPoint(x: px(3), y: py(4.6))); p.closeSubpath()
    return p
  }
  private func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)) }

  private func draw(_ context: GraphicsContext, _ size: CGSize) {
    let e = engine; if e.w <= 0 { return }
    var g = context
    let sx = e.shake > 0 ? CGFloat.random(in: -e.shake...e.shake) * 0.5 : 0
    let sy = e.shake > 0 ? CGFloat.random(in: -e.shake...e.shake) * 0.5 : 0
    g.translateBy(x: sx, y: sy)

    // starfield
    for s in e.stars {
      var sc = g; sc.opacity = Double(0.25 + s.z * 0.5)
      sc.fill(Path(CGRect(x: s.x, y: s.y, width: 1.6, height: 1.6 + s.z * 2.4)), with: .color(s.z > 0.7 ? DefenseEngine.starBright : DefenseEngine.starDim))
    }
    // ambient tint
    let inten = min(1, CGFloat(e.combo) / 12 * 0.6 + CGFloat(e.wave - 1) / 10 * 0.5)
    let pulse = Double(0.06 + 0.05 * inten * (0.6 + 0.4 * sin(e.elapsed * 3)))
    let amb = e.slow > 0 ? DefenseEngine.bl : (inten > 0.55 ? DefenseEngine.ambientOrange : DefenseEngine.cy)
    g.fill(Path(CGRect(origin: .zero, size: size)),
      with: .radialGradient(Gradient(colors: [amb.opacity(pulse), .clear]), center: CGPoint(x: e.w / 2, y: e.h * 0.32), startRadius: 0, endRadius: e.h * 0.8))

    // background fake call
    if e.fakeA > 0 {
      let a = Double(min(0.28, e.fakeA * 0.28)); let rp = (1 - e.fakeA) * min(e.w, e.h) * 0.9
      var fg = g; fg.opacity = a * 1.4
      fg.stroke(circle(e.w / 2, e.h * 0.42, rp), with: .color(DefenseEngine.amber), lineWidth: 2)
      fg.stroke(circle(e.w / 2, e.h * 0.42, rp * 0.6), with: .color(DefenseEngine.amber), lineWidth: 2)
      var tg = g; tg.opacity = a
      tg.draw(Text("📞  INCOMING…").font(.system(size: 12, weight: .bold, design: .monospaced)).foregroundColor(DefenseEngine.amber), at: CGPoint(x: e.w / 2, y: e.h * 0.16))
      tg.draw(Text(e.fakeDigits).font(.system(size: 15, weight: .heavy, design: .monospaced)).foregroundColor(DefenseEngine.amber), at: CGPoint(x: e.w / 2, y: e.h * 0.16 + 20))
    }
    // super-shield brand splash
    if e.nova > 0 {
      let p = e.nova; let cy = e.h * 0.46; let sz = min(e.w, e.h) * 0.68 * (1.12 - p * 0.14)
      var ng = g; ng.opacity = Double(min(0.55, p * 0.6))
      ng.fill(shieldPath(e.w / 2, cy, sz), with: .linearGradient(Gradient(colors: [DefenseEngine.bl, DefenseEngine.cy]), startPoint: CGPoint(x: e.w / 2, y: cy - sz / 2), endPoint: CGPoint(x: e.w / 2, y: cy + sz / 2)))
      var tg = g; tg.opacity = Double(min(0.92, p * 1.1))
      tg.draw(Text("WHO").font(.system(size: sz * 0.135, weight: .heavy)).foregroundColor(DefenseEngine.darkText), at: CGPoint(x: e.w / 2, y: cy - sz * 0.09))
      tg.draw(Text("CALLED").font(.system(size: sz * 0.135, weight: .heavy)).foregroundColor(DefenseEngine.darkText), at: CGPoint(x: e.w / 2, y: cy + sz * 0.055))
    }

    // defense line + hp fill
    let ly = e.lineY(); let hp = min(max(e.shield / e.shMax, 0), 1)
    let lineC = e.lineFlash > 0 ? DefenseEngine.spam.opacity(Double(0.6 + 0.4 * e.lineFlash)) : DefenseEngine.cy.opacity(Double(0.32 + 0.4 * hp))
    var lp = Path(); lp.move(to: CGPoint(x: 6, y: ly)); lp.addLine(to: CGPoint(x: e.w - 6, y: ly))
    g.stroke(lp, with: .color(lineC), lineWidth: 3)
    g.fill(Path(CGRect(x: 6, y: ly + 2, width: (e.w - 12) * hp, height: e.h - ly - 4)),
      with: .color((e.lineFlash > 0 ? DefenseEngine.spam : DefenseEngine.cy).opacity(Double(0.04 + 0.10 * hp))))

    // rings
    for r in e.rings { let a = 1 - r.age / r.life; g.stroke(circle(r.x, r.y, r.r), with: .color(r.color.opacity(Double(a * 0.8))), lineWidth: 2.5) }
    // bullets
    let bc = e.rapid > 0 ? DefenseEngine.rapidBullet : DefenseEngine.spark
    for b in e.bullets { g.fill(Path(CGRect(x: b.x - 1.6, y: b.y - 8, width: 3.2, height: 12)), with: .color(bc)) }
    // reds
    for en in e.reds {
      let boss = en.type == "boss"
      let outer = boss ? Color(red: 0.541, green: 0.114, blue: 0.353) : (en.type == "armored" ? DefenseEngine.redArmored : DefenseEngine.redOuter)
      g.fill(circle(en.x, en.y, en.r), with: .radialGradient(Gradient(colors: [DefenseEngine.redInner, outer]), center: CGPoint(x: en.x, y: en.y), startRadius: 0, endRadius: en.r))
      if boss {
        let frac = CGFloat(en.hp) / CGFloat(max(en.maxHp, 1)); let rr = en.r + 6
        g.stroke(circle(en.x, en.y, rr), with: .color(.white.opacity(0.2)), lineWidth: 3)
        var arc = Path(); arc.addArc(center: CGPoint(x: en.x, y: en.y), radius: rr, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * Double(frac)), clockwise: false)
        g.stroke(arc, with: .color(DefenseEngine.amber), lineWidth: 3)
      }
      g.draw(Text(en.glyph).font(.system(size: en.r * (boss ? 1.0 : 1.1))), at: CGPoint(x: en.x, y: en.y))
    }
    // green benevolent calls (harmless); 🎁 specials get an amber halo
    for fr in e.friends {
      g.fill(circle(fr.x, fr.y, fr.r), with: .radialGradient(Gradient(colors: [DefenseEngine.pickLight, DefenseEngine.pickDark]), center: CGPoint(x: fr.x, y: fr.y), startRadius: 0, endRadius: fr.r))
      if fr.special { g.stroke(circle(fr.x, fr.y, fr.r + 5), with: .color(DefenseEngine.amber), lineWidth: 3) }
      g.draw(Text(fr.glyph).font(.system(size: fr.r * 1.15)), at: CGPoint(x: fr.x, y: fr.y))
    }
    // ship + muzzle flash (while holding fire)
    if e.running && !e.over {
      if e.muzzle > 0 {
        var mg = g; mg.opacity = Double(min(1, e.muzzle / 0.07) * 0.85)
        mg.fill(circle(e.shipX, e.shipY - 20, 8), with: .color(DefenseEngine.rapidBullet))
      }
      g.fill(shieldPath(e.shipX, e.shipY, e.shipW),
        with: .linearGradient(Gradient(colors: [DefenseEngine.bl, e.rapid > 0 ? DefenseEngine.rapidBullet : DefenseEngine.cy]),
          startPoint: CGPoint(x: e.shipX, y: e.shipY - e.shipW / 2), endPoint: CGPoint(x: e.shipX, y: e.shipY + e.shipW / 2)))
    }
    // particles
    for pt in e.parts { let a = 1 - pt.age / pt.life; g.fill(circle(pt.x, pt.y, 3), with: .color(pt.color.opacity(Double(a)))) }
    // score pops
    for po in e.pops {
      let a = 1 - po.age / po.life; var pg = g; pg.opacity = Double(a)
      let big = po.text.first == "C" || po.text.first == "S"
      pg.draw(Text(po.text).font(.system(size: big ? 20 : 13, weight: .bold, design: .monospaced)).foregroundColor(po.color), at: CGPoint(x: po.x, y: po.y))
    }

    // low-shield vignette (screen space, no shake)
    if hp < 0.34 {
      let v = (0.34 - hp) / 0.34 * (0.5 + 0.5 * sin(e.elapsed * 6))
      context.fill(Path(CGRect(origin: .zero, size: size)),
        with: .radialGradient(Gradient(colors: [.clear, Color(red: 1, green: 0.235, blue: 0.314).opacity(Double(0.28 * v))]),
          center: CGPoint(x: e.w / 2, y: e.h / 2), startRadius: e.h * 0.3, endRadius: e.h * 0.75))
    }
    if e.flash > 0 { context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.white.opacity(Double(e.flash * 0.22)))) }
  }
}
