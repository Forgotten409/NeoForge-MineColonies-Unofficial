#!/usr/bin/env node
/**
 * towntalk-patch.mjs — PORT26 publishing support (no dependencies, Node 20.15+ / Bun, Windows-safe).
 *
 * Rewrites the dependency version ranges of a TownTalk mod jar built for MC 1.21.1 so it can
 * LOAD on MC 26.1.2 next to this port's merged mod ("minecolonies", which contains the former
 * structurize / blockui / domum_ornamentum / multipiston projects):
 *
 *   META-INF/neoforge.mods.toml (and legacy META-INF/mods.toml):
 *     - minecraft            versionRange -> "[1.21,)"
 *     - minecolonies         versionRange -> "[1.1.0,)"
 *     - neoforge             versionRange -> lower bound kept, upper bound removed
 *                             (default "[21.1.0,)" when unparseable)
 *     - structurize / blockui / domum_ornamentum / multipiston dependencies are RETARGETED
 *       to modId "minecolonies" + "[1.1.0,)" — those mod ids no longer exist separately
 *       (merged mod); a range patch alone would still fail with "missing dependency".
 *     - loaderVersion (top level) widened to "[4,)" when narrower.
 *
 *   fabric.mod.json (if present):
 *     - "depends": minecraft -> ">=1.21", minecolonies -> ">=1.1.0", merged mod ids
 *       retargeted to "minecolonies", neoforge/forge lower bound kept / "*".
 *
 *   Jar signing sections (META-INF/*.SF, *.RSA, *.DSA) are dropped — any existing signature
 *   would be invalidated by the rewrite (classic jarsigner removal behavior).
 *
 * Everything else is copied ENTRY-BY-ENTRY with the ORIGINAL COMPRESSED BYTES (byte-identical
 * where possible; only the patched text files are recompressed).
 *
 * The zip reader/writer is implemented manually over the central directory (node:zlib for
 * deflate) — no npm dependencies, runs on plain `node` or `bun` on Windows/Linux/macOS.
 *
 * HONEST RISK NOTE (see docs/PUBLISHING.md): patching only fixes the LOAD GATES. TownTalk's
 * own MC 1.21.1 internals may still crash or misbehave on 26.1.2 — test in-game before
 * shipping a patched jar to players.
 *
 * Usage:
 *   node tools/towntalk-patch.mjs <input.jar> [-o <output.jar>] [--dry-run] [--quiet]
 *     -o        output jar (default: <input>-patched.jar)
 *     --dry-run patch in memory, print the planned changes, write nothing
 *
 * Exit codes: 0 = patched (or dry-run ok), 1 = usage/IO/zip error,
 *             2 = no patchable metadata found (nothing written).
 */

import { readFileSync, writeFileSync, existsSync, statSync } from "node:fs";
import { dirname, join, resolve, basename } from "node:path";
import { deflateRawSync, inflateRawSync } from "node:zlib";
import { fileURLToPath } from "node:url";

/** CRC-32 (IEEE), table-based pure JS — no zlib.crc32 version requirement, same result. */
const crc32 = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return (buf) => {
    let c = 0 ^ -1;
    for (let i = 0; i < buf.length; i++) c = (c >>> 8) ^ table[(c ^ buf[i]) & 0xff];
    return (c ^ -1) >>> 0;
  };
})();

const scriptDir = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const quiet = args.includes("--quiet");
const dryRun = args.includes("--dry-run");

function argValue(flag) {
  const i = args.indexOf(flag);
  if (i !== -1 && i + 1 < args.length) return args[i + 1];
  return null;
}

const input = args.find((a) => !a.startsWith("-"));
if (!input) {
  console.error("usage: node tools/towntalk-patch.mjs <input.jar> [-o <output.jar>] [--dry-run] [--quiet]");
  process.exit(1);
}
const inputPath = resolve(input);
if (!existsSync(inputPath) || !statSync(inputPath).isFile()) {
  console.error(`error: input jar not found: ${inputPath}`);
  process.exit(1);
}
const outputPath = dryRun
  ? null
  : resolve(argValue("-o") ?? join(dirname(inputPath), basename(inputPath).replace(/\.jar$/i, "") + "-patched.jar"));

// ---------------------------------------------------------------------------
// Version-range helpers (NeoForge uses Maven-style ranges: "[1.21.1]", "[21.1.80,)",
// "(,1.21]", "1.21.1", or comma lists. We only need the lower bound.)
// ---------------------------------------------------------------------------

/** @returns {string|null} lower bound of a maven-style range, or null when unparseable */
function lowerBound(range) {
  if (typeof range !== "string") return null;
  const r = range.trim();
  if (r.length === 0) return null;
  // "[A,B)" / "[A,B]" / "(A,B)" / "[A,)" / "[A]" / "A"
  const bracket = r.match(/^[[({]\s*([^,\]\})]+)\s*(?:,([^\]\})]*))?[\]\})]$/);
  if (bracket) {
    const lower = bracket[1].trim();
    if (lower.length > 0) return lower;
    return null; // "(,1.21]" — no lower bound
  }
  return r; // bare version
}

const MINECOLONIES_RANGE = "[1.1.0,)";
const MINECRAFT_RANGE = "[1.21,)";
const NEOFORGE_DEFAULT_RANGE = "[21.1.0,)";
/** mod ids that were merged into this port's single "minecolonies" mod */
const MERGED_MOD_IDS = new Set(["structurize", "blockui", "domum_ornamentum", "multipiston"]);

// ---------------------------------------------------------------------------
// TOML patching (line-based — keeps every other line byte-identical)
// ---------------------------------------------------------------------------

const TOML_FILES = ["META-INF/neoforge.mods.toml", "META-INF/mods.toml"];

/**
 * Patches one dependencies TOML. Strategy: walk the lines tracking the current
 * [[dependencies.<id>]] section; remember the last `modId` seen in the section; rewrite the
 * `versionRange = "..."` line when that modId is one we widen/retarget; rewrite the modId line
 * itself when it names a merged mod id; rewrite a top-level loaderVersion when narrower.
 *
 * @param {string} text
 * @returns {{text: string, changes: string[]}}
 */
function patchModsToml(text) {
  const lines = text.split(/\r?\n/);
  const changes = [];

  let section = null; // null = top level, "dep" = inside a [[dependencies.*]] block
  let sectionModId = null;
  let loaderVersionLine = -1;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    const depHeader = trimmed.match(/^\[\[dependencies\.([A-Za-z0-9_.-]+)\]\]/);
    if (depHeader) {
      section = "dep";
      sectionModId = depHeader[1];
      continue;
    }
    if (trimmed.startsWith("[[") || (trimmed.startsWith("[") && !trimmed.startsWith("[["))) {
      section = trimmed.startsWith("[[") ? "other" : null;
      sectionModId = null;
      // fallthrough: table headers also terminate a dependency block
    }

    const modIdMatch = trimmed.match(/^modId\s*=\s*"([^"]*)"/);
    if (modIdMatch && section === "dep") {
      sectionModId = modIdMatch[1];
      if (MERGED_MOD_IDS.has(sectionModId)) {
        lines[i] = line.replace(/(modId\s*=\s*)"[^"]*"/, `$1"minecolonies"`);
        changes.push(`dependency '${sectionModId}' -> retargeted modId to 'minecolonies'`);
        sectionModId = "minecolonies";
      }
      continue;
    }

    const rangeMatch = trimmed.match(/^versionRange\s*=\s*"([^"]*)"/);
    if (rangeMatch && section === "dep" && sectionModId) {
      const original = rangeMatch[1];
      if (sectionModId === "minecraft") {
        lines[i] = line.replace(/(versionRange\s*=\s*)"[^"]*"/, `$1"${MINECRAFT_RANGE}"`);
        changes.push(`minecraft versionRange '${original}' -> '${MINECRAFT_RANGE}'`);
      } else if (sectionModId === "minecolonies") {
        lines[i] = line.replace(/(versionRange\s*=\s*)"[^"]*"/, `$1"${MINECOLONIES_RANGE}"`);
        changes.push(`minecolonies versionRange '${original}' -> '${MINECOLONIES_RANGE}'`);
      } else if (sectionModId === "neoforge" || sectionModId === "forge") {
        const lower = lowerBound(original) ?? "21.1.0";
        const widened = `[${lower},)`;
        if (widened !== original) {
          lines[i] = line.replace(/(versionRange\s*=\s*)"[^"]*"/, `$1"${widened}"`);
          changes.push(`${sectionModId} versionRange '${original}' -> '${widened}'`);
        } // else: already an open range including our target — leave untouched (byte-identical)
      }
      continue;
    }

    const loaderMatch = trimmed.match(/^loaderVersion\s*=\s*"([^"]*)"/);
    if (loaderMatch && section === null) {
      loaderVersionLine = i;
      const original = loaderMatch[1];
      const lower = lowerBound(original);
      const lowerNum = parseFloat(lower ?? "4");
      if (Number.isNaN(lowerNum) || lowerNum < 4) {
        lines[i] = line.replace(/(loaderVersion\s*=\s*)"[^"]*"/, `$1"[4,)"`);
        changes.push(`loaderVersion '${original}' -> '[4,)'`);
      }
      continue;
    }
  }
  if (loaderVersionLine === -1 && section === null) {
    // no loaderVersion found — leave as-is (inherited default is fine on 26.1.2)
  }

  return { text: lines.join("\n"), changes };
}

// ---------------------------------------------------------------------------
// fabric.mod.json patching (JSON-aware, but only inside "depends")
// ---------------------------------------------------------------------------

const FABRIC_FILE = "fabric.mod.json";

/**
 * @param {string} text
 * @returns {{text: string, changes: string[]}}
 */
function patchFabricModJson(text) {
  /** @type {any} */ let parsed;
  try {
    parsed = JSON.parse(text);
  } catch (e) {
    return { text, changes: [`fabric.mod.json is invalid JSON — left unpatched (${e.message})`] };
  }
  const changes = [];
  const depends = parsed?.depends;
  if (depends && typeof depends === "object" && !Array.isArray(depends)) {
    const out = {};
    for (const [key, value] of Object.entries(depends)) {
      if (key === "minecraft") {
        out[key] = ">=1.21";
        changes.push(`fabric depends.minecraft '${JSON.stringify(value)}' -> '>=1.21'`);
      } else if (MERGED_MOD_IDS.has(key)) {
        out.minecolonies = out.minecolonies ?? ">=1.1.0";
        changes.push(`fabric depends.${key} '${JSON.stringify(value)}' -> retargeted to minecolonies '>=1.1.0'`);
      } else if (key === "minecolonies") {
        out[key] = ">=1.1.0";
        changes.push(`fabric depends.minecolonies '${JSON.stringify(value)}' -> '>=1.1.0'`);
      } else if (key === "neoforge" || key === "forge") {
        const lower = typeof value === "string" ? (value.replace(/^>=?\s*/, "") || "21.1.0") : "21.1.0";
        out[key] = `>=${lower}`;
        changes.push(`fabric depends.${key} '${JSON.stringify(value)}' -> '>=${lower}'`);
      } else {
        out[key] = value;
      }
    }
    parsed.depends = out;
  }
  return { text: JSON.stringify(parsed, null, 2) + "\n", changes };
}

// ---------------------------------------------------------------------------
// Minimal zip reader (central directory) + writer
// ---------------------------------------------------------------------------

const EOCD_SIG = 0x06054b50;
const CEN_SIG = 0x02014b50;
const LOC_SIG = 0x04034b50;

/** @returns {{buf: Buffer, entries: Array, cdOffset: number}} */
function readZip(buf) {
  // locate the End Of Central Directory record (scan backwards, max 64 KiB + 22)
  const scanStart = Math.max(0, buf.length - (0xffff + 22));
  let eocd = -1;
  for (let i = buf.length - 22; i >= scanStart; i--) {
    if (buf.readUInt32LE(i) === EOCD_SIG) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) throw new Error("not a zip archive (no EOCD record found)");
  const totalEntries = buf.readUInt16LE(eocd + 10);
  const cdOffset = buf.readUInt32LE(eocd + 16);

  const entries = [];
  let p = cdOffset;
  for (let n = 0; n < totalEntries; n++) {
    if (buf.readUInt32LE(p) !== CEN_SIG) throw new Error(`corrupt central directory at entry ${n}`);
    const method = buf.readUInt16LE(p + 10);
    const flags = buf.readUInt16LE(p + 8);
    const crc = buf.readUInt32LE(p + 16);
    const compSize = buf.readUInt32LE(p + 20);
    const uncompSize = buf.readUInt32LE(p + 24);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    const localOffset = buf.readUInt32LE(p + 42);
    const name = buf.toString("utf8", p + 46, p + 46 + nameLen);
    entries.push({ name, method, flags, crc, compSize, uncompSize, localOffset, externalAttrs: buf.readUInt32LE(p + 38) });
    p += 46 + nameLen + extraLen + commentLen;
  }
  return { entries };
}

/** Reads one entry's raw (still compressed) bytes using its local header. */
function readEntryRaw(buf, entry) {
  const off = entry.localOffset;
  if (buf.readUInt32LE(off) !== LOC_SIG) throw new Error(`corrupt local header for ${entry.name}`);
  const nameLen = buf.readUInt16LE(off + 26);
  const extraLen = buf.readUInt16LE(off + 28);
  const dataStart = off + 30 + nameLen + extraLen;
  return buf.subarray(dataStart, dataStart + entry.compSize);
}

/** Decompresses one entry to a Buffer. */
function readEntryText(buf, entry) {
  const raw = readEntryRaw(buf, entry);
  if (entry.method === 0) return raw.toString("utf8");
  if (entry.method === 8) return inflateRawSync(raw).toString("utf8");
  throw new Error(`unsupported compression method ${entry.method} for ${entry.name}`);
}

/** Builds a zip from [{name, method, flags, crc, compSize, uncompSize, data(Buffer)}]. */
function writeZip(outEntries) {
  const chunks = [];
  const central = [];
  let offset = 0;
  for (const e of outEntries) {
    const nameBuf = Buffer.from(e.name, "utf8");
    const flags = e.flags & ~0x0008 & ~0x0001; // clear data-descriptor + encryption bits
    // local file header
    const lfh = Buffer.alloc(30);
    lfh.writeUInt32LE(LOC_SIG, 0);
    lfh.writeUInt16LE(20, 4); // version needed
    lfh.writeUInt16LE(flags, 6);
    lfh.writeUInt16LE(e.method, 8);
    lfh.writeUInt16LE(0, 10); // mod time
    lfh.writeUInt16LE(0x21, 12); // fixed date (1980-01-01) — deterministic output
    lfh.writeUInt32LE(e.crc, 14);
    lfh.writeUInt32LE(e.data.length, 18);
    lfh.writeUInt32LE(e.uncompSize, 22);
    lfh.writeUInt16LE(nameBuf.length, 26);
    lfh.writeUInt16LE(0, 28); // no extra
    chunks.push(lfh, nameBuf, e.data);

    // central directory record
    const cdh = Buffer.alloc(46);
    cdh.writeUInt32LE(CEN_SIG, 0);
    cdh.writeUInt16LE(0x031e, 4); // made by unix/java-ish
    cdh.writeUInt16LE(20, 6);
    cdh.writeUInt16LE(flags, 8);
    cdh.writeUInt16LE(e.method, 10);
    cdh.writeUInt16LE(0, 12);
    cdh.writeUInt16LE(0x21, 14);
    cdh.writeUInt32LE(e.crc, 16);
    cdh.writeUInt32LE(e.data.length, 20);
    cdh.writeUInt32LE(e.uncompSize, 24);
    cdh.writeUInt16LE(nameBuf.length, 28);
    cdh.writeUInt16LE(0, 30); // extra
    cdh.writeUInt16LE(0, 32); // comment
    cdh.writeUInt16LE(0, 34); // disk
    cdh.writeUInt16LE(0, 36); // internal attrs
    cdh.writeUInt32LE(e.externalAttrs ?? 0, 38);
    cdh.writeUInt32LE(offset, 42);
    central.push(Buffer.concat([cdh, nameBuf]));

    offset += 30 + nameBuf.length + e.data.length;
  }

  const cdStart = offset;
  const cdBuf = Buffer.concat(central);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(EOCD_SIG, 0);
  eocd.writeUInt16LE(0, 4);
  eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(outEntries.length, 8);
  eocd.writeUInt16LE(outEntries.length, 10);
  eocd.writeUInt32LE(cdBuf.length, 12);
  eocd.writeUInt32LE(cdStart, 16);
  eocd.writeUInt16LE(0, 20);

  return Buffer.concat([...chunks, cdBuf, eocd]);
}

/** Recompresses text content as a deflated zip entry. */
function deflateEntry(name, text, externalAttrs = 0) {
  const content = Buffer.from(text, "utf8");
  const data = deflateRawSync(content, { level: 9 });
  return {
    name,
    method: 8,
    flags: 0x0800, // utf-8 names
    crc: crc32(content) >>> 0,
    uncompSize: content.length,
    data,
    externalAttrs,
  };
}

/** Drops jar signature files that the rewrite would invalidate. */
function isSignatureFile(name) {
  return /^META-INF\/[^/]+\.(SF|RSA|DSA|EC)$/i.test(name);
}

// ---------------------------------------------------------------------------
// main (wrapped for clean error reporting)
// ---------------------------------------------------------------------------

try {
  main();
} catch (e) {
  console.error(`error: ${e.message}`);
  if (!quiet) console.error(e.stack);
  process.exit(1);
}

function main() {
const jar = readFileSync(inputPath);
const { entries } = readZip(jar);

const patchTargets = new Map(); // entry name -> {patch: (text:string)=>{text,changes}}
for (const entry of entries) {
  const lower = entry.name.toLowerCase();
  if (TOML_FILES.includes(entry.name)) patchTargets.set(entry.name, patchModsToml);
  else if (lower === FABRIC_FILE && entry.name === FABRIC_FILE) patchTargets.set(entry.name, patchFabricModJson);
}
if (patchTargets.size === 0) {
  console.error(`error: no META-INF/neoforge.mods.toml / mods.toml / fabric.mod.json found in ${basename(inputPath)} — is this a TownTalk mod jar?`);
  process.exit(2);
}

const allChanges = [];
const outEntries = [];
let droppedSignatures = 0;

for (const entry of entries) {
  if (isSignatureFile(entry.name)) {
    droppedSignatures++;
    continue; // signature would be invalid after the rewrite
  }
  if (patchTargets.has(entry.name)) {
    const text = readEntryText(jar, entry);
    const { text: patched, changes } = patchTargets.get(entry.name)(text);
    allChanges.push(`${entry.name}:`, ...changes.map((c) => `    ${c}`));
    outEntries.push(deflateEntry(entry.name, patched, entry.externalAttrs));
  } else {
    // copy the ORIGINAL compressed bytes — byte-identical content, zero recompression risk
    const raw = readEntryRaw(jar, entry);
    outEntries.push({
      name: entry.name,
      method: entry.method,
      flags: entry.flags,
      crc: entry.crc,
      uncompSize: entry.uncompSize,
      data: Buffer.from(raw), // copy (subarray view would also work; copy keeps it simple)
      externalAttrs: entry.externalAttrs,
    });
  }
}

if (droppedSignatures > 0) {
  allChanges.push(`signing: dropped ${droppedSignatures} META-INF signature file(s) (invalidated by the rewrite)`);
}

if (allChanges.length === 0) {
  if (!quiet) console.log(`nothing to patch in ${basename(inputPath)} — ranges already compatible.`);
  process.exit(0);
}

if (!quiet) {
  console.log(`TownTalk jar patcher — ${basename(inputPath)}`);
  for (const line of allChanges) console.log(`  ${line}`);
}

if (dryRun) {
  if (!quiet) console.log(`dry run — no output written (${outEntries.length} entries would be written)`);
  process.exit(0);
}

const outJar = writeZip(outEntries);
writeFileSync(outputPath, outJar);
if (!quiet) {
  console.log(`wrote ${outputPath} (${outEntries.length} entries, ${(outJar.length / 1024 / 1024).toFixed(2)} MiB)`);
  console.log(`NOTE: this only fixes the LOAD GATES — TownTalk's 1.21.1 internals may still crash on 26.1.2. Test in-game.`);
}
process.exit(0);
} // main()
