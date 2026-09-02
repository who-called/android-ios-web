import { Router } from "express";
import { z } from "zod";
import { timingSafeEqual } from "node:crypto";
import { prisma } from "../db.js";
import { config } from "../config.js";
import { normalizePhone } from "../phone.js";
import { rescoreNumber } from "../rescore.js";

export const privacyRouter = Router();

/**
 * RGPD / privacy endpoints.
 *
 * DELETE /api/v1/privacy/number/:phone   (operator only — Bearer PRIVACY_ADMIN_TOKEN)
 *   Right to erasure for a third party: removes a number and all its reports
 *   from the community database. (ARCEP patterns are official ranges, untouched.)
 *   Requests arrive by email (privacy@who-called.com) and are executed by the
 *   operator; left open, this let any spammer wipe their own community history.
 *
 * DELETE /api/v1/privacy/device/:deviceId
 *   Lets a user erase all reports they made from a device, and the device record.
 */

function isOperator(req) {
  const expected = config.privacy.adminToken;
  if (!expected) return false;
  const given = (req.headers.authorization ?? "").replace(/^Bearer\s+/i, "");
  const a = Buffer.from(given);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

privacyRouter.delete("/number/:phone", async (req, res) => {
  if (!config.privacy.adminToken) return res.status(404).json({ error: "not_found" });
  if (!isOperator(req)) return res.status(401).json({ error: "unauthorized" });

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

    // Full re-score (status + score, not just counts) so a number this device
    // alone pushed to block/warn is released now, not at the next 6 h job.
    for (const phone of phones) await rescoreNumber(tx, phone);

    return { removedReports: removed.count, affectedNumbers: phones.length };
  });

  return res.json({ deviceId, ...result });
});
