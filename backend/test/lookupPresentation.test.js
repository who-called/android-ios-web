import assert from "node:assert/strict";
import { test } from "node:test";
import {
  confidenceLevel,
  evidenceTimeline,
  findMatchingPattern,
  presentationSource,
  reasonBreakdown,
} from "../src/lookupPresentation.js";
import { REPORT_CATEGORIES } from "../src/categories.js";

test("findMatchingPattern selects the most specific matching pattern", () => {
  const match = findMatchingPattern("33899123456", [
    { pattern: "338########", name: "broad" },
    { pattern: "33899######", name: "specific" },
    { pattern: "33162######", name: "other" },
  ]);
  assert.equal(match?.name, "specific");
});

test("presentationSource distinguishes official and statistical evidence", () => {
  assert.equal(
    presentationSource({ hasStatisticalEvidence: false, hasOfficialPattern: false }),
    "none",
  );
  assert.equal(
    presentationSource({ hasStatisticalEvidence: true, hasOfficialPattern: false }),
    "community",
  );
  assert.equal(
    presentationSource({ hasStatisticalEvidence: false, hasOfficialPattern: true }),
    "arcep",
  );
  assert.equal(
    presentationSource({ hasStatisticalEvidence: true, hasOfficialPattern: true }),
    "mixed",
  );
});

test("evidenceTimeline counts the SIA aggregate at its import date", () => {
  const now = Date.parse("2026-07-31T10:00:00Z");
  const day = 86_400_000;
  const { frequency, firstReportedAt, lastReportedAt } = evidenceTimeline(
    [{ createdAt: new Date(now - 2 * day) }],
    { pos: 29, neg: 1, importedAt: new Date(now - 37 * day) },
    now,
  );

  // Our single report lands in 7d+; the 30 SIA votes only in the 1-year window.
  assert.deepEqual(frequency, { last24h: 0, last7d: 1, last30d: 1, last1y: 31 });
  assert.equal(firstReportedAt.getTime(), now - 37 * day); // SIA import is the origin
  assert.equal(lastReportedAt.getTime(), now - 2 * day);
});

test("evidenceTimeline has no history without any evidence", () => {
  const t = evidenceTimeline([], null, Date.now());
  assert.deepEqual(t.frequency, { last24h: 0, last7d: 0, last30d: 0, last1y: 0 });
  assert.equal(t.firstReportedAt, null);
  assert.equal(t.lastReportedAt, null);
});

test("reasonBreakdown folds the SIA negative aggregate into its category", () => {
  const reasons = reasonBreakdown(REPORT_CATEGORIES, [{ category: "scam" }], 4, "telemarketing");
  assert.equal(reasons.scam, 1);
  assert.equal(reasons.telemarketing, 4);
  // A seed with no negative votes must not invent a reason.
  assert.equal(reasonBreakdown(REPORT_CATEGORIES, [], 0, "scam").scam, 0);
  // Absent or non-canonical categories collapse into the catch-all bucket.
  assert.equal(reasonBreakdown(REPORT_CATEGORIES, [{ category: null }]).unknown, 1);
  assert.equal(reasonBreakdown(REPORT_CATEGORIES, [], 3, "legit").unknown, 3);
});

test("confidenceLevel uses stable cross-platform thresholds", () => {
  assert.equal(confidenceLevel(0), "none");
  assert.equal(confidenceLevel(20), "low");
  assert.equal(confidenceLevel(60), "medium");
  assert.equal(confidenceLevel(100), "high");
  assert.equal(confidenceLevel(null, true), "official");
});
