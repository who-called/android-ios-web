import { Router } from "express";
import { z } from "zod";
import { prisma } from "../db.js";
import { dailyPuzzles, trainingPuzzle } from "../game/puzzleGen.js";
import { rateLimit } from "../middleware/rateLimit.js";

export const gameRouter = Router();

// Two games share this router, each with its own leaderboard (keyed by
// `gameType`). Anti-cheat guardrails are proportionate (fun games, no stakes):
//  - day is the SERVER's epoch-day (client day accepted only within ±1)
//  - one row per device per day PER GAME (compound PK) — we keep the BEST score
//  - score is clamped to a per-game ceiling AND to what "waves" allow
//    (DEFENSE: waves survived · TRACE: grids solved) so a run can't yield an
//    absurd score for very little progress
//  - the board exposes rank/median (robust) so a bogus score can't skew others
//  - the client also caps ranked attempts to 3/day; here we just keep the max
const GAME_CONFIG = {
  // Endless arcade shmup.
  defense: { maxWaves: 500, perWaveCeil: 4_000, ceil: 5_000_000 },
  // 5-grid daily deduction sprint (grids 5×5→9×9, ~6k max per grid).
  trace: { maxWaves: 5, perWaveCeil: 6_000, ceil: 100_000 },
};
const gameOf = (v) => (typeof v === "string" && GAME_CONFIG[v] ? v : "defense");

const scoreSchema = z.object({
  deviceId: z.string().min(8),
  score: z.number().int().min(0),
  // DEFENSE: waves survived · TRACE: grids solved. Clamped per-game below.
  waves: z.number().int().min(1).max(500).default(1),
  // Client's local epoch-day; validated ±1 vs server to group fairly by the day
  // the player actually played.
  day: z.number().int().optional(),
  game: z.string().optional(),
});

/**
 * POST /api/v1/game/score
 * Body: { deviceId, score, waves, day?, game? } → records today's best for that
 * game and returns the day's anonymous leaderboard position
 * { day, game, players, bestScore, medianScore, yourScore, rank, topPercent }.
 */
gameRouter.post("/score", rateLimit({ windowMs: 86_400_000, max: 60 }), async (req, res) => {
  const parsed = scoreSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: "invalid_body" });

  const { deviceId } = parsed.data;
  const game = gameOf(parsed.data.game);
  const cfg = GAME_CONFIG[game];
  const waves = Math.min(cfg.maxWaves, Math.max(1, parsed.data.waves));
  // Sanity: clamp score to a plausible ceiling AND to what progress allows.
  const score = Math.max(0, Math.min(cfg.ceil, waves * cfg.perWaveCeil, parsed.data.score));

  const serverDay = Math.floor(Date.now() / 86_400_000);
  const day =
    parsed.data.day != null && Math.abs(parsed.data.day - serverDay) <= 1
      ? parsed.data.day
      : serverDay;

  const key = { day_deviceId_gameType: { day, deviceId, gameType: game } };
  const existing = await prisma.gameScore.findUnique({ where: key });
  const best = existing ? Math.max(existing.score, score) : score;
  if (!existing || score > existing.score) {
    await prisma.gameScore.upsert({
      where: key,
      create: { day, deviceId, gameType: game, score, waves },
      update: { score, waves },
    });
  }

  const [players, agg, rankAbove, medianRow] = await Promise.all([
    prisma.gameScore.count({ where: { day, gameType: game } }),
    prisma.gameScore.aggregate({ where: { day, gameType: game }, _max: { score: true } }),
    prisma.gameScore.count({ where: { day, gameType: game, score: { gt: best } } }),
    prisma.$queryRaw`SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY score) AS m FROM game_scores WHERE day = ${day} AND game_type = ${game}`,
  ]);

  const rank = rankAbove + 1;
  return res.json({
    day,
    game,
    players,
    bestScore: agg._max.score ?? best,
    medianScore: Math.round(Number(medianRow?.[0]?.m ?? best)),
    yourScore: best,
    rank,
    topPercent: Math.max(1, Math.round((rank / Math.max(players, 1)) * 100)),
  });
});

/**
 * GET /api/v1/game/leaderboard?period=day|week|all&day=<epochDay>&game=<id>&deviceId=<id>
 * Anonymous board for a period (best score per device in range): top + your rank.
 */
gameRouter.get("/leaderboard", async (req, res) => {
  const serverDay = Math.floor(Date.now() / 86_400_000);
  const period = ["day", "week", "all"].includes(req.query.period) ? req.query.period : "day";
  const game = gameOf(req.query.game);
  const reqDay = parseInt(req.query.day, 10);
  const ref = Number.isFinite(reqDay) ? Math.min(serverDay, Math.max(serverDay - 60, reqDay)) : serverDay;
  const d0 = period === "all" ? 0 : period === "week" ? ref - 6 : ref;
  const d1 = ref;
  const deviceId = typeof req.query.deviceId === "string" ? req.query.deviceId : null;

  // Best score per device within the range, for this game, ranked.
  const rows = await prisma.$queryRaw`
    SELECT device_id, MAX(score)::int AS score, MAX(waves)::int AS waves
    FROM game_scores WHERE game_type = ${game} AND day BETWEEN ${d0} AND ${d1}
    GROUP BY device_id ORDER BY score DESC`;
  const players = rows.length;
  const top = rows.slice(0, 10).map((r, i) => ({ rank: i + 1, score: Number(r.score), waves: Number(r.waves) }));
  const bestScore = players ? Number(rows[0].score) : 0;
  const medianScore = players ? Number(rows[Math.floor((players - 1) / 2)].score) : 0;

  let you = null;
  if (deviceId) {
    const idx = rows.findIndex((r) => r.device_id === deviceId);
    if (idx >= 0) {
      const rank = idx + 1;
      you = { score: Number(rows[idx].score), waves: Number(rows[idx].waves), rank, topPercent: Math.max(1, Math.round((rank / Math.max(players, 1)) * 100)) };
    }
  }
  return res.json({ day: ref, game, period, players, bestScore, medianScore, top, you });
});

/**
 * GET /api/v1/game/history?deviceId=<id>&game=<id>&days=14
 * A device's own recent results for a game with per-day rank.
 */
gameRouter.get("/history", async (req, res) => {
  const deviceId = typeof req.query.deviceId === "string" ? req.query.deviceId : null;
  const game = gameOf(req.query.game);
  if (!deviceId) return res.json({ game, entries: [] });
  const n = Math.min(30, Math.max(1, parseInt(req.query.days, 10) || 14));
  const rows = await prisma.gameScore.findMany({ where: { deviceId, gameType: game }, orderBy: { day: "desc" }, take: n });
  const entries = [];
  for (const r of rows) {
    const [players, above] = await Promise.all([
      prisma.gameScore.count({ where: { day: r.day, gameType: game } }),
      prisma.gameScore.count({ where: { day: r.day, gameType: game, score: { gt: r.score } } }),
    ]);
    const rank = above + 1;
    entries.push({ day: r.day, score: r.score, waves: r.waves, rank, players, topPercent: Math.max(1, Math.round((rank / Math.max(players, 1)) * 100)) });
  }
  return res.json({ game, entries });
});

/**
 * GET /api/v1/game/puzzle?day=<epochDay>&game=trace
 * The day's 5-grid sprint, identical for every player. Server-authoritative so
 * clients never generate (fair board, no cross-platform RNG drift).
 * → { day, game, grids: [{ n, region:number[][], solution:number[] }] }.
 */
gameRouter.get("/puzzle", (req, res) => {
  const game = gameOf(req.query.game);
  if (game !== "trace") return res.status(404).json({ error: "no_puzzle" });
  // Unranked "training" mode: a single ad-hoc grid of the requested size.
  if (req.query.mode === "training") {
    const size = Math.min(9, Math.max(5, parseInt(req.query.size, 10) || 6));
    const seed = (parseInt(req.query.seed, 10) || 1) >>> 0;
    return res.json({ game, mode: "training", grids: [trainingPuzzle(size, seed)] });
  }
  const serverDay = Math.floor(Date.now() / 86_400_000);
  const reqDay = parseInt(req.query.day, 10);
  // Allow the client's local "today" (±1 for timezones) so the grids match the
  // day the score is bucketed under.
  const day = Number.isFinite(reqDay) ? Math.min(serverDay + 1, Math.max(serverDay - 60, reqDay)) : serverDay;
  return res.json({ day, game, grids: dailyPuzzles(day) });
});
