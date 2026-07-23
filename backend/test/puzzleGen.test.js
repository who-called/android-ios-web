import assert from "node:assert/strict";
import { test } from "node:test";
import { dailyPuzzles, trainingPuzzle, SPRINT } from "../src/game/puzzleGen.js";

// Independent uniqueness solver (mirrors the client's win check) used to VERIFY
// the generator's guarantee rather than trusting it.
function analyze(n, region, solution) {
  const colUsed = Array(n).fill(false);
  const regUsed = Array(n).fill(false);
  const placed = Array(n).fill(-1);
  let sols = 0;
  let matchesGiven = false;
  const ok = (row, col) =>
    !colUsed[col] && !regUsed[region[row][col]] && !(row > 0 && Math.abs(placed[row - 1] - col) <= 1);
  const dfs = (row) => {
    if (sols >= 3) return;
    if (row === n) {
      sols++;
      if (solution.every((c, i) => c === placed[i])) matchesGiven = true;
      return;
    }
    for (let col = 0; col < n; col++)
      if (ok(row, col)) {
        placed[row] = col;
        colUsed[col] = true;
        regUsed[region[row][col]] = true;
        dfs(row + 1);
        colUsed[col] = false;
        regUsed[region[row][col]] = false;
        placed[row] = -1;
      }
  };
  dfs(0);
  return { sols, matchesGiven };
}

test("daily sprint has 5 grids sized 5→9", () => {
  const grids = dailyPuzzles(20650);
  assert.equal(grids.length, 5);
  grids.forEach((g, i) => assert.equal(g.n, SPRINT[i].n));
});

test("every served grid is uniquely solvable and its solution is valid", () => {
  for (const day of [0, 1, 20650, 20651, 99999]) {
    for (const g of dailyPuzzles(day)) {
      const { sols, matchesGiven } = analyze(g.n, g.region, g.solution);
      assert.equal(sols, 1, `day ${day} size ${g.n}: expected unique solution, got ${sols}`);
      assert.ok(matchesGiven, `day ${day} size ${g.n}: given solution not valid`);
    }
  }
});

test("region ids cover 0..n-1 exactly", () => {
  for (const g of dailyPuzzles(42)) {
    const ids = new Set(g.region.flat());
    assert.equal(ids.size, g.n);
    for (const id of ids) assert.ok(id >= 0 && id < g.n);
  }
});

test("puzzles are deterministic per day (fair leaderboard)", () => {
  assert.deepEqual(dailyPuzzles(777), dailyPuzzles(777));
  assert.notDeepEqual(dailyPuzzles(777), dailyPuzzles(778));
});

test("training puzzle honors requested size and stays unique", () => {
  for (const n of [5, 7, 9]) {
    const g = trainingPuzzle(n, 12345 + n);
    assert.equal(g.n, n);
    assert.equal(analyze(g.n, g.region, g.solution).sols, 1);
  }
});
