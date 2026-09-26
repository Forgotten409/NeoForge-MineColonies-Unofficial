package com.minecolonies.core.tileentities;

import com.ldtteam.structurize.api.RotationMirror;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.plantation.IPlantationModule;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries.BuildingExtensionEntry;
import com.minecolonies.api.tileentities.AbstractTileEntityPlantationField;
import com.minecolonies.api.tileentities.MinecoloniesTileEntities;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.minecolonies.api.util.constant.NbtTagConstants.*;

/**
 * Implementation for plantation field tile entities.
 */
public class TileEntityPlantationField extends AbstractTileEntityPlantationField
{
    /**
     * Cached result for {@link TileEntityPlantationField#getWorkingPositions(String)} ()}.
     */
    private final Map<String, List<BlockPos>> workingPositions = new HashMap<>();

    /**
     * The schematic name of the placeholder block.
     */
    private String schematicName = "";

    /**
     * The schematic path of the placeholder block.
     */
    private String schematicPath = "";

    /**
     * The packName it is included in.
     */
    private String packName = "";

    /**
     * Corner positions of schematic, relative to te pos.
     */
    private BlockPos corner1 = BlockPos.ZERO;
    private BlockPos corner2 = BlockPos.ZERO;

    /**
     * The used rotation and mirror.
     */
    private RotationMirror rotationMirror = RotationMirror.NONE;

    /**
     * Map of block positions relative to TE pos and string tags
     */
    private Map<BlockPos, List<String>> tagPosMap = new HashMap<>();

    /**
     * The colony this plantation field is located in.
     */
    private IColony currentColony;

    /**
     * Cached result for {@link TileEntityPlantationField#getPlantationFieldTypes()}.
     */
    private Set<BuildingExtensionEntry> plantationFieldTypes;

    /**
     * Default constructor.
     *
     * @param pos   The positions this tile entity is at.
     * @param state The state the entity is in.
     */
    public TileEntityPlantationField(final BlockPos pos, final BlockState state)
    {
        super(MinecoloniesTileEntities.PLANTATION_FIELD.get(), pos, state);
    }

    @Override
    public Set<BuildingExtensionEntry> getPlantationFieldTypes()
    {
        if (plantationFieldTypes == null)
        {
            plantationFieldTypes = tagPosMap.values().stream()
                                     .flatMap(Collection::stream)
                                     .map(this::getPlantationFieldEntryFromFieldTag)
                                     .filter(Objects::nonNull)
                                     .collect(Collectors.toSet());
        }
        return plantationFieldTypes;
    }

    @Override
    public List<BlockPos> getWorkingPositions(final String tag)
    {
        workingPositions.computeIfAbsent(tag, newTag -> tagPosMap.entrySet().stream()
                                                          .filter(f -> f.getValue().contains(newTag))
                                                          .distinct()
                                                          .map(Map.Entry::getKey)
                                                          .map(worldPosition::offset)
                                                          .toList());
        return workingPositions.get(tag);
    }

    @Override
    public IColony getCurrentColony()
    {
        if (currentColony == null && level != null)
        {
            this.currentColony = IColonyManager.getInstance().getIColony(level, worldPosition);
        }
        return currentColony;
    }

    @Override
    @Nullable
    public ResourceKey<Level> getDimension()
    {
        IColony colony = getCurrentColony();
        if (colony != null)
        {
            return colony.getDimension();
        }
        return null;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Get the rotation of the controller.
     *
     * @return the placed rotation.
     */
    @Override
    public RotationMirror getRotationMirror()
    {
        return rotationMirror;
    }

    private BuildingExtensionEntry getPlantationFieldEntryFromFieldTag(String fieldTag)
    {
        return BuildingExtensionRegistries.getBuildingExtensionRegistry().stream()
                 .filter(fieldEntry -> {
                     List<IPlantationModule> modules = fieldEntry.getExtensionModuleProducers().stream().map(m -> m.apply(null))
                                                         .filter(IPlantationModule.class::isInstance)
                                                         .map(m -> (IPlantationModule) m)
                                                         .toList();

                     return modules.stream().anyMatch(module -> module.getFieldTag().equals(fieldTag));
                 })
                 .findFirst()
                 .orElse(null);
    }

    @Override
    public String getSchematicName()
    {
        return schematicName;
    }

    @Override
    public void setSchematicName(final String s)
    {
        this.schematicName = s;
        setChanged();
    }

    @Override
    public Map<BlockPos, List<String>> getPositionedTags()
    {
        return tagPosMap;
    }

    @Override
    public void setPositionedTags(final Map<BlockPos, List<String>> positionedTags)
    {
        tagPosMap = positionedTags;
        setChanged();
    }

    @Override
    public Tuple<BlockPos, BlockPos> getSchematicCorners()
    {
        if (corner1 == BlockPos.ZERO || corner2 == BlockPos.ZERO)
        {
            return new Tuple<>(worldPosition, worldPosition);
        }

        return new Tuple<>(corner1, corner2);
    }

    @Override
    public void setSchematicCorners(final BlockPos pos1, final BlockPos pos2)
    {
        corner1 = pos1;
        corner2 = pos2;
        setChanged();
    }

    @Override
    public void readSchematicDataFromNBT(final CompoundTag compound)
    {
        super.readSchematicDataFromNBT(compound);
        final CompoundTag blueprintDataProvider = compound.getCompoundOrEmpty(TAG_BLUEPRINTDATA);

        this.packName = blueprintDataProvider.getStringOr(TAG_PACK, "");
        this.schematicPath = blueprintDataProvider.getStringOr(TAG_PATH, "");
    }

    @Override
    public BlockPos getTilePos()
    {
        return worldPosition;
    }

    @Override
    public void rotateAndMirror(final RotationMirror rotMir)
    {
        this.rotationMirror = rotMir;
    }

    @Override
    public void onDataPacket(final Connection net, final net.minecraft.world.level.storage.ValueInput input) {
        this.loadAdditional(input);
    }

    @Override
    public void loadAdditional(final net.minecraft.world.level.storage.ValueInput input)
    {
        super.loadAdditional(input);
        // PORT26: raw-tag bridge — read the whole input back as a CompoundTag for the
        // (port-own) schematic-data API (same pattern as BlockEntityTagSubstitution).
        final CompoundTag compound = input.read(com.mojang.serialization.MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseGet(CompoundTag::new);
        super.readSchematicDataFromNBT(compound);
        if (input.keySet().contains(TAG_ROTATION_MIRROR))
        {
            this.rotationMirror = RotationMirror.values()[input.getByteOr(TAG_ROTATION_MIRROR, (byte) 0)];
        }
        else
        {
            // TODO: remove this later (data break introduced in 1.20.4) because of blueprint data
            this.rotationMirror = RotationMirror.of(Rotation.values()[input.getIntOr(TAG_ROTATION, 0)], input.getBooleanOr(TAG_MIRROR, false) ? Mirror.FRONT_BACK : Mirror.NONE);
        }
        if (input.keySet().contains(TAG_PATH))
        {
            this.schematicPath = input.getStringOr(TAG_PATH, "");
        }

        if (input.keySet().contains(TAG_NAME))
        {
            this.schematicName = input.getStringOr(TAG_NAME, "");
            if (this.schematicPath == null || this.schematicPath.isEmpty())
            {
                //Setup for recovery
                this.schematicPath = this.schematicName;
                this.schematicName = "";
            }
        }
        this.packName = input.getStringOr(TAG_PACK, "");

        if (!this.schematicPath.endsWith(".blueprint"))
        {
            this.schematicPath = this.schematicPath + ".blueprint";
        }
    }

    @Override
    public void saveAdditional(final net.minecraft.world.level.storage.ValueOutput output)
    {
        super.saveAdditional(output);
        // PORT26: raw-tag bridge — write schematic data into a fresh CompoundTag, then
        // merge it into the root level of the output (NeoForge ValueOutputExtension#store).
        final CompoundTag compound = new CompoundTag();
        writeSchematicDataToNBT(compound);
        output.store(compound);
        output.putByte(TAG_ROTATION_MIRROR, (byte) this.rotationMirror.ordinal());
        output.putString(TAG_NAME, schematicName == null ? "" : schematicName);
        output.putString(TAG_PATH, schematicPath == null ? "" : schematicPath);
        output.putString(TAG_PACK, (packName == null || packName.isEmpty()) ? "" : packName);
    }

    @Override
    public void setChanged()
    {
        if (level != null)
        {
            WorldUtil.markChunkDirty(level, worldPosition);
        }
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag(@NotNull final HolderLookup.Provider provider)
    {
        return this.saveWithoutMetadata(provider);
    }

    @Override
    public void setBlueprintPath(final String filePath)
    {
        this.schematicPath = filePath;
        if (!this.schematicPath.endsWith(".blueprint"))
        {
            this.schematicPath = this.schematicPath + ".blueprint";
        }
        setChanged();
    }

    @Override
    public void setPackName(final String packName)
    {
        this.packName = packName;
        setChanged();
    }

    @Override
    public String getBlueprintPath()
    {
        return schematicPath;
    }

    /**
     * Getter for the pack.
     *
     * @return String name.
     */
    public String getPackName()
    {
        return packName;
    }
}
