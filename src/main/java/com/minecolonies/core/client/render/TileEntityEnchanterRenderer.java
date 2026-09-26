package com.minecolonies.core.client.render;

import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.tileentities.TileEntityEnchanter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PORT26: render-state rewrite, mirrors the vanilla {@code EnchantTableRenderer} — the
 * book animation values are extracted into {@link State} and the book model is submitted
 * with the enchanting book sprite from the block atlas.
 */
public class TileEntityEnchanterRenderer implements BlockEntityRenderer<TileEntityEnchanter, TileEntityEnchanterRenderer.State>
{
    /**
     * PORT26: block atlas sprite of the book (Sheets.BLOCKS_MAPPER adds the "block/" prefix,
     * equivalent to the old Material(InventoryMenu.BLOCK_ATLAS, "minecolonies:block/enchanting_table_book")).
     */
    public static final SpriteId TEXTURE_BOOK = net.minecraft.client.renderer.Sheets.BLOCKS_MAPPER
                                                   .apply(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "enchanting_table_book"));

    /**
     * PORT26: render state carrying the book animation of the enchanter.
     */
    public static class State extends BlockEntityRenderState
    {
        public float flip;
        public float open;
        public float time;
        public float yRot;
    }

    /**
     * The book model to be rendered.
     */
    private final BookModel modelBook;
    private final SpriteGetter sprites;

    /**
     * Create the renderer.
     *
     * @param context the context.
     */
    public TileEntityEnchanterRenderer(final BlockEntityRendererProvider.Context context)
    {
        this.modelBook = new BookModel(context.bakeLayer(ModelLayers.BOOK));
        this.sprites = context.sprites();
    }

    @Override
    public TileEntityEnchanterRenderer.State createRenderState()
    {
        return new TileEntityEnchanterRenderer.State();
    }

    @Override
    public void extractRenderState(
      @NotNull final TileEntityEnchanter entity,
      @NotNull final TileEntityEnchanterRenderer.State state,
      final float partialTicks,
      final Vec3 cameraPosition,
      @Nullable final ModelFeatureRenderer.CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);

        state.flip = Mth.lerp(partialTicks, entity.pageFlipPrev, entity.pageFlip);
        state.open = Mth.lerp(partialTicks, entity.bookSpreadPrev, entity.bookSpread);
        state.time = entity.tickCount + partialTicks;
        float rotDelta = entity.bookRotation - entity.bookRotationPrev;
        while (rotDelta >= (float) Math.PI)
        {
            rotDelta -= (float) (Math.PI * 2);
        }
        while (rotDelta < (float) -Math.PI)
        {
            rotDelta += (float) (Math.PI * 2);
        }
        state.yRot = entity.bookRotationPrev + rotDelta * partialTicks;
    }

    @Override
    public void submit(
      @NotNull final TileEntityEnchanterRenderer.State state,
      @NotNull final PoseStack matrixStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        matrixStack.pushPose();
        matrixStack.translate(0.5D, 0.75D, 0.5D);
        matrixStack.translate(0.0D, 0.1F + Mth.sin(state.time * 0.1F) * 0.01F, 0.0D);
        matrixStack.mulPose(Axis.YP.rotation(-state.yRot));
        matrixStack.mulPose(Axis.ZP.rotationDegrees(80.0F));
        final float flipA = Mth.frac(state.flip + 0.25F) * 1.6F - 0.3F;
        final float flipB = Mth.frac(state.flip + 0.75F) * 1.6F - 0.3F;
        final BookModel.State bookState =
          BookModel.State.forAnimation(state.time, Mth.clamp(flipA, 0.0F, 1.0F), Mth.clamp(flipB, 0.0F, 1.0F), state.open);
        // PORT26: submit through the collector with the block atlas sprite.
        submitNodeCollector.submitModel(
          this.modelBook,
          bookState,
          matrixStack,
          state.lightCoords,
          OverlayTexture.NO_OVERLAY,
          -1,
          TEXTURE_BOOK,
          this.sprites,
          0,
          state.breakProgress);
        matrixStack.popPose();
    }
}
