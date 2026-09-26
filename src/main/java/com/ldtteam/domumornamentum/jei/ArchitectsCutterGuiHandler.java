package com.ldtteam.domumornamentum.jei;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.client.screens.ArchitectsCutterScreen;
import com.ldtteam.domumornamentum.network.messages.CreativeSetArchitectCutterSlotMessage;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI GUI handler for the {@link ArchitectsCutterScreen} — PORT 26.1.2.
 *
 * <p>Identical to the 1.21.1 handler except for the {@code getScreenLeft()}/{@code getScreenTop()}
 * accessors: vanilla 26.1 renamed them to the protected {@code leftPos}/{@code topPos} fields,
 * so the screen now exposes them via explicit public getters.
 */
public class ArchitectsCutterGuiHandler implements IGhostIngredientHandler<ArchitectsCutterScreen>
{
    @Override
    public <I> List<Target<I>> getTargetsTyped(final ArchitectsCutterScreen gui,
                                               final ITypedIngredient<I> ingredient,
                                               final boolean doStart)
    {
        final List<Target<I>> targets = new ArrayList<>();
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.isCreative())
        {
            ingredient.getItemStack().ifPresent(stack ->
            {
                if (gui.getMenu().getCurrentVariant() != null &&
                        gui.getMenu().getCurrentVariant().getItem() instanceof BlockItem item &&
                        item.getBlock() instanceof IMateriallyTexturedBlock block)
                {
                    for (int i = 0; i < block.getComponents().size(); ++i)
                    {
                        final Slot slot = gui.getMenu().slots.get(i);
                        if (slot.isActive() && slot.mayPlace(stack))
                        {
                            targets.add(new Target<>()
                            {
                                @Override
                                public Rect2i getArea()
                                {
                                    return new Rect2i(gui.getScreenLeft() + slot.x, gui.getScreenTop() + slot.y, 17, 17);
                                }

                                @Override
                                public void accept(final I ingredient)
                                {
                                    new CreativeSetArchitectCutterSlotMessage(slot.index, (ItemStack) ingredient).sendToServer();
                                }
                            });
                        }
                    }
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete()
    {
    }
}
