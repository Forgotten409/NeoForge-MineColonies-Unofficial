package com.minecolonies.core.client.render.worldevent;

import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.items.component.RallyData;
import net.minecraft.core.BlockPos;

public class GuardTowerRallyBannerRenderer
{
    /**
     * Renders the rallying banner guard tower indicators into the world.
     * 
     * @param ctx rendering context
     */
    static void render(final WorldEventContext ctx)
    {
        if (ctx.mainHandItem.getItem() != ModItems.bannerRallyGuards)
        {
            return;
        }

        final ColonyId component = ColonyId.readFromItemStack(ctx.mainHandItem);
        if (!component.hasColonyId() || component.dimension() != ctx.clientLevel.dimension())
        {
            return;
        }

        for (final BlockPos guardTower : RallyData.readFromItemStack(ctx.mainHandItem).towers())
        {
            ctx.pushPoseCameraToPos(guardTower);
            ctx.renderBlackLineBox(BlockPos.ZERO, BlockPos.ZERO, WorldEventContext.DEFAULT_LINE_WIDTH);
            ctx.popPose();
        }
    }
}
