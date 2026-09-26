package com.minecolonies.api.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Class for specific minecraft tag utilities.
 */
public final class TagUtils
{
    private TagUtils()
    {
        throw new IllegalStateException("Tried to initialize: TagUtils but this is a Utility class.");
    }

    /**
     * Get a tag for items.
     * @param resourceLocation the unique id.
     * @return the tag or an empty placeholder if not existant.
     */
    public static TagKey<Item> getItem(final Identifier resourceLocation)
    {
        // PORT26 (Batch 27): ItemTags.create(String) is private in 26.1.2.
        return TagKey.create(Registries.ITEM, resourceLocation);
    }

    /**
     * Get a tag for items.
     * @param resourceLocation the unique id.
     * @return the tag or an empty placeholder if not existant.
     */
    public static TagKey<Block> getBlock(final Identifier resourceLocation)
    {
        // PORT26 (Batch 27): BlockTags.create(String) is private in 26.1.2.
        return TagKey.create(Registries.BLOCK, resourceLocation);
    }
}
