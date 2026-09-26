package com.ldtteam.structurize.items;

import com.ldtteam.structurize.api.ISpecialBlockPickItem;
import com.ldtteam.structurize.api.Utils;
import com.ldtteam.structurize.blockentities.BlockEntityTagSubstitution;
import com.ldtteam.structurize.blocks.ModBlocks;
import com.ldtteam.structurize.component.CapturedBlock;
import com.ldtteam.structurize.component.ModDataComponents;
import com.ldtteam.structurize.network.messages.AbsorbBlockMessage;
import com.ldtteam.structurize.tag.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item.Properties;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * PORT26:
 * <ul>
 *   <li>constructor takes the registrar-provided {@link Properties} (item id must be set
 *       before the Item constructor runs) — the block comes from the deferred holder lazily;</li>
 *   <li>{@code BlockState#getCloneItemStack(HitResult, LevelReader, BlockPos, Player)} is
 *       now a vanilla 3-arg method on the state: {@code getCloneItemStack(LevelReader, BlockPos,
 *       boolean includeData)} — includeData=true matches the pick-with-data intent;</li>
 *   <li>ModDataComponents holders need {@code .get()}.</li>
 * </ul>
 */
public class ItemTagSubstitution extends BlockItem implements ISpecialBlockPickItem
{
    public ItemTagSubstitution(final Properties properties)
    {
        // PORT26: BlockItem no longer derives its description id from the block — Item
        // properties default to the "item." prefix. 1.21.1's BlockItem#getDescriptionId
        // returned the block's key ("block.structurize.blocktagsubstitution" = "Tag Anchor
        // Block" in the lang file), so apply the block prefix explicitly.
        super(ModBlocks.blockTagSubstitution.get(),
          properties.useBlockDescriptionPrefix()
            .component(ModDataComponents.CAPTURED_BLOCK.get(), CapturedBlock.EMPTY));
    }

    @NotNull
    @Override
    public InteractionResult onBlockPick(@NotNull Player player,
                                         @NotNull ItemStack stack,
                                         @Nullable BlockPos pos,
                                         final boolean ctrlKey)
    {
        if (pos == null)
        {
            if (!player.level().isClientSide())
            {
                CapturedBlock.EMPTY.writeToItemStack(stack);
            }
            return InteractionResult.SUCCESS;
        }

        final BlockState blockstate = player.level().getBlockState(pos);
        if (blockstate.is(BlockTags.WITHER_IMMUNE))
        {
            // this way lies madness, and/or Sparta...
            if (!player.level().isClientSide())
            {
                CapturedBlock.EMPTY.writeToItemStack(stack);
            }
            return InteractionResult.SUCCESS;
        }

        if (player.level().isClientSide())
        {
            ItemStack pick = getPickedBlock(player, pos, blockstate);

            // sadly we can't use the default message since we want to pass an extra ItemStack...
            //   (and getCloneItemStack is client-side-only, somewhat strangely)
            new AbsorbBlockMessage(pos, pick).sendToServer();
        }
        return InteractionResult.FAIL;
    }

    @NotNull
    private ItemStack getPickedBlock(@NotNull Player player, @NotNull BlockPos pos, @NotNull BlockState blockstate)
    {
        // PORT26: BlockState#getCloneItemStack(LevelReader, BlockPos, boolean includeData) is
        // a vanilla method now (the old signature took HitResult + Player)
        return blockstate.getCloneItemStack(player.level(), pos, true);
    }

    public void onAbsorbBlock(@NotNull final ServerPlayer player,
                              @NotNull final ItemStack stack,
                              @NotNull final BlockPos pos,
                              @NotNull final ItemStack absorbItem)
    {
        final BlockState blockstate = player.level().getBlockState(pos);
        final BlockEntity blockentity = player.level().getBlockEntity(pos);

        final CapturedBlock replacement;
        if (blockentity instanceof BlockEntityTagSubstitution blockception)
        {
            replacement = blockception.getReplacement();
        }
        else if (!isAllowed(blockentity))
        {
            Utils.playErrorSound(player);
            return;
        }
        else
        {
            replacement = new CapturedBlock(blockstate, blockentity, player.level().registryAccess(), absorbItem);
        }

        replacement.writeToItemStack(stack);
    }

    private boolean isAllowed(@Nullable final BlockEntity blockentity)
    {
        if (blockentity == null) return true;

        // PORT26: Registry#getTag(TagKey) was removed; the tag membership check lives on
        // the registry Holder (Holder#is(TagKey)) — same pattern as the EntityType checks.
        return blockentity.getType().builtInRegistryHolder().is(ModTags.SUBSTITUTION_ABSORB_WHITELIST);
    }

    @Override
    public Component getHighlightTip(@NotNull final ItemStack stack, @NotNull final Component displayName)
    {
        final ItemStack absorbed = CapturedBlock.readFromItemStack(stack).itemStack();
        if (!absorbed.isEmpty())
        {
            return Component.empty()
                    .append(super.getHighlightTip(stack, displayName))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                    .append(absorbed.getHoverName());
        }

        return super.getHighlightTip(stack, displayName);
    }

    @NotNull
    @Override
    public Optional<TooltipComponent> getTooltipImage(@NotNull final ItemStack stack)
    {
        final ItemStack absorbedItem = CapturedBlock.readFromItemStack(stack).itemStack();

        if (!absorbedItem.isEmpty())
        {
            return Optional.of(new ItemStackTooltip(absorbedItem));
        }

        return super.getTooltipImage(stack);
    }
}
