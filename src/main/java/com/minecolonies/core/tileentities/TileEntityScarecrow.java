package com.minecolonies.core.tileentities;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.tileentities.AbstractTileEntityScarecrow;
import com.minecolonies.api.tileentities.ScareCrowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

import static com.minecolonies.core.colony.buildingextensions.FarmField.*;

/**
 * The scarecrow tile entity to store extra data.
 */
@SuppressWarnings("PMD.ExcessiveImports")
public class TileEntityScarecrow extends AbstractTileEntityScarecrow
{
    /**
     * Random generator.
     */
    private final Random random = new Random();

    /**
     * The colony this field is located in.
     */
    private IColony currentColony;

    /**
     * The type of the scarecrow.
     */
    private ScareCrowType type;

    /**
     * The size of the field in all four directions
     * in the same order as {@link Direction}:
     * S, W, N, E
     */
    private int[] fieldSize = {DEFAULT_RANGE, DEFAULT_RANGE, DEFAULT_RANGE, DEFAULT_RANGE};

    /**
     * Creates an instance of the tileEntity.
     */
    public TileEntityScarecrow(final BlockPos pos, final BlockState state)
    {
        super(pos, state);
    }

    @Override
    public ScareCrowType getScarecrowType()
    {
        if (this.type == null)
        {
            final ScareCrowType[] values = ScareCrowType.values();
            this.type = values[this.random.nextInt(values.length)];
        }
        return this.type;
    }

    @Override
    public IColony getCurrentColony()
    {
        if (currentColony == null && level != null)
        {
            this.currentColony = IColonyManager.getInstance().getIColony(level, worldPosition);
        }
        return currentColony;
    }

    @Override
    public void saveAdditional(final net.minecraft.world.level.storage.ValueOutput output)
    {
        super.saveAdditional(output);
        // PORT26: bodies rewritten from the old compoundTag param to the ValueOutput.
        output.putIntArray(TAG_RADIUS, fieldSize);
    }

    @Override
    public void loadAdditional(final net.minecraft.world.level.storage.ValueInput input)
    {
        super.loadAdditional(input);
        fieldSize = input.getIntArray(TAG_RADIUS).orElse(new int[0]);
    }

    /**
     * @param direction the direction for the radius
     * @param radius    the number of blocks from the scarecrow that the farmer will work with
     */
    public void setFieldSize(Direction direction, int radius)
    {
        this.fieldSize[direction.get2DDataValue()] = Math.min(radius, MAX_RANGE);
        setChanged();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag(@NotNull final HolderLookup.Provider provider)
    {
        // PORT26: saveWithId takes ValueOutput now — TagValueOutput bridge.
        final net.minecraft.world.level.storage.TagValueOutput output =
          net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, provider);
        this.saveWithId(output);
        return output.buildResult();
    }

    /**
     * Field size.
     * @return the field size.
     */
    public int[] getFieldSize()
    {
        return fieldSize;
    }
}
