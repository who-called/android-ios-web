import { Router } from "express";
import { z } from "zod";
import { prisma } from "../db.js";
import { normalizePhone } from "../phone.js";

export const privacyRouter = Router();

/**
 * RGPD / privacy endpoints.
 *
 * DELETE /api/v1/privacy/number/:phone
 *   Right to erasure for a third party: removes a number and all its reports
 *   from the community database. (ARCEP patterns are official ranges, untouched.)
 *
 * DELETE /api/v1/privacy/device/:deviceId
 *   Lets a user erase all reports they made from a device, and the device record.
 */

privacyRouter.delete("/number/:phone", async (req, res) => {
  const phone = normalizePhone(req.params.phone);
  if (!phone) return res.status(400).json({ error: "invalid_phone" });

  // Cascade removes the reports (FK onDelete: Cascade).
  const deleted = await prisma.number.deleteMany({ where: { phone } });

  return res.json({ phone, deleted: deleted.count > 0 });
});

const deviceSchema = z.object({ deviceId: z.string().min(8) });

privacyRouter.delete("/device/:deviceId", async (req, res) => {
  const parsed = deviceSchema.safeParse({ deviceId: req.params.deviceId });
  if (!parsed.success) return res.status(400).json({ error: "invalid_request" });
  const { deviceId } = parsed.data;

  const result = await prisma.$transaction(async (tx) => {
    // Collect phones this device reported, so we can re-score them after removal.
    const reports = await tx.report.findMany({
      where: { deviceId },
      select: { phone: true },
    });
    const phones = [...new Set(reports.map((r) => r.phone))];

    const removed = await tx.report.deleteMany({ where: { deviceId } });
    await tx.device.deleteMany({ where: { deviceId } });
    await tx.gameScore.deleteMany({ where: { deviceId } }); // RGPD: drop game scores too

    // Recompute counters for affected numbers (best-effort, counts only).
    for (const phone of phones) {
      const spam = await tx.report.count({ where: { phone, vote: "spam" } });
      const legit = await tx.report.count({ where: { phone, vote: "legit" } });
      await tx.number.updateMany({
        where: { phone },
        data: { reportCountSpam: spam, reportCountLegit: legit },
      });
    }

    return { removedReports: removed.count, affectedNumbers: phones.length };
  });

  return res.json({ deviceId, ...result });
});
