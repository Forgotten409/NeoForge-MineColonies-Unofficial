package com.ldtteam.structurize.blocks.schematic;

import com.ldtteam.structurize.blockentities.BlockEntityTagSubstitution;
import com.ldtteam.structurize.blocks.interfaces.IAnchorBlock;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import javax.annotation.Nullable;

/**
 * This block is a substitution block (it disappears on normal build) but stores blueprint data (mostly tags) during scan.
 *
 * <p>PORT26:
 * <ul>
 *   <li>constructor takes the registrar-provided properties (block id must be set before the
 *       Block constructor runs);</li>
 *   <li>{@code getCloneItemStack(LevelReader, BlockPos, BlockState)} became
 *       {@code (LevelReader, BlockPos, BlockState, boolean includeData)}; the old NeoForge
 *       5-arg variant {@code (BlockState, HitResult, LevelReader, BlockPos, Player)} became
 *       {@code (LevelReader, BlockPos, BlockState, boolean, Player)};</li>
 *   <li>{@code BlockEntity#saveToItem(stack, registryAccess)} was removed — the canonical 26.1
 *       flow (vanilla pick-block, ServerGamePacketListenerImpl#addBlockDataToItem) writes the
 *       custom tag + applies components.</li>
 * </ul>
 */
public class BlockTagSubstitution extends BlockSubstitution implements IAnchorBlock, EntityBlock
{
    // PORT26: ProblemReporter.ScopedCollector requires an slf4j logger (vanilla passes
    // com.mojang.logging.LogUtils.getLogger() results), not log4j.
    private static final Logger LOGGER = LogUtils.getLogger();

    public BlockTagSubstitution(final BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(final @NotNull BlockPos blockPos, final @NotNull BlockState blockState)
    {
        return new BlockEntityTagSubstitution(blockPos, blockState);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public ItemStack getCloneItemStack(@NotNull final LevelReader level,
        @NotNull final BlockPos pos,
        @NotNull final BlockState blockState,
        final boolean includeData)
    {
        return cloneItemStack(super.getCloneItemStack(level, pos, blockState, includeData), level, pos);
    }

    @Override
    public ItemStack getCloneItemStack(final LevelReader level, final BlockPos pos, final BlockState state, final boolean includeData, final Player player)
    {
        return cloneItemStack(super.getCloneItemStack(level, pos, state, includeData, player), level, pos);
    }

    private ItemStack cloneItemStack(final ItemStack stack, final LevelReader level, final BlockPos pos)
    {
        if (level.getBlockEntity(pos) instanceof final BlockEntityTagSubstitution entity)
        {
            // PORT26: BlockEntity#saveToItem removed — replicate vanilla pick-block flow
            // (ServerGamePacketListenerImpl#addBlockDataToItem): save the custom-only tag,
            // strip item-only components from it, attach it as BLOCK_ENTITY_DATA and apply
            // the BE's implicit components (CAPTURED_BLOCK here).
            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER))
            {
                final TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
                entity.saveCustomOnly(output);
                entity.removeComponentsFromTag(output);
                BlockItem.setBlockEntityData(stack, entity.getType(), output);
                stack.applyComponents(entity.collectComponents());
            }
        }
        return stack;
    }
}
