package com.ldtteam.domumornamentum.util;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Util;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public class MaterialTextureDataUtil
{

    private MaterialTextureDataUtil()
    {
        throw new IllegalStateException("Can not instantiate an instance of: MaterialTextureDataUtil. This is a utility class");
    }

    public static MaterialTextureData generateRandomTextureDataFrom(final ItemStack stack) {
        final Item item = stack.getItem();
        if (!(item instanceof BlockItem blockItem))
            return MaterialTextureData.EMPTY;

        final Block block = blockItem.getBlock();
        return generateRandomTextureDataFrom(block);
    }

    @NotNull
    public static MaterialTextureData generateRandomTextureDataFrom(final Block block)
    {
        if (!(block instanceof IMateriallyTexturedBlock materiallyTexturedBlock))
            return MaterialTextureData.EMPTY;

        try {
            final MaterialTextureData.Builder newData = MaterialTextureData.builder();

            int localOffset = BuiltInRegistries.BLOCK.getId(block);
            int offsetIndex = 0;
            for (IMateriallyTexturedBlockComponent component : materiallyTexturedBlock.getComponents())
            {
                final List<Block> candidates = new ArrayList<>(
                  StreamSupport
                    .stream(BuiltInRegistries.BLOCK.getTagOrEmpty(component.getValidSkins()).spliterator(), false)
                    .map(Holder::value).toList());
                if (candidates.isEmpty())
                {
                    continue;
                }

                // PORT26 (batch 10): the 1.21.1 original drove this counter off ClientTickEvent
                // (ClientTickEventHandler#getNonePausedTicks). In the merged port the event-driven
                // counter was observed FROZEN (user report: every materially-textured item showed one
                // fixed material — e.g. the fence permanently "stone" — and never cycled, while the
                // 1.21.1 original cycles one material per second). Wall-clock pseudo-ticks cannot
                // fail: Util.getMillis()/50 advances exactly like client ticks, so the preview still
                // changes material once per second (ticks/20 == seconds) in every context the player
                // can actually see (the pause menu covers the screen; creative inventory is not
                // paused, so wall clock matches the original's visible behaviour everywhere).
                final long previewTicks = Util.getMillis() / 50L;
                final int index = (int) ((previewTicks / 20 + (offsetIndex += localOffset)) % candidates.size());
                final Block texture = candidates.get(index);
                newData.setComponent(component.getId(), texture);
            }

            return newData.build();
        }
        catch (Exception e)
        {
            return MaterialTextureData.EMPTY;
        }
    }
}
