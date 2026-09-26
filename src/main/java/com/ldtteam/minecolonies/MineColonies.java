package com.ldtteam.minecolonies;

import com.ldtteam.blockui.mod.BlockUI;
import com.ldtteam.domumornamentum.DomumOrnamentum;
import com.ldtteam.multipiston.MultiPiston;
import com.ldtteam.structurize.Structurize;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MineColonies — port entry point.
 *
 * <p>Port target: Minecraft 26.1.2 / NeoForge 26.1.2.95 / Java 25.
 * Source baseline: MineColonies 1.1.1374-1.21.1-snapshot (MC 1.21.1 / NeoForge 21.1.80 / Java 21).
 *
 * <p>This is a SINGLE merged mod. The former separate dependencies (Structurize, BlockUI,
 * Domum Ornamentum, Multi-Piston, TownTalk) are merged into this mod's source tree during
 * the port — see PORTING.md in the project root for the phase plan.
 *
 * <p>Port status: PHASE 2 — domum-ornamentum merge in progress.
 */
@Mod(MineColonies.MOD_ID)
public final class MineColonies {
    public static final String MOD_ID = "minecolonies";
    public static final String MOD_NAME = "MineColonies";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    public MineColonies(final IEventBus modEventBus, final ModContainer modContainer) {
        LOGGER.info("MineColonies 26.1.2 port — starting (base 1.1.1374-1.21.1-snapshot)");
        LOGGER.info("Merged single-mod layout: structurize + blockui + domum-ornamentum + multipiston + towntalk");

        // PHASE 1: blockui (merged)
        BlockUI.init(modEventBus);

        // PHASE 2: domum-ornamentum (merged)
        DomumOrnamentum.init(modEventBus);

        // PHASE 5: multipiston (merged) — port order decided with user: multipiston first,
        // then structurize (its full merge replaces the minimal api-seed files multipiston
        // pulled in), then towntalk, then minecolonies.
        MultiPiston.init(modEventBus);

        // structurize (merged) — bootstrap needs the ModContainer for config registration.
        Structurize.init(modEventBus, modContainer);

        // PHASE 6: minecolonies (merged) — the main mod, ported on top of the merged deps.
        // See com.minecolonies.core.MineColonies for the registration chain.
        com.minecolonies.core.MineColonies.init(modEventBus, modContainer);

        // PORT26 (publishing support): external ARR asset loader — provisions the upstream
        // 1.21.1 assets/data/blueprints into <gamedir>/port-assets/ and injects them as an
        // always-enabled resource pack + data pack. See com.ldtteam.minecolonies.portassets.
        com.ldtteam.minecolonies.portassets.PortAssets.init(modEventBus);
    }
}
