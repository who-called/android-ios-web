/**
 * Import the ARCEP / operator block list (Saracroche-compatible JSON) into the
 * `patterns` table as source = "arcep".
 *
 * Expected JSON shape:
 *   { name, version, patterns: [ { name, action, pattern } ] }
 * where `pattern` uses trailing '#' wildcards (e.g. "33899######"),
 * and `action` is "block" or "identify" (mapped to status block | warn).
 *
 * URL is configurable via ARCEP_LIST_URL (.env). Run with `pnpm arcep`.
 */
import { fileURLToPath } from "node:url";
import { prisma } from "../db.js";
import { config } from "../config.js";

function mapStatus(action) {
  return action === "identify" ? "warn" : "block";
}

export async function importArcep(url = config.arcep.listUrl) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`ARCEP fetch failed: HTTP ${res.status}`);
  const json = await res.json();

  const items = Array.isArray(json.patterns) ? json.patterns : [];
  const listName = json.name ?? "ARCEP";

  let upserted = 0;
  for (const p of items) {
    const pattern = String(p.pattern ?? "").replace(/[^\d#]/g, "");
    if (!pattern || !pattern.includes("#")) continue; // keep only wildcard patterns

    await prisma.pattern.upsert({
      where: { pattern },
      create: {
        pattern,
        status: mapStatus(p.action),
        category: "telemarketing",
        source: "arcep",
        name: p.name ?? listName,
      },
      update: {
        status: mapStatus(p.action),
        name: p.name ?? listName,
      },
    });
    upserted += 1;
  }

  console.log(`ARCEP import: ${upserted} patterns from "${listName}".`);
  return upserted;
}

const isMain = process.argv[1] === fileURLToPath(import.meta.url);
if (isMain) {
  importArcep()
    .then(() => prisma.$disconnect())
    .catch(async (e) => {
      console.error(e);
      await prisma.$disconnect();
      process.exit(1);
    });
}
