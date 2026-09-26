package com.ldtteam.domumornamentum.client.event.handlers;

import com.ldtteam.domumornamentum.client.render.ModelGhostRenderer;
import com.ldtteam.domumornamentum.util.ItemStackUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * PORT26 (batch 10): converted from static listener + {@code register(Class)} to an INSTANCE
 * listener (see {@link ClientTickEventHandler} for the rationale — game-bus static registration
 * was suspect in the merged mod).
 */
public class MateriallyTexturedBlockPreviewRenderHandler {

    private static final MateriallyTexturedBlockPreviewRenderHandler INSTANCE = new MateriallyTexturedBlockPreviewRenderHandler();

    public static MateriallyTexturedBlockPreviewRenderHandler getInstance()
    {
        return INSTANCE;
    }

    private MateriallyTexturedBlockPreviewRenderHandler()
    {
    }

    @SubscribeEvent
    public void onRenderLevelStage(final RenderLevelStageEvent.AfterLevel event) {
        // PORT26: RenderLevelStageEvent#getStage() and the Stage enum are gone —
        // the event is now split into typed sub-events (AfterSky, AfterOpaqueBlocks, ..., AfterLevel).
        final PoseStack poseStack = event.getPoseStack();
        renderMateriallyTexturedBlockPreview(poseStack);
    }

    public static void renderMateriallyTexturedBlockPreview(final PoseStack poseStack) {
        final HitResult rayTraceResult = Minecraft.getInstance().hitResult;
        if (!(rayTraceResult instanceof final BlockHitResult blockRayTraceResult) || blockRayTraceResult.getType() == HitResult.Type.MISS)
            return;

        final Player playerEntity = Minecraft.getInstance().player;
        if (playerEntity == null || playerEntity.isSpectator())
            return;

        final ItemStack heldStack = ItemStackUtils.getMateriallyTexturedItemStackFromPlayer(playerEntity);
        if (heldStack.isEmpty())
            return;

        // PORT26: Direction#getNormal() is gone; BlockPos#relative(Direction) is the 1-step offset
        Vec3 targetedRenderPos = Vec3.atLowerCornerOf(blockRayTraceResult.getBlockPos().relative(blockRayTraceResult.getDirection()));
        renderGhost(poseStack, heldStack, targetedRenderPos, blockRayTraceResult, Minecraft.getInstance().level);
    }

    private static void renderGhost(
            final PoseStack poseStack,
            final ItemStack heldStack,
            final Vec3 targetedRenderPos, BlockHitResult blockRayTraceResult, ClientLevel level) {
        ModelGhostRenderer.getInstance().renderGhost(
                poseStack,
                heldStack,
                targetedRenderPos,
                blockRayTraceResult,
                level,
                false
        );
    }

}
