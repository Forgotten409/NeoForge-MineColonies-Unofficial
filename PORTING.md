# MineColonies 1.21.1 → 26.1.2 Port Plan

## Source Baseline (locked)
| Mod | Version | Source | Java files |
|---|---|---|---|
| minecolonies | 1.1.1374-1.21.1-snapshot | github.com/ldtteam/minecolonies tag v1.21.1-1.1.1374-snapshot | 2060 |
| structurize | 1.0.832-1.21.1 | github.com/ldtteam/structurize tag v1.21.1-1.0.832 | 194 |
| blockui | 1.0.211-1.21.1-snapshot | github.com/ldtteam/blockui tag v1.21.1-1.0.211-snapshot | 109 |
| domum-ornamentum | 1.0.234-snapshot | github.com/ldtteam/domum-ornamentum tag v1.21.1-1.0.234-snapshot | 231 |
| multipiston | 1.2.58-1.21.1 | github.com/ldtteam/Piston-Unlimited branch release/1.21.1 | 8 |
| towntalk | 1.2.0 | github.com/ldtteam/towntalk branch version/1.21 | 2 (+ sound assets) |

Reference sources cloned at: `/home/z/my-project/portsrc/` (283 MB total, 2604 java files).

## Target
- Minecraft **26.1.2**, NeoForge **26.1.2.95**, Java **25** (source was MC 1.21.1 / NeoForge 21.1.80 / Java 21)
- **SINGLE merged mod** — mod id `minecolonies`, group `com.ldtteam`.
  All former dependencies live in this one jar. No separate structurize/blockui/domum/multipiston/towntalk mods.
- `sophisticatedstorage-26.1.2-1.5.112.2123.jar` stays a runtime jar dependency (already on 26.1.2).

## Port Phases (dependency order — bottom-up)
- **PHASE 0 — skeleton** (DONE): gradle 26.1.2 build files, entry class, mods.toml.
- **PHASE 1 — blockui** (FUNCTIONAL — GUI opens, parses, renders, interacts; runtime-tested):
  all 108 files copied + ported.
  Deep rewrites: BOScreen (render→extractRenderState, projection hijack removed,
  vanilla background override, tooltip stratum), BOGuiGraphics (extends GuiGraphics →
  wrapper over GuiGraphicsExtractor + blit tint), UiRenderMacros (own tessellation →
  extractor primitives fill/fillGradient/blit, entity render states), Pane scissors
  (GL scissor → gui.enableScissor), AtlasManager (TextureAtlasHolder hack →
  RegisterTextureAtlasesEvent), out-of-jar texture subsystem (OutOfJarTexture +
  SpriteTexture as ReloadableTextures on the GpuTexture pipeline), cursors
  (CursorTexture + MissingCursorTexture fallback, reload-on-load), player skins
  (SkinManager → ClientAsset.Texture), TextField selection (GL logic op →
  textHighlight), EntityIcon (bufferSource → render states).
  PORT26-TODO markers in code: **0**. Upstream 1.21.1 TODO markers: **0** (sweep
  complete — see "Upstream TODO sweep" below).

  ### Remaining dev-log lines (intended, not bugs)
  The 4 lines that appear when opening the test GUI are deliberate fallback
  exercises from the upstream test suite — 1.21.1 logged the same situations
  (louder, with stacktraces):
  - `CursorUtils: Failed to load cursor texture ... missingcursortexture` — test.xml
    button with a missing cursor texture on purpose (fallback cursor is set).
  - `Cannot parse item nbt, rendering plain item` — test.xml uses a
    domum-ornamentum block entity NBT; the key registers in PHASE 2 and the
    warning disappears for good.
  - `Missing resource blockui:veryuglymissingtexture` — test image source that
    does not exist on purpose (renders the vanilla missing-texture checkerboard).
  - `Missing resource blockui:./blockui/missing_out_of_jar.png` — test.xml + the
    test GUI entry load `<gamedir>/blockui/missing_out_of_jar.png` on purpose
    (out-of-jar fallback path, renders the checkerboard).

  ### Upstream TODO sweep (all resolved)
  - `Loader` "create missing gui and don't crash" — implemented: missing/bad gui
    resources now log an error and open a built-in fallback window instead of
    throwing a RuntimeException that kills the client.
  - `AbstractTextElement` "fix me, surely not like this" + "add ellipsis if cut" —
    implemented: line cut is now height-aware (spacers carry their own pixel
    heights) and truncated text gets a `...` indicator on the last visible line.
  - `Scrollbar` "Parse Scrollbar-specific Params" — implemented:
    `scrollbarBackground` / `scrollbarColor` / `scrollbarColorHighlight` xml
    attributes (plus the already-parsed `scrollbarOffset`).
  - `BlockStateRenderingData` "move to tag" — implemented: new
    `blockui:y_rotation_models` block tag wins over property-based detection.
  - `FakeChunk` "return only BEs in this chunk" — implemented:
    getBlockEntities() filters the fake level's BEs by chunk position.
  - `ClientEventSubscriber` commented-out RenderLevelLastEvent block — removed
    (dead code; in-world hooks stay non-functional, upstream parity).
  - `HookScreen` "rework in-game gui rendering", `ContainerHook` "tag reloading",
    `Configurations`, `FakeLevel`/`FakeLevelChunkSection`/`FakeChunk` heightmaps
    notes, `block_ui.xsd` assert — converted to documentation comments stating
    the deliberate behavior (all are upstream design notes or disabled features,
    not port debt).

  ### Known limitations (documented, deliberate — revisit only if a phase needs them)
  - In-world GUI hooks (HookScreen world rendering + RenderLevelLastEvent entry)
    are non-functional — upstream parity: broken/disabled in the 1.21.1 source too.
  - `BOGuiGraphics.renderBlockStateAsItem` renders through the item path: vanilla
    honors the BLOCK_STATE component (property overrides render correctly), but
    block-entity-driven retexturing (domum-ornamentum) needs the fake-level block
    render path — deferred to the domum phase.
  - `BlockStateRenderingData.checkModelForYrotation` uses the
    `blockui:y_rotation_models` tag + blockstate properties (axis/facing) instead
    of model element introspection (old ModelBakery ATs gone).
  - Widget state dimming (disabled/hovered buttons) is applied per-blit
    (`BOGuiGraphics.withBlitTint`); raw-UV blits (nine-slice/repeat paths) cannot be
    tinted — vanilla has no color variant of that overload.
  - Cursor selection priority is "last set wins" (no depth accessor on the 2D pose
    stack; the 1.21.1 version compared pose depth).
  - Layered screens (`openAsLayer`) fall back to regular screen replacement —
    pushGuiLayer/popGuiLayer were removed in 26.1.2.
  Status: runtime-verified by user (GUI opens/renders/interacts, skins, tooltips,
  scrolling lists); log is clean apart from the intended test-case lines above.
- **PHASE 2 — domum-ornamentum** (231 files): decorative blocks. Merged as `com.ldtteam.domumornamentum`.
  Status: **main code ported** (compilation-iteration 1 fixes applied); model system rewritten for
  the 26.1.2 pipeline; **datagen (87 providers) moved to `src/datagen/java` pending rewrite**
  to the new vanilla `net.minecraft.client.data.models.ModelProvider` system.
  - Block API fixes (26.1.2): `updateShape` → 8 args `(state, LevelReader, ScheduledTickAccess,
    pos, dir, neighbourPos, neighbourState, RandomSource)`; `getCloneItemStack` →
    `(LevelReader, BlockPos, BlockState, boolean includeData)`; `DirectionProperty` removed →
    `EnumProperty<Direction>`; `onRemove` removed → `Block#destroy(LevelAccessor, BlockPos,
    BlockState)`.
  - Removed per-material hooks that no longer exist in 26.1.2 (PORT26 notes in
    `IMateriallyTexturedBlock`): `getDOExplosionResistance` (resistance is a fixed Properties
    value now) and `getDOSoundType` (only 1-arg `getSoundType(BlockState)` remains). Destroy
    progress delegation kept (hook survives).
  - Renames: `ResourceKey.location()` → `identifier()`; `Registry.get(Identifier)` →
    `getValue(Identifier)`; `advancements.critereon` → `advancements.criterion`;
    `RecipeOutput.accept(ResourceKey<Recipe<?>>, Recipe, AdvancementHolder)` +
    `RecipeUnlockedTrigger.unlocked(ResourceKey)`.
  - Model system (runtime) — rewritten, registered in `client/event/handlers/ModBusEventHandler`:
    custom block state model type `domum_ornamentum:materially_textured`
    (`MateriallyTexturedBlockStateModel.Unbaked` + codec, `RegisterBlockStateModels`), custom
    item model type (`MateriallyTexturedItemModel.Unbaked`, `RegisterItemModelsEvent`), tint
    sources via `RegisterColorHandlersEvent.BlockTintSources` (64 layers, tintIndex encoding
    `componentIdx << 3 | targetTint` — see `RetexturedQuadBuilder.encodeTintIndex`).
  - Vendored `com.ldtteam.data.LanguageProvider` (upstream external lib) in
    `src/main/java/com/ldtteam/data/` — aggregates per-category lang sub-providers.
  - Old hand-written asset JSONs (205 files in `main/resources/assets`) reference the removed
    1.21.1 `"loader": "domum_ornamentum:materially_textured"` system and stale rotations
    (Quadrant-only now) — they degrade to missing models until the datagen rewrite regenerates
    them (blockstates reference the new `"type"` custom model instead).
  - Datagen TODO (next iterations): port 87 providers in `src/datagen/java` from the removed
    NeoForge `BlockStateProvider`/`ModelBuilder`/`ModelFile`/`ExistingFileHelper` DSL to
    `GatherDataEvent.Client` + `ModelProvider.registerModels(BlockModelGenerators,
    ItemModelGenerators)` + `MultiVariantGenerator` + `CustomBlockStateModelBuilder.Simple(
    new MateriallyTexturedBlockStateModel.Unbaked(...))` (+ lang/tag/recipe/loot provider ctor
    updates); enable the commented `datagen` source set in build.gradle; re-add the
    GatherDataEvent wiring; run `./gradlew clientData` and move output to main resources.
  - JEI integration (4 files) — skipped as planned (external dep; document compat later).
- **PHASE 3 — towntalk** (2 files): voice chat, mostly assets. Merged as `com.ldtteam.towntalk`.
- **PHASE 4 — structurize** (194 files): depends on blockui. Merged as `com.ldtteam.structurize`.
- **PHASE 5 — multipiston** (8 files): depends on structurize. Merged as `com.ldtteam.multipiston`.
- **PHASE 6 — minecolonies** (2060 files): the main mod. Ported last, on top of merged deps.
  Status: **compile-clean** (2502 java files; 30-error batch fixed 12.09). First runtime crash fixed
  (mod construction, 13.09): `LanguageCache.load` NPE — `assets/minecolonies/lang/en_us.json` was
  missing (upstream ships lang via **Crowdin during Gradle build** — the files are NOT in the git
  repo, only `default.json`/`quests.json`/`tag.item.json` from datagen are). Fix:
  - `assets/minecolonies/lang/en_us.json` (3898 keys) + `pl_pl.json` extracted from the original
    `minecolonies-1.1.1374-1.21.1-snapshot.jar` (exact locked baseline, CurseForge file 8621898) —
    covers all default/quests/tag.item keys (1131/1132) + items/blocks/GUI/advancements;
  - `LanguageHandler.load` hardened: null-safe fallback chain locale → en_us → default.json →
    warn+skip (upstream code passed a null stream straight into `new InputStreamReader`).
  Second runtime crash fixed (13.09 00:30): `ConfigTracker` "config file conflict on
  minecolonies-client.toml" — both merged bootstraps (structurize + minecolonies) built
  `Configurations` on the ONE shared ModContainer, and the tracker derives the default file name
  from the mod id. Fix: `Configurations` gained an explicit config-namespace constructor
  (`ConfigTracker.registerConfig(type, spec, container, fileName)`); structurize registers
  `structurize-client/-server.toml`, minecolonies `minecolonies-client/-server/-common.toml` —
  the ORIGINAL file names, so existing 1.21.1 config files carry over 1:1 (BackUpHelper's
  hardcoded `serverconfig/minecolonies-server.toml` still matches). Config screen extension
  point is registered twice (identical value, Map.put overwrite — harmless).
  Third runtime crash fixed (13.09 00:42): EventBus 8.0.5 rejects listeners on ABSTRACT event
  types — 26.1.2 split `RenderLevelStageEvent` into an abstract base + 8 stage sub-classes
  (AfterSky/AfterOpaqueBlocks/AfterOpaqueFeatures/AfterTranslucentFeatures/AfterTranslucentBlocks/
  AfterTranslucentParticles/AfterWeather/AfterLevel). `ClientEventHandler.renderWorldLastEvent`
  still used the abstract type. Fix: split into two listeners on the sub-events
  `WorldEventContext.renderWithinContext` actually acts on — `AfterOpaqueFeatures`
  (STAGE_FOR_LINES: borders, blueprints, waypoints, patrol points, rally banners, pathfinding
  debug, boxes, overlays, highlights) and `AfterTranslucentBlocks` (colony sign hover) —
  mirroring structurize's `ClientEventSubscriber`. Full-port sweep (script
  `work/scripts/sweep_abstract_listeners.py`): 129 `@SubscribeEvent` methods, **0** abstract-param
  listeners remain; all `addListener` uses are concrete (reload-listener registrations don't apply).
  Also fixed a fallback bug in the batch-14 `LanguageHandler` hardening: locale `null`
  (Minecraft.getInstance() does not exist during mod construction) skipped the en_us fallback —
  restored the original "miss → en_us" behavior (blockui/structurize/minecolonies all load their
  en_us.json during construction now; the two WARN lines disappear).
  Fourth runtime crash fixed (13.09 00:53): RegisterEvent phase — `NPE: "Block id not set"`
  (BlockBehaviour ctor → Properties.effectiveDrops → Objects.requireNonNull(id)). MC 26.1.2
  requires the registry id on `BlockBehaviour.Properties` (`setId(ResourceKey<Block>)`) BEFORE
  the Block constructor runs, and likewise on `Item.Properties` (`setId(ResourceKey<Item>)`;
  the Item ctor reads descriptionId + data-component initializer from it — "Item id not set").
  Vanilla gets this via `Blocks.register(id, props)` and NeoForge's `DeferredRegister` injects
  it in its factory overloads — structurize/multipiston/domum were already on that flow; the
  minecolonies core "direct registration" style (`new XBlock().registerBlock(registry)` during
  RegisterEvent) was not. Java forbids instance-member references in explicit super() args
  (JLS 8.8.7.1 — javac "cannot reference X before supertype constructor has been called"), so
  a central `getRegistryName()` injection is impossible; the fix threads names as literals:
  * `PortIds` helper (api/util) — blockKey/itemKey builders.
  * New `AbstractColonyBlock(String hutName)` / `AbstractBlockHut(String hutName)` ctors build
    the standard Properties WITH the id; every hut leaf passes its getHutName() expression
    (script: 56 classes — literal / in-file constant / ModBuildings IDs).
  * Own-Properties leaves (rack, scarecrow, grave, namedgrave, decorationcontroller, townhall,
    colonysign, plantationfield, composteddirt, banners, tape, barrel, waypoint) append
    `.setId(PortIds.blockKey(<name>))`; crop/farmland/gate inject via their ctor params.
  * BlockItems: central injection in the 8 `registerBlockItem` overrides
    (`properties.setId(PortIds.itemKey(getRegistryName())).useBlockDescriptionPrefix()` — the
    1.21.1 BlockItem derived its descriptionId from the block, lang keys stay
    `block.minecolonies.*`), plus ItemCrop/ItemColonySign/ItemBlockHut/ItemGate/
    ItemColonyFlagBanner sites.
  * Items: `ModItemsInitializer` `props(name)` helper — 145 creation sites rewritten
    (script-resolved from same-line literals / field→name map of the Registry.register calls).
    Bug found by sweep: 4 bread items (Milky/Chorus/Sugary/Golden) IGNORED their ctor
    properties param and built fresh ones — fixed to `super(properties.food(...), tier)`.
  Verified: brace-balance 84 files 0 errors; every props()/block name cross-checked against
  the original jar's en_us.json lang keys (145/145) and blockstates (56/56) — ids are
  EXACTLY the 1.21.1 ones, so loot tables/description ids/world saves stay compatible.
  (The log's follow-on NPEs — `blockHutBaker is null`, `BlockEntityType` Set.of(null),
  "entity has no attributes", GameData rollback — all stem from the block registration
  aborting mid-init; they disappear once the id fix lets construction complete.)
  Fifth runtime crash fixed (13.09 01:37): RegisterEvent ITEM phase —
  `IllegalStateException: Trying to access unbound value '[unregistered]' from registry
  Registry[minecraft:root / minecraft:item]` at `Holder$Reference.key ← Holder$Reference.is ←
  ItemStackTemplate.<init> ← Item$Properties.craftRemainder ← ModItemsInitializer.init:275`.
  Root cause: MC 26.1.2 `Item.Properties.craftRemainder(Item)` EAGERLY builds an
  `ItemStackTemplate` (Item.java:432) whose constructor validates `item.builtInRegistryHolder()`
  (`is(AIR)` → `key()` — throws for constructed-but-UNregistered items; the holder is the
  intrusive `BuiltInRegistries.ITEM.createIntrusiveHolder(this)` reference that only
  `Registry.register` binds). 1.21.1 merely stored the Item reference, so referencing a
  not-yet-registered mod item was legal. ModItemsInitializer constructs ALL items first
  (init lines 90-288) and registers them in one block afterwards — the three large bottles
  passed an unregistered `ModItems.large_empty_bottle` as craftRemainder. Fix: register
  `large_empty_bottle` immediately after its construction (before the three bottles), drop its
  duplicate late registration; the remainder templates now build against a bound holder.
  Sweep of the merged tree: `craftRemainder`/`new ItemStackTemplate` with mod items occur ONLY
  at these 3 sites; `usingConvertsTo` references only vanilla `Items.BOWL` (bound before mod
  RegisterEvents); `.repairable` unused; `spawnEgg(EntityType)` stores only a TypedEntityData
  component (no holder validation), and ENTITY_TYPE registers before ITEM (the log's rollback
  lists all 46 minecolonies entities — they WERE registered). The "Entity ... has no
  attributes" ×46 lines are GameData-rollback noise, not a separate bug.
  Sixth runtime crash fixed (13.09 02:31 — game REACHED MAIN MENU, crash on world creation)
  + post-startup stabilization batch. World creation abort:
  `EventBus exception during AddServerReloadListenersEvent → RecruitmentItemsListener.<clinit>:58
  → ItemStack.<init> → Holder$Reference.components → NPE "Components not bound yet"`.
  Root cause: in 26.1.2 item components are DATA-DRIVEN — they bind to registry holders during
  registry-data (datapack) load, i.e. AFTER AddServerReloadListenersEvent. Any `new ItemStack(...)`
  before that point NPEs (in 1.21.1 components lived on the Item instance from construction).
  Three instances fixed:
  * `RecruitmentItemsListener.RARITY_TO_BOOT_MAP` was a static Map.ofEntries of ItemStacks →
    class-init ran during listener construction → NPE. Now lazily built on first `apply()` use.
  * `ModEquipmentTypes.initRegisterEquipmentTiers` (called `new ItemStack(Items.BOW).getMaxDamage()`
    etc. + one stack per registered item) ran in FMLCommonSetupEvent → caught NPE, skipped all
    durability-based tiers. Moved to `FMLEventHandler.onServerStarted` (ServerStartedEvent fires
    after registry-data load), guarded so failure degrades to missing tiers. `MineColonies.preInit`
    enqueueWork removed. Sweep: no other static-ItemStack initializers run before datapack load
    (WindowShapeTool's static stacks class-load at GUI open — runtime-safe).
  * `ClientStructurePackLoader`/`ServerStructurePackLoader` "Client/Server structure pack
    discovery crashed: ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1" —
    the pack owner was extracted via `modPath.toString().split("/")[1]`. For an exploded
    dev-workspace mod ("mod_resources" pack — the mod file path is a plain directory),
    `StructurePacks.findModResource` returns a HOST absolute path; on Windows that uses
    backslashes, `split("/")` yields ONE element and `[1]` throws — killing the entire
    structure pack discovery (9399 blueprints). Fixed by carrying path→modId pairs and passing
    `mod.getModId()` directly. (The "Failed loading packs from mod path: blueprints/minecraft|neoforge|jei"
    WARNs are benign — mods without a blueprints folder.)
  Model system (all 26.1.2 changes):
  * Racks: `models/block/blockrack*.json` were 1-line `"loader": "domum_ornamentum:materially_textured"`
    wrappers (loader class gone — the port replaced it with the 26.x `RegisterBlockStateModels` /
    `RegisterItemModelsEvent` system, `ModBusEventHandler` was already wired). Blockstate
    `blockminecoloniesrack.json` rewritten to `{"type": "domum_ornamentum:materially_textured",
    "model": "minecolonies:block/rack/<base>"}`; the 7 wrapper models deleted; the 4 `_1`/`_2`
    base models' parents repointed from wrappers to `rack/` bases. NOTE: 26.1.2 weighted variant
    arrays (`BlockStateModel.Unbaked.CODEC` → `WeightedVariants` of plain `Variant`s only) do
    NOT support custom types — the 40/30/30 weighted rack variants were flattened to the
    weight-40 primary model (full racks no longer randomize 3 clutter layouts — cosmetic only).
  * Spear: `models/item/spear.json` used `"loader": "neoforge:separate_transforms"` (loader
    removed in 26.1.2) — 26.1.2's `ModelManager.loadBlockModels` parses EVERY models/**.json, so
    dead files still fail loudly. The item already renders via the ported special-model system
    (`items/spear.json` def → `minecraft:special` → `minecolonies:spear` SpearSpecialRenderer,
    registered in `ClientRegistryHandler.registerSpecialModelRenderers`). Legacy files
    flattened: spear.json → plain generated model, spear_in_hand.json's `"parent":
    "builtin/entity"` removed (builtin/entity is gone in 26.1.2 — was also the source of the
    "Missing block model: minecraft:builtin/entity" warning). spear_throwing.json left as an
    unreferenced plain-parented model.
  * Hut/quarry block models reference ITEM textures (`"item/string"`, `minecolonies:item/scarecrow`,
    ...) — 26.1.2 splits item sprites into the separate items.png atlas and REJECTS block models
    with cross-atlas sprites. Fixed by stitching the 17 referenced vanilla item sprites + the
    mod scarecrow sprite into the blocks atlas via single sources in
    `assets/minecraft/atlases/blocks.json` (mod atlas files MERGE into vanilla's — verified:
    blocks.png still 4096x2048). 4 stale single sources removed (textures moved in 26.1.2:
    map/map_icons split into the map_decorations atlas, entity/chicken variant folders,
    armorstand/wood → armorstand/armorstand, models/armor/leather_layer_1 →
    entity/equipment/humanoid/leather) — none of them had any code/XML consumer.
    Domum block spec models need nothing — they use `block/*` textures only.
  Data format:
  * `loot_table/recipes/enchanter1-5.json`: `minecraft:stored_enchantments` component values
    carried a `"levels"` wrapper (1.21.1 ItemEnchantments). 26.1.2's `ItemEnchantments.CODEC`
    is a flat `unboundedMap(Enchantment.CODEC, intRange(1,255))` — the wrapper made all 5 files
    unparseable ("Not a number / Failed to get element minecraft:levels"). 278 components
    flattened by script. (`itemnbtmatching.json` keeps its format — parsed by the mod's own
    ItemNbtListener, not the vanilla codec. Loot-predicate `"levels": {"min": N}` in
    crops/blocks/short_grass.json is the match_tool enchantment-condition format — still valid.)
  * Tags: `minecraft:chain` → `minecraft:iron_chain` (renamed in 1.21.5+) in
    `tags/block/tier2blocks.json`; `#minecraft:trim_templates` removed from
    `tags/item/stonemason_product_excluded.json` (tag no longer exists in 26.1.2 — ItemTags
    only keeps trimmable_armor/trim_materials; smithing templates aren't stonemason products).
  Class parity vs original jar: 1961/1961 runtime classes ported (verified by path diff;
  64 absent = 57 datagen providers + 9 compat integration classes (journeymap/dynamictrees/tinkers,
  unreferenced) + 2 utilities replaced/moved). Resource parity: 9399/9399 blueprints,
  all assets/data namespaces present; only 1 dynamictrees compat tag absent (deliberate).
  Next: runtime stabilization — construct → common setup → client setup → world → colony.

  Seventh runtime crash fixed (13.09 03:39 — game STARTS, world creation completes, then
  reload-listener + datapack errors). Log: "Failed to parse thing: 'Item minecraft:sand does
  not have components yet'" → `Utils.deserializeCodecMessFromJson → ItemStorage.<init> →
  CustomRecipe.parse → CrafterRecipeListener.apply` (NoSuchElementException killed the whole
  datapack reload during CreateWorldScreen).
  Root cause (decompiled WorldLoader/ReloadableServerResources): item components are bound to
  the registry holders only in `updateComponentsAndStaticRegistryTags()`, which runs AFTER
  `loadResources()` completes — i.e. AFTER ALL reload listeners' apply(). Vanilla recipes avoid
  this by parsing results as `ItemStackTemplate` (plain `Item.CODEC`, no bound-component check)
  and only building ItemStacks at craft time. MineColonies' crafterrecipes/quests/recruitment
  listeners build ItemStacks directly in apply() → the ItemStack codec
  (`Item.CODEC_WITH_BOUND_COMPONENTS`) and the `ItemStack(Holder,...)` ctor
  (`item.components()`) both hard-fail mid-reload.
  Fix (crash fix #7): `BaseContextListener.reload()` now calls
  `BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider).forEach(apply)` BEFORE any
  MineColonies listener apply (once per JVM; provider = the NeoForge-injected registry lookup).
  This is safe and idempotent: `PendingComponents#apply` only assigns each holder's component
  map; vanilla's own later binding pass rewrites the identical data. Covers crafterrecipes,
  quests, recruitment, study items — all listeners extend BaseContextListener.
  Data format migrations in the same batch (~157 recipe JSONs were failing to parse):
  * Recipe ingredients: 1.21.1 `{"item": "X"}` / `{"tag": "X"}` objects are GONE in 26.1.2 —
    the vanilla ingredient codec is now a HolderSet codec that accepts a plain item id string,
    an array of strings, or a `"#tag"` string (custom NeoForge ingredients are dispatched on the
    `neoforge:ingredient_type` key). 154 files migrated (shaped "key" maps incl. arrays like
    blockhutfield "H", shapeless "ingredients", cooking "ingredient"). The 47 "List is too
    short: 0, expected range [1-9]" errors were the same root cause (list elements failing to
    parse → 0 valid entries).
  * `compostables{,_rich,_poor}.json` (minecolonies:composting, single Ingredient "input"):
    the old MIXED array [food, plant, tag] is no longer expressible → converted to
    `{"neoforge:ingredient_type": "neoforge:compound", "children": [...]}` (CompoundIngredient
    = any-of; children accept both `{"neoforge:ingredient_type": "minecolonies:food", ...}`
    custom entries and `"#tag"` strings). The port already registers minecolonies:food/plant/
    counted ingredient types (ModIngredientTypeInitializer).
  * Research costs (315 files, legacy flat `{"item": "X", "count": N}`): fixed in CODE instead —
    `SizedIngredientFlatCodec.decode` now rewrites the legacy single-key object forms
    ({"item":"X"} → "X", {"tag":"X"} → "#X") after count stripping, generically for any ops.
  * Global loot modifiers: `data/neoforge/loot_modifiers/global_loot_modifiers.json` (old
    registry file with "entries" list) DELETED — 26.1.2's LootModifierManager is a
    SimpleJsonResourceReloadListener over `loot_modifiers/` that parses each file directly as
    a modifier (the per-modifier files in data/minecolonies/loot_modifiers/ already parse fine
    — the old registry file was the only error). Supply loot for the camp/ship racks is driven
    by these GLMs (supplycamp/supplyship add_table + generate_supply_loot condition).
  Rack "3 random full models" RESTORED (was flattened in the sixth batch — that flattening
  note above is superseded): the original 1.21.1 blockstate used vanilla weighted variant
  arrays (40/30/30) over three full-rack models per variant (blockrackfull{,_1,_2} and
  blockrackfulldouble{,_1,_2}). Vanilla weighted arrays still only accept plain Variants, so
  the weights now live INSIDE the custom model: `MateriallyTexturedBlockStateModel.Unbaked`
  accepts either `"model": "<id>"` (single, unchanged — all 45 DO blockstates keep parsing) or
  `"models": [{"model": ..., "weight": ...}, ...]` (weighted). Baking the weighted form
  produces `WeightedMateriallyTexturedModel` (DynamicBlockStateModel) which picks one entry per
  position — stable per position like vanilla WeightedVariants (seed save/restore around
  `WeightedList.getRandomOrThrow`, mirroring NeoForge's CompositeBlockModel) and retextures via
  the selected submodel. Geometry key = (selected submodel identity + its own key) so positions
  with different picked variants never share meshing cache entries. Blockstate
  `blockminecoloniesrack.json` rewritten: 20 variants (5 RackType x 4 facings), full/fullsingle
  use the weighted 40/30/30 form, empty (double) → DO type on rack/blockrackemptydouble,
  emptysingle → plain blockrackempty, air → plain blockairrack (original rotations), the stale
  `variant=blockrackemptydouble` entries (the 4 "Unknown value: blockrackemptydouble"
  warnings) removed.

  Eighth fix — BUILD error (13.09 02:27, reported from user's IDE while compiling the seventh
  batch): `MateriallyTexturedBlockStateModel` (the new weighted-rack model) failed to compile with
  "Incompatible equality constraint: Identifier and Weighted<Identifier>" on
  `WEIGHTED_MODELS_CODEC = WeightedList.nonEmptyCodec(WEIGHTED_MODEL_CODEC)`.
  Root cause: in 26.1.2 `WeightedList.nonEmptyCodec(Codec<E>)` takes the ELEMENT codec
  (`Weighted.codec(elementCodec)` wraps entries itself, serialized as `{"data": E, "weight": n}`)
  — it does NOT accept a `Codec<Weighted<E>>`. Passing the `{"model":..., "weight":...}` entry
  codec inferred `E = Weighted<Identifier>` → `Codec<WeightedList<Weighted<Identifier>>>`,
  mismatching the `Codec<WeightedList<Identifier>>` field. Fix: compose the list codec manually
  with the exact vanilla internal recipe (`entryToNonEmptyListCodec`):
  `WEIGHTED_MODEL_CODEC.listOf().xmap(WeightedList::of, WeightedList::unwrap).validate(non-empty)`
  — keeps the original `{"model":..., "weight":...}` entry shape (all 20 rack blockstate variants
  parse unchanged) and the non-empty guarantee. Every `WeightedList`/`Weighted` API used by the
  batch (builder/add/build, unwrap/getFirst, getRandomOrThrow, record accessors) was re-verified
  against the 26.1.2 decompile; all other batch-34 Java changes (BaseContextListener early
  component binding, SizedIngredientFlatCodec legacy rewrite) symbol-verified clean.

  Ninth runtime crash fixed (13.09 16:15 — game COMPILES with fix #8, world creation now crashes
  inside the resource reload; log delivered as upload file, 588 lines):
  `ReportedException: mouseClicked event handler` ← reload future failed ←
  `NullPointerException: Components not bound yet` at `ItemStack.<init>` ←
  `CompostRecipe.<init>` ← `RecipeManager.prepare` (recipe JSON parsing).
  Root causes + fixes:
  * `CompostRecipe` eagerly built `new ItemStack(ModItems.compost, 6)` in its constructor. In
    26.1.2 the vanilla RecipeManager parses ALL recipe JSONs in the PREPARE phase of the resource
    reload — BEFORE item components are bound (verified in the decompile: vanilla PRE-BUILDS the
    pending components before the reload in `ReloadableServerResources.loadResources:90` and only
    APPLIES them afterwards in `updateComponentsAndStaticRegistryTags:113-115` — which is exactly
    why vanilla recipe results are lazy `ItemStackTemplate`s). Fix: `output` is now built lazily
    on first access (`output()` getter; `assemble()` copies, `getResultItem()` returns the cached
    instance) — first access is always post-binding (server tick / JEI / network). Sweep: the only
    other mod recipe type (`ZeroWasteRecipe`) already uses `ItemStackTemplate` (lazy) ✓.
  * The batch-34 early component binding failed harmlessly on EVERY CLIENT reload with
    `IllegalStateException: Missing tag TagKey[minecraft:damage_type / minecraft:is_fire]` —
    vanilla's `fireResistant` component initializer resolves `DamageTypeTags.IS_FIRE`, and the
    client menu lookup has no damage_type (the client only receives datapack registries at world
    join via `RegistryDataCollector`, which also binds the components itself). Fix:
    `BaseContextListener.ensureComponentsBoundEarly` now skips silently when the provider has no
    `damage_type` registry (server reloads keep working — RegistryDataLoader loads damage_type
    BEFORE the resource reload there).
  * Data: 1.21.5+ rename `minecraft:chain` → `minecraft:iron_chain` in 4 chainmail recipes, the
    `taunt` research icon and `itemnbtmatching.json` (the "Couldn't parse data file
    'minecolonies:chainmailchestplate'" error). Proactive item-id sweep of all recipe/research
    item positions against the decompiled 26.1.2 Items/Blocks: 0 other broken ids.
  * Cosmetics: "Unresolved texture references #…→#all" warnings on hut models silenced by adding
    `"all": "#particle"` (models parent `cube_all` — its per-face slots were unresolved; the
    models' own elements use slots #0-#5 and render unchanged). `spear_in_hand`/`spear_throwing`
    warnings left as-is (special-renderer models, same shape as the 1.21.1 originals).
    `blockhutcombatacademy_first/_second` warnings come from STALE files in the user's gradle
    `build/` dir (ModelManager parses every models/**.json in the jar) — user must run a clean
    build once.

  Tenth + Eleventh runtime crashes fixed (13.09 18:45 log batch — two independent crashes from
  the same run; game boots, world loads, login/world-entry now broken):
  * CRASH #11 (login death): `IllegalArgumentException: Can't find id for 'null' in map
    Registry[minecraft:data_component_type]` at `Utils.serializeCodecMess` ←
    `CompatibilityManager.serialize` ← `UpdateClientWithCompatibilityMessage` ←
    `DataPackSyncEventHandler$ServerEvents.sendOnLogin` → "Couldn't place player in world".
    Root cause: `ItemNbtListener` (datapack listener over `data/minecolonies/compatibility/
    itemnbtmatching.json`) does `BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id)` —
    `Registry#getValue` returns **null for unknown ids without throwing**, so renamed/removed
    vanilla components silently became nulls inside `CHECKED_NBT_KEYS`, and serializing them
    to the login packet exploded. Diff of all 31 referenced component ids against the
    decompiled 26.1.2 `DataComponents` registry (81 entries): exactly TWO dead ids —
    `minecraft:fire_resistant` → renamed `minecraft:damage_resistant` (record `DamageResistant`;
    `Item.Properties.fireResistant()` now sets DAMAGE_RESISTANT; 13 netherite-family entries),
    and `minecraft:hide_additional_tooltip` → REMOVED (tooltip rework; banners now carry a real
    `minecraft:banner_patterns` component — 1 white_banner entry). Fixes: (a) data — both ids
    replaced in itemnbtmatching.json (14 refs, deduped white_banner); (b) code —
    `ItemNbtListener.tryParse` now validates item AND component lookups and skips unknown ids
    with a WARN (future renames in user-edited datapacks degrade gracefully instead of
    poisoning the login packet); (c) defensive — `CompatibilityManager.serialize` filters
    null keys/items before writing, so a broken datapack can never kill the login sync.
  * CRASH #10 (class poisoning): `ExceptionInInitializerError` from
    `ColonyConstants.<clinit>` — `IllegalStateException: Registry is already frozen (trying to
    add key minecraft:ticket_type / minecolonies:initial_chunkload)`. In 26.1.2 `TicketType`
    is a registry record; the 1.21.1 code registered it lazily from the constants class, whose
    first initialization happens at first entity creation (RecipeAnalyzer's loot analysis,
    first server tick) — long after registries froze. The EIIE also POISONS ColonyConstants
    for the whole session ("Couldnt analyze animal: citizen/visitor/mercenary/cavalry_horse"
    x4 + cascading log4j appender failures "Could not initialize class ColonyConstants" —
    every later use of ANY colony constant would throw NoClassDefFoundError). Fix: new
    `com.minecolonies.apiimp.initializer.ModTicketTypeInitializer` (DeferredRegister over
    `Registries.TICKET_TYPE`, flags PERSIST|LOADING|SIMULATION|KEEP_DIMENSION_ACTIVE =
    vanilla-forced equivalent), wired into `MineColonies.init` next to the other registers;
    `Colony.java` now uses `ModTicketTypeInitializer.KEEP_LOADED_TYPE.get()` (2 call sites:
    addTicketWithRadius/removeTicketWithRadius); `ColonyConstants` keeps no registry side
    effects (dead static register + TICKET_ID + 4 now-unused imports removed). Sweep: no other
    lazy `Registry.register` outside registration-phase initializers exists in the tree
    (the Abstract*Block self-registrations all run inside RegisterEvent windows — correct).

  Twelfth fix batch (13.09 ~19:00-19:30 log — BREAKTHROUGH: world loads, login works, colony
  systems boot; first in-world issue wave, 4 user reports + logs):
  * CRASH #12 (mobs cannot spawn AT ALL — spawn eggs, raids, citizens; also the 4 remaining
    "Couldnt analyze animal" errors): `NullPointerException: Cannot invoke
    "PathFinder.setCaptureDebug(...)" because "this.pathFinder" is null` from
    `PathNavigation.<init>:65` via `MinecoloniesAdvancedPathNavigate.<init>`. Root cause: the
    1.21.1 port kept `createPathFinder` returning **null** (1.21.1 never touched the vanilla
    field — minecolonies runs its own async Pathfinding system), but 26.1.2's PathNavigation
    constructor ASSIGNS the result and immediately calls `setCaptureDebug` on it → every mob
    constructor dies. Fix: return a real ground-walker PathFinder (WalkNodeEvaluator +
    canPassDoors, mirroring vanilla GroundPathNavigation) — unused by the advanced system,
    but satisfies the constructor contract.
  * Blueprint "missing mods" spam (thousands of warnings during server structure pack load):
    blueprints declare required_mods entries for the former separate mods (domum_ornamentum,
    structurize...) which no longer exist as containers in the merged jar. Fix:
    `BlueprintUtil.MERGED_MOD_IDS` (minecraft + the 6 family ids) checked before ModList in
    both BlueprintUtil and UpdateSchematicsCommand.
  * Hut model dead textures (purple/black checkerboard on archery target dummy, fletcher
    shelf, chickenherder): four vanilla textures are gone/renamed in 26.1.2 —
    `entity/armorstand/wood` → `entity/armorstand/armorstand` (ArmorStandRenderer
    DEFAULT_SKIN_LOCATION), `entity/chicken` → `entity/chicken/chicken_temperate` (chicken
    variant rework), `map/map_icons` → `block/oak_planks` (map_icons sheet removed —
    decorations render from the dedicated MAP_DECORATIONS atlas now; the model refs sampled a
    single filler pixel), `models/armor/leather_layer_1` → `item/leather` (equipment asset
    rework). All replacements applied recursively incl. neoforge:composite children;
    `minecraft:entity/chicken/chicken_temperate` + `minecraft:entity/armorstand/armorstand`
    added as single sources to assets/minecraft/atlases/blocks.json (entity/bell/bell_body,
    entity/chest/normal etc. already resolve via vanilla's own atlas sources). Full recursive
    audit of all 560 referenced textures: clean. NOTE: remaining `*_first/_second` model
    warnings come from STALE files in the user's gradle build/ dir — a one-time CLEAN BUILD
    is still required.
  * Sounds: sounds.json covered 7002 events but ModSoundEvents registers events for ALL
    jobs — marksman + huscarl (2 jobs × 8 voices × 17 event types = 272) were missing →
    thousands of "Missing sound for event" warnings. Filled by cloning the per-
    (voice,event) sound lists from existing entries (7002 → 7274; all referenced ogg files
    verified present).
  * Build-tool preview not showing: pipeline is fully wired (events registered, renderer
    ported) and no exceptions appear — added throttled `[preview-diag]` logging in
    WorldRenderContext.renderBlueprints + `BlueprintPreviewData.describeState()` so the next
    client log pinpoints the failing stage (nothing queued / future never resolves /
    blueprint-ready-but-pos-null). Rack content-item textures: under investigation (need
    screenshot + runtime info from the user).

  Thirteenth fix batch (13.09 ~20:00 log — citizens spawn (Samir P.!), preview renders
  (preview-diag: rendered: 1), mobs spawn from eggs; new wave: client crash on citizen tick,
  faceless raiders, fletcher item texture, blueprint BE error spam):
  * CRASH #13 (client dies ~40s after placing Town Hall — "Ticking entity" while the spawned
    citizen ticks on the RENDER thread): `ClassCastException: ClientLevel cannot be cast to
    ServerLevel` at `AbstractEntityCitizen.detectEquipmentUpdates:670`. Root cause: the
    method is invoked from `aiStep()` which runs on BOTH sides, but it broadcasts the
    equipment packet through `((ServerLevel) level()).getChunkSource().sendToTrackingPlayers`
    — the 1.21.1 vanilla hook was only server-side. Fix: client-side guard at the top of
    `detectEquipmentUpdates()` + the same guard in `EntityCitizen.queueSound(...)` ×2 (also
    network sends with a `(ServerLevel)` cast — defense in depth).
  * FACELESS RAIDERS ("moby nie mają twarzy, jakby tekstura coś nie tak"): 8 renderers
    (barbarian, chief barbarian, 3 pirates, 3 drowned pirates) baked
    `ModelLayers.PLAYER` — a **64x64** layer with the modern skin layout (left arm
    texOffs(32,48), left leg (16,48)) — for **64x32 legacy skins** (barbarian1.png,
    pirate1-8.png, drowned_pirate1-8.png). Model UVs are normalized by the LAYER's texture
    size at bake time but sampled against the ACTUAL texture dimensions at draw time, so
    the face quad (v 8..16, normalized 0.125..0.25) landed on texture rows 4..8 — the face
    pixels were never sampled (heads showed the head-top strip smeared over them, limbs
    sampled wrong quadrants). Fix: new `ModelRaiderLegacy.createBodyLayer()` — humanoid
    mesh with legacy layout (left limbs MIRRORED onto the right UV regions, exactly like
    vanilla's 64x32 skeleton layer, at full 4px limb width) declared 64x32 — registered as
    `ClientRegistryHandler.RAIDER_LEGACY` and baked by all 8 renderers. Custom raiders
    (norsemen 124x64/128x64, amazons 128x128, mummies 64x64, pharao 128x64, mercenary
    128x64) already declare matching layer sizes — verified, untouched.
  * CITIZEN FALLBACK MODEL had the same disease: `CitizenModel` (renderer fallback +
    ModModelTypes.CUSTOM) was baked from `ModelLayers.PLAYER` (64x64) while citizen skins
    are **128x64 canvases** carrying the modern 64x64 layout in their top-left quadrant
    (verified pixel-level: face art at texel 8..16 × 8..16; the per-job models —
    FemaleCitizenModel etc. — declare 128x64 and are correct). Fix: (a)
    `CitizenModel.createMesh()` layer declaration 64x32 → 128x64 (the old value also put
    left limbs at v 1.5..2.0 — outside any texture); (b) `RenderBipedCitizen` +
    `ModModelTypeInitializer` (CUSTOM type) now bake the registered `CITIZEN` layer instead
    of ModelLayers.PLAYER.
  * FLETCHER ITEM TEXTURE (model REJECTED — "Multiple atlases used in model, expected
    blocks.png, but also got items.png"): the Twelfth batch's `models/armor/leather_layer_1
    → item/leather` mapping put an **items-atlas** sprite inside a block model — 26.1.2
    validates atlas purity and rejects the whole model. Fix: mod-owned
    `minecolonies:block/leather` (new 16x16 tanned-leather PNG under
    textures/block/) referenced from blockhutfletcher.json (auto-stitched by vanilla's
    all-namespace directory source for `textures/block`).
  * BLUEPRINT BE/STATE MISMATCH error spam ("Invalid block entity
    domum_ornamentum:materially_retexturable ... got Block{minecraft:barrel}" ×2 +
    "TileEntity creation failed"): NBT-decoded townhall2 (Desert Oasis) — it has 725 DO
    materially_retexturable BEs; 723 sit on real DO blocks and construct fine, exactly 2
    positions ((9,1,6),(9,1,7)) hold vanilla minecraft:barrel states with a leftover DO BE
    id (benign legacy scan artifact; 1.21.1's BlockEntity never validated state). 26.1.2's
    BlockEntity ctor validates and vanilla error-logs with a stacktrace. Fix:
    `BlueprintUtils.constructTileEntity` pre-validates the BE id against
    `BlockEntityType.isValid(blockState)` (and registry presence) before loadStatic and
    skips quietly at debug level. NOTE: the "placeholder blocks / structure block-looking"
    cubes in the build-tool preview are ORIGINAL behavior — structurize's substitution
    blocks (palette entries 1-2) always rendered that way; they become the world's blocks
    on placement.
  * Missing loot table `minecolonies:entities/cavalry_horse` (LootTableAnalyzer debug
    error at datapack load): the file simply never existed (every other raider has one).
    Added loot_table/entities/cavalry_horse.json (leather 0-2, horse-style).

  Fourteenth fix batch (13.09 ~21:20 log — spear model broken in hand+inventory, rack/
  grave/building-inv GUIs show no background, citizens randomly "switch gender",
  build-tool/preview delays, "Failed to parse thing" render-thread spam + JEI recipe
  load crash + more):
  * SPEAR (item renders weird 3D in inventory, held "backwards"): `items/spear.json`
    used a single `minecraft:special` model for ALL display contexts — the 3D special
    renderer also ran in the GUI (base = spear_base, a flat model with NO display
    transforms). Rebuilt after the *vanilla 26.1.2 trident* definition (extracted from
    the client jar, known-good in-game): `minecraft:select` on `display_context` →
    gui/ground/fixed/on_shelf render the flat 2D sprite (spear_base), the fallback is a
    `minecraft:condition` on `using_item` whose `transformation` (scale 1,-1,-1 — the
    new special-model pipeline renders the model Y/Z-flipped compared to the old ISTER
    pipeline, the flip compensates exactly like vanilla) picks `spear_in_hand` /
    `spear_throwing` bases. Both base models now carry the vanilla trident display
    transforms (the port renders the vanilla TRIDENT layer geometry — same as the
    1.21.1 original did — with the minecolonies spear skin; UV layout is
    trident-compatible, verified pixel-wise vs vanilla trident.png). Also defined the
    `particle` texture directly (kills the `#particle-> #spear` unresolved-ref warnings;
    the old 1.21.1 JSONs had `#spear` injected from the removed separate_transforms
    loader wrapper).
  * RACK / GRAVE / BUILDING-INV backgrounds fully invisible: the 9-arg 1.21.1
    `blit(loc, x, y, u, v, w, h, texW, texH)` calls were ported to 11 args
    (pipeline + one extra TEXTURE_SIZE) — which resolved against the NEW
    `blit(pipeline, loc, x, y, u, v, w, h, texW, texH, color)` overload, so
    TEXTURE_SIZE=350 was passed as the **color** (0x0000015E — alpha 0, fully
    transparent!). Fixed WindowRack ×3, WindowGrave ×4, WindowBuildingInventory ×2 —
    dropped the stray trailing arg (10-arg overload). (WindowCitizenInventory/
    WindowCrafting/WindowFurnace/WindowBrewingstand were already 10-arg and fine —
    matching exactly the screens the user reported.)
  * CITIZENS RANDOMLY "SWITCH GENDER / JOB MODEL": 26.x renders in two phases —
    LevelRenderer extracts the render state of EVERY visible entity into a list
    FIRST, then submits them. The ported `RenderBipedCitizen` still selected
    `this.model` per citizen during `extractRenderState` (fine in 1.21.1 where
    render() ran per-entity immediately) — by submit time every citizen used the
    LAST-extracted citizen's model instance (female dress/ponytail model on a male
    texture, per-job models on the wrong citizen — the "weird arm up" = wrong model
    class' working pose logic). Fix: the selected model now travels in
    `CitizenRenderState.model`; `submit()` swaps it onto the renderer for the duration
    of the super call only (restore in finally — submits are sequential on the render
    thread, shared singleton models from SimpleModelType are safe exactly like
    vanilla's one-model-per-renderer).
  * BUILD-TOOL / PREVIEW DELAYS (10 s until a selected blueprint previews; "menu lag"):
    two compounding causes. (1) **Every palette entry / TE / entity of every decoded
    blueprint ran the vanilla data fixer** across ~1000 schema versions (mod blueprints
    are 1.21-era data; 1.21.1 was same-version = no-op). DFU re-folds the rule chain
    per call — a folder load = ~1000 fixer runs = seconds; the building browser sweeps
    ALL 9399 blueprints through the same path. Fix: `DataFixerUtils` memoizes
    single-shot fixer results (bounded 8192-entry LRU keyed by type+range+input-tag;
    palette entries repeat massively across blueprints). (2) `IOPool` was
    (1 core, 2 max, unbounded queue) — ThreadPoolExecutor only grows past core size
    when the queue REJECTS, and a LinkedBlockingDeque never does → permanently
    single-threaded; GUI futures queued behind pack sweeps. Fix: 2 core workers.
    Also removed the batch-39 `[preview-diag]` throttled log (preview verified
    working — it spammed every 5 s).
  * "Failed to parse thing: 'Failed to parse either. First: … Not a string: null …'"
    render-thread bursts (every citizen view sync): `ServerCitizenInteraction
    .deserializeNBT` fed a null tag (`TAG_VALIDATOR_ID` missing for validator-less
    interactions) straight into `deserializeCodecMess`. Fix: null-safe
    `readInteractionComponent` (null → empty component; legacy JSON-string tags parsed
    like `AbstractInteractionResponseHandler#readComponent` does) + the same guard for
    `GlobalResearchFactory`'s `TAG_COSTS`.
  * JEI RECIPE LOAD CRASH ("ArrayIndexOutOfBoundsException: Index 1 out of bounds for
    length 0" in `ImbueRecipe.assemble` ← `RecipeCraftingType.getResultItem` ←
    `RecipeAnalyzer.buildVanillaRecipesMap` ← `JEIPlugin.onRecipesLoaded`): the probe
    called `craftingRecipe.assemble(CraftingInput.EMPTY)` — 26.x's ImbueRecipe actually
    indexes the input (`getItem(1)`) on the 0-slot EMPTY input. The exception aborted
    the whole CustomRecipesReloadedEvent handler, so NONE of the minecolonies job/
    compost/florist/fishing categories ever registered in JEI ("JEI nie jest w pełni
    compat — nie widać recept"). Fix: guarded probe → recipes with no static result
    are skipped; registration completes. Verified all 46 JEI API imports used by the
    port exist in the installed jei-26.1.2-neoforge-29.33.0.87.jar (API compatible —
    the only breakage was the crash).
  * RED_BED BE ISE spam during world placement ("Invalid block entity
    domum_ornamentum:materially_retexturable … got Block{minecraft:red_bed}" from
    BedPlacementHandler → handleBlockPlacement → handleTileEntityPlacement): batch 40
    pre-validated the PREVIEW decode path only; the world-placement path still called
    `BlockEntity.loadStatic` blind. Fix: same pre-validation
    (`BlockEntityType.isValid(worldState)`, registry presence) before loadStatic in
    `PlacementHandlers.handleTileEntityPlacement`.
  * blockhutcombatacademy texture warnings (`#up-> #all …` ×7 per model, repeated per
    resource reload, block AND item variants): its `neoforge:composite` children declare
    `parent: block/cube_all` (whose faces reference `#all`) without defining an `all`
    texture — the only composite hut model in the pack that does. Fix: added
    `"all": "block/oak_planks"` to both children (parent faces aren't rendered — the
    children have their own elements — this only satisfies the resolver).
  * "Failed loading packs from mod path: blueprints/minecraft|neoforge|jei" warnings:
    pseudo-mods in the dev workspace resolve to non-existent blueprint paths — that's
    the normal case. `ClientStructurePackLoader` now skips missing directories
    quietly (only real IO errors still warn).
  * NOTE (not a bug): the building-hut GUI "refreshing" on tab switch — each tab opens
    a fresh module window (page-turn sound, list rebuild; resource lists pull every 20
    ticks, blockui refreshes visible element panes per update) — all 1:1 with 1.21.1
    behavior. The perceived stutter should improve with the spam/delay fixes above.

  * JOURNEYMAP COMPILE ERROR ("Cannot resolve symbol 'v2' in journeymap.api.v2.client",
    14.09 — user sent their runtime jar journeymap-neoforge-26.1.2-6.0.8.jar): the full
    mod jar does NOT contain the v2 API at top level — it jar-in-jars it at
    META-INF/jarjar/journeymap-api-neoforge-2.0.0-26.1.jar (nested jars are invisible
    to javac/IntelliJ), while its own top-level journeymap.api.* packages are the NEW
    internal overlay API without the "v2" segment. Compiling against the full jar
    therefore resolves `journeymap` + `journeymap.api` and then fails exactly on `v2`.
    Fix: extracted the nested API artifact and committed it as
    libs/journeymap-api-neoforge-2.0.0-26.1.jar (flatDir compileOnly — offline, no
    blamejared maven needed for journeymap anymore; maven stays JEI-only), committed
    the full mod as libs/journeymap-neoforge-26.1.2-6.0.8.jar for
    `runWithJourneymap=true` dev runs. Every v2 member used by the 7-file compat
    package re-verified against the extracted artifact via constant-pool walk (fields,
    methods AND generic Signature attributes — e.g. InfoSlot register takes
    Supplier<String> which matches getCurrentColony(); ClientEventRegistry fields are
    Event<MappingEvent> etc.). compat code unchanged.

  * JOURNEYMAP GRADLE RESOLUTION FAIL (follow-up 14.09 wieczór — user dostał
    "Could not find info.journeymap:journeymap-api-neoforge:2.0.0-26.1" + to samo dla
    com.dtteam.dynamictrees:26.1.2-1.8.0-BETA04): poprawka z rana (flatDir libs/) była
    zbyt konserwatywna — odcięła blamejared od grupy info.journeymap, a plik API
    NIE dotarł do libs/ usera (sandbox ≠ jego dysk; lokalny DT jar usera ma zresztą
    inną nazwę: dynamictrees-neoforge-26.1.2-1.8.0.jar bez BETA04 → coordinate się
    nie dopasował). Diagnoza: JEI z blamejared rozwiązywało się u usera poprawnie
    (brak w liście "Could not find") → serwer osiągalny; maven-metadata blamejared
    potwierdza że 2.0.0-26.1 ISTNIEJE (listing grupy info.journeymap), a ściągnięty
    z mavena jar jest sha256-IDENTYCZNY z osadzonym w modzie runtime
    (60b94a89…ae9c). Pierwotny "Cannot resolve symbol 'v2'" usera bierze się z jego
    WŁASNEJ linii `compileOnly(files("libs/journeymap-neoforge-26.1.2-6.0.8.jar"))`
    — pełny mod jar na classpath kompilacji (v2 niewidoczne, jar-in-jar). FIX finalny:
    (1) blamejared znów obsługuje info.journeymap; (2) `compileOnly
    "info.journeymap:journeymap-api-neoforge:2.0.0-26.1"` z mavena (zero ręcznych
    kroków; sha256 == runtime); (3) pełny mod tylko `localRuntime files(...)` (JEI/DT
    analogicznie — wszystkie trzy mody dev-run unconditional, usunięte flagi
    runWith*); (4) flatDir USUNIĘTY (źródło pomyłek nazwa↔coordinate); (5) offline
    fallback: libs/journeymap-api-neoforge-2.0.0-26.1.jar (te same bajty) do podmiany
    na files(...) gdyby maven padł. Błąd "run type 'withDynamicTrees' was not found"
    (miejscami): próba custom runa o nazwie niebędącej znanym typem NeoGradle —
    standardowy run `client` + localRuntime załatwia sprawę, custom runy zbędne.

  * JOURNEYMAP COMPAT SOURCE FIXES (14.09 noc — user's IDE: xmap type error on
    Codec.LONG.xmap(ChunkPos::new, ChunkPos::toLong), "Cannot resolve method 'location'
    in ResourceKey" ×2, "Required int / Provided BlockPos" ×2 (no ChunkPos(BlockPos)/
    ChunkPos(long) ctors), 'x'/'z' private in ChunkPos, plus JM API deprecations).
    Root causes are 26.1.2 mapping changes, verified in decompiled vanilla:
    (a) ChunkPos is now a RECORD — fields private (accessors x()/z()), the long ctor and
    toLong() are GONE; replacements: ChunkPos.unpack(long) static, ChunkPos#pack()
    instance, ChunkPos.containing(BlockPos) static.
    (b) ResourceKey#location() → identifier().
    Fixes: CODEC_SET_CHUNKPOSLONG = Codec.LONG.xmap(ChunkPos::unpack, ChunkPos::pack);
    new ChunkPos(blockPos) → ChunkPos.containing(blockPos) ×2; chunkPos.x/z →
    chunkPos.x()/z() in the getChunk call; dimension.location() → identifier() ×2
    (overlay name + getDataPath).
    DEPRECATION MIGRATION (user: "lepiej już teraz przenieś" — JM 6.0.8 deprecates
    these for removal in 26.2). Correct parser written (dump_depr.py — real @Deprecated
    via legacy attribute/annotations, NOT access flag 0x0020 which is ACC_SUPER and
    gave false positives). Genuinely deprecated symbols we used, all with 1:1
    non-deprecated replacements IN the same v2 API:
    * journeymap.api.v2.client.option.* → journeymap.api.v2.common.option.* (Option,
      OptionCategory, BooleanOption, EnumOption, KeyedEnum — identical ctor/builder
      shapes verified by constant-pool walk);
    * client.JourneyMapPlugin annotation → common.JourneyMapPlugin (apiVersion required;
      dependencies()=[] and require()=true have AnnotationDefaults; PluginHelper in the
      full mod jar scans BOTH annotations and JM's own plugins use the common one);
    * ClientEventRegistry.OPTIONS_REGISTRY_EVENT_EVENT/INFO_SLOT_REGISTRY_EVENT_EVENT
      → the non-suffixed twins OPTIONS_REGISTRY_EVENT/INFO_SLOT_REGISTRY_EVENT;
    * WaypointFactory.createClientWaypoint(...) → createWaypoint(...) (same signature).
    NO v2 replacement exists for journeymap.api.v2.client.display.Context$UI (whole v2
    overlay API references it) → kept, with @SuppressWarnings("deprecation") + comment
    on the two methods that touch it (JourneymapPlugin.onEntityRadarUpdateEvent,
    ColonyBorderMapping.updatePending).
    VERIFIED: brace/paren balance (string/comment-aware lexer) on all 6 compat files ✓;
    no stale references left (grep sweep: location(, new ChunkPos(, toLong,
    createClientWaypoint, client.option, client.JourneyMapPlugin, _EVENT_EVENT —
    comment hits only); every replacement member exists non-deprecated in the
    user's artifact (dump_depr.py + api_dump.txt).

  Each phase must COMPILE + LOAD in the dev client before the next starts.

## Merge Rules (single mod)
1. All `@Mod("structurize")`, `@Mod("blockui")` etc. annotations are REMOVED;
   their registrations move to the single `minecolonies` mod bus (DeferredRegister.register(IEventBus) calls
   rewired to the main mod event bus in one bootstrap class per former-mod).
2. `ModList.get().isLoaded("structurize")` style checks → always-true or removed.
3. Cross-mod API classes keep original FQCNs (`com.ldtteam.structurize.api.*` etc.) so intra-project
   references compile unchanged.
4. Packets/registries: each former mod keeps its ResourceLocation namespace (`structurize:`, `blockui:`...)
   so world saves and datapacks stay compatible.
5. neoforge.mods.toml declares only `minecolonies`.

## Key 26.1.2 API map (verified during Phase 1)
- `ResourceLocation` → `Identifier` (final, private ctor — NO subclassing)
- `GuiGraphics` → `GuiGraphicsExtractor` (private ctor — NO subclassing; extract render state)
- `Screen.render` → `Screen.extractRenderState(GuiGraphicsExtractor, mx, my, partial)`
- `keyPressed(int,int,int)` → `keyPressed(KeyEvent)`; `charTyped(char,int)` → `charTyped(CharacterEvent)`
- `mouseClicked(double,double,int)` → `mouseClicked(MouseButtonEvent, boolean)`;
  `mouseReleased` → `mouseReleased(MouseButtonEvent)`; `mouseScrolled(4×double)` unchanged
- GUI pose: `PoseStack` → `Matrix3x2fStack` (pushMatrix/popMatrix, float-only translate/scale)
- Text: `drawString` → `text`; items: `renderItem` → `item`; tooltips: queued
- Immediate tessellation (Tesselator+BufferUploader+setShader) GONE — queue render states
- `RegisterClientReloadListenersEvent` → `AddClientReloadListenersEvent` (addListener(Identifier, listener))
- Custom atlases: `RegisterTextureAtlasesEvent` (AtlasConfig) on mod bus
- `Screen.hasShiftDown()` statics → `Minecraft.getInstance().hasShiftDown()` instance
- `FMLEnvironment.production/dist` fields → `isProduction()`/`getDist()` methods
- `RenderSystem.setShader*/blend/logicOp/recordRenderCall` GONE
- AT pattern: `[[accessTransformers]]` in mods.toml + `minecraft.accessTransformers.file` in build.gradle

## Sandbox Constraints
- No Java 25 / gradle in the sandbox — the user compiles and runs locally.
- Verdicts come only from user runtime logs.

