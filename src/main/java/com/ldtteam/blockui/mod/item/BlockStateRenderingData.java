package com.ldtteam.blockui.mod.item;

import com.ldtteam.blockui.mod.BlockUI;
import com.ldtteam.blockui.mod.Log;
import com.ldtteam.common.util.BlockToItemHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.common.util.Lazy;
import org.jetbrains.annotations.Nullable;
import java.util.function.Function;

/**
 * Holds blockstate rendering data for UIs. BlockState must match blockEntity
 */
public record BlockStateRenderingData(BlockState blockState,
    @Nullable BlockEntity blockEntity,
    ModelData modelData,
    boolean modelNeedsRotationFix,
    Lazy<ItemStack> playerPickedItemStack)
{
    public static final BlockPos ILLEGAL_BLOCK_ENTITY_POS = BlockPos.ZERO.below(1000);

    private BlockStateRenderingData(final BlockState blockState,
        final BlockEntity blockEntity,
        final ModelData modelData,
        final boolean modelNeedsRotationFix)
    {
        this(blockState,
            blockEntity,
            modelData,
            modelNeedsRotationFix,
            Lazy.of(() -> BlockToItemHelper.getItemStack(blockState, blockEntity, Minecraft.getInstance().player)));
    }

    private BlockStateRenderingData(final BlockState blockState, final BlockEntity blockEntity, final ModelData modelData)
    {
        this(blockState, blockEntity, modelData, checkModelForYrotation(blockState));
    }

    /**
     * @return captures blockstate in given level at given pos in current time (now)
     */
    public static BlockStateRenderingData of(final Level level, final BlockPos pos, final Player player)
    {
        final BlockState blockState = level.getBlockState(pos);
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        final ItemStack itemStack = BlockToItemHelper.getItemStack(level, pos, player);

        return new BlockStateRenderingData(blockState,
            blockEntity,
            getModelData(blockState, blockEntity),
            checkModelForYrotation(blockState),
            Lazy.of(() -> itemStack));
    }

    /**
     * @param blockEntity must match blockState
     */
    public static BlockStateRenderingData of(final BlockState blockState, @Nullable final BlockEntity blockEntity)
    {
        return blockEntity == null ? of(blockState) : new BlockStateRenderingData(blockState, blockEntity, getModelData(blockState, blockEntity));
    }

    /**
     * If blockState should have blockEntity then a new fresh empty one will be created. Use {@link #of(BlockState, BlockEntity)} everywhere possible
     */
    public static BlockStateRenderingData of(final BlockState blockState)
    {
        if (blockState.hasBlockEntity() && blockState.getBlock() instanceof final EntityBlock entityBlock)
        {
            final BlockEntity be = entityBlock.newBlockEntity(ILLEGAL_BLOCK_ENTITY_POS, blockState);
            if (be != null)
            {
                return of(blockState, be);
            }
        }
        return new BlockStateRenderingData(blockState, null, null);
    }

    /**
     * Useful when you want to update blockEntity. Keeps modelData in sync
     */
    public BlockStateRenderingData updateBlockEntity(final Function<BlockEntity, BlockEntity> updater)
    {
        final BlockEntity updated = updater.apply(blockEntity);
        return new BlockStateRenderingData(blockState, updated, getModelData(blockState, updated), modelNeedsRotationFix);
    }

    public ModelData modelData()
    {
        return modelData == null ? ModelData.EMPTY : modelData;
    }

    private static ModelData getModelData(final BlockState blockState, final BlockEntity blockEntity)
    {
        ModelData model = ModelData.EMPTY;
        try
        {
            model = blockEntity.getModelData();
        }
        catch (final Exception e)
        {
            Log.getLogger().warn("Could not get model data for: " + blockState.toString(), e);
        }
        return model;
    }

    /**
     * @return best guess using player pick and similar methods
     */
    public ItemStack itemStack()
    {
        return playerPickedItemStack.get();
    }

    /**
     * Data-driven override (upstream "move to tag" TODO resolved): blocks tagged here
     * always count as Y-rotation-only models, regardless of their blockstate properties.
     * Pack devs extend it via {@code data/blockui/tags/block/y_rotation_models.json}.
     */
    public static final TagKey<Block> Y_ROTATION_MODELS =
      TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(BlockUI.MOD_ID, "y_rotation_models"));

    /**
     * @return true if model contains only Y axis rotations (upright model)
     */
    public static boolean checkModelForYrotation(final BlockState blockState)
    {
        // explicit data-driven marker first — tag entries win over property guessing
        if (blockState.is(Y_ROTATION_MODELS))
        {
            return true;
        }

        // property-based fallback: the 1.21.1 implementation introspected the unbaked
        // model tree through BlockModelShaper.stateToModelLocation + ModelBakery
        // topLevelModels/modelResources (AT) and checked BlockElement rotations. Those
        // model classes no longer exist in that shape in 26.1.2 (models moved to
        // BlockStateModel/dispatch), so this uses the blockstate properties
        // (axis/facing) — the fallback half of the old logic. Blocks that need the
        // exact old behavior go into the y_rotation_models tag above.
        if (blockState.hasProperty(BlockStateProperties.AXIS))
        {
            return blockState.getValue(BlockStateProperties.AXIS) == Axis.Y;
        }

        if (blockState.hasProperty(BlockStateProperties.FACING))
        {
            final Direction facing = blockState.getValue(BlockStateProperties.FACING);
            return facing == Direction.UP || facing == Direction.DOWN;
        }

        return false;
    }
}
