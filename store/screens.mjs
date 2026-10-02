/**
 * Écrans de l'app recréés en SVG (fidèles aux vues Compose / SwiftUI), viewBox 430×932.
 * Partagés par les visuels stores (store/generate.mjs) et le film promo (store/motion).
 *
 * Chaque écran prend des textes `S` (français par défaut, cf. SCREEN_FR) et une option
 * `anim` : quand elle est vraie, l'écran expose des id et des calques d'état (bouton
 * non sélectionné, toast…) que le film anime. Sans `anim`, la sortie est strictement
 * celle des visuels stores.
 */

// ---------------------------------------------------------------------------
// Palette (source : android Theme.kt / ios App.swift / web tailwind)
// ---------------------------------------------------------------------------
export const C = {
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

export const FONT = `'Helvetica Neue', 'Segoe UI', Roboto, Arial, sans-serif`;

export const esc = (s) =>
  s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");

/** Dégradés utilisés par les écrans (à inclure une fois dans le document hôte). */
export const SCREEN_DEFS = `<defs>
  <linearGradient id="hdr" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="${C.blue}"/><stop offset="1" stop-color="${C.night}"/>
  </linearGradient>
  <linearGradient id="call" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#1E3A6E"/><stop offset="1" stop-color="${C.nightDark}"/>
  </linearGradient>
  <linearGradient id="gauge" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="${C.amber}"/><stop offset="1" stop-color="${C.coral}"/>
  </linearGradient>
</defs>`;

/** Textes des écrans — français (valeurs historiques des visuels stores). */
export const SCREEN_FR = {
  nav: ["Accueil", "Signaler", "Journal", "Réglages"],
  lookup: {
    title: "Vérifier un numéro",
    sub: "Réputation ARCEP + communauté",
    number: "+33 9 48 12 34 56",
    score: 87,
    scoreLabel: "indice de spam",
    src1: "ARCEP",
    src2: "Communauté",
    cat: "Démarchage",
    catSub: "Catégorie la plus signalée",
    catCount: "182×",
    recent: "Derniers signalements",
    rows: [["Arnaque", "il y a 2 h"], ["Démarchage", "hier"], ["Appel silencieux", "il y a 3 j"]],
  },
  report: {
    title: "Signaler un numéro",
    sub: "Anonyme — aucun compte requis",
    phoneLabel: "Numéro de téléphone",
    number: "+33 9 48 12 34 56",
    spam: "Indésirable",
    spamIconDx: -58,
    legit: "Légitime",
    legitIconDx: -52,
    catLabel: "Catégorie",
    chips: ["Démarchage", "Arnaque", "Appel automatisé", "Appel silencieux", "Recouvrement", "Sondage", "Autre"],
    selected: 0,
    anonTitle: "100 % anonyme",
    anonSub: "Aucun compte, aucune donnée personnelle.",
    send: "Envoyer le signalement",
    sendIconDx: -118,
    sent: "Merci ! Signalement anonyme envoyé.",
  },
  sms: {
    title: "Bouclier SMS",
    sub: "Filtrez aussi les SMS indésirables",
    onTitle: "Bouclier SMS actif",
    onSub: "Les SMS indésirables sont masqués",
    today: "Aujourd’hui",
    masked: "SMS indésirable masqué",
    filtered: "Filtré",
    spam: [
      ["+33 7 62 xx xx xx · « Votre colis est bloqué,", "cliquez ici pour payer 2 € … »"],
      ["36 6 xx · « Dernier rappel : votre compte", "CPF expire bientôt … »"],
    ],
    legitFrom: "Maman",
    legitText: "« On mange toujours dimanche ? » · Délivré",
  },
};

// ---------------------------------------------------------------------------
// Petites briques SVG
// ---------------------------------------------------------------------------

/** Texte utilitaire. */
export function txt(x, y, s, { size = 16, weight = 400, fill = C.ink, anchor = "start", spacing = 0, opacity = 1, id = "" } = {}) {
  return `<text${id ? ` id="${id}"` : ""} x="${x}" y="${y}" font-family="${FONT}" font-size="${size}" font-weight="${weight}" fill="${fill}" text-anchor="${anchor}"${spacing ? ` letter-spacing="${spacing}"` : ""}${opacity !== 1 ? ` fill-opacity="${opacity}"` : ""}>${esc(s)}</text>`;
}

/** Bouclier + coche Who Called (logo). Dessiné dans une boîte size×size. */
export function shieldLogo(x, y, size, { bg = C.blue, fg = C.white, rim = true } = {}) {
  const s = size / 48;
  return `<g transform="translate(${x},${y}) scale(${s})">
    <path d="M24 3l17 6.4v13c0 11.6-7.4 19-17 23.2C14.4 41.4 7 34 7 22.4v-13z" fill="${fg}"/>
    ${rim ? `<path d="M24 7.6l12.8 4.8v10c0 8.8-5.6 14.5-12.8 17.8-7.2-3.3-12.8-9-12.8-17.8v-10z" fill="${bg}"/>` : ""}
    <path d="M21.4 29.4l-5.4-5.4 2.7-2.7 2.7 2.7 9.6-9.6 2.7 2.7z" fill="${fg}"/>
  </g>`;
}

/** Icônes ligne (24×24 viewbox, stroke). */
export const ICONS = {
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

export function icon(name, x, y, size, color) {
  const s = size / 24;
  return `<g transform="translate(${x},${y}) scale(${s})" stroke="${color}" fill="${color}">${ICONS[name]}</g>`;
}

// ---------------------------------------------------------------------------
// Chrome commun des écrans
// ---------------------------------------------------------------------------
export const SCR_W = 430;
export const SCR_H = 932;

/** En mode anim, entoure `content` d'un groupe identifié ; sinon le rend tel quel. */
const grp = (anim, id, content) => (anim ? `<g id="${id}">${content}</g>` : content);

export function statusBar(dark = false) {
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
export function appHeader(title, subtitle, { trailingIcon = "shieldS", trailingColor = C.white } = {}) {
  return `
    <rect x="0" y="0" width="${SCR_W}" height="128" fill="url(#hdr)"/>
    ${statusBar(true)}
    ${txt(28, 84, title, { size: 26, weight: 800, fill: C.white })}
    ${txt(28, 110, subtitle, { size: 14.5, weight: 500, fill: C.white, opacity: 0.85 })}
    ${icon(trailingIcon, 372, 76, 30, trailingColor)}`;
}

/** Carte à bord arrondi. */
export function card(x, y, w, h, { fill = C.white, stroke = C.border, accent = null, rx = 16 } = {}) {
  let s = `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}" stroke="${stroke}" stroke-width="1.4"/>`;
  if (accent) s += `<rect x="${x}" y="${y + 12}" width="4" height="${h - 24}" rx="2" fill="${accent}"/>`;
  return s;
}

export function bottomNav(active = 0, labels = SCREEN_FR.nav) {
  const items = [
    ["home", labels[0]],
    ["flag", labels[1]],
    ["list", labels[2]],
    ["settings", labels[3]],
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

// ---------------------------------------------------------------------------
// Écrans
// ---------------------------------------------------------------------------

/** Écran Accueil — protection active (HomeScreen.kt). */
export function screenHome() {
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
export function screenCallBlocked({ legit = false } = {}) {
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
export function screenLookup({ S = SCREEN_FR, anim = false } = {}) {
  const T = S.lookup;
  let s = `<rect width="${SCR_W}" height="${SCR_H}" fill="#F7F9FE"/>`;
  s += appHeader(T.title, T.sub, { trailingIcon: "search" });
  let y = 148;

  // Champ de recherche
  s += `<rect x="24" y="${y}" width="${SCR_W - 48}" height="52" rx="26" fill="${C.white}" stroke="${C.border}" stroke-width="1.5"/>`;
  s += icon("search", 44, y + 14, 24, C.muted);
  s += txt(80, y + 33, T.number, { size: 16.5, weight: 600, id: anim ? "lk-num" : "" });
  if (anim) s += `<rect id="lk-caret" x="82" y="${y + 15}" width="2.2" height="22" fill="${C.blue}"/>`;
  y += 72;

  // Carte score avec jauge
  const ch = 236;
  const gx = SCR_W / 2;
  const gy = y + 118;
  let g = card(24, y, SCR_W - 48, ch);
  g += `<path d="M ${gx - 78} ${gy + 30} A 84 84 0 1 1 ${gx + 78} ${gy + 30}" fill="none" stroke="${C.tint}" stroke-width="15" stroke-linecap="round"/>`;
  g += `<path${anim ? ` id="lk-arc" pathLength="1"` : ""} d="M ${gx - 78} ${gy + 30} A 84 84 0 1 1 ${gx + 66} ${gy + 46}" fill="none" stroke="url(#gauge)" stroke-width="15" stroke-linecap="round"/>`;
  g += txt(gx, gy + 12, String(T.score), { size: 52, weight: 800, fill: C.coral, anchor: "middle", id: anim ? "lk-score" : "" });
  g += txt(gx, gy + 42, T.scoreLabel, { size: 13.5, fill: C.muted, anchor: "middle" });
  g += grp(anim, "lk-srcs", `<rect x="${gx - 92}" y="${y + 186}" width="88" height="30" rx="15" fill="${C.tint}"/>`
    + txt(gx - 48, y + 206, T.src1, { size: 13, weight: 700, fill: C.blue, anchor: "middle" })
    + `<rect x="${gx + 4}" y="${y + 186}" width="128" height="30" rx="15" fill="#FDECEC"/>`
    + txt(gx + 68, y + 206, T.src2, { size: 13, weight: 700, fill: C.coral, anchor: "middle" }));
  s += grp(anim, "lk-scorecard", g);
  y += ch + 16;

  // Catégorie dominante
  s += grp(anim, "lk-cat", card(24, y, SCR_W - 48, 66, { accent: C.coral })
    + icon("warn", 44, y + 20, 24, C.coral)
    + txt(80, y + 30, T.cat, { size: 15, weight: 700 })
    + txt(80, y + 50, T.catSub, { size: 12.5, fill: C.muted })
    + txt(SCR_W - 44, y + 40, T.catCount, { size: 16, weight: 800, fill: C.coral, anchor: "end" }));
  y += 82;

  // Derniers signalements
  let r = card(24, y, SCR_W - 48, 128);
  r += txt(44, y + 30, T.recent, { size: 14.5, weight: 700 });
  const dots = [C.coral, C.amber, C.muted];
  T.rows.forEach(([label, when], i) => {
    const ry = y + 56 + i * 26;
    r += grp(anim, `lk-row${i}`, `<circle cx="50" cy="${ry - 5}" r="4" fill="${dots[i]}"/>`
      + txt(66, ry, label, { size: 13.5, weight: 600 })
      + txt(SCR_W - 44, ry, when, { size: 12.5, fill: C.muted, anchor: "end" }));
  });
  s += grp(anim, "lk-recent", r);

  s += bottomNav(2, S.nav);
  return s;
}

/** Écran Signaler (ReportScreen). */
export function screenReport({ S = SCREEN_FR, anim = false } = {}) {
  const T = S.report;
  let s = `<rect width="${SCR_W}" height="${SCR_H}" fill="#F7F9FE"/>`;
  s += appHeader(T.title, T.sub, { trailingIcon: "flag" });
  let y = 152;

  s += txt(28, y, T.phoneLabel, { size: 13.5, weight: 700, fill: C.muted });
  y += 14;
  s += `<rect x="24" y="${y}" width="${SCR_W - 48}" height="54" rx="14" fill="${C.white}" stroke="${C.blue}" stroke-width="1.8"/>`;
  s += txt(44, y + 35, T.number, { size: 17, weight: 600 });
  s += `<rect x="${SCR_W - 40}" y="${y + 14}" width="2.4" height="26" fill="${C.blue}"/>`;
  y += 78;

  // Votes (en mode anim : état neutre dessous, état « Indésirable » sélectionné dessus)
  const half = (SCR_W - 48 - 12) / 2;
  if (anim) {
    s += `<rect x="24" y="${y}" width="${half}" height="56" rx="14" fill="${C.white}" stroke="${C.border}" stroke-width="1.5"/>`;
    s += icon("block", 24 + half / 2 + T.spamIconDx, y + 17, 22, C.coral);
    s += txt(24 + half / 2 + 14, y + 35, T.spam, { size: 15.5, weight: 700, fill: C.ink, anchor: "middle" });
  }
  s += grp(anim, "rp-spam-on", `<rect x="24" y="${y}" width="${half}" height="56" rx="14" fill="${C.coral}"/>`
    + icon("block", 24 + half / 2 + T.spamIconDx, y + 17, 22, C.white)
    + txt(24 + half / 2 + 14, y + 35, T.spam, { size: 15.5, weight: 700, fill: C.white, anchor: "middle" }));
  s += `<rect x="${36 + half}" y="${y}" width="${half}" height="56" rx="14" fill="${C.white}" stroke="${C.border}" stroke-width="1.5"/>`;
  s += icon("check", 36 + half + half / 2 + T.legitIconDx, y + 17, 22, C.emerald);
  s += txt(36 + half + half / 2 + 14, y + 35, T.legit, { size: 15.5, weight: 700, fill: C.ink, anchor: "middle" });
  if (anim) s += `<rect id="rp-spam-hit" x="24" y="${y}" width="${half}" height="56" fill="none"/>`;
  y += 82;

  s += txt(28, y, T.catLabel, { size: 13.5, weight: 700, fill: C.muted });
  y += 14;
  let cx = 24, cy2 = y;
  const chipH = 40;
  T.chips.forEach((label, i) => {
    const sel = i === T.selected;
    const w = label.length * 8.2 + 34;
    if (cx + w > SCR_W - 24) { cx = 24; cy2 += chipH + 12; }
    if (anim && sel) {
      // neutre dessous, sélectionné dessus (révélé par le film)
      s += `<rect x="${cx}" y="${cy2}" width="${w}" height="${chipH}" rx="20" fill="${C.white}" stroke="${C.border}" stroke-width="1.5"/>`;
      s += txt(cx + w / 2, cy2 + 26, label, { size: 13.5, weight: 500, fill: C.ink, anchor: "middle" });
    }
    s += grp(anim && sel, "rp-chip-on", `<rect x="${cx}" y="${cy2}" width="${w}" height="${chipH}" rx="20" fill="${sel ? C.blue : C.white}" stroke="${sel ? C.blue : C.border}" stroke-width="1.5"/>`
      + txt(cx + w / 2, cy2 + 26, label, { size: 13.5, weight: sel ? 700 : 500, fill: sel ? C.white : C.ink, anchor: "middle" }));
    if (anim && sel) s += `<rect id="rp-chip-hit" x="${cx}" y="${cy2}" width="${w}" height="${chipH}" fill="none"/>`;
    cx += w + 12;
  });
  y = cy2 + chipH + 26;

  // Note anonymat
  s += card(24, y, SCR_W - 48, 64, { fill: C.tint, stroke: "#D8E6FD" });
  s += icon("lock", 44, y + 20, 24, C.blue);
  s += txt(80, y + 30, T.anonTitle, { size: 14, weight: 700, fill: C.blue });
  s += txt(80, y + 50, T.anonSub, { size: 12.5, fill: C.muted });
  y += 84;

  // Bouton envoyer
  s += grp(anim, "rp-send", `<rect x="24" y="${y}" width="${SCR_W - 48}" height="56" rx="28" fill="${C.blue}"/>`
    + icon("flag", SCR_W / 2 + T.sendIconDx, y + 16, 24, C.white)
    + txt(SCR_W / 2 + 16, y + 36, T.send, { size: 16, weight: 700, fill: C.white, anchor: "middle" }));

  if (anim) {
    // Snackbar de confirmation au-dessus de la barre de navigation
    const ty = SCR_H - 84 - 82;
    s += `<g id="rp-toast" opacity="0">
      <rect x="20" y="${ty}" width="${SCR_W - 40}" height="64" rx="16" fill="${C.navy}"/>
      <circle cx="54" cy="${ty + 32}" r="17" fill="${C.emerald}"/>
      ${icon("check", 43, ty + 21, 22, C.white)}
      ${txt(84, ty + 38, T.sent, { size: 14.5, weight: 700, fill: C.white })}
    </g>`;
  }

  s += bottomNav(1, S.nav);
  return s;
}

/** Écran Bouclier SMS. */
export function screenSms({ S = SCREEN_FR, anim = false } = {}) {
  const T = S.sms;
  let s = `<rect width="${SCR_W}" height="${SCR_H}" fill="#F7F9FE"/>`;
  s += appHeader(T.title, T.sub, { trailingIcon: "msg" });
  let y = 148;

  // Toggle actif
  s += grp(anim, "sms-toggle", card(24, y, SCR_W - 48, 84, { fill: "#E9F8F2", stroke: "#A9E4CE" })
    + icon("msg", 44, y + 28, 30, C.emerald)
    + txt(88, y + 38, T.onTitle, { size: 16, weight: 800 })
    + txt(88, y + 60, T.onSub, { size: 12.5, fill: C.muted })
    + `<rect${anim ? ` id="sms-track"` : ""} x="${SCR_W - 96}" y="${y + 28}" width="52" height="30" rx="15" fill="${C.emerald}"/>`
    + `<circle${anim ? ` id="sms-knob"` : ""} cx="${SCR_W - 58}" cy="${y + 43}" r="12" fill="${C.white}"/>`);
  y += 104;

  // SMS masqués (exemples)
  s += txt(28, y + 6, T.today, { size: 13.5, weight: 700, fill: C.muted });
  y += 22;
  T.spam.forEach(([l1, l2], i) => {
    s += grp(anim, `sms-c${i}`, card(24, y, SCR_W - 48, 96, { accent: C.coral })
      + icon("eyeOff", 44, y + 18, 24, C.coral)
      + txt(80, y + 30, T.masked, { size: 15, weight: 700 })
      + txt(80, y + 52, l1, { size: 13, fill: C.muted })
      + txt(80, y + 71, l2, { size: 13, fill: C.muted })
      + grp(anim, `sms-f${i}`, `<rect x="${SCR_W - 110}" y="${y + 14}" width="66" height="24" rx="12" fill="#FDECEC"/>`
        + txt(SCR_W - 77, y + 30.5, T.filtered, { size: 12, weight: 700, fill: C.coral, anchor: "middle" })));
    y += 116;
  });

  // SMS légitime passé
  s += grp(anim, "sms-ok", card(24, y, SCR_W - 48, 76, { accent: C.emerald })
    + icon("check", 44, y + 20, 24, C.emerald)
    + txt(80, y + 32, T.legitFrom, { size: 15, weight: 700 })
    + txt(80, y + 54, T.legitText, { size: 13, fill: C.muted }));

  s += bottomNav(3, S.nav);
  return s;
}
