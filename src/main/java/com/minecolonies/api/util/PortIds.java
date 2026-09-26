package com.minecolonies.api.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.minecolonies.api.util.constant.Constants;

/**
 * PORT26 helper for the minecolonies "direct registry registration" style (blocks/items
 * created via {@code new XBlock().registerBlock(registry)} during {@code RegisterEvent}).
 *
 * <p>MC 26.1.2 requires the registry id to be present on {@link BlockBehaviour.Properties}
 * (via {@code setId(ResourceKey<Block>)}) BEFORE the Block constructor runs — the
 * constructor resolves the loot table and description id from it
 * ({@code BlockBehaviour$Properties.effectiveDrops} throws {@code NPE: "Block id not set"}
 * otherwise). The same applies to {@link Item.Properties} ({@code NPE: "Item id not set"}
 * from {@code Item.Properties.itemIdOrThrow()} in the Item constructor).</p>
 *
 * <p>DeferredRegister-based registrations (structurize, multipiston, domum ornamentum)
 * get this for free through {@code DeferredRegister.Blocks#registerBlock} /
 * {@code DeferredRegister.Items#registerItem}; this helper gives the legacy
 * direct-registration classes in minecolonies core the same semantics.</p>
 *
 * <p>Note: the id set on the properties MUST be identical to the name the object is
 * later registered under ({@code Registry.register(registry, id, value)}) — the derived
 * loot table key and translation key depend on it.</p>
 */
public final class PortIds
{
    private PortIds()
    {
        /* utility class */
    }

    /**
     * Block registry key for the given path under the minecolonies namespace.
     *
     * @param name the registry path (e.g. "blockhutbaker")
     * @return the block ResourceKey
     */
    public static ResourceKey<Block> blockKey(final String name)
    {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Constants.MOD_ID, name));
    }

    /**
     * Item registry key for the given path under the minecolonies namespace.
     *
     * @param name the registry path (e.g. "scepterlumberjack")
     * @return the item ResourceKey
     */
    public static ResourceKey<Item> itemKey(final String name)
    {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Constants.MOD_ID, name));
    }

    /**
     * Item registry key for a full identifier (used when the caller already knows the
     * block's registry name, e.g. for BlockItems).
     *
     * @param id the full identifier
     * @return the item ResourceKey
     */
    public static ResourceKey<Item> itemKey(final Identifier id)
    {
        return ResourceKey.create(Registries.ITEM, id);
    }
}
