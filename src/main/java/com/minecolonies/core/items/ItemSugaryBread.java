package com.minecolonies.core.items;

import com.minecolonies.api.util.constant.TranslationConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;

/**
 * Sweet Bread, made by the baker. Adds speed, removes poison
 */
public class ItemSugaryBread extends ItemFood
{

    /**
     * Setup the food definition
     */
    private static final FoodProperties sweetBread = (new FoodProperties.Builder())
                                        .nutrition(6)
                                        .saturationModifier(0.7F)
                                        .build();

    /**
     * PORT26: FoodProperties.Builder#effect(Supplier, float) removed in 1.21.2+ — food status
     * effects moved to the CONSUMABLE component (ConsumeEffects). Equivalent of the original
     * .effect(() -> new MobEffectInstance(MobEffects.SPEED, 600), 1.0F): a speed effect
     * applied on consume with probability 1.0 (the probability-less ctor is "always").
     */
    private static final Consumable sweetBreadConsumable = Consumable.builder()
                                        .consumeSeconds(1.6F)
                                        .animation(ItemUseAnimation.EAT)
                                        .sound(SoundEvents.GENERIC_EAT)
                                        .hasConsumeParticles(true)
                                        .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SPEED, 600)))
                                        .build();

    /**
     * Sets the name, creative tab, and registers the Sweet Bread item.
     *
     * @param properties the properties.
     */
    public ItemSugaryBread(final Properties properties)
    {
        // PORT26: two-arg food(FoodProperties, Consumable) wires the effect into the CONSUMABLE component
        // PORT26: use the caller-supplied properties (they carry the item id — "Item id
        // not set" NPE otherwise); append the food like the 1.21.1 ctor did.
        super(properties.food(sweetBread, sweetBreadConsumable), 1);
    }

   /**
    * Remove the poison effect
    */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level worldIn, LivingEntity entityLiving) {

        if (!worldIn.isClientSide())
        {
            entityLiving.removeEffect(MobEffects.POISON);
        }
  
        return super.finishUsingItem(stack, worldIn, entityLiving);
    }    
    
    @Override
    public void appendHoverText(@NotNull final ItemStack stack, @Nullable final TooltipContext ctx, @NotNull final TooltipDisplay display, @NotNull final Consumer<Component> tooltip, @NotNull final TooltipFlag flagIn)
    {
        final MutableComponent guiHint = Component.translatableEscape(TranslationConstants.COM_MINECOLONIES_COREMOD_SUGARY_BREAD_TOOLTIP_GUI);
        guiHint.setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
        tooltip.accept(guiHint);

        super.appendHoverText(stack, ctx, display, tooltip, flagIn);
    }
}
