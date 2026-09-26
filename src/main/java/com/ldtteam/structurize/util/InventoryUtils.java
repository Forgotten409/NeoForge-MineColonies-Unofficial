package com.ldtteam.structurize.util;

import com.ldtteam.structurize.api.ItemStackUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import java.util.ArrayList;
import java.util.List;

/**
 * Structurize specific inventory utilities.
 * <p>
 * PORT26: migrated off the deprecated {@code IItemHandler} item API onto the new NeoForge
 * transfer API ({@link ResourceHandler}{@code <}{@link ItemResource}{@code >}). Mapping used:
 * {@code getSlots() -> size()}, {@code getStackInSlot(i) -> getResource(i).toStack(getAmountAsInt(i))},
 * {@code insertItem(i, stack, simulate) -> insert(i, resource, amount, transaction)} and
 * {@code extractItem(i, amount, simulate) -> extract(i, resource, amount, transaction)}.
 */
public class InventoryUtils
{
    /**
     * Check if an inventory has all the required stacks.
     * @param inventory the inventory to check.
     * @param requiredItems the list of items.
     * @return true if so, else false.
     */
    public static boolean hasRequiredItems(final ResourceHandler<ItemResource> inventory, final List<ItemStack> requiredItems)
    {
        final List<ItemStack> listToDiscount = new ArrayList<>();
        for (final ItemStack stack : requiredItems)
        {
            listToDiscount.add(stack.copy());
        }

        for (int slot = 0; slot < inventory.size(); slot++)
        {
            final ItemStack content = inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
            if (content.isEmpty())
            {
                continue;
            }
            int contentCount = content.getCount();

            for (final ItemStack stack : listToDiscount)
            {
                if (!stack.isEmpty() && ItemStackUtils.compareItemStacksIgnoreStackSize(stack, content))
                {
                    if (stack.getCount() < content.getCount())
                    {
                        contentCount = contentCount - stack.getCount();
                        stack.setCount(0);
                    }
                    else
                    {
                        stack.setCount(stack.getCount() - contentCount);
                        break;
                    }
                }
            }
        }

        for (final ItemStack stack : listToDiscount)
        {
            if (!stack.isEmpty())
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Method to transfers a stack to the next best slot in the target inventory.
     *
     * @param targetHandler The {@link ResourceHandler} that works as Target.
     */
    public static void transferIntoNextBestSlot(final ItemStack stack, final ResourceHandler<ItemResource> targetHandler)
    {
        if(stack.isEmpty())
        {
            return;
        }

        // PORT26: insertItem(slot, remainder, simulate=false) -> transactional insert returning the inserted amount
        final ItemResource resource = ItemResource.of(stack);
        int remaining = stack.getCount();
        try (var tx = Transaction.openRoot())
        {
            for (int i = 0; i < targetHandler.size() && remaining > 0; i++)
            {
                remaining -= targetHandler.insert(i, resource, remaining, tx);
                if (remaining == 0)
                {
                    break;
                }
            }
            tx.commit();
        }
    }

    /**
     * Consume an ItemStack from an itemhandler.
     * @param tempStack the stack.
     * @param handler the handler.
     */
    public static void consumeStack(final ItemStack tempStack, final ResourceHandler<ItemResource> handler)
    {
        int count = tempStack.getCount();
        // PORT26: ItemStack#getCraftingRemainingItem() -> getCraftingRemainder() (ItemStackTemplate)
        final ItemStackTemplate containerTemplate = tempStack.getCraftingRemainder();
        final ItemStack container = containerTemplate == null ? ItemStack.EMPTY : containerTemplate.create();

        for (int i = 0; i < handler.size(); i++)
        {
            final ItemResource slotResource = handler.getResource(i);
            final ItemStack slotStack = slotResource.toStack(handler.getAmountAsInt(i));
            if (ItemStackUtils.compareItemStacksIgnoreStackSize(slotStack, tempStack))
            {
                // PORT26: extractItem(i, count, simulate=false) -> transactional extract returning the extracted amount
                int extracted = 0;
                try (var tx = Transaction.openRoot())
                {
                    extracted = handler.extract(i, slotResource, count, tx);
                    if (extracted > 0)
                    {
                        tx.commit();
                    }
                }
                if (extracted == count)
                {
                    if (!container.isEmpty())
                    {
                        for (int j = 0; j < tempStack.getCount(); j++)
                        {
                            transferIntoNextBestSlot(container, handler);
                        }
                    }
                    return;
                }
                count -= extracted;
            }
        }
    }
}
