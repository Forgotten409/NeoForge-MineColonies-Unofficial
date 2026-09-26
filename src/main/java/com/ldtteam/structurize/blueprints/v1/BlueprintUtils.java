package com.ldtteam.structurize.blueprints.v1;

import com.ldtteam.structurize.client.BlueprintBlockInfoTransformHandler;
import com.ldtteam.structurize.client.BlueprintEntityInfoTransformHandler;
import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.util.BlockEntityInfo;
import com.ldtteam.structurize.util.BlockInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
// PORT26: ModelData moved from net.neoforged.neoforge.client.model.data to net.neoforged.neoforge.model.data
import net.neoforged.neoforge.model.data.ModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility functions for blueprints.
 */
public final class BlueprintUtils
{
    private BlueprintUtils()
    {
        throw new IllegalArgumentException("Utils class");
    }

    /**
     * Creates a list of tileentities located in the blueprint, placed inside that blueprints block access world.
     *
     * @param blueprint   The blueprint whos tileentities need to be instantiated.
     * @param beLevel The blueprint world.
     * @return A list of tileentities in the blueprint.
     */
    public static Map<BlockPos, BlockEntity> instantiateTileEntities(final Blueprint blueprint, final Level beLevel, final Map<BlockPos, ModelData> teModelData)
    {
        return blueprint.getBlockInfoAsList()
            .stream()
            .map(blockInfo -> BlueprintBlockInfoTransformHandler.getInstance().Transform(blockInfo))
            .filter(BlockInfo::hasTileEntityData)
            .map(blockInfo -> {
                @Nullable
                final BlockEntity be = constructTileEntity(blockInfo, beLevel, blueprint.getRegistryAccess());
                if (be != null)
                {
                    teModelData.put(blockInfo.getPos(), be.getModelData());
                    return new BlockEntityInfo(blockInfo.getPos(), be);
                }
                else
                {
                    // PORT26 FIX (log noise): a null here is almost always the benign
                    // incompatible-BE skip pre-validated inside constructTileEntity (legacy
                    // blueprint data, e.g. byzantine doublebuilder1's stale domum BE on a
                    // chest) — that path already debug-logs the skip, and real failures
                    // (exceptions / post-validation) log their own errors inside. This used
                    // to be an ERROR per skip per preview init, which read as "something is
                    // broken" while rendering continued perfectly without that BE.
                    Log.getLogger().debug("TileEntity not instantiated for: " + blueprint + " " + blockInfo.getPos());
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(BlockEntityInfo::pos, BlockEntityInfo::blockEntity));
    }

    /**
     * Creates a list of entities located in the blueprint, placed inside that blueprints block access world.
     *
     * @param blueprint   The blueprint whos entities need to be instantiated.
     * @param entityLevel The blueprints world.
     * @return A list of entities in the blueprint
     */
    public static List<Entity> instantiateEntities(final Blueprint blueprint, final Level entityLevel)
    {
        return blueprint.getEntitiesAsList()
            .stream()
            .map(entityInfo -> BlueprintEntityInfoTransformHandler.getInstance().Transform(entityInfo))
            .map(entityInfo -> constructEntity(entityInfo, entityLevel))
            .filter(Objects::nonNull)
            .toList();
    }

    @Nullable
    public static BlockEntity constructTileEntity(final BlockInfo info, final Level beLevel, final HolderLookup.Provider provider)
    {
        if (info == null || info.getTileEntityData() == null) return null;

        // PORT26: getString(key) -> getStringOr(key, "") (getString now returns Optional)
        final String entityId = info.getTileEntityData().getStringOr("id", "");

        // PORT26 FIX (blueprint BE/block mismatch error spam): 26.1.2's BlockEntity
        // constructor validates the block state against the BE type's valid blocks and
        // vanilla error-logs (with stacktrace) on mismatch. Shipped blueprints legitimately
        // contain such legacy mismatches — e.g. townhall2 has exactly two vanilla
        // minecraft:barrel positions that kept a leftover domum_ornamentum
        // materially_retexturable BE from 1.21.x-era scans (723 other DO BEs sit on real
        // DO blocks and construct fine). Pre-validate and skip those quietly instead of
        // spamming errors on every preview render.
        final BlockState blockState = info.getState();
        if (!entityId.isEmpty())
        {
            @Nullable
            final Identifier beTypeId = Identifier.tryParse(entityId);
            @Nullable
            final BlockEntityType<?> beType = beTypeId == null ? null : BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(beTypeId);
            if (beType == null || !beType.isValid(blockState))
            {
                Log.getLogger().debug("Skipping block entity '{}' incompatible with block state {} at {}", entityId, blockState, info.getPos());
                return null;
            }
        }

        try
        {
            final CompoundTag compound = info.getTileEntityData().copy();
            compound.putInt("x", info.getPos().getX());
            compound.putInt("y", info.getPos().getY());
            compound.putInt("z", info.getPos().getZ());

            final BlockEntity entity = BlockEntity.loadStatic(info.getPos(), Objects.requireNonNull(blockState), compound, provider);

            if (entity != null)
            {
                if (!entity.getType().isValid(blockState))
                {
                    Log.getLogger().error("TileEntity " + entityId + " does not accept blockState: " + blockState);
                    return null;
                }

                if (beLevel != null)
                {
                    entity.setLevel(beLevel);
                }
            }
            return entity;
        }
        catch (final Exception ex)
        {
            Log.getLogger().error("Could not create tile entity: " + entityId + " with nbt: " + info.toString(), ex);
            return null;
        }
    }

    @Nullable
    private static Entity constructEntity(@Nullable final CompoundTag info, final Level entityLevel)
    {
        if (info == null) return null;

        // PORT26: getString(key) -> getStringOr(key, "") (getString now returns Optional)
        final String entityId = info.getStringOr("id", "");

        try
        {
            final CompoundTag compound = info.copy();
            // PORT26: CompoundTag#putUUID was removed in 26.1; strip any saved UUID instead —
            // the Entity constructor assigns a fresh random UUID (Mth.createInsecureUUID) and
            // Entity#load only overrides it when the "UUID" key is present, so this has the
            // same effect as 1.21.1's putUUID(randomUUID()).
            compound.remove("UUID");
            // PORT26: EntityType.by(CompoundTag) + EntityType#create(Level) + Entity#load(CompoundTag)
            // were replaced by the one-shot EntityType#create(ValueInput, Level, EntitySpawnReason)
            final Optional<Entity> entity = EntityType.create(
                TagValueInput.create(ProblemReporter.DISCARDING, entityLevel.registryAccess(), compound),
                entityLevel,
                EntitySpawnReason.LOAD);
            if (entity.isPresent())
            {
                final Entity finalEntity = entity.get();

                // prevent ticking rotations
                finalEntity.setOldPosAndRot();
                if (finalEntity instanceof LivingEntity lentity)
                {
                    lentity.yHeadRotO = lentity.yHeadRot;
                    lentity.yBodyRotO = lentity.yBodyRot;
                }

                return finalEntity;
            }
            return null;
        }
        catch (final Exception ex)
        {
            Log.getLogger().error("Could not create entity: " + entityId + " with nbt: " + info.toString(), ex);
            return null;
        }
    }
}
