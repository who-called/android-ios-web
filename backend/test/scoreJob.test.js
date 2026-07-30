import assert from "node:assert/strict";
import { test } from "node:test";
import { resolveCategory } from "../src/jobs/scoreJob.js";

test("community spam categories survive weighted report loading", () => {
  const category = resolveCategory(
    [
      { vote: "spam", category: "scam" },
      { vote: "spam", category: "scam" },
      { vote: "spam", category: "telemarketing" },
      { vote: "legit", category: null },
    ],
    null,
  );
  assert.equal(category, "scam");
});

test("SIA category remains the fallback without community spam reasons", () => {
  const category = resolveCategory(
    [{ vote: "legit", category: null }],
    { categoryId: 1 },
  );
  assert.notEqual(category, "unknown");
});
