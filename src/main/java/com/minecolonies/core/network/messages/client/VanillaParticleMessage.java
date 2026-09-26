package com.minecolonies.core.network.messages.client;

import com.ldtteam.common.network.AbstractClientPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Random;

import static com.minecolonies.api.util.constant.CitizenConstants.CITIZEN_HEIGHT;
import static com.minecolonies.api.util.constant.CitizenConstants.CITIZEN_WIDTH;

/**
 * Message for vanilla particles around a citizen, in villager-like shape.
 */
public class VanillaParticleMessage extends AbstractClientPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forClient(Constants.MOD_ID, "vanilla_particle_message", VanillaParticleMessage::new);

    /**
     * Citizen Position
     */
    private final double x;
    private final double y;
    private final double z;

    /**
     * Particle options
     */
    // PORT26: SimpleParticleType-only field widened to ParticleOptions — particles like
    // DRAGON_BREATH are ParticleType<PowerParticleOption> now, not SimpleParticleType.
    private final ParticleOptions type;

    public VanillaParticleMessage(final double x, final double y, final double z, final ParticleOptions type)
    {
        super(TYPE);
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
    }

    protected VanillaParticleMessage(final RegistryFriendlyByteBuf byteBuf, final PlayMessageType<?> type)
    {
        super(byteBuf, type);
        x = byteBuf.readDouble();
        y = byteBuf.readDouble();
        z = byteBuf.readDouble();
        // PORT26: rebuild options from the registry id; non-simple types (e.g. DRAGON_BREATH)
        // get their default option (power 1.0, same as the PowerParticleOption codec default).
        final ParticleType<?> particleType = BuiltInRegistries.PARTICLE_TYPE.getValue(byteBuf.readIdentifier());
        this.type = particleType instanceof SimpleParticleType simpleParticleType
                      ? simpleParticleType
                      : PowerParticleOption.create((ParticleType<PowerParticleOption>) particleType, 1.0F);
    }

    @Override
    protected void toBytes(final RegistryFriendlyByteBuf byteBuf)
    {
        byteBuf.writeDouble(x);
        byteBuf.writeDouble(y);
        byteBuf.writeDouble(z);
        // PORT26: ParticleOptions#getType() gives the registry key holder.
        byteBuf.writeIdentifier(BuiltInRegistries.PARTICLE_TYPE.getKey(this.type.getType()));
    }

    @Override
    public void onExecute(final IPayloadContext ctxIn, final Player player)
    {
        spawnParticles(type, player.level(), x, y, z);
    }

    /**
     * Spawns the given particle randomly around the position.
     *
     * @param particleType particle to spawn
     * @param world        world to use
     * @param x            x pos
     * @param y            y pos
     * @param z            z pos
     */
    // PORT26: operates on ParticleOptions instead of SimpleParticleType.
    private void spawnParticles(final ParticleOptions particleType, final Level world, final double x, final double y, final double z)
    {
        final Random rand = new Random();
        for (int i = 0; i < 5; ++i)
        {
            double d0 = rand.nextGaussian() * 0.02D;
            double d1 = rand.nextGaussian() * 0.02D;
            double d2 = rand.nextGaussian() * 0.02D;
            world.addParticle(particleType,
              x + (rand.nextFloat() * CITIZEN_WIDTH * 2.0F) - CITIZEN_WIDTH,
              y + 1.0D + (rand.nextFloat() * CITIZEN_HEIGHT),
              z + (rand.nextFloat() * CITIZEN_WIDTH * 2.0F) - CITIZEN_WIDTH,
              d0,
              d1,
              d2);
        }
    }
}
