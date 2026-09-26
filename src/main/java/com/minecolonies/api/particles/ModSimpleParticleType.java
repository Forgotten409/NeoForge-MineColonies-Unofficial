package com.minecolonies.api.particles;

import net.minecraft.core.particles.SimpleParticleType;

/**
 * PORT26: {@link SimpleParticleType}'s constructor became protected in 26.1.2 —
 * vanilla only instantiates it from within {@code net.minecraft.core.particles}
 * itself. Mods that need a legacy "simple" particle type extend it here instead.
 */
public class ModSimpleParticleType extends SimpleParticleType
{
    public ModSimpleParticleType(final boolean overrideLimiter)
    {
        super(overrideLimiter);
    }
}
