package com.minecolonies.api.entity.mobs.pirates;

import com.minecolonies.api.util.IItemHandlerCapProvider;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.capabilities.Capabilities.Item;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("removal") // PORT26: legacy IItemHandler / capability API (deprecated-for-removal) — migrate to ResourceHandler<ItemResource> in a later batch
public interface IPirateEntity extends Enemy, CommandSource, IItemHandlerCapProvider
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

    // PORT26: CommandSource has four abstract methods in 26.1.2 (incl. the new
    // shouldInformAdmins) — hostile MineColonies mobs never act as command feedback
    // sources, so the whole raider family gets NULL-like defaults here.
    @Override
    default void sendSystemMessage(final net.minecraft.network.chat.Component message)
    {
        // no-op: raiders do not receive command output
    }

    @Override
    default boolean acceptsSuccess()
    {
        return false;
    }

    @Override
    default boolean acceptsFailure()
    {
        return false;
    }

    @Override
    default boolean shouldInformAdmins()
    {
        return false;
    }
}
