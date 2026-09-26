package com.minecolonies.core.datalistener;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.minecolonies.api.util.Log;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base for all minecolonies JSON reload listeners.
 *
 * <p>PORT26: {@code SimpleJsonResourceReloadListener} became generic
 * ({@code SimpleJsonResourceReloadListener<T>} with a codec) and lost the
 * {@code (Gson, String)} constructor, while the NeoForge-patched
 * {@code getRegistryLookup()}/{@code getContext()} methods moved to
 * {@link ContextAwareReloadListener}. This base keeps the 1.21.1 contract: it
 * scans a plain data-pack directory into a {@code Map<Identifier, JsonElement>}
 * (through the identity {@link ExtraCodecs#JSON} codec, so parsing is still
 * strict JSON with no codec conversion) and hands the raw JSON map to
 * {@link #apply}, while getting the reload context and registry lookup injected
 * by NeoForge exactly like the old patched behavior.</p>
 */
public abstract class BaseContextListener extends ContextAwareReloadListener
{
    /**
     * The data pack directory (prefix) this listener scans.
     */
    private final FileToIdConverter lister;

    /**
     * Create a new listener scanning the given directory.
     *
     * @param directory the directory name where to look for json files.
     */
    protected BaseContextListener(final String directory)
    {
        this.lister = FileToIdConverter.json(directory);
    }

    @Override
    public CompletableFuture<Void> reload(
        final PreparableReloadListener.SharedState currentReload,
        final Executor taskExecutor,
        final PreparableReloadListener.PreparationBarrier preparationBarrier,
        final Executor reloadExecutor)
    {
        final ResourceManager manager = currentReload.resourceManager();
        return CompletableFuture.supplyAsync(() -> {
            final Map<Identifier, JsonElement> map = new HashMap<>();
            SimpleJsonResourceReloadListener.scanDirectory(manager, this.lister, JsonOps.INSTANCE, ExtraCodecs.JSON, map);
            return map;
        }, taskExecutor)
            .thenCompose(preparationBarrier::wait)
            .thenAcceptAsync(preparations -> {
                // PORT26 (crash fix #7 / batch 34): In 26.1.2 item components are only bound to the
                // registry holders AFTER the whole datapack reload finishes
                // (ReloadableServerResources#updateComponentsAndStaticRegistryTags, called from
                // WorldLoader/MinecraftServer once SimpleReloadInstance completes). MineColonies
                // listeners however build ItemStacks directly inside apply() (crafterrecipes
                // inputs/results, recruit costs, quest item rewards) — which threw
                // "Item minecraft:sand does not have components yet" and killed the reload.
                // Binding the pending components here is safe and idempotent: PendingComponents#apply
                // only assigns the component map on each holder, and vanilla's later binding pass
                // writes the exact same data again (build() reads the static
                // DATA_COMPONENT_INITIALIZERS registry + the same registry lookup NeoForge injects
                // into this listener).
                ensureComponentsBoundEarly(getRegistryLookup());
                this.apply(preparations, manager, Profiler.get());
            }, reloadExecutor);
    }

    /** Whether the early component binding already ran successfully in this JVM. */
    private static final AtomicBoolean COMPONENTS_BOUND_EARLY = new AtomicBoolean(false);

    /**
     * Binds the data-driven item components to the registry holders before this listener's apply
     * runs. Executed at most once per JVM (components stay bound across datapack reloads).
     */
    private static void ensureComponentsBoundEarly(final HolderLookup.Provider provider)
    {
        if (COMPONENTS_BOUND_EARLY.get() || provider == null)
        {
            return;
        }
        // PORT26 (crash fix #9): only the SERVER-side reload has the datapack registries + tags
        // available this early (RegistryDataLoader/ReloadableServerRegistries load damage_type
        // BEFORE the resource reload; the client only receives them at world join through
        // RegistryDataCollector, where it also binds the components itself). Vanilla's
        // fireResistant component initializer resolves DamageTypeTags.IS_FIRE — attempting the
        // binding with the client menu lookup failed with "Missing tag ... is_fire" on every
        // client reload, so skip silently when the damage_type registry is not present.
        if (provider.lookup(Registries.DAMAGE_TYPE).isEmpty())
        {
            return;
        }
        try
        {
            final List<DataComponentInitializers.PendingComponents<?>> pending =
              BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider);
            pending.forEach(DataComponentInitializers.PendingComponents::apply);
            COMPONENTS_BOUND_EARLY.set(true);
            Log.getLogger().info("Early-bound item/block/fluid components for datapack parsing (crash fix #7)");
        }
        catch (final Exception e)
        {
            // Fall back to the vanilla binding moment (post-reload); recipes that need components
            // will then fail individually instead of hard-crashing the whole reload.
            Log.getLogger().warn("Early component binding failed; relying on post-reload binding instead", e);
        }
    }

    /**
     * Handle the loaded JSON entries (same contract as the 1.21.1
     * {@code SimpleJsonResourceReloadListener#apply}).
     *
     * @param jsonElementMap the map of resource ids to raw json.
     * @param resourceManager the resource manager.
     * @param profiler the profiler.
     */
    protected abstract void apply(Map<Identifier, JsonElement> jsonElementMap, ResourceManager resourceManager, ProfilerFiller profiler);
}
