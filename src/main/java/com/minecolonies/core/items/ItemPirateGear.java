package com.minecolonies.core.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import org.jetbrains.annotations.NotNull;

/**
 * Class handling the Pirate Gear.
 *
 * <p>PORT26: {@code ArmorItem} was removed — plain {@link Item} whose properties are
 * built with {@code Item.Properties#humanoidArmor(ArmorMaterial, ArmorType)}. The explicit
 * per-item durabilities of the 1.21.1 registrations are chained after the helper so they
 * keep overriding the material-derived value.</p>
 */
public class ItemPirateGear extends Item
{
    /**
     * Constructor method for the Pirate Gear.
     *
     * @param name            the name (unused, kept for call-site parity).
     * @param materialIn      the material of the armour.
     * @param equipmentSlotIn the armor type of it.
     * @param properties      the item properties.
     */
    public ItemPirateGear(
      @NotNull final String name,
      @NotNull final ArmorMaterial materialIn,
      @NotNull final ArmorType equipmentSlotIn,
      final Item.Properties properties)
    {
        super(properties.humanoidArmor(materialIn, equipmentSlotIn));
    }
}
