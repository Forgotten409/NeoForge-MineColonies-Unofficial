package com.ldtteam.domumornamentum.recipe.architectscutter;

import com.google.common.collect.Lists;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.recipe.ModRecipeSerializers;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ArchitectsCutterRecipe implements Recipe<ArchitectsCutterRecipeInput>
{
    public static final MapCodec<ArchitectsCutterRecipe> CODEC = RecordCodecBuilder.mapCodec(builder -> builder
        .group(BuiltInRegistries.BLOCK.holderByNameCodec().fieldOf("block").forGetter(rec -> rec.getBlock().builtInRegistryHolder()),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(ArchitectsCutterRecipe::getCount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ArchitectsCutterRecipe::getComponentPatch))
        .apply(builder, ArchitectsCutterRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArchitectsCutterRecipe> STREAM_CODEC = StreamCodec.composite(Identifier.STREAM_CODEC,
            ArchitectsCutterRecipe::getBlockName,
            ByteBufCodecs.VAR_INT,
            ArchitectsCutterRecipe::getCount,
            DataComponentPatch.STREAM_CODEC,
            ArchitectsCutterRecipe::getComponentPatch,
            ArchitectsCutterRecipe::new);

    private final Identifier blockName;
    private final int count;
    private final DataComponentPatch componentMap;

    public ArchitectsCutterRecipe(final Identifier blockName, final int count, final DataComponentPatch componentMap)
    {
        this.blockName = blockName;
        this.count = count;
        this.componentMap = componentMap;
    }

    public ArchitectsCutterRecipe(final Holder<Block> block, final int count, final DataComponentPatch componentMap)
    {
        this(block.unwrapKey().orElseThrow().identifier(), count, componentMap);
    }

    public Identifier getBlockName()
    {
        return blockName;
    }

    public Block getBlock()
    {
        return BuiltInRegistries.BLOCK.getValue(blockName);
    }

    @Override
    public boolean matches(final @NotNull ArchitectsCutterRecipeInput inv, final @NotNull Level worldIn)
    {
        final Block generatedBlock = getBlock();

        if (!(generatedBlock instanceof final IMateriallyTexturedBlock materiallyTexturedBlock))
            return false;

        final List<IMateriallyTexturedBlockComponent> components = Lists.newArrayList(materiallyTexturedBlock.getComponents());
        for (int componentsIndex = 0; componentsIndex < components.size(); componentsIndex++)
        {
            final IMateriallyTexturedBlockComponent component = components.get(componentsIndex);
            final ItemStack itemStackInSlot = inv.getItem(componentsIndex);

            final Item item = itemStackInSlot.getItem();
            if (!(item instanceof final BlockItem blockItem))
                return false;

            final Block blockInSlot = blockItem.getBlock();

            if (!blockInSlot.defaultBlockState().is(component.getValidSkins()))
                return false;
        }

        return true;
    }

    @Override
    public @NotNull ItemStack assemble(final @NotNull ArchitectsCutterRecipeInput inv)
    {
        final Block generatedBlock = getBlock();

        if (!(generatedBlock instanceof final IMateriallyTexturedBlock materiallyTexturedBlock))
            return ItemStack.EMPTY;

        final List<IMateriallyTexturedBlockComponent> components = Lists.newArrayList(materiallyTexturedBlock.getComponents());

        final MaterialTextureData.Builder textureData = MaterialTextureData.builder();

        for (int componentsIndex = 0; componentsIndex < components.size(); componentsIndex++)
        {
            final IMateriallyTexturedBlockComponent component = components.get(componentsIndex);
            final ItemStack itemStackInSlot = inv.getItem(componentsIndex);

            if (itemStackInSlot.isEmpty() && component.isOptional())
                continue;

            final Item item = itemStackInSlot.getItem();
            if (!(item instanceof final BlockItem blockItem))
                return ItemStack.EMPTY;

            final Block blockInSlot = blockItem.getBlock();

            if (!blockInSlot.defaultBlockState().is(component.getValidSkins()))
                return ItemStack.EMPTY;

            textureData.setComponent(component.getId(), blockInSlot);
        }

        final ItemStack result = new ItemStack(generatedBlock);
        textureData.writeToItemStack(result);
        result.setCount(Math.max(components.size(), count));

        result.applyComponents(componentMap);

        return result;
    }

    // PORT26: canCraftInDimensions and getResultItem(Provider) were removed from the Recipe
    // interface in 26.1.2 — neither had external callers; getResultItem survives below as a
    // plain helper (without the registry provider, which the old body never used).

    /**
     * Plain helper (no longer an interface override): preview of the result stack.
     * Used by {@link com.ldtteam.domumornamentum.container.ArchitectsCutterContainer}.
     */
    public @NotNull ItemStack getResultItem()
    {
        final Block generatedBlock = getBlock();

        if (!(generatedBlock instanceof IMateriallyTexturedBlock))
            return ItemStack.EMPTY;

        final ItemStack result = new ItemStack(generatedBlock);
        result.applyComponents(componentMap);

        return result;
    }

    // PORT26: the interface now returns RecipeSerializer<? extends Recipe<T>> / RecipeType<? extends Recipe<T>>.
    @Override
    public @NotNull RecipeSerializer<? extends Recipe<ArchitectsCutterRecipeInput>> getSerializer()
    {
        return ModRecipeSerializers.ARCHITECTS_CUTTER.get();
    }

    @Override
    public @NotNull RecipeType<? extends Recipe<ArchitectsCutterRecipeInput>> getType()
    {
        return ModRecipeTypes.ARCHITECTS_CUTTER.get();
    }

    // PORT26: new abstract members of the 26.1.2 Recipe interface.

    /**
     * The cutter's ingredients come from the target block's component list (dynamic per
     * block), so there is no static placement to declare.
     */
    @Override
    public @NotNull PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    /**
     * PORT26 (batch 9): 26.1.2 RecipeManager#finalizeRecipeLoading logs
     * "Recipe ... can't be placed due to empty ingredients and will be ignored" for every
     * recipe that is not special AND has a non-placeable PlacementInfo (~90 warnings for our
     * cutter recipes). The recipe itself stays in the manager (the cutter container filters
     * recipeManager.getRecipes() directly), but marking it special — exactly what vanilla does
     * for recipes that can never be auto-placed in a crafting grid — silences the warning and
     * keeps the recipe out of the recipe book property sets, which is what we want anyway.
     */
    @Override
    public boolean isSpecial()
    {
        return true;
    }

    /**
     * The Architect's Cutter has its own UI and never appears in the vanilla recipe book —
     * the misc crafting category is a harmless placeholder.
     */
    @Override
    public @NotNull net.minecraft.world.item.crafting.RecipeBookCategory recipeBookCategory()
    {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    // PORT26: showNotification and group are no longer default on Recipe — explicit overrides required.
    @Override
    public boolean showNotification()
    {
        return true;
    }

    @Override
    public String group()
    {
        return "";
    }

    public @NotNull DataComponentPatch getComponentPatch()
    {
        return componentMap;
    }

    public int getCount()
    {
        return count;
    }
}
