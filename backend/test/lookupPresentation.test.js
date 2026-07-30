import assert from "node:assert/strict";
import { test } from "node:test";
import {
  confidenceLevel,
  findMatchingPattern,
  presentationSource,
} from "../src/lookupPresentation.js";

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

test("confidenceLevel uses stable cross-platform thresholds", () => {
  assert.equal(confidenceLevel(0), "none");
  assert.equal(confidenceLevel(20), "low");
  assert.equal(confidenceLevel(60), "medium");
  assert.equal(confidenceLevel(100), "high");
  assert.equal(confidenceLevel(null, true), "official");
});
