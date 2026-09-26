package com.minecolonies.core.datalistener;

import com.google.gson.*;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Log;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Loads and listens to get custom nbt matching rules.
 */
public class ItemNbtListener extends BaseContextListener
{
    /**
     * Create a new listener.
     */
    public ItemNbtListener()
    {
        super("compatibility");
    }

    @Override
    protected void apply(final Map<Identifier, JsonElement> jsonElementMap, final @NotNull ResourceManager resourceManager, final @NotNull ProfilerFiller profiler)
    {
        ItemStackUtils.CHECKED_NBT_KEYS.clear();
        for (final Map.Entry<Identifier, JsonElement> entry : jsonElementMap.entrySet())
        {
            tryParse(this.getRegistryLookup(), entry);
        }
    }

    /**
     * Tries to parse the entry
     *
     * PORT26 (crash fix #11): {@code Registry#getValue} returns {@code null} for unknown ids
     * WITHOUT throwing — a renamed/removed data component type (26.1.2 dropped
     * {@code fire_resistant} and {@code hide_additional_tooltip}) silently stored nulls in
     * CHECKED_NBT_KEYS, which crashed the login packet later ("Can't find id for 'null' in map
     * Registry[data_component_type]"). Unknown ids are now skipped with a warning instead.
     *
     * @param entry
     */
    private void tryParse(@NotNull final HolderLookup.Provider provider, final Map.Entry<Identifier, JsonElement> entry)
    {
        for (final JsonElement element : entry.getValue().getAsJsonArray())
        {
            try
            {
                final JsonObject jsonObj = element.getAsJsonObject();
                final Identifier itemLoc = Identifier.parse(jsonObj.get("item").getAsString());
                final Item item = BuiltInRegistries.ITEM.getValue(itemLoc);
                if (item == null)
                {
                    Log.getLogger().warn("Unknown item '" + itemLoc + "' in " + entry.getKey() + " — skipping entry.");
                    continue;
                }
                if (jsonObj.has("checkednbtkeys"))
                {
                    final HashSet<DataComponentType<?>> set = new HashSet<>();
                    final JsonArray jsonArray = jsonObj.getAsJsonArray("checkednbtkeys");
                    for (final JsonElement subElement : jsonArray)
                    {
                        final Identifier compLoc = Identifier.parse(subElement.getAsString());
                        final DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(compLoc);
                        if (type == null)
                        {
                            Log.getLogger().warn("Unknown data component type '" + compLoc + "' for item " + itemLoc + " in " + entry.getKey()
                                                   + " — skipping component (check for renamed vanilla components).");
                            continue;
                        }
                        set.add(type);
                    }

                    ItemStackUtils.CHECKED_NBT_KEYS.put(item, set);
                }
                else
                {
                    ItemStackUtils.CHECKED_NBT_KEYS.put(item, new HashSet<>());
                }
            }
            catch (Exception e)
            {
                Log.getLogger().warn("Could not nbt comparator for:" + entry.getKey(), e);
            }
        }
        Log.getLogger().warn("Read " + ItemStackUtils.CHECKED_NBT_KEYS.size() + " items with their nbt keys for compatibility.");
    }
}
