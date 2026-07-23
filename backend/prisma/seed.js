/**
 * Seed dev data so the apps' detail screen shows the difference between
 * community-scored numbers and ARCEP-listed entries.
 * FR-first (E.164 without '+').
 */
import { prisma } from "../src/db.js";
import { computeScore } from "../src/scoring.js";

const communityNumbers = [
  { phone: "33612345678", spam: 12, legit: 0, category: "scam" },
  { phone: "33162987654", spam: 8, legit: 1, category: "robocall" },
  { phone: "33970650000", spam: 3, legit: 2, category: "unknown" },
];

// ARCEP-listed example: a real démarchage range prefix is blocked by the official
// list regardless of community reports. Shown with source = "arcep".
const arcepNumbers = [
  { phone: "33899123456", category: "telemarketing" },
];

const arcepPatterns = [
  { pattern: "33899######", name: "Démarchage ARCEP", status: "block" },
  { pattern: "33162######", name: "Numéros opérateurs ARCEP", status: "warn" },
];

async function run() {
  for (const s of communityNumbers) {
    const { score, status } = computeScore({ spam: s.spam, legit: s.legit });
    await prisma.number.upsert({
      where: { phone: s.phone },
      create: {
        phone: s.phone,
        reportCountSpam: s.spam,
        reportCountLegit: s.legit,
        spamScore: score,
        status,
        category: s.category,
        source: "community",
      },
      update: {
        reportCountSpam: s.spam,
        reportCountLegit: s.legit,
        spamScore: score,
        status,
        category: s.category,
        source: "community",
      },
    });
  }

  for (const a of arcepNumbers) {
    await prisma.number.upsert({
      where: { phone: a.phone },
      create: {
        phone: a.phone,
        spamScore: 100,
        status: "block",
        category: a.category,
        source: "arcep",
      },
      update: { spamScore: 100, status: "block", source: "arcep", category: a.category },
    });
  }

  for (const p of arcepPatterns) {
    await prisma.pattern.upsert({
      where: { pattern: p.pattern },
      create: { pattern: p.pattern, status: p.status, category: "telemarketing", source: "arcep", name: p.name },
      update: { status: p.status, name: p.name },
    });
  }

  console.log(
    `Seeded ${communityNumbers.length} community + ${arcepNumbers.length} ARCEP numbers, ${arcepPatterns.length} patterns.`
  );
}

run()
  .then(() => prisma.$disconnect())
  .catch(async (e) => {
    console.error(e);
    await prisma.$disconnect();
    process.exit(1);
  });
