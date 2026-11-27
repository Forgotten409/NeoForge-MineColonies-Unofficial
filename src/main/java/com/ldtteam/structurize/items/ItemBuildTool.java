package com.ldtteam.structurize.items;

import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item.Properties;
import org.jspecify.annotations.Nullable;

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
 *   <li>PORT26 FIX (0.7.8, "recipes that use the Build Tool consume the Build Tool"):
 *       1.21.1 overrode the Item hooks {@code getCraftingRemainingItem(ItemStack)} /
 *       {@code hasCraftingRemainingItem(ItemStack)} so the build tool is returned to the
 *       player after crafting instead of being consumed — and 70 recipes of the official
 *       datapack use {@code structurize:sceptergold} as an ingredient (every hut block, the
 *       clipboard, the resource scroll, the colony map, the postbox, waypoints, stashes,
 *       quarries, …). Those Item methods no longer exist in 26.1.2: vanilla moved the
 *       crafting remainder to {@code Item#getCraftingRemainder()} (final, backed by
 *       {@code Item.Properties#craftRemainder} — unusable for a self-referencing remainder,
 *       see ModItemsInitializer "crash fix #5"), and NeoForge routes crafting remainders
 *       through {@code IItemExtension#getCraftingRemainder(ItemInstance)}, where a null
 *       return means "no remainder" (replacing the old has/get pair). The crafting-table
 *       path calls that hook via {@code CraftingRecipe#defaultCraftingReminder ->
 *       ItemStack#getCraftingRemainder() -> Item#getCraftingRemainder(ItemInstance)}, so
 *       overriding it here restores upstream behaviour everywhere.</li>
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

    /**
     * PORT26 FIX (0.7.8): restores the 1.21.1 overrides
     * {@code getCraftingRemainingItem(ItemStack) -> itemStack.copy()} and
     * {@code hasCraftingRemainingItem(ItemStack) -> !isEmpty(itemStack)} on the 26.1.2 API:
     * hand the build tool back to the crafter instead of consuming it (see class comment
     * for the full API-path analysis).
     *
     * @param instance the ingredient instance the remainder is requested for.
     * @return the build tool itself (components preserved), or {@code null} (= no remainder)
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
