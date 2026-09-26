package com.ldtteam.minecolonies.portassets.client;

import java.util.List;

import com.ldtteam.minecolonies.MineColonies;
import com.ldtteam.minecolonies.portassets.AssetNamespace;
import com.ldtteam.minecolonies.portassets.AssetProvisioner;
import com.ldtteam.minecolonies.portassets.PortAssetSettings;
import com.ldtteam.minecolonies.portassets.PortAssets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-side first-run notice controller — PORT26 (publishing support).
 *
 * <p>Once per game session, when the title screen is up and at least one required asset
 * namespace is neither bundled in the running jar (dev builds) nor provisioned in the
 * external store, the {@link PortAssetNoticeScreen} is opened on top of the title screen.</p>
 *
 * <p>Implementation notes:</p>
 * <ul>
 *   <li>The swap is done from {@code ClientTickEvent.Post} (NOT from
 *       {@code ScreenEvent.Init}) so the title screen is fully initialized when it is
 *       replaced — vanilla re-creates a {@code TitleScreen} on {@code setScreen(null)} /
 *       screen close, so returning is trivial and mid-init reentrancy is avoided.</li>
 *   <li>Instance registration on the game bus (singleton), the port's battle-tested pattern
 *       — see {@code DomumOrnamentum.init}'s note about static class registration.</li>
 *   <li>The check is a cheap cached-probe + file-existence test per tick until shown.</li>
 * </ul>
 */
public class PortAssetClientEvents
{
    private static final PortAssetClientEvents INSTANCE = new PortAssetClientEvents();

    /**
     * @return the singleton instance (registered on {@code NeoForge.EVENT_BUS}).
     */
    public static PortAssetClientEvents getInstance()
    {
        return INSTANCE;
    }

    private boolean shownThisSession = false;

    private PortAssetClientEvents()
    {
    }

    @SubscribeEvent
    public void onClientTickPost(final ClientTickEvent.Post event)
    {
        if (shownThisSession)
        {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !(minecraft.screen instanceof TitleScreen))
        {
            return;
        }
        shownThisSession = true;

        final PortAssetSettings settings = PortAssets.getSettings();
        if (!settings.noticeAllowed())
        {
            return;
        }

        final List<AssetNamespace> missing = AssetProvisioner.missingNamespaces();
        if (missing.isEmpty())
        {
            return;
        }

        MineColonies.LOGGER.info("port-assets: {} namespace(s) missing ({}); offering first-run provisioning",
            missing.size(), missing.stream().map(AssetNamespace::modId).toList());
        minecraft.setScreen(new PortAssetNoticeScreen(missing));
    }
}
