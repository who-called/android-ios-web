#!/usr/bin/env node
/**
 * Générateur de visuels stores (App Store / Play Store) pour Who Called.
 *
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

const ROOT = dirname(fileURLToPath(import.meta.url));
const SVG_DIR = join(ROOT, "svg");
const PNG_DIR = join(ROOT, "png");
mkdirSync(SVG_DIR, { recursive: true });
mkdirSync(join(PNG_DIR, "android"), { recursive: true });
mkdirSync(join(PNG_DIR, "ios"), { recursive: true });

// ---------------------------------------------------------------------------
// Palette (source : android Theme.kt / ios App.swift / web tailwind)
// ---------------------------------------------------------------------------
const C = {
  blue: "#2563EB",
  blueDark: "#1D4ED8",
  blueBright: "#3B82F6",
  night: "#15294D",
  nightDark: "#101F3C",
  navy: "#1B2A4A",
  amber: "#F59E0B",
  emerald: "#10B981",
  coral: "#EF4444",
  ink: "#0F172A",
  white: "#FFFFFF",
  border: "#E2E8F5",
  tint: "#EFF5FF",
  muted: "#64748B",
};

const FONT = `'Helvetica Neue', 'Segoe UI', Roboto, Arial, sans-serif`;

const esc = (s) =>
  s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");

// ---------------------------------------------------------------------------
// Petites briques SVG
// ---------------------------------------------------------------------------

/** Texte utilitaire. */
function txt(x, y, s, { size = 16, weight = 400, fill = C.ink, anchor = "start", spacing = 0, opacity = 1 } = {}) {
  return `<text x="${x}" y="${y}" font-family="${FONT}" font-size="${size}" font-weight="${weight}" fill="${fill}" text-anchor="${anchor}"${spacing ? ` letter-spacing="${spacing}"` : ""}${opacity !== 1 ? ` fill-opacity="${opacity}"` : ""}>${esc(s)}</text>`;
}

/** Bouclier + coche Who Called (logo). Dessiné dans une boîte size×size. */
function shieldLogo(x, y, size, { bg = C.blue, fg = C.white, rim = true } = {}) {
  const s = size / 48;
  return `<g transform="translate(${x},${y}) scale(${s})">
    <path d="M24 3l17 6.4v13c0 11.6-7.4 19-17 23.2C14.4 41.4 7 34 7 22.4v-13z" fill="${fg}"/>
    ${rim ? `<path d="M24 7.6l12.8 4.8v10c0 8.8-5.6 14.5-12.8 17.8-7.2-3.3-12.8-9-12.8-17.8v-10z" fill="${bg}"/>` : ""}
    <path d="M21.4 29.4l-5.4-5.4 2.7-2.7 2.7 2.7 9.6-9.6 2.7 2.7z" fill="${fg}"/>
  </g>`;
}

/** Icônes ligne (24×24 viewbox, stroke). */
const ICONS = {
  check: `<path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>`,
  block: `<circle cx="12" cy="12" r="8.4" fill="none" stroke-width="2.4"/><path d="M6.4 6.4l11.2 11.2" stroke-width="2.4" stroke-linecap="round"/>`,
  warn: `<path d="M12 4L21 20H3z" fill="none" stroke-width="2.2" stroke-linejoin="round"/><path d="M12 10.5v4" stroke-width="2.2" stroke-linecap="round"/><circle cx="12" cy="17" r="1.2" stroke="none"/>`,
  msg: `<path d="M4 5h16v11H9l-5 4z" fill="none" stroke-width="2.2" stroke-linejoin="round"/><path d="M8 9h8M8 12.4h5" stroke-width="2" stroke-linecap="round"/>`,
  phone: `<path d="M7.5 4c.8 0 1.6 2 1.9 3.1.2.8-.6 1.5-1.3 2.1 1 2.3 2.4 3.7 4.7 4.7.6-.7 1.3-1.5 2.1-1.3C16 12.9 18 13.7 18 14.5c0 1.7-1.6 3-3.2 2.7C10 16.4 5.6 12 4.8 7.2 4.5 5.6 5.8 4 7.5 4z" fill="none" stroke-width="2" stroke-linejoin="round"/>`,
  search: `<circle cx="10.5" cy="10.5" r="6" fill="none" stroke-width="2.4"/><path d="M15.2 15.2L20 20" stroke-width="2.4" stroke-linecap="round"/>`,
  db: `<ellipse cx="12" cy="6" rx="7" ry="2.8" fill="none" stroke-width="2"/><path d="M5 6v12c0 1.6 3.1 2.8 7 2.8s7-1.2 7-2.8V6M5 12c0 1.6 3.1 2.8 7 2.8s7-1.2 7-2.8" fill="none" stroke-width="2"/>`,
  refresh: `<path d="M19 12a7 7 0 1 1-2-4.9M17 4v3.4h-3.4" fill="none" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"/>`,
  chevron: `<path d="M9 6l6 6-6 6" fill="none" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>`,
  user: `<circle cx="12" cy="8.4" r="3.6" fill="none" stroke-width="2.2"/><path d="M5 20c.8-3.6 3.6-5.4 7-5.4s6.2 1.8 7 5.4" fill="none" stroke-width="2.2" stroke-linecap="round"/>`,
  bell: `<path d="M6 17h12c-1-1.2-1.6-2-1.6-5.2 0-2.8-1.8-4.8-4.4-4.8s-4.4 2-4.4 4.8C7.6 15 7 15.8 6 17z" fill="none" stroke-width="2.1" stroke-linejoin="round"/><path d="M10.4 19.6a1.8 1.8 0 0 0 3.2 0" fill="none" stroke-width="2.1" stroke-linecap="round"/>`,
  settings: `<circle cx="12" cy="12" r="3" fill="none" stroke-width="2.1"/><path d="M12 3.6v2.2M12 18.2v2.2M3.6 12h2.2M18.2 12h2.2M6.1 6.1l1.5 1.5M16.4 16.4l1.5 1.5M6.1 17.9l1.5-1.5M16.4 7.6l1.5-1.5" stroke-width="2.1" stroke-linecap="round"/>`,
  flag: `<path d="M6 21V4m0 1h11l-2.5 4L17 13H6" fill="none" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>`,
  home: `<path d="M4 11l8-7 8 7M6.5 9.8V20h11V9.8" fill="none" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>`,
  list: `<path d="M8 6h12M8 12h12M8 18h12" stroke-width="2.2" stroke-linecap="round"/><circle cx="4.4" cy="6" r="1.3" stroke="none"/><circle cx="4.4" cy="12" r="1.3" stroke="none"/><circle cx="4.4" cy="18" r="1.3" stroke="none"/>`,
  shieldS: `<path d="M12 3.4l7 2.8v5.4c0 4.8-3 7.9-7 9.6-4-1.7-7-4.8-7-9.6V6.2z" fill="none" stroke-width="2.2" stroke-linejoin="round"/>`,
  lock: `<rect x="5.5" y="10.5" width="13" height="9" rx="2" fill="none" stroke-width="2.2"/><path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5" fill="none" stroke-width="2.2"/>`,
  code: `<path d="M9 7l-5 5 5 5M15 7l5 5-5 5" fill="none" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"/>`,
  eyeOff: `<path d="M4 12s3-5.4 8-5.4S20 12 20 12s-3 5.4-8 5.4S4 12 4 12z" fill="none" stroke-width="2"/><path d="M5.5 5.5l13 13" stroke-width="2" stroke-linecap="round"/>`,
  euro: `<path d="M17 6.6A6.4 6.4 0 0 0 6.8 12 6.4 6.4 0 0 0 17 17.4M4.6 10.4h8M4.6 13.6h8" fill="none" stroke-width="2.2" stroke-linecap="round"/>`,
};

function icon(name, x, y, size, color) {
  const s = size / 24;
  return `<g transform="translate(${x},${y}) scale(${s})" stroke="${color}" fill="${color}">${ICONS[name]}</g>`;
}

// ---------------------------------------------------------------------------
// Écrans recréés (fidèles aux vues Compose / SwiftUI) — viewBox 430×932
// ---------------------------------------------------------------------------
const SCR_W = 430;
const SCR_H = 932;

function statusBar(dark = false) {
  const col = dark ? C.white : C.ink;
  return `
    ${txt(28, 32, "9:41", { size: 15, weight: 600, fill: col })}
    <g fill="${col}">
      <rect x="352" y="20" width="3" height="9" rx="1"/><rect x="357" y="17" width="3" height="12" rx="1"/>
      <rect x="362" y="14" width="3" height="15" rx="1"/><rect x="367" y="11" width="3" height="18" rx="1"/>
      <rect x="378" y="15" width="22" height="12" rx="3.5" fill="none" stroke="${col}" stroke-width="1.6"/>
      <rect x="380" y="17" width="15" height="8" rx="2"/>
      <rect x="401" y="18.5" width="2.4" height="5" rx="1.2"/>
    </g>`;
}

/** En-tête dégradé bleu → bleu nuit (GradientHeader de l'app). */
function appHeader(title, subtitle, { trailingIcon = "shieldS", trailingColor = C.white } = {}) {
  return `
    <rect x="0" y="0" width="${SCR_W}" height="128" fill="url(#hdr)"/>
    ${statusBar(true)}
    ${txt(28, 84, title, { size: 26, weight: 800, fill: C.white })}
    ${txt(28, 110, subtitle, { size: 14.5, weight: 500, fill: C.white, opacity: 0.85 })}
    ${icon(trailingIcon, 372, 76, 30, trailingColor)}`;
}

/** Carte à bord arrondi. */
function card(x, y, w, h, { fill = C.white, stroke = C.border, accent = null, rx = 16 } = {}) {
  let s = `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}" stroke="${stroke}" stroke-width="1.4"/>`;
  if (accent) s += `<rect x="${x}" y="${y + 12}" width="4" height="${h - 24}" rx="2" fill="${accent}"/>`;
  return s;
}

function bottomNav(active = 0) {
  const items = [
    ["home", "Accueil"],
    ["flag", "Signaler"],
    ["list", "Journal"],
    ["settings", "Réglages"],
  ];
  const w = SCR_W / items.length;
  let s = `<rect x="0" y="${SCR_H - 84}" width="${SCR_W}" height="84" fill="${C.white}"/>
    <line x1="0" y1="${SCR_H - 84}" x2="${SCR_W}" y2="${SCR_H - 84}" stroke="${C.border}" stroke-width="1.4"/>`;
  items.forEach(([ic, label], i) => {
    const cx = w * i + w / 2;
    const col = i === active ? C.blue : C.muted;
    if (i === active)
      s += `<rect x="${cx - 34}" y="${SCR_H - 74}" width="68" height="32" rx="16" fill="${C.tint}"/>`;
    s += icon(ic, cx - 12, SCR_H - 70, 24, col);
    s += txt(cx, SCR_H - 26, label, { size: 12, weight: i === active ? 700 : 500, fill: col, anchor: "middle" });
  });
  return s;
}

/** Écran Accueil — protection active (HomeScreen.kt). */
function screenHome() {
  let y = 148;
  let s = `<rect width="${SCR_W}" height="${SCR_H}" fill="#F7F9FE"/>`;
  s += appHeader("Who Called", "Vous êtes protégé");

  // Bouton « Mettre à jour la liste »
  s += `<rect x="24" y="${y}" width="${SCR_W - 48}" height="46" rx="23" fill="${C.white}" stroke="${C.blue}" stroke-width="1.6"/>`;
  s += icon("refresh", 118, y + 12, 22, C.blue);
  s += txt(SCR_W / 2 + 14, y + 30, "Mettre à jour la liste", { size: 15.5, weight: 600, fill: C.blue, anchor: "middle" });
  y += 66;

  // Carte protection émeraude « Bloqueur actif et à jour »
  const ph = 268;
  s += `<rect x="24" y="${y}" width="${SCR_W - 48}" height="${ph}" rx="16" fill="#E9F8F2" stroke="#A9E4CE" stroke-width="1.4"/>`;
  s += icon("check", 44, y + 22, 26, C.emerald);
  s += `<circle cx="57" cy="${y + 35}" r="17" fill="none" stroke="${C.emerald}" stroke-width="2.4"/>`;
  s += txt(86, y + 41, "Bloqueur actif et à jour", { size: 17.5, weight: 800 });
  s += `<line x1="44" y1="${y + 64}" x2="${SCR_W - 44}" y2="${y + 64}" stroke="#A9E4CE" stroke-width="1.2"/>`;

  s += icon("db", 44, y + 80, 22, C.emerald);
  s += txt(76, y + 92, "Numéros couverts", { size: 15, weight: 500 });
  s += txt(76, y + 112, "Mise à jour auto · à jour aujourd’hui", { size: 12.5, fill: C.muted });
  s += txt(SCR_W - 44, y + 96, "16 643 100", { size: 16, weight: 800, anchor: "end" });
  s += txt(76, y + 136, "France : 15 214 900 numéros · 41 préfixes ARCEP", { size: 12.5, fill: C.muted });

  s += icon("block", 44, y + 158, 22, C.coral);
  s += txt(76, y + 170, "Appels bloqués", { size: 15, weight: 600 });
  s += txt(76, y + 190, "Rejetés sans sonner · voir le détail", { size: 12.5, fill: C.muted });
  s += txt(SCR_W - 66, y + 178, "27", { size: 22, weight: 800, fill: C.coral, anchor: "end" });
  s += icon("chevron", SCR_W - 62, y + 162, 20, C.muted);

  s += icon("warn", 44, y + 212, 22, C.amber);
  s += txt(76, y + 224, "Appels suspects", { size: 15, weight: 600 });
  s += txt(76, y + 244, "Ont sonné, vous avez été prévenu", { size: 12.5, fill: C.muted });
  s += txt(SCR_W - 66, y + 232, "4", { size: 22, weight: 800, fill: C.amber, anchor: "end" });
  s += icon("chevron", SCR_W - 62, y + 216, 20, C.muted);
  y += ph + 16;

  // Bouclier communautaire
  s += card(24, y, SCR_W - 48, 92, { accent: C.blue });
  s += icon("shieldS", 44, y + 18, 26, C.blue);
  s += txt(82, y + 32, "Bouclier communautaire", { size: 15, weight: 700 });
  s += txt(82, y + 54, "412 380 signalements ensemble · +8 214 cette semaine", { size: 12, fill: C.muted });
  s += `<rect x="44" y="${y + 68}" width="${SCR_W - 88}" height="6" rx="3" fill="${C.tint}"/>`;
  s += `<rect x="44" y="${y + 68}" width="${(SCR_W - 88) * 0.66}" height="6" rx="3" fill="${C.blue}"/>`;
  y += 108;

  // Bouclier SMS
  s += card(24, y, SCR_W - 48, 76, { accent: C.emerald });
  s += icon("msg", 44, y + 24, 28, C.emerald);
  s += txt(84, y + 34, "Bouclier SMS", { size: 15, weight: 700 });
  s += txt(84, y + 55, "Actif · les SMS indésirables sont masqués", { size: 12.5, fill: C.emerald });
  s += icon("chevron", SCR_W - 62, y + 26, 22, C.muted);

  s += bottomNav(0);
  return s;
}

/** Écran d'appel entrant bloqué (mise en scène marketing). */
function screenCallBlocked({ legit = false } = {}) {
  let s = `<rect width="${SCR_W}" height="${SCR_H}" fill="url(#call)"/>`;
  s += statusBar(true);

  s += icon("phone", SCR_W / 2 - 60, 76, 20, "#9DB8E8");
  s += txt(SCR_W / 2 + 8, 92, "Who Called", { size: 15, weight: 600, fill: "#9DB8E8", anchor: "middle" });

  // Avatar
  s += `<circle cx="${SCR_W / 2}" cy="212" r="52" fill="#24457E"/>`;
  s += icon("user", SCR_W / 2 - 26, 186, 52, "#7E9CD1");

  s += txt(SCR_W / 2, 312, "Appel entrant…", { size: 17, weight: 500, fill: "#B9CBEA", anchor: "middle" });
  s += txt(SCR_W / 2, 356, "+33 9 48 12 34 56", { size: 30, weight: 700, fill: C.white, anchor: "middle" });

  // Carte d'identification
  const cy = 420;
  const ch = 176;
  if (!legit) {
    s += `<rect x="20" y="${cy}" width="${SCR_W - 40}" height="${ch}" rx="22" fill="${C.coral}"/>`;
    s += `<circle cx="72" cy="${cy + 62}" r="32" fill="${C.white}" fill-opacity="0.18"/>`;
    s += icon("block", 56, cy + 46, 32, C.white);
    s += txt(122, cy + 56, "Démarchage présumé", { size: 22, weight: 800, fill: C.white });
    s += txt(122, cy + 84, "256 signalements · communauté + ARCEP", { size: 13.5, fill: C.white, opacity: 0.92 });
    s += `<line x1="42" y1="${cy + 110}" x2="${SCR_W - 42}" y2="${cy + 110}" stroke="${C.white}" stroke-opacity="0.28"/>`;
    s += icon("block", 42, cy + 128, 20, C.white);
    s += txt(72, cy + 143, "Bloqué avant de sonner", { size: 14.5, weight: 700, fill: C.white });
    s += shieldLogo(SCR_W - 84, cy + 122, 34, { bg: C.coral, fg: C.white });
  } else {
    s += `<rect x="20" y="${cy}" width="${SCR_W - 40}" height="${ch}" rx="22" fill="${C.emerald}"/>`;
    s += `<circle cx="72" cy="${cy + 62}" r="32" fill="${C.white}" fill-opacity="0.18"/>`;
    s += icon("check", 58, cy + 48, 28, C.white);
    s += txt(122, cy + 56, "Numéro sûr", { size: 22, weight: 800, fill: C.white });
    s += txt(122, cy + 84, "Aucun signalement · vous pouvez répondre", { size: 13.5, fill: C.white, opacity: 0.92 });
  }

  // Boutons répondre / raccrocher
  const by = SCR_H - 150;
  s += `<circle cx="${SCR_W / 2 - 90}" cy="${by}" r="40" fill="${C.coral}"/>`;
  s += `<g transform="translate(${SCR_W / 2 - 90},${by}) rotate(135)">${icon("phone", -13, -13, 26, C.white)}</g>`;
  s += `<circle cx="${SCR_W / 2 + 90}" cy="${by}" r="40" fill="${C.emerald}"/>`;
  s += icon("phone", SCR_W / 2 + 77, by - 13, 26, C.white);
  return s;
}

/** Écran vérification d'un numéro (NumberLookup / NumberDetailView). */
function screenLookup() {
  let s = `<rect width="${SCR_W}" height="${SCR_H}" fill="#F7F9FE"/>`;
  s += appHeader("Vérifier un numéro", "Réputation ARCEP + communauté", { trailingIcon: "search" });
  let y = 148;

  // Champ de recherche
  s += `<rect x="24" y="${y}" width="${SCR_W - 48}" height="52" rx="26" fill="${C.white}" stroke="${C.border}" stroke-width="1.5"/>`;
  s += icon("search", 44, y + 14, 24, C.muted);
  s += txt(80, y + 33, "+33 9 48 12 34 56", { size: 16.5, weight: 600 });
  y += 72;

  // Carte score avec jauge
  const ch = 236;
  s += card(24, y, SCR_W - 48, ch);
  const gx = SCR_W / 2;
  const gy = y + 118;
  s += `<path d="M ${gx - 78} ${gy + 30} A 84 84 0 1 1 ${gx + 78} ${gy + 30}" fill="none" stroke="${C.tint}" stroke-width="15" stroke-linecap="round"/>`;
  s += `<path d="M ${gx - 78} ${gy + 30} A 84 84 0 1 1 ${gx + 66} ${gy + 46}" fill="none" stroke="url(#gauge)" stroke-width="15" stroke-linecap="round"/>`;
  s += txt(gx, gy + 12, "87", { size: 52, weight: 800, fill: C.coral, anchor: "middle" });
  s += txt(gx, gy + 42, "indice de spam", { size: 13.5, fill: C.muted, anchor: "middle" });
  s += `<rect x="${gx - 92}" y="${y + 186}" width="88" height="30" rx="15" fill="${C.tint}"/>`;
  s += txt(gx - 48, y + 206, "ARCEP", { size: 13, weight: 700, fill: C.blue, anchor: "middle" });
  s += `<rect x="${gx + 4}" y="${y + 186}" width="128" height="30" rx="15" fill="#FDECEC"/>`;
  s += txt(gx + 68, y + 206, "Communauté", { size: 13, weight: 700, fill: C.coral, anchor: "middle" });
  y += ch + 16;

  // Catégorie dominante
  s += card(24, y, SCR_W - 48, 66, { accent: C.coral });
  s += icon("warn", 44, y + 20, 24, C.coral);
  s += txt(80, y + 30, "Démarchage", { size: 15, weight: 700 });
  s += txt(80, y + 50, "Catégorie la plus signalée", { size: 12.5, fill: C.muted });
  s += txt(SCR_W - 44, y + 40, "182×", { size: 16, weight: 800, fill: C.coral, anchor: "end" });
  y += 82;

  // Derniers signalements
  s += card(24, y, SCR_W - 48, 128);
  s += txt(44, y + 30, "Derniers signalements", { size: 14.5, weight: 700 });
  const rows = [
    ["Arnaque", "il y a 2 h", C.coral],
    ["Démarchage", "hier", C.amber],
    ["Appel silencieux", "il y a 3 j", C.muted],
  ];
  rows.forEach(([label, when, col], i) => {
    const ry = y + 56 + i * 26;
    s += `<circle cx="50" cy="${ry - 5}" r="4" fill="${col}"/>`;
    s += txt(66, ry, label, { size: 13.5, weight: 600 });
    s += txt(SCR_W - 44, ry, when, { size: 12.5, fill: C.muted, anchor: "end" });
  });

  s += bottomNav(2);
  return s;
}

/** Écran Signaler (ReportScreen). */
function screenReport() {
  let s = `<rect width="${SCR_W}" height="${SCR_H}" fill="#F7F9FE"/>`;
  s += appHeader("Signaler un numéro", "Anonyme — aucun compte requis", { trailingIcon: "flag" });
  let y = 152;

  s += txt(28, y, "Numéro de téléphone", { size: 13.5, weight: 700, fill: C.muted });
  y += 14;
  s += `<rect x="24" y="${y}" width="${SCR_W - 48}" height="54" rx="14" fill="${C.white}" stroke="${C.blue}" stroke-width="1.8"/>`;
  s += txt(44, y + 35, "+33 9 48 12 34 56", { size: 17, weight: 600 });
  s += `<rect x="${SCR_W - 40}" y="${y + 14}" width="2.4" height="26" fill="${C.blue}"/>`;
  y += 78;

  // Votes
  const half = (SCR_W - 48 - 12) / 2;
  s += `<rect x="24" y="${y}" width="${half}" height="56" rx="14" fill="${C.coral}"/>`;
  s += icon("block", 24 + half / 2 - 58, y + 17, 22, C.white);
  s += txt(24 + half / 2 + 14, y + 35, "Indésirable", { size: 15.5, weight: 700, fill: C.white, anchor: "middle" });
  s += `<rect x="${36 + half}" y="${y}" width="${half}" height="56" rx="14" fill="${C.white}" stroke="${C.border}" stroke-width="1.5"/>`;
  s += icon("check", 36 + half + half / 2 - 52, y + 17, 22, C.emerald);
  s += txt(36 + half + half / 2 + 14, y + 35, "Légitime", { size: 15.5, weight: 700, fill: C.ink, anchor: "middle" });
  y += 82;

  s += txt(28, y, "Catégorie", { size: 13.5, weight: 700, fill: C.muted });
  y += 14;
  const chips = [
    ["Démarchage", true], ["Arnaque", false], ["Appel automatisé", false],
    ["Appel silencieux", false], ["Recouvrement", false], ["Sondage", false], ["Autre", false],
  ];
  let cx = 24, cy2 = y;
  const chipH = 40;
  chips.forEach(([label, sel]) => {
    const w = label.length * 8.2 + 34;
    if (cx + w > SCR_W - 24) { cx = 24; cy2 += chipH + 12; }
    s += `<rect x="${cx}" y="${cy2}" width="${w}" height="${chipH}" rx="20" fill="${sel ? C.blue : C.white}" stroke="${sel ? C.blue : C.border}" stroke-width="1.5"/>`;
    s += txt(cx + w / 2, cy2 + 26, label, { size: 13.5, weight: sel ? 700 : 500, fill: sel ? C.white : C.ink, anchor: "middle" });
    cx += w + 12;
  });
  y = cy2 + chipH + 26;

  // Note anonymat
  s += card(24, y, SCR_W - 48, 64, { fill: C.tint, stroke: "#D8E6FD" });
  s += icon("lock", 44, y + 20, 24, C.blue);
  s += txt(80, y + 30, "100 % anonyme", { size: 14, weight: 700, fill: C.blue });
  s += txt(80, y + 50, "Aucun compte, aucune donnée personnelle.", { size: 12.5, fill: C.muted });
  y += 84;

  // Bouton envoyer
  s += `<rect x="24" y="${y}" width="${SCR_W - 48}" height="56" rx="28" fill="${C.blue}"/>`;
  s += icon("flag", SCR_W / 2 - 118, y + 16, 24, C.white);
  s += txt(SCR_W / 2 + 16, y + 36, "Envoyer le signalement", { size: 16, weight: 700, fill: C.white, anchor: "middle" });

  s += bottomNav(1);
  return s;
}

/** Écran Bouclier SMS. */
function screenSms() {
  let s = `<rect width="${SCR_W}" height="${SCR_H}" fill="#F7F9FE"/>`;
  s += appHeader("Bouclier SMS", "Filtrez aussi les SMS indésirables", { trailingIcon: "msg" });
  let y = 148;

  // Toggle actif
  s += card(24, y, SCR_W - 48, 84, { fill: "#E9F8F2", stroke: "#A9E4CE" });
  s += icon("msg", 44, y + 28, 30, C.emerald);
  s += txt(88, y + 38, "Bouclier SMS actif", { size: 16, weight: 800 });
  s += txt(88, y + 60, "Les SMS indésirables sont masqués", { size: 12.5, fill: C.muted });
  s += `<rect x="${SCR_W - 96}" y="${y + 28}" width="52" height="30" rx="15" fill="${C.emerald}"/>`;
  s += `<circle cx="${SCR_W - 58}" cy="${y + 43}" r="12" fill="${C.white}"/>`;
  y += 104;

  // SMS masqué (exemple)
  s += txt(28, y + 6, "Aujourd’hui", { size: 13.5, weight: 700, fill: C.muted });
  y += 22;
  s += card(24, y, SCR_W - 48, 96, { accent: C.coral });
  s += icon("eyeOff", 44, y + 18, 24, C.coral);
  s += txt(80, y + 30, "SMS indésirable masqué", { size: 15, weight: 700 });
  s += txt(80, y + 52, "+33 7 62 xx xx xx · « Votre colis est bloqué,", { size: 13, fill: C.muted });
  s += txt(80, y + 71, "cliquez ici pour payer 2 € … »", { size: 13, fill: C.muted });
  s += `<rect x="${SCR_W - 110}" y="${y + 14}" width="66" height="24" rx="12" fill="#FDECEC"/>`;
  s += txt(SCR_W - 77, y + 30.5, "Filtré", { size: 12, weight: 700, fill: C.coral, anchor: "middle" });
  y += 116;

  s += card(24, y, SCR_W - 48, 96, { accent: C.coral });
  s += icon("eyeOff", 44, y + 18, 24, C.coral);
  s += txt(80, y + 30, "SMS indésirable masqué", { size: 15, weight: 700 });
  s += txt(80, y + 52, "36 6 xx · « Dernier rappel : votre compte", { size: 13, fill: C.muted });
  s += txt(80, y + 71, "CPF expire bientôt … »", { size: 13, fill: C.muted });
  s += `<rect x="${SCR_W - 110}" y="${y + 14}" width="66" height="24" rx="12" fill="#FDECEC"/>`;
  s += txt(SCR_W - 77, y + 30.5, "Filtré", { size: 12, weight: 700, fill: C.coral, anchor: "middle" });
  y += 116;

  // SMS légitime passé
  s += card(24, y, SCR_W - 48, 76, { accent: C.emerald });
  s += icon("check", 44, y + 20, 24, C.emerald);
  s += txt(80, y + 32, "Maman", { size: 15, weight: 700 });
  s += txt(80, y + 54, "« On mange toujours dimanche ? » · Délivré", { size: 13, fill: C.muted });

  s += bottomNav(3);
  return s;
}

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
