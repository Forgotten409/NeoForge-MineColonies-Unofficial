package com.minecolonies.api.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * PORT26 SHIM: adapts a legacy {@link IItemHandler} to the new
 * {@code ResourceHandler<ItemResource>} transfer API, so the capability system
 * (which now requires ResourceHandler) can still serve the legacy MineColonies inventories.
 *
 * <p>Insertion/extraction delegate to {@link IItemHandler#insertItem}/{@link IItemHandler#extractItem}.
 * Transactional safety is provided by whole-inventory snapshots via {@link SnapshotJournal}.
 */
public class ItemHandlerResourceAdapter extends SnapshotJournal<List<ItemStack>> implements ResourceHandler<ItemResource>
{
    private final IItemHandler handler;

    public ItemHandlerResourceAdapter(@NotNull final IItemHandler handler)
    {
        this.handler = handler;
    }

    /** Wrap (or pass through) a legacy handler for capability registration. */
    public static ResourceHandler<ItemResource> of(@NotNull final IItemHandler handler)
    {
        return new ItemHandlerResourceAdapter(handler);
    }

    @Override
    public int size()
    {
        return handler.getSlots();
    }

    @Override
    public ItemResource getResource(final int index)
    {
        return ItemResource.of(handler.getStackInSlot(index));
    }

    @Override
    public long getAmountAsLong(final int index)
    {
        return handler.getStackInSlot(index).getCount();
    }

    @Override
    public long getCapacityAsLong(final int index, final ItemResource resource)
    {
        return handler.getSlotLimit(index);
    }

    @Override
    public boolean isValid(final int index, final ItemResource resource)
    {
        return handler.isItemValid(index, resource.toStack());
    }

    @Override
    public int insert(final int index, final ItemResource resource, final int amount, final TransactionContext transaction)
    {
        if (amount <= 0 || resource.isEmpty())
        {
            return 0;
        }

        final ItemStack toInsert = resource.toStack(amount);
        final ItemStack simulatedRemainder = handler.insertItem(index, toInsert.copy(), true);
        final int inserted = amount - simulatedRemainder.getCount();
        if (inserted <= 0)
        {
            return 0;
        }

        updateSnapshots(transaction);
        handler.insertItem(index, toInsert, false);
        return inserted;
    }

    @Override
    public int extract(final int index, final ItemResource resource, final int amount, final TransactionContext transaction)
    {
        if (amount <= 0 || resource.isEmpty())
        {
            return 0;
        }

        final ItemStack current = handler.getStackInSlot(index);
        if (current.isEmpty() || !resource.matches(current))
        {
            return 0;
        }

        final int toExtract = Math.min(amount, Math.min(current.getCount(), resource.getMaxStackSize()));
        if (toExtract <= 0)
        {
            return 0;
        }

        final ItemStack simulatedExtracted = handler.extractItem(index, toExtract, true);
        final int extracted = simulatedExtracted.getCount();
        if (extracted <= 0)
        {
            return 0;
        }

        updateSnapshots(transaction);
        handler.extractItem(index, toExtract, false);
        return extracted;
    }

    @Override
    protected List<ItemStack> createSnapshot()
    {
        final List<ItemStack> snapshot = new ArrayList<>(handler.getSlots());
        for (int i = 0; i < handler.getSlots(); i++)
        {
            snapshot.add(handler.getStackInSlot(i).copy());
        }
        return snapshot;
    }

    @Override
    protected void revertToSnapshot(final List<ItemStack> snapshot)
    {
        if (!(handler instanceof final IItemHandlerModifiable modifiable))
        {
            // best effort: nothing we can do for non-modifiable handlers
            return;
        }

        for (int i = 0; i < snapshot.size() && i < modifiable.getSlots(); i++)
        {
            modifiable.setStackInSlot(i, snapshot.get(i).copy());
        }
    }
}
