package com.ldtteam.domumornamentum.block.decorative;

import com.ldtteam.domumornamentum.block.AbstractBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class FloatingCarpetBlock extends AbstractBlock<FloatingCarpetBlock>
{
    /**
     * This Blocks shape.
     */
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    /**
     * This Blocks color.
     */
    private final DyeColor color;

    // PORT26: properties come from the registrar (id is set on them before construction)
    public FloatingCarpetBlock(final DyeColor color, final Properties properties)
    {
        super(properties);
        this.color = color;
    }

    public DyeColor getColor()
    {
        return color;
    }

    @Override
    public @NotNull VoxelShape getShape(
      final @NotNull BlockState state, final @NotNull BlockGetter blockGetter, final @NotNull BlockPos pos, final @NotNull CollisionContext context)
    {
        return SHAPE;
    }
}
