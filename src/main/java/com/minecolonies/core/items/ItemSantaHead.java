package com.minecolonies.core.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import org.jetbrains.annotations.NotNull;

/**
 * Class handling the Santa hat.
 *
 * <p>PORT26: {@code ArmorItem} was removed — plain {@link Item} whose properties are
 * built with {@code Item.Properties#humanoidArmor(ArmorMaterial, ArmorType)}.</p>
 */
public class ItemSantaHead extends Item
{
    /**
     * Constructor method for the Santa hat item.
     *
     * @param name            the name (unused, kept for call-site parity).
     * @param materialIn      the material.
     * @param equipmentSlotIn the armor type.
     * @param properties      the item properties.
     */
    public ItemSantaHead(
      @NotNull final String name,
      @NotNull final ArmorMaterial materialIn,
      @NotNull final ArmorType equipmentSlotIn,
      final Item.Properties properties)
    {
        super(properties.humanoidArmor(materialIn, equipmentSlotIn));
    }
}
