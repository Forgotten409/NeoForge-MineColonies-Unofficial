package com.minecolonies.api.items;

import com.minecolonies.api.blocks.AbstractBlockHut;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.world.item.TooltipFlag;
import com.minecolonies.api.items.component.ModDataComponents;
import org.jetbrains.annotations.NotNull;

/**
 * A custom item class for hut blocks.
 */
public class ItemBlockHut extends BlockItem
{
    /**
     * This items block.
     */
    private AbstractBlockHut<?> block;

    /**
     * Creates a new ItemBlockHut representing the item form of the given {@link AbstractBlockHut}.
     * 
     * @param block   the {@link AbstractBlockHut} this item represents.
     * @param builder the item properties to use.
     */
    public ItemBlockHut(AbstractBlockHut<?> block, Properties builder)
    {
        super(block, builder);
        this.block = block;
    }

    @Override
    public void appendHoverText(@NotNull final ItemStack stack, @NotNull final Item.TooltipContext context, @NotNull final TooltipDisplay display, @NotNull final Consumer<Component> tooltip, @NotNull final TooltipFlag flags)
    {
        // PORT26: Block-level tooltip hook removed from the block hierarchy -> lives on the BlockItem now
        super.appendHoverText(stack, context, display, tooltip, flags);

        stack.addToTooltip(ModDataComponents.HUT_COMPONENT.get(), context, display, tooltip, flags);
        stack.addToTooltip(ModDataComponents.COLONY_ID_COMPONENT.get(), context, display, tooltip, flags);
    }
}
