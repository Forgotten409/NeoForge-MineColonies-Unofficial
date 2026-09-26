package com.minecolonies.api.compatibility.tinkers;

import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.ItemAbility;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Real Tinkers' Construct compatibility — restored for the unofficial
 * <b>Continuum Construct</b> port (CurseForge 1668892, MIT, registers the exact upstream
 * mod id {@code tconstruct} on NeoForge 26.1.2, with its mandatory {@code mantle}
 * companion "Continuum Core").
 *
 * <p><b>PORT26 implementation note — zero compile-time coupling:</b> the historical
 * upstream integration (see the commented bodies in {@code portsrc}/…/TinkersToolHelper)
 * imported {@code slimeknights.tconstruct} classes directly. This port instead implements
 * the same behaviour through <b>vanilla/data surfaces only</b>, so it compiles and runs
 * without any Tinkers jar on the classpath and keeps working across Tinkers API drift:</p>
 * <ul>
 *   <li><b>Item tags</b> (shipped by Continuum's datapack, verified against its jar):
 *       {@code tconstruct:modifiable} (every modifiable tool — the old
 *       {@code ModifiableItem instanceof}), {@code tconstruct:modifiable/melee/sword}
 *       (the old {@code TinkerTags.Items.SWORD}) and {@code tconstruct:modifiable/harvest}
 *       (the old {@code TinkerTags.Items.HARVEST}).</li>
 *   <li><b>Dig abilities by interned name</b>: NeoForge 26.1 removed the
 *       {@code ItemAbilities.AXE_DIG}/{@code PICKAXE_DIG}/{@code SHOVEL_DIG}/
 *       {@code HOE_DIG} constants (the vanilla {@code minecraft:tool} component now
 *       drives "correct tool" checks) — but Tinkers itself never stopped using the
 *       ability <i>names</i>: Continuum's tool definitions declare {@code axe_dig}/
 *       {@code pickaxe_dig}/{@code shovel_dig}/{@code hoe_dig} through the
 *       {@code tconstruct:tool_actions} module (verified in the shipped
 *       {@code data/tconstruct/tinkering/tool_definitions/*.json}: pickaxe →
 *       {@code pickaxe_dig}, hand_axe → {@code axe_dig}, excavator →
 *       {@code shovel_dig}, kama/mattock → {@code hoe_dig}), served at runtime by
 *       {@code ModifiableItem#canPerformAction} → {@code ToolActionsModule}.
 *       Since {@link ItemAbility#get} interns abilities by name, looking the names up
 *       here yields the identical instances Tinkers parses from its datapack, so
 *       {@code ItemStack#canPerformAction} compares equal — restoring the exact
 *       historical {@code canPerformAction(ItemAbilities.*_DIG)} behaviour with zero
 *       compile-time coupling.</li>
 *   <li><b>Tool data</b>: Tinkers 3.x keeps the tool record inside the vanilla
 *       {@code CUSTOM_DATA} component — {@code tic_broken} (boolean, what
 *       {@code ToolDamageUtil.isBroken} reads) and {@code tic_stats} (the
 *       {@code StatsNBT} compound with {@code attack_damage}/{@code harvest_tier}).
 *       Read defensively with the Or-getters, so a layout change degrades to
 *       "not broken / unknown damage", never an exception.</li>
 * </ul>
 *
 * <p>Wired from {@code CompatibilityManager.discoverModCompat()} behind
 * {@code ModList.get().isLoaded("tconstruct")}. Without Tinkers installed the base
 * {@link TinkersToolProxy} no-ops stay in place (identical to upstream behaviour).</p>
 */
public class TinkersToolHelper extends TinkersToolProxy
{
    /** All modifiable Tinkers tools (historical {@code ModifiableItem instanceof}). */
    private static final TagKey<Item> MODIFIABLE = makeItemTag("modifiable");

    /** Tinkers swords (historical {@code TinkerTags.Items.SWORD}). */
    private static final TagKey<Item> MELEE_SWORD = makeItemTag("modifiable/melee/sword");

    /** Harvest-capable Tinkers tools (historical {@code TinkerTags.Items.HARVEST}). */
    private static final TagKey<Item> HARVEST = makeItemTag("modifiable/harvest");

    /**
     * Dig abilities, restored by interned name: NeoForge 26.1 dropped the
     * {@code ItemAbilities.*_DIG} constants, but Tinkers tool definitions still declare
     * these ability names ({@code tconstruct:tool_actions} module) and answer
     * {@code canPerformAction} for them. {@link ItemAbility#get} interns by name, so
     * these are the same instances Tinkers parses from its datapack.
     */
    private static final ItemAbility AXE_DIG     = ItemAbility.get("axe_dig");

    /** Same interned-name lookup as {@link #AXE_DIG}, for shovel-class tools. */
    private static final ItemAbility SHOVEL_DIG  = ItemAbility.get("shovel_dig");

    /** Same interned-name lookup as {@link #AXE_DIG}, for pickaxe-class tools. */
    private static final ItemAbility PICKAXE_DIG = ItemAbility.get("pickaxe_dig");

    /** Same interned-name lookup as {@link #AXE_DIG}, for hoe-class tools. */
    private static final ItemAbility HOE_DIG     = ItemAbility.get("hoe_dig");

    /**
     * Check if a certain itemstack is a tinkers weapon (guard-usable melee weapon).
     *
     * @param stack the stack to check for.
     * @return true if so.
     */
    @Override
    public boolean isTinkersWeapon(@NotNull final ItemStack stack)
    {
        return !stack.isEmpty() && stack.is(MELEE_SWORD);
    }

    /**
     * Check if a certain item stack is a tinkers tool of the given tool type.
     *
     * @param stack the stack to check for.
     * @param toolType the tool type.
     * @return true if so.
     */
    @Override
    public boolean isTinkersTool(@Nullable final ItemStack stack, final EquipmentTypeEntry toolType)
    {
        if (stack == null || stack.isEmpty() || !stack.is(MODIFIABLE))
        {
            return false;
        }

        final String tool = equipmentId(toolType);
        if ("axe".equals(tool) && stack.canPerformAction(AXE_DIG))
        {
            return true;
        }
        if ("shovel".equals(tool) && stack.canPerformAction(SHOVEL_DIG))
        {
            return true;
        }
        if ("pickaxe".equals(tool) && stack.canPerformAction(PICKAXE_DIG))
        {
            return true;
        }
        if ("hoe".equals(tool) && stack.canPerformAction(HOE_DIG))
        {
            return true;
        }
        return stack.is(HARVEST);
    }

    /**
     * Calculate the actual attack damage of the tinkers weapon.
     *
     * @param stack the stack.
     * @return the attack damage (0 when the stat is absent).
     */
    @Override
    public double getAttackDamage(@NotNull final ItemStack stack)
    {
        final CompoundTag stats = toolStats(stack);
        return stats == null ? 0.0D : stats.getDoubleOr("attack_damage", 0.0D);
    }

    /**
     * Calculate the tool level (harvest tier) of the stack.
     *
     * @param stack the stack.
     * @return the harvest tier, or -1 when unknown/broken.
     */
    @Override
    public int getToolLevel(@NotNull final ItemStack stack)
    {
        if (checkTinkersBroken(stack))
        {
            return -1;
        }
        final CompoundTag stats = toolStats(stack);
        return stats == null ? -1 : stats.getIntOr("harvest_tier", -1);
    }

    /**
     * Checks to see if STACK is a tinker's tool, and if it is, checks its data to see if it's broken.
     *
     * @param stack the item in question.
     * @return boolean whether the stack is broken or not.
     */
    @Override
    public boolean checkTinkersBroken(@Nullable final ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null)
        {
            return false;
        }
        final CompoundTag tag = data.copyTag();
        // Tinkers 3.x: ToolDamageUtil.isBroken reads the tic_broken flag; older layouts
        // kept it inside tic_stats/broken — both are checked defensively
        if (tag.getBooleanOr("tic_broken", false))
        {
            return true;
        }
        final CompoundTag stats = tag.getCompoundOrEmpty("tic_stats");
        return !stats.isEmpty() && stats.getBooleanOr("broken", false);
    }

    /**
     * @param stack any stack.
     * @return the tool stats compound ({@code CUSTOM_DATA.tic_stats}), or null when the
     *         stack carries no Tinkers tool data.
     */
    @Nullable
    private static CompoundTag toolStats(@NotNull final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return null;
        }
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null)
        {
            return null;
        }
        final CompoundTag tag = data.copyTag();
        return tag.contains("tic_stats") ? tag.getCompoundOrEmpty("tic_stats") : null;
    }

    /**
     * @param toolType the equipment type entry (may be null).
     * @return its registry path (e.g. {@code "axe"}), or an empty string.
     */
    private static String equipmentId(@Nullable final EquipmentTypeEntry toolType)
    {
        if (toolType == null || toolType.getRegistryName() == null)
        {
            return "";
        }
        return toolType.getRegistryName().getPath();
    }

    /**
     * @param path the tag path below the {@code tconstruct} namespace.
     * @return the item tag key.
     */
    private static TagKey<Item> makeItemTag(final String path)
    {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("tconstruct", path));
    }
}
