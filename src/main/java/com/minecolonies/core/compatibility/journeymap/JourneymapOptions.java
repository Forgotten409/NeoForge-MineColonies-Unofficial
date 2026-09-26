package com.minecolonies.core.compatibility.journeymap;

import journeymap.api.v2.client.option.*; // PORT26-JMAP (rev2): MUST stay on client.option.* — see note below
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;
import static com.minecolonies.api.util.constant.TranslationConstants.PARTIAL_JOURNEY_MAP_INFO;

/**
 * PORT26-JMAP (rev2) — why this is back on client.option.*:
 *
 * Bytecode-verified against journeymap-neoforge-26.1.2-6.0.8.jar:
 * - The CLIENT options pipeline is journeymap.api.client.impl.OptionsDisplayFactory: it iterates
 *   OptionsRegistry.OPTION_REGISTRY (journeymap.api.v2.CLIENT.option), calls createField(Option) and
 *   reflectively invokes the private Option#setConfig(Config) before loading values from disk.
 *   The v2 client Option constructor self-registers into OptionsRegistry, so constructing them here is enough.
 * - The COMMON options pipeline is journeymap.api.server.impl.ServerOptionsDisplayFactory — SERVER side only.
 *   A common.option.Option created on the client NEVER receives a Config, so Option#get() NPEs
 *   ("Cannot invoke Config.get() because this.config is null") and crashed the client (see runtime log).
 * - The deprecation notice suggesting common.option as the "replacement" only applies to server-side
 *   options; the first-party sample plugin (journeymap.api.plugins.PokemonOptionsPlugin) shipped in the
 *   same jar also still uses v2 client.option. Migrate again only when JM ships an actual client successor.
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "deprecation"})
public class JourneymapOptions
{
    private final Option<BorderStyle> borderFullscreenStyle;
    private final Option<BorderStyle> borderMinimapStyle;
    private final Option<BorderStyle> borderLoadedStyle;
    private final Option<Boolean> deathpoints;
    private final Option<Boolean> colonyname;
    private final Option<Boolean> colonistNameMinimap;
    private final Option<Boolean> colonistNameFullscreen;
    private final Option<Boolean> colonistTooltips;
    private final Option<Boolean> colonistTeam;
    private final Option<Boolean> guards;
    private final Option<Boolean> citizens;
    private final Option<Boolean> visitors;
    private final Option<RaiderColor> raiders;

    public JourneymapOptions()
    {
        final String prefix = PARTIAL_JOURNEY_MAP_INFO + "options.";
        final OptionCategory category = new OptionCategory(MOD_ID, prefix + "category");

        this.borderFullscreenStyle = new EnumOption<>(category, "borderFullscreenStyle", prefix + "borderfullscreenstyle", BorderStyle.FILLED).setSortOrder(100);
        this.borderMinimapStyle = new EnumOption<>(category, "borderMinimapStyle", prefix + "borderminimapstyle", BorderStyle.FRAMED).setSortOrder(101);
        this.borderLoadedStyle = new EnumOption<>(category, "borderLoadedStyle", prefix + "borderloadedstyle", BorderStyle.HIDDEN).setSortOrder(102);
        this.deathpoints = new BooleanOption(category, "deathpoints", prefix + "deathpoints", true).setSortOrder(150);
        this.colonyname = new BooleanOption(category, "colonyname", prefix + "colonyname", true).setSortOrder(180);
        this.colonistNameMinimap = new BooleanOption(category, "colonistNameMinimap", prefix + "colonistnameminimap", true).setSortOrder(201);
        this.colonistNameFullscreen = new BooleanOption(category, "colonistNameFullscreen", prefix + "colonistnamefullscreen", true).setSortOrder(200);
        this.colonistTooltips = new BooleanOption(category, "colonistTooltips", prefix + "colonisttooltips", true).setSortOrder(202);
        this.colonistTeam = new BooleanOption(category, "colonistTeam", prefix + "colonistteam", true).setSortOrder(203);
        this.guards = new BooleanOption(category, "guards", prefix + "guards", true).setSortOrder(300);
        this.citizens = new BooleanOption(category, "citizens", prefix + "citizens", true).setSortOrder(301);
        this.visitors = new BooleanOption(category, "visitors", prefix + "visitors", true).setSortOrder(302);
        this.raiders = new EnumOption<>(category, "raiders", prefix + "raiders", RaiderColor.HOSTILE).setSortOrder(303);
    }

    /**
     * Reads an option defensively: JourneyMap injects the backing Config reflectively
     * (Option#setConfig) only AFTER the options factory ran. If that ever fails or races
     * (e.g. a future JM build changes the pipeline again), fall back to the declared
     * default value instead of letting Option#get() NPE-crash the client.
     */
    private static <T> T valueOf(@NotNull final Option<T> option, @NotNull final T fallback)
    {
        try
        {
            final T value = option.get();
            if (value != null)
            {
                return value;
            }
        }
        catch (final Throwable ignored)
        {
            // config not injected (yet) — use the declared default below
        }
        final T declaredDefault = option.getDefaultValue();
        return declaredDefault != null ? declaredDefault : fallback;
    }

    public static BorderStyle getBorderFullscreenStyle(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.borderFullscreenStyle, BorderStyle.FILLED)).orElse(BorderStyle.FILLED);
    }

    public static BorderStyle getBorderMinimapStyle(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.borderMinimapStyle, BorderStyle.FRAMED)).orElse(BorderStyle.FRAMED);
    }

    public static BorderStyle getBorderLoadedStyle(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.borderLoadedStyle, BorderStyle.HIDDEN)).orElse(BorderStyle.HIDDEN);
    }

    public static boolean getDeathpoints(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.deathpoints, Boolean.TRUE)).orElse(true);
    }

    public static boolean getShowColonyName(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.colonyname, Boolean.TRUE)).orElse(true);
    }

    public static boolean getShowColonistNameMinimap(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.colonistNameMinimap, Boolean.TRUE)).orElse(true);
    }

    public static boolean getShowColonistNameFullscreen(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.colonistNameFullscreen, Boolean.TRUE)).orElse(true);
    }

    public static boolean getShowColonistTooltip(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.colonistTooltips, Boolean.TRUE)).orElse(true);
    }

    public static boolean getShowColonistTeamColour(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.colonistTeam, Boolean.TRUE)).orElse(true);
    }

    public static boolean getShowGuards(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.guards, Boolean.TRUE)).orElse(true);
    }

    public static boolean getShowCitizens(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.citizens, Boolean.TRUE)).orElse(true);
    }

    public static boolean getShowVisitors(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.visitors, Boolean.TRUE)).orElse(true);
    }

    public static RaiderColor getRaiderColor(@NotNull final Optional<JourneymapOptions> options)
    {
        return options.map(o -> valueOf(o.raiders, RaiderColor.HOSTILE)).orElse(RaiderColor.HOSTILE);
    }

    public enum BorderStyle implements KeyedEnum
    {
        HIDDEN(PARTIAL_JOURNEY_MAP_INFO + "borderstyle.hidden"),
        FRAMED(PARTIAL_JOURNEY_MAP_INFO + "borderstyle.framed"),
        FILLED(PARTIAL_JOURNEY_MAP_INFO + "borderstyle.filled");

        private final String key;

        BorderStyle(final String key)
        {
            this.key = key;
        }

        @Override
        public String getKey()
        {
            return this.key;
        }
    }

    public enum RaiderColor implements KeyedEnum
    {
        HOSTILE(PARTIAL_JOURNEY_MAP_INFO + "raidercolor.hostile", TextColor.fromRgb(0xFFFFFFFF)),
        NONE(PARTIAL_JOURNEY_MAP_INFO + "raidercolor.none", TextColor.fromRgb(0xFF000000)),
        YELLOW(PARTIAL_JOURNEY_MAP_INFO + "raidercolor.yellow", TextColor.fromLegacyFormat(ChatFormatting.YELLOW)),
        RED(PARTIAL_JOURNEY_MAP_INFO + "raidercolor.red", TextColor.fromLegacyFormat(ChatFormatting.RED)),
        PURPLE(PARTIAL_JOURNEY_MAP_INFO + "raidercolor.purple", TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE)),
        ORANGE(PARTIAL_JOURNEY_MAP_INFO + "raidercolor.orange", TextColor.fromLegacyFormat(ChatFormatting.GOLD));

        private final String key;
        private final TextColor color;

        RaiderColor(final String key, final TextColor color)
        {
            this.key = key;
            this.color = color;
        }

        @Override
        public String getKey()
        {
            return this.key;
        }

        @NotNull
        public TextColor getColor()
        {
            return this.color;
        }
    }
}
