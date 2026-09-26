package com.minecolonies.core.client.render;

import com.ldtteam.structurize.blocks.ModBlocks;
import com.minecolonies.core.blocks.decorative.BlockColonyFlagBanner;
import com.minecolonies.core.blocks.decorative.BlockColonyFlagWallBanner;
import com.minecolonies.core.tileentities.TileEntityColonyFlag;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.model.object.banner.BannerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.SubmitNodeStorageExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The custom renderer to render the colony flag patterns if they exist,
 * and a placeholder marker if in Creative mode.
 *
 * <p>PORT26: render-state rewrite, mirrors the vanilla {@code BannerRenderer}: the banner
 * geometry comes from the standing/wall banner models (the old single BANNER layer with
 * manually toggled pole visibility no longer exists — 26.1 splits the models precisely
 * because part visibility can't be toggled per instance in the deferred submit graph),
 * the placement transformation is extracted into {@link State} and the patterns are
 * submitted through the vanilla pattern submit.</p>
 */
public class TileEntityColonyFlagRenderer implements BlockEntityRenderer<TileEntityColonyFlag, TileEntityColonyFlagRenderer.State>
{
    /**
     * PORT26: render state of the colony flag.
     */
    public static class State extends BlockEntityRenderState
    {
        public BannerPatternLayers patterns = BannerPatternLayers.EMPTY;
        public float phase;
        public Transformation transformation = Transformation.IDENTITY; // PORT26: static factory → constant
        public BannerBlock.AttachmentType attachmentType = BannerBlock.AttachmentType.GROUND;
        public boolean renderPlaceholder;

        // PORT26 FIX (batch 14): 1.21.1 placeholder frame — the marker cube has to sit INSIDE
        // the banner (top-center), rotated with it, exactly where the old
        // renderStatic(placeholder, ItemDisplayContext.FIXED) put it. The vanilla ground/wall
        // Transformation bundles the 2/3 model scale, which the placeholder must NOT inherit,
        // so the frame is carried separately.
        public float placeholderPivotX = 0.5F, placeholderPivotY = 0.5F, placeholderPivotZ = 0.5F;
        public float placeholderYaw;
        public float placeholderOffsetX, placeholderOffsetY, placeholderOffsetZ;
    }

    private final BannerModel       standingModel;
    private final BannerModel       wallModel;
    private final BannerFlagModel   standingFlagModel;
    private final BannerFlagModel   wallFlagModel;
    private final SpriteGetter      sprites;

    public TileEntityColonyFlagRenderer(final BlockEntityRendererProvider.Context context)
    {
        this.standingModel = new BannerModel(context.bakeLayer(ModelLayers.STANDING_BANNER));
        this.wallModel = new BannerModel(context.bakeLayer(ModelLayers.WALL_BANNER));
        this.standingFlagModel = new BannerFlagModel(context.bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
        this.wallFlagModel = new BannerFlagModel(context.bakeLayer(ModelLayers.WALL_BANNER_FLAG));
        this.sprites = context.sprites();
    }

    @Override
    public TileEntityColonyFlagRenderer.State createRenderState()
    {
        return new TileEntityColonyFlagRenderer.State();
    }

    @Override
    public void extractRenderState(
      @NotNull final TileEntityColonyFlag flagIn,
      @NotNull final TileEntityColonyFlagRenderer.State state,
      final float partialTicks,
      final Vec3 cameraPosition,
      @Nullable final ModelFeatureRenderer.CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(flagIn, state, partialTicks, cameraPosition, breakProgress);

        // PORT26: the noWorld branch of the 1.21.1 renderer belonged to the item rendering
        // path (renderByItem) — block entity renderers always have a level, so it is gone.
        state.patterns = flagIn.getPatterns();
        final long gameTime = flagIn.getLevel() != null ? flagIn.getLevel().getGameTime() : 0L;
        final BlockState blockState = flagIn.getBlockState();
        if (blockState.getBlock() instanceof BlockColonyFlagBanner)
        {
            state.transformation = BannerRenderer.TRANSFORMATIONS.freeTransformations(blockState.getValue(BlockColonyFlagBanner.ROTATION));
            state.attachmentType = BannerBlock.AttachmentType.GROUND;
            // PORT26 FIX (batch 14): 1.21.1 ground frame — translate(0.5, 0.5, 0.5) + rotY(-ROT * 360 / 16)
            state.placeholderPivotX = 0.5F;
            state.placeholderPivotY = 0.5F;
            state.placeholderPivotZ = 0.5F;
            state.placeholderYaw = (float) (-blockState.getValue(BlockColonyFlagBanner.ROTATION) * 360) / 16.0F;
            state.placeholderOffsetX = 0.0F;
            state.placeholderOffsetY = 0.0F;
            state.placeholderOffsetZ = 0.0F;
        }
        else if (blockState.getBlock() instanceof BlockColonyFlagWallBanner)
        {
            state.transformation = BannerRenderer.TRANSFORMATIONS.wallTransformation(blockState.getValue(BlockColonyFlagWallBanner.HORIZONTAL_FACING));
            state.attachmentType = BannerBlock.AttachmentType.WALL;
            // PORT26 FIX (batch 14): 1.21.1 wall frame — translate(0.5, -0.16666667, 0.5) +
            // rotY(-facing.toYRot()) + translate(0, -0.3125, -0.4375) (the second translate is
            // applied inside the rotated frame)
            state.placeholderPivotX = 0.5F;
            state.placeholderPivotY = -0.16666667F;
            state.placeholderPivotZ = 0.5F;
            state.placeholderYaw = -blockState.getValue(BlockColonyFlagWallBanner.HORIZONTAL_FACING).toYRot();
            state.placeholderOffsetX = 0.0F;
            state.placeholderOffsetY = -0.3125F;
            state.placeholderOffsetZ = -0.4375F;
        }

        final net.minecraft.core.BlockPos blockpos = flagIn.getBlockPos();
        state.phase = ((float) Math.floorMod((long)(blockpos.getX() * 7 + blockpos.getY() * 9 + blockpos.getZ() * 13) + gameTime, 100L) + partialTicks) / 100.0F;

        // creative placeholder preview (banner in main hand + creative mode)
        final Minecraft mc = Minecraft.getInstance();
        state.renderPlaceholder = mc.player != null
                                    && mc.player.getMainHandItem().getItem() instanceof BannerItem
                                    && mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.CREATIVE;
    }

    @Override
    public void submit(
      @NotNull final TileEntityColonyFlagRenderer.State state,
      @NotNull final PoseStack transform,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        if (state.renderPlaceholder)
        {
            submitPlaceholder(transform, submitNodeCollector, state);
        }

        transform.pushPose();
        transform.mulPose(state.transformation);
        final BannerModel model = state.attachmentType == BannerBlock.AttachmentType.WALL ? this.wallModel : this.standingModel;
        final BannerFlagModel flagModel = state.attachmentType == BannerBlock.AttachmentType.WALL ? this.wallFlagModel : this.standingFlagModel;

        // banner base (pole + bar)
        submitNodeCollector.submitModel(
          model,
          Unit.INSTANCE,
          transform,
          state.lightCoords,
          OverlayTexture.NO_OVERLAY,
          -1,
          net.minecraft.client.renderer.Sheets.BANNER_BASE,
          this.sprites,
          0,
          state.breakProgress);
        // waving flag cloth (the phase drives the flag animation at draw time)
        submitNodeCollector.submitModel(
          flagModel,
          state.phase,
          transform,
          state.lightCoords,
          OverlayTexture.NO_OVERLAY,
          -1,
          net.minecraft.client.renderer.Sheets.BANNER_BASE,
          this.sprites,
          0,
          state.breakProgress);
        // the colony patterns on a white base
        BannerRenderer.submitPatterns(
          this.sprites,
          transform,
          submitNodeCollector,
          state.lightCoords,
          OverlayTexture.NO_OVERLAY,
          flagModel,
          state.phase,
          true,
          DyeColor.WHITE,
          state.patterns,
          state.breakProgress);

        transform.popPose();
    }

    /**
     * Renders the substitution placeholder marker above the flag while a banner is held
     * in creative mode.
     *
     * @param transform the pose stack.
     * @param submitNodeCollector the submit collector.
     * @param state the render state.
     */
    private static void submitPlaceholder(final PoseStack transform, final SubmitNodeCollector submitNodeCollector, final TileEntityColonyFlagRenderer.State state)
    {
        // PORT26 FIX (batch 14): reproduces the 1.21.1 renderStatic(placeholder, FIXED) math —
        // the old code translated to (0.5, 0.5, 0.5) + (0, 0.5, 0) and submitted the RAW 0..1
        // cube, which put the marker a full block up-diagonal OUTSIDE the banner ("placeholder
        // renders beside it"). The 1.21.1 chain was:
        //   frame: pivot + rotY + (wall offset)
        //   then translate(0, 0.5, 0), scale(0.75)
        //   then the FIXED display transform (block/block "fixed": scale 0.5) + the item
        //   centering translate(-0.5, -0.5, -0.5) that ItemTransform.apply always appends.
        // Net effect: a 0.375-sized cube centered at pivot + offset + (0, 0.5, 0), rotated with
        // the banner — i.e. floating in the top-middle of the flag, INSIDE it.
        transform.pushPose();
        transform.translate(state.placeholderPivotX, state.placeholderPivotY, state.placeholderPivotZ);
        transform.mulPose(Axis.YP.rotationDegrees(state.placeholderYaw));
        transform.translate(state.placeholderOffsetX, state.placeholderOffsetY, state.placeholderOffsetZ);
        transform.translate(0.0D, 0.5D, 0.0D);
        transform.scale(0.375F, 0.375F, 0.375F); // 0.75 (1.21.1 scale) × 0.5 (FIXED display scale)
        transform.translate(-0.5D, -0.5D, -0.5D); // item centering (tail of ItemTransform.apply)

        final Minecraft mc = Minecraft.getInstance();
        final BlockState placeholderState = ModBlocks.blockSubstitution.get().defaultBlockState();
        final BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(placeholderState);
        final List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(), parts);
        if (!parts.isEmpty() && submitNodeCollector instanceof final SubmitNodeStorageExtension extension)
        {
            extension.submitMultiLayerBlockModel(
              transform,
              parts,
              model.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT),
              new int[0],
              state.lightCoords,
              OverlayTexture.NO_OVERLAY,
              0);
        }

        transform.popPose();
    }
}
