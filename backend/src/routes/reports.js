import { Router } from "express";
import { z } from "zod";
import { prisma } from "../db.js";
import { config } from "../config.js";
import { scoreFromReports, updateReputation, siaPrior } from "../scoring.js";
import { reportsWithWeights } from "../reportWeights.js";
import { rescoreNumber } from "../rescore.js";
import { normalizePhone } from "../phone.js";
import { REPORT_CATEGORIES } from "../categories.js";
import { fixedWindowCounter } from "../middleware/rateLimit.js";

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

// Anti-Sybil: `deviceId` is free-form, so one IP could mint N fresh ids and
// vote a number straight into "block". Bound how many NEW devices a single IP
// may create per day; devices already known are never affected, so a
// household or carrier NAT keeps working — only the (N+1)th brand-new install
// of the day waits until tomorrow for its first report.
const newDevicesPerIp = fixedWindowCounter({
  windowMs: 86_400_000,
  max: config.antiAbuse.maxNewDevicesPerIpPerDay,
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

  let device = await prisma.device.findUnique({ where: { deviceId } });
  if (!device) {
    const { allowed, retryAfterSec } = newDevicesPerIp.hit(req.ip ?? "unknown");
    if (!allowed) {
      res.setHeader("Retry-After", String(retryAfterSec));
      return res.status(429).json({ error: "rate_limited" });
    }
    device = await prisma.device.upsert({
      where: { deviceId },
      create: { deviceId, reportsToday: 0, lastReportDate: today },
      update: {},
    });
  }

  const lastDate = new Date(device.lastReportDate);
  lastDate.setHours(0, 0, 0, 0);
  const sameDay = lastDate.getTime() === today.getTime();
  const reportsToday = sameDay ? device.reportsToday : 0;

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
    const voteChanged = !existingVote || existingVote.vote !== vote;

    // Reputation moves only when the device takes a (new) position. A re-vote
    // that repeats the same opinion is free server-side, so without this guard
    // it was a farming loop: agree once, re-send 10× → weight 2.0.
    let newWeight = device.reputationWeight;
    if (voteChanged) {
      // SIA seed participates in the consensus like in the score job.
      const seed = await tx.siaSeed.findUnique({ where: { phone } });
      const prior = seed ? siaPrior(seed) : undefined;
      // Consensus BEFORE this vote drives the anti-poisoning update.
      const priorReports = await reportsWithWeights(tx, phone);
      const priorScore = scoreFromReports(priorReports, { prior });
      newWeight = updateReputation(device.reputationWeight, vote, {
        weightedSpam: priorScore.weightedSpam,
        weightedLegit: priorScore.weightedLegit,
      });
    }

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
        // Relative increment, so two concurrent reports can't overwrite each
        // other's count (the read above happens outside this transaction).
        reportsToday: existingVote ? undefined : sameDay ? { increment: 1 } : 1,
        lastReportDate: today,
        reputationWeight: newWeight,
      },
    });

    // Recompute the number's score from ALL reports + SIA prior.
    return rescoreNumber(tx, phone);
  });

  return res.status(201).json({
    phone: result.phone,
    spamScore: result.spamScore,
    status: result.status,
  });
});
