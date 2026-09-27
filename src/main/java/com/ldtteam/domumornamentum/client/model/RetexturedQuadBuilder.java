package com.ldtteam.domumornamentum.client.model;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Builds retextured {@link BakedQuad}s — PORT 26.1.2 rewrite of the 1.21.1
 * {@code RetexturedBakedModelBuilder} + {@code ModelSpriteQuadTransformer}.
 *
 * <p>26.1.2 {@link BakedQuad} is a record: {@code (position0..3, packedUV0..3, direction, MaterialInfo)}
 * — plus the two NeoForge components {@code bakedNormals}/{@code bakedColors} (per-vertex normals
 * and colors filled by the patched FaceBakery; see the note at {@link #retextureQuad}), which the
 * retextured quad inherits from its source. Retexturing a quad means:
 * <ol>
 *   <li>keeping positions + direction,</li>
 *   <li>remapping each vertex UV from the placeholder sprite's atlas window into the target
 *       sprite's window ({@link UVPair} unpack → normalize 0..1 → repack),</li>
 *   <li>swapping the {@link BakedQuad.MaterialInfo} for the target material's info (sprite, render
 *       layer, item render type) while re-encoding the tint index so the tint source can resolve
 *       the contained block's color.</li>
 * </ol>
 *
 * <p>Tint index encoding: {@code componentIndex << 3 | targetTintIndex} — the block's registered
 * tint source list has {@code TINT_LAYERS} entries, each decoding the layer index back into
 * component + target tint (see {@code MateriallyTexturedBlockTintSource}).
 *
 * <p>PORT26 (batch 13) — ERASURE: a texture mapped to {@code minecraft:air} (or whose target
 * model exposes no geometry for the face) removes the quad, exactly like the 1.21.1
 * {@code RetexturedBakedModelBuilder} did ({@code needsErasure} / empty retexturing-quad).
 * This is required by the dynamic timber frame, whose block entity maps slots to AIR to hide
 * merged corner/connection geometry.
 */
public final class RetexturedQuadBuilder
{
    /** Number of tint layers registered per materially textured block. */
    public static final int TINT_LAYERS = 1 << 6; // 64: 8 components x 8 target tints
    private static final int TINT_BITS = 3;

    private static final RandomSource RANDOM = RandomSource.createThreadSafe();

    private RetexturedQuadBuilder()
    {
    }

    /**
     * Retextures all quads of the given part according to the texture data.
     *
     * @param source      the placeholder model part
     * @param textureData material texture data from the block entity / item stack
     * @param block       the materially textured block (component list source)
     * @return retextured quad collection
     */
    public static QuadCollection retexture(final BlockStateModelPart source, final MaterialTextureData textureData, final IMateriallyTexturedBlock block)
    {
        final QuadCollection.Builder builder = new QuadCollection.Builder();

        for (final Direction direction : Direction.values())
        {
            for (final BakedQuad quad : source.getQuads(direction))
            {
                final BakedQuad retextured = retextureQuad(quad, direction, textureData, block);
                if (retextured != null)
                {
                    builder.addCulledFace(direction, retextured);
                }
            }
        }
        for (final BakedQuad quad : source.getQuads(null))
        {
            final BakedQuad retextured = retextureQuad(quad, null, textureData, block);
            if (retextured != null)
            {
                builder.addUnculledFace(retextured);
            }
        }

        return builder.build();
    }

    /**
     * Resolves the particle material for retextured data (target block's particle icon when the
     * placeholder particle is a replaced texture).
     */
    public static net.minecraft.client.resources.model.sprite.Material.Baked retexturedParticle(
      final MaterialTextureData textureData,
      final net.minecraft.client.resources.model.sprite.Material.Baked fallback)
    {
        final Identifier particleName = fallback.sprite().contents().name();
        final Block target = textureData.getTexturedComponents().get(particleName);
        // PORT26 (batch 13): AIR-mapped particles keep the placeholder particle — 1.21.1's
        // needsErasure kept the source particle icon as well; this also avoids asking the (empty)
        // air model for a particle material.
        if (target == null || target.defaultBlockState().isAir())
        {
            return fallback;
        }
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(target.defaultBlockState()).particleMaterial();
    }

    @Nullable
    private static BakedQuad retextureQuad(
      final BakedQuad quad,
      @Nullable final Direction cullDirection,
      final MaterialTextureData textureData,
      final IMateriallyTexturedBlock block)
    {
        final TextureAtlasSprite sourceSprite = quad.materialInfo().sprite();
        final Identifier sourceTextureName = sourceSprite.contents().name();

        // does this quad's texture correspond to one of the retexturable components?
        final Block target = textureData.getTexturedComponents().get(sourceTextureName);
        if (target == null)
        {
            return quad; // keep placeholder
        }

        // PORT26 (batch 13) — 1.21.1 ERASURE SEMANTICS. The original
        // RetexturedBakedModelBuilder#build dropped quads whose texture was explicitly mapped to
        // AIR (needsErasure) — the dynamic timber frame uses this to REMOVE geometry: its block
        // entity maps e.g. the four corner slots (purple/green wool, white/light-blue terracotta
        // debug textures) to AIR when neighbouring frames merge, and the connection pieces
        // (concrete debug textures) to AIR while disconnected. The port kept the placeholder
        // quads instead, so connected dynamic frames showed the raw 4-colour debug corners
        // overlapping the real geometry. A mapping to AIR now removes the quad entirely.
        if (target.defaultBlockState().isAir())
        {
            return null;
        }

        final int componentIndex = componentIndexOf(block, sourceTextureName);

        // find the target model's first quad for the (cull) direction to steal its material info
        final BlockState targetState = target.defaultBlockState();
        final var targetModel = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(targetState);
        final List<BlockStateModelPart> targetParts = new java.util.ArrayList<>();
        targetModel.collectParts(RANDOM, targetParts);

        BakedQuad targetQuad = findTargetQuad(targetParts, cullDirection);
        if (targetQuad == null)
        {
            targetQuad = findTargetQuad(targetParts, quad.direction());
        }
        if (targetQuad == null)
        {
            // PORT26 (batch 13) — 1.21.1 parity: a mapped texture whose target model has no
            // geometry for this face was DROPPED (getRetexturingQuad -> Optional.empty ->
            // ifPresent skipped the quad), not kept as a placeholder. Without this the dynamic
            // timber frame keeps its debug-textured connection pieces whenever the target model
            // exposes no matching face.
            return null;
        }

        final TextureAtlasSprite targetSprite = targetQuad.materialInfo().sprite();
        final int tintIndex = encodeTintIndex(componentIndex, targetQuad.materialInfo().tintIndex());

        final BakedQuad.MaterialInfo newInfo = new BakedQuad.MaterialInfo(
          targetSprite,
          targetQuad.materialInfo().layer(),
          targetQuad.materialInfo().itemRenderType(),
          tintIndex,
          targetQuad.materialInfo().shade(),
          targetQuad.materialInfo().lightEmission());

        // PORT26 FIX v5 (0.4.5, GUI items pale/flat with Sodium installed): the retextured
        // quad MUST carry the NeoForge per-vertex normals and colors of the SOURCE quad.
        //
        // NeoForge 26.1 extends the vanilla BakedQuad record with two extra components —
        // BakedNormals and BakedColors — which the vanilla FaceBakery fills for every
        // normally-baked quad. Vanilla's VertexConsumer#putBakedQuad falls back to the
        // quad's direction for UNSPECIFIED normals, but Sodium's fast path
        // (BakedModelEncoder.writeQuadVertices, via BufferBuilderMixin#putBakedQuad)
        // resolves the per-vertex normal through BakedQuadView#getAccurateNormal: for
        // UNSPECIFIED it yields the sentinel -1 (NOT the face-normal fallback) which is
        // written as a ~zero byte normal → item.vsh's minecraft_mix_light dots it with
        // both GUI lights at ~0 → every face gets the uniform 0.4 ambient term → the
        // "pale, no shadows, no contrast" DO item icons whenever Sodium is installed
        // (Iris itself is uninvolved — it just always ships alongside). The same fast
        // path also multiplies the vertex color with BakedColors, so an accidentally
        // non-white default would tint the item. The retextured quad keeps the source
        // quad's positions 1:1, so its normals and colors remain exactly correct —
        // pass them through instead of dropping them to UNSPECIFIED/DEFAULT.
        return new BakedQuad(
          quad.position0(), quad.position1(), quad.position2(), quad.position3(),
          remapUV(quad.packedUV0(), sourceSprite, targetSprite),
          remapUV(quad.packedUV1(), sourceSprite, targetSprite),
          remapUV(quad.packedUV2(), sourceSprite, targetSprite),
          remapUV(quad.packedUV3(), sourceSprite, targetSprite),
          quad.direction(),
          newInfo,
          quad.bakedNormals(),
          quad.bakedColors());
    }

    @Nullable
    private static BakedQuad findTargetQuad(final List<BlockStateModelPart> parts, @Nullable final Direction direction)
    {
        for (final BlockStateModelPart part : parts)
        {
            final List<BakedQuad> quads = part.getQuads(direction);
            if (!quads.isEmpty())
            {
                return quads.get(0);
            }
        }
        return null;
    }

    /**
     * Remaps a packed UV from the source sprite window into the target sprite window.
     */
    private static long remapUV(final long packedUV, final TextureAtlasSprite from, final TextureAtlasSprite to)
    {
        final float u = UVPair.unpackU(packedUV);
        final float v = UVPair.unpackV(packedUV);

        final float u0 = from.getU0();
        final float u1 = from.getU1();
        final float v0 = from.getV0();
        final float v1 = from.getV1();

        final float normU = (u - u0) / (u1 - u0);
        final float normV = (v - v0) / (v1 - v0);

        return UVPair.pack(to.getU(normU), to.getV(normV));
    }

    private static int componentIndexOf(final IMateriallyTexturedBlock block, final Identifier componentId)
    {
        int idx = 0;
        for (final IMateriallyTexturedBlockComponent component : block.getComponents())
        {
            if (component.getId().equals(componentId))
            {
                return idx;
            }
            idx++;
        }
        return 0;
    }

    /**
     * Encodes component index + target tint index into the tint layer index.
     */
    public static int encodeTintIndex(final int componentIndex, final int targetTintIndex)
    {
        if (targetTintIndex < 0)
        {
            // quad was not tinted on the target — no tint layer
            return -1;
        }
        return (componentIndex << TINT_BITS) | (targetTintIndex & 0x7);
    }

    /**
     * Decodes a tint layer index back into component + target tint.
     */
    public static int decodeComponentIndex(final int tintLayer)
    {
        return tintLayer >>> TINT_BITS;
    }

    /**
     * Decodes a tint layer index back into the target tint index.
     */
    public static int decodeTargetTintIndex(final int tintLayer)
    {
        return tintLayer & 0x7;
    }

    /**
     * Convenience: the block whose model part's first quad defines the replacement material for a
     * texture name (used by the item model path).
     */
    @Nullable
    public static BlockState targetStateFor(final MaterialTextureData textureData, final Identifier textureName)
    {
        final Block block = textureData.getTexturedComponents().get(textureName);
        return block == null ? null : block.defaultBlockState();
    }
}
