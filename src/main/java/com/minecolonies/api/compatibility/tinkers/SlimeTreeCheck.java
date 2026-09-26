package com.minecolonies.api.compatibility.tinkers;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Real slime-tree compatibility — restored for the unofficial <b>Continuum Construct</b>
 * Tinkers port (mod id {@code tconstruct}, MIT, see {@link TinkersToolHelper}'s class
 * javadoc for the licensing/coupling notes).
 *
 * <p>The lumberjack tree scanner ({@code Tree.java}) uses these checks to recognize and
 * chop Tinkers slime trees. The historical upstream implementation (see the commented
 * bodies in {@code portsrc}/…/SlimeTreeCheck) called {@code TinkerTags.Blocks.SLIMY_*}
 * and {@code SlimeDirtBlock}/{@code SlimeLeavesBlock} classes directly; this port
 * implements the identical semantics through <b>block tags only</b> (all shipped by
 * Continuum's datapack — verified against its jar):</p>
 * <ul>
 *   <li>{@code tconstruct:slimy_logs} — greenheart/skyroot/bloodshroom/enderbark logs
 *       (the historical {@code TinkerTags.Blocks.SLIMY_LOGS} — checked by the misleadingly
 *       named {@link #checkForTinkersSlimeBlock});</li>
 *   <li>{@code tconstruct:slimy_leaves} / {@code tconstruct:slimy_saplings} — earth/sky/
 *       ender slime foliage;</li>
 *   <li>{@code tconstruct:slimy_grass} + {@code tconstruct:slimy_soil} — the historical
 *       {@code instanceof SlimeDirtBlock || is(SLIMY_GRASS)} pair, expressed as tags.</li>
 * </ul>
 *
 * <p>{@link #getTinkersLeafVariant} derives the historical {@code FoliageType.ordinal()}
 * (earth=0, sky=1, ichor=2, ender=3) from the block's registry name — a stable surface
 * that needs no Tinkers classes.</p>
 */
public class SlimeTreeCheck extends SlimeTreeProxy
{
    private static final TagKey<Block> SLIMY_LOGS     = makeBlockTag("slimy_logs");
    private static final TagKey<Block> SLIMY_LEAVES   = makeBlockTag("slimy_leaves");
    private static final TagKey<Block> SLIMY_SAPLINGS = makeBlockTag("slimy_saplings");
    private static final TagKey<Block> SLIMY_GRASS    = makeBlockTag("slimy_grass");
    private static final TagKey<Block> SLIMY_SOIL     = makeBlockTag("slimy_soil");

    /**
     * Check if block is part of a slime tree trunk (slime log — historical name kept).
     *
     * @param block the block.
     * @return if the block is a slime log.
     */
    @Override
    public boolean checkForTinkersSlimeBlock(@NotNull final Block block)
    {
        return block.defaultBlockState().is(SLIMY_LOGS);
    }

    /**
     * Check if block is slime leaf.
     *
     * @param block the block.
     * @return if the block is a slime leaf.
     */
    @Override
    public boolean checkForTinkersSlimeLeaves(@NotNull final Block block)
    {
        return block.defaultBlockState().is(SLIMY_LEAVES);
    }

    /**
     * Check if block is slime sapling.
     *
     * @param block the block.
     * @return if the block is a slime sapling.
     */
    @Override
    public boolean checkForTinkersSlimeSapling(@NotNull final Block block)
    {
        return block.defaultBlockState().is(SLIMY_SAPLINGS);
    }

    /**
     * Check if block is slime dirt or slime grass.
     *
     * @param block the block.
     * @return if the block is slime dirt or grass.
     */
    @Override
    public boolean checkForTinkersSlimeDirtOrGrass(@NotNull final Block block)
    {
        return block.defaultBlockState().is(SLIMY_GRASS) || block.defaultBlockState().is(SLIMY_SOIL);
    }

    /**
     * Get the Slime leaf variant (historical {@code FoliageType.ordinal()}).
     *
     * @param leaf the leaf.
     * @return the variant (earth=0, sky=1, ichor=2, ender=3; 0 when unknown).
     */
    @Override
    public int getTinkersLeafVariant(@NotNull final BlockState leaf)
    {
        final String path = registryPath(leaf);
        if (path.contains("sky"))
        {
            return 1;
        }
        if (path.contains("ichor"))
        {
            return 2;
        }
        if (path.contains("ender"))
        {
            return 3;
        }
        return 0; // earth and everything else
    }

    /**
     * @param state any block state.
     * @return the block's registry path (e.g. {@code earth_slime_leaves}), or an empty string.
     */
    private static String registryPath(@NotNull final BlockState state)
    {
        final var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key == null ? "" : key.getPath();
    }

    /**
     * @param path the tag path below the {@code tconstruct} namespace.
     * @return the block tag key.
     */
    private static TagKey<Block> makeBlockTag(final String path)
    {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("tconstruct", path));
    }
}
