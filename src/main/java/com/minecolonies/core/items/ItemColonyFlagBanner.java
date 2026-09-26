package com.minecolonies.core.items;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.util.PortIds;
import com.minecolonies.core.tileentities.TileEntityColonyFlag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;

/**
 * This item represents the colony flag banner, both wall and floor blocks.
 * Allows duplication of other banner pattern lists to its own default
 */
public class ItemColonyFlagBanner extends BannerItem
{
    public ItemColonyFlagBanner(String name, Properties properties)
    {
        // PORT26: item id must be on Item.Properties before the Item constructor runs
        // ("Item id not set"); useBlockDescriptionPrefix keeps the 1.21.1 lang key
        // ("item.minecolonies.colony_banner" — the item places both banner blocks).
        this(ModBlocks.blockColonyBanner, ModBlocks.blockColonyWallBanner,
          properties.setId(PortIds.itemKey(name)).useBlockDescriptionPrefix().stacksTo(16));
    }

    public ItemColonyFlagBanner(Block standingBanner, Block wallBanner, Properties builder)
    {
        super(standingBanner, wallBanner, builder);
    }

    @NotNull
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        // Duplicate the patterns of the banner that was clicked on
        BlockEntity te = context.getLevel().getBlockEntity(context.getClickedPos());
        ItemStack stack = context.getPlayer().getMainHandItem();

        if (te instanceof BannerBlockEntity || te instanceof TileEntityColonyFlag)
        {
            final BannerPatternLayers bannerPatternLayers;
            if (te instanceof BannerBlockEntity)
            {
                bannerPatternLayers = ((BannerBlockEntity) te).getPatterns();
            }
            else
            {
                bannerPatternLayers = ((TileEntityColonyFlag) te).getPatterns();
            }

            stack.set(DataComponents.BANNER_PATTERNS, bannerPatternLayers);
            return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable TooltipContext ctx, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flagIn)
    {
        // PORT26: tooltips stream through a Consumer<Component) — collect super's lines in a
        // temporary list so the "drop the second line" edit below is still possible.
        final List<Component> lines = new java.util.ArrayList<>();
        super.appendHoverText(stack, ctx, display, lines::add, flagIn);

        // Remove the base, as they have no translations (Mojang were lazy. Or maybe saving space?)
        if (lines.size() > 1) lines.remove(1);

        lines.forEach(tooltip::accept);
    }
}
