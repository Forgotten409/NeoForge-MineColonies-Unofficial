package com.ldtteam.structurize.items;

import com.ldtteam.structurize.client.gui.WindowShapeTool;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item.Properties;
import org.jspecify.annotations.Nullable;

/**
 * PORT26: constructor takes the registrar-provided properties (item id must be set before
 * the Item constructor runs); {@code Item#use} now returns plain {@link InteractionResult}
 * (InteractionResultHolder was removed).
 *
 * <p>PORT26 FIX (0.7.8): the 1.21.1 crafting-remainder overrides (same pair as the build
 * tool — return the tool to the player when used as a crafting ingredient) were initially
 * dropped with a "no recipe uses this item" note. That held for structurize's own datapack,
 * but the build tool proved the minecolonies datapack DOES use these tools as ingredients
 * (70 recipes), so the overrides are restored on the 26.1.2 API —
 * {@code IItemExtension#getCraftingRemainder(ItemInstance)} (see ItemBuildTool for the full
 * API-path analysis). Parity with upstream ItemShapeTool.</p>
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

    /**
     * PORT26 FIX (0.7.8): restores the 1.21.1 overrides
     * {@code getCraftingRemainingItem(ItemStack) -> itemStack.copy()} and
     * {@code hasCraftingRemainingItem(ItemStack) -> !isEmpty(itemStack)} on the 26.1.2 API
     * (see ItemBuildTool for the full API-path analysis).
     *
     * @param instance the ingredient instance the remainder is requested for.
     * @return the shape tool itself (components preserved), or {@code null} (= no remainder)
     *              for an empty/absent ingredient.
     */
    @Override
    public @Nullable ItemStackTemplate getCraftingRemainder(final ItemInstance instance)
    {
        // Upstream 1.21.1: empty ingredient -> no remainder.
        if (instance == null || instance.count() <= 0)
        {
            return null;
        }
        // Upstream 1.21.1: return a copy of the ingredient stack (item + components).
        if (instance instanceof final ItemStack stack)
        {
            return ItemStackTemplate.fromNonEmptyStack(stack);
        }
        return new ItemStackTemplate(instance.typeHolder());
    }
}
