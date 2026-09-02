import { config } from "./config.js";

/**
 * who-called scoring engine.
 *
 * Computes a spam score (0..100) + status (block|warn|allow|unknown) from the
 * RAW reports of a number, combining:
 *
 *   1. Weighted vote ratio   — each report weighted by the reporting device's
 *                              reputation (anti-poisoning).
 *   2. Confidence            — ramps up with the (weighted) volume of reports.
 *   3. Time decay            — older reports count less (half-life), so stale
 *                              data fades and a number can recover.
 *   4. Velocity boost        — a sudden burst of spam reports (robocall in
 *                              progress) nudges the score up.
 *
 * Pure function of its inputs → easy to unit-test.
 *
 * @param {Array<{vote:string, createdAt:Date|string, weight?:number}>} reports
 * @param {{ now?: number }} [opts]
 * @returns {{ score:number, status:string, confidence:number, spam:number, legit:number,
 *             weightedSpam:number, weightedLegit:number, velocity:number }}
 */
export function scoreFromReports(reports, opts = {}) {
  const now = opts.now ?? Date.now();
  const s = config.scoring;

  // Optional seed prior (e.g. SIA): pre-weighted spam/legit evidence injected
  // before our raw reports so external sources and first-party votes share one
  // scale. Source-agnostic by design — the caller decides what the prior means.
  const prior = opts.prior ?? { weightedSpam: 0, weightedLegit: 0 };

  let weightedSpam = prior.weightedSpam ?? 0;
  let weightedLegit = prior.weightedLegit ?? 0;
  let recentSpam = 0; // raw spam reports within the velocity window
  let spam = 0;
  let legit = 0;

  for (const r of reports) {
    const ts = new Date(r.createdAt).getTime();
    const ageDays = Math.max(0, (now - ts) / 86_400_000);

    // Exponential time decay: weight halves every `halfLifeDays`.
    const decay = Math.pow(0.5, ageDays / s.halfLifeDays);

    // Device reputation weight (default 1.0), clamped to a sane range.
    const rep = clamp(r.weight ?? 1.0, 0, 2);

    const w = decay * rep;

    if (r.vote === "spam") {
      weightedSpam += w;
      spam += 1;
      if (ageDays <= s.velocityWindowDays) recentSpam += 1;
    } else if (r.vote === "legit") {
      weightedLegit += w;
      legit += 1;
    }
  }

  const weightedTotal = weightedSpam + weightedLegit;
  if (weightedTotal === 0) {
    return {
      score: 0,
      status: "unknown",
      confidence: 0,
      spam,
      legit,
      weightedSpam,
      weightedLegit,
      velocity: 0,
    };
  }

  const ratio = weightedSpam / weightedTotal; // 0..1
  const confidence = Math.min(1, weightedTotal / s.minReportsForBlock);

  // Velocity: fraction of spam reports that are very recent → small boost.
  const velocity = recentSpam >= s.velocityThreshold ? 1 : 0;
  const velocityBoost = velocity ? s.velocityBoost : 0;

  let score = Math.round(ratio * confidence * 100 + velocityBoost);
  score = clamp(score, 0, 100);

  const status = deriveStatus(score, weightedTotal, weightedSpam, weightedLegit);

  return {
    score,
    status,
    confidence: Math.round(confidence * 100),
    spam,
    legit,
    weightedSpam: round2(weightedSpam),
    weightedLegit: round2(weightedLegit),
    velocity,
  };
}

/**
 * Convert SIA aggregate counts into a decaying weighted prior for scoreFromReports.
 *
 * SIA has no per-vote timestamps, so `importedAt` is the synthetic age driving a
 * slow decay (sia.halfLifeDays). The whole prior is discounted by sia.trust (α)
 * vs first-party reports, and counts are capped (byte-saturated at 255). Neutral
 * votes lend a little legit weight. Result fades as fresh reports accumulate.
 *
 * @param {{pos:number, neg:number, neu:number, importedAt:Date|string|number}} seed
 * @param {{now?:number}} [opts]
 * @returns {{weightedSpam:number, weightedLegit:number}}
 */
export function siaPrior(seed, opts = {}) {
  const now = opts.now ?? Date.now();
  const c = config.sia;
  const ageDays = Math.max(0, (now - new Date(seed.importedAt).getTime()) / 86_400_000);
  const decay = Math.pow(0.5, ageDays / c.halfLifeDays);
  const f = c.trust * decay;
  return {
    weightedSpam: round2(f * capSiaCount(seed.neg)),
    weightedLegit: round2(f * (capSiaCount(seed.pos) + c.neutralWeight * capSiaCount(seed.neu))),
  };
}

/** Cap a SIA aggregate count (byte-saturated source). */
export function capSiaCount(n) {
  return Math.min(Math.max(n ?? 0, 0), config.sia.maxCount);
}

/**
 * Option B display counts: fold SIA pos/neg into the shown report counters so a
 * first community vote never replaces a seed-only history with "1".
 *
 * @param {{spam?:number, legit?:number}} scored  community (or raw) counts
 * @param {{pos?:number, neg?:number}|null|undefined} seed
 */
export function displayedReportCounts(scored, seed) {
  return {
    spam: (scored.spam ?? 0) + (seed ? capSiaCount(seed.neg) : 0),
    legit: (scored.legit ?? 0) + (seed ? capSiaCount(seed.pos) : 0),
  };
}

function deriveStatus(score, weightedTotal, weightedSpam, weightedLegit) {
  const s = config.scoring;
  if (score >= s.blockThreshold && weightedTotal >= s.minReportsForBlock) {
    return "block";
  }
  if (score >= s.warnThreshold) return "warn";
  if (weightedLegit > weightedSpam) return "allow";
  // Uncontradicted spam evidence is "potential spam" — don't mask it behind
  // "unknown" (reserved for truly no data). But it takes more than ONE
  // anonymous voice (minWeightForWarn ≈ two fresh devices): a lone report
  // stays visible as a count without flagging the number for everyone.
  if (weightedSpam >= s.minWeightForWarn && weightedLegit === 0) return "warn";
  return "unknown";
}

/**
 * Update a device's reputation after a vote, based on whether it agreed with the
 * community consensus for that number. Disagreeing with a strong consensus
 * (e.g. voting "legit" on an obvious spam number) erodes reputation → an
 * attacker spamming bad votes loses influence over time (anti-poisoning).
 *
 * @param {number} currentWeight
 * @param {"spam"|"legit"} vote
 * @param {{weightedSpam:number, weightedLegit:number}} consensus  consensus BEFORE this vote
 * @returns {number} new reputation weight (clamped 0.1..2.0)
 */
export function updateReputation(currentWeight, vote, consensus) {
  const total = consensus.weightedSpam + consensus.weightedLegit;
  // No consensus yet → neutral (no change).
  if (total < config.scoring.consensusMinWeight) return clampRep(currentWeight);

  const consensusIsSpam = consensus.weightedSpam > consensus.weightedLegit;
  const strength = Math.abs(consensus.weightedSpam - consensus.weightedLegit) / total; // 0..1

  const agrees = (vote === "spam") === consensusIsSpam;
  const delta = (agrees ? +1 : -1) * config.scoring.reputationStep * strength;

  return clampRep(currentWeight + delta);
}

function clampRep(w) {
  return clamp(w, 0.1, 2.0);
}

function clamp(v, lo, hi) {
  return Math.max(lo, Math.min(hi, v));
}

function round2(v) {
  return Math.round(v * 100) / 100;
}

/**
 * Backwards-compatible simple scorer (counts only). Kept for callers that don't
 * have raw reports; prefer scoreFromReports for production paths.
 */
export function computeScore({ spam, legit }) {
  const total = spam + legit;
  if (total === 0) return { score: 0, status: "unknown" };
  const ratio = spam / total;
  const confidence = Math.min(1, total / config.scoring.minReportsForBlock);
  const score = Math.round(ratio * confidence * 100);
  let status = "unknown";
  if (score >= config.scoring.blockThreshold && total >= config.scoring.minReportsForBlock) {
    status = "block";
  } else if (score >= config.scoring.warnThreshold) {
    status = "warn";
  } else if (legit > spam) {
    status = "allow";
  }
  return { score, status };
}
