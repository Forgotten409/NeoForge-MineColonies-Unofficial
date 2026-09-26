package com.minecolonies.core.blocks.huts;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Hut for the stone mason. No different from {@link AbstractBlockHut}
 */
public class BlockHutStonemason extends AbstractBlockHut<BlockHutStonemason>
{
    /**
     * PORT26: threads the registry name into AbstractBlockHut(String) so the block id
     * is set on the Properties before the Block constructor runs.
     */
    public BlockHutStonemason()
    {
        super("blockhutstonemason");
    }

    @NotNull
    @Override
    public String getHutName()
    {
        return "blockhutstonemason";
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.stoneMason.get();
    }
}
