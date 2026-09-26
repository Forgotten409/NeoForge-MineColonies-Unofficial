/**
 * converter-rules — the 1:1 JS mirror of PortAssetConverter (Java), shared by
 * tools/restore-dev-assets.mjs (offline conversion of the extracted dev tree) and
 * tools/simulate-converter.mjs (verification harness against the dev tree).
 *
 * The rule tables and transform functions below are copied EXACTLY from the Java
 * converter (src/main/java/com/ldtteam/minecolonies/portassets/PortAssetConverter.java,
 * rule set "port26-4") — any change here must be mirrored there and vice versa,
 * and both directions bump AssetProvisioner.MARKER_FORMAT.
 *
 * All transforms are IDEMPOTENT: running them over already-converted content is a
 * no-op (re-runs over an existing store/dev tree are safe).
 */

import fs from 'node:fs';
import path from 'node:path';

// ------------------------------------------------------------------ rule tables (1:1 with Java)

export const TEXTURE_RENAMES = {
  'entity/armorstand/wood': 'entity/armorstand/armorstand',
  'entity/chicken': 'entity/chicken/chicken_temperate',
  'map/map_icons': 'block/oak_planks',
  'models/armor/leather_layer_1': 'minecolonies:block/leather',
  'models/armor/leather_layer_2': 'minecolonies:block/leather',
};
export const OLD_SPAWN_EGG_PARENT = 'item/template_spawn_egg';
export const RACK_WRAPPER_MODELS = [
  'models/block/blockrackemptydouble.json',
  'models/block/blockrackfull.json',
  'models/block/blockrackfull_1.json',
  'models/block/blockrackfull_2.json',
  'models/block/blockrackfulldouble.json',
  'models/block/blockrackfulldouble_1.json',
  'models/block/blockrackfulldouble_2.json',
];
export const DEPRECATED_DISPLAY_KEYS = ['thirdperson', 'firstperson'];
export const DATA_VALUE_RENAMES = {
  'minecraft:fire_resistant': 'minecraft:damage_resistant',
  'minecraft:hide_additional_tooltip': 'minecraft:tooltip_display',
  'minecraft:chain': 'minecraft:iron_chain',
};
export const TAG_ENTRY_REMOVALS = ['#minecraft:trim_templates'];
export const TAG_ENTRY_ADDITIONS = {
  'tags/item/leather.json': ['minecraft:leather_horse_armor', 'minecraft:armadillo_scute'],
};

/** Whole-file port-authored overrides (Java copyOverride targets), relative to assets/minecolonies/. */
export const FILE_OVERRIDES = [
  'blockstates/blockminecoloniesrack.json',
  'models/item/spear.json',
  'models/item/spear_in_hand.json',
  'models/item/spear_throwing.json',
];

/** The obsolete 1.21.1 global-loot-modifier registry file (Java rule: delete). */
export const OBSOLETE_GLM = path.join('data', 'neoforge', 'loot_modifiers', 'global_loot_modifiers.json');

// ------------------------------------------------------------------ java rule ports (asset side)

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

/** In-place model conversion. `rel` is relative to assets/minecolonies/. Returns true when changed. */
export function convertModelFile(root, rel) {
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

// ------------------------------------------------------------------ java rule ports (data side)

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
  return changed;
}

/** In-place data-file conversion. `rel` is relative to data/minecolonies/. Returns true when changed. */
export function convertDataFile(root, rel) {
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

// ------------------------------------------------------------------ store-tree conversion (high level)

const walk = (dir) => {
  const results = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) results.push(...walk(full));
    else results.push(full);
  }
  return results;
};

/**
 * Converts an extracted official-jar store IN PLACE — the exact offline equivalent of
 * the Java converter that runs over <gamedir>/port-assets/ at provisioning time.
 *
 * <ul>
 *   <li>assets/minecolonies/**: model conversions, rack-wrapper deletion, file overrides
 *       from the publish overrides dir, sounds-extra.json merge into sounds.json;</li>
 *   <li>data/minecolonies/**: recipe/loot/tag/value conversions;</li>
 *   <li>data/neoforge/loot_modifiers/global_loot_modifiers.json: deleted (obsolete
 *       1.21.1 registry file — play-test #4's "No key type" error).</li>
 * </ul>
 *
 * Idempotent: re-running over converted content reports 0 conversions.
 *
 * @param {string} storeDir extracted jar root (contains assets/ + data/ + …)
 * @param {string} overridesDir directory with the port-authored overrides
 *   (src/main/resources-publish/portassets/overrides) — may be missing (conversion
 *   still runs, overrides are skipped like in a jar without them)
 * @param {(line: string) => void} [log] progress sink
 * @returns {{modelFiles:number, dataFiles:number, deletedWrappers:number,
 *            deletedObsolete:number, overrides:number, soundEntries:number,
 *            written:number}} conversion counters
 */
export function convertStoreDir(storeDir, overridesDir, log = () => {}) {
  const assetsDir = path.join(storeDir, 'assets', 'minecolonies');
  const dataDir = path.join(storeDir, 'data', 'minecolonies');
  let modelFiles = 0, dataFiles = 0, deletedWrappers = 0, deletedObsolete = 0;
  let overrides = 0, soundEntries = 0, written = 0;

  const writeBack = (file, root) => {
    const json = JSON.stringify(root, null, 2) + '\n';
    fs.writeFileSync(file, json);
    written++;
  };

  if (fs.existsSync(assetsDir)) {
    for (const file of walk(assetsDir)) {
      if (!file.endsWith('.json') && !file.endsWith('.mcmeta')) continue;
      const rel = path.relative(assetsDir, file).split(path.sep).join('/');
      let root;
      try { root = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { continue; }
      if (typeof root !== 'object' || root === null || Array.isArray(root)) continue;
      if (convertModelFile(root, rel)) { writeBack(file, root); modelFiles++; }
    }
    for (const wrapper of RACK_WRAPPER_MODELS) {
      const p = path.join(assetsDir, wrapper);
      if (fs.existsSync(p)) { fs.rmSync(p); deletedWrappers++; }
    }
  }

  if (fs.existsSync(dataDir)) {
    for (const file of walk(dataDir)) {
      if (!file.endsWith('.json') && !file.endsWith('.mcmeta')) continue;
      const rel = path.relative(dataDir, file).split(path.sep).join('/');
      let root;
      try { root = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { continue; }
      if (root === null || root === undefined) continue;
      if (convertDataFile(root, rel)) { writeBack(file, root); dataFiles++; }
    }
  }

  // obsolete global loot-modifier REGISTRY file (play-test #4): 26.1.2's
  // LootModifierManager scans data/<ns>/loot_modifiers/ as a registry folder; the
  // 1.21.1 entries/replace list file has no reader — delete it
  const glm = path.join(storeDir, OBSOLETE_GLM);
  if (fs.existsSync(glm)) { fs.rmSync(glm); deletedObsolete++; }
  else if (fs.existsSync(path.dirname(glm))) {
    log('note: global_loot_modifiers.json already absent (re-run over converted store)');
  }

  // sounds.json merge (mirror of the Java mergeSoundsExtra)
  const soundsPath = path.join(assetsDir, 'sounds.json');
  const soundsExtraPath = overridesDir ? path.join(overridesDir, 'sounds-extra.json') : null;
  if (fs.existsSync(soundsPath) && soundsExtraPath && fs.existsSync(soundsExtraPath)) {
    const sounds = JSON.parse(fs.readFileSync(soundsPath, 'utf8'));
    const extra = JSON.parse(fs.readFileSync(soundsExtraPath, 'utf8'));
    for (const [k, v] of Object.entries(extra)) {
      if (!(k in sounds)) { sounds[k] = v; soundEntries++; }
    }
    if (soundEntries > 0) writeBack(soundsPath, sounds);
    log(`sounds.json merged: ${soundEntries} entr(y|ies)`);
  }

  // whole-file port-authored overrides (blockstate + spear models)
  if (overridesDir && fs.existsSync(overridesDir)) {
    for (const rel of FILE_OVERRIDES) {
      const src = path.join(overridesDir, rel);
      if (fs.existsSync(src)) {
        const dst = path.join(assetsDir, rel);
        fs.mkdirSync(path.dirname(dst), { recursive: true });
        fs.copyFileSync(src, dst);
        overrides++;
      }
    }
  }

  log(`converted: ${modelFiles} model file(s), ${dataFiles} data file(s), `
      + `${deletedWrappers} wrapper(s) deleted, ${deletedObsolete} obsolete data file(s) `
      + `deleted, ${overrides} override(s), ${soundEntries} sound entrie(s)`);

  return { modelFiles, dataFiles, deletedWrappers, deletedObsolete, overrides, soundEntries, written };
}
