package com.minecolonies.core.client.render;

import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.model.SpearModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * Renders the spear item with the entity-style spear model.
 *
 * <p>PORT26: replaces the removed {@code BlockEntityWithoutLevelRenderer}
 * ({@code SpearItemTileEntityRenderer}) — custom item renderers are now
 * {@code SpecialModelRenderer}s that get registered via
 * {@code RegisterSpecialModelRendererEvent} and referenced from the item
 * model JSON ({@code assets/minecolonies/items/spear.json},
 * {@code "model": {"type": "minecraft:special", "model": {"type": "minecolonies:spear"}}}).
 * Mirrors the vanilla {@code TridentSpecialRenderer}.</p>
 */
public class SpearSpecialRenderer implements NoDataSpecialModelRenderer
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/entity/spear.png");

    private final SpearModel model;

    public SpearSpecialRenderer(final SpearModel model)
    {
        this.model = model;
    }

    @Override
    public void submit(
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      final int overlayCoords,
      final boolean hasFoil,
      final int outlineColor)
    {
        submitNodeCollector.submitModelPart(
          this.model.root(), poseStack, this.model.renderType(TEXTURE), lightCoords, overlayCoords, null, false, hasFoil, -1, null, outlineColor
        );
    }

    @Override
    public void getExtents(final Consumer<Vector3fc> output)
    {
        final PoseStack poseStack = new PoseStack();
        this.model.root().getExtentsForGui(poseStack, output);
    }

    public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked
    {
        public static final MapCodec<SpearSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new SpearSpecialRenderer.Unbaked());

        @Override
        public MapCodec<SpearSpecialRenderer.Unbaked> type()
        {
            return MAP_CODEC;
        }

        @Override
        public SpearSpecialRenderer bake(final SpecialModelRenderer.BakingContext context)
        {
            return new SpearSpecialRenderer(new SpearModel(context.entityModelSet().bakeLayer(ModelLayers.TRIDENT)));
        }
    }
}
