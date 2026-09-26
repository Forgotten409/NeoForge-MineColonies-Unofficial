package com.minecolonies.core.blocks.huts;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Block of the Archers trainings camp.
 */
public class BlockHutArchery extends AbstractBlockHut<BlockHutArchery>
{
    /**
     * PORT26: threads the registry name into AbstractBlockHut(String) so the block id
     * is set on the Properties before the Block constructor runs.
     */
    public BlockHutArchery()
    {
        super("blockhutarchery");
    }

    @NotNull
    @Override
    public String getHutName()
    {
        return "blockhutarchery";
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.archery.get();
    }
}
