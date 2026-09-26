package com.ldtteam.domumornamentum.client.color;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.RetexturedQuadBuilder;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.client.model.properties.ModProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.model.data.ModelData;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Tint source for materially textured blocks — PORT 26.1.2 rewrite of
 * {@code MateriallyTexturedBlockBlockColor}.
 *
 * <p>26.1.2 replaced the old {@code BlockColor} interface with per-block lists of
 * {@link BlockTintSource} layers; a quad's tint index selects the layer. Each layer instance is
 * created with its layer index, which encodes {@code componentIndex << 3 | targetTintIndex}
 * (see {@link RetexturedQuadBuilder#encodeTintIndex}). The source resolves the contained block
 * from the block entity's model data and delegates to the contained block's own tint source.
 */
public final class MateriallyTexturedBlockTintSource implements BlockTintSource
{
    private final int layer;

    public MateriallyTexturedBlockTintSource(final int layer)
    {
        this.layer = layer;
    }

    /**
     * PORT26: {@code BlockTintSource} declares {@code color(BlockState)} as its abstract
     * method (with {@code colorInWorld} as the world-aware default). Resolving the
     * contained block requires block-entity model data, which is only available with
     * world context — so the context-free variant returns the neutral tint.
     */
    @Override
    public int color(final BlockState state)
    {
        return -1;
    }

    @Override
    public int colorInWorld(final BlockState state, @Nullable final BlockAndTintGetter level, @Nullable final BlockPos pos)
    {
        if (!(state.getBlock() instanceof IMateriallyTexturedBlock texturedBlock))
        {
            return -1;
        }

        final int componentIndex = RetexturedQuadBuilder.decodeComponentIndex(layer);
        final int targetTintIndex = RetexturedQuadBuilder.decodeTargetTintIndex(layer);

        // find the component this layer belongs to (PORT26: getComponents() is a Collection — index access via helper)
        final var components = texturedBlock.getComponents();
        if (componentIndex >= components.size())
        {
            return -1;
        }
        final IMateriallyTexturedBlockComponent component = IMateriallyTexturedBlock.getComponentAtIndex(components, componentIndex);
        if (component == null)
        {
            return -1;
        }

        // resolve the contained block from the block entity data
        if (level == null || pos == null)
        {
            return -1;
        }
        final ModelData modelData = level.getModelData(pos);
        if (!modelData.has(ModProperties.MATERIAL_TEXTURE_PROPERTY))
        {
            return -1;
        }
        final MaterialTextureData textureData = modelData.get(ModProperties.MATERIAL_TEXTURE_PROPERTY);
        if (textureData == null)
        {
            return -1;
        }
        final var containedBlock = textureData.getTexturedComponents().get(component.getId());
        if (containedBlock == null)
        {
            return -1;
        }

        // delegate to the contained block's own tint source for the target tint layer
        final BlockState containedState = containedBlock.defaultBlockState();
        final BlockTintSource containedSource = Minecraft.getInstance().getBlockColors().getTintSource(containedState, targetTintIndex);
        return containedSource == null ? -1 : containedSource.colorInWorld(containedState, level, pos);
    }

    @Override
    public Set<Property<?>> relevantProperties()
    {
        return Set.of();
    }
}
