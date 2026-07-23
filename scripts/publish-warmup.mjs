// Publish warm-up artifacts to a stable GitHub release so app builds fetch them
// instead of depending on a local DB. Run inside the updater image after
// `dbforge snapshot`. Uses global fetch (Node 22).
//
// Env: GH_TOKEN (contents:write), GH_REPO ("owner/name"), optional WARMUP_DIR,
//      optional WARMUP_TAG (default "warmup-latest").
import { readFile } from "node:fs/promises";
import path from "node:path";

const TOKEN = process.env.GH_TOKEN;
const REPO = process.env.GH_REPO;
const DIR = process.env.WARMUP_DIR ?? "/tmp/w";
const TAG = process.env.WARMUP_TAG ?? "warmup-latest";
const FILES = ["warmup.sqlite", "warmup_ios.json", "manifest.json"];

if (!TOKEN || !REPO) {
  console.error("publish-warmup: GH_TOKEN and GH_REPO are required");
  process.exit(2);
}

const api = "https://api.github.com";
const h = { Authorization: `Bearer ${TOKEN}`, Accept: "application/vnd.github+json" };

async function gh(url, init = {}) {
  const res = await fetch(url, { ...init, headers: { ...h, ...(init.headers ?? {}) } });
  if (!res.ok && res.status !== 404) {
    throw new Error(`${init.method ?? "GET"} ${url} → ${res.status}: ${await res.text()}`);
  }
  return res;
}

// Get-or-create the release.
async function getOrCreateRelease() {
  let res = await gh(`${api}/repos/${REPO}/releases/tags/${TAG}`);
  if (res.status === 404) {
    res = await gh(`${api}/repos/${REPO}/releases`, {
      method: "POST",
      body: JSON.stringify({
        tag_name: TAG,
        name: "Warm-up artifacts (latest)",
        prerelease: true,
        body: "FR warm-up DB for app builds. Regenerated from prod by CI.",
      }),
    });
  }
  return res.json();
}

async function main() {
  const rel = await getOrCreateRelease();
  const existing = new Map((rel.assets ?? []).map((a) => [a.name, a.id]));

  for (const name of FILES) {
    const buf = await readFile(path.join(DIR, name)).catch(() => null);
    if (!buf) {
      console.warn(`skip ${name} (not produced)`);
      continue;
    }
    // Asset names are unique per release — delete the old one first.
    if (existing.has(name)) {
      await gh(`${api}/repos/${REPO}/releases/assets/${existing.get(name)}`, { method: "DELETE" });
    }
    const ct = name.endsWith(".json") ? "application/json" : "application/octet-stream";
    const up = await fetch(
      `https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=${name}`,
      { method: "POST", headers: { ...h, "Content-Type": ct }, body: buf },
    );
    if (!up.ok) throw new Error(`upload ${name} → ${up.status}: ${await up.text()}`);
    console.log(`uploaded ${name} (${buf.length} bytes)`);
  }
  console.log(`done → release ${TAG}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
