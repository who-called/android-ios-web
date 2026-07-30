import assert from "node:assert/strict";
import { test } from "node:test";
import { scoreFromReports, updateReputation, siaPrior } from "../src/scoring.js";

const DAY = 86_400_000;
const now = Date.now();

function reps(n, vote, ageDays = 0, weight = 1.0) {
  return Array.from({ length: n }, () => ({
    vote,
    createdAt: new Date(now - ageDays * DAY),
    weight,
  }));
}

test("no reports → unknown, score 0", () => {
  const r = scoreFromReports([], { now });
  assert.equal(r.score, 0);
  assert.equal(r.status, "unknown");
  assert.equal(r.confidence, 0);
});

test("many fresh spam reports → block", () => {
  const r = scoreFromReports(reps(10, "spam"), { now });
  assert.ok(r.score >= 85, `score=${r.score}`);
  assert.equal(r.status, "block");
});

test("mixed reports around the warn band (no velocity burst)", () => {
  // Reports a few days old → no velocity boost; ratio 0.75 → warn band.
  const r = scoreFromReports([...reps(6, "spam", 5), ...reps(2, "legit", 5)], { now });
  assert.ok(r.score >= 60 && r.score < 85, `score=${r.score}`);
  assert.equal(r.status, "warn");
});

test("legit majority → allow", () => {
  const r = scoreFromReports([...reps(1, "spam"), ...reps(5, "legit")], { now });
  assert.equal(r.status, "allow");
});

test("single uncontradicted spam report → warn (potential spam, not unknown)", () => {
  // 1 spam, 0 legit → low confidence → score < 60, but a negative signal exists.
  const r = scoreFromReports(reps(1, "spam"), { now });
  assert.ok(r.score < 60, `score=${r.score}`);
  assert.equal(r.status, "warn");
  assert.ok(r.confidence > 0 && r.confidence < 100, `confidence=${r.confidence}`);
});

test("confidence reflects weighted evidence volume", () => {
  const low = scoreFromReports(reps(1, "legit"), { now });
  const high = scoreFromReports(reps(5, "legit"), { now });
  assert.ok(low.confidence < high.confidence);
  assert.equal(high.confidence, 100);
});

test("balanced 1 spam / 1 legit → unknown (no clear signal either way)", () => {
  const r = scoreFromReports([...reps(1, "spam"), ...reps(1, "legit")], { now });
  assert.equal(r.status, "unknown");
});

test("time decay: old spam reports score lower than fresh ones", () => {
  const fresh = scoreFromReports(reps(8, "spam", 0), { now });
  const old = scoreFromReports(reps(8, "spam", 90), { now }); // 3 half-lives
  assert.ok(old.score < fresh.score, `old=${old.score} fresh=${fresh.score}`);
});

test("velocity boost: burst of recent spam raises score", () => {
  // Same counts, but recent burst gets the boost.
  const spread = scoreFromReports(reps(6, "spam", 10), { now });
  const burst = scoreFromReports(reps(6, "spam", 0), { now });
  assert.ok(burst.score >= spread.score, `burst=${burst.score} spread=${spread.score}`);
});

test("low-reputation device contributes less", () => {
  const trusted = scoreFromReports(reps(6, "spam", 0, 1.5), { now });
  const untrusted = scoreFromReports(reps(6, "spam", 0, 0.2), { now });
  assert.ok(untrusted.score < trusted.score, `untrusted=${untrusted.score} trusted=${trusted.score}`);
});

test("anti-poisoning: disagreeing with strong consensus erodes reputation", () => {
  const consensus = { weightedSpam: 10, weightedLegit: 0 }; // clearly spam
  const next = updateReputation(1.0, "legit", consensus); // attacker says legit
  assert.ok(next < 1.0, `weight=${next}`);
});

test("reputation: agreeing with consensus increases weight", () => {
  const consensus = { weightedSpam: 10, weightedLegit: 0 };
  const next = updateReputation(1.0, "spam", consensus);
  assert.ok(next > 1.0, `weight=${next}`);
});

test("reputation clamped to [0.1, 2.0]", () => {
  const consensus = { weightedSpam: 10, weightedLegit: 0 };
  let w = 2.0;
  for (let i = 0; i < 50; i++) w = updateReputation(w, "spam", consensus);
  assert.ok(w <= 2.0);
  let lo = 0.1;
  for (let i = 0; i < 50; i++) lo = updateReputation(lo, "legit", consensus);
  assert.ok(lo >= 0.1);
});

test("no consensus yet → reputation unchanged", () => {
  const next = updateReputation(1.0, "spam", { weightedSpam: 0.5, weightedLegit: 0 });
  assert.equal(next, 1.0);
});

// --- SIA seed harmonization (decaying prior) -------------------------------

test("siaPrior: fresh import, heavy negatives → strong spam prior", () => {
  const p = siaPrior({ pos: 0, neg: 200, neu: 0, importedAt: new Date(now) }, { now });
  // trust 0.6 × decay≈1 × 200 = 120
  assert.ok(p.weightedSpam > 100, `weightedSpam=${p.weightedSpam}`);
  assert.equal(p.weightedLegit, 0);
});

test("siaPrior: old import decays toward zero", () => {
  const fresh = siaPrior({ pos: 0, neg: 100, neu: 0, importedAt: new Date(now) }, { now });
  const old = siaPrior(
    { pos: 0, neg: 100, neu: 0, importedAt: new Date(now - 360 * DAY) }, // 2 half-lives
    { now }
  );
  assert.ok(old.weightedSpam < fresh.weightedSpam / 3, `old=${old.weightedSpam} fresh=${fresh.weightedSpam}`);
});

test("SIA prior alone (no reports) can drive block on high volume", () => {
  const prior = siaPrior({ pos: 0, neg: 255, neu: 0, importedAt: new Date(now) }, { now });
  const r = scoreFromReports([], { now, prior });
  assert.equal(r.status, "block");
  assert.equal(r.score, 100);
});

test("fresh community legit votes override a stale SIA spam prior", () => {
  const stalePrior = siaPrior(
    { pos: 0, neg: 50, neu: 0, importedAt: new Date(now - 540 * DAY) }, // 3 half-lives → /8
    { now }
  );
  const r = scoreFromReports(reps(8, "legit"), { now, prior: stalePrior });
  assert.equal(r.status, "allow", `status=${r.status} score=${r.score}`);
});
