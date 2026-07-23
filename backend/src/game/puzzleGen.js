// TRACE — daily deduction puzzle generator (server-authoritative).
//
// Logic is a "Queens"/Star-Battle variant reskinned as a spam hunt:
//   • one fraudster per row, per column, per region (area code)
//   • two fraudsters never touch, diagonals included
//   • each puzzle has a UNIQUE solution (guaranteed by the solver below)
//
// The server generates the grids so every client (Android + iOS) plays the
// EXACT same puzzle for a given day — a fair, comparable leaderboard — without
// porting this heavy solver to Kotlin/Swift or risking cross-platform RNG drift.
// Everything is deterministic from the epoch-day.

// ---- Deterministic RNG (mulberry32) ---------------------------------------
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Derive a stable 32-bit seed from (day, index) so each grid of the day differs
// but the whole set is reproducible.
function seedFor(day, index) {
  let h = (Math.imul(day >>> 0, 2654435761) + Math.imul(index + 1, 40503) + 0x9e3779b9) >>> 0;
  h ^= h >>> 13;
  h = Math.imul(h, 0x5bd1e995);
  h ^= h >>> 15;
  return h >>> 0;
}

const shuffle = (arr, rnd) => {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rnd() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
};

// ---- Valid solution (one "queen" per row, non-adjacent columns) ------------
function generateSolution(n, rnd, tries = 3000) {
  for (let attempt = 0; attempt < tries; attempt++) {
    const cols = new Array(n).fill(-1);
    const usedCol = new Set();
    let ok = true;
    for (let row = 0; row < n && ok; row++) {
      const candidates = shuffle([...Array(n).keys()].filter((c) => !usedCol.has(c)), rnd);
      let placed = false;
      for (const c of candidates) {
        if (row > 0 && Math.abs(cols[row - 1] - c) <= 1) continue;
        cols[row] = c;
        usedCol.add(c);
        placed = true;
        break;
      }
      if (!placed) ok = false;
    }
    if (ok) return cols;
  }
  return null;
}

// ---- Regions (area codes): flood-fill from each solution cell --------------
function growRegions(n, solutionCols, rnd, compactness) {
  const region = Array.from({ length: n }, () => new Array(n).fill(-1));
  const frontier = [];
  for (let r = 0; r < n; r++) region[r][solutionCols[r]] = r;

  const pushNeighbors = (r, c, id) => {
    for (const [dr, dc] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) {
      const nr = r + dr, nc = c + dc;
      if (nr >= 0 && nr < n && nc >= 0 && nc < n && region[nr][nc] === -1)
        frontier.push({ r: nr, c: nc, region: id });
    }
  };
  for (let r = 0; r < n; r++) pushNeighbors(r, solutionCols[r], r);

  let remaining = n * n - n;
  while (remaining > 0 && frontier.length) {
    const idx = rnd() < compactness ? frontier.length - 1 : Math.floor(rnd() * frontier.length);
    const cell = frontier[idx];
    frontier.splice(idx, 1);
    if (region[cell.r][cell.c] !== -1) continue;
    region[cell.r][cell.c] = cell.region;
    pushNeighbors(cell.r, cell.c, cell.region);
    remaining--;
  }
  // Plug any holes with a placed neighbor's region.
  for (let r = 0; r < n; r++)
    for (let c = 0; c < n; c++)
      if (region[r][c] === -1) {
        for (const [dr, dc] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) {
          const nr = r + dr, nc = c + dc;
          if (nr >= 0 && nr < n && nc >= 0 && nc < n && region[nr][nc] !== -1) {
            region[r][c] = region[nr][nc];
            break;
          }
        }
        if (region[r][c] === -1) region[r][c] = 0;
      }
  return region;
}

// ---- Solver: count solutions (stops at 2) → guarantees uniqueness ----------
function countSolutions(n, region, limit = 2) {
  const colUsed = new Array(n).fill(false);
  const regionUsed = new Array(n).fill(false);
  const placed = new Array(n).fill(-1);
  let solutions = 0;
  const ok = (row, col) => {
    if (colUsed[col]) return false;
    if (regionUsed[region[row][col]]) return false;
    if (row > 0 && Math.abs(placed[row - 1] - col) <= 1) return false;
    return true;
  };
  const dfs = (row) => {
    if (solutions >= limit) return;
    if (row === n) {
      solutions++;
      return;
    }
    for (let col = 0; col < n; col++) {
      if (ok(row, col)) {
        placed[row] = col;
        colUsed[col] = true;
        regionUsed[region[row][col]] = true;
        dfs(row + 1);
        colUsed[col] = false;
        regionUsed[region[row][col]] = false;
        placed[row] = -1;
        if (solutions >= limit) return;
      }
    }
  };
  dfs(0);
  return solutions;
}

// ---- Generate ONE unique puzzle of a fixed size (compactness ramp) ---------
function generateSized(n, compactness, seed) {
  const baseSeed = seed >>> 0;
  const ramps = [compactness, compactness + 0.12, compactness + 0.25, compactness + 0.4];
  for (const comp of ramps) {
    const cclamp = Math.min(comp, 0.97);
    for (let attempt = 0; attempt < 80; attempt++) {
      const s = (baseSeed + attempt * 7919 + Math.floor(comp * 1000) * 31) >>> 0;
      const rnd = mulberry32(s);
      const solution = generateSolution(n, rnd);
      if (!solution) continue;
      const region = growRegions(n, solution, rnd, cclamp);
      if (countSolutions(n, region, 2) === 1) return { n, region, solution };
    }
  }
  // Fallback (extremely rare): a compact grid that always solves.
  const rnd = mulberry32(baseSeed);
  const solution = generateSolution(n, rnd) || Array.from({ length: n }, (_, i) => i);
  const region = growRegions(n, solution, rnd, 0.95);
  return { n, region, solution };
}

// ---- The daily 5-grid sprint (sizes 5→9, ramping difficulty) --------------
// Sizes/compactness mirror SpamGuard's DIFFICULTIES ladder so grid 1 is a warm
// up and grid 5 is expert-tier. Identical for every player on a given day.
export const SPRINT = [
  { n: 5, compactness: 0.95 },
  { n: 6, compactness: 0.85 },
  { n: 7, compactness: 0.70 },
  { n: 8, compactness: 0.55 },
  { n: 9, compactness: 0.42 },
];

// Small in-process cache: puzzles are deterministic, so compute once per day.
const cache = new Map(); // `${day}` → grids[]

/**
 * The 5 grids for a given epoch-day. Each grid: { n, region:number[][], solution:number[] }.
 * region[r][c] = area-code id (0..n-1); solution[r] = column of the fraudster in row r.
 */
export function dailyPuzzles(day) {
  const key = String(day);
  const hit = cache.get(key);
  if (hit) return hit;
  const grids = SPRINT.map((spec, i) => {
    const { n, region, solution } = generateSized(spec.n, spec.compactness, seedFor(day, i));
    return { n, region, solution };
  });
  cache.set(key, grids);
  if (cache.size > 8) cache.delete(cache.keys().next().value); // keep it tiny
  return grids;
}

// A free-play puzzle for the unranked "training" mode (any size, ad-hoc seed).
export function trainingPuzzle(n, seed) {
  const spec = SPRINT.find((s) => s.n === n) || { n, compactness: 0.6 };
  const { region, solution } = generateSized(spec.n, spec.compactness, seed >>> 0);
  return { n: spec.n, region, solution };
}
