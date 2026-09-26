package com.ldtteam.structurize.blockentities;

import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.blocks.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

public final class ModBlockEntities
{
    private ModBlockEntities() { /* prevent construction */ }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Constants.MOD_ID);

    // PORT26: BlockEntityType.Builder was removed in 26.1 — the plain constructor takes the
    // supplier and the set of valid blocks (same pattern as the port's domum-ornamentum and
    // multipiston block entity registrations).
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityTagSubstitution>> TAG_SUBSTITUTION = BLOCK_ENTITIES.register("tagsubstitution",
      () -> new BlockEntityType<>(BlockEntityTagSubstitution::new, Set.of(ModBlocks.blockTagSubstitution.get())));
}
