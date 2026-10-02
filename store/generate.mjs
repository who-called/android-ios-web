#!/usr/bin/env node
/**
 * Générateur de visuels stores (App Store / Play Store) pour Who Called.
 *
 * Les écrans de l'app viennent de store/screens.mjs (partagés avec le film store/motion).
 * Produit des SVG maîtres dans store/svg/ puis les rend en PNG dans store/png/
 * via rsvg-convert (brew install librsvg).
 *
 *   node store/generate.mjs
 *
 * Deux directions :
 *   A — « Bleu confiance » : fond clair, gros titre, mockup téléphone fidèle aux vues.
 *   B — « Éditorial »      : bleu nuit, typographie massive, accents amber (à la Saracroche).
 *
 * Formats :
 *   Android  : 1080×1920 (9:16, screenshots téléphone) + feature graphic 1024×500.
 *   iOS 6.9" : 1320×2868 (iPhone 15/16 Pro Max).
 */

import { mkdirSync, writeFileSync } from "node:fs";
import { execSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  C, txt, icon, shieldLogo, SCR_W, SCR_H,
  screenHome, screenCallBlocked, screenLookup, screenReport, screenSms,
} from "./screens.mjs";

const ROOT = dirname(fileURLToPath(import.meta.url));
const SVG_DIR = join(ROOT, "svg");
const PNG_DIR = join(ROOT, "png");
mkdirSync(SVG_DIR, { recursive: true });
mkdirSync(join(PNG_DIR, "android"), { recursive: true });
mkdirSync(join(PNG_DIR, "ios"), { recursive: true });

// ---------------------------------------------------------------------------
// Mockup téléphone
// ---------------------------------------------------------------------------

/**
 * Téléphone avec un écran recréé. x,y = coin haut-gauche, w = largeur totale.
 * Retourne { svg, h }.
 */
function phone(screenSvg, x, y, w, { clipBottom = null, idSuffix = "" } = {}) {
  const bezel = w * 0.032;
  const sw = w - bezel * 2;
  const sh = sw * (SCR_H / SCR_W);
  const h = sh + bezel * 2;
  const rOut = w * 0.148;
  const rIn = rOut - bezel;
  const clipId = `scr${idSuffix}${Math.round(x)}x${Math.round(y)}`;
  const svg = `
  <g>
    <rect x="${x - 3}" y="${y - 3}" width="${w + 6}" height="${h + 6}" rx="${rOut + 3}" fill="${C.ink}" opacity="0.14"/>
    <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rOut}" fill="#0B1220"/>
    <clipPath id="${clipId}"><rect x="${x + bezel}" y="${y + bezel}" width="${sw}" height="${sh}" rx="${rIn}"/></clipPath>
    <g clip-path="url(#${clipId})">
      <svg x="${x + bezel}" y="${y + bezel}" width="${sw}" height="${sh}" viewBox="0 0 ${SCR_W} ${SCR_H}" preserveAspectRatio="xMidYMin slice">${screenSvg}</svg>
    </g>
    <circle cx="${x + w / 2}" cy="${y + bezel + sw * 0.045}" r="${sw * 0.017}" fill="#0B1220"/>
  </g>`;
  return { svg, h };
}

// ---------------------------------------------------------------------------
// Compositions store
// ---------------------------------------------------------------------------

const DEFS = `<defs>
  <linearGradient id="hdr" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="${C.blue}"/><stop offset="1" stop-color="${C.night}"/>
  </linearGradient>
  <linearGradient id="call" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#1E3A6E"/><stop offset="1" stop-color="${C.nightDark}"/>
  </linearGradient>
  <linearGradient id="gauge" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="${C.amber}"/><stop offset="1" stop-color="${C.coral}"/>
  </linearGradient>
  <linearGradient id="bgLight" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#EAF2FF"/><stop offset="1" stop-color="#C7DBFB"/>
  </linearGradient>
  <linearGradient id="bgNight" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="${C.navy}"/><stop offset="1" stop-color="#131F38"/>
  </linearGradient>
  <linearGradient id="bgBlue" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="${C.blue}"/><stop offset="1" stop-color="${C.blueDark}"/>
  </linearGradient>
</defs>`;

/** Halo concentrique derrière le téléphone (comme la concurrence). */
function halo(cx, cy, r, color = C.blueBright) {
  return `
    <circle cx="${cx}" cy="${cy}" r="${r}" fill="${color}" opacity="0.16"/>
    <circle cx="${cx}" cy="${cy}" r="${r * 0.78}" fill="${color}" opacity="0.18"/>
    <circle cx="${cx}" cy="${cy}" r="${r * 0.56}" fill="${color}" opacity="0.20"/>`;
}

/**
 * Direction A — fond bleu clair, titre haut, téléphone plein pied (crop bas).
 * W×H libres (1080×1920 ou 1080×2347 pour iOS).
 */
function slideA({ W, H, title, sub, screen, badge = null }) {
  const lines = Array.isArray(title) ? title : [title];
  const tSize = 74;
  let s = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${DEFS}`;
  s += `<rect width="${W}" height="${H}" fill="url(#bgLight)"/>`;

  const extra = H - 1920; // hauteur en plus sur iOS → aère le haut
  const phW = 700 + Math.min(extra * 0.18, 90);
  const phX = (W - phW) / 2;
  const phY = 470 + extra * 0.35;

  s += halo(W / 2, phY + phW * 0.9, W * 0.62);

  let ty = 150 + extra * 0.12;
  lines.forEach((l) => {
    s += txt(W / 2, ty, l, { size: tSize, weight: 800, fill: C.ink, anchor: "middle" });
    ty += tSize * 1.14;
  });
  if (sub) {
    s += txt(W / 2, ty + 12, sub, { size: 34, weight: 500, fill: "#3D5175", anchor: "middle" });
    ty += 46;
  }

  const p = phone(screen, phX, phY, phW);
  s += p.svg;

  if (badge) {
    // Pastille flottante à cheval sur le bord droit du téléphone (sous le header)
    const bx = phX + phW - 24;
    const by = phY + 330;
    s += `<g>
      <circle cx="${bx}" cy="${by}" r="66" fill="${C.white}"/>
      <circle cx="${bx}" cy="${by}" r="66" fill="none" stroke="${C.border}"/>
      ${badge(bx, by)}
    </g>`;
  }
  s += `</svg>`;
  return s;
}

/** Slide marque (direction A, fond nuit) : logo + valeurs. */
function slideABrand({ W, H }) {
  const extra = H - 1920;
  let s = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${DEFS}`;
  s += `<rect width="${W}" height="${H}" fill="url(#bgNight)"/>`;
  s += `<circle cx="${W - 60}" cy="140" r="300" fill="none" stroke="${C.white}" stroke-opacity="0.05" stroke-width="46"/>`;
  s += `<circle cx="${W - 60}" cy="140" r="200" fill="none" stroke="${C.white}" stroke-opacity="0.04" stroke-width="30"/>`;

  let y = 240 + extra * 0.2;
  s += halo(W / 2, y + 120, 330, C.blueBright);
  s += shieldLogo(W / 2 - 130, y - 10, 260, { bg: C.navy, fg: C.white });
  y += 340;
  s += txt(W / 2, y, "Who Called", { size: 92, weight: 800, fill: C.white, anchor: "middle" });
  y += 66;
  s += txt(W / 2, y, "GRATUIT · ANONYME · OPEN SOURCE", { size: 30, weight: 700, fill: C.amber, anchor: "middle", spacing: 3 });
  y += 130 + extra * 0.1;

  const items = [
    ["euro", "Gratuit, sans pub", "Aucun abonnement, aucun tracking tiers."],
    ["eyeOff", "Anonyme", "Pas de compte, pas de données personnelles."],
    ["db", "Liste officielle ARCEP", "+ les signalements de la communauté."],
    ["code", "Open source", "Le code est public et vérifiable."],
  ];
  const cw = W - 220;
  items.forEach(([ic, t, d]) => {
    s += `<rect x="110" y="${y}" width="${cw}" height="130" rx="26" fill="${C.white}" fill-opacity="0.06" stroke="${C.white}" stroke-opacity="0.14"/>`;
    s += `<circle cx="188" cy="${y + 65}" r="38" fill="${C.blue}" fill-opacity="0.28"/>`;
    s += icon(ic, 166, y + 43, 44, C.blueBright);
    s += txt(258, y + 58, t, { size: 34, weight: 700, fill: C.white });
    s += txt(258, y + 98, d, { size: 26, fill: C.white, opacity: 0.72 });
    y += 154 + extra * 0.02;
  });

  s += txt(W / 2, H - 70, "who-called.com", { size: 30, weight: 600, fill: C.white, opacity: 0.55, anchor: "middle" });
  s += `</svg>`;
  return s;
}

/**
 * Direction B — éditorial bleu nuit : gros titre gauche, accents amber,
 * téléphone coupé ou logo, listes à tirets (inspiration Saracroche).
 */
function slideB({ W, H, kicker, lines, bullets = [], screen = null, footer = "who-called.com" }) {
  const extra = H - 1920;
  let s = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${DEFS}`;
  s += `<rect width="${W}" height="${H}" fill="url(#bgNight)"/>`;

  let y = 170 + extra * 0.1;
  if (kicker) {
    s += `<rect x="96" y="${y - 44}" width="${kicker.length * 21 + 58}" height="60" rx="30" fill="${C.amber}"/>`;
    s += txt(124, y - 3, kicker, { size: 28, weight: 800, fill: "#231A02", spacing: 1.5 });
    y += 90;
  }
  const tSize = 96;
  lines.forEach((l) => {
    s += txt(90, y, l, { size: tSize, weight: 800, fill: C.white, anchor: "start" });
    y += tSize * 1.1;
  });
  y += 30;

  if (screen) {
    const phW = 640 + Math.min(extra * 0.15, 70);
    const p = phone(screen, (W - phW) / 2, y + 40, phW);
    s += halo(W / 2, y + 40 + phW * 0.85, W * 0.55, C.blueBright);
    s += p.svg;
    // Bandeau bas avec bullets par-dessus le téléphone coupé
    if (bullets.length) {
      const bh = 120 + bullets.length * 74 + extra * 0.06;
      s += `<rect x="0" y="${H - bh}" width="${W}" height="${bh}" fill="#0C1526"/>`;
      let by = H - bh + 96;
      bullets.forEach(([t, strong]) => {
        s += `<rect x="96" y="${by - 26}" width="26" height="10" rx="5" fill="${C.amber}"/>`;
        s += txt(146, by, t, { size: 38, weight: strong ? 800 : 600, fill: C.white });
        by += 74;
      });
    }
  } else {
    // Pas d'écran : gros logo + bullets
    s += halo(W / 2, y + 300, 340, C.blueBright);
    s += shieldLogo(W / 2 - 150, y + 90, 300, { bg: C.navy, fg: C.white });
    let by = y + 560 + extra * 0.25;
    bullets.forEach(([t, strong]) => {
      s += `<rect x="150" y="${by - 26}" width="30" height="12" rx="6" fill="${C.amber}"/>`;
      s += txt(210, by, t, { size: 44, weight: strong ? 800 : 600, fill: C.white });
      by += 96;
    });
  }

  // Pas de footer quand un bandeau de bullets recouvre déjà le bas.
  if (footer && !(screen && bullets.length)) {
    s += txt(W / 2, H - 56, footer, { size: 28, weight: 600, fill: C.white, opacity: 0.5, anchor: "middle" });
  }
  s += `</svg>`;
  return s;
}

// ---------------------------------------------------------------------------
// Feature graphics Play Store 1024×500
// ---------------------------------------------------------------------------

function featureGraphicA() {
  const W = 1024, H = 500;
  let s = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${DEFS}`;
  s += `<rect width="${W}" height="${H}" fill="url(#bgBlue)"/>`;
  s += `<circle cx="930" cy="80" r="240" fill="none" stroke="${C.white}" stroke-opacity="0.08" stroke-width="36"/>`;
  s += `<circle cx="930" cy="80" r="160" fill="none" stroke="${C.white}" stroke-opacity="0.06" stroke-width="24"/>`;

  s += shieldLogo(64, 56, 96, { bg: C.blue, fg: C.white });
  s += txt(180, 100, "Who Called", { size: 54, weight: 800, fill: C.white });
  s += txt(182, 136, "GRATUIT · ANONYME · OPEN SOURCE", { size: 19, weight: 700, fill: "#FDE68A", spacing: 2 });

  s += txt(64, 250, "Bloquez les appels", { size: 62, weight: 800, fill: C.white });
  s += txt(64, 322, "indésirables.", { size: 62, weight: 800, fill: C.white });
  s += txt(64, 386, "Liste officielle ARCEP + communauté.", { size: 25, fill: C.white, opacity: 0.85 });

  // Mini carte d'appel bloqué à droite
  s += `<g transform="translate(660,120) rotate(4)">
    <rect width="330" height="150" rx="24" fill="${C.coral}"/>
    <circle cx="52" cy="56" r="28" fill="${C.white}" fill-opacity="0.2"/>
    ${icon("block", 38, 42, 28, C.white)}
    ${txt(96, 50, "Démarchage", { size: 25, weight: 800, fill: C.white })}
    ${txt(96, 80, "256 signalements", { size: 17, fill: C.white, opacity: 0.9 })}
    ${txt(38, 126, "Bloqué avant de sonner", { size: 18, weight: 700, fill: C.white })}
  </g>`;
  s += `<g transform="translate(688,300) rotate(-3)">
    <rect width="316" height="110" rx="22" fill="${C.white}"/>
    ${icon("check", 30, 34, 26, C.emerald)}
    <circle cx="43" cy="47" r="19" fill="none" stroke="${C.emerald}" stroke-width="2.6"/>
    ${txt(76, 44, "Bloqueur actif", { size: 22, weight: 800, fill: C.ink })}
    ${txt(76, 76, "16 643 100 numéros couverts", { size: 16, fill: C.muted })}
  </g>`;
  s += txt(64, 448, "who-called.com", { size: 21, weight: 600, fill: C.white, opacity: 0.6 });
  s += `</svg>`;
  return s;
}

function featureGraphicB() {
  const W = 1024, H = 500;
  let s = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${DEFS}`;
  s += `<rect width="${W}" height="${H}" fill="url(#bgNight)"/>`;
  s += halo(830, 250, 260, C.blueBright);
  s += shieldLogo(730, 96, 220, { bg: C.navy, fg: C.white });
  s += txt(830, 400, "Who Called", { size: 40, weight: 800, fill: C.white, anchor: "middle" });

  s += txt(64, 150, "Stop au démarchage", { size: 64, weight: 800, fill: C.white });
  s += txt(64, 224, "et aux arnaques", { size: 64, weight: 800, fill: C.white });
  s += txt(64, 298, "téléphoniques.", { size: 64, weight: 800, fill: C.amber });

  const bullets = ["Gratuit et open source", "Anonyme — aucun compte", "Liste ARCEP + communauté"];
  let y = 366;
  bullets.forEach((b) => {
    s += `<rect x="66" y="${y - 15}" width="20" height="8" rx="4" fill="${C.amber}"/>`;
    s += txt(102, y, b, { size: 26, weight: 600, fill: C.white });
    y += 44;
  });
  s += `</svg>`;
  return s;
}

// ---------------------------------------------------------------------------
// Assemblage + rendu
// ---------------------------------------------------------------------------

const SIZES = {
  android: { W: 1080, H: 1920, outW: 1080, outH: 1920 }, // 9:16
  ios: { W: 1080, H: 2347, outW: 1320, outH: 2868 },     // iPhone 6.9" (15/16 Pro Max)
};

function badge27(bx, by) {
  return `${txt(bx, by - 4, "27", { size: 40, weight: 800, fill: C.coral, anchor: "middle" })}
    ${txt(bx, by + 28, "bloqués", { size: 17, weight: 600, fill: C.muted, anchor: "middle" })}`;
}
function badgeAnon(bx, by) {
  return `${icon("lock", bx - 17, by - 30, 34, C.blue)}
    ${txt(bx, by + 32, "anonyme", { size: 17, weight: 600, fill: C.muted, anchor: "middle" })}`;
}

function buildSlides({ W, H }) {
  return [
    ["01-blocage", slideA({
      W, H,
      title: ["Bloquez les appels", "indésirables"],
      sub: "Avant même que ça sonne",
      screen: screenCallBlocked(),
    })],
    ["02-protection", slideA({
      W, H,
      title: ["Protégé en", "permanence"],
      sub: "16 millions de numéros couverts",
      screen: screenHome(),
      badge: badge27,
    })],
    ["03-verifier", slideA({
      W, H,
      title: ["Vérifiez n’importe", "quel numéro"],
      sub: "Indice de spam ARCEP + communauté",
      screen: screenLookup(),
    })],
    ["04-signaler", slideA({
      W, H,
      title: ["Signalez", "en 5 secondes"],
      sub: "Et protégez toute la communauté",
      screen: screenReport(),
      badge: badgeAnon,
    })],
    ["05-sms", slideA({
      W, H,
      title: ["Bouclier SMS", "inclus"],
      sub: "Notifications de SMS indésirables masquées",
      screen: screenSms(),
    })],
    ["06-marque", slideABrand({ W, H })],

    ["B1-hero", slideB({
      W, H,
      kicker: "ANTI-SPAM",
      lines: ["Bloquez", "des millions", "de numéros", "indésirables."],
      bullets: [
        ["Gratuit et open source", true],
        ["Anonyme — aucun compte", false],
        ["Liste officielle ARCEP", false],
        ["Signalements communautaires", false],
      ],
    })],
    ["B2-eviter", slideB({
      W, H,
      kicker: "PROTECTION",
      lines: ["Pour éviter", "les appels."],
      bullets: [
        ["Démarchage, arnaques, spam", true],
        ["Bloqués avant de sonner", false],
      ],
      screen: screenHome(),
    })],
    ["B3-signaler", slideB({
      W, H,
      kicker: "COMMUNAUTÉ",
      lines: ["Signalez", "les numéros."],
      bullets: [
        ["Anonyme et en 5 secondes", true],
        ["Chaque signalement protège tout le monde", false],
      ],
      screen: screenReport(),
    })],
  ];
}

let rendered = 0;
for (const [platform, { W, H, outW, outH }] of Object.entries(SIZES)) {
  for (const [name, svg] of buildSlides({ W, H })) {
    const svgPath = join(SVG_DIR, `${platform}-${name}.svg`);
    writeFileSync(svgPath, svg);
    const pngPath = join(PNG_DIR, platform, `${name}.png`);
    execSync(`rsvg-convert -w ${outW} -h ${outH} "${svgPath}" -o "${pngPath}"`);
    rendered++;
  }
}

for (const [name, svg] of [
  ["feature-graphic-A", featureGraphicA()],
  ["feature-graphic-B", featureGraphicB()],
]) {
  const svgPath = join(SVG_DIR, `${name}.svg`);
  writeFileSync(svgPath, svg);
  execSync(`rsvg-convert -w 1024 -h 500 "${svgPath}" -o "${join(PNG_DIR, "android", `${name}.png`)}"`);
  rendered++;
}

console.log(`OK — ${rendered} PNG rendus dans store/png/ (SVG maîtres dans store/svg/)`);
