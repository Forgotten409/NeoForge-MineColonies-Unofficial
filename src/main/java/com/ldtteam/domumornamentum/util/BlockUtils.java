package com.ldtteam.domumornamentum.util;

import com.ldtteam.domumornamentum.entity.block.AbstractMateriallyTexturedBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.Collections;
import java.util.List;

public class BlockUtils
{

    private BlockUtils()
    {
        throw new IllegalStateException("Can not instantiate an instance of: BlockUtils. This is a utility class");
    }

    public static Component getHoverName(final Block block) {
        return new ItemStack(block).getHoverName();
    }

    @Deprecated(forRemoval = true, since = "1.21")
    public static List<ItemStack> getMaterializedDrops(final LootParams.Builder builder, final Property<?>... blockStateProperties)
    {
        final ItemStack stack = getMaterializedItemStack(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY), builder.getLevel().registryAccess(), blockStateProperties);
        if (!stack.isEmpty())
            return List.of(stack);

        return Collections.emptyList();
    }

    public static ItemStack getMaterializedItemStack(final BlockEntity blockEntity,
        final HolderLookup.Provider provider,
        final Property<?>... blockStateProperties)
    {
        if (!(blockEntity instanceof final AbstractMateriallyTexturedBlockEntity texturedBlockEntity))
        {
            return ItemStack.EMPTY;
        }

        final ItemStack result = new ItemStack(blockEntity.getBlockState().getBlock());
        // PORT26: BlockEntity#saveToItem(ItemStack, Provider) is gone — replicate the vanilla
        // pick-block flow (ServerGamePacketListenerImpl#addBlockDataToItem):
        //   saveCustomOnly -> removeComponentsFromTag -> setBlockEntityData -> applyComponents(collectComponents)
        try (final net.minecraft.util.ProblemReporter.ScopedCollector reporter =
               new net.minecraft.util.ProblemReporter.ScopedCollector(blockEntity.problemPath(), org.slf4j.LoggerFactory.getLogger(BlockUtils.class)))
        {
            final net.minecraft.world.level.storage.TagValueOutput output =
              net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, provider);
            blockEntity.saveCustomOnly(output);
            blockEntity.removeComponentsFromTag(output);
            BlockItem.setBlockEntityData(result, blockEntity.getType(), output);
            result.applyComponents(blockEntity.collectComponents());
        }

        if (blockStateProperties.length > 0)
        {
            final BlockState blockState = texturedBlockEntity.getBlockState();
            result.update(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY, props -> {
                for (final Property<?> property : blockStateProperties)
                {
                    props = props.with(property, blockState);
                }
                return props;
            });
        }

        return result;
    }

    public static <T extends Comparable<T>> void putPropertyIntoBlockStateTag(final ItemStack itemStack,
        final Property<T> property,
        final T value)
    {
        if (!FMLEnvironment.isProduction() && !(itemStack.getItem() instanceof BlockItem))
        {
            throw new IllegalArgumentException("item not BlockItem: " + itemStack.getItem());
        }
        itemStack.update(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY, props -> props.with(property, value));
    }

    public static <T extends Comparable<T>> T getPropertyFromBlockStateTag(final ItemStack itemStack,
        final Property<T> property,
        final T defaultValue)
    {
        final T blockValue = itemStack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(property);
        return blockValue == null ? defaultValue : blockValue;
    }
}
