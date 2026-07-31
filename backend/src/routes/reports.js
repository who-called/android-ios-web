import { Router } from "express";
import { z } from "zod";
import { prisma } from "../db.js";
import { config } from "../config.js";
import { scoreFromReports, updateReputation, siaPrior, displayedReportCounts } from "../scoring.js";
import { reportsWithWeights } from "../reportWeights.js";
import { normalizePhone } from "../phone.js";
import { REPORT_CATEGORIES } from "../categories.js";
import { resolveCategory } from "../jobs/scoreJob.js";

export const reportsRouter = Router();

const reportSchema = z.object({
  phone: z.string().min(3),
  deviceId: z.string().min(8),
  vote: z.enum(["spam", "legit"]),
  // Canonical categories (see categories.js). Accept missing OR null (clients
  // send category:null when voting "legit").
  category: z.enum(REPORT_CATEGORIES).nullish(),
  locale: z.string().default("fr"),
});

/**
 * POST /api/v1/reports
 * Submit an anonymous report (spam | legit) for a phone number.
 */
reportsRouter.post("/", async (req, res) => {
  const parsed = reportSchema.safeParse(req.body);
  if (!parsed.success) {
    return res
      .status(400)
      .json({ error: "invalid_request", detail: parsed.error.issues });
  }

  const { deviceId, vote, category, locale } = parsed.data;
  const phone = normalizePhone(parsed.data.phone);
  if (!phone) {
    return res.status(400).json({ error: "invalid_phone" });
  }

  // --- Anti-abuse: per-device daily rate limit ---
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const device = await prisma.device.upsert({
    where: { deviceId },
    create: { deviceId, reportsToday: 0, lastReportDate: today },
    update: {},
  });

  const lastDate = new Date(device.lastReportDate);
  lastDate.setHours(0, 0, 0, 0);
  const reportsToday =
    lastDate.getTime() === today.getTime() ? device.reportsToday : 0;

  if (reportsToday >= config.antiAbuse.maxReportsPerDevicePerDay) {
    return res.status(429).json({ error: "rate_limited" });
  }

  // --- Persist report + recompute weighted score in a transaction ---
  const result = await prisma.$transaction(async (tx) => {
    // Number must exist before the Report (FK reports_phone_fkey).
    await tx.number.upsert({
      where: { phone },
      create: { phone, category: category ?? "unknown" },
      update: { lastReportedAt: new Date() },
    });

    // Dedup: one device = one vote per number. Did this device already vote?
    const existingVote = await tx.report.findFirst({
      where: { phone, deviceId },
      select: { id: true, vote: true },
    });

    // SIA seed must participate in both consensus (reputation) and the
    // materialized counters — otherwise a first community vote would wipe the
    // seed-only history (e.g. 31 → 1) until the next score job.
    const seed = await tx.siaSeed.findUnique({ where: { phone } });
    const prior = seed ? siaPrior(seed) : undefined;

    // Load existing reports (with each device's reputation) to compute consensus
    // BEFORE this new vote — used for the anti-poisoning reputation update.
    const priorReports = await reportsWithWeights(tx, phone);
    const priorScore = scoreFromReports(priorReports, { prior });

    // Update this device's reputation based on agreement with prior consensus.
    const newWeight = updateReputation(
      device.reputationWeight,
      vote,
      { weightedSpam: priorScore.weightedSpam, weightedLegit: priorScore.weightedLegit }
    );

    if (existingVote) {
      // Replace the device's previous vote for this number (no duplicate, no
      // rate-limit consumption for a re-vote).
      await tx.report.update({
        where: { id: existingVote.id },
        data: { vote, category, locale, createdAt: new Date() },
      });
    } else {
      await tx.report.create({ data: { phone, deviceId, vote, category, locale } });
    }
    await tx.device.update({
      where: { deviceId },
      data: {
        // Re-votes don't consume the daily quota; only brand-new reports do.
        reportsToday: existingVote ? reportsToday : reportsToday + 1,
        lastReportDate: today,
        reputationWeight: newWeight,
      },
    });

    // Recompute the number's score from ALL reports + SIA prior (option B counts).
    const allReports = await reportsWithWeights(tx, phone);
    const scored = scoreFromReports(allReports, { prior });
    const counts = displayedReportCounts(scored, seed);

    return tx.number.update({
      where: { phone },
      data: {
        reportCountSpam: counts.spam,
        reportCountLegit: counts.legit,
        spamScore: scored.score,
        status: scored.status,
        category: resolveCategory(allReports, seed),
        source: seed ? "mixed" : "community",
      },
    });
  });

  return res.status(201).json({
    phone: result.phone,
    spamScore: result.spamScore,
    status: result.status,
  });
});
