package com.minecolonies.core.blocks.huts;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Hut for the mechanic. No different from {@link AbstractBlockHut}
 */
public class BlockHutMechanic extends AbstractBlockHut<BlockHutMechanic>
{
    /**
     * PORT26: threads the registry name into AbstractBlockHut(String) so the block id
     * is set on the Properties before the Block constructor runs.
     */
    public BlockHutMechanic()
    {
        super("blockhutmechanic");
    }

    @NotNull
    @Override
    public String getHutName()
    {
        return "blockhutmechanic";
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.mechanic.get();
    }
}
