package com.minecolonies.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * Key mappings
 */
public class ModKeyMappings
{
    /**
     * PORT26: key mapping categories changed from translation-key strings to the
     * {@link KeyMapping.Category} record keyed by an {@link Identifier}.
     * Label translation key is derived as {@code key.category.<namespace>.<path>}:
     * {@code key.category.minecolonies.categories.general}.
     */
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(MOD_ID, "categories.general"));

    /**
     * Toggle
     */
    public static final Lazy<KeyMapping> TOGGLE_GOGGLES = Lazy.of(() -> new KeyMapping("key.minecolonies.toggle_goggles",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));

    /**
     * Register key mappings
     */
    public static void register(@NotNull final RegisterKeyMappingsEvent event)
    {
        // PORT26: custom categories must be registered through the event.
        event.registerCategory(CATEGORY);
        event.register(TOGGLE_GOGGLES.get());
    }

    /**
     * Private constructor to hide the implicit one.
     */
    private ModKeyMappings()
    {
        /*
         * Intentionally left empty.
         */
    }
}
