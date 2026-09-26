package com.ldtteam.domumornamentum.block;

import com.ldtteam.domumornamentum.block.decorative.*;
import com.ldtteam.domumornamentum.block.types.BrickType;
import com.ldtteam.domumornamentum.block.types.ExtraBlockType;
import com.ldtteam.domumornamentum.block.types.FramedLightType;
import com.ldtteam.domumornamentum.block.types.TimberFrameType;
import com.ldtteam.domumornamentum.block.vanilla.*;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.item.decoration.*;
import com.ldtteam.domumornamentum.item.interfaces.IDoItem;
import com.ldtteam.domumornamentum.item.vanilla.*;
import com.ldtteam.domumornamentum.shingles.ShingleHeightType;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Class to create the modBlocks.
 * References to the blocks can be made here
 * <p>
 * We disabled the following finals since we are neither able to mark the items as final, nor do we want to provide public accessors.
 */
@SuppressWarnings({"squid:ClassVariableVisibilityCheck", "squid:S2444", "squid:S1444", "squid:S1820",})
public final class ModBlocks implements IModBlocks {
    /**
     * The deferred registry.
     */
    public final static DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Constants.MOD_ID);
    public final static DeferredRegister.Items ITEMS = DeferredRegister.createItems(Constants.MOD_ID);

    private static final List<Supplier<TimberFrameBlock>> TIMBER_FRAMES = new ArrayList<>();
    private static final List<Supplier<FramedLightBlock>> FRAMED_LIGHT = new ArrayList<>();
    private static final List<Supplier<FloatingCarpetBlock>> FLOATING_CARPETS = new ArrayList<>();
    private static final List<Supplier<ExtraBlock>> EXTRA_TOP_BLOCKS = new ArrayList<>();
    private static final List<Supplier<BrickBlock>> BRICK = new ArrayList<>();
    private static final List<Supplier<PillarBlock>> PILLARS = new ArrayList<>();
    private static final List<Supplier<AllBrickBlock>> ALL_BRICK = new ArrayList<>();
    private static final List<Supplier<AllBrickStairBlock>> ALL_BRICK_STAIR = new ArrayList<>();

    private static final ModBlocks INSTANCE = new ModBlocks();

    private static final DeferredBlock<ArchitectsCutterBlock> ARCHITECTS_CUTTER;
    private static final DeferredBlock<ShingleBlock> SHINGLE;
    private static final DeferredBlock<ShingleBlock> SHINGLE_FLAT;
    private static final DeferredBlock<ShingleBlock> SHINGLE_FLAT_LOWER;
    private static final DeferredBlock<ShingleBlock> SHINGLE_STEEP;
    private static final DeferredBlock<ShingleBlock> SHINGLE_STEEP_LOWER;

    private static final DeferredBlock<ShingleSlabBlock> SHINGLE_SLAB;
    private static final DeferredBlock<PaperWallBlock> PAPER_WALL;
    private static final DeferredBlock<BarrelBlock> STANDING_BARREL;
    private static final DeferredBlock<BarrelBlock> LAYING_BARREL;
    private static final DeferredBlock<FenceBlock> FENCE;
    private static final DeferredBlock<FenceGateBlock> FENCE_GATE;
    private static final DeferredBlock<SlabBlock> SLAB;
    private static final DeferredBlock<WallBlock> WALL;
    private static final DeferredBlock<StairBlock> STAIR;
    private static final DeferredBlock<TrapdoorBlock> TRAPDOOR;
    private static final DeferredBlock<DoorBlock> DOOR;
    private static final DeferredBlock<PostBlock> POST;
    private static final DeferredBlock<PanelBlock> PANEL;
    private static final DeferredBlock<FancyDoorBlock> FANCY_DOOR;
    private static final DeferredBlock<FancyTrapdoorBlock> FANCY_TRAPDOOR;
    private static final DeferredBlock<PaperWallBlock> TILED_PAPER_WALL;
    private static final DeferredBlock<DynamicTimberFrameBlock> DYNAMIC_TIMBER_FRAME;

    // PORT26: MC 26.1 requires the block id to be set on BlockBehaviour.Properties *before* the
    // Block constructor runs ("Block id not set" NPE in BlockBehaviour$Properties.effectiveDrops
    // otherwise). DeferredRegister.Blocks#registerBlock(name, factory, properties) sets the
    // ResourceKey on the properties for us, so every block constructor now receives the finished
    // Properties and must not build its own Properties.of() anymore.
    // The same holds for items: Item's constructor now reads its id from Item.Properties
    // ("Item id not set" NPE otherwise), so every BlockItem gets its properties from
    // DeferredRegister.Items#registerItem, which sets the Item ResourceKey before invoking us.
    static {
        ARCHITECTS_CUTTER = registerWithItem("architectscutter",
          ArchitectsCutterBlock::new,
          p -> p.mapColor(MapColor.STONE).sound(SoundType.STONE).requiresCorrectToolForDrops().strength(3.5F),
          BlockItem::new);

        for (final TimberFrameType blockType : TimberFrameType.values()) {
            TIMBER_FRAMES.add(registerWithItem(blockType.getName(),
              props -> new TimberFrameBlock(blockType, props),
              p -> p.mapColor(MapColor.WOOD).pushReaction(PushReaction.PUSH_ONLY).strength(3F, 1F).noOcclusion(),
              TimberFrameBlockItem::new));
        }
        DYNAMIC_TIMBER_FRAME = registerWithItem("dynamic_timberframe",
          DynamicTimberFrameBlock::new,
          p -> p.mapColor(MapColor.WOOD).pushReaction(PushReaction.PUSH_ONLY).strength(3F, 1F).noOcclusion(),
          DynamicTimberFrameBlockItem::new);

        SHINGLE = registerWithItem("shingle", ShingleBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(3F, 1F).noOcclusion(), ShingleBlockItem::new);
        SHINGLE_FLAT = registerWithItem("shingle_flat", ShingleBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(3F, 1F).noOcclusion(), ShingleBlockItem::new);
        SHINGLE_FLAT_LOWER = registerWithItem("shingle_flat_lower", ShingleBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(3F, 1F).noOcclusion(), ShingleBlockItem::new);
        SHINGLE_STEEP = registerWithItem("shingle_steep", ShingleBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(3F, 1F).noOcclusion(), ShingleBlockItem::new);
        SHINGLE_STEEP_LOWER = registerWithItem("shingle_steep_lower", ShingleBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(3F, 1F).noOcclusion(), ShingleBlockItem::new);

        SHINGLE_SLAB = registerWithItem("shingle_slab", ShingleSlabBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(3F, 1F), ShingleSlabBlockItem::new);
        PAPER_WALL = registerWithItem("blockpaperwall", PaperWallBlock::new,
          p -> p.mapColor(MapColor.NONE).isRedstoneConductor((state, getter, pos) -> false).strength(3F, 1F), PaperwallBlockItem::new);
        TILED_PAPER_WALL = registerWithItem("blocktiledpaperwall", PaperWallBlock::new,
          p -> p.mapColor(MapColor.NONE).isRedstoneConductor((state, getter, pos) -> false).strength(3F, 1F), PaperwallBlockItem::new);

        PILLARS.add(registerWithItem("blockpillar", PillarBlock::new,
          p -> p.mapColor(MapColor.STONE).strength(3F, 1F), PillarBlockItem::new));
        PILLARS.add(registerWithItem("blockypillar", PillarBlock::new,
          p -> p.mapColor(MapColor.STONE).strength(3F, 1F), PillarBlockItem::new));
        PILLARS.add(registerWithItem("squarepillar", PillarBlock::new,
          p -> p.mapColor(MapColor.STONE).strength(3F, 1F), PillarBlockItem::new));

        for (final ExtraBlockType blockType : ExtraBlockType.values()) {
            EXTRA_TOP_BLOCKS.add(registerWithItem(blockType.getSerializedName(),
              props -> new ExtraBlock(blockType, props),
              p -> p.mapColor(MapColor.WOOD).sound(blockType.getSoundType()).strength(3F, 1F),
              ExtraBlockItem::new));
        }

        for (final FramedLightType blockType : FramedLightType.values())
        {
            FRAMED_LIGHT.add(registerWithItem(blockType.getName(),
              props -> new FramedLightBlock(blockType, props),
              p -> p.mapColor(MapColor.WOOD).pushReaction(PushReaction.PUSH_ONLY).strength(3F, 1F).noOcclusion().lightLevel(state -> 15),
              FramedLightBlockItem::new));
        }

        for (final DyeColor color : DyeColor.values()) {
            FLOATING_CARPETS.add(registerWithItem(color.getName().toLowerCase(Locale.ROOT) + "_floating_carpet",
              props -> new FloatingCarpetBlock(color, props),
              p -> p.mapColor(MapColor.WOOL).sound(SoundType.WOOL).isRedstoneConductor((state, getter, pos) -> false).forceSolidOff().strength(0.1F),
              BlockItem::new));
        }

        for (final BrickType type : BrickType.values()) {
            BRICK.add(registerWithItem(type.getSerializedName(),
              props -> new BrickBlock(type, props),
              p -> p.mapColor(MapColor.WOOD).sound(SoundType.STONE).strength(3F, 1F),
              BlockItem::new));
        }

        // PORT26: Properties.ofLegacyCopy(Blocks.OAK_PLANKS) is not usable anymore (it would
        // create fresh properties and lose the block id set by the registrar). The planks
        // properties are replicated here instead (verified against Blocks.java 26.1.2:
        // mapColor(WOOD).sound(WOOD).ignitedByLava(), strength overridden like the original).
        STANDING_BARREL = registerWithItem("blockbarreldeco_standing", BarrelBlock::new,
          p -> p.mapColor(MapColor.WOOD).sound(SoundType.WOOD).ignitedByLava().strength(3.0F, 1.0F), BlockItem::new);
        LAYING_BARREL = registerWithItem("blockbarreldeco_onside", BarrelBlock::new,
          p -> p.mapColor(MapColor.WOOD).sound(SoundType.WOOD).ignitedByLava().strength(3.0F, 1.0F), BlockItem::new);

        FENCE = registerWithItem("vanilla_fence_compat", FenceBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(2.0F, 3.0F), FenceBlockItem::new);
        FENCE_GATE = registerWithItem("vanilla_fence_gate_compat", FenceGateBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(2.0F, 3.0F), FenceGateBlockItem::new);
        SLAB = registerWithItem("vanilla_slab_compat", SlabBlock::new,
          p -> p.mapColor(MapColor.WOOD).noOcclusion().strength(2.0F, 3.0F), SlabBlockItem::new);
        WALL = registerWithItem("vanilla_wall_compat", WallBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(2.0F, 3.0F), WallBlockItem::new);
        STAIR = registerWithItem("vanilla_stairs_compat", StairBlock::new,
          p -> p.mapColor(MapColor.WOOD).noOcclusion().strength(2.0F, 3.0F), StairsBlockItem::new);
        TRAPDOOR = registerWithItem("vanilla_trapdoors_compat", TrapdoorBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(3.0F).noOcclusion().isValidSpawn((state, blockGetter, pos, type) -> false), TrapdoorBlockItem::new);
        DOOR = registerWithItem("vanilla_doors_compat", DoorBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(3.0F).noOcclusion().isValidSpawn((state, blockGetter, pos, type) -> false), DoorBlockItem::new);
        PANEL = registerWithItem("panel", PanelBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(3.0F).noOcclusion().isValidSpawn((state, blockGetter, pos, type) -> false), PanelBlockItem::new);
        ALL_BRICK.add(registerWithItem("light_brick", AllBrickBlock::new,
          p -> p.mapColor(MapColor.STONE).strength(3F, 1F), AllBrickBlockItem::new));
        ALL_BRICK.add(registerWithItem("dark_brick", AllBrickBlock::new,
          p -> p.mapColor(MapColor.STONE).strength(3F, 1F), AllBrickBlockItem::new));
        ALL_BRICK_STAIR.add(registerWithItem("light_brick_stair", AllBrickStairBlock::new,
          p -> p.mapColor(MapColor.STONE).sound(SoundType.STONE).strength(3F, 1F), AllBrickStairBlockItem::new));
        ALL_BRICK_STAIR.add(registerWithItem("dark_brick_stair", AllBrickStairBlock::new,
          p -> p.mapColor(MapColor.STONE).sound(SoundType.STONE).strength(3F, 1F), AllBrickStairBlockItem::new));

        POST = registerWithItem("post", PostBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(3.0F), PostBlockItem::new);

        FANCY_DOOR = registerWithItem("fancy_door", FancyDoorBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(3.0F).noOcclusion().isValidSpawn((state, blockGetter, pos, type) -> false), FancyDoorBlockItem::new);
        FANCY_TRAPDOOR = registerWithItem("fancy_trapdoors", FancyTrapdoorBlock::new,
          p -> p.mapColor(MapColor.WOOD).strength(3.0F).noOcclusion().isValidSpawn((state, blockGetter, pos, type) -> false), FancyTrapdoorBlockItem::new);
    }

    /**
     * Specific item groups.
     */
    public Map<Identifier, List<ItemStack>> itemGroups = new TreeMap<>();

    /**
     * Private constructor to hide the implicit public one.
     */
    private ModBlocks() {
    }

    public static ModBlocks getInstance() {
        return INSTANCE;
    }

    /**
     * Utility shorthand to register a block and its item together, using the 26.1 registration
     * flow where both the block and the item id are set on their respective properties by the
     * registrar before the constructors run.
     *
     * @param name       the registry name of the block (and its item)
     * @param block      a factory creating the block from finished block properties
     * @param properties operator configuring the block properties (id is set afterwards)
     * @param item       a factory creating the block item from the block and finished item properties
     * @param <B>        the block type
     * @param <I>        the item type
     * @return the block entry saved to the registry
     */
    private static <B extends Block, I extends BlockItem> DeferredBlock<B> registerWithItem(
      final String name,
      final Function<BlockBehaviour.Properties, ? extends B> block,
      final UnaryOperator<BlockBehaviour.Properties> properties,
      final BiFunction<? super B, Item.Properties, ? extends I> item)
    {
        final DeferredBlock<B> registered = BLOCKS.registerBlock(name, block, properties);
        // PORT26 (batch 9): Item.Properties#useBlockDescriptionPrefix() is REQUIRED for BlockItems —
        // without it the descriptionId defaults to "item.<ns>.<path>" while the lang file ships
        // "block.<ns>.<path>" (vanilla Items.java and NeoForge registerSimpleBlockItem both apply it).
        // Missing prefix = raw translation key shown instead of every DO block name (batch 9 bug #2).
        ITEMS.registerItem(name, props -> item.apply(registered.value(), props.useBlockDescriptionPrefix()));
        return registered;
    }

    @Override
    public ArchitectsCutterBlock getArchitectsCutter() {
        return ModBlocks.ARCHITECTS_CUTTER.get();
    }

    @Override
    public ShingleBlock getShingle(final ShingleHeightType heightType) {
        return switch (heightType)
        {
            case DEFAULT -> ModBlocks.SHINGLE.get();
            case FLAT -> ModBlocks.SHINGLE_FLAT.get();
            case FLAT_LOWER -> ModBlocks.SHINGLE_FLAT_LOWER.get();
            case STEEP -> ModBlocks.SHINGLE_STEEP.get();
            case STEEP_LOWER -> ModBlocks.SHINGLE_STEEP_LOWER.get();
        };
    }

    @Override
    public List<TimberFrameBlock> getTimberFrames() {
        return ModBlocks.TIMBER_FRAMES.stream().map(Supplier::get).collect(Collectors.toList());
    }

    @Override
    public List<FramedLightBlock> getFramedLights()
    {
        return ModBlocks.FRAMED_LIGHT.stream().map(Supplier::get).collect(Collectors.toList());
    }

    @Override
    public List<PillarBlock> getPillars()
    {
        return ModBlocks.PILLARS.stream().map(Supplier::get).collect(Collectors.toList());
    }

    @Override
    public ShingleSlabBlock getShingleSlab() {
        return ModBlocks.SHINGLE_SLAB.get();
    }

    @Override
    public PaperWallBlock getPaperWall() {
        return ModBlocks.PAPER_WALL.get();
    }

    @Override
    public PaperWallBlock getTiledPaperWall() {
        return ModBlocks.TILED_PAPER_WALL.get();
    }

    @Override
    public List<ExtraBlock> getExtraTopBlocks() {
        return ModBlocks.EXTRA_TOP_BLOCKS.stream().map(Supplier::get).toList();
    }

    @Override
    public List<FloatingCarpetBlock> getFloatingCarpets() {
        return ModBlocks.FLOATING_CARPETS.stream().map(Supplier::get).toList();
    }

    @Override
    public BarrelBlock getStandingBarrel() {
        return ModBlocks.STANDING_BARREL.get();
    }

    @Override
    public BarrelBlock getLayingBarrel() {
        return ModBlocks.LAYING_BARREL.get();
    }

    @Override
    public FenceBlock getFence() {
        return ModBlocks.FENCE.get();
    }

    @Override
    public FenceGateBlock getFenceGate() {
        return ModBlocks.FENCE_GATE.get();
    }

    @Override
    public SlabBlock getSlab() {
        return ModBlocks.SLAB.get();
    }

    @Override
    public List<BrickBlock> getBricks() {
        return ModBlocks.BRICK.stream().map(Supplier::get).toList();
    }

    @Override
    public WallBlock getWall() {
        return ModBlocks.WALL.get();
    }

    @Override
    public StairBlock getStair() {
        return ModBlocks.STAIR.get();
    }

    @Override
    public TrapdoorBlock getTrapdoor() {
        return ModBlocks.TRAPDOOR.get();
    }

    @Override
    public PanelBlock getPanel() {
        return ModBlocks.PANEL.get();
    }

    @Override
    public PostBlock getPost() {
        return ModBlocks.POST.get();
    }

    @Override
    public DoorBlock getDoor() {
        return ModBlocks.DOOR.get();
    }

    @Override
    public FancyDoorBlock getFancyDoor() {
        return ModBlocks.FANCY_DOOR.get();
    }

    @Override
    public FancyTrapdoorBlock getFancyTrapdoor() {
        return ModBlocks.FANCY_TRAPDOOR.get();
    }

    @Override
    public List<AllBrickBlock> getAllBrickBlocks() {
        return ModBlocks.ALL_BRICK.stream().map(Supplier::get).toList();
    }

    @Override
    public List<AllBrickStairBlock> getAllBrickStairBlocks() {
        return ModBlocks.ALL_BRICK_STAIR.stream().map(Supplier::get).toList();
    }

    @Override
    public DynamicTimberFrameBlock getDynamicTimberFrame() {
        return ModBlocks.DYNAMIC_TIMBER_FRAME.get();
    }

    /**
     * Get or compute the item group specifics.
     * @return the item group.
     */
    public Map<Identifier, List<ItemStack>> getOrComputeItemGroups()
    {
        if (itemGroups.isEmpty())
        {
            BuiltInRegistries.ITEM.forEach(item -> {
                if (item instanceof IDoItem)
                {
                    final List<ItemStack> itemList = itemGroups.getOrDefault(((IDoItem) item).getGroup(), new ArrayList<>());
                    if (item instanceof BlockItem blockitem && blockitem.getBlock() instanceof IMateriallyTexturedBlock texturedBlock) {
                        if (blockitem.getBlock() instanceof ICachedItemGroupBlock cachedItemGroupBlock)
                        {
                            final NonNullList<ItemStack> stacks = NonNullList.create();
                            cachedItemGroupBlock.fillItemCategory(stacks);

                            for (final ItemStack stack : stacks)
                            {
                                itemList.add(process(stack.copy(), texturedBlock));
                            }
                        }
                        else
                        {
                            itemList.add(process(new ItemStack(item), texturedBlock));
                        }
                    }
                    itemGroups.put(((IDoItem) item).getGroup(), itemList);
                }
            });
        }
        return itemGroups;
    }

    private ItemStack process(final ItemStack stack, final IMateriallyTexturedBlock block)
    {
        final @NotNull List<IMateriallyTexturedBlockComponent> components = new ArrayList<>(block.getComponents());
        final MaterialTextureData.Builder textureData = MaterialTextureData.builder();

        for (final IMateriallyTexturedBlockComponent component : components)
        {
            textureData.setComponent(component.getId(), component.getDefault());
        }

        textureData.writeToItemStack(stack);

        return stack;
    }

    public static Block[] getMateriallyTexturableBlocks() {
        return BLOCKS.getRegistry()
                .get()
                .stream()
                .filter(IMateriallyTexturedBlock.class::isInstance)
                .toArray(Block[]::new);
    }

    public static Item[] getMateriallyTexturableItems() {
        return Arrays.stream(getMateriallyTexturableBlocks())
                .map(block -> BLOCKS.getRegistry().get().getKey(block))
                .map(name -> ITEMS.getRegistry().get().getValue(name)) // PORT26: Registry#get → getValue
                .toArray(Item[]::new);
    }
}
