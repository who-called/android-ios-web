// Procedural sound design for the film: every effect is synthesized here (no
// third-party samples → no licensing), timed to the cues in index.html.
// Usage: node sfx.mjs            → sfx.wav (48 kHz stereo) + sfx.m4a (preview)
import { rmSync, writeFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const SR = 48000, DUR = 23;
const N = SR * DUR;
const L = new Float32Array(N), R = new Float32Array(N);
const send = new Float32Array(N); // reverb send (mono)

let seed = 11;
const rnd = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
const TAU = Math.PI * 2;

function put(i, v, pan, rev) {
  if (i < 0 || i >= N) return;
  L[i] += v * Math.cos((pan + 1) * Math.PI / 4);
  R[i] += v * Math.sin((pan + 1) * Math.PI / 4);
  send[i] += v * rev;
}
const env = (x, a, d) => (x < a ? x / a : Math.exp(-(x - a) / d)); // attack then exp decay

/** Oscillator voice. f may glide to f2; shape: sine | tri | square | saw. */
function tone(t, f, dur, { f2 = f, gain = .3, a = .005, d = dur / 4, shape = "sine", pan = 0, rev = .2, vib = 0 } = {}) {
  const s = Math.floor(t * SR), n = Math.floor(dur * SR);
  let ph = 0;
  for (let k = 0; k < n; k++) {
    const x = k / SR, p = k / n;
    const fr = f * Math.pow(f2 / f, p) * (1 + vib * Math.sin(TAU * 6 * x));
    ph += fr / SR;
    const q = ph % 1;
    let o = shape === "sine" ? Math.sin(TAU * q) : shape === "tri" ? 1 - 4 * Math.abs(q - .5) : shape === "square" ? (q < .5 ? .6 : -.6) : 2 * q - 1;
    const fade = Math.min(1, (n - k) / (SR * .01));
    put(s + k, o * gain * env(x, a, d) * fade, pan, rev);
  }
}

/** Filtered noise: lowpass cutoff glides lo→hi (Hz), optional highpass. */
function noise(t, dur, { gain = .3, a = .01, d = dur / 3, lo = 800, hi = lo, hp = 0, pan = 0, rev = .25, swell = false } = {}) {
  const s = Math.floor(t * SR), n = Math.floor(dur * SR);
  let lp = 0, hpS = 0, prev = 0;
  for (let k = 0; k < n; k++) {
    const x = k / SR, p = k / n;
    const fc = lo * Math.pow(hi / lo, p);
    const al = 1 - Math.exp(-TAU * fc / SR);
    lp += al * (rnd() * 2 - 1 - lp);
    let o = lp;
    if (hp) { const ah = Math.exp(-TAU * hp / SR); hpS = ah * (hpS + o - prev); prev = o; o = hpS; }
    const e = swell ? Math.pow(p, 2.2) * Math.min(1, (n - k) / (SR * .02)) : env(x, a, d) * Math.min(1, (n - k) / (SR * .01));
    put(s + k, o * gain * e * 2.2, pan, rev);
  }
}

// ---------- vocabulary ----------
const whoosh = (t, dur = .45, g = .22, pan = 0, up = true) => noise(t, dur, { gain: g, a: dur * .55, d: dur * .25, lo: up ? 300 : 3500, hi: up ? 4500 : 400, hp: 120, pan, rev: .35 });
const pop = (t, f = 900, g = .16, pan = 0) => { tone(t, f * 1.6, .09, { f2: f, gain: g, d: .03, shape: "sine", pan, rev: .25 }); noise(t, .02, { gain: g * .4, lo: 6000, d: .006, pan, rev: 0 }); };
const tick = (t, g = .08, pan = 0) => { noise(t, .018, { gain: g, lo: 7000, hp: 2500, d: .004, pan, rev: .05 }); tone(t, 2400 + rnd() * 600, .02, { gain: g * .35, d: .006, pan, rev: 0 }); };
const kick = (t, g = .55, f = 120) => { tone(t, f, .45, { f2: 38, gain: g, a: .002, d: .14, rev: .05 }); noise(t, .03, { gain: g * .35, lo: 3000, d: .01, rev: 0 }); };
const hit = (t, g = .5) => { kick(t, g); noise(t, .35, { gain: g * .45, lo: 5000, hi: 900, hp: 200, d: .08, rev: .6 }); tone(t, 196, .5, { gain: g * .18, d: .18, shape: "square", rev: .4 }); };
const pluck = (t, f, g = .18, pan = 0) => { tone(t, f, .9, { gain: g, d: .22, shape: "tri", pan, rev: .45 }); tone(t, f * 2, .5, { gain: g * .3, d: .1, pan, rev: .45 }); };
const chime = (t, f, g = .14, pan = 0) => { tone(t, f, 1.4, { gain: g, d: .45, pan, rev: .6 }); tone(t, f * 2.76, .8, { gain: g * .25, d: .2, pan, rev: .6 }); };
const note = (m) => 440 * Math.pow(2, (m - 69) / 12);

// ---------- cue sheet (seconds — mirror of the GSAP timeline) ----------
// S1 ringing call
whoosh(.05, .5, .16, 0, false);
[.35, 1.15, 1.95].forEach((t) => { [76, 79, 83, 79].forEach((m, i) => pluck(t + i * .1, note(m), .1, -.2 + i * .15)); });
[.75, 1.55].forEach((t) => { tone(t, 155, .42, { gain: .2, a: .01, d: 1, shape: "square", vib: .02, rev: 0 }); noise(t, .42, { gain: .06, lo: 300, d: 1, rev: 0 }); });
whoosh(.85, .5, .1);

// S2 flood: hero card slides to its slot, swarm pops, three slams
whoosh(2.4, .5, .18, -.4);
for (let i = 0; i < 8; i++) pop(2.65 + i * .11 + .08, 500 + rnd() * 700, .12, (rnd() - .5) * 1.4);
[3.0, 3.75, 4.5].forEach((t, i) => { noise(t, .3, { gain: .2, lo: 600, hi: 6000, hp: 300, swell: true, rev: .2 }); hit(t + .3, .42 + i * .06); });
noise(3.1, 2.2, { gain: .05, lo: 160, hi: 260, a: .8, d: 2, rev: .1 }); // tension rumble
for (let i = 0; i < 9; i++) tick(4.8 + i * .02, .05, (rnd() - .5));

// S3 shield impact
noise(5.25, .55, { gain: .3, lo: 400, hi: 9000, hp: 200, swell: true, rev: .3 });
tone(5.25, 220, .55, { f2: 880, gain: .08, a: .5, d: 1, shape: "saw", rev: .3 });
kick(5.8, .85, 140);
tone(5.8, 55, 1.6, { f2: 32, gain: .4, a: .003, d: .6, rev: .1 });
noise(5.8, 1.6, { gain: .35, lo: 9000, hi: 1200, hp: 300, d: .35, rev: .9 });
[0, 4, 7, 12].forEach((s, i) => chime(5.85 + i * .015, note(72 + s), .07, (i - 1.5) * .4));
for (let i = 0; i < 9; i++) { kick(5.84 + i * .035, .08, 260); tick(5.84 + i * .035, .06, (rnd() - .5) * 1.6); }
whoosh(6.2, .8, .22, .25);
whoosh(6.7, .55, .1, -.3);
pop(7.35, 1300, .08);

// iris + phone entrance
whoosh(7.85, .6, .25, 0);
whoosh(8.25, .5, .18, 0, false);
whoosh(8.6, .7, .16, .6);

// S4 verify: typing, gauge sweep, verdict
for (let i = 0; i < 14; i++) tick(9.45 + i * (.8 / 14), .09, .25);
whoosh(10.2, .35, .1, .25);
tone(10.4, 330, 1.15, { f2: 990, gain: .07, a: .05, d: 2, shape: "tri", pan: .25, rev: .3 });
for (let i = 0; i < 12; i++) tick(10.4 + Math.pow(i / 12, 1.6) * 1.1, .05, .25);
[note(76), note(72)].forEach((f, i) => tone(11.0 + i * .12, f, .5, { gain: .12, d: .15, shape: "square", pan: .25, rev: .3 }));
pop(11.15, 1000, .1, .25); pop(11.25, 1200, .1, .25);

// S5 report: three taps on the real Report screen (Unwanted → category → Send), success, community sparkle
whoosh(11.9, .4, .14, .25);
whoosh(12.4, .45, .05, .3);
const tap = (t, f) => { tick(t, .16, .25); tone(t, 180, .12, { gain: .2, d: .04, pan: .25, rev: .1 }); pop(t + .02, f, .09, .25); };
tap(12.95, 760); tap(13.3, 980); tap(13.65, 620);
noise(13.68, .3, { gain: .06, lo: 1500, hi: 500, d: .12, pan: .25 });
[note(79), note(84)].forEach((f, i) => chime(13.82 + i * .13, f, .13, .25));
for (let i = 0; i < 22; i++) { const t = 13.85 + Math.pow(rnd(), .8) * 1.05; tone(t, note(84 + [0, 2, 4, 7, 9, 12][Math.floor(rnd() * 6)]), .25, { gain: .035, d: .07, pan: (rnd() - .5) * 1.8, rev: .7 }); }
pop(13.85, 1500, .1, .6);

// S6 SMS shield: toggle on, SMS cards arrive, scan flags the two scams, the real one passes
whoosh(14.85, .4, .14, .25);
tick(15.35, .12, .25); tone(15.37, 880, .14, { f2: 1320, gain: .08, d: .05, pan: .25, rev: .3 });
for (let i = 0; i < 3; i++) pop(15.62 + i * .15, 820, .07, .25);
tone(15.95, 420, 1.1, { f2: 260, gain: .06, a: .15, d: 3, shape: "saw", pan: .25, rev: .3 });
noise(15.95, 1.1, { gain: .06, lo: 2000, hi: 900, a: .2, d: 3, pan: .25 });
[16.28, 16.41].forEach((t) => tone(t, 700, .14, { f2: 300, gain: .12, d: .05, shape: "square", pan: .25, rev: .3 }));
pluck(16.53, note(84), .12, .25);

// S7 values: rising plucks on each check
whoosh(17.5, .6, .2, .4, false);
[0, 1, 2].forEach((i) => { whoosh(17.9 + i * .38, .3, .07); pluck(18.07 + i * .38, note([72, 76, 79][i]), .2); pop(18.07 + i * .38, 1100, .07); });
whoosh(19.0, .4, .06);
whoosh(19.95, .5, .16, 0, false);

// S8 outro: shimmer draw, logo pop, typing, resolve chord
noise(20.4, .75, { gain: .08, lo: 3000, hi: 9000, hp: 2000, swell: true, rev: .6 });
for (let i = 0; i < 10; i++) tone(20.4 + i * .07, note(84 + [0, 4, 7, 11, 12][i % 5]), .3, { gain: .03, d: .08, pan: (i % 2 ? .4 : -.4), rev: .7 });
kick(21.05, .45, 110);
[60, 64, 67, 71, 76].forEach((m, i) => tone(21.05, note(m), 2, { gain: .07 - i * .008, a: .02, d: .9, shape: i === 0 ? "tri" : "sine", pan: (i - 2) * .3, rev: .6 }));
tone(21.05, note(36), 1.9, { gain: .25, a: .01, d: .8, rev: .2 });
chime(21.15, note(88), .1);
for (let i = 0; i < 14; i++) tick(21.95 + i * (.55 / 14), .07);
pop(22.3, 900, .07, -.3); pop(22.4, 1100, .07, .3);

// ---------- reverb (4 combs + 2 allpass, Schroeder) ----------
function reverb(inp) {
  const out = new Float32Array(N);
  for (const [ms, fb] of [[29.7, .80], [37.1, .79], [41.1, .77], [43.7, .76]]) {
    const D = Math.floor(ms * SR / 1000), buf = new Float32Array(D); let i = 0, lp = 0;
    for (let k = 0; k < N; k++) { const y = buf[i]; lp = y * .6 + lp * .4; buf[i] = inp[k] + lp * fb; i = (i + 1) % D; out[k] += y * .25; }
  }
  for (const ms of [5, 1.7]) {
    const D = Math.floor(ms * SR / 1000), buf = new Float32Array(D); let i = 0;
    for (let k = 0; k < N; k++) { const b = buf[i], y = -.7 * out[k] + b; buf[i] = out[k] + .7 * y; i = (i + 1) % D; out[k] = y; }
  }
  return out;
}
const wet = reverb(send);
const pre = Math.floor(.011 * SR);

// mix → soft clip → normalize → 16-bit WAV
let peak = 0;
for (let k = 0; k < N; k++) {
  const w = wet[k], w2 = k >= pre ? wet[k - pre] : 0;
  L[k] = Math.tanh((L[k] + w * .5) * 1.1); R[k] = Math.tanh((R[k] + w2 * .5) * 1.1);
  peak = Math.max(peak, Math.abs(L[k]), Math.abs(R[k]));
}
const norm = .89 / peak; // ≈ -1 dBFS
const fadeOut = SR * .6;
const pcm = Buffer.alloc(44 + N * 4);
pcm.write("RIFF", 0); pcm.writeUInt32LE(36 + N * 4, 4); pcm.write("WAVEfmt ", 8);
pcm.writeUInt32LE(16, 16); pcm.writeUInt16LE(1, 20); pcm.writeUInt16LE(2, 22); pcm.writeUInt32LE(SR, 24);
pcm.writeUInt32LE(SR * 4, 28); pcm.writeUInt16LE(4, 32); pcm.writeUInt16LE(16, 34); pcm.write("data", 36); pcm.writeUInt32LE(N * 4, 40);
for (let k = 0; k < N; k++) {
  const f = Math.min(1, (N - k) / fadeOut);
  pcm.writeInt16LE(Math.round(Math.max(-1, Math.min(1, L[k] * norm * f)) * 32767), 44 + k * 4);
  pcm.writeInt16LE(Math.round(Math.max(-1, Math.min(1, R[k] * norm * f)) * 32767), 46 + k * 4);
}
writeFileSync(`${here}/sfx.raw.wav`, pcm);
// glue: compress the impact peaks so the small UI sounds read, then -16 LUFS / -1.5 dBTP
const master = "acompressor=threshold=-26dB:ratio=3.5:attack=4:release=140:makeup=2,loudnorm=I=-16:TP=-1.5:LRA=9,aresample=48000";
spawnSync("ffmpeg", ["-y", "-loglevel", "error", "-i", `${here}/sfx.raw.wav`, "-af", master, "-ar", "48000", "-c:a", "pcm_s16le", `${here}/sfx.wav`], { stdio: "inherit" });
spawnSync("ffmpeg", ["-y", "-loglevel", "error", "-i", `${here}/sfx.wav`, "-c:a", "aac", "-b:a", "160k", `${here}/sfx.m4a`], { stdio: "inherit" });
rmSync(`${here}/sfx.raw.wav`);
console.log("→ sfx.wav, sfx.m4a");
