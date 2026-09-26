#!/usr/bin/env node
/**
 * restore-dev-assets — brings the ARR dev assets back onto THIS machine, CONVERTED to
 * the 26.1 formats (the offline equivalent of the in-game provisioning conversion).
 *
 * NOT REQUIRED for normal `runClient` testing anymore: since the build.gradle change
 * (`sourceSets.main.resources.srcDir 'src/main/resources-publish'`) the dev classpath
 * carries the port-authored 26.1 content, and the game provisions + converts the
 * external store at first run exactly like a player's publish jar (online). This
 * script is for two remaining cases:
 *   - OFFLINE dev (no CurseForge access at run time) — run it with --bundled-flag;
 *   - re-creating the maintainer's fully-bundled local dev layout.
 *
 * Steps:
 *   1. Downloads the OFFICIAL MineColonies release jar from the CurseForge CDN
 *      (or uses a jar you already have via --jar <path>). Nothing is redistributed —
 *      this is the same file every player downloads.
 *   2. Extracts the ARR trees and converts them 1.21.1 → 26.1 IN PLACE with the exact
 *      rule set the game's runtime converter uses (tools/converter-rules.mjs — the 1:1
 *      JS mirror of PortAssetConverter; tools/simulate-converter.mjs verifies the
 *      output against the maintainer's dev tree). Models, recipes, loot tables, tags,
 *      spawn-egg models, racks, spears, sounds.json — all arrive dev-ready, so the
 *      old "raw 1.21.1 formats break the dev run" trap is gone.
 *   3. Moves the CONVERTED trees into the destination (src/main/resources by default):
 *        assets/minecolonies/**, assets/minecraft/textures/** (if present),
 *        blueprints/**, data/minecolonies/**, data/c/**,
 *        data/dynamictrees/**, data/neoforge/**, minecolonies.png
 *      (The old "mirror resources-publish into resources" step is GONE — the dev
 *      classpath includes src/main/resources-publish directly since the build.gradle
 *      sourceSets change; duplicating it into resources only created shadowing.)
 *   4. By default does NOT write portassets/bundled-flag.json — the game then still
 *      provisions + converts the external assets at first run exactly like a player's
 *      publish jar (recommended: identical behaviour). Pass --bundled-flag to also
 *      write the flag: the dev jar then claims the ARR art is bundled and no download
 *      happens (fully offline dev — the tree is CONVERTED, so models and recipes work
 *      offline too, unlike in the old raw-extraction mode).
 *
 * Requirements: node 18+ (or bun) + ONE of: `jar` (any JDK on the PATH), `unzip`, or —
 * on Windows — PowerShell (Expand-Archive; present on every stock Windows install).
 *
 * Usage:
 *   node tools/restore-dev-assets.mjs                  # download + convert into resources
 *   node tools/restore-dev-assets.mjs --bundled-flag   # also write the dev flag (offline dev)
 *   node tools/restore-dev-assets.mjs --jar ./minecolonies-1.1.1387-1.21.1-snapshot.jar
 *   node tools/restore-dev-assets.mjs --dest /tmp/x    # extract+convert elsewhere (testing)
 *   node tools/restore-dev-assets.mjs --clean          # REMOVE the dev assets again
 */

import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, mkdtempSync, rmSync, copyFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { resolve as pathResolve } from "node:path";
import { tmpdir } from "node:os";
import { convertStoreDir } from "./converter-rules.mjs";

const ROOT = join(import.meta.dirname, "..");
const RES = join(ROOT, "src/main/resources");
const RES_PUB = join(ROOT, "src/main/resources-publish");
const OVERRIDES = join(RES_PUB, "portassets", "overrides");

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
function managedPaths(base) {
  return [
    join(base, "assets/minecolonies"),
    join(base, "assets/minecraft/textures"),
    join(base, "blueprints"),
    join(base, "data/minecolonies"),
    join(base, "data/c"),
    join(base, "data/dynamictrees"),
    join(base, "data/neoforge"),
    join(base, "minecolonies.png"),
    join(base, "portassets/bundled-flag.json"),
  ];
}

function clean(base) {
  console.log(`restore-dev-assets: --clean — removing dev assets under ${base} …`);
  for (const p of managedPaths(base)) {
    if (existsSync(p)) {
      rmSync(p, { recursive: true, force: true });
      console.log("  removed " + p);
    }
  }
  console.log("Done. The tree is now exactly what git tracks (ARR-free).");
}

function extractJar(jarPath, targetDir) {
  // 1) the JDK's `jar` tool (present whenever a JDK is on the PATH),
  // 2) `unzip` (most Linux/macOS boxes),
  // 3) Windows PowerShell Expand-Archive — the stock fallback, because a Gradle-managed
  //    JDK toolchain does NOT put jar.exe on the PATH (the previous silent-failure mode:
  //    "neither jar nor unzip worked" on a plain Windows checkout).
  const attempts = [
    ["jar", ["xf", jarPath], null],
    ["unzip", ["-q", "-o", jarPath, "-d", targetDir], null],
  ];
  if (process.platform === "win32") {
    // Expand-Archive insists on a .zip extension — copy the jar aside
    const zipCopy = jarPath.replace(/\.jar$/i, "") + "-as.zip";
    const ps = `Expand-Archive -LiteralPath '${zipCopy.replaceAll("'", "''")}' `
      + `-DestinationPath '${targetDir.replaceAll("'", "''")}' -Force`;
    attempts.push(["powershell", ["-NoProfile", "-Command", ps], () => {
      copyFileSync(jarPath, zipCopy);
      return () => rmSync(zipCopy, { force: true });
    }]);
  }
  for (const [cmd, args, wrap] of attempts) {
    let cleanup = null;
    try {
      if (wrap) cleanup = wrap();
      execFileSync(cmd, args, { cwd: targetDir, stdio: "pipe" });
      return;
    } catch {
      /* try next */
    } finally {
      if (cleanup) { try { cleanup(); } catch { /* best effort */ } }
    }
  }
  throw new Error(
    "extraction failed — no `jar` (JDK), `unzip` or PowerShell Expand-Archive worked; "
      + "install a JDK or unzip, or extract the jar manually with any zip tool");
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
  // path.resolve (not join!): an absolute --dest/--jar must stay absolute
  const destBase = opt("--dest") ? pathResolve(process.cwd(), opt("--dest")) : RES;
  if (flag("--clean")) return clean(destBase);

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
    jarPath = pathResolve(process.cwd(), jarPath);
    if (!existsSync(jarPath)) throw new Error(`--jar: file not found: ${jarPath}`);
  }

  const tmp = mkdtempSync(join(tmpdir(), "mc-port-assets-"));
  try {
    console.log(`restore-dev-assets: extracting ${JAR_NAME} …`);
    extractJar(jarPath, tmp);

    // convert the 1.21.1 formats to the 26.1 formats BEFORE anything lands in the
    // dev tree — same rule set the game's runtime converter applies to the external
    // store (tools/converter-rules.mjs ⇔ PortAssetConverter, rule set port26-4)
    console.log(`restore-dev-assets: converting 1.21.1 → 26.1 formats …`);
    const counts = convertStoreDir(tmp, existsSync(OVERRIDES) ? OVERRIDES : null,
      (line) => console.log("  " + line));

    const move = (sub) => {
      const src = join(tmp, sub);
      if (!existsSync(src)) return false;
      const dst = join(destBase, sub);
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
      console.log(`  ${move(sub) ? "installed (converted) " : "not in jar (skipped): "}${sub}`);
    }
    const logo = join(tmp, "minecolonies.png");
    if (existsSync(logo)) cpSync(logo, join(destBase, "minecolonies.png"));
    console.log(`  ${existsSync(logo) ? "installed " : "not in jar (skipped): "}minecolonies.png`);

    // optional dev flag
    if (flag("--bundled-flag")) {
      const flagPath = join(destBase, "portassets/bundled-flag.json");
      mkdirSync(join(flagPath, ".."), { recursive: true });
      writeFileSync(
        flagPath,
        JSON.stringify(
          {
            _comment:
              "DEV-ONLY (written by tools/restore-dev-assets.mjs --bundled-flag): claims the ARR art is bundled in this jar so the runtime download is skipped. The extracted tree IS converted to the 26.1 formats, so offline dev works.",
            bundled: ["minecolonies"],
          },
          null,
          2,
        ) + "\n",
      );
      console.log("  wrote portassets/bundled-flag.json (dev-only marker — fully offline dev)");
    } else {
      console.log("  (no bundled-flag written — the game still provisions+converts at first run, like the player's jar)");
    }

    console.log("");
    console.log("Dev assets restored (CONVERTED to 26.1) — LOCAL TESTING ONLY, never commit/push them:");
    console.log("  git status   → the ARR paths must NOT appear as 'to be committed'");
    console.log("  node tools/check-arr-clean.mjs   → must print OK");
    console.log("");
    console.log(`(${counts.modelFiles} model file(s) and ${counts.dataFiles} data file(s) converted; `
      + `${counts.deletedWrappers + counts.deletedObsolete} obsolete file(s) removed, `
      + `${counts.overrides} port override(s) applied)`);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
}

main().catch((e) => {
  console.error("restore-dev-assets: " + (e?.message || e));
  process.exit(1);
});
