package com.ldtteam.domumornamentum.jei;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.ldtteam.domumornamentum.IDomumOrnamentumApi;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockManager;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.item.interfaces.IDoItem;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipeInput;
import com.ldtteam.domumornamentum.util.Constants;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ldtteam.domumornamentum.util.Constants.MOD_ID;
import static com.ldtteam.domumornamentum.util.GuiConstants.*;

/**
 * JEI category for the Architect's Cutter — PORT 26.1.2 (JEI 29.x).
 *
 * <p>Adaptations from the 1.21.1 category:
 * <ul>
 *   <li>{@code RecipeType.createFromVanilla} → {@link IRecipeType#create} (JEI 20+ renamed the
 *       type to avoid the vanilla name clash);</li>
 *   <li>{@code getBackground()} was removed from the category API (JEI 27+) — the layout size is
 *       now declared via {@link #getWidth()}/{@link #getHeight()} and the cutter texture is drawn
 *       manually at the start of {@link #draw} (JEI calls the category's draw before the slots,
 *       so it still acts as the background layer);</li>
 *   <li>{@code getTooltipStrings} → {@link #getTooltip} with a tooltip builder;</li>
 *   <li>the "re-assemble the output when JEI cycles the displayed inputs" logic moved from
 *       {@code draw} into {@link #onDisplayedIngredientsUpdate}, the dedicated JEI 19.8.3+ hook
 *       for exactly this pattern;</li>
 *   <li>{@code Recipe#assemble} lost its registry-access parameter and
 *       {@code BuiltInRegistries.BLOCK.getTag(...)} became {@code getTagOrEmpty(...)} in 26.1;</li>
 *   <li>ingredient rendering goes through {@link IIngredientRenderer#render(GuiGraphicsExtractor, Object, int, int)}
 *       which performs its own translate (the old PoseStack manipulation is gone — the 26.1 GUI
 *       pose is a 2D matrix stack managed by the render helpers).</li>
 * </ul>
 *
 * <p>PORT26 (batch 15): the {@code @OnlyIn(Dist.CLIENT)} annotation was removed — NeoForge 26.1
 * no longer strips members at runtime and logs a startup warning for every {@code @OnlyIn} use
 * ("The mod minecolonies uses the @OnlyIn annotation..."). This class is client-side by
 * construction anyway: JEI only loads {@code @JeiPlugin} classes on the client, and the jar is
 * never loaded on a dedicated server without JEI present.</p>
 */
public class ArchitectsCutterCategory implements IRecipeCategory<RecipeHolder<ArchitectsCutterRecipe>>
{
    public static final IRecipeType<RecipeHolder<ArchitectsCutterRecipe>> TYPE = IRecipeType.create(ModRecipeTypes.ARCHITECTS_CUTTER.get());

    /**
     * Horizontal offset between the real cutter display and the JEI display, since we only show a portion.
     */
    private static final int JEI_OFFSET_X = 55;
    /**
     * Vertical offset between the real cutter display and the JEI display, since we only show a portion.
     */
    private static final int JEI_OFFSET_Y = 14;

    /**
     * The visible portion of the cutter background: same region the 1.21.1 category declared as
     * its background drawable (see {@link #getWidth()}/{@link #getHeight()}).
     */
    private static final int JEI_BG_W = CUTTER_BG_W - JEI_OFFSET_X - 9;
    private static final int JEI_BG_H = 88;

    private final JEIPlugin plugin;
    private final IDrawable background;
    private final IDrawable thumb;
    private final IDrawable slot;
    private final IDrawable button;
    private final IDrawable icon;
    private final LoadingCache<ArchitectsCutterRecipe, DisplayData> cachedDisplayData;

    public ArchitectsCutterCategory(final IGuiHelper guiHelper, final JEIPlugin plugin)
    {
        this.plugin = plugin;
        final Identifier texture = Constants.resLocDO("textures/gui/container/architectscutter2.png");
        this.background = guiHelper.createDrawable(texture, JEI_OFFSET_X, JEI_OFFSET_Y, JEI_BG_W, JEI_BG_H);
        this.thumb = guiHelper.createDrawable(texture, CUTTER_SLIDER_U_DISABLED, CUTTER_SLIDER_V, CUTTER_SLIDER_W, CUTTER_SLIDER_H);
        this.slot = guiHelper.createDrawable(texture, CUTTER_SLOT_U, CUTTER_SLOT_V, CUTTER_SLOT_W, CUTTER_SLOT_H);
        this.button = guiHelper.createDrawable(texture, CUTTER_RECIPE_U_NORMAL, CUTTER_RECIPE_V, CUTTER_RECIPE_W, CUTTER_RECIPE_H);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(IDomumOrnamentumApi.getInstance().getBlocks().getArchitectsCutter()));
        this.cachedDisplayData = CacheBuilder.newBuilder()
                .maximumSize(25)
                .build(new CacheLoader<>()
                {
                    @Override
                    public DisplayData load(final ArchitectsCutterRecipe key)
                    {
                        return new DisplayData(key);
                    }
                });
    }

    @Override
    public IRecipeType<RecipeHolder<ArchitectsCutterRecipe>> getRecipeType()
    {
        return TYPE;
    }

    @Override
    public Component getTitle()
    {
        return Component.translatable(MOD_ID + ".architectscutter");
    }

    @Override
    public int getWidth()
    {
        return JEI_BG_W;
    }

    @Override
    public int getHeight()
    {
        return JEI_BG_H;
    }

    @Override
    public IDrawable getIcon()
    {
        return this.icon;
    }

    @Override
    public void setRecipe(final IRecipeLayoutBuilder builder,
                          final RecipeHolder<ArchitectsCutterRecipe> holder,
                          final IFocusGroup focuses)
    {
        final ArchitectsCutterRecipe recipe = holder.value();
        final var generatedBlock = recipe.getBlock();

        if (!(generatedBlock instanceof final IMateriallyTexturedBlock materiallyTexturedBlock))
            return;

        final List<IMateriallyTexturedBlockComponent> components = List.copyOf(materiallyTexturedBlock.getComponents());
        final List<List<ItemStack>> inputs = components.stream()
                .map(component -> {
                    final List<ItemStack> list = new ArrayList<>();
                    // PORT26: Registry#getTag (Optional) was replaced by getTagOrEmpty (Iterable).
                    BuiltInRegistries.BLOCK.getTagOrEmpty(component.getValidSkins())
                            .forEach(holderSetEntry -> list.add(new ItemStack(holderSetEntry.value())));
                    Collections.shuffle(list);
                    return list;
                })
                .collect(Collectors.toList());

        final List<ItemStack> defaultInputs = components.stream()
                .map(component -> new ItemStack(component.getDefault()))
                .collect(Collectors.toList());

        final DisplayData displayData = cachedDisplayData.getUnchecked(recipe);
        final Container container = displayData.getIngredientContainer();

        for (int i = 0; i < defaultInputs.size(); ++i)
        {
            container.setItem(i, defaultInputs.get(i));
        }

        ItemStack output = recipe.assemble(new ArchitectsCutterRecipeInput(container));
        if (output.isEmpty())   // wat?
        {
            output = recipe.getResultItem();
            if (output.isEmpty())   // WAT?
            {
                output = new ItemStack(generatedBlock);
            }
            output.setCount(Math.max(components.size(), recipe.getCount()));
        }
        displayData.setOutput(output);

        builder.addSlot(RecipeIngredientRole.OUTPUT, CUTTER_OUTPUT_X - JEI_OFFSET_X, CUTTER_OUTPUT_Y - JEI_OFFSET_Y)
                .setCustomRenderer(VanillaTypes.ITEM_STACK, new OutputRenderer(plugin, displayData))
                .add(output);

        for (int slot = 0; slot < IMateriallyTexturedBlockManager.getInstance().getMaxTexturableComponentCount(); ++slot)
        {
            final int x = CUTTER_INPUT_X - JEI_OFFSET_X;
            final int y = CUTTER_INPUT_Y - JEI_OFFSET_Y + (slot * CUTTER_INPUT_SPACING);
            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .setBackground(this.slot, -1, -1)
                    .addItemStacks(slot < inputs.size() ? inputs.get(slot) : Collections.emptyList());
        }
    }

    @Override
    public void getTooltip(final ITooltipBuilder tooltip,
                           final RecipeHolder<ArchitectsCutterRecipe> holder,
                           final IRecipeSlotsView recipeSlotsView,
                           final double mouseX, final double mouseY)
    {
        final ArchitectsCutterRecipe recipe = holder.value();

        final Rect2i groupButton = new Rect2i(CUTTER_RECIPE_X - JEI_OFFSET_X, CUTTER_RECIPE_Y + 1 - JEI_OFFSET_Y, this.button.getWidth(), this.button.getHeight());
        if (groupButton.contains((int) mouseX, (int) mouseY))
        {
            final DisplayData displayData = cachedDisplayData.getUnchecked(recipe);
            tooltip.add(Component.translatable("cuttergroup." +
                    displayData.getGroupId().getNamespace() + "." + displayData.getGroupId().getPath()));
        }

        final Rect2i recipeButton = new Rect2i(CUTTER_RECIPE_X - JEI_OFFSET_X, CUTTER_RECIPE_Y + 1 - JEI_OFFSET_Y + CUTTER_RECIPE_SPACING, this.button.getWidth(), this.button.getHeight());
        if (recipeButton.contains((int) mouseX, (int) mouseY))
        {
            final DisplayData displayData = cachedDisplayData.getUnchecked(recipe);
            tooltip.add(displayData.getOutput().getHoverName());
        }
    }

    @Override
    public void draw(final RecipeHolder<ArchitectsCutterRecipe> holder,
                     final IRecipeSlotsView recipeSlotsView,
                     final GuiGraphicsExtractor guiGraphics,
                     final double mouseX, final double mouseY)
    {
        final ArchitectsCutterRecipe recipe = holder.value();
        final DisplayData displayData = cachedDisplayData.getUnchecked(recipe);

        // JEI 27+ removed getBackground(): the category draws its own background here.
        // JEI invokes this before rendering the slots, so the texture stays underneath them.
        this.background.draw(guiGraphics);

        this.thumb.draw(guiGraphics, CUTTER_SLIDER_X - JEI_OFFSET_X, CUTTER_RECIPE_Y + 1 - JEI_OFFSET_Y);
        this.thumb.draw(guiGraphics, CUTTER_SLIDER_X - JEI_OFFSET_X, CUTTER_RECIPE_Y + 1 + CUTTER_RECIPE_SPACING - JEI_OFFSET_Y);

        drawButton(guiGraphics, CUTTER_RECIPE_X - JEI_OFFSET_X, CUTTER_RECIPE_Y + 1 - JEI_OFFSET_Y, displayData.getGroup());
        drawButton(guiGraphics, CUTTER_RECIPE_X - JEI_OFFSET_X, CUTTER_RECIPE_Y + 1 - JEI_OFFSET_Y + CUTTER_RECIPE_SPACING, displayData.getOutput());
    }

    /**
     * PORT26 (JEI 19.8.3+): replaces the old {@code reassembleIfNeeded} call inside {@code draw} —
     * JEI now has a dedicated hook that runs whenever it cycles the displayed ingredients of a
     * recipe, which is exactly the trigger the cutter needs to re-assemble its dynamic output.
     */
    @Override
    public void onDisplayedIngredientsUpdate(final RecipeHolder<ArchitectsCutterRecipe> holder,
                                             final List<IRecipeSlotDrawable> recipeSlots,
                                             final IFocusGroup focuses)
    {
        final ArchitectsCutterRecipe recipe = holder.value();
        final DisplayData displayData = cachedDisplayData.getUnchecked(recipe);
        displayData.reassembleIfNeeded(recipeSlots.stream()
          .filter(slotView -> slotView.getRole() == RecipeIngredientRole.INPUT)
          .collect(Collectors.toList()));
    }

    private void drawButton(final GuiGraphicsExtractor guiGraphics, final int x, final int y, final ItemStack item)
    {
        this.button.draw(guiGraphics, x, y);
        final ItemStack buttonStack = item.copy();
        buttonStack.setCount(1);
        this.plugin.getIngredientManager()
          .getIngredientRenderer(VanillaTypes.ITEM_STACK)
          .render(guiGraphics, buttonStack, x, y + 1);
    }

    private static class OutputRenderer implements IIngredientRenderer<ItemStack>
    {
        private final JEIPlugin plugin;
        private final DisplayData displayData;

        private IIngredientRenderer<ItemStack> renderer;

        public OutputRenderer(final JEIPlugin plugin, final DisplayData displayData)
        {
            this.plugin = plugin;
            this.displayData = displayData;
        }

        private IIngredientRenderer<ItemStack> getRenderer()
        {
            if (renderer == null)
            {
                renderer = plugin.getIngredientManager().getIngredientRenderer(VanillaTypes.ITEM_STACK);
            }
            return renderer;
        }

        @Override
        public void render(final GuiGraphicsExtractor guiGraphics, final ItemStack ingredient)
        {
            getRenderer().render(guiGraphics, displayData.getOutput());
        }

        @Override
        @SuppressWarnings("deprecation")
        public List<Component> getTooltip(final ItemStack ingredient, final TooltipFlag tooltipFlag)
        {
            // the abstract (and only) tooltip method of JEI 29.x ingredient renderers —
            // every builder/context overload defaults down to this one, and the standard
            // item renderer's tooltip for the CURRENT dynamic output is what we want to show
            return getRenderer().getTooltip(displayData.getOutput(), tooltipFlag);
        }

        @Override
        public Font getFontRenderer(final Minecraft minecraft, final ItemStack ingredient)
        {
            return getRenderer().getFontRenderer(minecraft, displayData.getOutput());
        }

        @Override
        public int getWidth()
        {
            return 16;
        }

        @Override
        public int getHeight()
        {
            return 16;
        }
    }

    private static class DisplayData
    {
        private final ArchitectsCutterRecipe recipe;

        private Identifier groupId = Identifier.withDefaultNamespace("");
        private ItemStack group = ItemStack.EMPTY;
        private ItemStack output = ItemStack.EMPTY;

        private final Container ingredientContainer =
                new SimpleContainer(IMateriallyTexturedBlockManager.getInstance().getMaxTexturableComponentCount());

        public DisplayData(final ArchitectsCutterRecipe recipe)
        {
            this.recipe = recipe;
        }

        public Container getIngredientContainer()
        {
            return this.ingredientContainer;
        }

        public Identifier getGroupId()
        {
            return this.groupId;
        }

        public ItemStack getGroup()
        {
            return this.group;
        }

        public ItemStack getOutput()
        {
            return this.output;
        }

        public void setOutput(final ItemStack output)
        {
            if (output.getItem() instanceof IDoItem doItem)
            {
                this.groupId = doItem.getGroup();
                this.group = ModBlocks.getInstance().getOrComputeItemGroups()
                        .getOrDefault(this.groupId, Collections.singletonList(ItemStack.EMPTY))
                        .get(0);
            }
            else
            {
                this.groupId = Identifier.withDefaultNamespace("");
                this.group = ItemStack.EMPTY;
            }

            this.output = output;
        }

        public void reassembleIfNeeded(final List<? extends IRecipeSlotView> slotViews)
        {
            boolean same = true;

            for (int i = 0; i < slotViews.size(); ++i)
            {
                final Optional<ItemStack> currentItem = slotViews.get(i).getDisplayedItemStack();

                if (currentItem.isPresent())
                {
                    if (!ItemStack.isSameItemSameComponents(currentItem.get(), this.ingredientContainer.getItem(i)))
                    {
                        same = false;
                        this.ingredientContainer.setItem(i, currentItem.get());
                    }
                }
            }

            if (!same)
            {
                this.output = recipe.assemble(new ArchitectsCutterRecipeInput(this.ingredientContainer));
            }
        }
    }
}
