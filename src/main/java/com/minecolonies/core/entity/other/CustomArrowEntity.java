package com.minecolonies.core.entity.other;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

/**
 * Custom arrow entity class which remove themselves when on the ground for a bit to not cause lag and they do not scale in damage with their motion.
 */
public class CustomArrowEntity extends Arrow
{
    /**
     * Max time the arrow is stuck before removing it
     */
    private static final int MAX_LIVE_TIME = 10 * 20;

    /**
     * Max time the arrow is stuck in ground before removing it.
     */
    private static final int GROUND_LIVE_TIME = 2 * 20;

    /**
     * Whether the arrow entity pierces players
     */
    private boolean armorPiercePlayer = false;

    /**
     * The water inertia.
     */
    private float waterInertia = 0.6f;

    /**
     * Callback on hitting an entity
     */
    private Predicate<EntityHitResult> onHitCallback = null;

    /**
     * PORT26: AbstractArrow#getBaseDamage() was removed (only setBaseDamage remains public).
     * We mirror every set into our own field so damage readers (marksman true-damage share,
     * onHit damage compensation below) keep working. Vanilla default is 2.0.
     */
    private double trackedBaseDamage = 2.0D;

    @Override
    public void setBaseDamage(final double baseDamage)
    {
        super.setBaseDamage(baseDamage);
        this.trackedBaseDamage = baseDamage;
    }

    /**
     * PORT26: replacement for the removed AbstractArrow#getBaseDamage().
     *
     * @return the base damage last set on this arrow.
     */
    public double getTrackedBaseDamage()
    {
        return this.trackedBaseDamage;
    }

    public CustomArrowEntity(final EntityType<? extends Arrow> type, final Level world)
    {
        super(type, world);
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target)
    {
        // TODO add enderman damage hit research here. Note that this is also used by mobs, so check the shooter first.
        super.doPostHurtEffects(target);
    }

    @Override
    protected float getWaterInertia()
    {
        return waterInertia;
    }

    /**
     * Setter for the water inertia to allow to penetrate liquids better.
     * @param waterInertia the new inertia.
     */
    public void setWaterInertia(final float waterInertia)
    {
        this.waterInertia = waterInertia;
    }

    @Override
    protected void onHitEntity(EntityHitResult traceResult)
    {
        final double prevDamage = getTrackedBaseDamage(); // PORT26: getBaseDamage() removed

        // Reduce damage by motion before vanilla increases it by the same factor, so our damage stays.
        float f = (float) this.getDeltaMovement().length();
        if (f != 0)
        {
            setBaseDamage(prevDamage / f);
        }

        if (armorPiercePlayer)
        {
            final Entity player = traceResult.getEntity();
            if (player instanceof Player)
            {
                Entity shooter = this.getOwner();
                DamageSource source;
                if (shooter == null)
                {
                    source = level().damageSources().arrow(this, this);
                }
                else
                {
                    source = level().damageSources().arrow(this, shooter);
                }
                player.hurt(source, (float) getTrackedBaseDamage()); // PORT26: getBaseDamage() removed
                setBaseDamage(0);
            }
        }

        super.onHitEntity(traceResult);

        // Set the old actual damage value back
        setBaseDamage(prevDamage);
        if (onHitCallback != null && onHitCallback.test(traceResult))
        {
            onHitCallback = null;
        }
    }

    /**
     * Set the hit callback action
     *
     * @param onHitCallback
     */
    public void setOnHitCallback(final Predicate<EntityHitResult> onHitCallback)
    {
        this.onHitCallback = onHitCallback;
    }

    /**
     * Makes the arrow pierce player armor
     */
    public void setPlayerArmorPierce()
    {
        armorPiercePlayer = true;
    }

    // PORT26: AbstractArrow#shouldFall is private now — vanilla's own implementation already does
    // exactly this (isInGround + noCollision check), so the override was dropped.

    /**
     * PORT26: Entity#save(CompoundTag)/load(CompoundTag) were replaced by the ValueInput/ValueOutput
     * system — the old "never persist this entity" overrides collapse into shouldBeSaved() = false.
     */
    @Override
    public boolean shouldBeSaved()
    {
        return false;
    }

    @Override
    public void tick()
    {
        if (this.tickCount > MAX_LIVE_TIME)
        {
            remove(RemovalReason.DISCARDED);
            return;
        }

        if (this.inGroundTime > GROUND_LIVE_TIME)
        {
            remove(RemovalReason.DISCARDED);
            return;
        }

        super.tick();
    }
}
