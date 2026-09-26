package com.ldtteam.domumornamentum.entity.block;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.decorative.DynamicTimberFrameBlock;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.Set;

/**
 * Class to create the modBlocks.
 * References to the blocks can be made here
 */
public final class ModBlockEntityTypes
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Constants.MOD_ID);

    // PORT26: BlockEntityType.Builder is gone and there is no static factory — NeoForge 26.1.2
    // re-publicizes the constructor, adding the convenience overload
    // `BlockEntityType(BlockEntitySupplier, Block... validBlocks)` (verified against the
    // NeoForge 26.1.2 patch set + the working lso-port). The nested BlockEntitySupplier
    // type is private in vanilla, so we use plain method references without naming it.
    // The vararg overload rejects an empty array ("Block entity type instantiated without
    // valid blocks ... pass Set.of() instead of an empty varag"), so we explicitly go through
    // the Set overload, which documents an empty set as intentional.
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntity>> MATERIALLY_TEXTURED = BLOCK_ENTITIES.register(Constants.BlockEntityTypes.MATERIALLY_RETEXTURABLE.getPath(),
      () -> new BlockEntityType<BlockEntity>(MateriallyTexturedBlockEntity::new,
        Set.copyOf(Arrays.asList(BuiltInRegistries.BLOCK.stream().filter(IMateriallyTexturedBlock.class::isInstance).toArray(Block[]::new)))
      )
    );

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntity>> DYNAMIC_TIMBERFRAME = BLOCK_ENTITIES.register(Constants.BlockEntityTypes.DYNAMIC_TIMBERFRAME.getPath(),
        () -> new BlockEntityType<BlockEntity>(DynamicTimberFrameBlockEntity::new,
            Set.copyOf(Arrays.asList(BuiltInRegistries.BLOCK.stream().filter(DynamicTimberFrameBlock.class::isInstance).toArray(Block[]::new)))
        )
    );

    /**
     * Private constructor to hide the implicit public one.
     */
    private ModBlockEntityTypes()
    {
    }
}
