package com.minecolonies.core.client.render;

import com.minecolonies.api.blocks.huts.AbstractBlockMinecoloniesDefault;
import com.minecolonies.api.tileentities.AbstractTileEntityScarecrow;
import com.minecolonies.api.tileentities.ScareCrowType;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.blocks.BlockScarecrow;
import com.minecolonies.core.client.model.ScarecrowModel;
import com.minecolonies.core.event.ClientRegistryHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.SubmitNodeStorageExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to render the scarecrow.
 *
 * <p>PORT26: render-state rewrite — the block state dependent data (half, facing, lantern,
 * scarecrow type) is extracted into {@link State}; the model is submitted through the
 * submit collector with a block atlas sprite, and the lantern block is submitted through
 * the NeoForge multi-layer block model submit (the old
 * {@code BlockRenderDispatcher#renderSingleBlock} is gone — same pattern as the structurize
 * tag anchor renderer).</p>
 */
public class TileEntityScarecrowRenderer implements BlockEntityRenderer<AbstractTileEntityScarecrow, TileEntityScarecrowRenderer.State>
{
    /**
     * Offset to the block middle.
     */
    private static final double BLOCK_MIDDLE = 0.5;

    /**
     * Y-Offset in order to have the scarecrow over ground.
     */
    private static final double YOFFSET = 1.5;

    /**
     * Rotate the model some degrees.
     */
    private static final int ROTATION = 180;

    /**
     * Basic rotation to achieve a certain direction.
     */
    private static final int BASIC_ROTATION = 90;

    /**
     * Rotate by amount to go east.
     */
    private static final int ROTATE_EAST = 1;

    /**
     * Rotate by amount to go south.
     */
    private static final int ROTATE_SOUTH = 2;

    /**
     * Rotate by amount to go west.
     */
    private static final int ROTATE_WEST = 3;

    public static final SpriteId SCARECROW_A;
    public static final SpriteId SCARECROW_B;

    static
    {
        // PORT26: Material(atlas, texture) + buffer() is gone — sprites are addressed by
        // SpriteId through the Sheets mapper on the block atlas ("block/" prefix).
        SCARECROW_A = net.minecraft.client.renderer.Sheets.BLOCKS_MAPPER
                        .apply(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "blockscarecrowpumpkin"));
        SCARECROW_B = net.minecraft.client.renderer.Sheets.BLOCKS_MAPPER
                        .apply(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "blockscarecrownormal"));
    }

    /**
     * PORT26: render state of the scarecrow — everything the submit pass needs.
     */
    public static class State extends BlockEntityRenderState
    {
        public boolean upperHalf;
        public boolean pumpkinHead;
        public boolean hasLantern;
        @Nullable
        public Direction facing;
    }

    /**
     * The model of the scarecrow.
     */
    @NotNull
    private final ScarecrowModel model;

    private final SpriteGetter sprites;

    /**
     * The public constructor for the renderer.
     *
     * @param context the render context.
     */
    public TileEntityScarecrowRenderer(final BlockEntityRendererProvider.Context context)
    {
        this.model = new ScarecrowModel(context.bakeLayer(ClientRegistryHandler.SCARECROW));
        this.sprites = context.sprites();
    }

    @Override
    public TileEntityScarecrowRenderer.State createRenderState()
    {
        return new TileEntityScarecrowRenderer.State();
    }

    @Override
    public void extractRenderState(
      @NotNull final AbstractTileEntityScarecrow te,
      @NotNull final TileEntityScarecrowRenderer.State state,
      final float partialTicks,
      final Vec3 cameraPosition,
      @Nullable final ModelFeatureRenderer.CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(te, state, partialTicks, cameraPosition, breakProgress);

        // PORT26: block state data extracted up front (the submit pass has no entity access).
        state.upperHalf = te.getBlockState().getValue(BlockScarecrow.HALF) == DoubleBlockHalf.UPPER;
        state.hasLantern = te.getBlockState().getValue(BlockScarecrow.LANTERN);
        if (te.getLevel() != null && te.getLevel().getBlockState(te.getBlockPos()).getBlock() instanceof BlockScarecrow)
        {
            state.facing = te.getLevel().getBlockState(te.getBlockPos()).getValue(AbstractBlockMinecoloniesDefault.FACING);
        }
        else
        {
            state.facing = null;
        }
        state.pumpkinHead = te.getScarecrowType() == ScareCrowType.PUMPKINHEAD;
    }

    @Override
    public void submit(
      @NotNull final TileEntityScarecrowRenderer.State state,
      @NotNull final PoseStack matrixStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        if (state.upperHalf)
        {
            return;
        }

        //Store the transformation
        matrixStack.pushPose();
        //Set viewport to tile entity position to render it
        matrixStack.translate(BLOCK_MIDDLE, YOFFSET, BLOCK_MIDDLE);
        matrixStack.mulPose(Axis.ZP.rotationDegrees(ROTATION));

        if (state.facing != null)
        {
            switch (state.facing)
            {
                case EAST:
                    matrixStack.mulPose(Axis.YP.rotationDegrees(BASIC_ROTATION * ROTATE_EAST));
                    break;
                case SOUTH:
                    matrixStack.mulPose(Axis.YP.rotationDegrees(BASIC_ROTATION * ROTATE_SOUTH));
                    break;
                case WEST:
                    matrixStack.mulPose(Axis.YP.rotationDegrees(BASIC_ROTATION * ROTATE_WEST));
                    break;
                default:
                    //don't rotate at all.
            }
        }

        // PORT26: model submit through the collector with the block atlas sprite
        // (was Material#buffer + renderToBuffer).
        submitNodeCollector.submitModel(
          this.model,
          Unit.INSTANCE,
          matrixStack,
          state.lightCoords,
          OverlayTexture.NO_OVERLAY,
          -1,
          state.pumpkinHead ? SCARECROW_A : SCARECROW_B,
          this.sprites,
          0,
          state.breakProgress);

        if (state.hasLantern)
        {
            renderLantern(matrixStack, submitNodeCollector, state);
        }

        matrixStack.popPose();
    }

    /**
     * Renders the lantern attached to the scarecrow.
     *
     * @param matrixStack the pose stack.
     * @param submitNodeCollector the submit collector.
     * @param state the render state.
     */
    private static void renderLantern(final PoseStack matrixStack, final SubmitNodeCollector submitNodeCollector, final TileEntityScarecrowRenderer.State state)
    {
        matrixStack.pushPose();

        matrixStack.mulPose(Axis.ZP.rotationDegrees(180f));
        matrixStack.translate(0.6f, -0.6f, -0.375f);

        matrixStack.scale(0.75f, 0.65f, 0.75f);

        // PORT26: renderSingleBlock is gone — submit the lantern block model through the
        // NeoForge multi-layer block model submit (structurize tag anchor pattern).
        final Minecraft mc = Minecraft.getInstance();
        final BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(Blocks.LANTERN.defaultBlockState());
        final List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(), parts);
        if (!parts.isEmpty() && submitNodeCollector instanceof final SubmitNodeStorageExtension extension)
        {
            extension.submitMultiLayerBlockModel(
              matrixStack,
              parts,
              model.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT),
              new int[0],
              state.lightCoords,
              OverlayTexture.NO_OVERLAY,
              0);
        }

        matrixStack.popPose();
    }
}
