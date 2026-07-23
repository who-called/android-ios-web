/**
 * Load all reports for a phone, each annotated with the reporting device's
 * current reputation weight. Shared by the /reports route and the scoring job.
 *
 * @param {import("@prisma/client").Prisma.TransactionClient | import("@prisma/client").PrismaClient} client
 * @param {string} phone
 * @returns {Promise<Array<{vote:string, createdAt:Date, weight:number}>>}
 */
export async function reportsWithWeights(client, phone) {
  const reports = await client.report.findMany({
    where: { phone },
    select: { vote: true, createdAt: true, deviceId: true },
  });
  if (reports.length === 0) return [];

  const deviceIds = [...new Set(reports.map((r) => r.deviceId))];
  const devices = await client.device.findMany({
    where: { deviceId: { in: deviceIds } },
    select: { deviceId: true, reputationWeight: true },
  });
  const weightById = new Map(devices.map((d) => [d.deviceId, d.reputationWeight]));

  return reports.map((r) => ({
    vote: r.vote,
    createdAt: r.createdAt,
    weight: weightById.get(r.deviceId) ?? 1.0,
  }));
}
