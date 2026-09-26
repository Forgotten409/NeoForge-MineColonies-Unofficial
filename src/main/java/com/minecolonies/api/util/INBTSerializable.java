package com.minecolonies.api.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * PORT26 SHIM: replacement for the removed
 * {@code net.neoforged.neoforge.common.util.INBTSerializable} interface.
 *
 * <p>NeoForge 26.1 removed the legacy INBTSerializable interface
 * (in favor of {@code ValueIOSerializable} and codecs). The MineColonies code base uses
 * the provider-aware NBT serialization contract extensively, so we keep the exact
 * old contract in our own API package.
 *
 * @param <T> the tag type this object serializes to.
 */
public interface INBTSerializable<T extends Tag>
{
    /**
     * Serialize this object to NBT.
     *
     * @param provider the registry access provider.
     * @return the tag.
     */
    T serializeNBT(@NotNull final HolderLookup.Provider provider);

    /**
     * Deserialize this object from NBT.
     *
     * @param provider the registry access provider.
     * @param nbt      the tag to read from.
     */
    void deserializeNBT(@NotNull final HolderLookup.Provider provider, final T nbt);
}
