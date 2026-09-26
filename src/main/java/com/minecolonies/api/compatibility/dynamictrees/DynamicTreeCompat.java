package com.minecolonies.api.compatibility.dynamictrees;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.item.Seed;
import com.dtteam.dynamictrees.tree.family.Family;
import com.minecolonies.api.util.Log;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PORT26: re-added compat (was dropped in the initial merge).
 *
 * <p>Verified against the actual Dynamic Trees 26.1.2 build
 * (dynamictrees-neoforge-26.1.2-1.8.0-BETA04, API classes ship inside the mod jar —
 * BranchBlock/TrunkShellBlock/DynamicLeavesBlock/Seed/Family all kept their 1.21.1
 * surface: {@code Seed#getSpecies}, {@code Species#getSeedStack/plantSapling},
 * {@code Family#getCommonSpecies}, {@code DynamicLeavesBlock#getFamily}). The only API
 * drift is the vanilla/NeoForge {@code onDestroyedByPlayer} call, which gained an
 * {@code ItemStack toolStack} parameter in 26.x — the tool in the fake player's hand is
 now passed through explicitly.</p>
 */
public final class DynamicTreeCompat extends DynamicTreeProxy
{
    private static final Map<ResourceKey<Level>, FakePlayer> fakePlayers = new HashMap<>();

    public DynamicTreeCompat()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Check whether dynamic tree's mod is present
     *
     * @return true
     */
    @Override
    public boolean isDynamicTreePresent()
    {
        return true;
    }

    /**
     * Check whether the block is part of a dynamic Tree
     *
     * @param block Block to check
     */
    @Override
    public boolean checkForDynamicTreeBlock(@NotNull final Block block)
    {
        return block instanceof BranchBlock;
    }

    /**
     * Check whether the block is a dynamic leaf
     *
     * @param block Block to check
     */
    @Override
    public boolean checkForDynamicLeavesBlock(final Block block)
    {
        return block instanceof DynamicLeavesBlock;
    }

    /**
     * Check whether the block is a shell block.
     *
     * @param block the block to check
     * @return true if it is a shell block.
     */
    @Override
    public boolean checkForDynamicTrunkShellBlock(final Block block)
    {
        return block instanceof TrunkShellBlock;
    }

    /**
     * Returns drops of a dynamic seed as List
     *
     * @param world      world the Leaf is in
     * @param pos        position of the Leaf
     * @param blockstate Blockstate of the Leaf
     * @param fortune    amount of fortune to use
     * @param leaf       The leaf to check
     */
    @Override
    public NonNullList<ItemStack> getDropsForLeaf(
            final LevelAccessor world,
            final BlockPos pos,
            final BlockState blockstate,
            final int fortune,
            final Block leaf)
    {
        if (leaf instanceof final DynamicLeavesBlock leaves)
        {
            ItemStack stack = leaves.getFamily(blockstate, world, pos).getCommonSpecies().getSeedStack(1);
            final NonNullList<ItemStack> list = NonNullList.create();
            list.add(stack);
            return list;
        }

        return NonNullList.create();
    }

    /**
     * Check whether the item is a dynamic Sapling
     *
     * @param item Item to check
     */
    @Override
    public boolean checkForDynamicSapling(@NotNull final Item item)
    {
        return item instanceof Seed;
    }

    /**
     * Creates a runnable to harvest/break a dynamic tree
     *
     * @param world        The world the tree is in
     * @param blockToBreak The block of the dynamic tree
     * @param toolToUse    The tool to break the tree with, optional
     * @param workerPos    The position the fakeplayer breaks the tree from, optional
     * @return Runnable to break the Tree
     */
    @Override
    public Runnable getTreeBreakActionCompat(@NotNull final Level world, @NotNull final BlockPos blockToBreak, final ItemStack toolToUse, final BlockPos workerPos)
    {
        return () ->
        {
            final BlockState curBlockState = world.getBlockState(blockToBreak);
            final Block curBlock = curBlockState.getBlock();

            if (world.getServer() == null)
            {
                Log.getLogger().error("Minecolonies:DynamicTreeCompat unexpected null while trying to get World");
                return;
            }

            final ResourceKey<Level> dim = world.dimension();
            FakePlayer fake = fakePlayers.get(dim);

            if (fake == null)
            {
                fakePlayers.put(dim, new FakePlayer((ServerLevel) world,
                        new GameProfile(UUID.randomUUID(), "minecolonies_LumberjackFake")));
                fake = fakePlayers.get(dim);
            }

            if (workerPos != null)
            {
                fake.setPos(workerPos.getX(), workerPos.getY(), workerPos.getZ());
            }

            if (toolToUse != null)
            {
                fake.setItemInHand(InteractionHand.MAIN_HAND, toolToUse);
            }

            // PORT26: onDestroyedByPlayer gained the ItemStack toolStack parameter in 26.x —
            // pass the tool explicitly (the old 6-arg overload no longer exists).
            curBlock.onDestroyedByPlayer(curBlockState, world, blockToBreak, fake, toolToUse, true, world.getFluidState(blockToBreak));
        };
    }

    /**
     * Tries to plant a sapling at the given location
     *
     * @param world        World to plant the sapling in
     * @param location     location to plant the sapling
     * @param saplingStack Itemstack of the sapling
     * @return true if successful
     */
    @Override
    public boolean plantDynamicSaplingCompat(@NotNull final Level world, @NotNull final BlockPos location, @NotNull final ItemStack saplingStack)
    {
        if (saplingStack.getItem() instanceof final Seed seed)
        {
            return seed.getSpecies().plantSapling(world, location, false);
        }

        return false;
    }

    /**
     * Method to check if two given blocks have the same Tree family
     *
     * @param block1 First blockpos to compare
     * @param block2 Second blockpos to compare
     * @return true when same family
     */
    @Override
    public boolean hasFittingTreeFamilyCompat(@NotNull final BlockPos block1, @NotNull final BlockPos block2, @NotNull final LevelAccessor world)
    {
        final Family fam1 = getFamilyForBlock(block1, world);
        final Family fam2 = getFamilyForBlock(block2, world);

        if (fam1 != null && fam2 != null)
        {
            return fam1 == fam2;
        }
        return false;
    }

    /**
     * Returns the dynamic tree family for the given block pos
     *
     * @param blockPos position
     * @param world    world
     * @return dynamic tree family
     */
    private static Family getFamilyForBlock(@NotNull final BlockPos blockPos, @NotNull final LevelAccessor world)
    {
        final Block block = world.getBlockState(blockPos).getBlock();
        if (block instanceof final BranchBlock branch)
        {
            return branch.getFamily();
        }
        if (block instanceof final DynamicLeavesBlock leaves)
        {
            return leaves.getFamily(world.getBlockState(blockPos), world, blockPos);
        }

        return null;
    }
}
