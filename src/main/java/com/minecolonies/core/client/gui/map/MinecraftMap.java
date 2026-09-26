package com.minecolonies.core.client.gui.map;

import com.ldtteam.blockui.BOGuiGraphics;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneParams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Simple minecraft map element.
 */
public class MinecraftMap extends Pane
{
    public static final int MAP_SIZE = 128;
    public static final int MAP_CENTER = 64;

    private MapItemSavedData mapData;
    private MapId mapId;

    /** PORT26: reusable render state — MapRenderer works through extractRenderState + GuiGraphics#map now. */
    private final MapRenderState mapRenderState = new MapRenderState();

    /**
     * Default Constructor.
     */
    public MinecraftMap()
    {
        super();
    }

    /**
     * Constructor used by the xml loader.
     *
     * @param params PaneParams loaded from the xml.
     */
    public MinecraftMap(final PaneParams params)
    {
        super(params);
    }

    /**
     * Set the fitting map data.
     * @param mapData the mapData to set.
     */
    public void setMapData(final MapId mapId, final MapItemSavedData mapData)
    {
        this.mapId = mapId;
        this.mapData = mapData;
    }

    /**
     * Draw this image on the GUI.
     *
     * @param mx Mouse x (relative to parent)
     * @param my Mouse y (relative to parent)
     */
    @Override
    public void drawSelf(final BOGuiGraphics ms, final double mx, final double my)
    {
        if (mapData != null)
        {
            // PORT26: map rendering moved to the extract-render-state flow:
            //  - MapRenderer lives on Minecraft now (GameRenderer#getMapRenderer is gone),
            //  - render(pose, bufferSource, mapId, data, showOnlyFrame, light) is gone —
            //    extract the state first, then draw it via GuiGraphicsExtractor#map
            //    (which draws the map texture + frame-only decorations — exactly the old
            //    showOnlyFrame=true behavior),
            //  - the immediate flush is obsolete (deferred extraction pipeline),
            //  - Matrix3x2fStack uses pushMatrix/popMatrix and 2-arg translate.
            ms.pose().pushMatrix();
            ms.pose().translate(x, y);
            ms.pose().scale(getWidth() / MAP_SIZE, getHeight() / MAP_SIZE);

            Minecraft.getInstance().getMapRenderer().extractRenderState(mapId, mapData, mapRenderState);
            ms.gui().map(mapRenderState);

            ms.pose().popMatrix();
        }
    }
}
