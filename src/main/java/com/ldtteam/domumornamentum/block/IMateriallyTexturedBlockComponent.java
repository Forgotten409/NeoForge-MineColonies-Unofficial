package com.ldtteam.domumornamentum.block;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.Identifier;

public interface IMateriallyTexturedBlockComponent
{
    Identifier getId();

    TagKey<Block> getValidSkins();

    Block getDefault();

    boolean isOptional();
}
