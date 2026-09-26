package com.minecolonies.core.blocks.huts;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Hut for the StoneSmeltery. No different from {@link AbstractBlockHut}
 */
public class BlockHutStoneSmeltery extends AbstractBlockHut<BlockHutStoneSmeltery>
{
    /**
     * PORT26: threads the registry name into AbstractBlockHut(String) so the block id
     * is set on the Properties before the Block constructor runs.
     */
    public BlockHutStoneSmeltery()
    {
        super("blockhutstonesmeltery");
    }

    @NotNull
    @Override
    public String getHutName()
    {
        return "blockhutstonesmeltery";
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.stoneSmelter.get();
    }
}
