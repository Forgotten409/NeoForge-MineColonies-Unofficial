package com.minecolonies.core.blocks.huts;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Block of the combat academy camp.
 */
public class BlockHutCombatAcademy extends AbstractBlockHut<BlockHutCombatAcademy>
{
    /**
     * PORT26: threads the registry name into AbstractBlockHut(String) so the block id
     * is set on the Properties before the Block constructor runs.
     */
    public BlockHutCombatAcademy()
    {
        super("blockhutcombatacademy");
    }

    @NotNull
    @Override
    public String getHutName()
    {
        return "blockhutcombatacademy";
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.combatAcademy.get();
    }
}
