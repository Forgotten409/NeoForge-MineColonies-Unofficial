package com.ldtteam.structurize.blocks;

import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.blocks.schematic.BlockFluidSubstitution;
import com.ldtteam.structurize.blocks.schematic.BlockSolidSubstitution;
import com.ldtteam.structurize.blocks.schematic.BlockSubstitution;
import com.ldtteam.structurize.blocks.schematic.BlockTagSubstitution;
import com.ldtteam.structurize.items.ModItems;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Class to register blocks to Structurize
 *
 * <p>PORT26: MC 26.1 requires the block id to be set on {@link BlockBehaviour.Properties}
 * BEFORE the Block constructor runs ("Block id not set" NPE otherwise).
 * {@code DeferredRegister.Blocks#registerBlock(name, factory, UnaryOperator)} sets the
 * ResourceKey on the properties before invoking the factory, and
 * {@code DeferredRegister.Items#registerSimpleBlockItem(Holder)} does the same for the
 * BlockItem (deriving the name from the block key + applying useBlockDescriptionPrefix, so
 * the lang key stays {@code block.structurize.*} like in 1.21.1). The old plain
 * {@code BLOCKS.register(name, Supplier)} + self-built {@code Properties.of()} flow is gone.
 */
public final class ModBlocks
{
    private ModBlocks() { /* prevent construction */ }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Constants.MOD_ID);

    public static final TagKey<Block> NULL_PLACEMENT = BlockTags.create(Constants.resLocStruct("null_placement"));

    public static final DeferredBlock<BlockSubstitution>      blockSubstitution;
    public static final DeferredBlock<BlockSolidSubstitution> blockSolidSubstitution;
    public static final DeferredBlock<BlockFluidSubstitution> blockFluidSubstitution;
    public static final DeferredBlock<BlockTagSubstitution> blockTagSubstitution;

    /**
     * Utility shorthand to register a block and its simple BlockItem together, using the
     * 26.1 registration flow.
     *
     * @param name the registry name of the block (and its item)
     * @param block a factory creating the block from finished block properties
     * @param properties operator configuring the block properties (id is set afterwards)
     * @param <B> the block subclass for the factory response
     * @return the block entry saved to the registry
     */
    public static <B extends Block> DeferredBlock<B> registerWithBlockItem(
      final String name,
      final Function<BlockBehaviour.Properties, ? extends B> block,
      final UnaryOperator<BlockBehaviour.Properties> properties)
    {
        final DeferredBlock<B> registered = BLOCKS.registerBlock(name.toLowerCase(java.util.Locale.ROOT), block, properties);
        ModItems.ITEMS.registerSimpleBlockItem(registered);
        return registered;
    }

    /*
     *  Registration
     */

    static
    {
        blockSubstitution       = registerWithBlockItem("blockSubstitution", BlockSubstitution::new,
          BlockSubstitution::defaultSubstitutionProperties);
        blockSolidSubstitution  = registerWithBlockItem("blockSolidSubstitution", BlockSolidSubstitution::new,
          BlockSubstitution::defaultSubstitutionProperties);
        blockFluidSubstitution  = registerWithBlockItem("blockFluidSubstitution", BlockFluidSubstitution::new,
          BlockSubstitution::defaultSubstitutionProperties);
        // No block item: the tag substitution block is only used internally by the scan tool.
        // PORT26: registry paths must be [a-z0-9/._-] (Identifier.assertValidPath) — the 1.21.1
        // source registered this as "blockTagSubstitution".toLowerCase(); the assets
        // (blockstates/blocktagsubstitution.json etc.) use the same lowercase id.
        blockTagSubstitution    = BLOCKS.registerBlock("blocktagsubstitution", BlockTagSubstitution::new,
          BlockSubstitution::defaultSubstitutionProperties);
    }
}
