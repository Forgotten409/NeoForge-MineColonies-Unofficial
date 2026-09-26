#!/usr/bin/env node
/**
 * restore-dev-assets — brings the ARR dev assets back onto THIS machine.
 *
 * The git repository ships WITHOUT MineColonies ARR content (see .gitignore,
 * LICENSE-THIRD-PARTY.md and docs/PUBLISHING.md). This script re-creates the
 * local dev layout so `gradlew build` / `runClient` behave like on the
 * maintainer's machine:
 *
 *   1. Downloads the OFFICIAL MineColonies release jar from the CurseForge CDN
 *      (or uses a jar you already have via --jar <path>). Nothing is
 *      redistributed — this is the same file every player downloads.
 *   2. Extracts the ARR trees into src/main/resources/:
 *        assets/minecolonies/**, assets/minecraft/textures/** (if present),
 *        blueprints/**, data/minecolonies/**, data/c/**,
 *        data/dynamictrees/**, data/neoforge/**, minecolonies.png
 *   3. Mirrors the port-authored 26.1 content from src/main/resources-publish/
 *      into the dev tree (items/**, equipment/**, novel lang keys) so the dev
 *      jar carries the new item models too.
 *   4. By default does NOT write portassets/bundled-flag.json — the game then
 *      provisions + converts the external assets at first run exactly like a
 *      player's publish jar does (recommended: identical behaviour, and the
 *      runtime converter handles the 1.21.1→26.1 format conversions).
 *      Pass --bundled-flag to also write the flag (dev jar then claims the ARR
 *      art is bundled — only useful for quick offline tests; raw 1.21.1
 *      formats are NOT converted in that mode).
 *
 * Requirements: node 18+ (or bun) + `jar` (any JDK) or `unzip` on the PATH.
 *
 * Usage:
 *   node tools/restore-dev-assets.mjs                 # download from CDN
 *   node tools/restore-dev-assets.mjs --jar ./minecolonies-1.1.1387-1.21.1-snapshot.jar
 *   node tools/restore-dev-assets.mjs --bundled-flag  # also write the dev flag
 *   node tools/restore-dev-assets.mjs --clean         # REMOVE the dev assets again
 */

import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, mkdtempSync, rmSync, statSync, writeFileSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

const ROOT = join(import.meta.dirname, "..");
const RES = join(ROOT, "src/main/resources");
const RES_PUB = join(ROOT, "src/main/resources-publish");

// mirrors docs/PUBLISHING.md §3 (stable forgecdn direct links, no auth)
const CDN_URL = "https://mediafilez.forgecdn.net/files/8872/247/minecolonies-1.1.1387-1.21.1-snapshot.jar";
const JAR_NAME = "minecolonies-1.1.1387-1.21.1-snapshot.jar";

const argv = process.argv.slice(2);
const flag = (name) => argv.includes(name);
const opt = (name) => {
  const i = argv.indexOf(name);
  return i >= 0 && i + 1 < argv.length ? argv[i + 1] : undefined;
};

/** Everything this script manages (so --clean can remove exactly that). */
const MANAGED = [
  join(RES, "assets/minecolonies"),
  join(RES, "assets/minecraft/textures"),
  join(RES, "blueprints"),
  join(RES, "data/minecolonies"),
  join(RES, "data/c"),
  join(RES, "data/dynamictrees"),
  join(RES, "data/neoforge"),
  join(RES, "minecolonies.png"),
  join(RES, "portassets/bundled-flag.json"),
];

function clean() {
  console.log("restore-dev-assets: --clean — removing dev assets …");
  for (const p of MANAGED) {
    if (existsSync(p)) {
      rmSync(p, { recursive: true, force: true });
      console.log("  removed " + p);
    }
  }
  console.log("Done. The tree is now exactly what git tracks (ARR-free).");
}

function extractJar(jarPath, targetDir) {
  // Prefer the JDK's `jar` tool (guaranteed present for mod development),
  // fall back to `unzip`.
  for (const cmd of [
    ["jar", ["xf", jarPath]],
    ["unzip", ["-q", "-o", jarPath, "-d", targetDir]],
  ]) {
    try {
      execFileSync(cmd[0], cmd[1], { cwd: targetDir, stdio: "pipe" });
      return;
    } catch {
      /* try next */
    }
  }
  throw new Error("neither `jar` (JDK) nor `unzip` worked — install a JDK or unzip and retry");
}

async function download(url, dest) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`download failed: HTTP ${res.status} for ${url}`);
  const buf = Buffer.from(await res.arrayBuffer());
  const { writeFileSync: wf } = await import("node:fs");
  wf(dest, buf);
  return buf.length;
}

async function main() {
  if (flag("--clean")) return clean();

  let jarPath = opt("--jar");
  if (!jarPath) {
    jarPath = join(tmpdir(), JAR_NAME);
    if (!existsSync(jarPath)) {
      console.log(`restore-dev-assets: downloading the official jar from the CurseForge CDN …`);
      console.log(`  ${CDN_URL}`);
      const n = await download(CDN_URL, jarPath);
      console.log(`  ${(n / 1048576).toFixed(1)} MB — same file every player gets from CurseForge.`);
    } else {
      console.log(`restore-dev-assets: reusing cached ${jarPath}`);
    }
  } else {
    jarPath = join(process.cwd(), jarPath);
    if (!existsSync(jarPath)) throw new Error(`--jar: file not found: ${jarPath}`);
  }

  const tmp = mkdtempSync(join(tmpdir(), "mc-port-assets-"));
  try {
    console.log(`restore-dev-assets: extracting ${JAR_NAME} …`);
    extractJar(jarPath, tmp);

    const move = (sub) => {
      const src = join(tmp, sub);
      if (!existsSync(src)) return false;
      const dst = join(RES, sub);
      mkdirSync(join(dst, ".."), { recursive: true });
      rmSync(dst, { recursive: true, force: true });
      cpSync(src, dst, { recursive: true });
      return true;
    };

    for (const sub of [
      "assets/minecolonies",
      "assets/minecraft/textures",
      "blueprints",
      "data/minecolonies",
      "data/c",
      "data/dynamictrees",
      "data/neoforge",
    ]) {
      console.log(`  ${move(sub) ? "extracted " : "not in jar (skipped): "}${sub}`);
    }
    const logo = join(tmp, "minecolonies.png");
    if (existsSync(logo)) cpSync(logo, join(RES, "minecolonies.png"));
    console.log(`  ${existsSync(logo) ? "extracted " : "not in jar (skipped): "}minecolonies.png`);

    // 3. mirror the port-authored publish content into the dev tree
    const from = join(RES_PUB, "assets/minecolonies");
    const to = join(RES, "assets/minecolonies");
    if (existsSync(from)) {
      for (const sub of ["items", "equipment", "models", "textures", "lang"]) {
        const s = join(from, sub);
        if (!existsSync(s)) continue;
        mkdirSync(join(to, sub), { recursive: true });
        cpSync(s, join(to, sub), { recursive: true });
        console.log(`  mirrored port-authored resources-publish/assets/minecolonies/${sub}`);
      }
    }

    // 4. optional dev flag
    if (flag("--bundled-flag")) {
      const flagPath = join(RES, "portassets/bundled-flag.json");
      mkdirSync(join(flagPath, ".."), { recursive: true });
      writeFileSync(
        flagPath,
        JSON.stringify(
          {
            _comment:
              "DEV-ONLY (written by tools/restore-dev-assets.mjs --bundled-flag): claims the ARR art is bundled in this jar so the runtime download is skipped. Raw 1.21.1 formats are NOT converted in this mode — prefer running without the flag.",
            bundled: ["minecolonies"],
          },
          null,
          2,
        ) + "\n",
      );
      console.log("  wrote portassets/bundled-flag.json (dev-only marker)");
    } else {
      console.log("  (no bundled-flag written — the game provisions+converts external assets at first run, like the publish jar)");
    }

    console.log("");
    console.log("Dev assets restored — LOCAL TESTING ONLY, never commit/push them:");
    console.log("  git status   → the ARR paths must NOT appear as 'to be committed'");
    console.log("  node tools/check-arr-clean.mjs   → must print OK");
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
}

main().catch((e) => {
  console.error("restore-dev-assets: " + (e?.message || e));
  process.exit(1);
});
