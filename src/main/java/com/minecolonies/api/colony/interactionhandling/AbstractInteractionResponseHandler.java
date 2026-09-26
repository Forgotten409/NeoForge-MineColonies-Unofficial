package com.minecolonies.api.colony.interactionhandling;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.NbtTagConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.minecolonies.api.util.constant.NbtTagConstants.*;

/**
 * The abstract interaction response handler to be extended by the other ones.
 */
public abstract class AbstractInteractionResponseHandler implements IInteractionResponseHandler
{
    /**
     * The text the citizen is saying.
     */
    private Component inquiry;

    /**
     * The map of response options of the player, to new inquires of the interacting entity.
     */
    private Map<Component, Component> responses = new LinkedHashMap<>();

    /**
     * If the interaction is a primary (true) or secondary (false) interaction.
     */
    private boolean primary;

    /**
     * The interaction priority.
     */
    private IChatPriority priority;

    /**
     * The inquiry of the citizen.
     *
     * @param inquiry        the inquiry.
     * @param primary        if primary inquiry.
     * @param priority       the priority.
     * @param responseTuples optional response options.
     */
    @SafeVarargs
    public AbstractInteractionResponseHandler(
      @NotNull final Component inquiry,
      final boolean primary,
      final IChatPriority priority,
      final Tuple<Component, Component>... responseTuples)
    {
        this.inquiry = inquiry;
        this.primary = primary;
        this.priority = priority;
        for (final Tuple<Component, Component> element : responseTuples)
        {
            this.responses.put(element.getA(), element.getB());
        }
    }

    /**
     * Way to load the response handler.
     */
    public AbstractInteractionResponseHandler()
    {
        // Do nothing, await loading from NBT.
    }

    @Override
    public Component getInquiry()
    {
        return inquiry;
    }

    @Nullable
    @Override
    public Component getResponseResult(final Component response)
    {
        return responses.getOrDefault(response, null);
    }

    @Override
    public List<Component> getPossibleResponses()
    {
        return ImmutableList.copyOf(responses.keySet());
    }

    /**
     * PORT26: reads a component from NBT.
     *
     * <p>BUGFIX (render-thread "Failed to parse thing: 'Failed to parse either… null…'" spam):
     * the port's writer serializes plain components through {@code ComponentSerialization.CODEC}
     * (NbtOps), which stores them as a raw {@code StringTag} (empty component → {@code
     * StringTag("")}, plain text → {@code StringTag("some text")}). The old reader sent every
     * StringTag into the legacy-JSON path, where {@code JsonParser.parseString("")} returns
     * {@code JsonNull} (NOT an exception — verified) and {@code parseString("some text")} throws
     * {@code JsonSyntaxException} — the first produced the exact observed spam (log + swallowed
     * NoSuchElementException), the second silently degraded quest inquiries to empty components.
     *
     * <p>Reader now mirrors the writer: raw strings map to literal components; only strings that
     * look like JSON (leading {@code {} / {@code [} / {@code "}, i.e. 1.21.1-carryover
     * {@code Component.Serializer.toJson} output) take the legacy-JSON path; blank → empty.
     */
    @NotNull
    protected static Component readComponent(@Nullable final Tag tag, @NotNull final HolderLookup.Provider provider)
    {
        if (tag == null)
        {
            return Component.empty();
        }
        if (tag instanceof final net.minecraft.nbt.StringTag stringTag)
        {
            final String value = stringTag.value();
            final String trimmed = value.trim();
            if (trimmed.isEmpty())
            {
                // the port's serialization of Component.empty() — do NOT feed to JsonParser
                // (JsonParser.parseString("") == JsonNull → codec error spam).
                return Component.empty();
            }
            if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\""))
            {
                // legacy (pre-26) JSON string produced by Component.Serializer.toJson
                try
                {
                    final com.google.gson.JsonElement json = com.google.gson.JsonParser.parseString(value);
                    if (json.isJsonNull())
                    {
                        return Component.empty();
                    }
                    return Utils.deserializeCodecMessFromJson(ComponentSerialization.CODEC, provider, json);
                }
                catch (final Exception ex)
                {
                    return Component.empty();
                }
            }
            // plain text — the port's own StringTag form (component codec string branch).
            return Component.literal(trimmed);
        }
        return Utils.deserializeCodecMess(ComponentSerialization.CODEC, provider, tag);
    }

    /**
     * Serialize the response handler to NBT.
     *
     * @return the serialized data.
     */
    public CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag tag = new CompoundTag();
        // PORT26: Component.Serializer (JSON strings) is gone — components are stored as NBT via codec.
        tag.put(TAG_INQUIRY, Utils.serializeCodecMess(ComponentSerialization.CODEC, provider, this.inquiry));
        final ListTag list = new ListTag();
        for (final Map.Entry<Component, Component> element : responses.entrySet())
        {
            final CompoundTag elementTag = new CompoundTag();
            elementTag.put(TAG_RESPONSE, Utils.serializeCodecMess(ComponentSerialization.CODEC, provider, element.getKey()));
            elementTag.put(TAG_NEXT_INQUIRY, Utils.serializeCodecMess(ComponentSerialization.CODEC, provider, element.getValue()));

            list.add(elementTag);
        }
        tag.put(TAG_RESPONSES, list);
        tag.putBoolean(TAG_PRIMARY, isPrimary());
        tag.putInt(TAG_PRIORITY, priority.getPriority());
        tag.putString(NbtTagConstants.TAG_HANDLER_TYPE, getType());
        return tag;
    }
    
    /**
     * Deserialize the response handler from NBT.
     */
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, @NotNull final CompoundTag compoundNBT)
    {
        this.inquiry = readComponent(compoundNBT.get(TAG_INQUIRY), provider);
        final ListTag list = compoundNBT.getListOrEmpty(TAG_RESPONSES);
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag nbt = list.getCompoundOrEmpty(i);
            this.responses.put(readComponent(nbt.get(TAG_RESPONSE), provider), readComponent(nbt.get(TAG_NEXT_INQUIRY), provider));
        }
        this.primary = compoundNBT.getBooleanOr(TAG_PRIMARY, false);
        this.priority = ChatPriority.values()[compoundNBT.getIntOr(TAG_PRIORITY, 0)];
    }

    @Override
    public boolean isPrimary()
    {
        return primary;
    }

    @Override
    public IChatPriority getPriority()
    {
        return this.priority;
    }

    @Override
    public boolean isVisible(final Level world)
    {
        return true;
    }

    @Override
    public boolean isValid(final ICitizenData colony)
    {
        return true;
    }
}
