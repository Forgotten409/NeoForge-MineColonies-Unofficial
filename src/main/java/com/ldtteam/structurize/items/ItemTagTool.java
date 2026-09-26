package com.ldtteam.structurize.items;

import com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE;
import com.ldtteam.structurize.client.gui.WindowTagTool;
import com.ldtteam.structurize.component.ModDataComponents;
import com.ldtteam.structurize.network.messages.AddRemoveTagMessage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Item for tagging positions with tags
 *
 * <p>PORT26:
 * <ul>
 *   <li>constructor takes the registrar-provided properties (item id must be set before the
 *       Item constructor runs);</li>
 *   <li>{@code Item#use} now returns plain {@link InteractionResult};
 *       {@code displayClientMessage(msg, false)} → {@code sendSystemMessage(msg)};</li>
 *   <li>{@code canAttackBlock} (removed in 26.1) logic moved to
 *       {@link #onBlockLeftClick(Player, ItemStack, BlockPos)} — the tag application that
 *       1.21.1 performed on both sides now runs locally AND sends the existing
 *       {@link AddRemoveTagMessage} from the client, because cancelling the client
 *       left-click event stops the destroy packet (the server would never run its side).</li>
 * </ul>
 */
public class ItemTagTool extends AbstractItemWithPosSelector
{
    /**
     * Creates default scan tool item.
     */
    public ItemTagTool(final Properties properties)
    {
        // PORT26: Properties#setNoRepair was removed; not calling repairable(...) means no
        // REPAIRABLE component, which already prevents anvil repair (see ItemScanTool).
        super(properties.durability(0)
            .rarity(Rarity.UNCOMMON)
            .component(ModDataComponents.TAGS_DATA.get(), TagData.EMPTY));
    }

    @Override
    public AbstractItemWithPosSelector getRegisteredItemInstance()
    {
        return ModItems.tagTool.get();
    }

    @Override
    public InteractionResult onAirRightClick(final BlockPos start, final BlockPos end, final Level worldIn, final Player playerIn, final ItemStack itemStack)
    {
        if (worldIn.isClientSide())
        {
            final TagData tagData = TagData.readFromItemStack(itemStack);
            if (tagData.anchorPos().isEmpty())
            {
                playerIn.sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.noanchor"));
                return InteractionResult.FAIL;
            }

            final WindowTagTool window = new WindowTagTool(tagData.currentTag().orElse(""), tagData.anchorPos().get(), worldIn, itemStack);
            window.open();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level worldIn, Player playerIn, InteractionHand handIn)
    {
        return onAirRightClick(
            null,
            null,
            worldIn,
            playerIn,
            playerIn.getItemInHand(handIn));
    }

    @Override
    public InteractionResult useOn(final UseOnContext context)
    {
        if (context.getPlayer() == null)
        {
            return InteractionResult.SUCCESS;
        }

        // Set anchor
        if (context.getPlayer().isShiftKeyDown())
        {
            BlockEntity te = context.getLevel().getBlockEntity(context.getClickedPos());
            if (te instanceof IBlueprintDataProviderBE)
            {
                TagData.updateItemStack(context.getItemInHand(), tags -> tags.setAnchorPos(context.getClickedPos()));
                if (context.getLevel().isClientSide())
                {
                    context.getPlayer().sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.anchorsaved"));
                }
                return InteractionResult.SUCCESS;
            }
            else
            {
                if (context.getLevel().isClientSide())
                {
                    context.getPlayer().sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.anchor.notvalid"));
                }
                return InteractionResult.FAIL;
            }
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * PORT26: former {@code canAttackBlock} — applies/removes the current tag at the
     * clicked position relative to the anchor. Runs whenever the left-click event fires
     * (both sides when delivered); the client additionally sends
     * {@link AddRemoveTagMessage} so the server BE stays in sync (client event
     * cancellation stops the destroy packet, so the server side never fires for normal
     * clicks).
     */
    @Override
    protected void onBlockLeftClick(final Player player, final ItemStack stack, final BlockPos pos)
    {
        final TagData tagData = TagData.readFromItemStack(stack);

        if (tagData.anchorPos().isEmpty())
        {
            player.sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.noanchor"));
            return;
        }

        if (tagData.currentTag().isEmpty())
        {
            player.sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.notag"));
            return;
        }

        // Apply tag to item
        final BlockPos anchorPos = tagData.anchorPos().get();
        final String currentTag = tagData.currentTag().get();
        BlockPos relativePos = pos.subtract(anchorPos);

        final BlockEntity te = player.level().getBlockEntity(anchorPos);
        if (!(te instanceof final IBlueprintDataProviderBE blueprintBe))
        {
            player.sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.anchor.notvalid"));
            TagData.updateItemStack(stack, tags -> tags.setAnchorPos(null));
            return;
        }

        // add/remove tags
        Map<BlockPos, List<String>> tagPosMap = blueprintBe.getPositionedTags();

        final boolean add;
        if (!tagPosMap.containsKey(relativePos) || !tagPosMap.get(relativePos).contains(currentTag))
        {
            blueprintBe.addTag(relativePos, currentTag);
            add = true;
            if (player.level().isClientSide())
            {
                player.sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.addtag",
                        currentTag,
                        player.level().getBlockState(pos).getBlock().getName()));
            }
        }
        else
        {
            blueprintBe.removeTag(relativePos, currentTag);
            add = false;
            if (player.level().isClientSide())
            {
                player.sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.removed",
                        currentTag,
                        player.level().getBlockState(pos).getBlock().getName()));
            }
        }

        // PORT26: keep the server BE in sync — the cancelled client event stops the server
        // side of the click from ever firing.
        if (player.level().isClientSide())
        {
            new AddRemoveTagMessage(add, currentTag, relativePos, anchorPos).sendToServer();
        }
    }

    /**
     * Data components for storing start and end pos
     */
    public record TagData(Optional<BlockPos> anchorPos, Optional<String> currentTag)
    {
        public static final TagData EMPTY = new TagData(Optional.empty(), Optional.empty());

        public static final Codec<TagData> CODEC = RecordCodecBuilder.create(
            builder -> builder
                .group(BlockPos.CODEC.optionalFieldOf("anchor_pos_tag").forGetter(TagData::anchorPos),
                    Codec.STRING.optionalFieldOf("current_tag").forGetter(TagData::currentTag))
                .apply(builder, TagData::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, TagData> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
                TagData::anchorPos,
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
                TagData::currentTag,
                TagData::new);

        /**
         * For use with {@link ItemStack#update(DataComponentType, Object, UnaryOperator)}
         */
        public TagData setAnchorPos(final BlockPos pos)
        {
            return new TagData(Optional.ofNullable(pos), currentTag);
        }

        /**
         * For use with {@link ItemStack#update(DataComponentType, Object, UnaryOperator)}
         */
        public TagData setCurrentTag(final String currentTag)
        {
            return new TagData(anchorPos, Optional.ofNullable(currentTag.isEmpty() ? null : currentTag));
        }

        /**
         * Writes this tagData into given itemStack.
         */
        public void writeToItemStack(final ItemStack itemStack)
        {
            itemStack.set(ModDataComponents.TAGS_DATA.get(), this);
        }

        /**
         * @return tagData stored in given itemStack (or empty instance)
         */
        public static TagData readFromItemStack(final ItemStack itemStack)
        {
            return itemStack.getOrDefault(ModDataComponents.TAGS_DATA.get(), TagData.EMPTY);
        }

        /**
         * Performs updating of tagData in given itemStack
         */
        public static void updateItemStack(final ItemStack itemStack, final UnaryOperator<TagData> updater)
        {
            updater.apply(readFromItemStack(itemStack)).writeToItemStack(itemStack);
        }
    }
}
