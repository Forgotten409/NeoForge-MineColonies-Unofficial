package com.minecolonies.core.compatibility.journeymap;

import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.jobs.registry.IJobRegistry;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.visitor.VisitorCitizen;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin; // PORT26-JMAP: client.JourneyMapPlugin annotation is deprecated for removal in JM 26.2 — the common one is the 1:1 replacement (apiVersion + defaulted dependencies/require); PluginHelper scans BOTH
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.entity.WrappedEntity;
import journeymap.api.v2.client.event.EntityRadarUpdateEvent;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.event.RegistryEvent;
import journeymap.api.v2.common.event.ClientEventRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier; // PORT26: ResourceLocation → Identifier (26.1.2 mappings)
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static com.minecolonies.api.entity.citizen.AbstractEntityCitizen.DATA_JOB;
import static com.minecolonies.api.util.constant.Constants.MOD_ID;
import static com.minecolonies.api.util.constant.TranslationConstants.PARTIAL_JOURNEY_MAP_INFO;

/**
 * Plugin entrypoint for JourneyMap
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public class JourneymapPlugin implements IClientPlugin
{
    private static final Style JOB_TOOLTIP = Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(true);

    private Journeymap jmap;
    @SuppressWarnings("unused")
    private EventListener listener;

    @Override
    public void initialize(@NotNull final IClientAPI api)
    {
        this.jmap = new Journeymap(api);
        this.listener = new EventListener(this.jmap);

        ClientEventRegistry.MAPPING_EVENT.subscribe(MOD_ID, this::onMappingEvent);
        ClientEventRegistry.OPTIONS_REGISTRY_EVENT.subscribe(MOD_ID, this::onOptionsRegistryEvent); // PORT26-JMAP: OPTIONS_REGISTRY_EVENT_EVENT is the deprecated legacy alias
        ClientEventRegistry.INFO_SLOT_REGISTRY_EVENT.subscribe(MOD_ID, this::onInfoRegistryEvent); // PORT26-JMAP: INFO_SLOT_REGISTRY_EVENT_EVENT is the deprecated legacy alias
        ClientEventRegistry.ENTITY_RADAR_UPDATE_EVENT.subscribe(MOD_ID, this::onEntityRadarUpdateEvent);

        // PORT26-JMAP (rev2): construct the options EAGERLY, right here. JourneyMap's
        // JourneymapClient#init calls PluginHelper#initPlugins (this method) BEFORE
        // JourneymapClient#loadConfigProperties, which is where
        // OptionsDisplayFactory#buildAddonProperties snapshots OptionsRegistry.OPTION_REGISTRY
        // and reflectively injects the backing Config into every registered Option
        // (journeymap.api.v2.client.option.Option self-registers via its constructor).
        // Creating them only from the (later, advisory) registry event left them out of the
        // snapshot and Option#get() NPE'd; the first-party PokemonOptionsPlugin does the same.
        registerOptions();
    }

    @Override
    public String getModId()
    {
        return MOD_ID;
    }

    private void onMappingEvent(final MappingEvent event)
    {
        // PORT26: MappingEvent dropped the public `dimension` field (ResourceKey) in favor of
        // getWorldId() (string). JourneyMap passes the dimension location ("modid:path") as
        // the world id; parse it back into a dimension key, falling back to the client world
        // when the id isn't a well-formed identifier (ResourceKey equality is location-based,
        // so recreated keys compare equal to the vanilla dimension keys).
        final ResourceKey<Level> dimension = dimensionOf(event);
        switch (event.getStage())
        {
            case MAPPING_STARTED:
                ColonyBorderMapping.load(this.jmap, dimension);
                break;

            case MAPPING_STOPPED:
                ColonyBorderMapping.unload(this.jmap, dimension);
                ColonyDeathpoints.unload(this.jmap, dimension);
                break;
        }
    }

    private static ResourceKey<Level> dimensionOf(final MappingEvent event)
    {
        final String worldId = event.getWorldId();
        if (worldId != null && !worldId.isEmpty())
        {
            try
            {
                return ResourceKey.create(Registries.DIMENSION, Identifier.parse(worldId));
            }
            catch (final Exception ignored)
            {
                // not a valid identifier — fall back to the client dimension below
            }
        }
        final Level level = Minecraft.getInstance().level;
        return level == null ? Level.OVERWORLD : level.dimension();
    }

    private void onOptionsRegistryEvent(final RegistryEvent.OptionsRegistryEvent event)
    {
        // Idempotent fallback: normally the options were already created in initialize();
        // creating them twice would re-register duplicate field entries in JourneyMap's
        // OptionsRegistry while this instance holds a reference to the (unconfigured) copy.
        registerOptions();
    }

    private void registerOptions()
    {
        if (this.jmap.getOptions().isEmpty())
        {
            this.jmap.setOptions(new JourneymapOptions());
            Log.getLogger().info("[JourneyMap compat] client options registered for {}", MOD_ID);
        }
    }

    private void onInfoRegistryEvent(final RegistryEvent.InfoSlotRegistryEvent event)
    {
        event.register(MOD_ID, "com.minecolonies.coremod.journeymap.currentcolony", 2500, ColonyBorderMapping::getCurrentColony);
    }

    @SuppressWarnings("deprecation") // PORT26-JMAP: journeymap.api.v2.client.display.Context$UI is deprecated without a v2 replacement (the whole v2 overlay API references it); keep until JM ships the successor
    private void onEntityRadarUpdateEvent(final EntityRadarUpdateEvent event)
    {
        final WrappedEntity wrapper = event.getWrappedEntity();
        final Entity entity = wrapper.getEntityRef().get();

        if (entity instanceof AbstractEntityCitizen)
        {
            final boolean isVisitor = entity instanceof VisitorCitizen;
            MutableComponent jobName;

            if (isVisitor)
            {
                if (!JourneymapOptions.getShowVisitors(this.jmap.getOptions()))
                {
                    wrapper.setDisable(true);
                    return;
                }

                jobName = Component.translatableEscape(PARTIAL_JOURNEY_MAP_INFO + "visitor");
            }
            else
            {
                final String jobId = entity.getEntityData().get(DATA_JOB);
                final JobEntry jobEntry = jobId.isEmpty() ? null : IJobRegistry.getInstance().getValue(Identifier.parse(jobId)); // PORT26: Registry#get(ResourceLocation) → getValue(Identifier) — @Nullable, like the old get
                final IJob<?> job = jobEntry == null ? null : jobEntry.produceJob(null);

                if (job instanceof AbstractJobGuard
                        ? !JourneymapOptions.getShowGuards(this.jmap.getOptions())
                        : !JourneymapOptions.getShowCitizens(this.jmap.getOptions()))
                {
                    wrapper.setDisable(true);
                    return;
                }

                jobName = Component.translatableEscape(jobEntry == null
                        ? PARTIAL_JOURNEY_MAP_INFO + "unemployed"
                        : jobEntry.getTranslationKey());
            }

            if (JourneymapOptions.getShowColonistTooltip(this.jmap.getOptions()))
            {
                Component name = entity.getCustomName();
                if (name != null)
                {
                    wrapper.setEntityToolTips(Arrays.asList(name, jobName.setStyle(JOB_TOOLTIP)));
                }
            }

            final boolean showName = event.getActiveUiState().ui.equals(Context.UI.Minimap)
                    ? JourneymapOptions.getShowColonistNameMinimap(this.jmap.getOptions())
                    : JourneymapOptions.getShowColonistNameFullscreen(this.jmap.getOptions());

            if (!showName)
            {
                wrapper.setCustomName("");
            }

            if (!isVisitor && JourneymapOptions.getShowColonistTeamColour(this.jmap.getOptions()))
            {
                wrapper.setColor(entity.getTeamColor());
            }
        }
        else if (entity instanceof AbstractEntityMinecoloniesRaider)
        {
            final JourneymapOptions.RaiderColor color = JourneymapOptions.getRaiderColor(this.jmap.getOptions());

            if (JourneymapOptions.RaiderColor.NONE.equals(color))
            {
                wrapper.setDisable(true);
            }
            else if (!JourneymapOptions.RaiderColor.HOSTILE.equals(color))
            {
                wrapper.setColor(color.getColor().getValue());
            }
        }
    }
}
