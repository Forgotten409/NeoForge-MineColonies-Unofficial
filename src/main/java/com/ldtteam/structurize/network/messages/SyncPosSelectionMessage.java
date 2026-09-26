package com.ldtteam.structurize.network.messages;

import com.ldtteam.common.network.AbstractServerPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.items.AbstractItemWithPosSelector;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * PORT26 (NEW MESSAGE — no 1.21.1 counterpart): syncs the captured start position from the
 * client to the server item stack.
 *
 * <p>Background: 1.21.1's {@code Item#canAttackBlock} ran on BOTH sides when the player
 * started left-clicking a block, so both stacks got the PosSelection component updated.
 * 26.1 removed that hook; the replacement {@code PlayerInteractEvent.LeftClickBlock}
 * handler cancels the event on the client (which also stops the destroy packet), so the
 * server would never learn the captured position — this message closes that gap, keeping
 * the direct shift-click save flow (server reads its own stack's PosSelection) working
 * exactly like 1.21.1.
 */
public class SyncPosSelectionMessage extends AbstractServerPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(Constants.MOD_ID, "sync_pos_selection", SyncPosSelectionMessage::new);

    /**
     * The hand the tool is in.
     */
    private final InteractionHand hand;

    /**
     * The current pos selection component of the client stack.
     */
    private final AbstractItemWithPosSelector.PosSelection selection;

    /**
     * Empty public constructor.
     */
    public SyncPosSelectionMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        this.hand = buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        this.selection = AbstractItemWithPosSelector.PosSelection.STREAM_CODEC.decode(buf);
    }

    /**
     * Create the sync message.
     * @param stack the client-side tool stack
     * @param hand the hand the stack is held in
     */
    public SyncPosSelectionMessage(final ItemStack stack, final InteractionHand hand)
    {
        super(TYPE);
        this.hand = hand;
        this.selection = AbstractItemWithPosSelector.PosSelection.readFromItemStack(stack);
    }

    @Override
    public void toBytes(final RegistryFriendlyByteBuf buf)
    {
        buf.writeBoolean(this.hand == InteractionHand.MAIN_HAND);
        AbstractItemWithPosSelector.PosSelection.STREAM_CODEC.encode(buf, this.selection);
    }

    @Override
    protected void onExecute(final IPayloadContext context, final ServerPlayer player)
    {
        final ItemStack stack = player.getItemInHand(this.hand);
        if (stack.getItem() instanceof AbstractItemWithPosSelector)
        {
            this.selection.writeToItemStack(stack);
        }
    }
}
