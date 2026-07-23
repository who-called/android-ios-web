package com.whocalled.android.game

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * DEFENSE — arcade shmup engine (pure logic, no Android/Compose deps so it can be
 * unit-tested and rendered by any surface). You pilot a shield-ship that auto-fires;
 * destroy the RED spam calls before they breach the shield line. Catch green boosts,
 * ride the combo, and charge the "Who Called" super-shield to wipe the screen.
 *
 * Difficulty is a function of TIME (auto speed/density ramp) + a SURGE every 4 waves,
 * and score scales with the wave — so a player can't camp an easy level for points.
 */
class DefenseEngine {
    // entities ------------------------------------------------------------
    class Enemy(var x: Float, var y: Float, var r: Float, var vy: Float, var vx: Float,
                var amp: Float, var hp: Int, val type: String, var t: Float, var bx: Float,
                var glyph: String = "📞", val maxHp: Int = 1)
    class Bullet(var x: Float, var y: Float, val vy: Float, val vx: Float)
    // A benevolent green "call". Harmless (bullets pass through, no damage). Most
    // do nothing; `special` ones (🎁) grant a Mario-Kart-style item when caught.
    class Friend(var x: Float, var y: Float, val r: Float, val vy: Float, var t: Float, val glyph: String, val special: Boolean)
    class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, val life: Float, var age: Float, val color: Long)
    class Ring(var x: Float, var y: Float, var r: Float, val max: Float, var age: Float, val life: Float, val color: Long)
    class Pop(var x: Float, var y: Float, val text: String, var age: Float, val life: Float, val color: Long)
    class Star(var x: Float, var y: Float, val z: Float)

    // brand palette (ARGB) ------------------------------------------------
    companion object {
        const val CY = 0xFF2BE0C6L
        const val BL = 0xFF4C9BFFL
        const val SPAM = 0xFFFF5D6CL
        const val SAFE = 0xFF37E29AL
        const val AMBER = 0xFFFFC24BL
        const val INK = 0xFFEAF0FFL
        const val SPARK = 0xFFBFE9FFL
        const val SLOWBLUE = 0xFF8FD4FFL
        private const val SH_MAX = 100f
        private const val CHARGE_FULL = 14 // reds to destroy to earn one item
        // Spam "characters" — DIY/house/phones + sly animals & mischievous faces.
        private val GLYPHS = listOf("🛠️", "🏠", "☎️", "📱", "🐺", "🦊", "🐻", "😈", "😼", "👺")
        // Benevolent green callers (harmless atmosphere).
        private val FRIEND_GLYPHS = listOf("👵", "👨‍⚕️", "👶", "🧑‍🍳", "🐥", "👩‍🏫", "🧑‍🚒", "💚")
        // Item pool (Mario-Kart style) — shield is rare (1/9), so it's not spammy.
        private val ITEM_POOL = listOf("rapid", "spread", "slow", "repair", "rapid", "spread", "slow", "repair", "shield")
        fun itemLabel(i: String) = when (i) {
            "rapid" -> "TIR RAPIDE"; "spread" -> "TIR ×5"; "slow" -> "RALENTI"; "repair" -> "RÉPARATION"; else -> "BOUCLIER"
        }
        fun itemGlyph(i: String) = when (i) {
            "rapid" -> "⚡"; "spread" -> "✳️"; "slow" -> "❄️"; "repair" -> "💚"; else -> "🛡️"
        }
    }

    // geometry
    var w = 0f; var h = 0f
    var shipX = 0f; var shipY = 0f; val shipW = 34f
    private var targetX = 0f

    // collections
    val bullets = ArrayList<Bullet>()
    val reds = ArrayList<Enemy>()
    val friends = ArrayList<Friend>()
    val parts = ArrayList<Particle>()
    val rings = ArrayList<Ring>()
    val pops = ArrayList<Pop>()
    val stars = ArrayList<Star>()

    // scalars
    var shield = SH_MAX; var score = 0; var combo = 0; private var comboT = 0f
    var wave = 1; var blocked = 0
    var charge = 0; var item: String? = null // item gauge (fills on kills) + held item
    var elapsed = 0f; private var spawnT = 0f; private var pickT = 10f; private var fireT = 0f
    var rapid = 0f; var spread = 0f; var slow = 0f
    var firing = false; var muzzle = 0f // hold-to-fire + muzzle flash
    var shake = 0f; var lineFlash = 0f; var flash = 0f
    private var eventT = 12f; var fakeA = 0f; var fakeDigits = ""; private var lastWave = 1
    var nova = 0f
    var running = false; var over = false

    val shieldMax get() = SH_MAX
    val chargeFull get() = CHARGE_FULL
    private fun randomItem() = ITEM_POOL[Random.nextInt(ITEM_POOL.size)]
    private fun earnCharge(n: Int) { charge = min(CHARGE_FULL, charge + n); if (charge >= CHARGE_FULL && item == null) item = randomItem() }
    fun lineY() = h - 58f
    private fun rand(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
    private fun waveMult() = 1f + (wave - 1) * 0.12f

    fun setSize(nw: Float, nh: Float) {
        w = nw; h = nh; initStars()
        if (shipX == 0f) { shipX = w / 2; targetX = w / 2 }
        shipY = h - 30f
    }
    private fun initStars() {
        stars.clear(); repeat(48) { stars.add(Star(Random.nextFloat() * w, Random.nextFloat() * h, rand(0.3f, 1f))) }
    }

    fun pointer(x: Float) { targetX = x.coerceIn(shipW / 2, w - shipW / 2) }

    fun start() {
        bullets.clear(); reds.clear(); friends.clear(); parts.clear(); rings.clear(); pops.clear()
        shipX = w / 2; targetX = w / 2; shipY = h - 30f
        shield = SH_MAX; score = 0; combo = 0; comboT = 0f; wave = 1; blocked = 0; charge = 0; item = null
        elapsed = 0f; spawnT = 0f; pickT = 6f; fireT = 0f; rapid = 0f; spread = 0f; slow = 0f
        shake = 0f; lineFlash = 0f; flash = 0f; eventT = 6f; fakeA = 0f; fakeDigits = ""; lastWave = 1; nova = 0f
        firing = false; muzzle = 0f
        initStars(); running = true; over = false
        // Seed a few gentle enemies so the screen is alive immediately.
        for (i in 0 until 4) makeRed(y0 = -20f - i * 80f)
    }

    // Lots of movement from the start (dense), but SLOW early → alive, not hard.
    // Density (spawn rate) ramps fast; speed (fall) ramps gently over time.
    private fun ease() = if (elapsed < 5f) 1.2f - elapsed * 0.04f else 1f
    private fun spawnInterval() = max(300f, 720f - elapsed * 5.2f) * ease()
    private fun baseFall() = 30f + elapsed * 1.15f + (wave - 1) * 4f

    private fun makeRed(x: Float? = null, r0: Float? = null, hp0: Int? = null, type0: String? = null,
                        vx0: Float? = null, vy0: Float? = null, y0: Float? = null) {
        val armoredP = min(0.26f, elapsed * 0.0016f)
        val diverP = min(0.26f, 0.04f + elapsed * 0.0018f)
        val zigP = min(0.22f, elapsed * 0.0015f)
        val splitP = min(0.14f, (elapsed - 18f) * 0.0016f)
        var type = "normal"; var hp = 1; var r = 22f; var vy = baseFall() * rand(0.9f, 1.15f); var vx = rand(-30f, 30f); var amp = 0f
        val rr = Random.nextFloat()
        if (splitP > 0 && rr < splitP) { type = "split"; hp = 1; r = 26f; vy *= 0.85f }
        else if (rr < splitP + diverP) { type = "diver"; vy *= 2.0f; vx *= 0.4f; r = 18f }
        else if (rr < splitP + diverP + armoredP) { type = "armored"; hp = 2; r = 25f; vy *= 0.82f }
        else if (rr < splitP + diverP + armoredP + zigP) { type = "zig"; amp = rand(40f, 80f); vx = 0f; r = 21f; vy *= 0.92f }
        val ty = type0 ?: type; val tr = r0 ?: r; val thp = hp0 ?: hp; val tvx = vx0 ?: vx; val tvy = vy0 ?: vy
        val glyph = if (ty == "boss") "👹" else GLYPHS[Random.nextInt(GLYPHS.size)]
        reds.add(Enemy(x ?: rand(tr + 8, w - tr - 8), y0 ?: -tr, tr, tvy, tvx, if (ty == "zig") amp else 0f, thp, ty, Random.nextFloat() * 6.28f, 0f, glyph, thp))
    }

    private fun spawnBoss() {
        makeRed(x = w / 2, r0 = 42f, hp0 = 5 + wave / 2, type0 = "boss", vx0 = rand(-28f, 28f), vy0 = baseFall() * 0.5f, y0 = -52f)
    }
    private fun spawnFriend() {
        val r = 20f; val special = Random.nextFloat() < 0.22f
        val glyph = if (special) "🎁" else FRIEND_GLYPHS[Random.nextInt(FRIEND_GLYPHS.size)]
        friends.add(Friend(rand(r + 10, w - r - 10), -r, r, rand(70f, 95f), 0f, glyph, special))
    }

    /** Activate the held item (Mario-Kart style — manual, never automatic). */
    fun useItem() {
        val it = item ?: return
        when (it) {
            "rapid" -> rapid = 8f
            "spread" -> spread = 8f
            "slow" -> slow = 4.5f
            "repair" -> shield = min(SH_MAX, shield + 32f)
            "shield" -> { // weaker than before: clears only the near threats, not the whole screen
                flash = 0.5f; shake = 8f; lineFlash = 0.4f; nova = 1f
                val cutY = lineY() - 260f
                val kept = ArrayList<Enemy>()
                for (e in reds) {
                    if (e.y >= cutY) { score += (4 * waveMult()).toInt(); burst(e.x, e.y, CY, 6); ring(e.x, e.y, CY, e.r + 14f) } else kept.add(e)
                }
                reds.clear(); reds.addAll(kept)
            }
        }
        pop(shipX, shipY - 44f, itemLabel(it), CY)
        item = null; charge = 0
    }
    private fun ring(x: Float, y: Float, c: Long, mx: Float) { rings.add(Ring(x, y, 8f, mx, 0f, 0.45f, c)) }
    private fun burst(x: Float, y: Float, c: Long, n: Int) {
        repeat(n) { val a = Random.nextFloat() * 6.28f; val s = rand(40f, 190f)
            parts.add(Particle(x, y, kotlin.math.cos(a) * s, sin(a) * s, rand(0.4f, 0.8f), 0f, c)) }
    }
    private fun pop(x: Float, y: Float, text: String, c: Long) { pops.add(Pop(x, y, text, 0f, 0.8f, c)) }
    private fun comboColor() = if (combo >= 12) 0xFFFF7BA0uL.toLong() else if (combo >= 6) AMBER else CY

    private fun killRed(e: Enemy) {
        blocked++; combo++; comboT = 1.3f
        val gain = ((8 + combo * 2) * waveMult()).toInt(); score += gain
        ring(e.x, e.y, comboColor(), e.r + 26f); burst(e.x, e.y, 0xFFFF8B96uL.toLong(), 12); pop(e.x, e.y - e.r, "+$gain", comboColor())
        earnCharge(1) // reds fill the item gauge
        if (combo == 5 || combo == 10 || combo == 15 || combo == 25) { pop(w / 2, h * 0.36f, "COMBO ×$combo", AMBER); flash = max(flash, 0.35f) }
        if (e.type == "boss") { val b = (120 * waveMult()).toInt(); score += b; earnCharge(4)
            pop(e.x, e.y, "BOSS +$b", AMBER); ring(e.x, e.y, AMBER, e.r + 90f); burst(e.x, e.y, AMBER, 30); flash = max(flash, 0.55f); shake = 12f }
        if (e.type == "split") { var k = -1; while (k <= 1) { makeRed(x = e.x, r0 = 15f, hp0 = 1, type0 = "normal", vx0 = k * 70f, vy0 = e.vy * 1.05f, y0 = e.y); k += 2 } }
    }

    private fun triggerEvent() {
        val pool = if (elapsed < 20f) arrayOf("fake", "calm", "friend") else arrayOf("fake", "swarm", "formation", "calm", "friend", "friend")
        when (pool[Random.nextInt(pool.size)]) {
            "fake" -> { fakeA = 1f; fakeDigits = "+" + (10 + Random.nextInt(89)) + " " + threeDigits() + " " + threeDigits() }
            "swarm" -> repeat(5) { i -> makeRed(r0 = 16f, hp0 = 1, type0 = "normal", vy0 = baseFall() * 1.2f, vx0 = rand(-40f, 40f), y0 = -16f - i * 30f) }
            "formation" -> { val n = 4; for (i in 0 until n) makeRed(x = (i + 1) * w / (n + 1), vx0 = 0f, vy0 = baseFall() * 0.95f, type0 = "normal", hp0 = 1) }
            "calm" -> spawnT += 1.6f
            "friend" -> spawnFriend()
        }
        eventT = rand(8f, 14f)
    }
    private fun threeDigits() = (Random.nextInt(1000)).toString().padStart(3, '0')

    fun update(dt: Float) {
        elapsed += dt; wave = 1 + floor(elapsed / 13f).toInt()
        if (wave != lastWave) { lastWave = wave
            if (wave % 4 == 0) { flash = max(flash, 0.4f); pop(w / 2, h * 0.30f, "SURGE ×$wave", AMBER)
                for (i in 0 until 5) makeRed(x = (i + 1) * w / 6, vx0 = 0f, vy0 = baseFall() * 1.05f, type0 = "normal", hp0 = 1) }
            if (wave % 5 == 0) { pop(w / 2, h * 0.24f, "⚠ BOSS", SPAM); spawnBoss() } }
        val sd = if (slow > 0) 0.4f else 1f; if (slow > 0) slow -= dt; if (rapid > 0) rapid -= dt; if (spread > 0) spread -= dt; if (muzzle > 0) muzzle = max(0f, muzzle - dt)
        spawnT -= dt; pickT -= dt; eventT -= dt
        if (eventT <= 0) triggerEvent()
        if (fakeA > 0) fakeA -= dt * 0.42f
        if (nova > 0) nova -= dt / 1.15f
        if (comboT > 0) { comboT -= dt; if (comboT <= 0) combo = 0 }
        if (spawnT <= 0) { makeRed(); spawnT = spawnInterval() / 1000f; if (elapsed > 28f && Random.nextFloat() < 0.3f) makeRed() }
        if (pickT <= 0) { spawnFriend(); pickT = rand(3.5f, 6f) }

        shipX += (targetX - shipX) * min(1f, dt * 16f); shipX = shipX.coerceIn(shipW / 2, w - shipW / 2)
        fireT -= dt
        // Hold to fire (no more continuous auto-fire).
        if (firing && fireT <= 0) {
            fireT = if (rapid > 0) 0.06f else 0.13f; val nose = shipY - 18f; muzzle = 0.07f
            if (spread > 0) {
                bullets.add(Bullet(shipX, nose, -600f, 0f))
                bullets.add(Bullet(shipX, nose, -580f, -120f)); bullets.add(Bullet(shipX, nose, -580f, 120f))
                bullets.add(Bullet(shipX, nose, -540f, -260f)); bullets.add(Bullet(shipX, nose, -540f, 260f))
            } else bullets.add(Bullet(shipX, nose, -600f, 0f))
        }

        for (b in bullets) { b.y += b.vy * dt; b.x += b.vx * dt }
        bullets.removeAll { it.y < -12 || it.x < -12 || it.x > w + 12 }

        val ly = lineY()
        var i = reds.size - 1
        while (i >= 0) {
            val e = reds[i]; e.t += dt * 4; e.y += e.vy * dt * sd
            if (e.type == "zig") { e.bx += dt * 3 * sd; e.x = (e.x + sin(e.bx) * e.amp * dt * 3).coerceIn(e.r, w - e.r) }
            else { e.x += e.vx * dt * sd; if (e.x < e.r) { e.x = e.r; e.vx *= -1 }; if (e.x > w - e.r) { e.x = w - e.r; e.vx *= -1 } }
            var hitDead = false
            var j = bullets.size - 1
            while (j >= 0) { val b = bullets[j]
                val dx = b.x - e.x; val dy = b.y - e.y
                if (dx * dx + dy * dy < (e.r + 4) * (e.r + 4)) { bullets.removeAt(j); e.hp--; burst(b.x, b.y, SPARK, 3)
                    if (e.hp <= 0) { reds.removeAt(i); killRed(e); hitDead = true; break } }
                j--
            }
            if (!hitDead && e.y >= ly) { reds.removeAt(i)
                shield -= when (e.type) { "diver" -> 18f; "boss" -> 40f; else -> 14f }; combo = 0; shake = 12f; lineFlash = 1f; flash = max(flash, 0.3f); burst(e.x, ly, SPAM, 16)
                if (shield <= 0) { gameOver(); return } }
            i--
        }
        // Green benevolent calls: harmless. Catching a SPECIAL one (🎁) grants a
        // full item charge; normal ones do nothing and simply pass by.
        var p = friends.size - 1
        while (p >= 0) { val fr = friends[p]; fr.y += fr.vy * dt * sd; fr.t += dt * 4
            if (fr.special) {
                val dx = fr.x - shipX; val dy = fr.y - shipY
                if (dx * dx + dy * dy < (fr.r + shipW * 0.6f) * (fr.r + shipW * 0.6f)) { friends.removeAt(p)
                    earnCharge(CHARGE_FULL); ring(fr.x, fr.y, AMBER, fr.r + 30f); burst(fr.x, fr.y, AMBER, 20); pop(fr.x, fr.y, "BONUS 🎁", AMBER); score += 10; p--; continue
                }
            }
            if (fr.y > h + fr.r) friends.removeAt(p)
            p--
        }
        for (pt in parts) { pt.age += dt; pt.x += pt.vx * dt; pt.y += pt.vy * dt; pt.vy += 170 * dt }
        parts.removeAll { it.age >= it.life }
        for (r in rings) { r.age += dt; r.r += (r.max - r.r) * min(1f, dt * 6) }
        rings.removeAll { it.age >= it.life }
        for (po in pops) { po.age += dt; po.y -= 26 * dt }
        pops.removeAll { it.age >= it.life }
        for (s in stars) { s.y += (40f + elapsed * 3f + s.z * 90f) * dt * sd; if (s.y > h) { s.y = -2f; s.x = Random.nextFloat() * w } }
        if (shake > 0) shake = max(0f, shake - dt * 40); if (lineFlash > 0) lineFlash = max(0f, lineFlash - dt * 2.2f); if (flash > 0) flash = max(0f, flash - dt * 1.8f)
    }

    private fun gameOver() { over = true; shield = 0f; shake = 14f; flash = 0.5f }
}
