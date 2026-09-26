package com.minecolonies.core.network.messages.client;

import com.ldtteam.common.network.AbstractClientPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Handles the server telling nearby clients to render a particle effect. Created: February 10, 2016
 *
 * @author Colton
 */
public class BlockParticleEffectMessage extends AbstractClientPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forClient(Constants.MOD_ID, "block_particle_effect", BlockParticleEffectMessage::new);

    public static final int BREAK_BLOCK = -1;

    private final BlockPos   pos;
    private final BlockState block;
    private final int        side;

    /**
     * Sends a message for particle effect.
     *
     * @param pos   Coordinates
     * @param state Block State
     * @param side  Side of the block causing effect
     */
    public BlockParticleEffectMessage(final BlockPos pos, @NotNull final BlockState state, final int side)
    {
        super(TYPE);
        this.pos = pos;
        this.block = state;
        this.side = side;
    }

    public BlockParticleEffectMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        pos = buf.readBlockPos();
        block = Block.stateById(buf.readInt());
        side = buf.readInt();
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        buf.writeInt(Block.getId(block));
        buf.writeInt(side);
    }

    @Override
    protected void onExecute(final IPayloadContext ctxIn, final Player player)
    {
        if (side == BREAK_BLOCK)
        {
            // PORT26: ParticleEngine#destroy(BlockPos, BlockState) removed — the vanilla
            // replacement is ClientLevel#addDestroyBlockEffect (same burst of terrain particles
            // + break sound; used by LevelEventHandler for level event 2001).
            Minecraft.getInstance().level.addDestroyBlockEffect(pos, block);
        }
        else
        {
            // PORT26: ParticleEngine#crack(BlockPos, Direction) removed. Replicates its exact
            // behavior from 1.21.x: a single BlockParticleOption(ParticleTypes.BLOCK, state)
            // particle at the center of the face being hit.
            final Direction face = Direction.from3DDataValue(side);
            Minecraft.getInstance().level.addParticle(
              new BlockParticleOption(ParticleTypes.BLOCK, block),
              pos.getX() + 0.5 + face.getStepX() * 0.4,
              pos.getY() + 0.5 + face.getStepY() * 0.4,
              pos.getZ() + 0.5 + face.getStepZ() * 0.4,
              0.0, 0.0, 0.0);
        }
    }
}
