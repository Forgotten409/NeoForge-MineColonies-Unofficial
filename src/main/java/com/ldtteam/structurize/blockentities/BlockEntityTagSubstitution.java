package com.ldtteam.structurize.blockentities;

import com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE;
import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.component.CapturedBlock;
import com.ldtteam.structurize.component.ModDataComponents;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * The block entity for BlockTagSubstitution
 *
 * <p>PORT26:
 * <ul>
 *   <li>{@code loadAdditional(CompoundTag, Provider)} / {@code saveAdditional(CompoundTag, Provider)}
 *       became {@code (ValueInput)} / {@code (ValueOutput)} — the raw-tag logic (schematic data,
 *       captured-block replacement) is bridged through the NeoForge
 *       {@code ValueInput.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC))} /
 *       {@code ValueOutput.store(CompoundTag)} extensions, the documented escape hatch for
 *       tag-heavy serializers; NBT KEYS are unchanged so 1.21.1 saves load fine;</li>
 *   <li>{@code applyImplicitComponents(BlockEntity.DataComponentInput)} — DataComponentInput is
 *       gone, the parameter is the plain {@link DataComponentGetter} now;</li>
 *   <li>{@code removeComponentsFromTag(CompoundTag)} became {@code (ValueOutput)};</li>
 *   <li>{@code getUpdateTag} uses {@code saveWithoutMetadata} (1.21.1 already did via
 *       saveCustomOnly — 26.1 renamed the equivalent).</li>
 * </ul>
 */
public class BlockEntityTagSubstitution extends BlockEntity implements IBlueprintDataProviderBE
{
    public static final String CAPTURED_BLOCK_TAG = "captured_block";
    /**
     * Up to 1.21.1
     */
    public static final String CAPTURED_BLOCK_TAG_OLD = "replacement";

    /**
     * The schematic name of the block.
     */
    private String schematicName = "";

    /**
     * Corner positions of schematic, relative to te pos.
     */
    private BlockPos corner1 = BlockPos.ZERO;
    private BlockPos corner2 = BlockPos.ZERO;

    /**
     * Map of block positions relative to TE pos and string tags
     */
    private Map<BlockPos, List<String>> tagPosMap = new HashMap<>();

    /**
     * Structure pack name.
     */
    private String packName;

    /**
     * Structure pack path.
     */
    private String inPackPath;

    /**
     * Replacement block.
     */
    private CapturedBlock replacement = CapturedBlock.EMPTY;

    public BlockEntityTagSubstitution(final BlockPos pos, final BlockState state)
    {
        super( ModBlockEntities.TAG_SUBSTITUTION.get(), pos, state);
    }

    @Override
    public String getSchematicName()
    {
        return schematicName;
    }

    @Override
    public void setSchematicName(final String name)
    {
        schematicName = name;
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
    }

    @Override
    public BlockPos getTilePos()
    {
        return worldPosition;
    }

    /**
     * @return the replacement block details
     */
    @NotNull
    public CapturedBlock getReplacement()
    {
        return this.replacement;
    }

    @Override
    public void loadAdditional(@NotNull final ValueInput input)
    {
        super.loadAdditional(input);
        // PORT26: raw-tag bridge — the NeoForge extension reads the whole input back as a
        // CompoundTag (same keys as 1.21.1).
        final CompoundTag compound = input.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseGet(CompoundTag::new);
        final DynamicOps<Tag> dynamicOps = input.lookup().createSerializationContext(NbtOps.INSTANCE);

        IBlueprintDataProviderBE.super.readSchematicDataFromNBT(compound);
        // PORT26: contains(String, int) was removed — type check via the returned Tag instead
        if (compound.get(CAPTURED_BLOCK_TAG_OLD) instanceof final CompoundTag oldNbt)
        {
            replacement = new CapturedBlock(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, oldNbt.getCompoundOrEmpty("b")),
              Optional.of(oldNbt.getCompoundOrEmpty("e")),
              oldNbt.contains("i") ? parseOptionalStack(input.lookup(), oldNbt.getCompoundOrEmpty("i")) : ItemStack.EMPTY);
        }
        else
        {
            replacement = deserializeReplacement(compound, dynamicOps);
        }
    }

    /**
     * PORT26: {@code ItemStack.parseOptional(provider, tag)} was removed — the OPTIONAL_CODEC
     * parse is its exact replacement (empty tag -> EMPTY stack).
     */
    private static ItemStack parseOptionalStack(final net.minecraft.core.HolderLookup.Provider provider, final CompoundTag tag)
    {
        return ItemStack.OPTIONAL_CODEC
          .parse(provider.createSerializationContext(NbtOps.INSTANCE), tag)
          .result()
          .orElse(ItemStack.EMPTY);
    }

    public static CapturedBlock deserializeReplacement(final CompoundTag compound, final DynamicOps<Tag> dynamicOps)
    {
        if (compound.getCompoundOrEmpty(CAPTURED_BLOCK_TAG).isEmpty())
        {
            return CapturedBlock.EMPTY;
        }
        return CapturedBlock.CODEC.parse(dynamicOps, compound.get(CAPTURED_BLOCK_TAG)).resultOrPartial(error -> {
            Log.getLogger()
                .error("Parsing {} with data {}: {}", ModBlockEntities.TAG_SUBSTITUTION.getId(), compound, error);
            Log.getLogger().error("", new RuntimeException());
        }).orElse(CapturedBlock.EMPTY);
    }

    @Override
    public void saveAdditional(@NotNull final ValueOutput output)
    {
        super.saveAdditional(output);
        // PORT26: raw-tag bridge — serialize the schematic data into a fresh CompoundTag,
        // then store all its entries at the root level through the NeoForge
        // ValueOutputExtension.store(CompoundTag). ValueOutput has no lookup(), so the
        // captured-block component is stored through the codec-based store(String, Codec, T)
        // which the output serializes with its own ops (identical result to the 1.21.1
        // encodeStart-with-provider-context).
        final CompoundTag compound = new CompoundTag();
        writeSchematicDataToNBT(compound);
        output.store(compound);

        // this is still needed even with data components as of 1.21
        output.store(CAPTURED_BLOCK_TAG, CapturedBlock.CODEC, replacement);
    }

    public static void serializeReplacement(final CompoundTag compound, final DynamicOps<Tag> dynamicOps, final CapturedBlock replacement)
    {
        compound.put(CAPTURED_BLOCK_TAG, CapturedBlock.CODEC.encodeStart(dynamicOps, replacement).getOrThrow());
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setPackName(final String packName)
    {
        this.packName = packName;
    }

    @Override
    public void setBlueprintPath(final String inPackPath)
    {
        this.inPackPath = inPackPath;
    }

    @Override
    public String getPackName()
    {
        return packName;
    }

    @Override
    public String getBlueprintPath()
    {
        return inPackPath;
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag(final net.minecraft.core.HolderLookup.Provider provider)
    {
        // PORT26: saveWithoutMetadata takes ValueOutput now — TagValueOutput bridge.
        final net.minecraft.world.level.storage.TagValueOutput output =
          net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, provider);
        this.saveWithoutMetadata(output);
        return output.buildResult();
    }

    @Override
    protected void applyImplicitComponents(final DataComponentGetter componentInput)
    {
        super.applyImplicitComponents(componentInput);
        replacement = componentInput.getOrDefault(ModDataComponents.CAPTURED_BLOCK.get(), CapturedBlock.EMPTY);
    }

    @Override
    protected void collectImplicitComponents(final DataComponentMap.Builder componentBuilder)
    {
        super.collectImplicitComponents(componentBuilder);
        componentBuilder.set(ModDataComponents.CAPTURED_BLOCK.get(), replacement);
    }

    @Override
    public void removeComponentsFromTag(final ValueOutput output)
    {
        // PORT26: the 1.21.1 override removed the raw tag key so the saved BLOCK_ENTITY_DATA
        // component does not duplicate the data that now lives in CAPTURED_BLOCK. The 26.1
        // ValueOutput variant discards through the same key.
        output.discard(CAPTURED_BLOCK_TAG);
    }
}
