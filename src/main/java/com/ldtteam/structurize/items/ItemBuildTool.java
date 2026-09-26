package com.ldtteam.structurize.items;

import com.ldtteam.structurize.api.ItemStackUtils;
import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item.Properties;
import static com.ldtteam.structurize.api.constants.Constants.GROUNDSTYLE_RELATIVE;

/**
 * Class handling the buildTool item.
 *
 * <p>PORT26:
 * <ul>
 *   <li>the constructor takes the registrar-provided {@link Properties} (item id must be set
 *       on the properties by {@code DeferredRegister.Items#registerItem} before the Item
 *       constructor runs — "Item id not set" NPE otherwise);</li>
 *   <li>{@code Item#use} now returns plain {@link InteractionResult}
 *       (InteractionResultHolder was removed);</li>
 *   <li>{@code getCraftingRemainingItem}/{@code hasCraftingRemainingItem} were removed from
 *       vanilla — no structurize recipe uses the tools as ingredients (verified against the
 *       datapack), so the overrides are dropped. TODO(minecolonies-phase): re-add via
 *       {@code IItemExtension#getCraftingRemainder(ItemInstance)} if any minecolonies
 *       recipe uses the build tool as an ingredient.</li>
 * </ul>
 */
public class ItemBuildTool extends AbstractItemStructurize
{
    /**
     * Instantiates the buildTool on load.
     */
    public ItemBuildTool(final Properties properties)
    {
        super("sceptergold", properties.stacksTo(1));
    }

    @Override
    @SuppressWarnings("resource")
    public InteractionResult useOn(final UseOnContext context)
    {
        if (context.getLevel().isClientSide())
        {
            openBuildToolWindow(context.getClickedPos().relative(context.getClickedFace()), GROUNDSTYLE_RELATIVE, context.getLevel().registryAccess());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(final Level worldIn, final Player playerIn, final InteractionHand handIn)
    {
        if (worldIn.isClientSide())
        {
            openBuildToolWindow(null, GROUNDSTYLE_RELATIVE, worldIn.registryAccess());
        }

        return InteractionResult.SUCCESS;
    }

    private static void openBuildToolWindow(final BlockPos pos, final int groundstyle, final HolderLookup.Provider provider)
    {
        if (Minecraft.getInstance().screen != null)
        {
            return;
        }

        new WindowExtendedBuildTool(pos, groundstyle, null, WindowExtendedBuildTool.BLOCK_BLUEPRINT_REQUIREMENT, provider).open();
    }
}
