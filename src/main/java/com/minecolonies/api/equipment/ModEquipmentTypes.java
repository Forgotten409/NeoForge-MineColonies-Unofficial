package com.minecolonies.api.equipment;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.compatibility.Compatibility;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.translation.ToolTranslationConstants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Class used for storing and registering any EquipmentTypes.
 */
public class ModEquipmentTypes
{
    public final static DeferredRegister<EquipmentTypeEntry> DEFERRED_REGISTER = DeferredRegister.create(CommonMinecoloniesAPIImpl.EQUIPMENT_TYPES, Constants.MOD_ID);

    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> none;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> pickaxe;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> shovel;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> axe;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> hoe;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> sword;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> bow;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> fishing_rod;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> shears;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> shield;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> helmet;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> leggings;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> chestplate;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> boots;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> flint_and_steel;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> lead;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> spear;
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> crossbow;

    static
    {
        none = register("none",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_NONE))
                       .setIsEquipment((itemStack, equipmentType) -> true)
                       .setEquipmentLevel((itemStack, equipmentType) -> -1)
                   .build());

        pickaxe = register("pickaxe",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_PICKAXE))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.is(ItemTags.PICKAXES) || Compatibility.isTinkersTool(
                         itemStack,
                         equipmentType))
                       .setEquipmentLevel(ModEquipmentTypes::vanillaToolLevel)
                  .build());

        shovel = register("shovel",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_SHOVEL))
                       .setIsEquipment((itemStack, equipmentType) -> canPerformDefaultActions(itemStack, ItemAbilities.DEFAULT_SHOVEL_ACTIONS) || Compatibility.isTinkersTool(
                         itemStack,
                         equipmentType))
                       .setEquipmentLevel(ModEquipmentTypes::vanillaToolLevel)
                  .build());

        axe = register("axe",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_AXE))
                       .setIsEquipment((itemStack, equipmentType) -> canPerformDefaultActions(itemStack, ItemAbilities.DEFAULT_AXE_ACTIONS) || Compatibility.isTinkersTool(itemStack,
                         equipmentType))
                       .setEquipmentLevel(ModEquipmentTypes::vanillaToolLevel)
                  .build());

        hoe = register("hoe",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_HOE))
                       .setIsEquipment((itemStack, equipmentType) -> canPerformDefaultActions(itemStack, ItemAbilities.DEFAULT_HOE_ACTIONS) || Compatibility.isTinkersTool(itemStack,
                         equipmentType))
                       .setEquipmentLevel(ModEquipmentTypes::vanillaToolLevel)
                  .build());

        sword = register("sword",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_SWORD))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.is(ItemTags.SWORDS)
                                                                     || Compatibility.isTinkersWeapon(itemStack)
                                                                     || Compatibility.isCustomWeapon(itemStack))
                       .setEquipmentLevel(ModEquipmentTypes::vanillaToolLevel)
                  .build());

        bow = register("bow",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_BOW))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.getItem() instanceof BowItem)
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        crossbow = register("crossbow",
            builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_CROSSBOW))
                .setIsEquipment((itemStack, equipmentType) -> itemStack.getItem() instanceof CrossbowItem)
                .setEquipmentLevel((itemStack, equipmentType) -> durabilityBasedLevel(itemStack, Items.CROSSBOW.getMaxDamage(itemStack)))
                .build());

        fishing_rod = register("rod",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_FISHING_ROD))
                       .setIsEquipment((itemStack, equipmentType) -> canPerformDefaultActions(itemStack, ItemAbilities.DEFAULT_FISHING_ROD_ACTIONS))
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        shears = register("shears",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_SHEARS))
                       .setIsEquipment((itemStack, equipmentType) -> canPerformDefaultActions(itemStack, ItemAbilities.DEFAULT_SHEARS_ACTIONS))
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        shield = register("shield",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_SHIELD))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.getItem() instanceof ShieldItem)
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        helmet = register("helmet",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_HELMET))
                       .setIsEquipment((itemStack, equipmentType) -> isEquippableInSlot(itemStack, EquipmentSlot.HEAD))
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        leggings = register("leggings",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_LEGGINGS))
                       .setIsEquipment((itemStack, equipmentType) -> isEquippableInSlot(itemStack, EquipmentSlot.LEGS))
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        chestplate = register("chestplate",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_CHEST_PLATE))
                       .setIsEquipment((itemStack, equipmentType) -> isEquippableInSlot(itemStack, EquipmentSlot.CHEST))
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        boots = register("boots",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_BOOTS))
                       .setIsEquipment((itemStack, equipmentType) -> isEquippableInSlot(itemStack, EquipmentSlot.FEET))
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        flint_and_steel = register("flintandsteel",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_LIGHTER))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.getItem() instanceof FlintAndSteelItem)
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        lead = register("lead",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_LEAD))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.getItem() instanceof LeadItem)
                       .setEquipmentLevel((itemStack, equipmentType) -> -1)
                  .build());

        spear = register("spear",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_SPEAR))
                      .setIsEquipment((itemStack, equipmentType) -> itemStack.is(ModItems.spear))
                      .setEquipmentLevel((itemStack, equipmentType) -> durabilityBasedLevel(itemStack, ModItems.spear.getMaxDamage(itemStack)))
                  .build());

    }

    /**
     * Get the equipmentType registry.
     *
     * @return The equipmentType registry
     */
    public static Registry<EquipmentTypeEntry> getRegistry()
    {
        return IMinecoloniesAPI.getInstance().getEquipmentTypeRegistry();
    }

    /**
     * Register a new equipmentType to the registry.
     *
     * @param id The unique ID of the equipment type
     * @param consumer The consumer that builds the equipment type
     * @return The registry entry
     */
    private static DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> register(final String id, final Consumer<EquipmentTypeEntry.Builder> consumer)
    {
        EquipmentTypeEntry.Builder equipmentType = new EquipmentTypeEntry.Builder()
                                           .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, id));
        consumer.accept(equipmentType);
        return DEFERRED_REGISTER.register(id, equipmentType::build);
    }

    /**
     * Get the equipment level for vanilla tools.
     *
     * @param equipmentType  The type of vanilla tool
     * @param itemStack The item stack to check
     * @return The tool level
     */
    public static int vanillaToolLevel(final ItemStack itemStack, final EquipmentTypeEntry equipmentType)
    {
        if (Compatibility.isTinkersTool(itemStack, equipmentType) || Compatibility.isTinkersWeapon(itemStack))
        {
            return Compatibility.getToolLevel(itemStack);
        }
        return Compatibility.getItemLevel(itemStack);
    }

    /**
     * Get the durability based item level.
     *
     * @param itemStack The item stack to check
     * @return The item level
     */
    public static int durabilityBasedLevel(ItemStack itemStack, int vanillaItemDurability)
    {
        if (!itemStack.isDamageableItem())
        {
            return 5;
        }

        return Math.min(itemStack.getMaxDamage() / vanillaItemDurability, 5);
    }

    /**
     * PORT26: tool/weapon level on the MineColonies scale (wood/gold 0, stone 1, iron 2, diamond 3, netherite 4).
     * <p>
     * In 1.21.1 this came from the item's Tier. 26.1 items no longer expose their ToolMaterial, and the previous
     * replacement (durability / iron sword durability) rated stone as 0, iron as 1 and diamond/netherite as 5, so a
     * "max: stone" request accepted iron tools. Instead: digging tools carry a TOOL component whose deny-drops rule
     * uses the material's {@code incorrect_for_*_tool} block tag, which identifies the tier exactly (also for modded
     * tools built on vanilla materials). Items without such a rule (swords, other weapons) fall back to comparing
     * durability against the vanilla material durabilities.
     *
     * @param itemStack the tool or weapon.
     * @return the equipment level.
     */
    public static int toolMaterialLevel(final ItemStack itemStack)
    {
        final net.minecraft.world.item.component.Tool tool = itemStack.get(DataComponents.TOOL);
        if (tool != null)
        {
            for (final net.minecraft.world.item.component.Tool.Rule rule : tool.rules())
            {
                if (rule.correctForDrops().isPresent() && !rule.correctForDrops().get())
                {
                    final var tag = rule.blocks().unwrapKey();
                    if (tag.isPresent())
                    {
                        switch (tag.get().location().getPath())
                        {
                            case "incorrect_for_wooden_tool", "incorrect_for_gold_tool":
                                return 0;
                            case "incorrect_for_stone_tool", "incorrect_for_copper_tool":
                                return 1;
                            case "incorrect_for_iron_tool":
                                return 2;
                            case "incorrect_for_diamond_tool":
                                return 3;
                            case "incorrect_for_netherite_tool":
                                return 4;
                            default:
                                break;
                        }
                    }
                }
            }
        }

        if (!itemStack.isDamageableItem())
        {
            return 4;
        }
        final int durability = itemStack.getMaxDamage();
        if (durability < ToolMaterial.STONE.durability())
        {
            return 0;
        }
        if (durability < ToolMaterial.IRON.durability())
        {
            return 1;
        }
        if (durability < ToolMaterial.DIAMOND.durability())
        {
            return 2;
        }
        if (durability < ToolMaterial.NETHERITE.durability())
        {
            return 3;
        }
        return 4;
    }

    /**
     * Populate the tier registry with every item currently in the game.
     * Called once during FMLCommonSetupEvent via MineColonies.preInit.
     */
    @SuppressWarnings("null")
    public static void initRegisterEquipmentTiers()
    {
        int bowRef    = 0;
        int rodRef    = 0;
        int swordRef  = 0;
        int shearsRef = 0;
        int shieldRef = 0;
        int flintRef  = 0;
        try
        {
            bowRef    = new ItemStack(Items.BOW).getMaxDamage();
            rodRef    = new ItemStack(Items.FISHING_ROD).getMaxDamage();
            swordRef  = new ItemStack(Items.IRON_SWORD).getMaxDamage();
            shearsRef = new ItemStack(Items.SHEARS).getMaxDamage();
            shieldRef = new ItemStack(Items.SHIELD).getMaxDamage();
            flintRef  = new ItemStack(Items.FLINT_AND_STEEL).getMaxDamage();
        }
        catch (Exception e)
        {
            // In case something goes wrong with fetching durability references, we can still continue and just won't have durability based tiers for those items.
            Log.getLogger().error("Failed to fetch getMaxDamage references for equipment tier registration, durability based tiers for certain items will not be registered.", e);
            return;
        }
        

        for (final Item item : BuiltInRegistries.ITEM)
        {
            try
            {
                final ItemStack dummy = new ItemStack(item);

                // PORT26: TieredItem removed → weapons carry the WEAPON component; the ToolMaterial
                // is no longer retrievable from the stack, so register by durability-based level.
                if (dummy.has(DataComponents.WEAPON))
                {
                    // PORT26: was durabilityBasedLevel(dummy, swordRef), which put iron tools at stone level
                    Compatibility.registerItemTierIfAbsent(item, toolMaterialLevel(dummy));
                }
                else if (isEquippableArmor(dummy))
                {
                    final int level = ItemStackUtils.getArmorLevel(dummy);
                    if (level > 0)
                    {
                        Compatibility.registerItemTierIfAbsent(item, level);
                    }
                }
                else if (item instanceof BowItem)
                {
                    Compatibility.registerItemTierIfAbsent(item, durabilityBasedLevel(dummy, bowRef));
                }
                else if (canPerformDefaultActions(dummy, ItemAbilities.DEFAULT_FISHING_ROD_ACTIONS))
                {
                    Compatibility.registerItemTierIfAbsent(item, durabilityBasedLevel(dummy, rodRef));
                }
                else if (canPerformDefaultActions(dummy, ItemAbilities.DEFAULT_SHEARS_ACTIONS))
                {
                    Compatibility.registerItemTierIfAbsent(item, durabilityBasedLevel(dummy, shearsRef));
                }
                else if (dummy.getItem() instanceof ShieldItem)
                {
                    Compatibility.registerItemTierIfAbsent(item, durabilityBasedLevel(dummy, shieldRef));
                }
                else if (item instanceof FlintAndSteelItem)
                {
                    Compatibility.registerItemTierIfAbsent(item, durabilityBasedLevel(dummy, flintRef));
                }
            }
            catch (Exception e)
            {
                Log.getLogger().error("Failed to register equipment tiers for item: " + BuiltInRegistries.ITEM.getKey(item), e);
            }
        }
    }

    /**
     * Determine whether an item stack can perform the default actions of a given tool.
     *
     * @param itemStack The item stack to check
     * @param actions   The set of actions to compare
     * @return Whether the item stack can perform the actions
     */
    public static boolean canPerformDefaultActions(ItemStack itemStack, Set<ItemAbility> actions)
    {
        for (final ItemAbility toolAction : actions)
        {
            if (!itemStack.canPerformAction(toolAction))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * PORT26: ArmorItem was removed — armor is identified via the EQUIPPABLE data component.
     */
    private static boolean isEquippableInSlot(final ItemStack stack, final EquipmentSlot slot)
    {
        final Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == slot && isArmorSlot(slot);
    }

    private static boolean isArmorSlot(final EquipmentSlot slot)
    {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    private static boolean isEquippableArmor(final ItemStack stack)
    {
        final Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && isArmorSlot(equippable.slot());
    }
}
