package com.minecolonies.api.loot;

import com.minecolonies.api.util.constant.Constants;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.criterion.DataComponentMatchers;
import net.minecraft.advancements.criterion.EnchantmentPredicate;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.core.component.predicates.EnchantmentsPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * Container class for registering custom loot conditions.
 *
 * <p>PORT26: {@code LootItemConditionType} was removed — the LOOT_CONDITION_TYPE registry
 * now holds the conditions' {@link com.mojang.serialization.MapCodec}s directly (see
 * {@code LootItemConditions#bootstrap}), and conditions implement {@code codec()}
 * instead of the old {@code getType()}.</p>
 */
public final class ModLootConditions
{
    public final static DeferredRegister<MapCodec<? extends LootItemCondition>> DEFERRED_REGISTER = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Constants.MOD_ID);

    public static final Identifier ENTITY_IN_BIOME_TAG_ID = Identifier.fromNamespaceAndPath(MOD_ID, "entity_in_biome_tag");
    public static final Identifier RESEARCH_UNLOCKED_ID = Identifier.fromNamespaceAndPath(MOD_ID, "research_unlocked");
    public static final Identifier GENERATE_SUPPLY_LOOT_ID = Identifier.fromNamespaceAndPath(MOD_ID, "generate_supply_loot");

    public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<? extends LootItemCondition>> entityInBiomeTag;
    public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<? extends LootItemCondition>> researchUnlocked;
    public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<? extends LootItemCondition>> generateSupplyLoot;

    public static void init()
    {
        // just for classloading
    }

    static
    {
        entityInBiomeTag = DEFERRED_REGISTER.register(ModLootConditions.ENTITY_IN_BIOME_TAG_ID.getPath(),
          () -> EntityInBiomeTag.CODEC);

        researchUnlocked = DEFERRED_REGISTER.register(ModLootConditions.RESEARCH_UNLOCKED_ID.getPath(),
          () -> ResearchUnlocked.CODEC);

        generateSupplyLoot = DEFERRED_REGISTER.register(ModLootConditions.GENERATE_SUPPLY_LOOT_ID.getPath(),
                () -> GenerateSupplyLoot.CODEC);
    }

    // ------------------------------------------------------------------
    // PORT26: convenience conditions (was: static HAS_* constants + helpers).
    // ItemPredicate#of now needs a HolderGetter and the sub-predicate system is gone —
    // enchantment checks go through DataComponentMatchers (vanilla BlockLootSubProvider
    // pattern). All helpers take the registry lookup now.
    // ------------------------------------------------------------------

    public static LootItemCondition.Builder hasShears(@NotNull final HolderLookup.Provider provider)
    {
        return MatchTool.toolMatches(ItemPredicate.Builder.item()
          .of(provider.lookupOrThrow(Registries.ITEM), net.minecraft.world.item.Items.SHEARS));
    }

    public static LootItemCondition.Builder hasHoe(@NotNull final HolderLookup.Provider provider)
    {
        return MatchTool.toolMatches(ItemPredicate.Builder.item()
          .of(provider.lookupOrThrow(Registries.ITEM), TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("hoes"))));
    }

    public static LootItemCondition.Builder hasSilkTouch(@NotNull final HolderLookup.RegistryLookup<Enchantment> enchantments)
    {
        return MatchTool.toolMatches(
                ItemPredicate.Builder.item()
                        .withComponents(
                                DataComponentMatchers.Builder.components()
                                        .partial(
                                                DataComponentPredicates.ENCHANTMENTS,
                                                EnchantmentsPredicate.enchantments(
                                                        List.of(new EnchantmentPredicate(enchantments.getOrThrow(Enchantments.SILK_TOUCH), MinMaxBounds.Ints.atLeast(1)))
                                                )
                                        )
                                        .build()
                        )
        );
    }

    public static LootItemCondition.Builder hasShearsOrSilkTouch(@NotNull final HolderLookup.Provider provider)
    {
        return hasShears(provider).or(hasSilkTouch(provider.lookupOrThrow(Registries.ENCHANTMENT)));
    }

    public static LootItemCondition.Builder doesNotHaveShearsOrSilkTouch(@NotNull final HolderLookup.Provider provider)
    {
        return hasShearsOrSilkTouch(provider).invert();
    }


    private ModLootConditions()
    {
        throw new IllegalStateException("Tried to initialize: ModLootConditions but this is a Utility class.");
    }
}
