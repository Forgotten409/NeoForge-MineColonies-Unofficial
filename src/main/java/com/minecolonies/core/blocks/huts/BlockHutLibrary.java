package com.minecolonies.core.blocks.huts;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Hut for the library. No different from {@link AbstractBlockHut}
 */
public class BlockHutLibrary extends AbstractBlockHut<BlockHutLibrary>
{
    /**
     * PORT26: threads the registry name into AbstractBlockHut(String) so the block id
     * is set on the Properties before the Block constructor runs.
     */
    public BlockHutLibrary()
    {
        super("blockhutlibrary");
    }

    @NotNull
    @Override
    public String getHutName()
    {
        return "blockhutlibrary";
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.library.get();
    }
}
