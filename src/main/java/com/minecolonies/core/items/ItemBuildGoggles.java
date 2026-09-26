package com.minecolonies.core.items;

import com.minecolonies.core.client.render.worldevent.ColonyBlueprintRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Class handling the Builder's Goggles.
 *
 * <p>PORT26: {@code ArmorItem} was removed — plain {@link Item} with
 * {@code Item.Properties#humanoidArmor(ArmorMaterial, ArmorType)} built from the
 * build-goggles material constant.</p>
 */
public class ItemBuildGoggles extends Item
{
    /**
     * Constructor.
     *
     * @param name       the name (unused, kept for call-site parity).
     * @param properties the item properties.
     */
    public ItemBuildGoggles(
            @NotNull final String name,
            final Item.Properties properties)
    {
        // PORT26: Properties#setNoRepair was removed; repairability is now data-driven via
        // the REPAIRABLE component — not setting it keeps the 1.21.1 "not repairable" behavior.
        super(properties.humanoidArmor(com.minecolonies.apiimp.initializer.ModItemsInitializer.GOGGLES_MATERIAL, net.minecraft.world.item.equipment.ArmorType.HELMET)
            .rarity(Rarity.UNCOMMON));
    }

    @Override
    public void appendHoverText(@NotNull final ItemStack stack, @Nullable final TooltipContext ctx, @NotNull final TooltipDisplay display, @NotNull final Consumer<Component> components, @NotNull final TooltipFlag flags)
    {
        super.appendHoverText(stack, ctx, display, components, flags);

        components.accept(Component.translatableEscape("\"%s\"",
                        Component.translatableEscape("item.minecolonies.build_goggles.lore")
                                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        components.accept(Component.translatableEscape(ColonyBlueprintRenderer.willRenderBlueprints()
                ? "item.minecolonies.build_goggles.enabled" : "item.minecolonies.build_goggles.disabled")
                .withStyle(ChatFormatting.GRAY));
    }
}
