package com.minecolonies.core.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Class handling the Scimitar item.
 *
 * <p>PORT26: {@code SwordItem} was removed — plain {@link Item} with
 * {@code Item.Properties#sword(ToolMaterial, float, float)}. The 1.21.1 item was
 * {@code new SwordItem(Tiers.IRON, createAttributes(Tiers.WOOD, 3, -2.4F))} (attack damage
 * attribute 3, speed -2.4, iron durability); the new helper computes damage as
 * {@code baseline + material.attackDamageBonus} (iron = 2), so a baseline of 1 keeps
 * the exact old attribute value.</p>
 */
public class ItemIronScimitar extends Item
{
    /**
     * Constructor for the Scimitar Item.
     *
     * @param properties the properties.
     */
    public ItemIronScimitar(final Item.Properties properties)
    {
        super(properties.sword(ToolMaterial.IRON, 1.0F, -2.4F));
    }
}
