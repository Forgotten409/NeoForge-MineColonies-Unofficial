package com.minecolonies.api.entity.other;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Special minecolonies minecart that doesn't collide.
 *
 * <p>PORT26: the 1.15-era copy of the vanilla minecart physics (moveAlongTrack/tick/etc.)
 * was dropped. The new vanilla {@code MinecartBehavior} handles rail movement; this class
 * only keeps the minecolonies-specific behaviour: no collision, no pickup, no pushing,
 * no interaction, and self-discard when unoccupied.</p>
 */
public class MinecoloniesMinecart extends Minecart
{
    private static final Vec3 LOWERED_PASSENGER_ATTACHMENT = new Vec3(0.0, 0.0, 0.0);

    /**
     * Constructor to create the minecart.
     *
     * @param type  the entity type.
     * @param world the world.
     */
    public MinecoloniesMinecart(final EntityType<?> type, final Level world)
    {
        super(type, world);
    }

    @Override
    public void tick()
    {
        super.tick();

        if (!this.level().isClientSide() && this.tickCount % 20 == 19 && this.getPassengers().isEmpty())
        {
            this.discard();
        }
    }

    @Override
    public void destroy(final net.minecraft.server.level.ServerLevel level, final DamageSource source)
    {
        this.kill(level);
    }

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand, final Vec3 location)
    {
        return InteractionResult.FAIL;
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    @Override
    public void push(@NotNull final Entity entityIn)
    {
        // Do nothing
    }

    @Override
    public void playerTouch(final Player entityIn)
    {
        // Do nothing
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public boolean canCollideWith(final Entity other)
    {
        return false;
    }

    @Override
    @NotNull
    protected Vec3 getPassengerAttachmentPoint(@NotNull Entity entity, @NotNull EntityDimensions dimensions, float scaleFactor)
    {
        return LOWERED_PASSENGER_ATTACHMENT;
    }
}
