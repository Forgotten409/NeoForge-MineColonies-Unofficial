package com.minecolonies.api.tileentities;

import com.mojang.serialization.Codec;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import static com.minecolonies.api.util.constant.Constants.TAG_STRING;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_CONTENT;
import net.minecraft.core.Direction;

public class AbstractTileEntityNamedGrave extends BlockEntity
{
    /**
     * The position it faces.
     */
    public static final EnumProperty<Direction> FACING       = HorizontalDirectionalBlock.FACING;

    /**
     * The text displayed on the name plate
     */
    private ArrayList<String> textLines = new ArrayList<>();

    public AbstractTileEntityNamedGrave(BlockEntityType<?> tileEntityTypeIn, final BlockPos pos, final BlockState state)
    {
        super(tileEntityTypeIn, pos, state);
        textLines.add("Unknown Citizen");
    }

    public ArrayList<String> getTextLines()
    {
        return textLines;
    }

    public void setTextLines(final ArrayList<String> content)
    {
        this.textLines = content;
        setChanged();
    }

    @Override
    public void loadAdditional(final net.minecraft.world.level.storage.ValueInput input)
    {
        super.loadAdditional(input);

        textLines.clear();
        // PORT26: getList(key, TAG_STRING) -> codec list read (same ListTag wire format)
        for (final String line : input.read(TAG_CONTENT, Codec.STRING.listOf()).orElse(java.util.List.of()))
        {
            textLines.add(line);
        }
    }

    @Override
    public void saveAdditional(final net.minecraft.world.level.storage.ValueOutput output)
    {
        super.saveAdditional(output);

        // PORT26: string list written through the codec — same wire format as the
        // 1.21.1 ListTag-of-StringTag.
        output.store(TAG_CONTENT, Codec.STRING.listOf(), textLines);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
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
    public void handleUpdateTag(final net.minecraft.world.level.storage.ValueInput input) {
        this.loadAdditional(input);
    }

    @Override
    public void setChanged()
    {
        if (level != null)
        {
            WorldUtil.markChunkDirty(level, worldPosition);
        }
    }
}
