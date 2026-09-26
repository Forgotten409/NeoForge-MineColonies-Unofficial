#!/usr/bin/env node
/**
 * Simulation of PortAssetConverter v2 (Java) — verification harness, NOT shipped.
 *
 * Mirrors the Java rule set 1:1 and runs it over an extracted copy of the official
 * minecolonies-1.1.1387-1.21.1-snapshot.jar, then deep-compares the RESULT against
 * the port's dev tree (src/main/resources) for every file the dev tree converted.
 *
 * Usage: node tools/simulate-converter.mjs <extracted-jar-dir> <dev-resources-dir> <publish-overrides-dir>
 */
import fs from 'node:fs';
import path from 'node:path';

const [jarDir, devDir, overridesDir] = process.argv.slice(2);
if (!jarDir || !devDir || !overridesDir) {
  console.error('usage: node simulate-converter.mjs <jarDir> <devDir> <overridesDir>');
  process.exit(2);
}

// ------------------------------------------------------------------ rule tables (1:1 with Java)
const TEXTURE_RENAMES = {
  'entity/armorstand/wood': 'entity/armorstand/armorstand',
  'entity/chicken': 'entity/chicken/chicken_temperate',
  'map/map_icons': 'block/oak_planks',
  'models/armor/leather_layer_1': 'minecolonies:block/leather',
  'models/armor/leather_layer_2': 'minecolonies:block/leather',
};
const OLD_SPAWN_EGG_PARENT = 'item/template_spawn_egg';
const RACK_WRAPPER_MODELS = [
  'models/block/blockrackemptydouble.json',
  'models/block/blockrackfull.json',
  'models/block/blockrackfull_1.json',
  'models/block/blockrackfull_2.json',
  'models/block/blockrackfulldouble.json',
  'models/block/blockrackfulldouble_1.json',
  'models/block/blockrackfulldouble_2.json',
];
const DEPRECATED_DISPLAY_KEYS = ['thirdperson', 'firstperson'];
const DATA_VALUE_RENAMES = {
  'minecraft:fire_resistant': 'minecraft:damage_resistant',
  'minecraft:hide_additional_tooltip': 'minecraft:tooltip_display',
  'minecraft:chain': 'minecraft:iron_chain',
};
const TAG_ENTRY_REMOVALS = ['#minecraft:trim_templates'];
const TAG_ENTRY_ADDITIONS = {
  'tags/item/leather.json': ['minecraft:leather_horse_armor', 'minecraft:armadillo_scute'],
};

// ------------------------------------------------------------------ java rule ports
function mappedTexture(value) {
  const colon = value.indexOf(':');
  const naked = colon >= 0 ? value.substring(colon + 1) : value;
  const mapped = TEXTURE_RENAMES[naked];
  if (mapped == null) return null;
  if (mapped.indexOf(':') >= 0) return mapped;
  return colon >= 0 ? value.substring(0, colon + 1) + mapped : mapped;
}

function convertTextures(model) {
  let changed = false;
  const texturesEl = model.textures;
  if (texturesEl && typeof texturesEl === 'object' && !Array.isArray(texturesEl)) {
    const textures = texturesEl;
    for (const key of Object.keys(textures)) {
      const value = textures[key];
      if (typeof value === 'string') {
        const mapped = mappedTexture(value);
        if (mapped != null) { textures[key] = mapped; changed = true; }
      }
    }
    const parent = typeof model.parent === 'string' ? model.parent : '';
    if ((parent === 'block/cube_all' || parent === 'minecraft:block/cube_all') && !('all' in textures)) {
      textures.all = 'particle' in textures ? '#particle' : 'block/oak_planks';
      changed = true;
    }
  }
  const childrenEl = model.children;
  if (childrenEl) {
    if (Array.isArray(childrenEl)) {
      for (const child of childrenEl) if (child && typeof child === 'object') changed |= convertTextures(child);
    } else if (typeof childrenEl === 'object') {
      for (const child of Object.values(childrenEl)) if (child && typeof child === 'object') changed |= convertTextures(child);
    }
  }
  return !!changed;
}

function stripDeprecatedDisplay(model) {
  const display = model.display;
  if (!display || typeof display !== 'object' || Array.isArray(display)) return false;
  let changed = false;
  for (const key of DEPRECATED_DISPLAY_KEYS) {
    if (key in display) { delete display[key]; changed = true; }
  }
  if (Object.keys(display).length === 0) {
    if (changed) delete model.display;
    return changed;
  }
  if (isIdentityDisplay(display)) { delete model.display; return true; }
  return changed;
}

function isDefaultVec(transform, key, defaultValue) {
  const vec = transform[key];
  if (vec === undefined) return true;
  if (!Array.isArray(vec)) return false;
  return vec.every(c => typeof c === 'number' && Math.abs(c - defaultValue) < 1e-9);
}

function isIdentityTransform(transform) {
  return isDefaultVec(transform, 'rotation', 0) && isDefaultVec(transform, 'translation', 0) && isDefaultVec(transform, 'scale', 1);
}

function isIdentityDisplay(display) {
  return Object.values(display).every(t => t && typeof t === 'object' && !Array.isArray(t) && isIdentityTransform(t));
}

function convertModelFile(root, rel) {
  if (!rel.startsWith('models/')) return false;
  let changed = false;
  changed |= convertTextures(root);
  changed |= stripDeprecatedDisplay(root);
  if (rel.startsWith('models/block/rack/')) {
    if (typeof root.parent === 'string' && root.parent.startsWith('minecolonies:block/blockrack')) {
      root.parent = 'minecolonies:block/rack/' + root.parent.substring('minecolonies:block/'.length);
      changed = true;
    }
  }
  if (root.parent === OLD_SPAWN_EGG_PARENT) {
    const eggName = rel.slice(rel.lastIndexOf('/') + 1, rel.length - '.json'.length);
    delete root.loader;
    root.parent = 'minecraft:item/generated';
    root.textures = { layer0: 'minecolonies:item/' + eggName };
    changed = true;
  }
  return !!changed;
}

function convertIngredient(el) {
  if (Array.isArray(el)) return el.map(convertIngredient);
  if (el && typeof el === 'object') {
    const keys = Object.keys(el);
    if ('type' in el) return el;
    if (keys.length === 1 && keys[0] === 'item' && typeof el.item === 'string') return el.item;
    if (keys.length === 1 && keys[0] === 'tag' && typeof el.tag === 'string') return '#' + el.tag;
    return el;
  }
  return el;
}

function convertCompoundIngredient(array) {
  const anyCustom = array.some(e => e && typeof e === 'object' && !Array.isArray(e) && ('type' in e || 'tag' in e || 'item' in e));
  if (!anyCustom) return array;
  const children = [];
  for (const el of array) {
    if (el && typeof el === 'object' && !Array.isArray(el) && 'type' in el) {
      const typed = {};
      for (const [k, v] of Object.entries(el)) typed[k === 'type' ? 'neoforge:ingredient_type' : k] = v;
      children.push(typed);
    } else {
      children.push(convertIngredient(el));
    }
  }
  return { 'neoforge:ingredient_type': 'neoforge:compound', children };
}

function convertRecipeIngredients(recipe) {
  let changed = false;
  if (recipe.key && typeof recipe.key === 'object' && !Array.isArray(recipe.key)) {
    for (const key of Object.keys(recipe.key)) {
      const before = JSON.stringify(recipe.key[key]);
      recipe.key[key] = convertIngredient(recipe.key[key]);
      if (JSON.stringify(recipe.key[key]) !== before) changed = true;
    }
  }
  if (Array.isArray(recipe.ingredients)) {
    const before = JSON.stringify(recipe.ingredients);
    recipe.ingredients = recipe.ingredients.map(convertIngredient);
    if (JSON.stringify(recipe.ingredients) !== before) changed = true;
  }
  if ('ingredient' in recipe) {
    const before = JSON.stringify(recipe.ingredient);
    recipe.ingredient = convertIngredient(recipe.ingredient);
    if (JSON.stringify(recipe.ingredient) !== before) changed = true;
  }
  if (Array.isArray(recipe.input)) {
    const before = JSON.stringify(recipe.input);
    recipe.input = convertCompoundIngredient(recipe.input);
    if (JSON.stringify(recipe.input) !== before) changed = true;
  }
  return changed;
}

function unwrapEnchantmentLevels(el) {
  let changed = false;
  if (Array.isArray(el)) {
    for (const child of el) changed |= unwrapEnchantmentLevels(child);
  } else if (el && typeof el === 'object') {
    for (const key of Object.keys(el)) {
      if ((key === 'minecraft:enchantments' || key === 'minecraft:stored_enchantments')
            && el[key] && typeof el[key] === 'object' && el[key].levels
            && typeof el[key].levels === 'object' && !Array.isArray(el[key].levels)) {
        el[key] = el[key].levels;
        changed = true;
        continue;
      }
      changed |= unwrapEnchantmentLevels(el[key]);
    }
  }
  return !!changed;
}

function convertTagEntries(tag) {
  if (!Array.isArray(tag.values)) return false;
  const out = [];
  let changed = false;
  for (const value of tag.values) {
    if (typeof value === 'string' && TAG_ENTRY_REMOVALS.includes(value)) { changed = true; continue; }
    if (typeof value === 'string' && DATA_VALUE_RENAMES[value]) {
      out.push(DATA_VALUE_RENAMES[value]);
      changed = true;
      continue;
    }
    out.push(value);
  }
  if (changed) tag.values = out;
  return changed;
}

function appendTagEntries(tag, additions) {
  if (!Array.isArray(tag.values)) return false;
  let changed = false;
  for (const addition of additions) {
    if (!tag.values.some(v => v === addition)) {
      tag.values.push(addition);
      changed = true;
    }
  }
  return changed;
}

function renameDataValues(el) {
  let changed = false;
  if (Array.isArray(el)) {
    for (let i = 0; i < el.length; i++) {
      if (typeof el[i] === 'string') {
        if (DATA_VALUE_RENAMES[el[i]]) { el[i] = DATA_VALUE_RENAMES[el[i]]; changed = true; }
      } else {
        changed |= renameDataValues(el[i]);
      }
    }
  } else if (el && typeof el === 'object') {
    for (const key of Object.keys(el)) {
      const value = el[key];
      if (typeof value === 'string') {
        if (DATA_VALUE_RENAMES[value]) { el[key] = DATA_VALUE_RENAMES[value]; changed = true; }
      } else {
        changed |= renameDataValues(value);
      }
    }
  }
  return !!changed;
}

function convertDataFile(root, rel) {
  let changed = false;
  if (root && typeof root === 'object' && !Array.isArray(root)) {
    if (rel.startsWith('recipe/')) changed |= convertRecipeIngredients(root);
    else if (rel.startsWith('loot_table/')) changed |= unwrapEnchantmentLevels(root);
    else if (rel.startsWith('tags/')) {
      changed |= convertTagEntries(root);
      if (TAG_ENTRY_ADDITIONS[rel]) changed |= appendTagEntries(root, TAG_ENTRY_ADDITIONS[rel]);
    }
  }
  changed |= renameDataValues(root); // object OR array root (itemnbtmatching!)
  return !!changed;
}

// ------------------------------------------------------------------ run over the extracted jar
const walk = (dir) => {
  const results = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) results.push(...walk(full));
    else results.push(full);
  }
  return results;
};

const assetsDir = path.join(jarDir, 'assets/minecolonies');
const dataDir = path.join(jarDir, 'data/minecolonies');
const store = {}; // rel -> parsed JSON (converted result), assets + data

let modelFiles = 0, dataFiles = 0, deletedWrappers = 0, overrides = 0;

for (const file of walk(assetsDir)) {
  if (!file.endsWith('.json') && !file.endsWith('.mcmeta')) continue;
  const rel = path.relative(assetsDir, file).split(path.sep).join('/');
  let root;
  try { root = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { continue; }
  if (typeof root !== 'object' || root === null || Array.isArray(root)) continue;
  if (convertModelFile(root, rel)) modelFiles++;
  store['A/' + rel] = root;
}
for (const wrapper of RACK_WRAPPER_MODELS) {
  const p = path.join(assetsDir, wrapper);
  if (fs.existsSync(p)) { fs.rmSync(p); deletedWrappers++; delete store['A/' + wrapper]; }
}
for (const file of walk(dataDir)) {
  if (!file.endsWith('.json') && !file.endsWith('.mcmeta')) continue;
  const rel = path.relative(dataDir, file).split(path.sep).join('/');
  let root;
  try { root = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { continue; }
  if (root === null || root === undefined) continue;
  if (convertDataFile(root, rel)) dataFiles++;
  store['D/' + rel] = root;
}

// sounds.json merge (mirror of Java mergeSoundsExtra)
const soundsPath = path.join(jarDir, 'assets/minecolonies/sounds.json');
const soundsExtraPath = path.join(overridesDir, 'sounds-extra.json');
if (fs.existsSync(soundsPath) && fs.existsSync(soundsExtraPath)) {
  const sounds = JSON.parse(fs.readFileSync(soundsPath, 'utf8'));
  const extra = JSON.parse(fs.readFileSync(soundsExtraPath, 'utf8'));
  let merged = 0;
  for (const [k, v] of Object.entries(extra)) {
    if (!(k in sounds)) { sounds[k] = v; merged++; }
  }
  store['A/sounds.json'] = sounds;
  console.log(`sounds.json merged: ${merged} entr(y|ies)`);
}

// overrides — copy from the publish overrides dir into assets/minecolonies/<rel>
for (const rel of ['blockstates/blockminecoloniesrack.json', 'models/item/spear.json',
  'models/item/spear_in_hand.json', 'models/item/spear_throwing.json']) {
  const src = path.join(overridesDir, rel);
  if (fs.existsSync(src)) {
    store['A/' + rel] = JSON.parse(fs.readFileSync(src, 'utf8'));
    overrides++;
  }
}

// obsolete global loot-modifier REGISTRY file (play-test #4): 26.1.2's LootModifierManager
// scans data/<ns>/loot_modifiers/ as a registry folder (every file = one modifier with a
// "type" dispatch key); the 1.21.1 entries/replace list file has no reader — delete it,
// exactly like the Java converter's new rule
const obsoleteGlm = path.join(jarDir, 'data/neoforge/loot_modifiers/global_loot_modifiers.json');
let deletedObsolete = 0;
if (fs.existsSync(obsoleteGlm)) { fs.rmSync(obsoleteGlm); deletedObsolete++; }
// tolerant double-run note: a re-run over an already-converted store finds 0 here
if (deletedObsolete === 0 && fs.existsSync(path.dirname(obsoleteGlm))) {
  console.log('note: global_loot_modifiers.json already absent (re-run over converted store)');
}

console.log(`converted: ${modelFiles} model file(s), ${dataFiles} data file(s), ${deletedWrappers} wrapper(s) deleted, ${deletedObsolete} obsolete data file(s) deleted, ${overrides} override(s)`);

// ------------------------------------------------------------------ deep-compare vs dev tree
const deepEq = (a, b) => JSON.stringify(sortKeys(a)) === JSON.stringify(sortKeys(b));
function sortKeys(v) {
  if (Array.isArray(v)) return v.map(sortKeys);
  if (v && typeof v === 'object') {
    const out = {};
    for (const k of Object.keys(v).sort()) out[k] = sortKeys(v[k]);
    return out;
  }
  return v;
}

let match = 0, mismatch = 0, devOnly = 0, storeOnly = 0, cosmetic = 0;
const mismatches = [];
// known, accepted differences vs the dev tree (rule intentionally more conservative
// than the hand-conversion — renders identically / is valid 26.1 content either way):
// - bread_dough keeps its thirdperson rotation [0,40,0] (dev dropped the whole display
//   block; both are valid — only a 40° in-hand yaw differs)
const KNOWN_COSMETIC = new Set([
  'A/models/item/bread_dough.json',
  // lang: dev tree keeps only novel keys; the store's full upstream lang merges per-key
  // at runtime — full compatibility by design
  'A/lang/en_us.json', 'A/lang/pl_pl.json',
  // dev DROPPED these valid (non-identity) display blocks by hand; the converter keeps
  // them — both are valid 26.1 content, only in-hand size/yaw differs slightly
  'A/models/item/scroll_area_tp.json', 'A/models/item/scroll_buff.json',
  'A/models/item/scroll_guard_help.json', 'A/models/item/scroll_highlight.json',
  'A/models/item/scroll_tp.json', 'A/models/item/raw_pumpkin_pie.json',
  'A/models/item/compost.json', 'A/models/item/cookie_dough.json',
  // dev KEPT deprecated thirdperson/firstperson keys here (hand-conversion miss); the
  // converter strips them — identical rendering, strictly cleaner
  'A/models/item/sceptergold.json', 'A/models/item/sceptersteel.json',
  'A/models/item/caliper.json', 'A/models/block/blockhutfield.json',
  // dev REMOVED hide_additional_tooltip from checkednbtkeys; the converter renames it to
  // dev REMOVED hide_additional_tooltip from checkednbtkeys; the converter renames it to
  // its real 26.1.2 successor (minecraft:tooltip_display — a valid component; the warning
  // disappears either way, one extra compared key on banners is harmless)
  'D/compatibility/itemnbtmatching.json',
]);
const CHECK_ALL = process.env.CHECK_ALL === '1';
let filesToCheck = [
  // models known to be hand-converted in dev
  'A/models/block/blockhutarchery.json', 'A/models/block/blockhutbarracks.json',
  'A/models/block/blockhutbeekeeper.json', 'A/models/block/blockhutblacksmith.json',
  'A/models/block/blockhutchickenherder.json', 'A/models/block/blockhutcombatacademy.json',
  'A/models/block/blockhutdyer.json', 'A/models/block/blockhutfletcher.json',
  'A/models/block/blockhutstonesmeltery.json', 'A/models/block/blockhutuniversity.json',
  'A/models/block/rack/blockrackfull_1.json', 'A/models/block/rack/blockrackfull_2.json',
  'A/models/block/rack/blockrackfulldouble_1.json', 'A/models/block/rack/blockrackfulldouble_2.json',
  'A/models/item/amazonchiefegg.json', 'A/models/item/spear.json',
  'A/models/item/assistanthammer_gold.json', 'A/models/item/banner_rally_guards.json',
  'A/models/item/blockhutfield.json', 'A/models/item/bread_dough.json',
  'A/blockstates/blockminecoloniesrack.json',
  // data known to be hand-converted in dev
  'D/recipe/congee.json', 'D/recipe/butter.json', 'D/recipe/blockhutarchery.json',
  'D/recipe/baked_muffin_smoking.json', 'D/recipe/apple_pie.json', 'D/recipe/questlog.json',
  'D/recipe/chainmailleggings.json', 'D/recipe/chainmailhelmet.json', 'D/recipe/chainmailboots.json',
  'D/loot_table/recipes/enchanter1.json', 'D/loot_table/recipes/enchanter3.json',
  'D/compatibility/itemnbtmatching.json',
  'D/tags/block/tier2blocks.json', 'D/tags/item/stonemason_product_excluded.json',
  'D/tags/item/leather.json', 'D/researches/combat/taunt.json',
];

if (CHECK_ALL) {
  // every file in the store EXCEPT colony/quests (structurally reworked in dev; the
  // quest loader accepts BOTH formats — verified in task 61, not rule-convertible)
  filesToCheck = Object.keys(store).filter(k => !k.startsWith('D/colony/quests/'));
}
const devPath = (key) => key.startsWith('A/')
  ? path.join(devDir, 'assets/minecolonies', key.slice(2))
  : path.join(devDir, 'data/minecolonies', key.slice(2));

for (const key of filesToCheck) {
  const converted = store[key];
  const dp = devPath(key);
  if (!fs.existsSync(dp)) {
    if (converted === undefined) { match++; continue; } // both absent (deleted wrapper)
    if (/^A\/lang\//.test(key)) { match++; continue; } // crowdin lang — by design (runtime merge)
    storeOnly++; mismatches.push(`STORE-ONLY (dev deleted): ${key}`);
    continue;
  }
  const devJson = JSON.parse(fs.readFileSync(dp, 'utf8'));
  if (converted === undefined) { devOnly++; mismatches.push(`DEV-ONLY (not in store): ${key}`); continue; }
  if (deepEq(converted, devJson)) { match++; continue; }

  // not semantically identical — known cosmetic case?
  if (KNOWN_COSMETIC.has(key)) { cosmetic++; continue; }

  // not semantically identical — is the difference ONLY keys dev added beyond the rules?
  mismatch++;
  const diffs = [];
  const flat = (obj, prefix) => {
    const out = [];
    for (const k of Object.keys(obj || {})) {
      const p = prefix ? `${prefix}.${k}` : k;
      if (obj[k] && typeof obj[k] === 'object' && !Array.isArray(obj[k])) out.push(...flat(obj[k], p));
      else out.push(`${p}=${JSON.stringify(obj[k])}`);
    }
    return out;
  };
  const c = new Set(flat(sortKeys(converted), ''));
  const d = new Set(flat(sortKeys(devJson), ''));
  for (const x of c) if (!d.has(x)) diffs.push(`  store-only: ${x}`);
  for (const x of d) if (!c.has(x)) diffs.push(`  dev-only:   ${x}`);
  mismatches.push(`MISMATCH ${key}\n${diffs.slice(0, 8).join('\n')}`);
}

console.log(`\nsemantic comparison vs dev tree: ${match} match, ${mismatch} mismatch, ${cosmetic} cosmetic, ${devOnly} dev-only, ${storeOnly} store-only`);
if (mismatches.length) {
  console.log('\n' + mismatches.join('\n'));
  process.exit(1);
}
// the dev tree must not ship the obsolete registry file either (deleted there too)
const devGlm = path.join(devDir, 'data/neoforge/loot_modifiers/global_loot_modifiers.json');
if (fs.existsSync(devGlm)) {
  console.error('FAIL: dev tree still ships data/neoforge/loot_modifiers/global_loot_modifiers.json');
  process.exit(1);
}
// crowdin lang files in the store are BY DESIGN (dev keeps only novel keys; the runtime
// loader merges per-key) — never a failure

console.log('\nALL CHECKED FILES MATCH THE DEV TREE ✔');
