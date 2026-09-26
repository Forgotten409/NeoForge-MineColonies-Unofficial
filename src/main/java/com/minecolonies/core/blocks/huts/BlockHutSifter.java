package com.minecolonies.core.blocks.huts;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Hut for the sifter. No different from {@link AbstractBlockHut}
 */
public class BlockHutSifter extends AbstractBlockHut<BlockHutSifter>
{
    /**
     * PORT26: threads the registry name into AbstractBlockHut(String) so the block id
     * is set on the Properties before the Block constructor runs.
     */
    public BlockHutSifter()
    {
        super("blockhutsifter");
    }

    @NotNull
    @Override
    public String getHutName()
    {
        return "blockhutsifter";
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.sifter.get();
    }
}
