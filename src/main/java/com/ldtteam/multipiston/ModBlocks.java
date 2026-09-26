package com.ldtteam.multipiston;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static com.ldtteam.multipiston.MultiPiston.MOD_ID;

/**
 * Class to create the modBlocks.
 * References to the blocks can be made here
 *
 * <p>PORT26: MC 26.1 requires the block id to be set on {@link BlockBehaviour.Properties}
 * BEFORE the Block constructor runs ("Block id not set" NPE in
 * {@code BlockBehaviour$Properties.effectiveDrops} otherwise), and likewise the item id on
 * {@code Item.Properties} ("Item id not set" NPE). {@code DeferredRegister.Blocks#registerBlock}
 * sets the ResourceKey on the properties before invoking the block factory, and
 * {@code DeferredRegister.Items#registerSimpleBlockItem} does the same for the BlockItem
 * (deriving the item name from the block key and applying {@code useBlockDescriptionPrefix}
 * so the lang key stays {@code block.multipiston.multipistonblock} like in 1.21.1).
 * This is the same registration flow the port uses for every domum-ornamentum block.
 */
public class ModBlocks
{
    /**
     * Blocks which the multipiston is allowed to move even though they are entity blocks.
     */
    public static final TagKey<Block> MOVEABLE_ENTITY_BLOCKS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "moveable_entity_blocks"));

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items  ITEMS  = DeferredRegister.createItems(MOD_ID);

    /**
     * The hardness this block has.
     */
    private static final float BLOCK_HARDNESS = 1F;

    /**
     * The resistance this block has.
     */
    private static final float RESISTANCE = 1F;

    public static final DeferredBlock<MultiPistonBlock> multipiston = registerBlockWithItem("multipistonblock",
      MultiPistonBlock::new,
      p -> p.mapColor(MapColor.STONE).sound(SoundType.STONE).strength(BLOCK_HARDNESS, RESISTANCE).isRedstoneConductor((a, b, c) -> true));

    /**
     * Utility shorthand to register a block and its BlockItem together, using the 26.1
     * registration flow where both the block and the item id are set on their respective
     * properties by the registrar before the constructors run.
     *
     * @param name       the registry name of the block (and its item)
     * @param block      a factory creating the block from finished block properties
     * @param properties operator configuring the block properties (id is set afterwards)
     * @param <B>        the block subclass for the factory response
     * @return the block entry saved to the registry
     */
    public static <B extends Block> DeferredBlock<B> registerBlockWithItem(
      final String name,
      final Function<BlockBehaviour.Properties, ? extends B> block,
      final UnaryOperator<BlockBehaviour.Properties> properties)
    {
        final DeferredBlock<B> registered = BLOCKS.registerBlock(name.toLowerCase(Locale.ENGLISH), block, properties);
        ITEMS.registerSimpleBlockItem(registered);
        return registered;
    }
}
