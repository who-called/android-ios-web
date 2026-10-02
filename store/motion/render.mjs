// Renders index.html frame by frame into an MP4 (deterministic, not screen capture).
// Usage: node render.mjs [--lang fr] [--format 16x9|9x16|ios] [--fps 30] [--no-audio] [--out out/who-called-fr-16x9.mp4]
// `ios` = App Store app preview: rendered at 1080×2340, delivered at 886×1920, 30 fps, H.264 High + AAC 256k.
// Needs Google Chrome installed and ffmpeg on PATH.
import { chromium } from "playwright-core";
import { spawn } from "node:child_process";
import { existsSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const arg = (name, def) => { const i = process.argv.indexOf(`--${name}`); return i > -1 ? process.argv[i + 1] : def; };
const lang = arg("lang", "fr");
const format = arg("format", "16x9");
const fps = Number(arg("fps", 30));
const out = resolve(arg("out", `${here}/out/who-called-${lang}-${format}.mp4`));
const [W, H] = format === "ios" ? [1080, 2340] : format === "9x16" ? [1080, 1920] : [1920, 1080];
const audio = !process.argv.includes("--no-audio");
if (format === "ios" && fps > 30) throw new Error("App Store previews are capped at 30 fps");

mkdirSync(dirname(out), { recursive: true });
if (audio && !existsSync(`${here}/sfx.wav`)) await import("./sfx.mjs");
const browser = await chromium.launch({ channel: "chrome" });
const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
const url = pathToFileURL(`${here}/index.html`);
url.search = new URLSearchParams({ render: "1", lang, format }).toString();
await page.goto(url.href, { waitUntil: "networkidle" });
await page.evaluate(() => window.__ready);
const duration = await page.evaluate(() => window.__duration);
const frames = Math.round(duration * fps);

const ff = spawn("ffmpeg", ["-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", String(fps), "-i", "-",
  ...(audio ? ["-i", `${here}/sfx.wav`, "-map", "0:v", "-map", "1:a", "-c:a", "aac", "-b:a", "256k", "-ar", "48000", "-ac", "2"] : []),
  ...(format === "ios" ? ["-vf", "scale=886:1920:flags=lanczos"] : []),
  "-c:v", "libx264", "-preset", "slow", "-crf", "16", "-profile:v", "high", "-level", "4.0", "-pix_fmt", "yuv420p",
  "-r", String(fps), "-t", String(duration), "-movflags", "+faststart", out], { stdio: ["pipe", "inherit", "inherit"] });

for (let i = 0; i < frames; i++) {
  await page.evaluate((t) => window.__seek(t), i / fps);
  const buf = await page.screenshot({ type: "png" });
  if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once("drain", r));
  if (i % fps === 0) process.stdout.write(`\r${lang} ${format}  ${(i / fps).toFixed(0)}s / ${duration}s`);
}
ff.stdin.end();
await new Promise((r, j) => ff.on("close", (c) => (c === 0 ? r() : j(new Error(`ffmpeg exited ${c}`)))));
await browser.close();
console.log(`\n→ ${out}`);
