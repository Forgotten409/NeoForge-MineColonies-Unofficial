package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.tileentities.*;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.tileentities.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;
import net.neoforged.neoforge.registries.DeferredRegister;

public class TileEntityInitializer
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Constants.MOD_ID);

    static
    {
        MinecoloniesTileEntities.SCARECROW = BLOCK_ENTITIES.register("scarecrow", () -> new BlockEntityType<>(TileEntityScarecrow::new, ModBlocks.blockScarecrow));

        MinecoloniesTileEntities.PLANTATION_FIELD = BLOCK_ENTITIES.register("plantationfield", () -> new BlockEntityType<>(TileEntityPlantationField::new, ModBlocks.blockPlantationField));

        MinecoloniesTileEntities.BARREL = BLOCK_ENTITIES.register("barrel", () -> new BlockEntityType<>(TileEntityBarrel::new, ModBlocks.blockBarrel));

        MinecoloniesTileEntities.BUILDING = BLOCK_ENTITIES.register("colonybuilding", () -> new BlockEntityType<>(TileEntityColonyBuilding::new, ModBlocks.getHuts()));

        MinecoloniesTileEntities.DECO_CONTROLLER = BLOCK_ENTITIES.register("decorationcontroller", () -> new BlockEntityType<>(TileEntityDecorationController::new, Set.of(ModBlocks.blockDecorationPlaceholder)));

        MinecoloniesTileEntities.RACK = BLOCK_ENTITIES.register("rack", () -> new BlockEntityType<>(TileEntityRack::new, ModBlocks.blockRack));

        MinecoloniesTileEntities.GRAVE = BLOCK_ENTITIES.register("grave", () -> new BlockEntityType<>(TileEntityGrave::new, ModBlocks.blockGrave));

        MinecoloniesTileEntities.NAMED_GRAVE = BLOCK_ENTITIES.register("namedgrave", () -> new BlockEntityType<>(TileEntityNamedGrave::new, ModBlocks.blockNamedGrave));

        MinecoloniesTileEntities.WAREHOUSE = BLOCK_ENTITIES.register("warehouse", () -> new BlockEntityType<>(TileEntityWareHouse::new, ModBlocks.blockHutWareHouse));

        MinecoloniesTileEntities.COMPOSTED_DIRT = BLOCK_ENTITIES.register("composteddirt", () -> new BlockEntityType<>(TileEntityCompostedDirt::new, Set.of(ModBlocks.blockCompostedDirt)));

        MinecoloniesTileEntities.ENCHANTER = BLOCK_ENTITIES.register("enchanter", () -> new BlockEntityType<>(TileEntityEnchanter::new, ModBlocks.blockHutEnchanter));

        MinecoloniesTileEntities.STASH = BLOCK_ENTITIES.register("stash", () -> new BlockEntityType<>(TileEntityStash::new, ModBlocks.blockStash));

        MinecoloniesTileEntities.COLONY_FLAG = BLOCK_ENTITIES.register("colony_flag", () -> new BlockEntityType<>(TileEntityColonyFlag::new, ModBlocks.blockColonyBanner, ModBlocks.blockColonyWallBanner));

        MinecoloniesTileEntities.COLONY_SIGN = BLOCK_ENTITIES.register("colonysign", () -> new BlockEntityType<>(TileEntityColonySign::new, ModBlocks.blockColonySign));
    }
}
