package com.minecolonies.core.compatibility.jei;

import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.blocks.MinecoloniesCropBlock;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import com.minecolonies.core.colony.crafting.LootTableAnalyzer;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.minecolonies.api.util.constant.TranslationConstants.PARTIAL_JEI_INFO;

/**
 * JEI crop recipe category renderer
 */
@SuppressWarnings("MethodParameterOfConcreteClass")
public class CropRecipeCategory extends AbstractRecipeCategory<CropRecipeCategory.CropRecipe>
{
    // PORT26: IRecipeCategory lost getBackground(); the blank background drawable is gone —
    // width/height are declared through the AbstractRecipeCategory constructor instead.
    private static final int WIDTH = 150;
    private static final int HEIGHT = 54;

    private final IDrawable slot;
    private final IDrawable chanceSlot;

    public CropRecipeCategory(@NotNull final IGuiHelper guiHelper)
    {
        super(ModRecipeTypes.CROPS,
                Component.translatable(PARTIAL_JEI_INFO + "crops"),
                guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.DIAMOND_HOE)),
                WIDTH,
                HEIGHT);
        this.slot = guiHelper.getSlotDrawable();
        this.chanceSlot = guiHelper.createDrawable(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/jei_recipe.png"), 0, 121, 18, 18);
    }

    @NotNull
    public static List<CropRecipe> findRecipes()
    {
        final Set<Block> sourceBlocks = new HashSet<>();
        for (final MinecoloniesCropBlock crop : ModBlocks.getCrops())
        {
            sourceBlocks.addAll(crop.getDroppedFrom());
        }

        return sourceBlocks.stream().map(CropRecipe::new).toList();
    }

    @Override
    public void setRecipe(@NotNull final IRecipeLayoutBuilder builder,
                          @NotNull final CropRecipe recipe,
                          @NotNull final IFocusGroup focuses)
    {
        final EquipmentTypeEntry requiredTool = ModEquipmentTypes.hoe.get();
        builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, WIDTH - 18, 0) // PORT26: JEI renamed CATALYST → CRAFTING_STATION (JEI 20.0.0)
                .setSlotName("tool")
                .setBackground(this.chanceSlot, -1, -1)
                .addItemStacks(MinecoloniesAPIProxy.getInstance().getColonyManager().getCompatibilityManager().getListOfAllItems().stream()
                        .filter(requiredTool::checkIsEquipment)
                        .sorted(Comparator.comparing(requiredTool::getMiningLevel))
                        .toList());

        builder.addSlot(RecipeIngredientRole.INPUT, 0, 0)
                .setSlotName("block")
                .setBackground(this.slot, -1, -1)
                .add(recipe.source().getCloneItemStack(Minecraft.getInstance().level, BlockPos.ZERO, recipe.source().defaultBlockState(), false, null)); // PORT26: NeoForge IBlockExtension 5-arg (level, pos, state, includeData, player)

        final List<LootTableAnalyzer.LootDrop> drops = CustomRecipeManager.getInstance().getLootDrops(recipe.source().getLootTable().orElse(null)); // PORT26: Block#getLootTable returns Optional
        final int initialColumns = (WIDTH - 36) / this.slot.getWidth();
        final int rows = Math.max(1, (drops.size() + initialColumns - 1) / initialColumns);
        final int columns = (drops.size() + rows - 1) / rows;
        final int startX = (WIDTH - (columns * this.slot.getWidth())) / 2;
        int x = startX;
        int y = HEIGHT - rows * this.slot.getHeight() + 1;
        int c = 0;

        for (final LootTableAnalyzer.LootDrop drop : drops)
        {
            final IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                    .setBackground(this.chanceSlot, -1, -1)
                    .addItemStacks(drop.getItemStacks());
            slot.addRichTooltipCallback(new JobBasedRecipeCategory.LootTableTooltipCallback(drop, recipe.source().getLootTable().orElse(null))); // PORT26: Optional unwrap
            if (++c >= columns)
            {
                c = 0;
                x = startX;
                y += this.slot.getHeight();
            }
            else
            {
                x += this.slot.getWidth();
            }
        }
    }

    public record CropRecipe(@NotNull Block source)
    {
    }
}
