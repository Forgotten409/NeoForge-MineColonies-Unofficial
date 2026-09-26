package com.ldtteam.multipiston;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

import static com.ldtteam.multipiston.MultiPiston.MOD_ID;

/**
 * Class to create the modBlockEntities.
 * References to the block entities can be made here
 */
public final class ModTileEntities
{
    public static final DeferredRegister<BlockEntityType<?>> TILE_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    private ModTileEntities() { /* prevent construction */ }

    /**
     * PORT26: the 1.21.1 {@code BlockEntityType.Builder.of(...).build(null)} chain was
     * replaced by the plain constructor (the Builder is gone in 26.1) — same pattern as the
     * port's domum-ornamentum block entity registration.
     */
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityMultiPiston>>
      multipiston = TILE_ENTITIES.register("multipistonte", () -> new BlockEntityType<>(TileEntityMultiPiston::new,
        Set.of(ModBlocks.multipiston.get())));
}
