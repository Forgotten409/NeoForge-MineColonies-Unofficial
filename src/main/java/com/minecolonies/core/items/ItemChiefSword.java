package com.minecolonies.core.items;

import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.api.entity.mobs.barbarians.AbstractEntityBarbarianRaider;
import com.minecolonies.api.items.IChiefSwordItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.minecolonies.api.util.constant.Constants.GLOW_EFFECT_DISTANCE;
import static com.minecolonies.api.util.constant.Constants.GLOW_EFFECT_DURATION;

/**
 * Class handling the Chief Sword item.
 *
 * <p>PORT26: {@code SwordItem} was removed — the item is a plain {@link Item} whose
 * properties are built with {@code Item.Properties#sword(ToolMaterial, float, float)}.
 * The 1.21.1 item was {@code new SwordItem(Tiers.DIAMOND, createAttributes(Tiers.WOOD, 3, -2.4F))}
 * (attack damage attribute 3, attack speed -2.4, diamond durability/enchantability) —
 * the new {@code sword(...)} helper computes damage as {@code baseline + material.attackDamageBonus}
 * (diamond = 3), so a baseline of 0 keeps the exact old attribute value.</p>
 */
public class ItemChiefSword extends Item implements IChiefSwordItem
{
    private static final int LEVITATION_EFFECT_DURATION   = 20 * 10;
    private static final int LEVITATION_EFFECT_MULTIPLIER = 2;

    /**
     * Constructor method for the Chief Sword Item
     *
     * @param properties the properties.
     */
    public ItemChiefSword(final Properties properties)
    {
        super(properties.sword(ToolMaterial.DIAMOND, 0.0F, -2.4F));
    }

    @Override
    public void inventoryTick(final ItemStack stack, final ServerLevel level, final Entity owner, final @Nullable EquipmentSlot slot)
    {
        // PORT26: the old isSelected flag is now slot == MAINHAND (Inventory passes MAINHAND for
        // the selected hotbar slot). Also inventoryTick is server-side only now.
        if (owner instanceof Player && slot == EquipmentSlot.MAINHAND)
        {
            RaiderMobUtils.getBarbariansCloseToEntity(owner, GLOW_EFFECT_DISTANCE)
                .forEach(entity -> entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_EFFECT_DURATION, 0)));
        }
    }

    @Override
    public void hurtEnemy(final ItemStack stack, final LivingEntity target, @NotNull final LivingEntity attacker)
    {
        if (attacker instanceof Player && target instanceof AbstractEntityBarbarianRaider)
        {
            target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LEVITATION_EFFECT_DURATION, LEVITATION_EFFECT_MULTIPLIER));
        }
    }
}
