package com.minecolonies.api.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * PORT26 (0.6.0) crash-safety wrapper around {@code Level#damageSources()#source(...)}.
 *
 * <p><b>Why this exists:</b> the minecolonies damage types are data-driven registry
 * entries ({@code data/minecolonies/damage_type/*.json}). Since the 0.6.0 ARR
 * externalization those JSONs are no longer bundled in the publish jar — the external
 * store (the player's own downloaded official jar, converted at provisioning time)
 * provides them, exactly like the recipes, loot tables, researches and tags. Vanilla's
 * {@code DamageSources#source(ResourceKey, …)} however resolves the key with
 * {@code getOrThrow} and <b>hard-crashes</b> the moment an entry is unbound — so a
 * skipped/failed store download used to turn the very first raider despawn, guard
 * attack or citizen death into a server crash (the 0.4.x play-test regression that
 * originally justified bundling the 47 ARR JSONs).</p>
 *
 * <p>This wrapper keeps the mod alive in that degraded state instead: when the key is
 * unbound it logs ONE warning per key and falls back to a vanilla damage source —
 * {@code mobAttack(causing)} when an attacker exists (damage attribution and kill
 * credit stay intact), otherwise {@code minecraft:generic_kill} (the same damage type
 * {@code /kill} uses; built via a runtime {@link ResourceKey} so no compile-time
 * dependency on a vanilla constant). Every code path that previously called
 * {@code level.damageSources().source(DamageSourceKeys.X, …)} goes through here.</p>
 *
 * <p>When the external store IS provisioned (the normal case) the behaviour is
 * byte-identical to vanilla: the key resolves and the real damage type is used.</p>
 */
public final class PortDamageSources
{
    /**
     * Fallback damage-type key for entity-less sources: {@code minecraft:generic_kill} —
     * the vanilla {@code /kill} damage type, always bound by the built-in vanilla
     * datapack. Constructed at RUNTIME (not via a {@code DamageTypes} constant) so the
     * class only depends on API this port already uses elsewhere.
     */
    private static final ResourceKey<DamageType> GENERIC_KILL = ResourceKey.create(
        Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("minecraft", "generic_kill"));

    /** Keys already reported as unbound — one warning per key per JVM, not per hit. */
    private static final Set<ResourceKey<DamageType>> WARNED_UNBOUND = ConcurrentHashMap.newKeySet();

    private PortDamageSources()
    {
        // static helper only
    }

    /**
     * Damage source for a minecolonies damage-type key, without involved entities.
     *
     * @param level the level the damage happens in.
     * @param key   the damage type key ({@link DamageSourceKeys}).
     * @return the resolved source, or the generic-kill fallback when the key is unbound.
     */
    public static DamageSource source(final Level level, final ResourceKey<DamageType> key)
    {
        return source(level, key, null, null);
    }

    /**
     * Damage source for a minecolonies damage-type key with a single involved entity.
     *
     * @param level   the level the damage happens in.
     * @param key     the damage type key ({@link DamageSourceKeys}).
     * @param causing the entity causing the damage (attacker), may be {@code null}.
     * @return the resolved source, or a fallback when the key is unbound.
     */
    public static DamageSource source(final Level level, final ResourceKey<DamageType> key, @Nullable final Entity causing)
    {
        return source(level, key, null, causing);
    }

    /**
     * Damage source for a minecolonies damage-type key with direct and causing entities
     * (the same argument contract as vanilla {@code DamageSources#source}).
     *
     * @param level   the level the damage happens in.
     * @param key     the damage type key ({@link DamageSourceKeys}).
     * @param direct  the direct source entity (e.g. the projectile), may be {@code null}.
     * @param causing the entity causing the damage (attacker), may be {@code null}.
     * @return the resolved source, or a fallback when the key is unbound.
     */
    public static DamageSource source(
        final Level level, final ResourceKey<DamageType> key,
        @Nullable final Entity direct, @Nullable final Entity causing)
    {
        final HolderLookup.RegistryLookup<DamageType> lookup = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        if (lookup.get(key).isPresent())
        {
            return level.damageSources().source(key, direct, causing);
        }
        if (WARNED_UNBOUND.add(key))
        {
            Log.getLogger().warn("minecolonies damage type {} is not bound (external asset store missing?) — "
                + "falling back to a vanilla damage source for it; run the port-assets download to fix this", key.identifier());
        }
        // PORT26: DamageSources#mobAttack requires a LivingEntity — an Entity-typed causer
        // (e.g. a projectile acting as its own owner) cannot be attributed, so it gets the
        // generic-kill fallback too; attribution is already broken in that degraded state.
        return causing instanceof LivingEntity livingAttacker
                 ? level.damageSources().mobAttack(livingAttacker)
                 : level.damageSources().source(GENERIC_KILL);
    }
}
