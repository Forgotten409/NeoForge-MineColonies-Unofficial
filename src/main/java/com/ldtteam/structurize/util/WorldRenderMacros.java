package com.ldtteam.structurize.util;

import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.util.Collection;
import java.util.List;

/**
 * PORT26 note: this file needed the heaviest adaptation of the util/ package.
 * <ul>
 * <li>{@code RenderLevelStageEvent#Stage} was removed — stages are now concrete sub-event
 * classes ({@link RenderLevelStageEvent.AfterOpaqueFeatures} replaces the old
 * {@code AFTER_ENTITIES}/{@code AFTER_BLOCK_ENTITIES}). The event also no longer carries
 * partial tick/camera/frustum; those now come from {@code Minecraft} and
 * {@code event.getLevelRenderState().cameraRenderState}.</li>
 * <li>{@code RenderType} moved to {@code net.minecraft.client.renderer.rendertype} and is now
 * built from a {@code RenderPipeline} + {@link RenderSetup} instead of CompositeState shards.
 * The old never/always depth test shard hacks became first-class {@link CompareOp}s and
 * {@code GLINT_TRANSPARENCY} maps to {@link BlendFunction#GLINT}.</li>
 * <li>{@code VertexConsumer#addVertex(Matrix4f, x, y, z)} was removed — the matrix-carrying
 * overloads now take a {@link Pose}.</li>
 * </ul>
 */
public abstract class WorldRenderMacros
{
    // 4 chunks squared
    public static final int MAX_DEBUG_TEXT_RENDER_DIST_SQUARED = Mth.square(4 * 16);
    public static final RenderType LINES = RenderTypes.LINES;
    public static final RenderType LINES_WITH_WIDTH = RenderTypes.LINES_WITH_WIDTH;
    public static final RenderType LINES_WITH_WIDTH_DEPTH_INVERT = RenderTypes.LINES_WITH_WIDTH_DEPTH_INVERT;
    public static final RenderType GLINT_LINES = RenderTypes.GLINT_LINES;
    public static final RenderType GLINT_LINES_WITH_WIDTH = RenderTypes.GLINT_LINES_WITH_WIDTH;
    public static final RenderType COLORED_TRIANGLES = RenderTypes.COLORED_TRIANGLES;
    public static final RenderType COLORED_TRIANGLES_NC_ND = RenderTypes.COLORED_TRIANGLES_NC_ND;
    /**
     * PORT26: the {@code Stage} enum is gone; stages are sub-event classes now. The old
     * {@code Stage.AFTER_ENTITIES} maps to {@link RenderLevelStageEvent.AfterOpaqueFeatures}
     * (entities + block entities + particles are submitted as "opaque features" there).
     * Compare with {@code event instanceof STAGE_FOR_LINES...} / {@code STAGE_FOR_LINES.isInstance(event)}.
     */
    public static final Class<? extends RenderLevelStageEvent> STAGE_FOR_LINES = RenderLevelStageEvent.AfterOpaqueFeatures.class;
    public static final float DEFAULT_LINE_WIDTH = 0.025f;

    public Minecraft mc;
    public RenderLevelStageEvent event;
    public LocalPlayer clientPlayer;
    public BufferSource bufferSource;
    public PoseStack poseStack;
    public DeltaTracker deltaTracker;
    public ClientLevel clientLevel;
    public ItemStack mainHandItem;
    public Vec3 cameraPosition;
    /**
     * PORT26: new field — replaces {@code EntityRenderDispatcher#cameraOrientation()} (removed).
     */
    public Quaternionf cameraOrientation;
    /**
     * In chunks
     */
    public int clientRenderDist;

    /**
     * Call this from event handler
     *
     * @param event
     */
    public void renderWorldLastEvent(final RenderLevelStageEvent e)
    {
        mc = Minecraft.getInstance();
        event = e;
        clientPlayer = mc.player;
        if (clientPlayer == null) // server login phase
        {
            return;
        }

        bufferSource = mc.renderBuffers().bufferSource();
        poseStack = e.getPoseStack();
        // PORT26: event#getPartialTick/getCamera removed — delta tracker from Minecraft,
        // camera data from the event's LevelRenderState
        deltaTracker = mc.getDeltaTracker();
        clientLevel = mc.level;
        mainHandItem = clientPlayer.getMainHandItem();
        cameraPosition = e.getLevelRenderState().cameraRenderState.pos;
        cameraOrientation = e.getLevelRenderState().cameraRenderState.orientation;
        clientRenderDist = mc.options.renderDistance().get();

        final Matrix4fStack mvMatrix = RenderSystem.getModelViewStack();
        mvMatrix.pushMatrix();
        try
        {
            mvMatrix.identity();
            mvMatrix.mul(e.getModelViewMatrix());
            // PORT26: RenderSystem#applyModelViewMatrix removed — pipelines read the stack directly

            // PORT26 FIX (play-test #7 — "Pose stack not empty" crash): this event handler runs
            // INSIDE LevelRenderer's main-pass lambda, so anything thrown out of
            // renderWithinContext unwinds straight into the frame graph and takes the whole
            // game down ("Unreported exception thrown!" + crash-to-menu) — and any pose/model-view
            // push without its pop leaks into LevelRenderer#checkPoseStack's
            // IllegalStateException at the end of the very same frame. Neither can ever be
            // allowed to escape: the port's own render sections (blueprint draw, boxes, tag
            // tool) balance their pushes in finally blocks; this catch is the outer safety net
            // for everything else (LinkageError included — guava's cache wraps constructor
            // failures in ExecutionError, which "catch Exception" misses). OOM/StackOverflowError
            // still escape — those are genuinely unrecoverable.
            try
            {
                renderWithinContext(e);
            }
            catch (final Exception | LinkageError error)
            {
                Log.getLogger().error("Structurize world render failed — skipping this frame's overlay rendering", error);
            }
        }
        finally
        {
            RenderSystem.getModelViewStack().popMatrix();
        }
    }

    /**
     * This is called with properly prepared context. Do here what you want
     *
     * @param event the render level stage event being rendered (was: Stage stage; the Stage
     *              enum no longer exists, check {@code instanceof} of the sub-event classes)
     */
    protected abstract void renderWithinContext(RenderLevelStageEvent event);

    /**
     * Moved pose context to camera and given pos
     *
     * @see #popPose()
     */
    public final void pushPoseCameraToPos(final BlockPos pos)
    {
        poseStack.pushPose();
        poseStack.translate(pos.getX() - cameraPosition.x(), pos.getY() - cameraPosition.y(), pos.getZ() - cameraPosition.z());
    }

    public final void popPose()
    {
        poseStack.popPose();
    }

    public void pushShaderMvMatrixFromPose()
    {
        final Matrix4fStack mvMatrix = RenderSystem.getModelViewStack();
        mvMatrix.pushMatrix();
        mvMatrix.mul(poseStack.last().pose());
    }

    public void popShaderMvMatrix()
    {
        RenderSystem.getModelViewStack().popMatrix();
    }

    /**
     * @return true if given aabb can be in any way seen by camera
     */
    public final boolean isVisible(final AABB aabb)
    {
        // PORT26: event#getFrustum removed — culling frustum lives on the camera render state
        return event.getLevelRenderState().cameraRenderState.cullFrustum.isVisible(aabb);
    }

    /**
     * @return true if given pos can be in any way seen by camera
     */
    public final boolean isVisible(final BlockPos pos)
    {
        return isVisible(pos, pos);
    }

    /**
     * @return true if given box can be in any way seen by camera
     */
    public final boolean isVisible(final BlockPos posA, final BlockPos posB)
    {
        // PORT26: Frustum#cubeInFrustum(6 floats) now returns a bitmask int — use the
        // AABB isVisible overload with the same box instead
        return isVisible(new AABB(Math.min(posA.getX(), posB.getX()),
            Math.min(posA.getY(), posB.getY()),
            Math.min(posA.getZ(), posB.getZ()),
            Math.max(posA.getX(), posB.getX()) + 1,
            Math.max(posA.getY(), posB.getY()) + 1,
            Math.max(posA.getZ(), posB.getZ()) + 1));
    }

    /**
     * Draw a blueprint at given pos.
     *
     * @param previewData the blueprint and context to draw.
     * @param pos         position to render at
     */
    public final void renderBlueprint(final BlueprintPreviewData blueprint, final BlockPos pos)
    {
        BlueprintHandler.getInstance().draw(blueprint, pos, event);
    }

    /**
     * Draw a blueprint at list of given pos.
     *
     * @param previewData the blueprint and context to draw.
     * @param points      list of positions to render at
     */
    public final void renderBlueprint(final BlueprintPreviewData blueprint, final Collection<BlockPos> points)
    {
        BlueprintHandler.getInstance().drawAtListOfPositions(blueprint, points, event);
    }

    /**
     * Render a black box around two positions
     *
     * @param posA The first Position
     * @param posB The second Position
     */
    public final void renderBlackLineBox(final BlockPos posA, final BlockPos posB, final float lineWidth)
    {
        renderLineBox(LINES_WITH_WIDTH, posA, posB, 0x00, 0x00, 0x00, 0xff, lineWidth);
    }

    /**
     * Render a red glint box around two positions
     *
     * @param posA The first Position
     * @param posB The second Position
     */
    public final void renderRedGlintLineBox(final BlockPos posA, final BlockPos posB, final float lineWidth)
    {
        renderLineBox(GLINT_LINES_WITH_WIDTH, posA, posB, 0xff, 0x0, 0x0, 0xff, lineWidth);
    }

    /**
     * Render a white box around two positions
     *
     * @param posA The first Position
     * @param posB The second Position
     */
    public final void renderWhiteLineBox(final BlockPos posA, final BlockPos posB, final float lineWidth)
    {
        renderLineBox(LINES_WITH_WIDTH, posA, posB, 0xff, 0xff, 0xff, 0xff, lineWidth);
    }

    /**
     * Render a colored box around from aabb
     *
     * @param aabb the box
     */
    public final void renderLineAABB(final RenderType renderType, final AABB aabb, final int argbColor, final float lineWidth)
    {
        renderLineAABB(renderType,
            aabb,
            (argbColor >> 16) & 0xff,
            (argbColor >> 8) & 0xff,
            argbColor & 0xff,
            (argbColor >> 24) & 0xff,
            lineWidth);
    }

    /**
     * Render a colored box around from aabb
     *
     * @param aabb the box
     */
    public final void renderLineAABB(final RenderType renderType,
        final AABB aabb,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final float lineWidth)
    {
        renderLineBox(renderType,
            (float) aabb.minX,
            (float) aabb.minY,
            (float) aabb.minZ,
            (float) aabb.maxX,
            (float) aabb.maxY,
            (float) aabb.maxZ,
            red,
            green,
            blue,
            alpha,
            lineWidth);
    }

    /**
     * Render a colored box around position
     *
     * @param pos The Position
     */
    public final void renderLineBox(final RenderType renderType,
        final BlockPos pos,
        final int argbColor,
        final float lineWidth)
    {
        renderLineBox(renderType,
            pos,
            pos,
            (argbColor >> 16) & 0xff,
            (argbColor >> 8) & 0xff,
            argbColor & 0xff,
            (argbColor >> 24) & 0xff,
            lineWidth);
    }

    /**
     * Render a colored box around two positions
     *
     * @param posA The first Position
     * @param posB The second Position
     */
    public final void renderLineBox(final RenderType renderType,
        final BlockPos posA,
        final BlockPos posB,
        final int argbColor,
        final float lineWidth)
    {
        renderLineBox(renderType,
            posA,
            posB,
            (argbColor >> 16) & 0xff,
            (argbColor >> 8) & 0xff,
            argbColor & 0xff,
            (argbColor >> 24) & 0xff,
            lineWidth);
    }

    /**
     * Render a box around two positions
     *
     * @param posA First position
     * @param posB Second position
     */
    public final void renderLineBox(final RenderType renderType,
        final BlockPos posA,
        final BlockPos posB,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final float lineWidth)
    {
        renderLineBox(renderType,
            Math.min(posA.getX(), posB.getX()),
            Math.min(posA.getY(), posB.getY()),
            Math.min(posA.getZ(), posB.getZ()),
            Math.max(posA.getX(), posB.getX()) + 1,
            Math.max(posA.getY(), posB.getY()) + 1,
            Math.max(posA.getZ(), posB.getZ()) + 1,
            red,
            green,
            blue,
            alpha,
            lineWidth);
    }

    /**
     * Render a box around two positions
     *
     * @param posA First position
     * @param posB Second position
     */
    public final void renderLineBox(final RenderType renderType,
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final float lineWidth)
    {
        if (alpha == 0)
        {
            return;
        }

        final float halfLine = lineWidth / 2.0f;
        minX -= halfLine;
        minY -= halfLine;
        minZ -= halfLine;
        final float minX2 = minX + lineWidth;
        final float minY2 = minY + lineWidth;
        final float minZ2 = minZ + lineWidth;

        maxX += halfLine;
        maxY += halfLine;
        maxZ += halfLine;
        final float maxX2 = maxX - lineWidth;
        final float maxY2 = maxY - lineWidth;
        final float maxZ2 = maxZ - lineWidth;

        populateRenderLineBox(minX, minY, minZ, minX2, minY2, minZ2, maxX, maxY, maxZ, maxX2, maxY2, maxZ2, red, green, blue, alpha, poseStack.last(), bufferSource.getBuffer(renderType));
    }

    // TODO: ebo this, does vanilla have any ebo things?
    protected final void populateRenderLineBox(final float minX,
        final float minY,
        final float minZ,
        final float minX2,
        final float minY2,
        final float minZ2,
        final float maxX,
        final float maxY,
        final float maxZ,
        final float maxX2,
        final float maxY2,
        final float maxZ2,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final Pose pose,
        final VertexConsumer buf)
    {
        // z plane

        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, minZ).setColor(red, green, blue, alpha);

        //

        buf.addVertex(pose, minX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(pose, minX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        // PORT26 FIX (batch 25): vertex order transcribed swapped vs 1.21.1 — the flipped
        // winding got backface-culled (missing face on the top-right fat edge).
        buf.addVertex(pose, maxX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);

        // x plane

        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);

        //

        buf.addVertex(pose, minX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(pose, maxX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY, maxZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);

        // y plane

        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY, maxZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(pose, maxX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, minY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, minY2, maxZ).setColor(red, green, blue, alpha);

        // PORT26 FIX (batch 25): two vertices were transcribed as minX2 instead of maxX2,
        // which spanned huge flat triangles across the BOTTOM face of every line-box
        // (the "white/red triangle under the box" artifact on scan/build/anchor boxes).
        buf.addVertex(pose, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, minY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, minY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY2, maxZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(pose, maxX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, maxY2, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY2, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY2, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX2, maxY2, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY2, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY2, maxZ2).setColor(red, green, blue, alpha);

        //

        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY, minZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX2, maxY, maxZ2).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
    }

    public final void renderBox(final RenderType renderType,
        final BlockPos posA,
        final BlockPos posB,
        final int argbColor)
    {
        renderBox(renderType,
            posA,
            posB,
            (argbColor >> 16) & 0xff,
            (argbColor >> 8) & 0xff,
            argbColor & 0xff,
            (argbColor >> 24) & 0xff);
    }

    public final void renderBox(final RenderType renderType,
        final BlockPos posA,
        final BlockPos posB,
        final int red,
        final int green,
        final int blue,
        final int alpha)
    {
        if (alpha == 0)
        {
            return;
        }

        final float minX = Math.min(posA.getX(), posB.getX());
        final float minY = Math.min(posA.getY(), posB.getY());
        final float minZ = Math.min(posA.getZ(), posB.getZ());

        final float maxX = Math.max(posA.getX(), posB.getX()) + 1;
        final float maxY = Math.max(posA.getY(), posB.getY()) + 1;
        final float maxZ = Math.max(posA.getZ(), posB.getZ()) + 1;

        populateCuboid(minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha, poseStack.last(), bufferSource.getBuffer(renderType));
    }

    protected final void populateCuboid(final float minX,
        final float minY,
        final float minZ,
        final float maxX,
        final float maxY,
        final float maxZ,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final Pose pose,
        final VertexConsumer buf)
    {
        // z plane

        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);

        // y plane

        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        // x plane

        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        buf.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buf.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
    }

    public final void renderFillRectangle(final int x,
        final int y,
        final int z,
        final int w,
        final int h,
        final int argbColor)
    {
        populateRectangle(x,
            y,
            z,
            w,
            h,
            (argbColor >> 16) & 0xff,
            (argbColor >> 8) & 0xff,
            argbColor & 0xff,
            (argbColor >> 24) & 0xff,
            bufferSource.getBuffer(COLORED_TRIANGLES_NC_ND),
            poseStack.last());
    }

    protected final void populateRectangle(final int x,
        final int y,
        final int z,
        final int w,
        final int h,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final VertexConsumer buffer,
        final Pose pose)
    {
        if (alpha == 0)
        {
            return;
        }

        buffer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
        buffer.addVertex(pose, x, y + h, z).setColor(red, green, blue, alpha);
        buffer.addVertex(pose, x + w, y + h, z).setColor(red, green, blue, alpha);

        buffer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
        buffer.addVertex(pose, x + w, y + h, z).setColor(red, green, blue, alpha);
        buffer.addVertex(pose, x + w, y, z).setColor(red, green, blue, alpha);
    }

    /**
     * Renders the given list of strings, 3 elements a row.
     *
     * @param pos                     position to render at
     * @param text                    text list
     * @param matrixStack             stack to use
     * @param buffer                  render buffer
     * @param forceWhite              force white for no depth rendering
     * @param mergeEveryXListElements merge every X elements of text list using a tostring call
     */
    public final void renderDebugText(final BlockPos pos,
        final List<String> text,
        final boolean forceWhite,
        final int mergeEveryXListElements)
    {
        renderDebugText(pos, pos, text, forceWhite, mergeEveryXListElements);
    }

    /**
     * Renders the given list of strings, 3 elements a row.
     *
     * @param renderPos               position to render at
     * @param worldPos                (logic) position in world
     * @param text                    text list
     * @param matrixStack             stack to use
     * @param buffer                  render buffer
     * @param forceWhite              force white for no depth rendering
     * @param mergeEveryXListElements merge every X elements of text list using a tostring call
     */
    @SuppressWarnings("resource")
    public final void renderDebugText(final BlockPos renderPos,
        final BlockPos worldPos,
        final List<String> text,
        final boolean forceWhite,
        final int mergeEveryXListElements)
    {
        if (mergeEveryXListElements < 1)
        {
            throw new IllegalArgumentException("mergeEveryXListElements is less than 1");
        }

        // PORT26: EntityRenderDispatcher#distanceToSqr(x, y, z) and #cameraOrientation() were
        // removed — compute the camera distance manually and use the captured camera orientation
        final double distX = cameraPosition.x - worldPos.getX();
        final double distY = cameraPosition.y - worldPos.getY();
        final double distZ = cameraPosition.z - worldPos.getZ();
        final int cap = text.size();
        if (cap > 0 && distX * distX + distY * distY + distZ * distZ <= MAX_DEBUG_TEXT_RENDER_DIST_SQUARED)
        {
            final Font fontrenderer = Minecraft.getInstance().font;

            poseStack.pushPose();
            try
            {
                poseStack.translate(renderPos.getX() + 0.5d, renderPos.getY() + 0.6d, renderPos.getZ() + 0.5d);
                poseStack.mulPose(cameraOrientation);
                poseStack.scale(0.014f, -0.014f, 0.014f);

                final float backgroundTextOpacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
                final int alphaMask = (int) (backgroundTextOpacity * 255.0F) << 24;

                final Matrix4f rawPosMatrix = poseStack.last().pose();

                for (int i = 0; i < cap; i += mergeEveryXListElements)
                {
                    final MutableComponent renderText = Component.literal(
                        mergeEveryXListElements == 1 ? text.get(i) : text.subList(i, Math.min(i + mergeEveryXListElements, cap)).toString());
                    final float textCenterShift = (float) (-fontrenderer.width(renderText) / 2);

                    fontrenderer.drawInBatch(renderText,
                        textCenterShift,
                        0,
                        forceWhite ? 0xffffffff : 0x20ffffff,
                        false,
                        rawPosMatrix,
                        bufferSource,
                        Font.DisplayMode.SEE_THROUGH,
                        alphaMask,
                        0x00f000f0);
                    if (!forceWhite)
                    {
                        fontrenderer.drawInBatch(renderText, textCenterShift, 0, 0xffffffff, false, rawPosMatrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0x00f000f0);
                    }
                    poseStack.translate(0.0d, fontrenderer.lineHeight + 1, 0.0d);
                }
            }
            // PORT26 FIX (play-test #7): never leak the pose push — LevelRenderer#checkPoseStack
            // throws "Pose stack not empty" at the end of the frame otherwise
            finally
            {
                poseStack.popPose();
            }
        }
    }

    /**
     * PORT26: fully rewritten — the CompositeState shard system is gone. Each render type is
     * now {@code RenderType#create(name, RenderSetup)} on top of a {@link RenderPipeline}.
     * Old state shard mapping used below:
     * <ul>
     * <li>{@code TRANSLUCENT_TRANSPARENCY} -> {@link BlendFunction#TRANSLUCENT}</li>
     * <li>{@code GLINT_TRANSPARENCY} -> {@link BlendFunction#GLINT}</li>
     * <li>{@code LEQUAL/GREATER/NEVER/ALWAYS_DEPTH_TEST} + {@code COLOR_[DEPTH_]WRITE} ->
     * {@code new DepthStencilState(CompareOp.X, depthWrite)}</li>
     * <li>{@code NO_CULL}/{@code CULL} -> {@code withCull(false/true)}</li>
     * <li>{@code POSITION_COLOR_SHADER} -> {@code core/position_color} shaders (same as vanilla's
     * {@code DEBUG_FILLED_SNIPPET})</li>
     * </ul>
     * The custom Never/AlwaysDepthTestStateShard hacks were dropped — vanilla
     * {@link CompareOp#NEVER_PASS}/{@link CompareOp#ALWAYS_PASS} are first-class now.
     */
    public static final class RenderTypes
    {
        private RenderTypes()
        {
            throw new IllegalStateException();
        }

        private static RenderPipeline.Builder positionColorPipeline(final String path, final BlendFunction blend, final boolean cull, final CompareOp depthFunc, final boolean depthWrite, final VertexFormat.Mode mode)
        {
            return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("structurize", path))
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(new ColorTargetState(blend))
                .withCull(cull)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
                .withDepthStencilState(new DepthStencilState(depthFunc, depthWrite));
        }

        private static final RenderPipeline LINES_PIPELINE = positionColorPipeline("pipeline/lines",
            BlendFunction.TRANSLUCENT, false, CompareOp.LESS_THAN_OR_EQUAL, false, VertexFormat.Mode.DEBUG_LINES).build();
        private static final RenderPipeline LINES_WITH_WIDTH_PIPELINE = positionColorPipeline("pipeline/lines_with_width",
            BlendFunction.TRANSLUCENT, true, CompareOp.LESS_THAN_OR_EQUAL, true, VertexFormat.Mode.TRIANGLES).build();
        private static final RenderPipeline LINES_WITH_WIDTH_DEPTH_INVERT_PIPELINE = positionColorPipeline("pipeline/lines_with_width_depth_invert",
            BlendFunction.TRANSLUCENT, true, CompareOp.GREATER_THAN, false, VertexFormat.Mode.TRIANGLES).build();
        private static final RenderPipeline GLINT_LINES_PIPELINE = positionColorPipeline("pipeline/glint_lines",
            BlendFunction.GLINT, false, CompareOp.NEVER_PASS, false, VertexFormat.Mode.DEBUG_LINES).build();
        private static final RenderPipeline GLINT_LINES_WITH_WIDTH_PIPELINE = positionColorPipeline("pipeline/glint_lines_with_width",
            BlendFunction.GLINT, true, CompareOp.ALWAYS_PASS, true, VertexFormat.Mode.TRIANGLES).build();
        private static final RenderPipeline COLORED_TRIANGLES_NC_ND_PIPELINE = positionColorPipeline("pipeline/colored_triangles_nc_nd",
            BlendFunction.TRANSLUCENT, false, CompareOp.NEVER_PASS, false, VertexFormat.Mode.TRIANGLES).build();

        private static final RenderType GLINT_LINES = RenderType.create("structurize_glint_lines",
            RenderSetup.builder(GLINT_LINES_PIPELINE).bufferSize(1 << 12).createRenderSetup());

        private static final RenderType GLINT_LINES_WITH_WIDTH = RenderType.create("structurize_glint_lines_with_width",
            RenderSetup.builder(GLINT_LINES_WITH_WIDTH_PIPELINE).bufferSize(1 << 13).createRenderSetup());

        private static final RenderType LINES = RenderType.create("structurize_lines",
            RenderSetup.builder(LINES_PIPELINE).bufferSize(1 << 14).createRenderSetup());

        private static final RenderType LINES_WITH_WIDTH = RenderType.create("structurize_lines_with_width",
            RenderSetup.builder(LINES_WITH_WIDTH_PIPELINE).bufferSize(1 << 13).createRenderSetup());

        private static final RenderType LINES_WITH_WIDTH_DEPTH_INVERT = RenderType.create("structurize_lines_with_width_depth_invert",
            RenderSetup.builder(LINES_WITH_WIDTH_DEPTH_INVERT_PIPELINE).bufferSize(1 << 12).createRenderSetup());

        private static final RenderType COLORED_TRIANGLES = RenderType.create("structurize_colored_triangles",
            RenderSetup.builder(LINES_WITH_WIDTH_PIPELINE).bufferSize(1 << 13).createRenderSetup());

        private static final RenderType COLORED_TRIANGLES_NC_ND = RenderType.create("structurize_colored_triangles_nc_nd",
            RenderSetup.builder(COLORED_TRIANGLES_NC_ND_PIPELINE).bufferSize(1 << 12).createRenderSetup());

        /**
         * PORT26: new — pipelines must be registered (mod bus, client) before use.
         */
        public static void registerPipelines(final RegisterRenderPipelinesEvent event)
        {
            event.registerPipeline(LINES_PIPELINE);
            event.registerPipeline(LINES_WITH_WIDTH_PIPELINE);
            event.registerPipeline(LINES_WITH_WIDTH_DEPTH_INVERT_PIPELINE);
            event.registerPipeline(GLINT_LINES_PIPELINE);
            event.registerPipeline(GLINT_LINES_WITH_WIDTH_PIPELINE);
            event.registerPipeline(COLORED_TRIANGLES_NC_ND_PIPELINE);
        }

        /**
         * Register our buffers
         */
        public static void registerBuffer(final RegisterRenderBuffersEvent event)
        {
            event.registerRenderBuffer(LINES);
            event.registerRenderBuffer(LINES_WITH_WIDTH);
            event.registerRenderBuffer(LINES_WITH_WIDTH_DEPTH_INVERT);
            event.registerRenderBuffer(GLINT_LINES);
            event.registerRenderBuffer(GLINT_LINES_WITH_WIDTH);
            event.registerRenderBuffer(COLORED_TRIANGLES);
            event.registerRenderBuffer(COLORED_TRIANGLES_NC_ND);
        }

        /**
         * Managed by structurize, ends above buffers in context similar to {@code RenderType#LINES}
         */
        public static void finishBuffer(final RenderLevelStageEvent event)
        {
            final MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

            // PORT26: Stage enum removed — old AFTER_BLOCK_ENTITIES maps to AfterOpaqueFeatures
            if (event instanceof RenderLevelStageEvent.AfterOpaqueFeatures)
            {
                bufferSource.endBatch(LINES_WITH_WIDTH_DEPTH_INVERT);

                bufferSource.endBatch(COLORED_TRIANGLES);
                bufferSource.endBatch(COLORED_TRIANGLES_NC_ND);

                bufferSource.endBatch(LINES);
                bufferSource.endBatch(LINES_WITH_WIDTH);

                // fallthrough into levelRenderer master endBatch
                // bufferSource.endBatch(GLINT_LINES);
                // bufferSource.endBatch(GLINT_LINES_WITH_WIDTH);
            }
        }
    }
}
