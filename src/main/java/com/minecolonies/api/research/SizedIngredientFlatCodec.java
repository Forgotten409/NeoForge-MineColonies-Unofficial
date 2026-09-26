package com.minecolonies.api.research;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * PORT26: NeoForge removed {@code SizedIngredient#FLAT_CODEC}.
 * <p>
 * MineColonies research data (both the datapack JSONs and the saved colony NBT) uses the flat
 * form where the ingredient fields live at the top level next to an optional {@code count} field,
 * e.g. {@code {"item": "minecraft:gold_ingot", "count": 2}} or {@code {"tag": "minecraft:planks", "count": 8}}.
 * <p>
 * This codec reimplements that format on top of the current {@link Ingredient#CODEC} (plus
 * {@link SizedIngredient#NESTED_CODEC} as an accepted input form for robustness), so the existing
 * data files keep parsing unchanged.
 * <p>
 * PORT26 (crash fix #7 / batch 34): the 26.1.2 {@link Ingredient#CODEC} no longer accepts the
 * legacy object forms {@code {"item": ...}} / {@code {"tag": ...}} (vanilla HolderSet codec only
 * takes item id strings, {@code "#tag"} strings or custom ingredients keyed by
 * {@code neoforge:ingredient_type}). All ~315 research JSONs still use the legacy flat objects,
 * so the decode path rewrites them to the new string form before delegating. This keeps the
 * research data loadable without rewriting 200+ data files.
 */
public final class SizedIngredientFlatCodec
{
    private static final String COUNT_KEY = "count";
    private static final String ITEM_KEY = "item";
    private static final String TAG_KEY = "tag";

    /**
     * Replacement for the removed {@code SizedIngredient#FLAT_CODEC}.
     * <p>
     * Accepted inputs:
     * <ul>
     *     <li>Any plain ingredient value (item id string, {@code "#tag"} string, array, or the
     *     legacy object forms {@code {"item": ...}} / {@code {"tag": ...}}) — count defaults to 1.</li>
     *     <li>The flat MineColonies form: ingredient object with a sibling {@code "count": N} key.</li>
     *     <li>The NeoForge nested form {@code {"ingredient": ..., "count": N}}.</li>
     * </ul>
     */
    public static final Codec<SizedIngredient> FLAT_CODEC = new Codec<>()
    {
        @Override
        public <T> DataResult<Pair<SizedIngredient, T>> decode(final DynamicOps<T> ops, final T input)
        {
            // PORT26 (crash fix #7 / batch 34): research JSONs still use the legacy flat objects;
            // rewrite {"item": "X"} → "X" and {"tag": "X"} → "#X" (after count stripping) before parsing.
            final DataResult<Pair<SizedIngredient, T>> flat = stripCount(ops, input).flatMap(pair -> Ingredient.CODEC
                                                                          .decode(ops, rewriteLegacyIngredientObject(ops, pair.getFirst()))
                                                                          .map(ingredientPair -> Pair.of(
                                                                            new SizedIngredient(ingredientPair.getFirst(), pair.getSecond()),
                                                                            ingredientPair.getSecond())));
            final DataResult<Pair<SizedIngredient, T>> nested = SizedIngredient.NESTED_CODEC.decode(ops, input);
            return flat.result().map(DataResult::success).orElse(nested);
        }

        @Override
        public <T> DataResult<T> encode(final SizedIngredient input, final DynamicOps<T> ops, final T prefix)
        {
            if (input.count() == 1)
            {
                return Ingredient.CODEC.encode(input.ingredient(), ops, prefix);
            }
            // Encode multi-count ingredients in the nested form; decode accepts it as well.
            return SizedIngredient.NESTED_CODEC.encode(input, ops, prefix);
        }
    };

    private SizedIngredientFlatCodec()
    {
        // Static utility class
    }

    /**
     * Removes the {@code count} key from the input map (if the input is a map that has one) and
     * returns the cleaned input together with the extracted count. Non-map inputs (plain string
     * or array ingredients) and maps without a {@code count} key are passed through unchanged
     * with a count of 1.
     */
    private static <T> DataResult<Pair<T, Integer>> stripCount(final DynamicOps<T> ops, final T input)
    {
        final var mapOpt = ops.getMap(input).result();
        if (mapOpt.isEmpty())
        {
            return DataResult.success(Pair.of(input, 1));
        }

        final MapLike<T> mapLike = mapOpt.get();
        final T countValue = mapLike.get(COUNT_KEY);
        if (countValue == null)
        {
            return DataResult.success(Pair.of(input, 1));
        }

        final int count = ops.getNumberValue(countValue).map(Number::intValue).result().orElse(1);
        final Map<T, T> cleaned = new LinkedHashMap<>();
        // PORT26: MapLike#entries() now returns a Stream instead of a List.
        for (final Pair<T, T> entry : mapLike.entries().toList())
        {
            final String key = ops.getStringValue(entry.getFirst()).result().orElse(null);
            if (COUNT_KEY.equals(key))
            {
                continue;
            }
            cleaned.put(entry.getFirst(), entry.getSecond());
        }
        return DataResult.success(Pair.of(ops.createMap(cleaned), Math.max(1, count)));
    }

    /**
     * PORT26 (crash fix #7 / batch 34): rewrites the legacy 1.21.1 ingredient object forms to the
     * 26.1.2 string form, generically for any ops:
     * <ul>
     *     <li>{@code {"item": "X"}} → {@code "X"}</li>
     *     <li>{@code {"tag": "X"}} → {@code "#X"}</li>
     * </ul>
     * Maps that carry other keys (new-format custom ingredients with {@code neoforge:ingredient_type},
     * nested {@code {"ingredient": ...}} forms, …) are returned unchanged so the downstream codecs
     * handle them.
     */
    private static <T> T rewriteLegacyIngredientObject(final DynamicOps<T> ops, final T input)
    {
        final var mapOpt = ops.getMap(input).result();
        if (mapOpt.isEmpty())
        {
            return input;
        }
        final MapLike<T> mapLike = mapOpt.get();
        final T itemValue = mapLike.get(ITEM_KEY);
        final T tagValue = mapLike.get(TAG_KEY);
        final int entryCount = mapLike.entries().toList().size();

        if (entryCount == 1 && itemValue != null)
        {
            final var item = ops.getStringValue(itemValue).result();
            if (item.isPresent())
            {
                return ops.createString(item.get());
            }
        }
        if (entryCount == 1 && tagValue != null)
        {
            final var tag = ops.getStringValue(tagValue).result();
            if (tag.isPresent())
            {
                return ops.createString("#" + tag.get());
            }
        }
        return input;
    }
}
