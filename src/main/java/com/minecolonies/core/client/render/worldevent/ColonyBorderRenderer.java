package com.minecolonies.core.client.render.worldevent;

import com.ldtteam.blockui.util.color.ColourARGB;
import com.ldtteam.blockui.util.color.ColourQuartet;
import com.ldtteam.blockui.util.color.ColouredVertexConsumer;
import com.ldtteam.blockui.util.color.IColour;
import com.ldtteam.structurize.items.ModItems;
import com.ldtteam.structurize.util.WorldRenderMacros;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.core.MineColonies;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
// PORT26: Screen#hasControlDown moved to Minecraft#hasControlDown.
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

public class ColonyBorderRenderer
{
    private static final int RENDER_DIST_THRESHOLD = 3;
    private static final int CHUNK_SIZE = 16;
    private static final int PLAYER_CHUNK_STEP = CHUNK_SIZE / 4;

    // PORT26: VertexBuffer (upload + drawWithShader) no longer exists — the border geometry
    // is cached as chunk→colony maps and re-emitted into the shared buffer source every
    // frame. The LINES batch is flushed by structurize's LOWEST-priority finishBuffers hook.
    private static Map<ChunkPos, Integer> coloniesMap     = null;
    private static Map<ChunkPos, Integer> chunkticketsMap = null;
    private static ChunkPos               lastPlayerChunkPos = null;
    private static IColonyView            lastColony = null;
    private static int                    lastNearestColonyId = 0;
    private static int                    lastPlayerRenderDist = 0;

    static void render(final WorldEventContext ctx)
    {
        if (ctx.mainHandItem.getItem() != ModItems.buildTool.get() || !ctx.hasNearestColony())
        {
            return;
        }

        // PORT26: ChunkPos(BlockPos) ctor removed → ChunkPos.containing(BlockPos).
        final ChunkPos playerChunkPos = ChunkPos.containing(ctx.clientPlayer.blockPosition());

        if (lastColony != ctx.nearestColony || !lastPlayerChunkPos.equals(playerChunkPos))
        {
            lastColony = ctx.nearestColony;
            lastPlayerChunkPos = playerChunkPos;

            final Map<ChunkPos, Integer> newColoniesMap = new HashMap<>();
            final Map<ChunkPos, Integer> newChunkticketsMap = new HashMap<>();
            final int nearestColonyId = ctx.nearestColony.getID();
            final int playerRenderDist = Math.max(ctx.clientRenderDist - RENDER_DIST_THRESHOLD, 2);
            final int range = Math.max(ctx.clientRenderDist, MineColonies.getConfig().getServer().maxColonySize.get());

            for (int chunkX = -range; chunkX <= range; chunkX++)
            {
                for (int chunkZ = -range; chunkZ <= range; chunkZ++)
                {
                    final LevelChunk chunk = ctx.clientLevel.getChunk(playerChunkPos.x() + chunkX, playerChunkPos.z() + chunkZ);
                    if (chunk.isEmpty()) { continue; }
                    final ChunkPos chunkPos = chunk.getPos();

                    final IChunkClaimData cap = IColonyManager.getInstance().getClaimData(ctx.nearestColony.getDimension(), chunkPos);;
                    if (cap != null)
                    {
                        newColoniesMap.put(chunkPos, cap.getOwningColony());
                    }

                    if (ctx.nearestColony.getTicketedChunks().contains(chunkPos.pack()))
                    {
                        newChunkticketsMap.put(chunkPos, nearestColonyId);
                    }
                    else
                    {
                        newChunkticketsMap.put(chunkPos, 0);
                    }
                }
            }

            coloniesMap = newColoniesMap;
            chunkticketsMap = newChunkticketsMap;
            lastNearestColonyId = nearestColonyId;
            lastPlayerRenderDist = playerRenderDist;
        }

        final Map<ChunkPos, Integer> mapToDraw = Minecraft.getInstance().hasControlDown() ? chunkticketsMap : coloniesMap;
        if (mapToDraw == null || mapToDraw.isEmpty())
        {
            return;
        }

        ctx.pushPoseCameraToPos(lastPlayerChunkPos.getWorldPosition());
        draw(ctx, mapToDraw, lastNearestColonyId, playerChunkPos, lastPlayerRenderDist, ctx.poseStack.last());
        ctx.popPose();
    }

    private static void draw(final WorldEventContext ctx,
        final Map<ChunkPos, Integer> mapToDraw,
        final int playerColonyId,
        final ChunkPos playerChunkPos,
        final int playerRenderDist,
        final PoseStack.Pose pose)
    {
        final Map<Integer, IColour> colonyColours = new HashMap<>();
        final boolean useColonyColour = IMinecoloniesAPI.getInstance().getConfig().getClient().colonyteamborders.get();

        final ColouredVertexConsumer buf = new ColouredVertexConsumer(ctx.bufferSource.getBuffer(WorldRenderMacros.LINES));
        mapToDraw.forEach((chunkPos, colonyId) -> {
            if (colonyId == 0 || chunkPos.x() <= playerChunkPos.x() - playerRenderDist || chunkPos.x() >= playerChunkPos.x() + playerRenderDist
                || chunkPos.z() <= playerChunkPos.z() - playerRenderDist || chunkPos.z() >= playerChunkPos.z() + playerRenderDist)
            {
                return;
            }

            final boolean isPlayerChunkX = colonyId == playerColonyId && chunkPos.x() == playerChunkPos.x();
            final boolean isPlayerChunkZ = colonyId == playerColonyId && chunkPos.z() == playerChunkPos.z();
            final float minX = chunkPos.getMinBlockX() - playerChunkPos.getMinBlockX();
            final float maxX = chunkPos.getMaxBlockX() - playerChunkPos.getMinBlockX() + 1.0f;
            final float minZ = chunkPos.getMinBlockZ() - playerChunkPos.getMinBlockZ();
            final float maxZ = chunkPos.getMaxBlockZ() - playerChunkPos.getMinBlockZ() + 1.0f;
            final int minY = ctx.clientLevel.getMinY();
            final int maxY = ctx.clientLevel.getMaxY() + 1;
            final int testedColonyId = colonyId;

            if (useColonyColour)
            {
                buf.defaultColor = colonyColours.computeIfAbsent(colonyId, id ->
                {
                    final IColonyView colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyView(id, ctx.clientLevel.dimension());
                    final ChatFormatting team = colony != null ? colony.getTeamColonyColor()
                            : id == playerColonyId ? ChatFormatting.WHITE : ChatFormatting.RED;
                    return new ColourARGB(team.getColor() | 0xff000000).asQuartet();
                });
            }
            else if (colonyId == playerColonyId)
            {
                buf.defaultColor = new ColourQuartet(255, 255, 255, 255);
            }
            else
            {
                buf.defaultColor = new ColourQuartet(255, 70, 70, 255);
            }

            // PORT26: ChunkPos is a final record — the old mutable-key lookups are plain
            // record allocations now (only run while the cache is being drawn).
            final boolean north = mapToDraw.getOrDefault(new ChunkPos(chunkPos.x(), chunkPos.z() - 1), -1) != testedColonyId;
            final boolean south = mapToDraw.getOrDefault(new ChunkPos(chunkPos.x(), chunkPos.z() + 1), -1) != testedColonyId;
            final boolean east = mapToDraw.getOrDefault(new ChunkPos(chunkPos.x() + 1, chunkPos.z()), -1) != testedColonyId;
            final boolean west = mapToDraw.getOrDefault(new ChunkPos(chunkPos.x() - 1, chunkPos.z()), -1) != testedColonyId;

            // vert lines
            if (north || west)
            {
                buf.addVertex(pose, minX, minY, minZ).setDefaultColor();
                buf.addVertex(pose, minX, maxY, minZ).setDefaultColor();
            }
            if (north || east)
            {
                buf.addVertex(pose, maxX, minY, minZ).setDefaultColor();
                buf.addVertex(pose, maxX, maxY, minZ).setDefaultColor();
            }
            if (south || west)
            {
                buf.addVertex(pose, minX, minY, maxZ).setDefaultColor();
                buf.addVertex(pose, minX, maxY, maxZ).setDefaultColor();
            }
            if (south || east)
            {
                buf.addVertex(pose, maxX, minY, maxZ).setDefaultColor();
                buf.addVertex(pose, maxX, maxY, maxZ).setDefaultColor();
            }

            // horizontal lines
            if (north)
            {
                if (isPlayerChunkX)
                {
                    for (int shift = PLAYER_CHUNK_STEP; shift < CHUNK_SIZE; shift += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(pose, minX + shift, minY, minZ).setDefaultColor();
                        buf.addVertex(pose, minX + shift, maxY, minZ).setDefaultColor();
                    }
                    for (int y = minY + PLAYER_CHUNK_STEP; y < maxY; y += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(pose, minX, y, minZ).setDefaultColor();
                        buf.addVertex(pose, maxX, y, minZ).setDefaultColor();
                    }
                }
                else
                {
                    for (int y = minY + CHUNK_SIZE; y < maxY; y += CHUNK_SIZE)
                    {
                        buf.addVertex(pose, minX, y, minZ).setDefaultColor();
                        buf.addVertex(pose, maxX, y, minZ).setDefaultColor();
                    }
                }
            }
            if (south)
            {
                if (isPlayerChunkX)
                {
                    for (int shift = PLAYER_CHUNK_STEP; shift < CHUNK_SIZE; shift += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(pose, minX + shift, minY, maxZ).setDefaultColor();
                        buf.addVertex(pose, minX + shift, maxY, maxZ).setDefaultColor();
                    }
                    for (int y = minY + PLAYER_CHUNK_STEP; y < maxY; y += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(pose, minX, y, maxZ).setDefaultColor();
                        buf.addVertex(pose, maxX, y, maxZ).setDefaultColor();
                    }
                }
                else
                {
                    for (int y = minY + CHUNK_SIZE; y < maxY; y += CHUNK_SIZE)
                    {
                        buf.addVertex(pose, minX, y, maxZ).setDefaultColor();
                        buf.addVertex(pose, maxX, y, maxZ).setDefaultColor();
                    }
                }
            }
            if (west)
            {
                if (isPlayerChunkZ)
                {
                    for (int shift = PLAYER_CHUNK_STEP; shift < CHUNK_SIZE; shift += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(pose, minX, minY, minZ + shift).setDefaultColor();
                        buf.addVertex(pose, minX, maxY, minZ + shift).setDefaultColor();
                    }
                    for (int y = minY + PLAYER_CHUNK_STEP; y < maxY; y += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(pose, minX, y, minZ).setDefaultColor();
                        buf.addVertex(pose, minX, y, maxZ).setDefaultColor();
                    }
                }
                else
                {
                    for (int y = minY + CHUNK_SIZE; y < maxY; y += CHUNK_SIZE)
                    {
                        buf.addVertex(pose, minX, y, minZ).setDefaultColor();
                        buf.addVertex(pose, minX, y, maxZ).setDefaultColor();
                    }
                }
            }
            if (east)
            {
                if (isPlayerChunkZ)
                {
                    for (int shift = PLAYER_CHUNK_STEP; shift < CHUNK_SIZE; shift += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(pose, maxX, minY, minZ + shift).setDefaultColor();
                        buf.addVertex(pose, maxX, maxY, minZ + shift).setDefaultColor();
                    }
                    for (int y = minY + PLAYER_CHUNK_STEP; y < maxY; y += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(pose, maxX, y, minZ).setDefaultColor();
                        buf.addVertex(pose, maxX, y, maxZ).setDefaultColor();
                    }
                }
                else
                {
                    for (int y = minY + CHUNK_SIZE; y < maxY; y += CHUNK_SIZE)
                    {
                        buf.addVertex(pose, maxX, y, minZ).setDefaultColor();
                        buf.addVertex(pose, maxX, y, maxZ).setDefaultColor();
                    }
                }
            }
        });
    }

    /**
     * Cleanup on logout.
     */
    public static void cleanup()
    {
        coloniesMap = null;
        chunkticketsMap = null;
        lastColony = null;
        lastPlayerChunkPos = null;
    }
}
