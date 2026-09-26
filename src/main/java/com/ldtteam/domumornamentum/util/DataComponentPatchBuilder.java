package com.ldtteam.domumornamentum.util;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Extended builder with update support.
 *
 * <p>PORT26: {@link DataComponentPatch.Builder} now has a private constructor and private
 * state (no subclassing, no direct map access) — instances come from
 * {@link DataComponentPatch#builder()}. This is now a composition wrapper: writes are
 * forwarded to the vanilla builder and mirrored into a local identity map so the
 * read-back ({@code getOrDefault}) and {@code update} operations keep working.
 */
public class DataComponentPatchBuilder
{
    private final DataComponentPatch.Builder inner = DataComponentPatch.builder();
    private final Map<DataComponentType<?>, Optional<?>> values = new IdentityHashMap<>();

    public DataComponentPatch build()
    {
        return inner.build();
    }

    public <T> T getOrDefault(final Supplier<DataComponentType<T>> type, final T defaultValue)
    {
        return getOrDefault(type.get(), defaultValue);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(final DataComponentType<T> type, final T defaultValue)
    {
        return ((Optional<T>) values.getOrDefault(type, Optional.empty())).orElse(defaultValue);
    }

    public <T> DataComponentPatchBuilder update(final Supplier<DataComponentType<T>> type, final T defaultValue, final UnaryOperator<T> updater)
    {
        return update(type.get(), defaultValue, updater);
    }

    public <T> DataComponentPatchBuilder update(final DataComponentType<T> type, final T defaultValue, final UnaryOperator<T> updater)
    {
        return set(type, updater.apply(getOrDefault(type, defaultValue)));
    }

    public <T> DataComponentPatchBuilder set(final Supplier<DataComponentType<T>> type, final T value)
    {
        return set(type.get(), value);
    }

    public <T> DataComponentPatchBuilder set(final DataComponentType<T> type, final T value)
    {
        inner.set(type, value);
        values.put(type, Optional.of(value));
        return this;
    }

    public <T> DataComponentPatchBuilder remove(final DataComponentType<T> type)
    {
        inner.remove(type);
        values.put(type, Optional.empty());
        return this;
    }
}
