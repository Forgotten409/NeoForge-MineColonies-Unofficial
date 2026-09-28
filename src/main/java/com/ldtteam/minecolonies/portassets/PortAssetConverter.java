package com.ldtteam.minecolonies.portassets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.ldtteam.minecolonies.MineColonies;

/**
 * Runtime conversion layer applied to the extracted external assets: 1.21.1 jar content →
 * 26.1.2 formats — PORT26 (publishing support).
 *
 * <p><b>Why this exists:</b> the dev tree of this port ({@code src/main/resources}) was
 * hand-converted to the 26.1.2 formats during the port; the official 1.21.1 jars that the
 * user downloads still carry the OLD formats. Without conversion the injected pack breaks
 * exactly the way reported on the second play-test (recipe ingredients, loot-table
 * enchantments, tag entries, composite-model textures, removed model loaders, …).</p>
 *
 * <h2>v2 — play-test #3 rule set</h2>
 * <p>v1 only walked {@code assets/minecolonies/models} + {@code blockstates} and had three
 * real bugs (fixed here, each verified against a full recursive diff of the dev tree vs
 * the official 1.1.1399 jar):</p>
 * <ul>
 *   <li><b>overrides landed outside the pack</b> — {@code copyOverride} resolved
 *       {@code models/item/spear.json} against the STORE ROOT instead of
 *       {@code assets/minecolonies/models/item/spear.json}, so the spear/rack override
 *       files never took effect (log: {@code Unknown loader: neoforge:separate_transforms}
 *       + {@code domum_ornamentum:materially_textured});</li>
 *   <li><b>composite {@code children} is an OBJECT</b>, not an array — v1's texture renames
 *       never descended into {@code neoforge:composite} children, leaving
 *       {@code entity/armorstand/wood}, {@code entity/chicken}, {@code map/map_icons} and
 *       {@code models/armor/leather_layer_1} unreplaced (log: missing-texture spam for the
 *       archery/fletcher/university/combatacademy huts);</li>
 *   <li><b>no DATA-side rules at all</b> — the publish jar ships none of
 *       {@code data/minecolonies} (ARR), so recipes ({@code {"item":…}} ingredient
 *       objects), loot tables ({@code "levels"} wrapper), tags and the compatibility table
 *       arrived in 1.21.1 format (log: {@code Couldn't parse data file} spam).</li>
 * </ul>
 *
 * <p>Asset-side rules (verified dev-vs-jar diff, 64 files):</p>
 * <ul>
 *   <li><b>vanilla texture renames</b> 1.21.1 → 26.1.2 (namespace prefixes are stripped
 *       before lookup and preserved on write-back), including
 *       {@code models/armor/leather_layer_1 → minecolonies:block/leather} (port-authored
 *       texture shipped in the publish jar) — applied to every {@code textures} block,
 *       including the nested {@code children} of composite models (object AND array
 *       forms);</li>
 *   <li><b>{@code block/cube_all} models</b> missing the {@code "all"} texture get it
 *       (alias of {@code #particle} when present, otherwise {@code block/oak_planks}) —
 *       26.1.2 resolves {@code #all} references strictly;</li>
 *   <li><b>deprecated display contexts</b> ({@code thirdperson}, {@code firstperson} —
 *       removed in the 1.21.4 line) are stripped; an emptied {@code display} block is
 *       removed wholesale;</li>
 *   <li><b>spawn-egg models</b> reference the removed vanilla parent
 *       {@code item/template_spawn_egg} — rewritten to the generated parent + the
 *       port-authored egg textures (shipped in the publish jar);</li>
 *   <li><b>rack models</b> — parents {@code minecolonies:block/blockrack*} rewritten to
 *       {@code block/rack/blockrack*}; the seven old <b>wrapper</b> models (whose
 *       {@code domum_ornamentum:materially_textured} model loader does not exist on
 *       26.1.2 — the port registers that name as a BLOCKSTATE variant type instead) are
 *       DELETED, and the blockstate is replaced wholesale by the port-converted copy;</li>
 *   <li><b>spear models</b> used the removed {@code neoforge:separate_transforms} loader —
 *       replaced by the port-converted plain models (the new-format item definition with
 *       display-context {@code select} lives in the publish jar's {@code items/spear.json}
 *       — also fixes the "spear held backwards" report: the store's old model is exactly
 *       what dev replaced).</li>
 * </ul>
 *
 * <p>Data-side rules (verified dev-vs-jar diff, 216 files):</p>
 * <ul>
 *   <li><b>recipes</b> — 1.21.1 ingredient objects become 26.1 strings:
 *       {@code {"item":"X"}} → {@code "X"}, {@code {"tag":"X"}} → {@code "#X"} (applied to
 *       shaped {@code key} values, shapeless {@code ingredients} arrays and cooking
 *       {@code ingredient} fields alike);</li>
 *   <li><b>loot tables</b> — the {@code minecraft:enchantments}/{@code :stored_enchantments}
 *       components drop the {@code "levels"} wrapper (and the removed
 *       {@code show_in_tooltip} flag goes with it);</li>
 *   <li><b>exact value renames</b> in ALL data JSONs (keys AND string values are safe here
 *       because the match is exact):
 *       {@code minecraft:fire_resistant → minecraft:damage_resistant},
 *       {@code minecraft:hide_additional_tooltip → minecraft:tooltip_display},
 *       {@code minecraft:chain → minecraft:iron_chain} (block AND item renamed in vanilla
 *       26.1.2 — covers chainmail recipes, the tier2blocks tag, research icons and the
 *       compatibility table in one rule);</li>
 *   <li><b>tags</b> — entries referencing registry tags that no longer exist are REMOVED
 *       ({@code #minecraft:trim_templates}); the port-authored additions to
 *       {@code tags/item/leather.json} (leather_horse_armor, armadillo_scute) are appended
 *       when missing;</li>
 *   <li><b>sounds.json</b> — the official 1387 snapshot jar predates the marksman voice
 *       entries the port's Java registers; the missing entries (port-maintained table,
 *       {@code overrides/sounds-extra.json}) are merged in.</li>
 * </ul>
 *
 * <p><b>Legal framing</b> (docs/PUBLISHING.md §4): the conversion happens on the USER'S OWN
 * downloaded copy, on their machine, at extraction time — the published jar carries only
 * conversion RULES plus a handful of port-authored override files, never the upstream ARR
 * content itself.</p>
 *
 * <p>All conversions are best-effort and individually guarded: one bad file never aborts
 * the provisioning run — the failure is logged and the original file stays in place.</p>
 */
final class PortAssetConverter
{
    /** Shared Gson — lenient parsing, pretty output (readability of the extracted store). */
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    /** Progress report interval while walking/converting the store (files between reports). */
    private static final int PROGRESS_REPORT_INTERVAL = 100;

    /**
     * Vanilla texture renames 1.21.1 → 26.1.2 (values inside {@code textures} blocks).
     * Key = old path (namespace stripped before lookup), value = new path (a
     * namespace-qualified value is used verbatim, a bare one keeps the original prefix).
     */
    private static final Map<String, String> TEXTURE_RENAMES = Map.of(
        "entity/armorstand/wood", "entity/armorstand/armorstand",
        "entity/chicken", "entity/chicken/chicken_temperate",
        "map/map_icons", "block/oak_planks",
        "models/armor/leather_layer_1", "minecolonies:block/leather",
        "models/armor/leather_layer_2", "minecolonies:block/leather");

    /**
     * Equipment (armor-layer) textures 1.21.1 → 26.1.2 path renames, applied to the FILES
     * themselves (not model references): the 1.21.1 layout keeps every wearable layer as
     * {@code textures/models/armor/<name>_layer_1/_2.png} (rendered by the old humanoid
     * armor model system), while 26.1.2 resolves equipment assets
     * ({@code assets/minecolonies/equipment/<name>.json}, port-authored and shipped in the
     * publish jar) against {@code textures/entity/equipment/humanoid/<name>.png} and
     * {@code …/humanoid_leggings/<name>.png}. The bytes are identical — only the path
     * changes — and the SOURCE is the user's own downloaded official jar, so the publish
     * jar no longer needs to bundle these ARR textures at all (port26-6: the eight PNGs
     * previously shipped in {@code resources-publish} were byte-identical ARR copies and
     * got the 0.5.2 CurseForge submission rejected as "derivative work").
     *
     * <p>Key = old store path (relative to {@code assets/minecolonies/}), value = new store
     * path. Copy is idempotent ({@code REPLACE_EXISTING}, same bytes on re-run) and the old
     * file is left in place — nothing references it after conversion, and textures never
     * parse, so it cannot produce load errors.</p>
     */
    private static final Map<String, String> EQUIPMENT_TEXTURES = Map.of(
        "textures/models/armor/pirate_layer_1.png", "textures/entity/equipment/humanoid/pirate.png",
        "textures/models/armor/pirate_layer_2.png", "textures/entity/equipment/humanoid_leggings/pirate.png",
        "textures/models/armor/pirate2_layer_1.png", "textures/entity/equipment/humanoid/pirate2.png",
        "textures/models/armor/pirate2_layer_2.png", "textures/entity/equipment/humanoid_leggings/pirate2.png",
        "textures/models/armor/plate_armor_layer_1.png", "textures/entity/equipment/humanoid/plate_armor.png",
        "textures/models/armor/plate_armor_layer_2.png", "textures/entity/equipment/humanoid_leggings/plate_armor.png",
        "textures/models/armor/build_goggles_layer_1.png", "textures/entity/equipment/humanoid/build_goggles.png",
        "textures/models/armor/santa_hat_layer_1.png", "textures/entity/equipment/humanoid/santa_hat.png");

    /**
     * Old spawn-egg model parent (removed in the 1.21.2+ line) — replaced by the
     * generated parent + the port-authored egg texture (shipped in the publish jar).
     */
    private static final String OLD_SPAWN_EGG_PARENT = "item/template_spawn_egg";

    /**
     * The seven rack WRAPPER models ({@code loader: domum_ornamentum:materially_textured})
     * — deleted because 26.1.2 has no such MODEL loader (the port registers that name as a
     * blockstate variant type instead) and the converted blockstate references the
     * {@code rack/} geometry models (and {@code blockrackempty}) directly. ModelManager
     * parses EVERY file under {@code models/}, so leaving them would keep the loader error
     * spam alive even though nothing references them anymore.
     */
    private static final List<String> RACK_WRAPPER_MODELS = List.of(
        "models/block/blockrackemptydouble.json",
        "models/block/blockrackfull.json",
        "models/block/blockrackfull_1.json",
        "models/block/blockrackfull_2.json",
        "models/block/blockrackfulldouble.json",
        "models/block/blockrackfulldouble_1.json",
        "models/block/blockrackfulldouble_2.json");

    /** Display context keys removed in the 1.21.4 line (renamed to the *_hand forms). */
    private static final List<String> DEPRECATED_DISPLAY_KEYS = List.of("thirdperson", "firstperson");

    /**
     * Exact-string value renames in data JSONs (components, item ids, icons, tag entries).
     * Exact match only — {@code minecraft:chain_command_block} can never match.
     */
    private static final Map<String, String> DATA_VALUE_RENAMES = Map.of(
        "minecraft:fire_resistant", "minecraft:damage_resistant",
        "minecraft:hide_additional_tooltip", "minecraft:tooltip_display",
        "minecraft:chain", "minecraft:iron_chain");

    /**
     * Tag entries to REMOVE: the referenced vanilla registry tag does not exist on 26.1.2
     * and an unresolvable entry makes the whole tag fail to load.
     */
    private static final List<String> TAG_ENTRY_REMOVALS = List.of("#minecraft:trim_templates");

    /**
     * Port-authored tag ADDITIONS (path relative to {@code data/minecolonies/} → entries
     * appended when missing — mirrors the dev tree, additive like vanilla tag merging).
     */
    private static final Map<String, List<String>> TAG_ENTRY_ADDITIONS = Map.of(
        "tags/item/leather.json", List.of("minecraft:leather_horse_armor", "minecraft:armadillo_scute"));

    /** Classpath root of the port-authored override files shipped in the publish jar. */
    private static final String OVERRIDES_ROOT = "/portassets/overrides/";

    /**
     * Runs every conversion rule over one namespace store.
     *
     * @param storeDir the extracted store directory (temp dir during provisioning).
     * @param namespace the namespace the store belongs to (rules are minecolonies-only).
     * @param convertedFiles mutable counter — incremented for each rewritten file.
     * @param listener optional progress sink (worker thread) — "Converting… N file(s)".
     */
    static void convert(final Path storeDir, final AssetNamespace namespace, final int[] convertedFiles,
        @Nullable final AssetProvisioner.ProgressListener listener)
    {
        if (!"minecolonies".equals(namespace.modId()))
        {
            return; // TownTalk's store is plain sound content — nothing to convert.
        }

        final Path assetsDir = storeDir.resolve("assets/minecolonies");
        final Path dataDir = storeDir.resolve("data/minecolonies");

        int modelFiles = 0;
        int dataFiles = 0;
        int deletedWrappers = 0;
        int reported = 0;

        // ---------------------------------------------------------------- assets
        if (Files.isDirectory(assetsDir))
        {
            try (final var stream = Files.walk(assetsDir))
            {
                for (final Path file : stream.filter(Files::isRegularFile).filter(PortAssetConverter::isJsonFile).toList())
                {
                    final String rel = assetsDir.relativize(file).toString().replace('\\', '/');
                    try
                    {
                        if (convertModelFile(file, rel))
                        {
                            modelFiles++;
                            if (listener != null && (modelFiles + dataFiles) / PROGRESS_REPORT_INTERVAL > reported)
                            {
                                reported = (modelFiles + dataFiles) / PROGRESS_REPORT_INTERVAL;
                                listener.accept(PortAssetText.format("portassets.status.converting", modelFiles + dataFiles));
                            }
                        }
                    }
                    catch (final Exception e)
                    {
                        MineColonies.LOGGER.warn("port-assets: conversion of {} failed (left as-is): {}", rel, e.toString());
                    }
                }
            }
            catch (final IOException e)
            {
                MineColonies.LOGGER.warn("port-assets: converter could not walk {}: {}", assetsDir, e.toString());
            }
        }

        // old rack wrapper models: unreferenced after the blockstate override, and their
        // model loader does not exist on 26.1.2 — delete instead of leaving load errors
        for (final String wrapper : RACK_WRAPPER_MODELS)
        {
            try
            {
                if (Files.deleteIfExists(assetsDir.resolve(wrapper)))
                {
                    deletedWrappers++;
                }
            }
            catch (final IOException e)
            {
                MineColonies.LOGGER.warn("port-assets: could not delete rack wrapper {}: {}", wrapper, e.toString());
            }
        }

        // equipment (armor-layer) textures: 1.21.1 file layout → 26.1.2 equipment asset
        // layout (byte-identical copy from the user's own jar — port26-6 ARR externalization;
        // the publish jar's equipment/*.json reference the NEW paths)
        int equipmentTextures = 0;
        for (final var entry : EQUIPMENT_TEXTURES.entrySet())
        {
            final Path old = assetsDir.resolve(entry.getKey().replace('/', storeDir.getFileSystem().getSeparator().charAt(0)));
            final Path target = assetsDir.resolve(entry.getValue().replace('/', storeDir.getFileSystem().getSeparator().charAt(0)));
            if (!Files.isRegularFile(old))
            {
                MineColonies.LOGGER.warn("port-assets: equipment texture {} missing from the store — equipment asset renders without it", entry.getKey());
                continue;
            }
            try
            {
                Files.createDirectories(target.getParent());
                Files.copy(old, target, StandardCopyOption.REPLACE_EXISTING);
                equipmentTextures++;
            }
            catch (final IOException e)
            {
                MineColonies.LOGGER.warn("port-assets: could not copy equipment texture {}: {}", entry.getKey(), e.toString());
            }
        }

        // obsolete global loot-modifier REGISTRY file: 26.1.2's LootModifierManager scans
        // data/<ns>/loot_modifiers/ as a registry folder (every file = one modifier
        // instance, parsed by the DIRECT_CODEC with its "type" dispatch key) — the 1.21.1
        // entries/replace list file has no reader anymore and parses as a modifier without
        // a "type" ("No key type in MapLike[…]" — play-test #4). The per-modifier JSONs
        // (supplycamp_loot, crops/**) already carry "type" + "conditions" and load as-is.
        int deletedObsoleteData = 0;
        final Path obsoleteGlm = storeDir.resolve("data/neoforge/loot_modifiers/global_loot_modifiers.json");
        try
        {
            if (Files.deleteIfExists(obsoleteGlm))
            {
                deletedObsoleteData++;
                MineColonies.LOGGER.info("port-assets: deleted obsolete loot-modifier registry file {} "
                    + "(26.1.2 loads each loot_modifiers/ file directly)", obsoleteGlm.getFileName());
            }
        }
        catch (final IOException e)
        {
            MineColonies.LOGGER.warn("port-assets: could not delete {}: {}", obsoleteGlm, e.toString());
        }

        // ---------------------------------------------------------------- data
        if (Files.isDirectory(dataDir))
        {
            try (final var stream = Files.walk(dataDir))
            {
                for (final Path file : stream.filter(Files::isRegularFile).filter(PortAssetConverter::isJsonFile).toList())
                {
                    final String rel = dataDir.relativize(file).toString().replace('\\', '/');
                    try
                    {
                        if (convertDataFile(file, rel))
                        {
                            dataFiles++;
                        }
                    }
                    catch (final Exception e)
                    {
                        MineColonies.LOGGER.warn("port-assets: data conversion of {} failed (left as-is): {}", rel, e.toString());
                    }
                }
            }
            catch (final IOException e)
            {
                MineColonies.LOGGER.warn("port-assets: converter could not walk {}: {}", dataDir, e.toString());
            }
        }

        // whole-file port-authored overrides (blockstate + spear models) — target paths
        // live INSIDE the pack: assets/minecolonies/<…> (v1 wrote them to the store root
        // where no pack scanner ever saw them — the play-test #3 "unknown loader" errors)
        int overrides = 0;
        overrides += copyOverride(storeDir, "blockstates/blockminecoloniesrack.json");
        overrides += copyOverride(storeDir, "models/item/spear.json");
        overrides += copyOverride(storeDir, "models/item/spear_in_hand.json");
        overrides += copyOverride(storeDir, "models/item/spear_throwing.json");

        // sounds.json merge (missing voice entries)
        final int soundEntries = mergeSoundsExtra(storeDir);

        if (convertedFiles.length > 0)
        {
            convertedFiles[0] += modelFiles + dataFiles + deletedWrappers + deletedObsoleteData + overrides
                + equipmentTextures + (soundEntries > 0 ? 1 : 0);
        }
        MineColonies.LOGGER.info("port-assets: conversion applied — {} model file(s), {} data file(s), {} rack wrapper(s) deleted, "
            + "{} obsolete data file(s) deleted, {} override file(s), {} equipment texture(s) renamed, {} sounds.json entr(y|ies) merged",
            modelFiles, dataFiles, deletedWrappers, deletedObsoleteData, overrides, equipmentTextures, soundEntries);
    }

    /**
     * @param file any file.
     * @return true for {@code .json} / {@code .mcmeta} files.
     */
    private static boolean isJsonFile(final Path file)
    {
        final String name = file.getFileName().toString();
        return name.endsWith(".json") || name.endsWith(".mcmeta");
    }

    // ------------------------------------------------------------------ assets

    /**
     * One asset JSON (model/blockstate) through the rule set. Writes back only when
     * something changed.
     *
     * @param file the model JSON.
     * @param rel  path relative to {@code assets/<ns>} (forward slashes).
     * @return true when the file was rewritten.
     * @throws IOException on read/write failure (caller logs and keeps going).
     */
    private static boolean convertModelFile(final Path file, final String rel) throws IOException
    {
        if (!rel.startsWith("models/"))
        {
            return false; // blockstates are replaced wholesale by the override copy
        }
        final String raw = Files.readString(file, StandardCharsets.UTF_8);
        final JsonElement parsed;
        try
        {
            parsed = JsonParser.parseString(raw);
        }
        catch (final com.google.gson.JsonParseException e)
        {
            return false; // not valid JSON — leave untouched (a pack reload will report it)
        }
        if (!parsed.isJsonObject())
        {
            return false;
        }
        final JsonObject root = parsed.getAsJsonObject();
        boolean changed = false;

        // 1) texture renames + cube_all "all" alias (recurses into composite children)
        changed |= convertTextures(root);

        // 2) deprecated display contexts (thirdperson / firstperson)
        changed |= stripDeprecatedDisplay(root);

        // 3) rack wrapper parents: minecolonies:block/blockrack* -> block/rack/blockrack*
        if (rel.startsWith("models/block/rack/"))
        {
            final JsonElement parentEl = root.get("parent");
            if (parentEl != null && parentEl.isJsonPrimitive())
            {
                final String value = parentEl.getAsString();
                if (value.startsWith("minecolonies:block/blockrack"))
                {
                    root.addProperty("parent", "minecolonies:block/rack/" + value.substring("minecolonies:block/".length()));
                    changed = true;
                }
            }
        }

        // 4) spawn-egg models: removed vanilla parent -> generated + port egg texture
        final JsonElement parentEl = root.get("parent");
        if (parentEl != null && parentEl.isJsonPrimitive() && OLD_SPAWN_EGG_PARENT.equals(parentEl.getAsString()))
        {
            final int lastSlash = Math.max(0, rel.lastIndexOf('/'));
            final String eggName = rel.substring(lastSlash == 0 ? 0 : lastSlash + 1, rel.length() - ".json".length());
            root.remove("loader");
            root.addProperty("parent", "minecraft:item/generated");
            final JsonObject textures = new JsonObject();
            textures.addProperty("layer0", "minecolonies:item/" + eggName);
            root.add("textures", textures);
            changed = true;
        }

        if (changed)
        {
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        }
        return changed;
    }

    /**
     * Texture-block conversion for one model object — and its nested {@code children},
     * which {@code neoforge:composite} writes as an OBJECT (child name → model) while
     * other loaders use arrays. v1 only handled the array form, which is why the
     * hut-model children kept their old texture paths.
     *
     * @param model the model JSON object.
     * @return true when the object (or a nested child) was modified.
     */
    private static boolean convertTextures(final JsonObject model)
    {
        boolean changed = false;

        final JsonElement texturesEl = model.get("textures");
        if (texturesEl != null && texturesEl.isJsonObject())
        {
            final JsonObject textures = texturesEl.getAsJsonObject();
            // snapshot — safe to mutate `textures` while iterating it
            for (final var entry : toMap(textures).entrySet())
            {
                final String mapped = mappedTexture(entry.getValue());
                if (mapped != null)
                {
                    textures.addProperty(entry.getKey(), mapped);
                    changed = true;
                }
            }
            // cube_all models need "all" in 26.1.2 (its elements reference #all):
            // alias #particle when present, otherwise the safe oak-planks default
            final JsonElement parentEl = model.get("parent");
            final String parent = parentEl != null && parentEl.isJsonPrimitive() ? parentEl.getAsString() : "";
            if ((parent.equals("block/cube_all") || parent.equals("minecraft:block/cube_all")) && !textures.has("all"))
            {
                if (textures.has("particle"))
                {
                    textures.addProperty("all", "#particle");
                }
                else
                {
                    textures.addProperty("all", "block/oak_planks");
                }
                changed = true;
            }
        }

        final JsonElement childrenEl = model.get("children");
        if (childrenEl != null)
        {
            if (childrenEl.isJsonArray())
            {
                for (final JsonElement child : childrenEl.getAsJsonArray())
                {
                    if (child.isJsonObject())
                    {
                        changed |= convertTextures(child.getAsJsonObject());
                    }
                }
            }
            else if (childrenEl.isJsonObject())
            {
                for (final var childEntry : childrenEl.getAsJsonObject().entrySet())
                {
                    if (childEntry.getValue().isJsonObject())
                    {
                        changed |= convertTextures(childEntry.getValue().getAsJsonObject());
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Looks one texture reference up in {@link #TEXTURE_RENAMES}.
     *
     * @param value the raw texture reference (bare path or {@code namespace:path}).
     * @return the rewritten reference, or null when no rule matches.
     */
    @Nullable
    private static String mappedTexture(final String value)
    {
        final int colon = value.indexOf(':');
        final String naked = colon >= 0 ? value.substring(colon + 1) : value;
        final String mapped = TEXTURE_RENAMES.get(naked);
        if (mapped == null)
        {
            return null;
        }
        if (mapped.indexOf(':') >= 0)
        {
            return mapped; // fully-qualified target (e.g. minecolonies:block/leather)
        }
        return colon >= 0 ? value.substring(0, colon + 1) + mapped : mapped;
    }

    /**
     * Strips the deprecated {@code thirdperson}/{@code firstperson} display contexts and
     * removes the {@code display} block entirely when nothing remains (mirrors the dev
     * tree, which dropped these legacy blocks during the hand-conversion). A display
     * block whose every remaining entry is the identity default (rotation 0/0/0,
     * translation 0/0/0, scale 1/1/1 — absent fields are defaults too) renders exactly
     * like no display block at all, so it is dropped as well.
     *
     * @param model the model JSON object.
     * @return true when the model was modified.
     */
    private static boolean stripDeprecatedDisplay(final JsonObject model)
    {
        final JsonElement displayEl = model.get("display");
        if (displayEl == null || !displayEl.isJsonObject())
        {
            return false;
        }
        final JsonObject display = displayEl.getAsJsonObject();
        boolean changed = false;
        for (final String key : DEPRECATED_DISPLAY_KEYS)
        {
            if (display.remove(key) != null)
            {
                changed = true;
            }
        }
        if (display.entrySet().isEmpty())
        {
            if (changed)
            {
                model.remove("display");
            }
            return changed;
        }
        if (isIdentityDisplay(display))
        {
            model.remove("display");
            return true;
        }
        return changed;
    }

    /**
     * @param display a display block.
     * @return true when every context in the block is the identity transform.
     */
    private static boolean isIdentityDisplay(final JsonObject display)
    {
        for (final var entry : display.entrySet())
        {
            if (!entry.getValue().isJsonObject() || !isIdentityTransform(entry.getValue().getAsJsonObject()))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @param transform one display-context transform.
     * @return true when rotation/translation/scale are all at their defaults.
     */
    private static boolean isIdentityTransform(final JsonObject transform)
    {
        return isDefaultVec(transform, "rotation", 0.0)
                 && isDefaultVec(transform, "translation", 0.0)
                 && isDefaultVec(transform, "scale", 1.0);
    }

    /**
     * @param transform     one display-context transform.
     * @param key           the vector field ("rotation" / "translation" / "scale").
     * @param defaultValue  the default for every component of that vector.
     * @return true when the field is absent (default) or every component equals the
     *         default.
     */
    private static boolean isDefaultVec(final JsonObject transform, final String key, final double defaultValue)
    {
        final JsonElement vecEl = transform.get(key);
        if (vecEl == null)
        {
            return true;
        }
        if (!vecEl.isJsonArray())
        {
            return false;
        }
        for (final JsonElement component : vecEl.getAsJsonArray())
        {
            if (!component.isJsonPrimitive() || Math.abs(component.getAsDouble() - defaultValue) > 1e-9)
            {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ data

    /**
     * One data JSON through the rule set (recipes / loot tables / tags / everything else
     * gets the exact-value renames). Root-level ARRAYS are supported too —
     * {@code compatibility/itemnbtmatching.json} (the renamed-component warning source
     * of play-test #3) is a top-level array, and v2's object-only guard silently skipped
     * it, leaving {@code fire_resistant}/{@code hide_additional_tooltip} unreplaced.
     *
     * @param file the data JSON.
     * @param rel  path relative to {@code data/minecolonies} (forward slashes).
     * @return true when the file was rewritten.
     * @throws IOException on read/write failure (caller logs and keeps going).
     */
    private static boolean convertDataFile(final Path file, final String rel) throws IOException
    {
        final String raw = Files.readString(file, StandardCharsets.UTF_8);
        final JsonElement parsed;
        try
        {
            parsed = JsonParser.parseString(raw);
        }
        catch (final com.google.gson.JsonParseException e)
        {
            return false;
        }
        boolean changed = false;

        if (parsed.isJsonObject())
        {
            final JsonObject root = parsed.getAsJsonObject();
            if (rel.startsWith("recipe/"))
            {
                // 1.21.1 ingredient objects -> 26.1 strings ("X" / "#X")
                changed |= convertRecipeIngredients(root);
            }
            else if (rel.startsWith("loot_table/"))
            {
                // enchantment components drop the "levels" wrapper
                changed |= unwrapEnchantmentLevels(root);
            }
            else if (rel.startsWith("tags/"))
            {
                // remove entries whose registry tag no longer exists; rename renamed ids
                changed |= convertTagEntries(root);
                final List<String> additions = TAG_ENTRY_ADDITIONS.get(rel);
                if (additions != null)
                {
                    changed |= appendTagEntries(root, additions);
                }
            }
        }

        // exact-value renames everywhere — object OR array root (itemnbtmatching!):
        // fire_resistant → damage_resistant, hide_additional_tooltip → tooltip_display,
        // chain → iron_chain
        final boolean[] renamed = {false};
        final JsonElement result = renamedElement(parsed, renamed);
        changed |= renamed[0];

        if (changed)
        {
            Files.writeString(file, GSON.toJson(result), StandardCharsets.UTF_8);
        }
        return changed;
    }

    /**
     * Recipe ingredient conversion: shaped {@code key} values, shapeless
     * {@code ingredients} arrays, cooking {@code ingredient} fields, and the custom
     * compostable-recipe {@code input} field (whose 1.21.1 array-of-typed-objects form
     * becomes a {@code neoforge:compound} ingredient on 26.1.2 — see
     * {@link #convertCompoundIngredient}).
     *
     * @param recipe the recipe JSON object.
     * @return true when the recipe was modified.
     */
    private static boolean convertRecipeIngredients(final JsonObject recipe)
    {
        boolean changed = false;

        final JsonElement keyEl = recipe.get("key");
        if (keyEl != null && keyEl.isJsonObject())
        {
            for (final var entry : List.copyOf(keyEl.getAsJsonObject().entrySet()))
            {
                final JsonElement converted = convertIngredient(entry.getValue());
                if (converted != entry.getValue())
                {
                    keyEl.getAsJsonObject().add(entry.getKey(), converted);
                    changed = true;
                }
            }
        }

        final JsonElement ingredientsEl = recipe.get("ingredients");
        if (ingredientsEl != null && ingredientsEl.isJsonArray())
        {
            final JsonArray convertedArray = new JsonArray();
            boolean arrayChanged = false;
            for (final JsonElement element : ingredientsEl.getAsJsonArray())
            {
                final JsonElement converted = convertIngredient(element);
                arrayChanged |= converted != element;
                convertedArray.add(converted);
            }
            if (arrayChanged)
            {
                recipe.add("ingredients", convertedArray);
                changed = true;
            }
        }

        final JsonElement ingredientEl = recipe.get("ingredient");
        if (ingredientEl != null)
        {
            final JsonElement converted = convertIngredient(ingredientEl);
            if (converted != ingredientEl)
            {
                recipe.add("ingredient", converted);
                changed = true;
            }
        }

        // custom compostable recipes: "input" was an ARRAY of typed/tagged ingredients
        // in 1.21.1; 26.1.2 wants one neoforge:compound ingredient object
        final JsonElement inputEl = recipe.get("input");
        if (inputEl != null && inputEl.isJsonArray())
        {
            final JsonElement converted = convertCompoundIngredient(inputEl.getAsJsonArray());
            if (converted != inputEl)
            {
                recipe.add("input", converted);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Converts the old {@code "input": [ … ]} array of the minecolonies compostable
     * recipes into the 26.1.2 compound ingredient: custom typed objects rename their
     * {@code "type"} key to {@code "neoforge:ingredient_type"}, and plain
     * {@code {"tag"/"item"}} objects become strings — verified against the dev tree's
     * hand-converted {@code compostables*.json}.
     *
     * @param array the old ingredient array.
     * @return the compound ingredient object, or the original array when it carries no
     *         typed/tagged elements (nothing this rule understands).
     */
    private static JsonElement convertCompoundIngredient(final JsonArray array)
    {
        boolean anyCustom = false;
        for (final JsonElement element : array)
        {
            if (element.isJsonObject() && (element.getAsJsonObject().has("type")
                || element.getAsJsonObject().has("tag") || element.getAsJsonObject().has("item")))
            {
                anyCustom = true;
                break;
            }
        }
        if (!anyCustom)
        {
            return array;
        }

        final JsonObject compound = new JsonObject();
        compound.addProperty("neoforge:ingredient_type", "neoforge:compound");
        final JsonArray children = new JsonArray();
        for (final JsonElement element : array)
        {
            if (element.isJsonObject() && element.getAsJsonObject().has("type"))
            {
                // custom minecolonies ingredient type (food/plant/…): the type key is
                // neoforge:ingredient_type in 26.1.2; all other properties carry over
                final JsonObject typed = new JsonObject();
                for (final var entry : element.getAsJsonObject().entrySet())
                {
                    typed.add("type".equals(entry.getKey()) ? "neoforge:ingredient_type" : entry.getKey(),
                        entry.getValue());
                }
                children.add(typed);
            }
            else
            {
                children.add(convertIngredient(element));
            }
        }
        compound.add("children", children);
        return compound;
    }

    /**
     * Converts one 1.21.1 ingredient element to the 26.1 string form:
     * {@code {"item":"X"}} → {@code "X"} and {@code {"tag":"X"}} → {@code "#X"} (arrays
     * are mapped element-wise). Strings and typed objects ({@code "type"} key present)
     * are already the new format and pass through unchanged.
     *
     * @param element the ingredient element.
     * @return the converted element (identity when nothing to do).
     */
    private static JsonElement convertIngredient(final JsonElement element)
    {
        if (element.isJsonArray())
        {
            final JsonArray out = new JsonArray();
            for (final JsonElement child : element.getAsJsonArray())
            {
                out.add(convertIngredient(child));
            }
            return out;
        }
        if (element.isJsonObject())
        {
            final JsonObject obj = element.getAsJsonObject();
            if (obj.has("type"))
            {
                return element; // typed (new-format) ingredient
            }
            if (obj.size() == 1 && obj.has("item") && obj.get("item").isJsonPrimitive())
            {
                return new JsonPrimitive(obj.get("item").getAsString());
            }
            if (obj.size() == 1 && obj.has("tag") && obj.get("tag").isJsonPrimitive())
            {
                return new JsonPrimitive("#" + obj.get("tag").getAsString());
            }
            return element; // count-bearing or unknown — the codec will report it
        }
        return element; // plain string — already the new format
    }

    /**
     * Recursively unwraps the {@code "levels"} object of the
     * {@code minecraft:enchantments}/{@code minecraft:stored_enchantments} components
     * (the 26.1 codec reads the level map directly; {@code show_in_tooltip} was removed
     * from the component in the 1.21.5 line and is dropped together with the wrapper).
     *
     * @param element any JSON subtree of a loot table.
     * @return true when the subtree was modified.
     */
    private static boolean unwrapEnchantmentLevels(final JsonElement element)
    {
        boolean changed = false;
        if (element.isJsonArray())
        {
            for (final JsonElement child : element.getAsJsonArray())
            {
                changed |= unwrapEnchantmentLevels(child);
            }
        }
        else if (element.isJsonObject())
        {
            final JsonObject obj = element.getAsJsonObject();
            for (final String key : List.copyOf(obj.keySet()))
            {
                if (("minecraft:enchantments".equals(key) || "minecraft:stored_enchantments".equals(key))
                      && obj.get(key).isJsonObject())
                {
                    final JsonElement levelsEl = obj.getAsJsonObject(key).get("levels");
                    if (levelsEl != null && levelsEl.isJsonObject())
                    {
                        obj.add(key, levelsEl);
                        changed = true;
                        continue;
                    }
                }
                changed |= unwrapEnchantmentLevels(obj.get(key));
            }
        }
        return changed;
    }

    /**
     * Tag conversion: removes entries whose referenced registry tag no longer exists on
     * 26.1.2 (an unresolvable entry makes the ENTIRE tag fail to load) and renames
     * renamed ids.
     *
     * @param tag the tag JSON object.
     * @return true when the tag was modified.
     */
    private static boolean convertTagEntries(final JsonObject tag)
    {
        final JsonElement valuesEl = tag.get("values");
        if (valuesEl == null || !valuesEl.isJsonArray())
        {
            return false;
        }
        final JsonArray values = valuesEl.getAsJsonArray();
        final JsonArray out = new JsonArray();
        boolean changed = false;
        for (final JsonElement value : values)
        {
            if (value.isJsonPrimitive() && TAG_ENTRY_REMOVALS.contains(value.getAsString()))
            {
                changed = true;
                continue;
            }
            if (value.isJsonPrimitive())
            {
                final String mapped = DATA_VALUE_RENAMES.get(value.getAsString());
                if (mapped != null)
                {
                    out.add(new JsonPrimitive(mapped));
                    changed = true;
                    continue;
                }
            }
            out.add(value);
        }
        if (changed)
        {
            tag.add("values", out);
        }
        return changed;
    }

    /**
     * Appends port-authored tag entries when missing (mirrors the dev tree).
     *
     * @param tag the tag JSON object.
     * @param additions the entries to add.
     * @return true when the tag was modified.
     */
    private static boolean appendTagEntries(final JsonObject tag, final List<String> additions)
    {
        final JsonElement valuesEl = tag.get("values");
        if (valuesEl == null || !valuesEl.isJsonArray())
        {
            return false;
        }
        final JsonArray values = valuesEl.getAsJsonArray();
        final List<String> present = new java.util.ArrayList<>();
        for (final JsonElement value : values)
        {
            if (value.isJsonPrimitive())
            {
                present.add(value.getAsString());
            }
        }
        boolean changed = false;
        for (final String addition : additions)
        {
            if (!present.contains(addition))
            {
                values.add(new JsonPrimitive(addition));
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Recursively renames exact string values per {@link #DATA_VALUE_RENAMES} (renamed
     * vanilla data components and the {@code chain → iron_chain} id split) — in object
     * values AND array elements ({@code itemnbtmatching.json}'s {@code checkednbtkeys}
     * arrays carry the component ids as plain array elements). Exact match only, so
     * longer ids such as {@code minecraft:chain_command_block} are safe.
     *
     * <p>Objects are mutated in place; an array that needs an element replaced is
     * REBUILT and returned, and every parent (object property or array slot) replaces
     * the child it holds when the returned element is a different instance. This uses
     * only long-stable Gson APIs (no {@code JsonArray#set} — belt and braces against
     * ancient Gson builds).</p>
     *
     * @param element any JSON subtree.
     * @param changed one-element flag — set to true when any rename happened.
     * @return the (possibly rebuilt) subtree.
     */
    private static JsonElement renamedElement(final JsonElement element, final boolean[] changed)
    {
        if (element.isJsonObject())
        {
            final JsonObject obj = element.getAsJsonObject();
            for (final var entry : List.copyOf(obj.entrySet()))
            {
                final JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
                {
                    final String mapped = DATA_VALUE_RENAMES.get(value.getAsString());
                    if (mapped != null)
                    {
                        obj.addProperty(entry.getKey(), mapped);
                        changed[0] = true;
                    }
                }
                else
                {
                    final JsonElement converted = renamedElement(value, changed);
                    if (converted != value)
                    {
                        obj.add(entry.getKey(), converted);
                    }
                }
            }
            return element;
        }
        if (element.isJsonArray())
        {
            final JsonArray array = element.getAsJsonArray();
            JsonArray rebuilt = null;
            final int size = array.size();
            for (int i = 0; i < size; i++)
            {
                final JsonElement converted = renamedElement(array.get(i), changed);
                if (converted != array.get(i) && rebuilt == null)
                {
                    rebuilt = new JsonArray();
                    for (int j = 0; j < i; j++)
                    {
                        rebuilt.add(array.get(j));
                    }
                }
                if (rebuilt != null)
                {
                    rebuilt.add(converted);
                }
            }
            return rebuilt != null ? rebuilt : element;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
        {
            final String mapped = DATA_VALUE_RENAMES.get(element.getAsString());
            if (mapped != null)
            {
                changed[0] = true;
                return new JsonPrimitive(mapped);
            }
        }
        return element;
    }

    // ------------------------------------------------------------------ shared helpers

    /**
     * Helper: JsonObject entry set as a plain modifiable map.
     */
    private static Map<String, String> toMap(final JsonObject textures)
    {
        final Map<String, String> map = new TreeMap<>();
        for (final var entry : textures.entrySet())
        {
            if (entry.getValue().isJsonPrimitive())
            {
                map.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return map;
    }

    /**
     * Copies one port-authored override file from the jar's classpath
     * ({@code portassets/overrides/<rel>}) into the extracted store at
     * {@code assets/minecolonies/<rel>} — the path INSIDE the pack, where the pack
     * scanners actually read it (v1 resolved the store ROOT, a silent no-op).
     *
     * @param storeDir the store directory.
     * @param rel      path relative to {@code assets/minecolonies} (forward slashes).
     * @return 1 when the override was applied, 0 otherwise.
     */
    private static int copyOverride(final Path storeDir, final String rel)
    {
        final Path target = storeDir.resolve(
            ("assets/minecolonies/" + rel).replace('/', storeDir.getFileSystem().getSeparator().charAt(0)));
        try (final InputStream in = PortAssetConverter.class.getResourceAsStream(OVERRIDES_ROOT + rel))
        {
            if (in == null)
            {
                MineColonies.LOGGER.warn("port-assets: override {} missing from the jar — skipped", rel);
                return 0;
            }
            Files.createDirectories(target.getParent());
            try (final OutputStream out = Files.newOutputStream(target))
            {
                in.transferTo(out);
            }
            return 1;
        }
        catch (final Exception e)
        {
            MineColonies.LOGGER.warn("port-assets: could not apply override {}: {}", rel, e.toString());
            return 0;
        }
    }

    /**
     * Merges {@code portassets/overrides/sounds-extra.json} (port-maintained voice entries
     * missing from the official 1387 snapshot jar) into the store's
     * {@code assets/minecolonies/sounds.json}. Existing entries are never overwritten.
     *
     * @param storeDir the store directory.
     * @return number of entries merged (0 when nothing to merge or no target file).
     */
    private static int mergeSoundsExtra(final Path storeDir)
    {
        final Path target = storeDir.resolve("assets/minecolonies/sounds.json");
        if (!Files.isRegularFile(target))
        {
            return 0;
        }
        try (final InputStream in = PortAssetConverter.class.getResourceAsStream(OVERRIDES_ROOT + "sounds-extra.json"))
        {
            if (in == null)
            {
                MineColonies.LOGGER.warn("port-assets: sounds-extra.json missing from the jar — skipped");
                return 0;
            }
            final JsonObject extra = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            final JsonObject sounds = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8)).getAsJsonObject();

            int merged = 0;
            for (final var entry : extra.entrySet())
            {
                if (!sounds.has(entry.getKey()))
                {
                    sounds.add(entry.getKey(), entry.getValue());
                    merged++;
                }
            }
            if (merged > 0)
            {
                Files.writeString(target, GSON.toJson(sounds), StandardCharsets.UTF_8);
            }
            return merged;
        }
        catch (final Exception e)
        {
            MineColonies.LOGGER.warn("port-assets: sounds.json merge failed: {}", e.toString());
            return 0;
        }
    }

    private PortAssetConverter()
    {
        // static helper only
    }
}
