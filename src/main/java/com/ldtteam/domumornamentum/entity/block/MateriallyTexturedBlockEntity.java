package com.ldtteam.domumornamentum.entity.block;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.client.model.properties.ModProperties;
import com.ldtteam.domumornamentum.component.ModDataComponents;
import com.ldtteam.domumornamentum.util.MaterialTextureDataUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import org.jetbrains.annotations.NotNull;


import static com.ldtteam.domumornamentum.entity.block.ModBlockEntityTypes.MATERIALLY_TEXTURED;
import static com.ldtteam.domumornamentum.util.Constants.BLOCK_ENTITY_TEXTURE_DATA;

public class MateriallyTexturedBlockEntity extends AbstractMateriallyTexturedBlockEntity
{

    private MaterialTextureData textureData = MaterialTextureData.EMPTY;

    public MateriallyTexturedBlockEntity(BlockPos pos, BlockState state)
    {
        super(MATERIALLY_TEXTURED.get(), pos, state);
    }

    @Override
    public void updateTextureDataWith(final MaterialTextureData materialTextureData)
    {
        this.textureData = materialTextureData;

        if (this.textureData.isEmpty())
        {
            this.textureData = MaterialTextureDataUtil.generateRandomTextureDataFrom(this.getBlockState().getBlock());
        }
        else if (getBlockState().getBlock() instanceof final IMateriallyTexturedBlock materiallyTexturedBlock)
        {
            textureData = textureData.retainComponentsFromBlock(materiallyTexturedBlock);
        }

        this.requestModelDataUpdate();
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(final HolderLookup.Provider provider)
    {
        // PORT26: saveWithoutMetadata takes ValueOutput now — TagValueOutput bridge.
        final net.minecraft.world.level.storage.TagValueOutput output =
          net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, provider);
        this.saveWithoutMetadata(output);
        return output.buildResult();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void saveAdditional(@NotNull final ValueOutput output)
    {
        super.saveAdditional(output);

        // this is still needed even with data components as of 26.1
        output.store(BLOCK_ENTITY_TEXTURE_DATA, MaterialTextureData.CODEC, textureData);
    }

    @Override
    public void loadAdditional(@NotNull final ValueInput input)
    {
        super.loadAdditional(input);

        // keep this as DFU; ValueInput.read returns Optional directly
        input.read(BLOCK_ENTITY_TEXTURE_DATA, MaterialTextureData.CODEC)
          .ifPresent(this::updateTextureDataWith);
    }

    @Override
    public void requestModelDataUpdate()
    {
        super.requestModelDataUpdate();

        // manually ask level to recompile rendering
        if (level != null && level.isClientSide())
        {
            level.setBlocksDirty(worldPosition, Blocks.AIR.defaultBlockState(), getBlockState());
        }
    }

    // PORT26: onLoad kept without @Override — not present in the partial vanilla decompile,
    // so we cannot verify it still exists; an unannotated override is valid either way.
    public void onLoad()
    {
        // noop (dont call requestModelDataUpdate)
    }

    @NotNull
    @Override
    public ModelData getModelData()
    {
        return ModelData.builder()
          .with(ModProperties.MATERIAL_TEXTURE_PROPERTY, this.textureData)
          .build();
    }

    @Override
    @NotNull
    public MaterialTextureData getTextureData()
    {
        return textureData;
    }

    @Override
    protected void applyImplicitComponents(final DataComponentGetter componentInput)
    {
        super.applyImplicitComponents(componentInput);
        updateTextureDataWith(componentInput.getOrDefault(ModDataComponents.TEXTURE_DATA.get(), MaterialTextureData.EMPTY));
    }

    @Override
    protected void collectImplicitComponents(final DataComponentMap.Builder componentBuilder)
    {
        super.collectImplicitComponents(componentBuilder);
        componentBuilder.set(ModDataComponents.TEXTURE_DATA.get(), textureData);
    }

    @Override
    public void removeComponentsFromTag(final ValueOutput output)
    {
        output.discard(BLOCK_ENTITY_TEXTURE_DATA);
    }
}
