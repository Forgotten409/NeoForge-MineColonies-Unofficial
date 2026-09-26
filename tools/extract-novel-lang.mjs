#!/usr/bin/env node
/**
 * extract-novel-lang.mjs — PORT26 publishing support (no dependencies, Node 18+ / Bun).
 *
 * Diffs the PORT's language files against the PRISTINE upstream 1.21.1 originals and writes
 * ONLY the novel/changed keys to src/main/resources-publish/assets/<ns>/lang/<file>.json.
 *
 * SCOPE — the minecolonies namespace ONLY: it is the single ARR-externalized namespace
 * (license policy of docs/PUBLISHING.md §1). Structurize / Domum Ornamentum / BlockUI /
 * Multi-Piston are GPL-3.0, so the publish jar bundles their FULL lang files straight from
 * src/main/resources — writing novel-key copies for them into resources-publish would
 * collide with those bundled files as duplicate ZIP entries in publishJar. The script also
 * DELETES stale non-minecolonies outputs from a previous run of the old all-namespace mode.
 *
 * Rationale: Minecraft merges language files across packs PER KEY (identical resource path),
 * so shipping only the keys we authored/changed at the same path fully overrides those keys,
 * while the ARR upstream text stays externalized in the downloaded official MineColonies
 * pack. The publishJar task then re-includes resources-publish instead of the full bundled
 * minecolonies lang files.
 *
 * Reference resolution per (namespace, file) — first hit wins:
 *   1. <jarRef>/assets/<ns>/lang/<file>     lang extracted from the OFFICIAL 1.21.1 jars
 *                                            (authoritative distributed artifact; generate
 *                                            with: see PUBLISHING.md "Refreshing lang refs")
 *   2. portsrc/<project>/src/main/resources/assets/<ns>/lang/<file>
 *                                            (structurize, multipiston, blockui keep lang
 *                                            in the source tree)
 *   3. portsrc/<project>/src/datagen/generated/<project>/assets/<ns>/lang/<file>
 *                                            (minecolonies default/quests/tag.item.json +
 *                                            domum_ornamentum en_us.json are datagen output)
 *
 * SAFETY: when NO reference can be resolved (e.g. minecolonies en_us.json/pl_pl.json,
 * which only ship inside the official jars — never in the git source tree), the file is
 * SKIPPED with a loud warning instead of dumping every key into the publish tree — an
 * unresolved diff would bundle the entire upstream (ARR) translation into the published
 * jar, which is exactly what this tool exists to prevent. Generate the jar reference first
 * (see PUBLISHING.md) and re-run.
 *
 * Usage:
 *   node tools/extract-novel-lang.mjs [--port <dir>] [--src <dir>] [--jarref <dir>] [--out <dir>] [--quiet]
 *     --port    port root              (default: parent of tools/)
 *     --src     upstream source root   (default: <portRoot>/../portsrc)
 *     --jarref  jar-extracted lang ref (default: <portRoot>/../newer-versions/upstream-lang)
 *     --out     output resources root  (default: <portRoot>/src/main/resources-publish)
 *
 * Exit code 0 on success; 1 when a port lang file is invalid JSON.
 */

import { readdirSync, readFileSync, writeFileSync, mkdirSync, existsSync, statSync, rmSync } from "node:fs";
import { join, relative, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);

function argValue(name, fallback) {
  const i = args.indexOf(name);
  if (i !== -1 && i + 1 < args.length) return args[i + 1];
  return fallback;
}
const hasFlag = (name) => args.includes(name);

const portRoot = resolve(argValue("--port", resolve(scriptDir, "..")));
const srcRoot = resolve(argValue("--src", resolve(portRoot, "..", "portsrc")));
const jarRef = resolve(argValue("--jarref", resolve(portRoot, "..", "newer-versions", "upstream-lang")));
const outRoot = resolve(argValue("--out", join(portRoot, "src", "main", "resources-publish")));
const quiet = hasFlag("--quiet");

// port asset namespace -> upstream project folder inside the pristine source tree
// (jar references are namespace-based and need no mapping)
const NAMESPACE_TO_PROJECT = new Map([
  ["minecolonies", "minecolonies"],
  ["structurize", "structurize"],
  ["domum_ornamentum", "domum-ornamentum"],
  ["multipiston", "piston-unlimited"],
  ["blockui", "blockui"],
]);

// Namespaces whose lang must be split into novel keys (ARR — externalized via the official
// jar download). Every other namespace ships its full GPL lang file in the publish jar.
const EXTERNALIZED = ["minecolonies"];

const portLangDir = join(portRoot, "src", "main", "resources", "assets");

/** Parses JSON, throwing with the file name for a readable error. */
function parseJson(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch (e) {
    throw new Error(`invalid JSON in ${file}: ${e.message}`);
  }
}

/** Reads a flat key->string map; ignores nested objects (lang files are flat). */
function readLangMap(file) {
  const parsed = parseJson(file);
  const map = new Map();
  for (const [key, value] of Object.entries(parsed)) {
    if (typeof value === "string") map.set(key, value);
  }
  return map;
}

/** Candidate reference locations for one upstream project, most authoritative first. */
function referenceCandidates(srcRoot, project, namespace, file) {
  return [
    // 2) checked-in source-tree lang (structurize, multipiston, blockui layout)
    join(srcRoot, project, "src", "main", "resources", "assets", namespace, "lang", file),
    // 3) datagen-generated lang (minecolonies default/quests/tag.item + domum_ornamentum)
    join(srcRoot, project, "src", "datagen", "generated", project, "assets", namespace, "lang", file),
  ];
}

/**
 * Resolves the upstream reference map for one lang file.
 * @returns {{map: Map<string,string>, origin: string} | null} null when unresolved.
 */
function upstreamReference(namespace, project, file) {
  // 1) official-jar reference (authoritative — the actually distributed artifact)
  const jarFile = join(jarRef, "assets", namespace, "lang", file);
  if (existsSync(jarFile)) {
    return { map: readLangMap(jarFile), origin: relative(portRoot, jarFile) };
  }
  // 2) + 3) pristine source-tree references
  for (const srcFile of referenceCandidates(srcRoot, project, namespace, file)) {
    if (existsSync(srcFile)) {
      return { map: readLangMap(srcFile), origin: relative(portRoot, srcFile) };
    }
  }
  return null;
}

let novelTotal = 0;
let filesWritten = 0;
const unresolved = [];
const report = [];

for (const namespace of EXTERNALIZED) {
  const project = NAMESPACE_TO_PROJECT.get(namespace);
  const portLang = join(portLangDir, namespace, "lang");
  if (!existsSync(portLang)) {
    report.push(`[skip] assets/${namespace}/lang: not present in port`);
    continue;
  }

  const files = readdirSync(portLang).filter((f) => f.endsWith(".json"));
  for (const file of files) {
    const portFile = join(portLang, file);
    if (!statSync(portFile).isFile()) continue;

    // Non-runtime source templates: upstream keeps manual_en_us.json (the hand-maintained
    // CrowdIn master) in the SOURCE TREE ONLY — it is absent from every official jar, no
    // code references it, and vanilla never loads "manual_" as a locale. The publish jar
    // must not carry it; a stale output copy (if any) is removed.
    if (/^manual_[a-z]{2,3}_[a-z]{2}\.json$/.test(file)) {
      const stale = join(outRoot, "assets", namespace, "lang", file);
      if (existsSync(stale)) rmSync(stale);
      report.push(`[skip] assets/${namespace}/lang/${file}: CrowdIn source template, not a runtime lang file (upstream jar does not ship it)`);
      continue;
    }

    const portMap = readLangMap(portFile);
    const ref = upstreamReference(namespace, project, file);
    if (ref === null) {
      // SAFETY: unresolved reference — see the header comment. Skip the file entirely
      // (upstream's own distributed lang covers it in the external pack) rather than
      // writing every key as "novel" and bundling upstream text into the publish jar.
      const stale = join(outRoot, "assets", namespace, "lang", file);
      if (existsSync(stale)) {
        rmSync(stale);
        report.push(`[DROP ] assets/${namespace}/lang/${file}: stale output REMOVED (no upstream reference)`);
      }
      unresolved.push(`assets/${namespace}/lang/${file}`);
      report.push(`[WARN ] assets/${namespace}/lang/${file}: NO upstream reference found — skipped. Generate the jar reference (see PUBLISHING.md "Refreshing lang refs") and re-run.`);
      continue;
    }
    const { map: upstreamMap, origin } = ref;

    const novel = {};
    let novelCount = 0;
    // iterate in the PORT's key order for stable diffs
    for (const [key, value] of portMap) {
      if (!upstreamMap.has(key) || upstreamMap.get(key) !== value) {
        novel[key] = value;
        novelCount++;
      }
    }
    novelTotal += novelCount;

    const outFile = join(outRoot, "assets", namespace, "lang", file);
    if (novelCount > 0) {
      mkdirSync(dirname(outFile), { recursive: true });
      writeFileSync(outFile, JSON.stringify(novel, null, 4) + "\n", "utf8");
      filesWritten++;
      report.push(`[write] assets/${namespace}/lang/${file}: ${novelCount}/${portMap.size} novel keys (ref: ${origin})`);
    } else if (existsSync(outFile)) {
      report.push(`[empty] assets/${namespace}/lang/${file}: 0 novel keys (stale output left in place — delete manually if undesired)`);
    } else {
      report.push(`[empty] assets/${namespace}/lang/${file}: 0 novel keys (ref: ${origin})`);
    }
  }
}

if (!quiet) {
  for (const line of report) console.log(line);
  console.log(`\nnovel lang keys total: ${novelTotal} across ${filesWritten} file(s)`);
  if (unresolved.length > 0) {
    console.log(`\n*** WARNING — ${unresolved.length} lang file(s) had NO upstream reference and were SKIPPED:`);
    for (const f of unresolved) console.log(`    ${f}`);
    console.log(`    Their keys will be missing from the publish jar until the official-jar lang`);
    console.log(`    reference is regenerated (maintainer workflow — see this script's header).`);
  }
  console.log(`port:    ${portRoot}`);
  console.log(`src:     ${srcRoot}`);
  console.log(`jarref:  ${jarRef}`);
  console.log(`out:     ${outRoot}`);
}

// Cleanup: remove stale novel-lang outputs for namespaces that are NO LONGER externalized
// (their full GPL lang files ship bundled now — a leftover novel copy would collide with
// them as a duplicate ZIP entry inside publishJar).
const outAssets = join(outRoot, "assets");
if (existsSync(outAssets)) {
  for (const entry of readdirSync(outAssets)) {
    if (!EXTERNALIZED.includes(entry)) {
      rmSync(join(outAssets, entry), { recursive: true, force: true });
      if (!quiet) console.log(`[clean] assets/${entry}: stale novel-lang output REMOVED (namespace ships full GPL lang bundled)`);
    }
  }
}
