#!/usr/bin/env node
/**
 * check-arr-clean — hard guard that this repository carries NO ARR content.
 *
 * MineColonies art assets, data and blueprints are All Rights Reserved
 * (LICENSE-THIRD-PARTY.md). They must never be committed, pushed or otherwise
 * redistributed from this repository. This script checks exactly that:
 *
 *   1. If run inside a git repo      → checks the FILES GIT WOULD TRACK
 *      (`git ls-files`, i.e. the index + working tree, after .gitignore).
 *   2. If run outside a git repo     → simulates .gitignore with the same
 *      pattern list (kept in sync manually — see PATTERNS below) and checks
 *      the files that would be tracked.
 *
 * Additionally it flags "suspicious" binaries that should simply not exist in
 * a source distribution: *.ogg, *.nbt, *.blueprint, *.mca, *.zip inside the
 * resources trees, and any *.jar anywhere — with ONE exception: the Gradle
 * wrapper (gradle/wrapper/gradle-wrapper.jar), which is Apache-2.0 licensed
 * by Gradle Inc. and is the standard, redistributable bootstrap of every
 * Gradle project. A fresh `git clone` needs it for `./gradlew` to work.
 *
 * Usage:
 *   node tools/check-arr-clean.mjs            (or: bun tools/check-arr-clean.mjs)
 *   node tools/check-arr-clean.mjs --verbose  (list every checked file)
 *
 * Exit codes: 0 = clean (safe to push) · 1 = ARR violations found
 * Write those two lines into your CI / pre-push hook and you can never
 * accidentally publish ARR content again.
 */

import { execFileSync } from "node:child_process";
import { existsSync, readdirSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";

const ROOT = join(import.meta.dirname, "..");
const VERBOSE = process.argv.includes("--verbose");

/**
 * Ignore patterns — MUST mirror .gitignore. Each entry:
 *   "dir"  → a directory prefix (everything below is ignored)
 *   "file" → an exact file path
 *   "name" → a file name matched anywhere
 */
const PATTERNS = [
  { kind: "dir", p: "src/main/resources/assets/minecolonies" },
  { kind: "dir", p: "src/main/resources/assets/minecraft/textures" },
  { kind: "dir", p: "src/main/resources/blueprints" },
  { kind: "dir", p: "src/main/resources/data/minecolonies" },
  { kind: "dir", p: "src/main/resources/data/c" },
  { kind: "dir", p: "src/main/resources/data/dynamictrees" },
  { kind: "dir", p: "src/main/resources/data/neoforge" },
  { kind: "dir", p: "src/main/resources/portassets/bundled-flag.json" },
  { kind: "file", p: "src/main/resources/minecolonies.png" },
  // build / IDE / runs / OS
  { kind: "dir", p: ".gradle" },
  { kind: "dir", p: "build" },
  { kind: "dir", p: "out" },
  { kind: "dir", p: "classes" },
  { kind: "dir", p: ".idea" },
  { kind: "dir", p: ".vscode" },
  { kind: "dir", p: "run" },
  { kind: "dir", p: "runs" },
  { kind: "dir", p: "run-client" },
  { kind: "dir", p: "run-server" },
  { kind: "dir", p: "port-assets" },
  // local third-party compat mod jars (gitignored; build uses CurseMaven)
  { kind: "dir", p: "libs" },
  // maintainer playbook — local-only, never pushed (mirrors .gitignore)
  { kind: "dir", p: "docs" },
  { kind: "file", p: "CURSEFORGE-SUBMISSION.md" },
  { kind: "name", p: ".DS_Store" },
  { kind: "name", p: "Thumbs.db" },
  { kind: "name", p: "desktop.ini" },
  { kind: "name", p: "*.iml" },
  { kind: "name", p: "*.ipr" },
  { kind: "name", p: "*.iws" },
  { kind: "name", p: "*.swp" },
  { kind: "name", p: "*.swo" },
];

/** The ARR directories — a tracked file under any of these is a violation. */
const ARR_DIRS = [
  "src/main/resources/assets/minecolonies",
  "src/main/resources/assets/minecraft/textures",
  "src/main/resources/blueprints",
  "src/main/resources/data/minecolonies",
  "src/main/resources/data/c",
  "src/main/resources/data/dynamictrees",
  "src/main/resources/data/neoforge",
];

const ARR_FILES = ["src/main/resources/minecolonies.png"];

/** Binaries that have no business in a source repo (ARR or not). */
const FORBIDDEN_EXTENSIONS = [".ogg", ".nbt", ".mca", ".jar"]; // .blueprint handled below

/** The only .jar that may ship in the repo: Gradle's own wrapper (Apache-2.0). */
const JAR_ALLOWLIST = ["gradle/wrapper/gradle-wrapper.jar"];

/**
 * .blueprint schematics: ARR under blueprints/** (minecolonies) but GPL inside
 * assets/structurize/schematics (upstream ships big_well.blueprint in-repo).
 */
const BLUEPRINT_GPL_DIR = "src/main/resources/assets/structurize";

/** GPL namespaces that legitimately contain pngs (blockui, domum, …). */
const GPL_ASSET_DIRS = [
  "src/main/resources/assets/blockui",
  "src/main/resources/assets/domum_ornamentum",
  "src/main/resources/assets/multipiston",
  "src/main/resources/assets/structurize",
  "src/main/resources/assets/minecraft/atlases",
  "src/main/resources-publish/assets/minecolonies", // port-authored eggs + leather (20 png, verified)
];

/**
 * resources-publish sub-trees that are STORE-PROVIDED since the 0.6.0 ARR externalization:
 * any tracked file here is a regression (ARR-derived content re-bundled into the publish
 * jar). The port ships only port-authored content in resources-publish — items/, equipment
 * JSONs, novel lang keys, egg/leather pngs, the cavalry_horse loot table and the
 * portassets/overrides/ conversion rules.
 */
const STORE_PROVIDED_DIRS = [
  "src/main/resources-publish/assets/minecolonies/textures/entity", // equipment armor-layer pngs — renamed from the store by the converter
  "src/main/resources-publish/data/minecolonies/damage_type", // 47 ARR records — store provides
  "src/main/resources-publish/data/minecolonies/researches", // upstream research JSONs — store provides
  "src/main/resources-publish/data/minecolonies/recipe", // blockhutstable etc. — store + converter provide
];

function norm(p) {
  return p.split(sep).join("/");
}

function ignored(relPath) {
  const p = norm(relPath);
  for (const { kind, p: pat } of PATTERNS) {
    if (kind === "dir" && (p === pat || p.startsWith(pat + "/"))) return true;
    if (kind === "file" && p === pat) return true;
    if (kind === "name") {
      const name = p.split("/").pop();
      if (name === pat) return true;
      if (pat.startsWith("*") && name.endsWith(pat.slice(1))) return true;
    }
  }
  return false;
}

/** Collect the file list exactly the way git would see it. */
function trackedFiles() {
  const gitDir = join(ROOT, ".git");
  if (existsSync(gitDir)) {
    try {
      const out = execFileSync("git", ["ls-files", "--cached", "--others", "--exclude-standard"], {
        cwd: ROOT,
        encoding: "utf8",
        maxBuffer: 64 * 1024 * 1024,
      });
      return { source: "git ls-files", files: out.split("\n").filter(Boolean) };
    } catch (e) {
      console.error("check-arr-clean: `git ls-files` failed — falling back to walk:", String(e.message || e));
    }
  }
  // No git repo (or git failed): walk the tree and apply PATTERNS ourselves.
  const files = [];
  (function walk(dir) {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      const rel = relative(ROOT, full);
      if (entry.isDirectory()) {
        if (ignored(rel)) continue;
        walk(full);
      } else if (!ignored(rel)) {
        files.push(rel);
      }
    }
  })(ROOT);
  return { source: "simulated .gitignore walk", files };
}

const { source, files } = trackedFiles();
if (VERBOSE) console.log(`# source: ${source}, files: ${files.length}`);

const violations = [];
const warnings = [];

for (const f of files) {
  const p = norm(f);

  // 0. resources-publish: store-provided ARR-derived sub-trees (0.6.0 externalization)
  if (STORE_PROVIDED_DIRS.some((d) => p === d || p.startsWith(d + "/"))) {
    violations.push(`store-provided: ${p} (ARR-derived content must come from the external store + converter, not the publish jar)`);
    continue;
  }

  // 1. ARR paths
  if (ARR_DIRS.some((d) => p === d || p.startsWith(d + "/"))) {
    violations.push(`ARR path:   ${p}`);
    continue;
  }
  if (ARR_FILES.includes(p)) {
    violations.push(`ARR file:   ${p}`);
    continue;
  }

  // 2. dev-only bundled flag (must never be tracked without the assets)
  if (p === "src/main/resources/portassets/bundled-flag.json") {
    violations.push(`dev flag:   ${p} (only valid on a machine with restored dev assets)`);
    continue;
  }

  // 3. forbidden binaries
  const ext = p.slice(p.lastIndexOf(".")).toLowerCase();
  if (ext === ".blueprint") {
    if (!p.startsWith(BLUEPRINT_GPL_DIR + "/")) {
      violations.push(`blueprint:  ${p} (minecolonies schematics are ARR — only structurize's GPL one may ship)`);
    }
    continue;
  }
  if (FORBIDDEN_EXTENSIONS.includes(ext)) {
    if (ext === ".jar" && JAR_ALLOWLIST.includes(p)) {
      // gradle wrapper jar — Apache-2.0, required for ./gradlew on fresh clones
    } else {
      violations.push(`binary:     ${p} (${ext})`);
      continue;
    }
  }

  // 4. png outside the GPL asset namespaces (unexpected — warn, don't fail)
  if (ext === ".png" && !GPL_ASSET_DIRS.some((d) => p.startsWith(d + "/") || p === d)) {
    warnings.push(`png?:       ${p} — verify this is GPL/port-authored before pushing`);
  }

  // 5. zip inside resources (e.g. blueprints/template.zip is ARR — but any zip
  //    in resources is suspicious)
  if (ext === ".zip" && p.includes("/resources")) {
    violations.push(`zip:        ${p} (archives do not belong in resources)`);
  }
}

console.log("check-arr-clean — MineColonies ARR redistribution guard");
console.log(`scanned ${files.length} tracked files (${source})`);
console.log("");

if (warnings.length) {
  console.log(`Warnings (${warnings.length}):`);
  for (const w of warnings) console.log("  " + w);
  console.log("");
}

if (violations.length) {
  console.error(`VIOLATIONS (${violations.length}) — ARR or forbidden content WOULD BE PUSHED:`);
  for (const v of violations) console.error("  " + v);
  console.error("");
  console.error("Fix: remove / gitignore these paths (see .gitignore + tools/restore-dev-assets.mjs).");
  console.error("If they are already in your git history: delete the GitHub repository and re-push");
  console.error("from a clean tree, or rewrite the history with 'git filter-repo' --invert-paths.");
  process.exit(1);
} else {
  console.log("OK — no ARR content tracked. Safe to push to GitHub.");
  console.log("Reminder: the published jar must be built with `gradlew publishJar` (never the dev jar).");
  process.exit(0);
}
