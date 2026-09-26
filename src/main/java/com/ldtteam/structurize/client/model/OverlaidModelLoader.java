package com.ldtteam.structurize.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import org.jetbrains.annotations.NotNull;

/**
 * Simple loader to create {@link OverlaidGeometry}.
 *
 * <p>PORT26: {@code net.neoforged.neoforge.client.model.geometry.IGeometryLoader} was removed
 * with the 1.21.4+ model system rework; the 26.1.2 equivalent is
 * {@link UnbakedModelLoader} (registered via {@code ModelEvent.RegisterLoaders}, which the
 * ported {@code ClientLifecycleSubscriber#registerGeometry} already does). The JSON contract
 * is unchanged: a model with {@code "loader": "structurize:overlaid"} and a {@code "parent"}
 * pointing at the overlay model.
 */
public class OverlaidModelLoader implements UnbakedModelLoader<OverlaidGeometry>
{
    @NotNull
    @Override
    public OverlaidGeometry read(@NotNull JsonObject jsonObject,
                                 @NotNull JsonDeserializationContext deserializationContext) throws JsonParseException
    {
        final String parent = jsonObject.get("parent").getAsString();
        final Identifier parentLocation = Identifier.parse(parent);

        return new OverlaidGeometry(parentLocation);
    }
}
