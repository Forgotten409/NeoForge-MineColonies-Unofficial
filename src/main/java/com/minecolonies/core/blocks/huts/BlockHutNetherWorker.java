package com.minecolonies.core.blocks.huts;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

public class BlockHutNetherWorker extends AbstractBlockHut<BlockHutNetherWorker>
{
    /**
     * PORT26: threads the registry name into AbstractBlockHut(String) so the block id
     * is set on the Properties before the Block constructor runs.
     */
    public BlockHutNetherWorker()
    {
        super("blockhutnetherworker");
    }


    /**
     * Method to return the name of the block.
     *
     * @return Name of the block.
     */
    @Override
    public String getHutName()
    {
        return "blockhutnetherworker";
    }

    /**
     * Method to get the building registry entry.
     *
     * @return The building entry.
     */
    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.netherWorker.get();
    }
    
}
