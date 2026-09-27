package com.ldtteam.domumornamentum.client.model;

import com.google.common.base.Suppliers;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.DomumOrnamentum;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.util.Constants;
import com.ldtteam.domumornamentum.util.MaterialTextureDataUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.joml.Vector3fc;

/**
 * The materially textured item model — PORT 26.1.2.
 *
 * <p>Replaces the 1.21.1 {@code MateriallyTexturedBakedModel#getRenderPasses(ItemStack, boolean)}
 * + {@code SpecificRenderTypeBakedModelWrapper} item path, which no longer exists in the 26.1
 * item model pipeline.
 *
 * <p>Item model JSON: {@code {"model": {"type": "domum_ornamentum:materially_textured",
 * "model": "domum_ornamentum:block/shingle/straight"}}}. The referenced model is baked once as a
 * standard cuboid model; per {@link ItemStack} the texture data component is read and the quads
 * are retextured the same way as the block model path (see {@link RetexturedQuadBuilder}).
 *
 * <p>PORT26 (batch 13) — VARIANT GEOMETRY. In 1.21.1 the item spec models (trapdoors, doors,
 * posts, panels) carried vanilla {@code overrides} driven by custom {@code ItemProperties}
 * predicates ({@code domum_ornamentum:trapdoor_type} etc. — registered in
 * {@code ModBusEventHandler#onFMLClientSetup}); the predicate read the {@code type} blockstate
 * property from the stack's {@code minecraft:block_state} component and returned its enum ordinal,
 * selecting the per-variant spec model. Item overrides + custom predicates were removed in
 * 1.21.2+, so the port's item definitions referenced a single spec — every variant of the same
 * item rendered the same geometry ("all trapdoors look the same"). The equivalent is now explicit:
 * an optional {@code variants} map in the item definition JSON, keyed by the serialized value of
 * the block's {@code type} property, holding the per-variant model location. At render time the
 * stack's block-state component selects the variant — mirroring the original override resolution.
 */
public class MateriallyTexturedItemModel implements ItemModel
{
    /**
     * Batch 9 diagnostics: items whose random-preview material lookup came up empty get logged
     * exactly once, so a broken/missing valid-skins tag is immediately visible in the client log
     * instead of manifesting as "items render as static placeholder" (the reported symptom).
     */
    private static final Set<Identifier> LOGGED_NO_CANDIDATES = ConcurrentHashMap.newKeySet();

    private final Identifier modelLocation;

    /**
     * Render properties (display transforms, particle, gui light) of the BASE model.
     *
     * <p>PORT26 (batch 13): in 1.21.1 the display transforms came from the loader-wrapped item
     * model (e.g. {@code item/vanilla_doors_compat.json}) while the override system only supplied
     * GEOMETRY — the caller applied the wrapper's transforms to every render pass. To reproduce
     * that split, every variant renders with the BASE model's transforms; only the geometry
     * (quads) comes from the selected variant model.
     */
    private final ModelRenderProperties renderProperties;

    /** Default (no variant selected) geometry, baked once. */
    private final BakedVariant defaultVariant;

    /**
     * Per-variant geometries keyed by the serialized value of the block's {@code type} property
     * (e.g. {@code "boss"}, {@code "full"}, {@code "plain"}). Empty for items without variants.
     */
    private final Map<String, BakedVariant> variants;

    /**
     * One baked geometry (base or variant): placeholder quads + the per-variant retexture cache.
     *
     * <p>PORT26 (0.4.3): carries the memoized {@code extents} supplier like vanilla's
     * {@link CuboidItemModelWrapper} — retexturing only swaps sprites/UVs, the quad positions
     * are shared, so one extents array per variant serves every material data.
     * {@code update()} passes it to {@code LayerRenderState#setExtents}; vanilla uses the
     * extents for item bounding boxes (shelf rendering, dropped-item merging, oversized-in-GUI
     * detection) and the port previously left them empty (0×0×0 box).</p>
     */
    private record BakedVariant(
      Identifier location,
      QuadCollection quads,
      boolean ambientOcclusion,
      Supplier<Vector3fc[]> extents,
      Cache<MaterialTextureData, QuadCollection> retexturedCache)
    {
        static BakedVariant bake(final ModelBaker baker, final Identifier location, final ResolvedModel resolvedModel)
        {
            final TextureSlots textureSlots = resolvedModel.getTopTextureSlots();
            final QuadCollection quads = resolvedModel.bakeTopGeometry(textureSlots, baker,
              net.minecraft.client.renderer.block.dispatch.Variant.SimpleModelState.DEFAULT.asModelState());
            final Supplier<Vector3fc[]> extents = Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(quads.getAll()));
            final Cache<MaterialTextureData, QuadCollection> cache = CacheBuilder.newBuilder()
              .expireAfterAccess(2, TimeUnit.MINUTES)
              .concurrencyLevel(4)
              .maximumSize(1000)
              .build();
            return new BakedVariant(location, quads, resolvedModel.getTopAmbientOcclusion(), extents, cache);
        }
    }

    public MateriallyTexturedItemModel(
      final ModelBaker baker,
      final Identifier modelLocation,
      final ResolvedModel resolvedModel,
      final Map<String, Identifier> variantLocations)
    {
        this.modelLocation = modelLocation;
        final TextureSlots textureSlots = resolvedModel.getTopTextureSlots();
        this.renderProperties = ModelRenderProperties.fromResolvedModel(baker, resolvedModel, textureSlots);
        this.defaultVariant = BakedVariant.bake(baker, modelLocation, resolvedModel);

        final Map<String, BakedVariant> baked = new LinkedHashMap<>();
        for (final Map.Entry<String, Identifier> entry : variantLocations.entrySet())
        {
            final ResolvedModel variantModel = baker.getModel(entry.getValue());
            baked.put(entry.getKey(), BakedVariant.bake(baker, entry.getValue(), variantModel));
        }
        this.variants = ImmutableMap.copyOf(baked);
    }

    @Override
    public void update(
      final ItemStackRenderState output,
      final ItemStack item,
      final ItemModelResolver resolver,
      final ItemDisplayContext displayContext,
      @Nullable final ClientLevel level,
      @Nullable final ItemOwner owner,
      final int seed)
    {
        if (!(item.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof IMateriallyTexturedBlock texturedBlock))
        {
            return;
        }

        // PORT26 (batch 13): select the variant geometry from the stack's block-state component —
        // the replacement for the 1.21.1 item-model override/predicate mechanism.
        final BakedVariant variant = resolveVariant(item, blockItem);

        // PORT26 (batch 12) — GUI ITEM ATLAS IDENTITY. This was THE root cause of "every DO
        // icon shows the same block and never cycles":
        // 26.1 does NOT draw GUI items directly — GuiRenderer renders each item ONCE into a
        // texture-atlas slot (GuiItemAtlas#getOrUpdate) and then blits the cached slot. The
        // atlas slot is keyed by the MODEL IDENTITY collected through
        // TrackingItemStackRenderState#getModelIdentity — the list of elements models append
        // via ItemStackRenderState#appendModelIdentityElement during update(). Vanilla models
        // append themselves (CuboidItemModelWrapper#update: output.appendModelIdentityElement(this)).
        // Our model appended NOTHING, so every materially-textured item shared the same identity
        // (the empty list) => ONE shared atlas slot for ALL DO items => every icon displayed the
        // same first-rendered bitmap, frozen forever — while the tooltip (which bypasses the
        // atlas) kept cycling its materials. Appending `this` gives every item its own slot.
        output.appendModelIdentityElement(this);

        // PORT26 (batch 13) — the selected variant's model location is part of the identity so
        // that two stacks of the same item with different `type` blockstate values (e.g. the
        // fifteen trapdoor variants) get distinct atlas slots even when their material data is
        // identical.
        output.appendModelIdentityElement(variant.location());

        MaterialTextureData textureData = MaterialTextureData.readFromItemStack(item);
        if (textureData.isEmpty())
        {
            // no material picked yet — show randomized preview like 1.21.1 did
            textureData = MaterialTextureDataUtil.generateRandomTextureDataFrom(item);
            if (textureData.isEmpty())
            {
                // PORT26 (batch 9): 1.21.1's MateriallyTexturedBakedModel#getRenderPasses fell
                // through to the unmodified inner model when the random preview produced no data —
                // the item showed the spec's placeholder textures (e.g. the fence looks like an
                // oak fence) instead of disappearing. Reproduce that degradation here and leave a
                // one-time breadcrumb in the log: empty candidates almost always means the
                // valid-skins tags under data/domum_ornamentum/tags/block/ did not load.
                final Identifier itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
                if (LOGGED_NO_CANDIDATES.add(itemId))
                {
                    DomumOrnamentum.LOGGER.warn(
                      "[DO] No material candidates for item {} — its valid-skins tag is empty/missing "
                        + "(check data/domum_ornamentum/tags/block/); rendering placeholder textures instead",
                      itemId);
                }
                final ItemStackRenderState.LayerRenderState layer = output.newLayer();
                renderProperties.applyToLayer(layer, displayContext);
                layer.setUsesBlockLight(true); // see the note at the retextured layer below
                layer.prepareQuadList().addAll(variant.quads().getAll());
                // PORT26 (0.4.3): vanilla parity (CuboidItemModelWrapper) — extents + animated flag
                layer.setExtents(variant.extents());
                if (variant.quads().hasMaterialFlag(BakedQuad.FLAG_ANIMATED))
                {
                    output.setAnimated();
                }
                return;
            }
        }

        // PORT26 (batch 12) — the retextured quads (and their tints) derive from the texture
        // data, so the atlas slot must be re-rendered whenever the data changes. Appending the
        // data itself as an identity element gives exactly the original 1.21.1 behaviour:
        //   * crafted stacks (constant component data) keep a stable identity — the atlas slot
        //     stays cached, zero re-draws;
        //   * showcase stacks without material data use the cycling random preview (data changes
        //     once per second) — the identity changes with it and the slot is re-drawn per
        //     material, exactly like vanilla's tint-value identity elements
        //     (CuboidItemModelWrapper appends each computed tint int for the same reason).
        // MaterialTextureData is a record — list identity equality works out of the box.
        output.appendModelIdentityElement(textureData);

        final var layer = output.newLayer();
        renderProperties.applyToLayer(layer, displayContext);
        // PORT26 FIX v4 (0.4.4, GUI items pale/flat — "blade, szare, bez cieni i kontrastu"):
        // DO items are 3D block-shaped models — in GUI they must use the BLOCK light setup
        // (Lighting.Entry.ITEMS_3D: two directional lights shading the faces by normal),
        // exactly like vanilla block items (their models inherit "gui_light": "side" from
        // minecraft:block/block). usesBlockLight comes from the resolved model's gui_light —
        // the default IS side, but any "gui_light": "front" anywhere in the parent chain
        // (or a future loader change) silently downgrades DO items to ITEMS_FLAT: flat,
        // washed-out, contrast-less icons. This is forced here — a materially textured
        // block item is always block-lit, so the fallback can never regress it.
        layer.setUsesBlockLight(true);
        layer.setParticleMaterial(RetexturedQuadBuilder.retexturedParticle(textureData, renderProperties.particleMaterial()));

        // retexture the placeholder geometry for this stack — cached per texture data per variant
        // (update() runs every frame; the retexture itself only happens on data changes)
        final QuadCollection retextured = buildRetextured(variant, textureData, texturedBlock);
        layer.prepareQuadList().addAll(retextured.getAll());

        // PORT26 (0.4.3): vanilla parity (CuboidItemModelWrapper#update) — the layer needs the
        // geometry extents (retexturing keeps the source quad positions, so the variant extents
        // are exact), and the render state must be flagged animated when any sprite is animated
        // (water/lava-framed DO blocks) — otherwise the GUI item atlas caches the slot forever
        // with a frozen animation frame (vanilla checks quads.hasMaterialFlag(FLAG_ANIMATED)).
        layer.setExtents(variant.extents());
        if (retextured.hasMaterialFlag(BakedQuad.FLAG_ANIMATED))
        {
            output.setAnimated();
        }

        // compute tint layers for every encoded tint index present in the quads;
        // every quad of a component shares its tint index — resolve each distinct index once
        final var tints = layer.tintLayers();
        final boolean[] resolvedTint = new boolean[RetexturedQuadBuilder.TINT_LAYERS];
        for (final var quad : retextured.getAll())
        {
            final int tintIndex = quad.materialInfo().tintIndex();
            if (tintIndex >= 0 && tintIndex < resolvedTint.length)
            {
                while (tints.size() <= tintIndex)
                {
                    tints.add(-1);
                }
                if (!resolvedTint[tintIndex])
                {
                    resolvedTint[tintIndex] = true;
                    tints.set(tintIndex, resolveItemTint(textureData, texturedBlock, tintIndex));
                }
            }
        }

    }

    /**
     * Resolves the geometry variant for the stack — the port of the 1.21.1 override/predicate
     * mechanism ({@code ModBusEventHandler#getTypeOrdinal} + item model {@code overrides}).
     *
     * <p>Reads the {@code type} property (see {@link Constants#TYPE_BLOCK_PROPERTY}) from the
     * stack's {@code minecraft:block_state} component and looks it up in the {@code variants}
     * map of the item definition. Stacks without the component (or unknown values) fall back to
     * the default model — 1.21.1 fell back to the predicate's default enum value, whose override
     * pointed at the same "full"/"plain" spec the item definitions use as their base model.
     */
    private BakedVariant resolveVariant(final ItemStack stack, final BlockItem blockItem)
    {
        if (this.variants.isEmpty())
        {
            return this.defaultVariant;
        }

        final Property<?> typeProperty = blockItem.getBlock().getStateDefinition().getProperty(Constants.TYPE_BLOCK_PROPERTY);
        if (typeProperty == null)
        {
            return this.defaultVariant;
        }

        final String value = getSerializedPropertyValue(stack, typeProperty);
        if (value == null)
        {
            return this.defaultVariant;
        }

        return this.variants.getOrDefault(value, this.defaultVariant);
    }

    /**
     * Reads a property's serialized value name off the stack's block-state component.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    private static String getSerializedPropertyValue(final ItemStack stack, final Property property)
    {
        try
        {
            final Comparable value = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(property);
            return value == null ? null : property.getName(value);
        }
        catch (final Exception e)
        {
            return null;
        }
    }

    /**
     * Builds (and caches) the retextured quads for the given texture data and geometry variant.
     */
    private QuadCollection buildRetextured(final BakedVariant variant, final MaterialTextureData textureData, final IMateriallyTexturedBlock block)
    {
        try
        {
            return variant.retexturedCache().get(textureData, () -> RetexturedQuadBuilder.retexture(
              new MateriallyTexturedBlockStateModel.RetexturedModelPart(variant.quads(), variant.ambientOcclusion(), renderProperties.particleMaterial()),
              textureData,
              block));
        }
        catch (final Exception e)
        {
            return QuadCollection.EMPTY;
        }
    }

    /**
     * Resolves the tint color for an item quad tint layer — 26.1.2 items resolve tint values
     * directly at render-state update time (no ItemColors registry lookup), so we decode the
     * contained block and ask its tint source for the out-of-world (default) color.
     *
     * <p>PORT26 (batch 8 crash fix): {@code BlockTintSource#colorInWorld(state, level, pos)} MUST
     * NOT be called with a null level — vanilla's world-aware sources (e.g. {@code foliage()}) call
     * {@code level.getBlockTint(...)} unguarded and NPE in item context (crash: creative inventory
     * → MateriallyTexturedItemModel.resolveItemTint). The context-free variant
     * {@code color(BlockState)} is the interface's abstract method returning the out-of-world
     * default color (vanilla {@code BlockStateModelWrapper#updateTints} uses exactly this when
     * rendering tinted block models as items; e.g. vanilla's own leaves item tints are the default
     * foliage color constants).
     *
     * <p>PORT26 (0.4.3): batch 11 resolved the batch-8 NPE by sampling the REAL client level at
     * {@code BlockPos.ZERO} — but that is the <em>world</em> tint (biome color at world origin),
     * not the <em>item</em> tint. Vanilla items in GUI always show the constant in-hand color
     * ({@code color(state)}): a grass block item is the default grass green, never the swamp
     * olive that happens to cover (0,0,0). In many biomes (swamp / dark forest / taiga) the
     * world-sampled color is significantly darker, so DO items textured with grass/foliage-tinted
     * components came out dark and muddy in every inventory slot. This now matches vanilla
     * exactly: the constant in-hand color, try/catch-guarded so a tint failure can never kill
     * item rendering.</p>
     */
    private static int resolveItemTint(final MaterialTextureData textureData, final IMateriallyTexturedBlock block, final int tintLayer)
    {
        final int componentIndex = RetexturedQuadBuilder.decodeComponentIndex(tintLayer);
        final int targetTintIndex = RetexturedQuadBuilder.decodeTargetTintIndex(tintLayer);

        final var components = block.getComponents();
        if (componentIndex >= components.size())
        {
            return -1;
        }
        // PORT26: getComponents() is a Collection — index access via helper
        final var component = IMateriallyTexturedBlock.getComponentAtIndex(components, componentIndex);
        if (component == null)
        {
            return -1;
        }
        final Block contained = textureData.getTexturedComponents().get(component.getId());
        if (contained == null)
        {
            return -1;
        }
        final var containedState = contained.defaultBlockState();
        final var source = Minecraft.getInstance().getBlockColors().getTintSource(containedState, targetTintIndex);
        if (source == null)
        {
            return -1;
        }
        // PORT26 (0.4.3): vanilla-parity item tint — the constant in-hand color, exactly what
        // vanilla's BlockStateModelWrapper#updateTints uses for tinted block items in GUI.
        // Never the biome-sampled world color (dark in swamp/dark-forest/taiga biomes — that was
        // the "items look dark and bland in every inventory" report). The interface default
        // colorInWorld(state, level, pos) just forwards to color(state) unless overridden, and
        // the world-aware overrides are the ones that NPE'd in batch 8 — so calling color()
        // directly is both the vanilla behavior and the safe one. Hard-guarded regardless.
        try
        {
            return source.color(containedState);
        }
        catch (final Exception e)
        {
            return -1;
        }
    }

    /**
     * The unbaked (JSON) form.
     *
     * <p>Fields: {@code model} (default/base model location — also the source of the display
     * transforms) and the optional {@code variants} map (serialized {@code type} property value →
     * variant model location, batch 13).
     */
    public record Unbaked(Identifier modelLocation, Map<String, Identifier> variants) implements ItemModel.Unbaked
    {
        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
          Identifier.CODEC.fieldOf("model").forGetter(Unbaked::modelLocation),
          Codec.unboundedMap(Codec.STRING, Identifier.CODEC).optionalFieldOf("variants", Map.of()).forGetter(Unbaked::variants)
        ).apply(i, Unbaked::new));

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type()
        {
            return CODEC;
        }

        @Override
        public void resolveDependencies(final ResolvableModel.Resolver resolver)
        {
            resolver.markDependency(this.modelLocation);
            for (final Identifier variant : this.variants.values())
            {
                resolver.markDependency(variant);
            }
        }

        @Override
        public ItemModel bake(final BakingContext context, final org.joml.Matrix4fc transformation)
        {
            final ModelBaker baker = context.blockModelBaker();
            final ResolvedModel resolved = baker.getModel(this.modelLocation);
            return new MateriallyTexturedItemModel(baker, this.modelLocation, resolved, this.variants);
        }
    }
}
