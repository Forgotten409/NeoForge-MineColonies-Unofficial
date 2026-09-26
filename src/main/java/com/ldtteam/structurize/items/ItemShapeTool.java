package com.ldtteam.structurize.items;

import com.ldtteam.structurize.client.gui.WindowShapeTool;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item.Properties;

/**
 * PORT26: constructor takes the registrar-provided properties (item id must be set before
 * the Item constructor runs); {@code Item#use} now returns plain {@link InteractionResult}
 * (InteractionResultHolder was removed). The 1.21.1 crafting-remainder overrides were
 * dropped — no recipe uses the tool as an ingredient (see ItemBuildTool note).
 */
public class ItemShapeTool extends AbstractItemStructurize
{
    /**
     * Sets the name, creative tab, and registers the item.
     */
    public ItemShapeTool(final Properties properties)
    {
        super("shapetool", properties.stacksTo(1));
    }

    @Override
    @SuppressWarnings("resource")
    public InteractionResult useOn(final UseOnContext context)
    {
        if (context.getLevel().isClientSide())
        {
            new WindowShapeTool(context.getClickedPos().relative(context.getClickedFace()), context.getLevel().registryAccess()).open();
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(final Level worldIn, final Player playerIn, final InteractionHand hand)
    {
        if (worldIn.isClientSide())
        {
            new WindowShapeTool(null, worldIn.registryAccess()).open();
        }

        return InteractionResult.SUCCESS;
    }
}
