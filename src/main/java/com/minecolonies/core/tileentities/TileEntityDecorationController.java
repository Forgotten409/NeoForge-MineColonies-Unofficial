package com.minecolonies.core.tileentities;

import com.ldtteam.structurize.api.IRotatableBlockEntity;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.tileentities.MinecoloniesTileEntities;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.Tuple;
import net.minecraft.core.BlockPos;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.minecolonies.api.util.constant.NbtTagConstants.*;

public class TileEntityDecorationController extends BlockEntity implements IBlueprintDataProviderBE, IRotatableBlockEntity
{
    /**
     * The schematic name of the placerholder block.
     */
    private String schematicName = "";

    /**
     * The schematic path of the placerholder block.
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

    public TileEntityDecorationController(final BlockPos pos, final BlockState state)
    {
        super(MinecoloniesTileEntities.DECO_CONTROLLER.get(), pos, state);
    }

    @Override
    public String getPackName()
    {
        return packName;
    }

    @Override
    public String getBlueprintPath()
    {
        return schematicPath;
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

    /**
     * Trigger update action.
     */
    private void update()
    {
        this.setChanged();
    }

    @Override
    public void setChanged()
    {
        if (level != null)
        {
            WorldUtil.markChunkDirty(level, worldPosition);
        }
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
    public void readSchematicDataFromNBT(CompoundTag compound)
    {
        IBlueprintDataProviderBE.super.readSchematicDataFromNBT(compound);
        final CompoundTag blueprintDataProvider = compound.getCompoundOrEmpty(TAG_BLUEPRINTDATA);

        this.packName = blueprintDataProvider.getStringOr(TAG_PACK, "");
        this.schematicPath = blueprintDataProvider.getStringOr(TAG_PATH, "");
    }

    @Override
    public void loadAdditional(final net.minecraft.world.level.storage.ValueInput input)
    {
        super.loadAdditional(input);
        // PORT26: raw-tag bridge for the (port-own) schematic-data API — read the whole
        // input back as a CompoundTag (same pattern as BlockEntityTagSubstitution).
        final CompoundTag compound = input.read(com.mojang.serialization.MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseGet(CompoundTag::new);
        IBlueprintDataProviderBE.super.readSchematicDataFromNBT(compound);
        if (input.keySet().contains(TAG_ROTATION_MIRROR))
        {
            this.rotationMirror = RotationMirror.values()[input.getByteOr(TAG_ROTATION_MIRROR, (byte) 0)];
        }
        else
        {
            // TODO: remove this later (data break introduced in 1.20.4) because of blueprint data
            this.rotationMirror = RotationMirror.of(Rotation.values()[input.getIntOr(TAG_ROTATION, 0)], input.getBooleanOr(TAG_MIRROR, false) ? Mirror.FRONT_BACK : Mirror.NONE);
        }

        // inexplicably IBlueprintDataProviderBE does not load the pack/path even though it saved them
        this.packName = input.read(TAG_BLUEPRINTDATA, net.minecraft.nbt.CompoundTag.CODEC).orElseGet(net.minecraft.nbt.CompoundTag::new).getStringOr(TAG_PACK, "");
        this.schematicPath = input.read(TAG_BLUEPRINTDATA, net.minecraft.nbt.CompoundTag.CODEC).orElseGet(net.minecraft.nbt.CompoundTag::new).getStringOr(TAG_PATH, "");

        // the rest of this is backwards compat code that can be removed at some point (maybe even now)
        if(input.keySet().contains(TAG_PATH) && StringUtils.isEmpty(this.schematicName))
        {
            this.schematicPath = input.getStringOr(TAG_PATH, "");
        }
        if(input.keySet().contains(TAG_PACK) && StringUtils.isEmpty(this.packName))
        {
            this.packName = input.getStringOr(TAG_PACK, "");
        }
        if(input.keySet().contains(TAG_NAME) && StringUtils.isEmpty(this.schematicName))
        {
            this.schematicName = input.getStringOr(TAG_NAME, "");
            if (this.schematicPath == null || this.schematicPath.isEmpty())
            {
                //Setup for recovery
                this.schematicPath = this.schematicName;
                this.schematicName = "";
            }
        }
        // end of backwards compat code

        if (!this.schematicPath.endsWith(".blueprint"))
        {
            this.schematicPath = this.schematicPath + ".blueprint";
        }
    }

    @Override
    public void saveAdditional(final net.minecraft.world.level.storage.ValueOutput output)
    {
        super.saveAdditional(output);
        // PORT26: raw-tag bridge — schematic data into a fresh CompoundTag, then merge
        // into the output root (NeoForge ValueOutputExtension#store).
        final CompoundTag compound = new CompoundTag();
        writeSchematicDataToNBT(compound);
        output.store(compound);
        output.putByte(TAG_ROTATION_MIRROR, (byte) this.rotationMirror.ordinal());
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
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

    @NotNull
    @Override
    public CompoundTag getUpdateTag(@NotNull final HolderLookup.Provider provider)
    {
        return this.saveWithoutMetadata(provider);
    }

    @Override
    public void onDataPacket(final Connection net, final net.minecraft.world.level.storage.ValueInput input) {
        this.loadAdditional(input);
    }

    @Override
    public BlockPos getTilePos()
    {
        return worldPosition;
    }

    @Override
    public void rotateAndMirror(final RotationMirror rotationMirror)
    {
        this.rotationMirror = rotationMirror;
    }

    /**
     * Get the rotation of the controller.
     * @return the placed rotation and mirror.
     */
    public RotationMirror getRotationMirror()
    {
        return rotationMirror;
    }
}
