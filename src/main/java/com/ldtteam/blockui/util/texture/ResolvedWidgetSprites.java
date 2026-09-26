package com.ldtteam.blockui.util.texture;

import com.ldtteam.blockui.UiRenderMacros.ResolvedBlit;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Just as {@link WidgetSprites} but resolved
 */
public record ResolvedWidgetSprites(ResolvedBlit enabled,
    ResolvedBlit disabled,
    ResolvedBlit enabledFocused,
    ResolvedBlit disabledFocused)
{
    public static float FOCUSED_MODULATOR = 1.1f;
    public static float DISABLED_MODULATOR = 0.5f;
    public static float DISABLED_FOCUSED_MODULATOR = 0.6f;

    /** untinted marker — white, fully opaque */
    private static final int UNTINTED = 0xFFFFFFFF;

    /**
     * @return resolve given sprites using given resolver
     */
    public static ResolvedWidgetSprites fromUnresolved(final WidgetSprites widgetSprites,
        final Function<Identifier, ResolvedBlit> resolver)
    {
        final Map<Identifier, ResolvedBlit> resolved = new HashMap<>();
        final ResolvedBlit defaultEnabledBlit = resolver.apply(Objects.requireNonNull(widgetSprites.enabled(), "Forgot to put null check somewhere?"));
        resolved.put(null, defaultEnabledBlit);
        resolved.put(widgetSprites.enabled(), defaultEnabledBlit);

        return new ResolvedWidgetSprites(defaultEnabledBlit,
            resolved.computeIfAbsent(widgetSprites.disabled(), resolver),
            resolved.computeIfAbsent(widgetSprites.enabledFocused(), resolver),
            resolved.computeIfAbsent(widgetSprites.disabledFocused(), resolver));
    }

    /**
     * @param isEnabled whether element is interactive
     * @param isFocused whether element is hovered/focused
     * @return correct blit for the given state (side-effect free — pair with {@link #getTint})
     */
    public ResolvedBlit getAndPrepare(final boolean isEnabled, final boolean isFocused)
    {
        if (isEnabled)
        {
            if (isFocused)
            {
                return enabledFocused;
            }
            else
            {
                return enabled;
            }
        }
        else
        {
            if (isFocused)
            {
                return disabledFocused;
            }
            else
            {
                return disabled;
            }
        }
    }

    /**
     * ARGB tint for the given state — the 26.1.2 replacement for the old global
     * {@code RenderSystem#setShaderColor} modulators, applied per-blit through
     * {@code BOGuiGraphics#withBlitTint}.
     *
     * <p>Exactly like 1.21.1: the tint is only returned when the state-specific sprite is
     * missing (falls back to the enabled sprite, same instance) — the color then signals
     * the state instead of a distinct texture.
     *
     * @param isEnabled whether element is interactive
     * @param isFocused whether element is hovered/focused
     * @return ARGB tint, {@code 0xFFFFFFFF} when untinted
     */
    public int getTint(final boolean isEnabled, final boolean isFocused)
    {
        if (isEnabled)
        {
            if (isFocused)
            {
                return sameTint(enabled, enabledFocused, FOCUSED_MODULATOR);
            }
            else
            {
                return UNTINTED;
            }
        }
        else
        {
            if (isFocused)
            {
                return sameTint(enabled, disabledFocused, DISABLED_MODULATOR);
            }
            else
            {
                return sameTint(enabled, disabled, DISABLED_MODULATOR);
            }
        }
    }

    /**
     * @return tint for the modulator when both blits are the same instance (missing
     *         state-specific sprite), untinted otherwise
     */
    private static int sameTint(final ResolvedBlit a, final ResolvedBlit b, final float modulator)
    {
        if (a != b)
        {
            return UNTINTED;
        }

        final int channel = Math.min(255, Math.round(255.0f * modulator));
        return 0xFF000000 | (channel << 16) | (channel << 8) | channel;
    }
}
