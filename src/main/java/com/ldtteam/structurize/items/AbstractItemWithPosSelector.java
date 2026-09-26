package com.ldtteam.structurize.items;

import com.ldtteam.structurize.api.Utils;
import com.ldtteam.structurize.component.ModDataComponents;
import com.ldtteam.structurize.network.messages.SyncPosSelectionMessage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Optional;
import java.util.function.UnaryOperator;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract item mechanic for pos selecting
 *
 * <p>PORT26:
 * <ul>
 *   <li>{@code Item#canAttackBlock} was REMOVED in 26.1 (full-jar bytecode search — zero
 *       references). The start-position capture now lives in
 *       {@link #handleLeftClickBlock(PlayerInteractEvent.LeftClickBlock)}, a
 *       {@code PlayerInteractEvent.LeftClickBlock} handler (fired on both sides by NeoForge,
 *       cancellable — the 1.21.1 {@code canAttackBlock} returned false, i.e. also prevented
 *       breaking). Because cancelling on the client also stops the destroy packet (so the
 *       server-side event never fires for normal clicks), the client branch additionally
 *       sends {@link SyncPosSelectionMessage} to keep the server stack in sync — this
 *       preserves the 1.21.1 both-sides component update that {@code canAttackBlock}
 *       provided. Registration happens in the mod event subscriber.</li>
 *   <li>{@code Item#use} now returns plain {@link InteractionResult} (InteractionResultHolder
 *       was removed — the held stack is mutated in place, no wrapper needed).</li>
 *   <li>{@code Player#displayClientMessage(msg, false)} → {@code sendSystemMessage(msg)}.</li>
 * </ul>
 */
public abstract class AbstractItemWithPosSelector extends Item
{
    private static final String START_POS_TKEY   = "item.possetter.firstpos";
    private static final String END_POS_TKEY     = "item.possetter.secondpos";
    private static final String MISSING_POS_TKEY = "item.possetter.missingpos";

    /**
     * MC redirect.
     *
     * @param properties item properties
     */
    public AbstractItemWithPosSelector(final Properties properties)
    {
        // PORT26: POS_SELECTION is a DeferredHolder now — .get() yields the component type
        super(properties.component(ModDataComponents.POS_SELECTION.get(), PosSelection.EMPTY));
    }

    /**
     * Is called when player air-right-clicks with item.
     *
     * @param start    first pos
     * @param end      second pos
     * @param worldIn  event world
     * @param playerIn event player
     * @return event result, typically success
     */
    public abstract InteractionResult onAirRightClick(BlockPos start, BlockPos end, Level worldIn, Player playerIn, ItemStack itemStack);

    /**
     * Uses to search for correct itemstack in both hands.
     *
     * @return item reference from {@link ModItems}
     */
    public abstract AbstractItemWithPosSelector getRegisteredItemInstance();

    /**
     * Structurize: Calls {@link AbstractItemWithPosSelector#onAirRightClick(BlockPos, BlockPos, Level, Player, ItemStack)}.
     * {@inheritDoc}
     */
    @Override
    public InteractionResult use(final Level worldIn, final Player playerIn, final InteractionHand handIn)
    {
        final ItemStack itemstack = playerIn.getItemInHand(handIn);
        final PosSelection compound = PosSelection.readFromItemStack(itemstack);

        if (compound.startPos().isEmpty())
        {
            if (worldIn.isClientSide())
            {
                playerIn.sendSystemMessage(Component.translatable(MISSING_POS_TKEY + "1"));
            }
            return InteractionResult.FAIL;
        }

        if (compound.endPos().isEmpty())
        {
            if (worldIn.isClientSide())
            {
                playerIn.sendSystemMessage(Component.translatable(MISSING_POS_TKEY + "2"));
            }
            return InteractionResult.FAIL;
        }

        return onAirRightClick(
            compound.startPos().get(),
            compound.endPos().get(),
            worldIn,
            playerIn,
            itemstack);
    }

    /**
     * Structurize: Captures second position or Anchor Pos.
     * {@inheritDoc}
     */
    @Override
    public InteractionResult useOn(final UseOnContext context)
    {
        final BlockPos pos = context.getClickedPos();
        if (context.getLevel().isClientSide())
        {
            context.getPlayer().sendSystemMessage(Component.translatable(END_POS_TKEY, pos.getX(), pos.getY(), pos.getZ()));
            Utils.playSuccessSound(context.getPlayer());
        }
        PosSelection.updateItemStack(context.getItemInHand(), data -> data.setEndpos(pos));
        return InteractionResult.SUCCESS;
    }

    /**
     * PORT26: replacement for the removed {@code Item#canAttackBlock(BlockState, Level, BlockPos, Player)}
     * (1.21.1 semantics: capture the start position on the held tool, message+sound on the
     * client, never actually break the block). Registered on the NeoForge event bus by the
     * mod event subscriber. Dispatches to the overridable {@link #onBlockLeftClick} so
     * subclasses with custom left-click logic (ItemTagTool, ItemScanTool) keep working.
     *
     * @param event the left-click-block event (fires on both sides)
     */
    public static void handleLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event)
    {
        if (!(event.getItemStack().getItem() instanceof final AbstractItemWithPosSelector selector))
        {
            return;
        }

        selector.onBlockLeftClick(event.getEntity(), event.getItemStack(), event.getPos());
        // 1.21.1 canAttackBlock always returned false — never break blocks with the tool
        event.setCanceled(true);
    }

    /**
     * PORT26: left-click handling (replaces the removed {@code Item#canAttackBlock} hook,
     * which ran on both sides). Default: capture the start position on the stack.
     * Override for custom logic — see ItemTagTool/ItemScanTool.
     *
     * @param player the clicking player
     * @param stack  the held tool stack
     * @param pos    the clicked block pos
     */
    protected void onBlockLeftClick(final Player player, final ItemStack stack, final BlockPos pos)
    {
        PosSelection.updateItemStack(stack, data -> data.setStartPos(pos));
        if (player.level().isClientSide())
        {
            Utils.playSuccessSound(player);
            player.sendSystemMessage(Component.translatable(START_POS_TKEY, pos.getX(), pos.getY(), pos.getZ()));
            // Cancelling the client event stops the destroy packet — the server would never
            // see this click, so push the captured start pos explicitly (1.21.1
            // canAttackBlock ran on both sides and updated both stacks).
            new SyncPosSelectionMessage(stack, InteractionHand.MAIN_HAND).sendToServer();
        }
    }

    /**
     * Structurize: Prevent block breaking server side.
     *
     * <p>PORT26: {@code Item#canAttackBlock} no longer exists on vanilla Item in 26.1 — this is
     * a plain Java method now (NOT an override; never called by vanilla), kept so that the
     * 1.21.1 subclass overrides keep compiling. The live logic moved to
     * {@link #handleLeftClickBlock(PlayerInteractEvent.LeftClickBlock)}.
     */
    public boolean canAttackBlock(final BlockState state, final Level worldIn, final BlockPos pos, final Player player)
    {
        return false;
    }

    /**
     * Override this so items have instant click in survival.
     */
    @Override
    public float getDestroySpeed(final ItemStack stack, final BlockState state)
    {
        return Float.MAX_VALUE;
    }

    /**
     * Saves the start/end coordinates on this stack.
     * @param tool The tool stack (assumed already been validated)
     * @param start The new start position
     * @param end The new end position
     * @deprecated use datacomponents
     */
    @Deprecated(forRemoval = true, since = "1.21")
    public static void setBounds(@NotNull final ItemStack tool,
                                 @NotNull final BlockPos start,
                                 @NotNull final BlockPos end)
    {
        PosSelection.updateItemStack(tool, data -> data.setSelection(start, end));
    }

    /**
     * Loads the start/end coordinates from this stack.
     * @param tool The tool stack (assumed already been validated)
     * @return the start/end positions
     * @deprecated use datacomponents
     */
    @Deprecated(forRemoval = true, since = "1.21")
    public static Tuple<BlockPos, BlockPos> getBounds(@NotNull final ItemStack tool)
    {
        final PosSelection tag = PosSelection.readFromItemStack(tool);
        return new Tuple<>(tag.startPos().orElse(null), tag.endPos().orElse(null));
    }

    /**
     * Data components for storing start and end pos
     */
    public record PosSelection(Optional<BlockPos> startPos, Optional<BlockPos> endPos)
    {
        public static final PosSelection EMPTY = new PosSelection(Optional.empty(), Optional.empty());

        public static final Codec<PosSelection> CODEC = RecordCodecBuilder.create(
            builder -> builder
                .group(BlockPos.CODEC.optionalFieldOf("start_pos").forGetter(PosSelection::startPos),
                    BlockPos.CODEC.optionalFieldOf("end_pos").forGetter(PosSelection::endPos))
                .apply(builder, PosSelection::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, PosSelection> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
                PosSelection::startPos,
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
                PosSelection::endPos,
                PosSelection::new);

        /**
         * @return true if both start and end positions are set
         */
        public boolean hasSelection()
        {
            return startPos.isPresent() && endPos.isPresent();
        }

        /**
         * For use with {@link ItemStack#update(DataComponentType, Object, UnaryOperator)}
         */
        public PosSelection setStartPos(final BlockPos pos)
        {
            return new PosSelection(Optional.ofNullable(pos), endPos);
        }

        /**
         * For use with {@link ItemStack#update(DataComponentType, Object, UnaryOperator)}
         */
        public PosSelection setEndpos(final BlockPos pos)
        {
            return new PosSelection(startPos, Optional.ofNullable(pos));
        }

        /**
         * For use with {@link ItemStack#update(DataComponentType, Object, UnaryOperator)}
         */
        public PosSelection setSelection(final BlockPos startPos, final BlockPos endPos)
        {
            return new PosSelection(Optional.ofNullable(startPos), Optional.ofNullable(endPos));
        }

        /**
         * Writes this posSelection into given itemStack.
         */
        public void writeToItemStack(final ItemStack itemStack)
        {
            itemStack.set(ModDataComponents.POS_SELECTION.get(), this);
        }

        /**
         * @return posSelection stored in given itemStack (or empty instance)
         */
        public static PosSelection readFromItemStack(final ItemStack itemStack)
        {
            return itemStack.getOrDefault(ModDataComponents.POS_SELECTION.get(), PosSelection.EMPTY);
        }

        /**
         * Performs updating of posSelection in given itemStack
         */
        public static void updateItemStack(final ItemStack itemStack, final UnaryOperator<PosSelection> updater)
        {
            updater.apply(readFromItemStack(itemStack)).writeToItemStack(itemStack);
        }
    }
}
