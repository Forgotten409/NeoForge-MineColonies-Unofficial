package com.ldtteam.minecolonies.portassets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.ldtteam.minecolonies.MineColonies;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * Runtime bridge for the official <b>TownTalk</b> companion jar — PORT26 (publishing
 * support).
 *
 * <p>TownTalk (mod id {@code towntalk}, ARR) is the citizen-voice pack of the MineColonies
 * ecosystem: a {@code respack/} folder inside its jar carrying
 * {@code respack/assets/minecolonies/sounds/**} plus exactly THREE classes, of which only
 * one matters at runtime — {@code com.ldtteam.towntalk.TownTalk}, whose whole job is to
 * register the {@code respack/} folder as a resource pack via
 * {@code AddPackFindersEvent.addPackFinders(ResourceLocation("towntalk","respack"), …)}.</p>
 *
 * <h2>Why the official jar cannot run as a mod on 26.1.2</h2>
 * <p>The class references {@code net.minecraft.resources.ResourceLocation} — renamed to
 * {@code Identifier} in this MC line — so its pack-registration listener dies with
 * {@code NoClassDefFoundError} the moment the pack repository is built. (Its datagen
 * provider, the other two classes, only runs in data-generation launches and is
 * irrelevant here.) This bridge therefore never relies on the old classes: THIS port
 * re-implements their entire runtime behaviour and treats the user's TownTalk jar purely
 * as an <b>asset source</b>, exactly like the MineColonies jar itself:</p>
 *
 * <ul>
 *   <li><b>jar in {@code mods/}, disabled</b> (renamed {@code towntalk-1.2.0.jar.disabled}
 *       — the recommended way to keep it in the mods folder): detected by the provisioner's
 *       mods-folder scan and extracted into {@code port-assets/towntalk/}.</li>
 *   <li><b>jar in {@code port-assets/source-jars/}</b>: classic manual install path.</li>
 *   <li><b>jar active in {@code mods/}</b>: the game may crash during resource loading
 *       (the old listener's {@code NoClassDefFoundError} — nothing this port can prevent
 *       from inside another mod). A loud warning is logged at construction time; if the
 *       game survives anyway (event-bus error containment), the jar is ALSO harvested as
 *       an asset source so the voices work through the format-safe injected store pack.</li>
 *   <li><b>no jar anywhere</b>: the normal download flow offers the official CurseForge
 *       file (see {@link PortAssets#NAMESPACES}).</li>
 * </ul>
 *
 * <p>The extracted store is injected by {@link PortAssetPackSources} like every other
 * namespace — with a {@code pack.mcmeta} regenerated for the CURRENT pack formats, which
 * the old jar's own format-34 metadata could never satisfy on 26.1.2.</p>
 */
final class TownTalkCompat
{
    /** The TownTalk mod id. */
    static final String MOD_ID = "towntalk";

    private TownTalkCompat()
    {
        // static helper only
    }

    /**
     * @return true when the official TownTalk mod is loaded as an ACTIVE mod in this game.
     */
    static boolean isModActive()
    {
        try
        {
            return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        }
        catch (final Throwable t)
        {
            return false;
        }
    }

    /**
     * Called once from {@link PortAssets#init} — logs the compatibility warning when the
     * official TownTalk jar sits ACTIVE in the mods folder.
     */
    static void warnIfActiveMod()
    {
        if (!isModActive())
        {
            return;
        }
        MineColonies.LOGGER.warn("port-assets: TownTalk detected as an ACTIVE mod in mods/. TownTalk 1.2.0 is built"
            + " for MC 1.21.1 and references classes that no longer exist on 26.1.2 — its own pack registration may"
            + " crash the game during resource loading. This port re-implements TownTalk's entire runtime behaviour,"
            + " so the jar only needs to be an ASSET SOURCE. If the game crashes or shows a loading error:"
            + " rename the jar to 'towntalk-1.2.0.jar.disabled' (or move it to <gamedir>/port-assets/source-jars/)"
            + " and restart — the voices keep working through this port's loader either way.");
    }

    /**
     * Path of the ACTIVE TownTalk mod's jar file — usable as a provisioning source when the
     * old mod itself cannot run. Uses the same mod-file lookup as
     * {@code StructurePacks.findModResource}.
     *
     * @return the jar path, or null when TownTalk is not an active packaged mod (or its
     *         file is an exploded dev directory).
     */
    static Path activeModJar()
    {
        try
        {
            // Wildcard REQUIRED — ModList#getModContainerById returns Optional<? extends
            // ModContainer> on 26.1.2 (same as BundledAssetDetector's verified pattern).
            final Optional<? extends ModContainer> container = ModList.get().getModContainerById(MOD_ID);
            if (container.isEmpty())
            {
                return null;
            }
            final IModInfo mod = container.get().getModInfo();
            final Path path = mod.getOwningFile().getFile().getFilePath();
            return path != null && Files.isRegularFile(path) ? path : null;
        }
        catch (final Throwable t)
        {
            MineColonies.LOGGER.warn("port-assets: could not locate the active TownTalk mod file: {}", t.toString());
            return null;
        }
    }
}
