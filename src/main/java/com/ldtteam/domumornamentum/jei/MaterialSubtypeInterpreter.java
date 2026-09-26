package com.ldtteam.domumornamentum.jei;

import com.ldtteam.domumornamentum.util.Constants;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * Tells JEI that the multi-variant DO items (door, trapdoor, fancy door, fancy trapdoor, post,
 * panel) are distinct ingredients depending on the {@code type} property stored in their
 * {@code minecraft:block_state} component — PORT of the 1.21.1
 * {@code MaterialSubtypeInterpreter} ({@code IIngredientSubtypeInterpreter#apply} returning the
 * property value string).
 *
 * <p>PORT26 (JEI 29.x): the interface was renamed to {@link ISubtypeInterpreter} and the method
 * changed from {@code String apply(stack, context)} to
 * {@code @Nullable Object getSubtypeData(ingredient, context)} — returning {@code null} replaces
 * the old {@code IIngredientSubtypeInterpreter.NONE} "no subtype" result.
 */
public class MaterialSubtypeInterpreter implements ISubtypeInterpreter<ItemStack>
{
    private static final MaterialSubtypeInterpreter INSTANCE = new MaterialSubtypeInterpreter();

    public static MaterialSubtypeInterpreter getInstance()
    {
        return INSTANCE;
    }

    private MaterialSubtypeInterpreter()
    {
    }

    @Override
    @Nullable
    public Object getSubtypeData(final ItemStack itemStack, final UidContext context)
    {
        return itemStack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
            .properties()
            .get(Constants.TYPE_BLOCK_PROPERTY);
    }
}
