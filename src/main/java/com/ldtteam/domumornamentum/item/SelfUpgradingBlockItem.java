package com.ldtteam.domumornamentum.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * BlockItem that historically performed a DFU-style upgrade of legacy NBT into data components
 * via {@code Item#verifyComponentsAfterLoad}.
 *
 * <p>PORT26: {@code verifyComponentsAfterLoad} was removed from {@code Item} in 1.21.9 (see the
 * 1.21.9 primer "List of Removals"), and {@code DataComponents#BLOCK_ENTITY_DATA} is now the
 * type-safe {@code TypedEntityData}. Since a 26.1.2 install has no legacy domum NBT to migrate,
 * the upgrade machinery was dropped and this class is kept as the item-type anchor used by
 * registration code.
 */
public class SelfUpgradingBlockItem extends BlockItem
{
    public SelfUpgradingBlockItem(final Block block, final Properties properties)
    {
        super(block, properties);
    }
}
