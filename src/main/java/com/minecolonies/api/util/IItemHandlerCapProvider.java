package com.minecolonies.api.util;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities.Item;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Our class for to join {@link IItemHandler} providers, so we can have type independent code.
 */
@SuppressWarnings("removal") // PORT26: legacy IItemHandler / capability API (deprecated-for-removal) — migrate to ResourceHandler<ItemResource> in a later batch
@FunctionalInterface
public interface IItemHandlerCapProvider
{
    /**
     * For EntityCap register only
     */
    @Nullable
    default IItemHandler getItemHandlerCap(final Void nothing)
    {
        return getItemHandlerCap();
    }

    /**
     * @return direction-unaware itemHandler
     */
    @Nullable
    default IItemHandler getItemHandlerCap()
    {
        return getItemHandlerCap((Direction) null);
    }

    /**
     * @return direction-aware itemHandler
     */
    @Nullable
    IItemHandler getItemHandlerCap(final Direction direction);

    public static IItemHandlerCapProvider wrap(final BlockEntity blockEntity)
    {
        // PORT26: Capabilities.ItemHandler -> Capabilities.Item; capability now yields a
        // ResourceHandler<ItemResource> which we adapt back to the legacy IItemHandler view.
        return direction -> {
            final var handler = Item.BLOCK.getCapability(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, direction);
            return handler == null ? null : IItemHandler.of(handler);
        };
    }

    /**
     * @param sided if true then will use Direction aware capability, roughly should be true for machine-entities and false for mobs
     */
    public static IItemHandlerCapProvider wrap(final Entity entity, final boolean sided)
    {
        // PORT26: Capabilities.ItemHandler -> Capabilities.Item + legacy adapter
        return sided ? direction -> {
            final var handler = Item.ENTITY_AUTOMATION.getCapability(entity, direction);
            return handler == null ? null : IItemHandler.of(handler);
        } : direction -> {
            final var handler = Item.ENTITY.getCapability(entity, null);
            return handler == null ? null : IItemHandler.of(handler);
        };
    }

    public static IItemHandlerCapProvider wrap(final ItemStack itemStack)
    {
        // PORT26: stack capability needs an ItemAccess context now
        return direction -> {
            final var handler = itemStack.getCapability(Item.ITEM, net.neoforged.neoforge.transfer.access.ItemAccess.forStack(itemStack));
            return handler == null ? null : IItemHandler.of(handler);
        };
    }
}