package com.ldtteam.structurize.client.model;

import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Simple wrapper to create {@link OverlaidBakedModel}.
 *
 * <p>PORT26 (model system rework): the 1.21.1 version implemented NeoForge's
 * {@code IUnbakedGeometry} and manually baked the referenced overlay model via
 * {@code baker.getModel(overlayModelId).bake(...)}, wrapping the result in
 * {@link OverlaidBakedModel}. In 26.1.2 {@code IUnbakedGeometry}/{@code BakedModel} no longer
 * exist — the pipeline is {@link UnbakedModel} → {@code ResolvedModel} →
 * {@code BlockStateModelPart}.
 *
 * <p>The port achieves the same visual result declaratively: {@link #parent()} returns the
 * overlay model location, so vanilla's {@code ModelDiscovery} links the parent chain exactly
 * like a plain {@code "parent"} reference would — the overlay's geometry, textures and
 * transforms are inherited through the standard top-model resolution
 * ({@code ResolvedModel#getTopGeometry} et al.) and baked into the regular
 * {@code SimpleModelWrapper}. No manual baking hook is needed (or possible) anymore.
 *
 * <p><b>BUGFIX (user report: "tag anchor block has no texture"):</b> {@code geometry()} MUST
 * return {@code null} — the vanilla default — to signal "no own geometry, inherit from
 * parent". {@code ResolvedModel#findTopGeometry} walks the parent chain only while
 * {@code geometry() == null}; returning {@code UnbakedGeometry.EMPTY} (a non-null instance)
 * short-circuits the lookup and bakes an empty quad collection, which made the overlay
 * (and with it the whole tag anchor block + item) render completely invisible. This mirrors
 * vanilla JSON models without an {@code "elements"} section, whose deserialized
 * {@code geometry()} is also null.
 */
public class OverlaidGeometry implements UnbakedModel
{
    private final Identifier overlayModelId;

    public OverlaidGeometry(final Identifier overlayModelId)
    {
        this.overlayModelId = overlayModelId;
    }

    /**
     * The overlay model this model inherits everything from (geometry, textures, transforms).
     */
    @Nullable
    @Override
    public Identifier parent()
    {
        return this.overlayModelId;
    }

    @Override
    public @Nullable UnbakedGeometry geometry()
    {
        // no own geometry — everything comes from the overlay parent chain.
        // MUST be null (not UnbakedGeometry.EMPTY!): ResolvedModel.findTopGeometry stops at
        // the first NON-NULL geometry, and EMPTY bakes to zero quads = invisible block.
        return null;
    }
}
