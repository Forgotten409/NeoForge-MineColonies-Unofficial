package com.ldtteam.structurize.api;

import com.ldtteam.common.fakelevel.SingleBlockFakeLevel.SidedSingleBlockFakeLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Utility methods for the inventories.
 *
 * <p>PORT26 (NeoForge transfer rework): the legacy {@code IItemHandler}/{@code Capabilities.ItemHandler}
 * system was replaced by the new transfer API — handlers are now
 * {@link ResourceHandler}{@code <}{@link ItemResource}{@code >} and the capability tokens live in
 * {@link Capabilities.Item}. Concretely:
 * <ul>
 * <li>{@code IItemHandler} → {@code ResourceHandler<ItemResource>} (getSlots/getStackInSlot → size/getResource+getAmountAsInt)</li>
 * <li>{@code Capabilities.ItemHandler.BLOCK/ENTITY/ENTITY_AUTOMATION/ITEM} → {@code Capabilities.Item.BLOCK/ENTITY/ENTITY_AUTOMATION/ITEM}</li>
 * <li>{@code new InvWrapper(container)} → {@code VanillaContainerWrapper.of(container)} (InvWrapper is deprecated for removal)</li>
 * <li>{@code stack.getCapability(ItemHandler.ITEM)} → {@code stack.getCapability(Capabilities.Item.ITEM, ItemAccess.forStack(stack))}
 * (item capabilities now take an {@link ItemAccess} context instead of the implicit single-arg Void overload)</li>
 * <li>{@code entity.getPickedResult(new EntityHitResult(entity))} → {@code entity.getPickResult()} (renamed, parameter dropped)</li>
 * <li>{@code compound.getInt("x")} → {@code compound.getIntOr("x", 0)} (getInt now returns Optional)</li>
 * </ul></p>
 */
public final class ItemStackUtils
{
    public static final SidedSingleBlockFakeLevel ITEM_HANDLER_FAKE_LEVEL = new SidedSingleBlockFakeLevel();

    /**
     * Private constructor to hide the implicit one.
     */
    private ItemStackUtils()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Get itemStack of tileEntityData. Retrieve the data from the tileEntity. Including recursive content, eg. shulkers
     *
     * @param compound the tileEntity stored in a compound.
     * @param state the block.
     * @param level real vanilla instance for fakeLevel
     * @return the list of itemstacks.
     * @see #getListOfStackForEntity(Entity, BlockPos)
     */
    public static List<ItemStack> getItemStacksOfTileEntity(final CompoundTag compound, final BlockState state, final Level level)
    {
        if (compound == null)
        {
            return List.of();
        }

        // PORT26 FIX (BE/block mismatch error spam + builder stall): 26.1.2's BlockEntity
        // constructor validates the block state against the BE type's valid blocks and
        // vanilla error-logs a full stack trace on mismatch — and shipped blueprints carry
        // such legacy mismatches (verified: byzantine doublebuilder1 has a stale
        // domum_ornamentum:materially_retexturable BE on a minecraft:chest; townhall2 has two
        // barrels with the same leftover). This method runs on the client build screen
        // (WindowBuildBuilding.updateResources, every few ticks) AND on the server from the
        // builder AI (AbstractEntityAIStructureWithWorkOrder.requestMaterials) — the repeated
        // stack-trace logging was both log spam and a real perf drag, and read as "the builder
        // is broken". Pre-validate and return an empty list instead — identical to the
        // upstream 1.21.1 behaviour, where the mismatched BE loaded fine but simply carried
        // no items (a non-container BE on a chest contributes no contents).
        final String beId = compound.getStringOr("id", "");
        if (!beId.isEmpty())
        {
            @Nullable
            final Identifier beTypeId = Identifier.tryParse(beId);
            @Nullable
            final BlockEntityType<?> beType = beTypeId == null ? null : BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(beTypeId);
            if (beType == null || !beType.isValid(state))
            {
                return List.of();
            }
        }

        // PORT26: getInt -> getIntOr (getInt now returns Optional)
        final BlockPos blockpos = new BlockPos(compound.getIntOr("x", 0), compound.getIntOr("y", 0), compound.getIntOr("z", 0));
        final BlockEntity tileEntity = BlockEntity.loadStatic(blockpos, state, compound, level.registryAccess());
        if (tileEntity == null)
        {
            return List.of();
        }

        return ITEM_HANDLER_FAKE_LEVEL.get(level).useFakeLevelContext(state, tileEntity, level, fakeLevel -> {
            final List<ItemStack> items = new ArrayList<>();
            getItemHandlersFromProvider(tileEntity, blockpos, state).forEach(itemHandler -> deepExtractItemHandler(itemHandler, items::add));
            return items;
        });
    }

    /**
     * @param handler root itemHandler to extract
     * @param sink where to put content of all found itemStacks, incl. recursive contents
     */
    public static void deepExtractItemHandler(@Nullable final ResourceHandler<ItemResource> handler, final Consumer<ItemStack> sink)
    {
        if (handler == null)
        {
            return;
        }

        for (int slot = 0; slot < handler.size(); slot++)
        {
            // PORT26: getStackInSlot(slot).copy() -> getResource(slot).toStack(getAmountAsInt(slot));
            // toStack always returns a fresh stack (EMPTY for empty resources/amount 0)
            final ItemStack stack = handler.getResource(slot).toStack(handler.getAmountAsInt(slot));
            if (!ItemStackUtils.isEmpty(stack))
            {
                sink.accept(stack);
                // PORT26: stack.getCapability(ItemHandler.ITEM) -> Capabilities.Item.ITEM with ItemAccess context
                deepExtractItemHandler(stack.getCapability(Capabilities.Item.ITEM, ItemAccess.forStack(stack)), sink);
            }
        }
    }

    /**
     * Method to get sensible item handlers from blockEntity. Tries to provide whole deduplicated content. However this assumption is
     * weak. There still might be content (in returned set) that is not present at all or duplicated.
     *
     * @param provider The provider to get the IItemHandlers from.
     * @return A list with all the unique IItemHandlers a provider has.
     */
    public static Set<ResourceHandler<ItemResource>> getItemHandlersFromProvider(@Nullable final BlockEntity provider, final BlockPos pos, final BlockState state)
    {
        if (provider == null)
        {
            return Set.of();
        }
        if (provider instanceof final ResourceHandler<?> itemHandler)
        {
            // be is itemHandler itself = easy
            // PORT26: legacy "instanceof IItemHandler" -> instanceof ResourceHandler (new transfer API); raw cast is safe for item BEs
            @SuppressWarnings("unchecked")
            final ResourceHandler<ItemResource> typedHandler = (ResourceHandler<ItemResource>) itemHandler;
            return Set.of(typedHandler);
        }
        if (provider instanceof final Container container)
        {
            // be is vanilla container = itemHandler cap might return SidedInvWrapper with partial inv view
            // PORT26: new InvWrapper(container) -> VanillaContainerWrapper.of(container)
            return Set.of(VanillaContainerWrapper.of(container));
        }

        // PORT26: ItemHandler.BLOCK -> Capabilities.Item.BLOCK
        final ResourceHandler<ItemResource> unsidedItemHandler = provider.getLevel().getCapability(Capabilities.Item.BLOCK, pos, state, provider, null);
        if (unsidedItemHandler != null)
        {
            // weak assumption of unsided being partial view only
            return Set.of(unsidedItemHandler);
        }

        final Set<ResourceHandler<ItemResource>> handlerSet = new HashSet<>();
        for (final Direction side : Direction.values())
        {
            final ResourceHandler<ItemResource> cap = provider.getLevel().getCapability(Capabilities.Item.BLOCK, pos, state, provider, side);
            if (cap != null)
            {
                handlerSet.add(cap);
            }
        }
        // weakest assumption of sided itemHandler having disjoint sides
        return handlerSet;
    }

    /**
     * Wrapper method to check if a stack is empty.
     * Used for easy updating to 1.11.
     *
     * @param stack The stack to check.
     * @return True when the stack is empty, false when not.
     */
    public static boolean isEmpty(@Nullable final ItemStack stack)
    {
        return stack == null || stack.isEmpty() || stack == ItemStack.EMPTY || stack.getCount() <= 0;
    }

    /**
     * get the size of the stack.
     * This is for compatibility between 1.10 and 1.11
     *
     * @param stack to get the size from
     * @return the size of the stack
     */
    public static int getSize(final ItemStack stack)
    {
        if (ItemStackUtils.isEmpty(stack))
        {
            return 0;
        }

        return stack.getCount();
    }

    /**
     * @deprecated {@link #getListOfStackForEntity(Entity)}
     */
    @Deprecated(forRemoval = true, since = "1.21.1")
    public static List<ItemStack> getListOfStackForEntity(final Entity entity, final BlockPos pos)
    {
        return getListOfStackForEntity(entity);
    }

    /**
     * Get the list of required resources for entities + entity spawning item. Same implementation as blockEntity logic. Including recursive content, eg. shulkers
     *
     * @param entity the entity object.
     * @return a list of stacks.
     * @see #getItemStacksOfTileEntity(BlockEntity)
     */
    public static List<ItemStack> getListOfStackForEntity(final Entity entity)
    {
        if (entity == null)
        {
            return List.of();
        }

        final List<ItemStack> request = new ArrayList<>();

        // process entity itself
        final ItemStack spawnItem = getEntitySpawningItem(entity);
        if (spawnItem != null && !(spawnItem.getItem() instanceof SpawnEggItem))
        {
            request.add(spawnItem);
        }

        // process entity contents
        request.addAll(getItemStacksOfEntity(entity));

        return request.stream().filter(stack -> !stack.isEmpty()).toList();
    }

    /**
     * Get the list of required resources for entities. Same implementation as blockEntity logic. Including recursive content, eg. shulkers
     *
     * @param entity the entity object.
     * @return a list of stacks.
     * @see #getItemStacksOfTileEntity(BlockEntity)
     */
    public static List<ItemStack> getItemStacksOfEntity(final Entity entity)
    {
        if (entity == null)
        {
            return List.of();
        }

        final List<ItemStack> entityContent = new ArrayList<>();

        ResourceHandler<ItemResource> itemHandler = null;
        if (entity instanceof final ResourceHandler<?> rawHandler)
        {
            // entity is itemHandler itself = easy
            // PORT26: legacy "instanceof IItemHandler" -> instanceof ResourceHandler (new transfer API); raw cast is safe for item entities
            @SuppressWarnings("unchecked")
            final ResourceHandler<ItemResource> typedHandler = (ResourceHandler<ItemResource>) rawHandler;
            itemHandler = typedHandler;
        }
        else if (entity instanceof final Container container)
        {
            // entity is vanilla container = itemHandler cap might return SidedInvWrapper with partial inv view
            // PORT26: new InvWrapper(container) -> VanillaContainerWrapper.of(container)
            itemHandler = VanillaContainerWrapper.of(container);
        }
        if (itemHandler == null)
        {
            // PORT26: ItemHandler.ENTITY -> Capabilities.Item.ENTITY; explicit null context
            // (Void-context capability, queried via the 2-arg overload)
            itemHandler = entity.getCapability(Capabilities.Item.ENTITY, null);
        }
        if (itemHandler == null)
        {
            // weak assumption of unsided being partial view only
            // PORT26: ItemHandler.ENTITY_AUTOMATION -> Capabilities.Item.ENTITY_AUTOMATION
            itemHandler = entity.getCapability(Capabilities.Item.ENTITY_AUTOMATION, null);
        }

        if (itemHandler != null)
        {
            deepExtractItemHandler(itemHandler, entityContent::add);
        }
        // some vanilla entities "have inventory" but not forge cap yet
        else if (entity instanceof final ItemFrame itemFrame)
        {
            final ItemStack stack = itemFrame.getItem();
            entityContent.add(stack);
            // PORT26: stack.getCapability(ItemHandler.ITEM) -> Capabilities.Item.ITEM with ItemAccess context
            deepExtractItemHandler(stack.getCapability(Capabilities.Item.ITEM, ItemAccess.forStack(stack)), entityContent::add);
        }
        else if (entity instanceof final ItemEntity itemEntity)
        {
            final ItemStack stack = itemEntity.getItem();
            entityContent.add(stack);
            // PORT26: stack.getCapability(ItemHandler.ITEM) -> Capabilities.Item.ITEM with ItemAccess context
            deepExtractItemHandler(stack.getCapability(Capabilities.Item.ITEM, ItemAccess.forStack(stack)), entityContent::add);
        }
        else // sided item handler
        {
            for (final Direction side : Direction.values())
            {
                // PORT26: ItemHandler.ENTITY_AUTOMATION -> Capabilities.Item.ENTITY_AUTOMATION
                final ResourceHandler<ItemResource> cap = entity.getCapability(Capabilities.Item.ENTITY_AUTOMATION, side);
                if (cap != null)
                {
                    deepExtractItemHandler(cap, entityContent::add);
                }
            }
        }

        return entityContent;
    }

    /**
     * @return item that should spawn given entity
     */
    @Nullable
    public static ItemStack getEntitySpawningItem(final Entity entity)
    {
        // PORT26: `instanceof final` without a pattern variable is invalid Java —
        // plain type test (no variable is needed, the body reads entity.getType()).
        if (entity instanceof ItemFrame)
        {
            // PORT26: ItemFrame#getFrameItemStack became protected; build the frame item explicitly
            // (glow item frame is the only vanilla subclass).
            return entity.getType() == EntityType.GLOW_ITEM_FRAME
                       ? new ItemStack(Items.GLOW_ITEM_FRAME)
                       : new ItemStack(Items.ITEM_FRAME);
        }
        // PORT26: entity.getPickedResult(new EntityHitResult(entity)) -> entity.getPickResult() (renamed, param dropped)
        return entity.getPickResult();
    }

    /**
     * Method to compare to stacks, ignoring their stacksize.
     *
     * @param itemStack1 The left stack to compare.
     * @param itemStack2 The right stack to compare.
     * @return True when they are equal except the stacksize, false when not.
     */
    public static boolean compareItemStacksIgnoreStackSize(final ItemStack itemStack1, final ItemStack itemStack2)
    {
        return compareItemStacksIgnoreStackSize(itemStack1, itemStack2, true, true);
    }

    /**
     * Method to compare to stacks, ignoring their stacksize.
     *
     * @param itemStack1  The left stack to compare.
     * @param itemStack2  The right stack to compare.
     * @param matchDamage Set to true to match damage data.
     * @param matchNBT    Set to true to match nbt
     * @return True when they are equal except the stacksize, false when not.
     */
    public static boolean compareItemStacksIgnoreStackSize(final ItemStack itemStack1, final ItemStack itemStack2, final boolean matchDamage, final boolean matchNBT)
    {
        return compareItemStacksIgnoreStackSize(itemStack1, itemStack2, matchDamage, matchNBT, false);
    }

    /**
     * Method to compare to stacks, ignoring their stacksize.
     *
     * @param itemStack1  The left stack to compare.
     * @param itemStack2  The right stack to compare.
     * @param matchDamage Set to true to match damage data.
     * @param matchNBT    Set to true to match nbt
     * @param min         if the count of stack2 has to be at least the same as stack1.
     * @return True when they are equal except the stacksize, false when not.
     */
    public static boolean compareItemStacksIgnoreStackSize(
      final ItemStack itemStack1,
      final ItemStack itemStack2,
      final boolean matchDamage,
      final boolean matchNBT,
      final boolean min)
    {
        if (isEmpty(itemStack1) && isEmpty(itemStack2))
        {
            return true;
        }

        if (isEmpty(itemStack1) != isEmpty(itemStack2))
        {
            return false;
        }

        if (itemStack1.getItem() == itemStack2.getItem() && (!matchDamage || itemStack1.getDamageValue() == itemStack2.getDamageValue()))
        {
            if (!matchNBT)
            {
                // Not comparing nbt
                return true;
            }

            if (min && itemStack1.getCount() > itemStack2.getCount())
            {
                return false;
            }

            // Then sort on NBT
            if (!itemStack1.getComponents().isEmpty() && !itemStack2.getComponents().isEmpty())
            {
                final DataComponentMap nbt1 = itemStack1.getComponents();
                final DataComponentMap nbt2 = itemStack2.getComponents();

                for(final DataComponentType<?> key : nbt1.keySet())
                {
                    if(!matchDamage && key == DataComponents.DAMAGE)
                    {
                        continue;
                    }
                    if(!nbt2.has(key) || !nbt1.get(key).equals(nbt2.get(key)))
                    {
                        return false;
                    }
                }

                return nbt1.keySet().size() == nbt2.keySet().size();
            }
            else
            {
                return itemStack1.getComponents().isEmpty() == itemStack2.getComponents().isEmpty();
            }
        }
        return false;
    }

    /**
     * Item serializer helper, including air
     *
     * @param stack
     * @param buf
     */
    public static void serializeToBuffer(final ItemStack stack, RegistryFriendlyByteBuf buf)
    {
        buf.writeBoolean(stack.isEmpty());
        if (!stack.isEmpty())
        {
            ItemStack.STREAM_CODEC.encode(buf, stack);
        }
    }

    /**
     * Item deserializer helper, including air. Must be serialized with the above util
     *
     * @param buf
     */
    public static ItemStack deserializeFromBuffer(RegistryFriendlyByteBuf buf)
    {
        if (!buf.readBoolean())
        {
            return ItemStack.STREAM_CODEC.decode(buf);
        }

        return ItemStack.EMPTY;
    }
}
