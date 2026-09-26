package com.ldtteam.domumornamentum.block;

/**
 * PORT26: {@code net.minecraft.data.models.blockstates.PropertyDispatch} moved to
 * {@code net.minecraft.client.data.models.blockstates} and lost its {@code QuadFunction}
 * inner interface — we ship our own (only used for destroy-progress delegation in
 * {@link IMateriallyTexturedBlock#getDODestroyProgress}).
 *
 * <p>Moved to its own file: Java only allows one public top-level type per compilation
 * unit, and the 1.21.1 sources inlined this next to {@code IMateriallyTexturedBlock}.
 */
@FunctionalInterface
public interface QuadFunction<A, B, C, D, R>
{
    R apply(A a, B b, C c, D d);
}
