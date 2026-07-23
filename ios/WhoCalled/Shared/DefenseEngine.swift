import SwiftUI

/// DEFENSE — arcade shmup engine (logic + light rendering state). Swift port of the
/// Android `DefenseEngine`. You pilot a shield-ship that auto-fires; destroy the RED
/// spam calls before they breach the shield line, catch green boosts, and charge the
/// "Who Called" super-shield. Difficulty ramps with TIME (+ a SURGE every 4 waves)
/// and score scales with the wave, so an easy level can't be farmed.
final class DefenseEngine {
  // palette (SwiftUI colors) --------------------------------------------
  static let cy = Color(red: 0.169, green: 0.878, blue: 0.776)
  static let bl = Color(red: 0.298, green: 0.608, blue: 1.0)
  static let spam = Color(red: 1.0, green: 0.365, blue: 0.424)
  static let safe = Color(red: 0.216, green: 0.886, blue: 0.604)
  static let amber = Color(red: 1.0, green: 0.761, blue: 0.294)
  static let ink = Color(red: 0.918, green: 0.941, blue: 1.0)
  static let spark = Color(red: 0.749, green: 0.914, blue: 1.0)
  static let slowBlue = Color(red: 0.561, green: 0.831, blue: 1.0)
  static let redInner = Color(red: 1.0, green: 0.604, blue: 0.643)
  static let redOuter = Color(red: 0.878, green: 0.196, blue: 0.247)
  static let redArmored = Color(red: 0.702, green: 0.122, blue: 0.173)
  static let pickLight = Color(red: 0.518, green: 0.949, blue: 0.776)
  static let pickDark = Color(red: 0.122, green: 0.741, blue: 0.514)
  static let rapidBullet = Color(red: 1.0, green: 0.878, blue: 0.541)
  static let ambientOrange = Color(red: 1.0, green: 0.471, blue: 0.353)
  static let comboHot = Color(red: 1.0, green: 0.482, blue: 0.627)
  static let burstPink = Color(red: 1.0, green: 0.545, blue: 0.588)
  static let burstMint = Color(red: 0.498, green: 0.941, blue: 0.761)
  static let darkText = Color(red: 0.016, green: 0.071, blue: 0.173)
  static let starBright = Color(red: 0.624, green: 0.816, blue: 1.0)
  static let starDim = Color(red: 0.290, green: 0.373, blue: 0.541)

  final class Enemy { var x, y, r, vy, vx, amp, t, bx: CGFloat; var hp: Int; let type: String; var glyph = "📞"; let maxHp: Int
    init(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ vy: CGFloat, _ vx: CGFloat, _ amp: CGFloat, _ hp: Int, _ type: String) {
      self.x = x; self.y = y; self.r = r; self.vy = vy; self.vx = vx; self.amp = amp; self.hp = hp; self.type = type; self.maxHp = hp; self.t = CGFloat.random(in: 0...6.28); self.bx = 0 } }
  final class Bullet { var x, y: CGFloat; let vy, vx: CGFloat; init(_ x: CGFloat, _ y: CGFloat, _ vy: CGFloat, _ vx: CGFloat) { self.x = x; self.y = y; self.vy = vy; self.vx = vx } }
  final class Friend { var x, y, t: CGFloat; let r, vy: CGFloat; let glyph: String; let special: Bool
    init(_ x: CGFloat, _ r: CGFloat, _ vy: CGFloat, _ glyph: String, _ special: Bool) { self.x = x; self.y = -r; self.r = r; self.vy = vy; self.glyph = glyph; self.special = special; self.t = 0 } }
  final class Particle { var x, y, vx, vy, age: CGFloat; let life: CGFloat; let color: Color; init(_ x: CGFloat, _ y: CGFloat, _ vx: CGFloat, _ vy: CGFloat, _ life: CGFloat, _ color: Color) { self.x = x; self.y = y; self.vx = vx; self.vy = vy; self.life = life; self.age = 0; self.color = color } }
  final class Ring { var x, y, r, age: CGFloat; let max, life: CGFloat; let color: Color; init(_ x: CGFloat, _ y: CGFloat, _ mx: CGFloat, _ color: Color) { self.x = x; self.y = y; self.r = 8; self.max = mx; self.age = 0; self.life = 0.45; self.color = color } }
  final class Pop { var x, y, age: CGFloat; let text: String; let life: CGFloat; let color: Color; init(_ x: CGFloat, _ y: CGFloat, _ text: String, _ color: Color) { self.x = x; self.y = y; self.text = text; self.age = 0; self.life = 0.8; self.color = color } }
  final class Star { var x, y: CGFloat; let z: CGFloat; init(_ x: CGFloat, _ y: CGFloat, _ z: CGFloat) { self.x = x; self.y = y; self.z = z } }

  static let glyphs = ["🛠️", "🏠", "☎️", "📱", "🐺", "🦊", "🐻", "😈", "😼", "👺"]
  static let friendGlyphs = ["👵", "👨‍⚕️", "👶", "🧑‍🍳", "🐥", "👩‍🏫", "🧑‍🚒", "💚"]
  static let itemPool = ["rapid", "spread", "slow", "repair", "rapid", "spread", "slow", "repair", "shield"]
  static func itemLabel(_ i: String) -> String {
    switch i { case "rapid": return "TIR RAPIDE"; case "spread": return "TIR ×5"; case "slow": return "RALENTI"; case "repair": return "RÉPARATION"; default: return "BOUCLIER" }
  }
  static func itemGlyph(_ i: String) -> String {
    switch i { case "rapid": return "⚡"; case "spread": return "✳️"; case "slow": return "❄️"; case "repair": return "💚"; default: return "🛡️" }
  }
  let shMax: CGFloat = 100
  let chargeFull = 14

  var w: CGFloat = 0, h: CGFloat = 0
  var shipX: CGFloat = 0, shipY: CGFloat = 0; let shipW: CGFloat = 34
  private var targetX: CGFloat = 0

  var bullets: [Bullet] = [], reds: [Enemy] = [], friends: [Friend] = []
  var parts: [Particle] = [], rings: [Ring] = [], pops: [Pop] = [], stars: [Star] = []

  var shield: CGFloat = 100, combo = 0, score = 0, wave = 1, blocked = 0, charge = 0
  var item: String?
  private func randomItem() -> String { Self.itemPool.randomElement()! }
  private func earnCharge(_ n: Int) { charge = min(chargeFull, charge + n); if charge >= chargeFull, item == nil { item = randomItem() } }
  private var comboT: CGFloat = 0
  var elapsed: CGFloat = 0; private var spawnT: CGFloat = 0, pickT: CGFloat = 10, fireT: CGFloat = 0
  var rapid: CGFloat = 0, spread: CGFloat = 0, slow: CGFloat = 0
  var firing = false; var muzzle: CGFloat = 0 // hold-to-fire + muzzle flash
  var shake: CGFloat = 0, lineFlash: CGFloat = 0, flash: CGFloat = 0
  private var eventT: CGFloat = 12; var fakeA: CGFloat = 0; var fakeDigits = ""; private var lastWave = 1
  var nova: CGFloat = 0
  var running = false, over = false
  private var lastFrame: TimeInterval = 0

  var rank: GameScoreResponseDTO?
  var onGameOver: ((Int, Int) -> Void)?

  func lineY() -> CGFloat { h - 58 }
  private func rand(_ a: CGFloat, _ b: CGFloat) -> CGFloat { a + CGFloat.random(in: 0...1) * (b - a) }
  private func waveMult() -> CGFloat { 1 + CGFloat(wave - 1) * 0.12 }

  func setSize(_ nw: CGFloat, _ nh: CGFloat) {
    w = nw; h = nh; if stars.isEmpty { initStars() }
    if shipX == 0 { shipX = w / 2; targetX = w / 2 }
    shipY = h - 30
  }
  private func initStars() { stars = (0..<48).map { _ in Star(CGFloat.random(in: 0...max(w, 1)), CGFloat.random(in: 0...max(h, 1)), rand(0.3, 1)) } }
  func pointer(_ x: CGFloat) { targetX = min(max(x, shipW / 2), w - shipW / 2) }

  func start() {
    bullets = []; reds = []; friends = []; parts = []; rings = []; pops = []
    shipX = w / 2; targetX = w / 2; shipY = h - 30
    shield = shMax; score = 0; combo = 0; comboT = 0; wave = 1; blocked = 0; charge = 0; item = nil
    elapsed = 0; spawnT = 0; pickT = 6; fireT = 0; rapid = 0; spread = 0; slow = 0
    firing = false; muzzle = 0
    shake = 0; lineFlash = 0; flash = 0; eventT = 6; fakeA = 0; fakeDigits = ""; lastWave = 1; nova = 0
    rank = nil; initStars(); running = true; over = false; lastFrame = 0
    for i in 0..<4 { makeRed(y0: -20 - CGFloat(i) * 80) }  // alive immediately
  }

  /// Advance the sim to `date` (called each animation frame).
  func advance(to date: Date) {
    let now = date.timeIntervalSince1970
    if lastFrame != 0, running, !over {
      let dt = CGFloat(min(0.05, now - lastFrame))
      update(dt)
    }
    lastFrame = now
  }

  private func ease() -> CGFloat { elapsed < 5 ? 1.2 - elapsed * 0.04 : 1 }
  private func spawnInterval() -> CGFloat { max(300, 720 - elapsed * 5.2) * ease() }
  private func baseFall() -> CGFloat { 30 + elapsed * 1.15 + CGFloat(wave - 1) * 4 }

  private func makeRed(x: CGFloat? = nil, r0: CGFloat? = nil, hp0: Int? = nil, type0: String? = nil, vx0: CGFloat? = nil, vy0: CGFloat? = nil, y0: CGFloat? = nil) {
    let armoredP = min(0.26, elapsed * 0.0016), diverP = min(0.26, 0.04 + elapsed * 0.0018)
    let zigP = min(0.22, elapsed * 0.0015), splitP = min(0.14, (elapsed - 18) * 0.0016)
    var type = "normal"; var hp = 1; var r: CGFloat = 22; var vy = baseFall() * rand(0.9, 1.15); var vx = rand(-30, 30); var amp: CGFloat = 0
    let rr = CGFloat.random(in: 0...1)
    if splitP > 0 && rr < splitP { type = "split"; hp = 1; r = 26; vy *= 0.85 }
    else if rr < splitP + diverP { type = "diver"; vy *= 2.0; vx *= 0.4; r = 18 }
    else if rr < splitP + diverP + armoredP { type = "armored"; hp = 2; r = 25; vy *= 0.82 }
    else if rr < splitP + diverP + armoredP + zigP { type = "zig"; amp = rand(40, 80); vx = 0; r = 21; vy *= 0.92 }
    let ty = type0 ?? type, tr = r0 ?? r, thp = hp0 ?? hp, tvx = vx0 ?? vx, tvy = vy0 ?? vy
    let e = Enemy(x ?? rand(tr + 8, w - tr - 8), y0 ?? -tr, tr, tvy, tvx, ty == "zig" ? amp : 0, thp, ty)
    e.glyph = ty == "boss" ? "👹" : Self.glyphs.randomElement()!
    reds.append(e)
  }

  private func spawnBoss() { makeRed(x: w / 2, r0: 42, hp0: 5 + wave / 2, type0: "boss", vx0: rand(-28, 28), vy0: baseFall() * 0.5, y0: -52) }
  private func spawnFriend() {
    let r: CGFloat = 20; let special = CGFloat.random(in: 0...1) < 0.22
    let glyph = special ? "🎁" : Self.friendGlyphs.randomElement()!
    friends.append(Friend(rand(r + 10, w - r - 10), r, rand(70, 95), glyph, special))
  }

  /// Activate the held item (manual, Mario-Kart style).
  func useItem() {
    guard let it = item else { return }
    switch it {
    case "rapid": rapid = 8
    case "spread": spread = 8
    case "slow": slow = 4.5
    case "repair": shield = min(shMax, shield + 32)
    default: // "shield" — weaker: clears only the near threats
      flash = 0.5; shake = 8; lineFlash = 0.4; nova = 1
      let cutY = lineY() - 260
      var kept: [Enemy] = []
      for e in reds { if e.y >= cutY { score += Int(4 * waveMult()); burst(e.x, e.y, Self.cy, 6); ring(e.x, e.y, Self.cy, e.r + 14) } else { kept.append(e) } }
      reds = kept
    }
    pop(shipX, shipY - 44, Self.itemLabel(it), Self.cy)
    item = nil; charge = 0
  }
  private func ring(_ x: CGFloat, _ y: CGFloat, _ c: Color, _ mx: CGFloat) { rings.append(Ring(x, y, mx, c)) }
  private func burst(_ x: CGFloat, _ y: CGFloat, _ c: Color, _ n: Int) { for _ in 0..<n { let a = CGFloat.random(in: 0...6.28); let s = rand(40, 190); parts.append(Particle(x, y, cos(a) * s, sin(a) * s, rand(0.4, 0.8), c)) } }
  private func pop(_ x: CGFloat, _ y: CGFloat, _ text: String, _ c: Color) { pops.append(Pop(x, y, text, c)) }
  private func comboColor() -> Color { combo >= 12 ? Self.comboHot : (combo >= 6 ? Self.amber : Self.cy) }

  private func killRed(_ e: Enemy) {
    blocked += 1; combo += 1; comboT = 1.3
    let gain = Int((CGFloat(8 + combo * 2)) * waveMult()); score += gain
    ring(e.x, e.y, comboColor(), e.r + 26); burst(e.x, e.y, Self.burstPink, 12); pop(e.x, e.y - e.r, "+\(gain)", comboColor())
    earnCharge(1)
    if combo == 5 || combo == 10 || combo == 15 || combo == 25 { pop(w / 2, h * 0.36, "COMBO ×\(combo)", Self.amber); flash = max(flash, 0.35) }
    if e.type == "boss" { let b = Int(120 * waveMult()); score += b; earnCharge(4)
      pop(e.x, e.y, "BOSS +\(b)", Self.amber); ring(e.x, e.y, Self.amber, e.r + 90); burst(e.x, e.y, Self.amber, 30); flash = max(flash, 0.55); shake = 12 }
    if e.type == "split" { for k in [-1, 1] { makeRed(x: e.x, r0: 15, hp0: 1, type0: "normal", vx0: CGFloat(k) * 70, vy0: e.vy * 1.05, y0: e.y) } }
  }

  private func triggerEvent() {
    let pool = elapsed < 20 ? ["fake", "calm", "friend"] : ["fake", "swarm", "formation", "calm", "friend", "friend"]
    switch pool.randomElement()! {
    case "fake": fakeA = 1; fakeDigits = "+\(Int.random(in: 10...98)) \(three()) \(three())"
    case "swarm": for i in 0..<5 { makeRed(r0: 16, hp0: 1, type0: "normal", vx0: rand(-40, 40), vy0: baseFall() * 1.2, y0: -16 - CGFloat(i) * 30) }
    case "formation": let n = 4; for i in 0..<n { makeRed(x: CGFloat(i + 1) * w / CGFloat(n + 1), hp0: 1, type0: "normal", vx0: 0, vy0: baseFall() * 0.95) }
    case "calm": spawnT += 1.6
    default: spawnFriend()
    }
    eventT = rand(8, 14)
  }
  private func three() -> String { String(format: "%03d", Int.random(in: 0...999)) }

  func update(_ dt: CGFloat) {
    elapsed += dt; wave = 1 + Int(floor(elapsed / 13))
    if wave != lastWave { lastWave = wave
      if wave % 4 == 0 { flash = max(flash, 0.4); pop(w / 2, h * 0.30, "SURGE ×\(wave)", Self.amber)
        for i in 0..<5 { makeRed(x: CGFloat(i + 1) * w / 6, hp0: 1, type0: "normal", vx0: 0, vy0: baseFall() * 1.05) } }
      if wave % 5 == 0 { pop(w / 2, h * 0.24, "⚠ BOSS", Self.spam); spawnBoss() } }
    let sd: CGFloat = slow > 0 ? 0.4 : 1; if slow > 0 { slow -= dt }; if rapid > 0 { rapid -= dt }; if spread > 0 { spread -= dt }; if muzzle > 0 { muzzle = max(0, muzzle - dt) }
    spawnT -= dt; pickT -= dt; eventT -= dt
    if eventT <= 0 { triggerEvent() }
    if fakeA > 0 { fakeA -= dt * 0.42 }
    if nova > 0 { nova -= dt / 1.15 }
    if comboT > 0 { comboT -= dt; if comboT <= 0 { combo = 0 } }
    if spawnT <= 0 { makeRed(); spawnT = spawnInterval() / 1000; if elapsed > 28 && CGFloat.random(in: 0...1) < 0.3 { makeRed() } }
    if pickT <= 0 { spawnFriend(); pickT = rand(3.5, 6) }

    shipX += (targetX - shipX) * min(1, dt * 16); shipX = min(max(shipX, shipW / 2), w - shipW / 2)
    fireT -= dt
    // Hold to fire (no more continuous auto-fire).
    if firing && fireT <= 0 {
      fireT = rapid > 0 ? 0.06 : 0.13; let nose = shipY - 18; muzzle = 0.07
      if spread > 0 {
        bullets.append(Bullet(shipX, nose, -600, 0))
        bullets.append(Bullet(shipX, nose, -580, -120)); bullets.append(Bullet(shipX, nose, -580, 120))
        bullets.append(Bullet(shipX, nose, -540, -260)); bullets.append(Bullet(shipX, nose, -540, 260))
      } else { bullets.append(Bullet(shipX, nose, -600, 0)) }
    }

    for b in bullets { b.y += b.vy * dt; b.x += b.vx * dt }
    bullets.removeAll { $0.y < -12 || $0.x < -12 || $0.x > w + 12 }

    let ly = lineY()
    var i = reds.count - 1
    while i >= 0 {
      let e = reds[i]; e.t += dt * 4; e.y += e.vy * dt * sd
      if e.type == "zig" { e.bx += dt * 3 * sd; e.x = min(max(e.x + sin(e.bx) * e.amp * dt * 3, e.r), w - e.r) }
      else { e.x += e.vx * dt * sd; if e.x < e.r { e.x = e.r; e.vx *= -1 }; if e.x > w - e.r { e.x = w - e.r; e.vx *= -1 } }
      var hitDead = false
      var j = bullets.count - 1
      while j >= 0 { let b = bullets[j]; let dx = b.x - e.x; let dy = b.y - e.y
        if dx * dx + dy * dy < (e.r + 4) * (e.r + 4) { bullets.remove(at: j); e.hp -= 1; burst(b.x, b.y, Self.spark, 3)
          if e.hp <= 0 { reds.remove(at: i); killRed(e); hitDead = true; break } }
        j -= 1
      }
      if !hitDead && e.y >= ly { reds.remove(at: i)
        shield -= e.type == "diver" ? 18 : (e.type == "boss" ? 40 : 14); combo = 0; shake = 12; lineFlash = 1; flash = max(flash, 0.3); burst(e.x, ly, Self.spam, 16)
        if shield <= 0 { gameOver(); return } }
      i -= 1
    }
    // Green benevolent calls: harmless. Catching a special 🎁 grants a full item.
    var p = friends.count - 1
    while p >= 0 { let fr = friends[p]; fr.y += fr.vy * dt * sd; fr.t += dt * 4
      if fr.special {
        let dx = fr.x - shipX; let dy = fr.y - shipY
        if dx * dx + dy * dy < (fr.r + shipW * 0.6) * (fr.r + shipW * 0.6) { friends.remove(at: p)
          earnCharge(chargeFull); ring(fr.x, fr.y, Self.amber, fr.r + 30); burst(fr.x, fr.y, Self.amber, 20); pop(fr.x, fr.y, "BONUS 🎁", Self.amber); score += 10; p -= 1; continue
        }
      }
      if fr.y > h + fr.r { friends.remove(at: p) }
      p -= 1
    }
    for pt in parts { pt.age += dt; pt.x += pt.vx * dt; pt.y += pt.vy * dt; pt.vy += 170 * dt }
    parts.removeAll { $0.age >= $0.life }
    for r in rings { r.age += dt; r.r += (r.max - r.r) * min(1, dt * 6) }
    rings.removeAll { $0.age >= $0.life }
    for po in pops { po.age += dt; po.y -= 26 * dt }
    pops.removeAll { $0.age >= $0.life }
    for s in stars { s.y += (40 + elapsed * 3 + s.z * 90) * dt * sd; if s.y > h { s.y = -2; s.x = CGFloat.random(in: 0...w) } }
    if shake > 0 { shake = max(0, shake - dt * 40) }; if lineFlash > 0 { lineFlash = max(0, lineFlash - dt * 2.2) }; if flash > 0 { flash = max(0, flash - dt * 1.8) }
  }

  private func gameOver() { over = true; shield = 0; shake = 14; flash = 0.5; onGameOver?(score, wave) }
}
