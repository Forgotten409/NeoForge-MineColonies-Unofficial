package com.ldtteam.structurize.blocks.schematic;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * This block is used as a substitution block for the Builder.
 * Every solid block can be substituted by this block in schematics.
 * This helps make schematics independent from location and ground.
 *
 * <p>PORT26: constructor takes the registrar-provided properties (block id must be set
 * before the Block constructor runs — see {@link BlockSubstitution}).
 */
public class BlockFluidSubstitution extends Block
{
    /**
     * Constructor for the Substitution block.
     * sets the creative tab, as well as the resistance and the hardness.
     *
     * @param properties finished block properties (id already set by the registrar)
     */
    public BlockFluidSubstitution(final BlockBehaviour.Properties properties)
    {
        super(BlockSubstitution.defaultSubstitutionProperties(properties));
    }
}
