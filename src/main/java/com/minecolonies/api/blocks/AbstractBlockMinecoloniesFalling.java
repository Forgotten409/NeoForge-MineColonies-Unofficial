package com.minecolonies.api.blocks;

import com.minecolonies.api.blocks.interfaces.IBlockMinecolonies;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

public abstract class AbstractBlockMinecoloniesFalling<B extends AbstractBlockMinecoloniesFalling<B>> extends FallingBlock implements IBlockMinecolonies<B>
{
    public AbstractBlockMinecoloniesFalling(final Properties properties)
    {
        super(properties);
    }

    /**
     * PORT26: FallingBlock#getDustColor became abstract in 26.1.2. The 1.21.1 base-class
     * default returned {@code -1} (white/untinted falling dust particles), which the
     * construction tape inherited — preserve exactly that.
     */
    @Override
    public int getDustColor(final BlockState state, final BlockGetter level, final BlockPos pos)
    {
        return -1;
    }

    @Override
    public void registerBlockItem(final Registry<Item> registry, final Item.Properties properties)
    {
        // PORT26: item id on Item.Properties before the Item constructor runs
        // ("Item id not set"); useBlockDescriptionPrefix keeps the 1.21.1 lang key.
        Registry.register(registry, getRegistryName(),
          new BlockItem(this, properties.setId(ResourceKey.create(Registries.ITEM, getRegistryName())).useBlockDescriptionPrefix()));
    }

    @Override
    public B registerBlock(final Registry<Block> registry)
    {
        Registry.register(registry, getRegistryName(), this);
        return (B) this;
    }
}
