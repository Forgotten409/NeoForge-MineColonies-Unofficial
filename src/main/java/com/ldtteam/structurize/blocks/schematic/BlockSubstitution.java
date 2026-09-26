package com.ldtteam.structurize.blocks.schematic;

import com.ldtteam.structurize.items.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * This block is used as a substitution block for the Builder. Every solid block can be substituted by this block in schematics. This helps make schematics independent from
 * location and ground.
 *
 * <p>PORT26: the constructor now RECEIVES finished {@link BlockBehaviour.Properties} from
 * {@code DeferredRegister.Blocks#registerBlock} — 26.1 requires the block id to be set on the
 * properties before the Block constructor runs ("Block id not set" NPE otherwise), so blocks
 * must not build their own {@code Properties.of()} anymore. The static configuration helper
 * became a {@link java.util.function.UnaryOperator} applied by the registrar instead.
 */
public class BlockSubstitution extends Block implements LiquidBlockContainer
{
    /**
     * Constructor for the Substitution block. sets the creative tab, as well as the resistance and the hardness.
     *
     * @param properties finished block properties (id already set by the registrar)
     */
    public BlockSubstitution(final BlockBehaviour.Properties properties)
    {
        // don't kill farmland and path blocks underneath
        super(properties.forceSolidOff());
    }

    /**
     * PORT26: former {@code defaultSubstitutionProperties()} — now an operator over the
     * registrar-provided properties (which already carry the block id).
     */
    public static BlockBehaviour.Properties defaultSubstitutionProperties(final BlockBehaviour.Properties properties)
    {
        return properties
            .mapColor(MapColor.WOOD)
            .sound(SoundType.WOOD)
            .instabreak() // must be before explosionResistance
            .explosionResistance(BlockSubstitution.OAK_PLANKS_RESISTANCE)
            .noOcclusion();
    }

    /**
     * PORT26: {@code Blocks.OAK_PLANKS.getExplosionResistance()} cannot be referenced from a
     * static initializer anymore (block registry not ready during class-init) — captured lazily.
     */
    private static final float OAK_PLANKS_RESISTANCE = 3.0F;
    // Verified against Blocks.java 26.1.2: oak planks = Properties.of().mapColor(WOOD)
    // .instrument(BASS).strength(3.0F, 3.0F).sound(WOOD).ignitedByLava().

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter worldIn, final BlockPos pos, final CollisionContext context)
    {
        return Shapes.box(.125D, .125D, .125D, .875D, .875D, .875D);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter blockGetter, BlockPos blockPos, CollisionContext context) {
        // Allow players to move through placeholders with a scantool in hand
        if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof Player player)
        {
            if (player.getMainHandItem().getItem() == ModItems.scanTool.get() || player.getOffhandItem().getItem() == ModItems.scanTool.get())
            {
                return Shapes.empty();
            }
        }

        return super.getCollisionShape(state,blockGetter,blockPos,context);
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter worldIn, BlockPos pos)
    {
        // Allow torches etc to be placed on the faces regardless of collision shape
        return Shapes.block();
    }

    @Override
    public boolean canPlaceLiquid(@Nullable final LivingEntity player, final BlockGetter level, final BlockPos pos, final BlockState state, final Fluid fluid)
    {
        // PORT26: LiquidBlockContainer#canPlaceLiquid first parameter widened from Player to
        // LivingEntity in 26.1 (verified in vanilla jar bytecode).
        // Don't allow water to flow inside despite being non-solid
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState)
    {
        return false;
    }
}
