package com.ldtteam.blockui.controls;

import com.ldtteam.blockui.BOGuiGraphics;
import com.ldtteam.blockui.PaneParams;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Useful for overriding things like clock/compass textures. In xml defined through {@value #PARAM_PROPERTIES} key using nbt:
 * {<item registry key>:{<property name>:<float value>, ...}, ...}.
 * <p>
 * Special keys: {@value #NBT_CURRENT_ITEM} - refers to xml item (resolved during parsing not dynamic),
 * {@value #NBT_GENERIC_KEY} - generic properties
 * <p>
 * PORT26 (26.1.2): {@code net.minecraft.client.renderer.item.ItemProperties} and
 * {@code ItemPropertyFunction} were removed as part of the 1.21.4+ item model rework
 * (item model variants are now resolved via the {@code assets/<ns>/items/<item>.json}
 * definitions and cannot be overridden at draw time). The xml format is still parsed and
 * the overrides are kept in memory for API compatibility; rendering delegates to the
 * vanilla item model resolver, which picks model variants (compass/clock etc.) on its
 * own. The stored values are therefore inert by design, not by omission.
 */
@SuppressWarnings("deprecation")
public class ItemIconWithProperties extends ItemIcon
{
    private static final String NBT_GENERIC_KEY = "_generic";
    private static final String NBT_CURRENT_ITEM = "_item";

    public static final String PARAM_PROPERTIES = "properties";

    /** property name -> fixed float value (would be passed to the removed item property system) */
    protected final Map<Identifier, Float> genericPropertyOverrides = new HashMap<>();
    protected final Map<Item, Map<Identifier, Float>> itemPropertyOverrides = new HashMap<>();

    public ItemIconWithProperties()
    {
        super();
    }

    public ItemIconWithProperties(final PaneParams paneParams)
    {
        super(paneParams);

        final String data = paneParams.getString(PARAM_PROPERTIES);
        if (data != null && itemStack != null)
        {
            final CompoundTag tag;
            try
            {
                tag = TagParser.parseCompoundFully(data);
            }
            catch (CommandSyntaxException e)
            {
                throw new RuntimeException(data, null);
            }
            tag.keySet().forEach(itemKey -> {
                if (tag.get(itemKey) instanceof final CompoundTag child)
                {
                    final var itemOverrides = NBT_GENERIC_KEY.equals(itemKey) ? genericPropertyOverrides :
                        itemPropertyOverrides.computeIfAbsent(NBT_CURRENT_ITEM.equals(itemKey) ? itemStack.getItem() :
                            BuiltInRegistries.ITEM.getValue(Identifier.parse(itemKey)), i -> new HashMap<>());

                    // PORT26: CompoundTag#getAllKeys -> keySet, contains(key, TAG_ANY_NUMERIC) ->
                    // instanceof NumericTag (typed getter + Optional getters on the new NBT API).
                    child.keySet().forEach(key -> {
                        if (child.get(key) instanceof final NumericTag numericValue)
                        {
                            final float value = numericValue.floatValue(); // intentionally out of lambda
                            itemOverrides.put(Identifier.parse(key), value);
                        }
                    });
                }
            });

            onItemUpdate();
        }
    }

    /**
     * Short call for adding an item property override to the current item.
     * PORT26: the item property system is gone (see class javadoc) — this only stores
     * the value for API compatibility.
     */
    public void addPropertyForCurrentItem(final Identifier propertyKey, final float value)
    {
        itemPropertyOverrides
            .computeIfAbsent(Objects.requireNonNull(itemStack, "Call #setItem before this method").getItem(), item -> new HashMap<>())
            .put(propertyKey, value);
    }

    /**
     * @return modifiable all item-based overrides
     */
    public Map<Item, Map<Identifier, Float>> getItemPropertyOverrides()
    {
        return itemPropertyOverrides;
    }

    /**
     * @return modifiable generic overrides
     */
    public Map<Identifier, Float> getGenericPropertyOverrides()
    {
        return genericPropertyOverrides;
    }

    @Override
    public void drawSelf(final BOGuiGraphics target, final double mx, final double my)
    {
        // PORT26: property overrides are inert (see class javadoc) — vanilla resolves
        // model variants itself
        super.drawSelf(target, mx, my);
    }
}
