package com.ldtteam.multipiston;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This Class is about the multipiston which takes care of pushing others around (In a non mean way).
 *
 * <p>PORT26 API adaptations:
 * <ul>
 *   <li>{@code ItemInteractionResult} was merged back into {@link InteractionResult} in
 *       1.21.2+ — {@code useItemOn} returns the plain result now;</li>
 *   <li>{@code neighborChanged} lost its {@code fromPos} parameter — vanilla replaced it
 *       with the redstone {@link Orientation} (ignored here, as the 1.21.1 code ignored
 *       fromPos too);</li>
 *   <li>{@code Level#isClientSide} is a private field in 26.1 — the getter
 *       {@code isClientSide()} is used instead;</li>
 *   <li>the constructor now RECEIVES finished {@link BlockBehaviour.Properties} from
 *       {@code DeferredRegister.Blocks#registerBlock} — 26.1 requires the block id to be set
 *       on the properties before the Block constructor runs ("Block id not set" NPE in
 *       {@code Properties.effectiveDrops} otherwise), so blocks must not build their own
 *       {@code Properties.of()} anymore.</li>
 * </ul>
 */
public class MultiPistonBlock extends BaseEntityBlock
{
    /**
     * Constructor for the Substitution block.
     * sets the creative tab, as well as the resistance and the hardness.
     *
     * @param properties finished block properties (id already set by the registrar)
     */
    public MultiPistonBlock(final BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    /**
     * The blocks shape.
     */
    private static final VoxelShape SHAPE = Block.box(0.01D, 0.01D, 0.01D, 15.99D, 15.99D, 15.99D);

    @Override
    public InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult)
    {
        if (level.isClientSide())
        {
            new WindowMultiPiston(pos).open();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useItemOn(
      final ItemStack stack,
      final BlockState state,
      final Level level,
      final BlockPos pos,
      final Player player,
      final InteractionHand hand,
      final BlockHitResult hitResult)
    {
        if (level.isClientSide())
        {
            new WindowMultiPiston(pos).open();
        }
        return InteractionResult.SUCCESS;
    }

    @NotNull
    @Override
    public VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext ctx)
    {
        return Shapes.block();
    }

    @Override
    public void neighborChanged(@NotNull final BlockState state, @NotNull final Level level, @NotNull final BlockPos pos, @NotNull final Block block, @Nullable final Orientation orientation, final boolean isMoving)
    {
        if (level.isClientSide())
        {
            return;
        }
        final BlockEntity te = level.getBlockEntity(pos);
        if(te instanceof TileEntityMultiPiston)
        {
            ((TileEntityMultiPiston) te).handleRedstone(level.hasNeighborSignal(pos));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull final BlockPos blockPos, @NotNull final BlockState blockState)
    {
        return new TileEntityMultiPiston(blockPos, blockState);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull final Level level, @NotNull final BlockState state, @NotNull final BlockEntityType<T> type)
    {
        return createTickerHelper(type, ModTileEntities.multipiston.get(), (l, pos, s, te) -> te.tick());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
    {
        // PORT26: kept null exactly like the 1.21.1 source — the block never serializes
        // through its codec (no datapack features reference it). Upstream parity.
        return null;
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state)
    {
        return RenderShape.MODEL;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull final BlockState state, @NotNull final BlockGetter getter, @NotNull final BlockPos pos, @NotNull final CollisionContext context)
    {
        return SHAPE;
    }
}
