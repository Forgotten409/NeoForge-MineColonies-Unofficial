package com.minecolonies.core.client.gui.huts;

import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.gui.AbstractWindowWorkerModuleBuilding;
import com.minecolonies.core.client.gui.WindowHutGuide;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.resources.Identifier;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.Optional;

import static com.minecolonies.api.util.constant.WindowConstants.*;

/**
 * BOWindow for the builder hut.
 */
public class WindowHutBuilderModule extends AbstractWindowWorkerModuleBuilding<BuildingBuilder.View>
{
    /**
     * The advancement location.
     */

    private static final Identifier GUIDE_ADVANCEMENT = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "minecolonies/check_out_guide");

    /**
     * PORT26: ClientAdvancements#progress became private with no accessor. The guide check
     * needs a one-shot "is this advancement done" answer — read the map through a VarHandle
     * (fail-soft: if the field cannot be located, treat the advancement as NOT done, which
     * keeps the guide opening — the pre-26.1 default experience for new players anyway).
     */
    private static final VarHandle PROGRESS_HANDLE = resolveProgressHandle();

    private static VarHandle resolveProgressHandle()
    {
        try
        {
            final var lookup = MethodHandles.privateLookupIn(ClientAdvancements.class, MethodHandles.lookup());
            return lookup.findVarHandle(ClientAdvancements.class, "progress", Map.class);
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            Log.getLogger().warn("Unable to access ClientAdvancements#progress — guide-done check disabled", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean isAdvancementDone(final ClientAdvancements advancements)
    {
        if (PROGRESS_HANDLE == null)
        {
            return false;
        }
        final Map<AdvancementHolder, AdvancementProgress> progress =
            (Map<AdvancementHolder, AdvancementProgress>) (Map<?, ?>) PROGRESS_HANDLE.get(advancements);
        if (progress == null)
        {
            return false;
        }
        final AdvancementHolder holder = advancements.get(GUIDE_ADVANCEMENT);
        return Optional.ofNullable(holder)
            .flatMap(h -> Optional.ofNullable(progress.get(h)))
            .map(AdvancementProgress::isDone)
            .orElse(false);
    }

    /**
     * If the guide should be attempted to be opened.
     */
    private final boolean needGuide;

    /**
     * Constructor for window builder hut.
     *
     * @param building {@link BuildingBuilder.View}.
     */
    public WindowHutBuilderModule(final BuildingBuilder.View building)
    {
        this(building, true);
    }

    /**
     * Constructor for window builder hut.
     *
     * @param needGuide if the guide should be opened.
     * @param building  {@link BuildingBuilder.View}.
     */
    public WindowHutBuilderModule(final BuildingBuilder.View building, final boolean needGuide)
    {
        super(building, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "gui/windowhutworkerplaceholder.xml"));
        this.needGuide = needGuide;
    }

    @Override
    public void onOpened()
    {
        if (needGuide)
        {
            if (!isAdvancementDone(Minecraft.getInstance().player.connection.getAdvancements()))
            {
                close();
                new WindowHutGuide(buildingView).open();
                return;
            }
        }
        super.onOpened();
    }
}
