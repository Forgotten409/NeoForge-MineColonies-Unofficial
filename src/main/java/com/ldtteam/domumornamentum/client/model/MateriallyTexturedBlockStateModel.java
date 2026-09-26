package com.ldtteam.domumornamentum.client.model;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.client.model.properties.ModProperties;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.math.Quadrant;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.extensions.BlockStateModelExtension;
import net.neoforged.neoforge.model.data.ModelData;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The materially textured block state model — PORT 26.1.2 rewrite.
 *
 * <p>Replaces the 1.21.1 {@code MateriallyTexturedBakedModel} (BakedModel + getQuads(state, side,
 * rand, ModelData, renderType)) which no longer exists in the 26.1 model pipeline.
 *
 * <p>New pipeline: the blockstate JSON references this model via
 * {@code {"type": "domum_ornamentum:materially_textured", "model": "..."}} in a variant/multipart
 * entry. During baking the referenced (placeholder) model is baked once via the standard
 * {@link BlockStateModelPart}. During chunk meshing
 * {@link #collectParts(BlockAndTintGetter, BlockPos, BlockState, RandomSource, List)} reads the
 * block entity's {@link ModelData} and swaps the placeholder sprites for the target material
 * sprites — building new {@link net.minecraft.client.resources.model.geometry.BakedQuad} records
 * with remapped UVs and swapped {@link net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo}.
 *
 * <p>{@link #createGeometryKey(BlockAndTintGetter, BlockPos, BlockState, RandomSource)} returns the
 * {@link MaterialTextureData} so the meshing cache can reuse retextured geometry for equal data.
 */
public class MateriallyTexturedBlockStateModel implements DynamicBlockStateModel
{
    /**
     * Cache of retextured quads keyed by the texture data (record — proper equals/hashCode).
     */
    private final Cache<MaterialTextureData, QuadCollection> cache = CacheBuilder.newBuilder()
      .expireAfterAccess(2, TimeUnit.MINUTES)
      .concurrencyLevel(4)
      .maximumSize(1000)
      .build();

    private final BlockStateModelPart innerPart;

    public MateriallyTexturedBlockStateModel(final BlockStateModelPart innerPart)
    {
        this.innerPart = innerPart;
    }

    @Override
    public void collectParts(final RandomSource random, final List<BlockStateModelPart> parts)
    {
        parts.add(innerPart);
    }

    @Override
    public void collectParts(
      final BlockAndTintGetter level,
      final BlockPos pos,
      final BlockState state,
      final RandomSource random,
      final List<BlockStateModelPart> parts)
    {
        final ModelData modelData = level.getModelData(pos);
        if (!modelData.has(ModProperties.MATERIAL_TEXTURE_PROPERTY))
        {
            // no BE data yet — render the placeholder model as-is
            parts.add(innerPart);
            return;
        }

        final MaterialTextureData textureData = modelData.get(ModProperties.MATERIAL_TEXTURE_PROPERTY);
        if (textureData == null || textureData.isEmpty() || !(state.getBlock() instanceof IMateriallyTexturedBlock texturedBlock))
        {
            parts.add(innerPart);
            return;
        }

        final QuadCollection retextured = buildRetexturedQuads(textureData, texturedBlock);
        parts.add(new RetexturedModelPart(retextured, innerPart.useAmbientOcclusion(), retexturedParticle(textureData, innerPart.particleMaterial())));
    }

    @Nullable
    @Override
    public Object createGeometryKey(final BlockAndTintGetter level, final BlockPos pos, final BlockState state, final RandomSource random)
    {
        final ModelData modelData = level.getModelData(pos);
        if (!modelData.has(ModProperties.MATERIAL_TEXTURE_PROPERTY))
        {
            return MaterialTextureData.EMPTY;
        }
        final MaterialTextureData textureData = modelData.get(ModProperties.MATERIAL_TEXTURE_PROPERTY);
        return textureData == null ? MaterialTextureData.EMPTY : textureData;
    }

    @Override
    public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial()
    {
        return innerPart.particleMaterial();
    }

    @Override
    public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial(final BlockAndTintGetter level, final BlockPos pos, final BlockState state)
    {
        final ModelData modelData = level.getModelData(pos);
        if (modelData.has(ModProperties.MATERIAL_TEXTURE_PROPERTY))
        {
            final MaterialTextureData textureData = modelData.get(ModProperties.MATERIAL_TEXTURE_PROPERTY);
            if (textureData != null && !textureData.isEmpty())
            {
                return retexturedParticle(textureData, innerPart.particleMaterial());
            }
        }
        return innerPart.particleMaterial();
    }

    @Override
    public int materialFlags()
    {
        return innerPart.materialFlags();
    }

    private QuadCollection buildRetexturedQuads(final MaterialTextureData textureData, final IMateriallyTexturedBlock block)
    {
        try
        {
            return cache.get(textureData, () -> RetexturedQuadBuilder.retexture(innerPart, textureData, block));
        }
        catch (final Exception e)
        {
            return innerPart instanceof RetexturedModelPart rpm ? rpm.quads : QuadCollection.EMPTY;
        }
    }

    private net.minecraft.client.resources.model.sprite.Material.Baked retexturedParticle(
      final MaterialTextureData textureData,
      final net.minecraft.client.resources.model.sprite.Material.Baked fallback)
    {
        return RetexturedQuadBuilder.retexturedParticle(textureData, fallback);
    }

    /**
     * Simple part wrapper exposing a prebuilt quad collection.
     */
    public record RetexturedModelPart(
      QuadCollection quads,
      boolean useAmbientOcclusion,
      net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial) implements BlockStateModelPart
    {
        @Override
        public List<net.minecraft.client.resources.model.geometry.BakedQuad> getQuads(final net.minecraft.core.@Nullable Direction direction)
        {
            return quads.getQuads(direction);
        }

        @Override
        public int materialFlags()
        {
            return quads.materialFlags();
        }
    }

    /**
     * The unbaked (JSON) form. Two shapes are accepted:
     * <ul>
     *     <li>{@code {"type": "domum_ornamentum:materially_textured", "model": "...", "x": 0, "y": 0, "uvlock": false}}
     *     &mdash; single model (the original 1.21.1 form via the model loader).</li>
     *     <li>{@code {"type": "domum_ornamentum:materially_textured", "models": [{"model": "a", "weight": 40}, {"model": "b", "weight": 30}], "y": 90}}
     *     &mdash; weighted list of models; one is picked per position during meshing (stable per
     *     position, like vanilla weighted blockstate variants).</li>
     * </ul>
     *
     * <p>PORT26 (batch 34, "3 random full racks"): the weighted form restores the original
     * MineColonies rack blockstate, which used vanilla weighted variant arrays with three
     * full-rack models at 40/30/30. The custom-model-type path cannot use vanilla weighted
     * arrays, so the weights live inside this custom model instead.</p>
     *
     * <p>PORT26: a custom block state model replaces the whole variant entry, so the standard
     * rotation fields (x/y/uvlock) are part of THIS codec and applied when baking the wrapped
     * model &mdash; mirrors what vanilla {@code Variant.SimpleModelState} does for normal variants.</p>
     */
    public record Unbaked(
      @Nullable Identifier modelLocation,
      @Nullable WeightedList<Identifier> weightedModels,
      Quadrant x,
      Quadrant y,
      boolean uvLock) implements CustomUnbakedBlockStateModel
    {
        /** One {@code {"model": ..., "weight": ...}} entry of the "models" list. */
        private static final Codec<Weighted<Identifier>> WEIGHTED_MODEL_CODEC = RecordCodecBuilder.create(i -> i.group(
          Identifier.CODEC.fieldOf("model").forGetter(Weighted::value),
          ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(Weighted::weight)
        ).apply(i, Weighted::new));

        /**
         * The non-empty "models" array.
         *
         * <p>PORT26 (compile fix #8): 26.1.2 {@code WeightedList.nonEmptyCodec(Codec<E>)} expects the
         * ELEMENT codec — it wraps the entries itself into the vanilla {@code {"data": E, "weight": n}}
         * form and does NOT accept a {@code Codec<Weighted<E>>} (passing one infers
         * {@code E = Weighted<Identifier>} and produces
         * {@code Codec<WeightedList<Weighted<Identifier>>>}, mismatching the field type). Compose the
         * list codec manually instead — the same composition vanilla uses internally
         * (listOf → xmap({@code WeightedList::of}/{@code WeightedList::unwrap}) → non-empty
         * validate) — so the entry shape stays the original
         * {@code {"model": ..., "weight": ...}} used by the ported blockstates.</p>
         */
        private static final Codec<WeightedList<Identifier>> WEIGHTED_MODELS_CODEC = WEIGHTED_MODEL_CODEC.listOf()
          .xmap(WeightedList::of, WeightedList::unwrap)
          .validate(list -> list.isEmpty()
                              ? DataResult.error(() -> "domum_ornamentum:materially_textured 'models' must contain at least one entry")
                              : DataResult.success(list));

        /** Exactly one of {@code model}/{@code models} must be present. */
        public Unbaked
        {
            if ((modelLocation == null) == (weightedModels == null))
            {
                throw new IllegalArgumentException(
                  "domum_ornamentum:materially_textured requires exactly one of 'model' or 'models'");
            }
        }

        /** Canonical single-model constructor (used by item models and existing blockstates). */
        public Unbaked(final Identifier modelLocation)
        {
            this(modelLocation, null, Quadrant.R0, Quadrant.R0, false);
        }

        /** Codec factory: maps the two optional fields onto the record. */
        static Unbaked create(
          final Optional<Identifier> model,
          final Optional<WeightedList<Identifier>> models,
          final Quadrant x,
          final Quadrant y,
          final boolean uvLock)
        {
            return new Unbaked(model.orElse(null), models.orElse(null), x, y, uvLock);
        }

        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
          Identifier.CODEC.optionalFieldOf("model").forGetter(u -> Optional.ofNullable(u.modelLocation)),
          WEIGHTED_MODELS_CODEC.optionalFieldOf("models").forGetter(u -> Optional.ofNullable(u.weightedModels)),
          Quadrant.CODEC.optionalFieldOf("x", Quadrant.R0).forGetter(Unbaked::x),
          Quadrant.CODEC.optionalFieldOf("y", Quadrant.R0).forGetter(Unbaked::y),
          Codec.BOOL.optionalFieldOf("uvlock", false).forGetter(Unbaked::uvLock)
        ).apply(i, Unbaked::create));

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec()
        {
            return CODEC;
        }

        @Override
        public void resolveDependencies(final ResolvableModel.Resolver resolver)
        {
            if (this.modelLocation != null)
            {
                resolver.markDependency(this.modelLocation);
            }
            else
            {
                this.weightedModels.unwrap().forEach(entry -> resolver.markDependency(entry.value()));
            }
        }

        @Override
        public BlockStateModel bake(final ModelBaker modelBakery)
        {
            final var state = new net.minecraft.client.renderer.block.dispatch.Variant.SimpleModelState(this.x, this.y, Quadrant.R0, this.uvLock);
            if (this.modelLocation != null)
            {
                final BlockStateModelPart part = net.minecraft.client.resources.model.SimpleModelWrapper.bake(modelBakery, this.modelLocation, state.asModelState());
                return new MateriallyTexturedBlockStateModel(part);
            }

            final WeightedList.Builder<MateriallyTexturedBlockStateModel> builder = WeightedList.builder();
            for (final Weighted<Identifier> entry : this.weightedModels.unwrap())
            {
                final BlockStateModelPart part = net.minecraft.client.resources.model.SimpleModelWrapper.bake(modelBakery, entry.value(), state.asModelState());
                builder.add(new MateriallyTexturedBlockStateModel(part), entry.weight());
            }
            return new WeightedMateriallyTexturedModel(builder.build());
        }
    }

    /**
     * Weighted set of materially textured models &mdash; picks one per position (stable per position,
     * like vanilla {@code WeightedVariants}), then applies the material retexturing of the
     * selected submodel.
     *
     * <p>PORT26 (batch 34): restores the original MineColonies rack behavior where each rack
     * position randomly showed one of three "full rack" arrangements (40/30/30).</p>
     */
    public static final class WeightedMateriallyTexturedModel implements DynamicBlockStateModel
    {
        private final WeightedList<MateriallyTexturedBlockStateModel> models;
        private final MateriallyTexturedBlockStateModel first;

        private WeightedMateriallyTexturedModel(final WeightedList<MateriallyTexturedBlockStateModel> models)
        {
            this.models = models;
            this.first = models.unwrap().getFirst().value();
        }

        @Override
        public void collectParts(final RandomSource random, final List<BlockStateModelPart> parts)
        {
            this.models.getRandomOrThrow(random).collectParts(random, parts);
        }

        @Override
        public void collectParts(
          final BlockAndTintGetter level,
          final BlockPos pos,
          final BlockState state,
          final RandomSource random,
          final List<BlockStateModelPart> parts)
        {
            final long seed = random.nextLong();
            random.setSeed(seed);
            final MateriallyTexturedBlockStateModel selected = this.models.getRandomOrThrow(random);
            random.setSeed(seed);
            selected.collectParts(level, pos, state, random, parts);
        }

        @Nullable
        @Override
        public Object createGeometryKey(final BlockAndTintGetter level, final BlockPos pos, final BlockState state, final RandomSource random)
        {
            final long seed = random.nextLong();
            random.setSeed(seed);
            final MateriallyTexturedBlockStateModel selected = this.models.getRandomOrThrow(random);
            random.setSeed(seed);
            final Object subKey = selected.createGeometryKey(level, pos, state, random);
            return subKey == null ? null : new WeightedGeometryKey(selected, subKey);
        }

        @Override
        public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial()
        {
            return this.first.particleMaterial();
        }

        @Override
        public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial(final BlockAndTintGetter level, final BlockPos pos, final BlockState state)
        {
            return this.first.particleMaterial(level, pos, state);
        }

        @Override
        public int materialFlags()
        {
            int flags = 0;
            for (final Weighted<MateriallyTexturedBlockStateModel> entry : this.models.unwrap())
            {
                flags |= entry.value().materialFlags();
            }
            return flags;
        }

        /**
         * Geometry key combining the selected submodel (identity) with its own geometry key, so
         * positions that picked different weighted variants never share meshing cache entries
         * (mirrors {@code CompositeBlockModel.GeometryKey}).
         */
        private record WeightedGeometryKey(MateriallyTexturedBlockStateModel selected, Object subKey)
        {
            @Override
            public boolean equals(final Object o)
            {
                if (this == o)
                {
                    return true;
                }
                if (!(o instanceof final WeightedGeometryKey that))
                {
                    return false;
                }
                return this.selected == that.selected && Objects.equals(this.subKey, that.subKey);
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(System.identityHashCode(this.selected), this.subKey);
            }
        }
    }
}
