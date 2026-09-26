package com.ldtteam.domumornamentum.item;

import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.level.block.Block;

/**
 * PORT26: the legacy {@code verifyComponentsAfterLoad} upgrade hook was removed from Item in
 * 1.21.9 — see {@link SelfUpgradingBlockItem} for details. Kept as the item-type anchor for
 * double-high (door-like) materially textured blocks.
 */
public class SelfUpgradingDoubleHighBlockItem extends DoubleHighBlockItem
{
    public SelfUpgradingDoubleHighBlockItem(final Block block, final Properties properties)
    {
        super(block, properties);
    }
}
