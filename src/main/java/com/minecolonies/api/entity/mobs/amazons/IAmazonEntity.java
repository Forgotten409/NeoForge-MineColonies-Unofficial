package com.minecolonies.api.entity.mobs.amazons;

import com.minecolonies.api.util.IItemHandlerCapProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.capabilities.Capabilities.Item;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A tagging interface for Amazon Entities.
 */
@SuppressWarnings("removal") // PORT26: legacy IItemHandler / capability API (deprecated-for-removal) — migrate to ResourceHandler<ItemResource> in a later batch
public interface IAmazonEntity extends Enemy, IItemHandlerCapProvider
{
    @Override
    @Nullable
    default IItemHandler getItemHandlerCap(final Direction direction)
    {
        // LivingEntities have cap registered by forge
        // PORT26: capability yields ResourceHandler<ItemResource> -> adapt to legacy IItemHandler
        final var handler = Item.ENTITY.getCapability((LivingEntity) this, null);
        return handler == null ? null : IItemHandler.of(handler);
    }
}
