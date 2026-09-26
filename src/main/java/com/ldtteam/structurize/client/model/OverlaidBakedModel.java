package com.ldtteam.structurize.client.model;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This exists because it seems to be the only way to override {@code isCustomRenderer}...
 *
 * <p>PORT26 (model system rework): {@code BakedModel}/{@code BakedModelWrapper} and with them
 * {@code isCustomRenderer()} + {@code applyTransform(...)} no longer exist in 26.1.2 — the baked
 * block model contract is now {@link BlockStateModelPart} (see vanilla's
 * {@code SimpleModelWrapper}), and custom ITEM rendering is data-driven through the item model
 * pipeline instead. The 1.21.1 purpose of this class (marking the tag-substitution item as
 * "rendered by a custom BlockEntityWithoutLevelRenderer", which was the
 * {@code TagSubstitutionRenderer} hooked up via {@code IClientItemExtensions#getCustomRenderer})
 * therefore has no direct equivalent — see the S5-b rework note in the ported
 * {@code ClientLifecycleSubscriber}.
 *
 * <p>The class is retained as a delegating {@link BlockStateModelPart} wrapper (the 26.1.2
 * analogue of {@code BakedModelWrapper}) so client code that needs to identify or wrap the
 * baked overlay model still has a dedicated type to hook onto. It changes no behaviour by
 * itself: every method delegates to the wrapped part.
 */
public class OverlaidBakedModel implements BlockStateModelPart
{
    protected final BlockStateModelPart overlay;

    public OverlaidBakedModel(@NotNull final BlockStateModelPart overlay)
    {
        this.overlay = overlay;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable final Direction direction)
    {
        return this.overlay.getQuads(direction);
    }

    @Override
    public boolean useAmbientOcclusion()
    {
        return this.overlay.useAmbientOcclusion();
    }

    @Override
    public Material.Baked particleMaterial()
    {
        return this.overlay.particleMaterial();
    }

    @Override
    public int materialFlags()
    {
        return this.overlay.materialFlags();
    }
}
