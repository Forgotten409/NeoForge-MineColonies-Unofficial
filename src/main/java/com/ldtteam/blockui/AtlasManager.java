package com.ldtteam.blockui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterTextureAtlasesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Splits global vanilla gui atlas on per mod-id basis. Requires atlas definition in
 * atlases/&lt;mod_id&gt;_gui.json. "directory" sources in the atlas definition must have unique
 * path (ideally contain mod_id) because they join across all mod ids (blame mojang).
 *
 * <p>PORT 26.1.2: the old implementation kept a private {@code CustomGuiSpriteManager}
 * extending {@code TextureAtlasHolder} (an AT-opened reload listener hack) per mod id.
 * In 26.1.2 custom atlases are first-class citizens: {@link RegisterTextureAtlasesEvent}
 * on the mod bus registers {@code net.minecraft.client.resources.model.sprite.AtlasManager.AtlasConfig}s
 * with the vanilla sprite AtlasManager, which handles loading and reloading natively. Mods
 * announce their atlas via {@link #registerModAtlas(String)} from their constructor (before
 * the event fires, which happens during {@link Minecraft} startup).
 */
public class AtlasManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AtlasManager.class);
    public static final AtlasManager INSTANCE = new AtlasManager();

    /** mod ids that want a custom GUI atlas; populated by consumer mods at construction time */
    private static final Set<String> MOD_ATLAS_IDS = ConcurrentHashMap.newKeySet();

    private AtlasManager()
    {}

    /**
     * Announce a custom GUI atlas for the given mod id.
     * Call from a mod constructor (the merged mod does this for each merged namespace).
     *
     * @param modId owning mod id (must also ship {@code assets/<modId>/atlases/<modId>_gui.json})
     */
    public static void registerModAtlas(final String modId)
    {
        MOD_ATLAS_IDS.add(modId);
    }

    /**
     * Mod-bus handler — registers the announced atlases with the vanilla AtlasManager.
     */
    @SubscribeEvent
    public static void onRegisterTextureAtlases(final RegisterTextureAtlasesEvent event)
    {
        for (final String modId : MOD_ATLAS_IDS)
        {
            event.register(new net.minecraft.client.resources.model.sprite.AtlasManager.AtlasConfig(
                Identifier.fromNamespaceAndPath(modId, "textures/atlas/" + modId + "_gui.png"),
                Identifier.fromNamespaceAndPath(modId, modId + "_gui"),
                false,
                Set.of(GuiMetadataSection.TYPE)));
        }
    }

    /**
     * @return sprite for given resLoc, checks in order: custom mod atlases > vanilla
     */
    public TextureAtlasSprite getSprite(final Identifier resLoc)
    {
        final net.minecraft.client.resources.model.sprite.AtlasManager vanillaAtlasManager = Minecraft.getInstance().getAtlasManager();
        if (MOD_ATLAS_IDS.contains(resLoc.getNamespace()))
        {
            final TextureAtlas modAtlas = vanillaAtlasManager.getAtlasOrThrow(atlasIdFor(resLoc.getNamespace()));
            final TextureAtlasSprite sprite = modAtlas.getSprite(resLoc);
            if (sprite.contents().name() != MissingTextureAtlasSprite.getLocation())
            {
                return sprite;
            }
        }
        return vanillaAtlasManager.getAtlasOrThrow(AtlasIds.GUI).getSprite(resLoc);
    }

    /**
     * Atlas definition location (resolved to {@code assets/<modId>/atlases/<modId>_gui.json}).
     * Note: in 26.1.2 {@code AtlasManager#getAtlasOrThrow} is keyed by the definition
     * location, NOT by the texture path.
     */
    private static Identifier atlasIdFor(final String modId)
    {
        return Identifier.fromNamespaceAndPath(modId, modId + "_gui");
    }

    /**
     * Dump texture content and coordinates description per each registered modid.
     *
     * @param dumpingFolder ideally empty target folder, will be created if doesn't exist
     * @see TextureAtlas#dumpContents(Identifier, Path)
     */
    public void dumpAtlases(final Path dumpingFolder)
    {
        final net.minecraft.client.resources.model.sprite.AtlasManager vanillaAtlasManager = Minecraft.getInstance().getAtlasManager();
        MOD_ATLAS_IDS.forEach(modId -> {
            try
            {
                final TextureAtlas atlas = vanillaAtlasManager.getAtlasOrThrow(atlasIdFor(modId));
                atlas.dumpContents(atlasIdFor(modId), Files.createDirectories(dumpingFolder.resolve(modId)));
            }
            catch (final IOException e)
            {
                LOGGER.warn("Failed to dump atlas for mod id: " + modId, e);
            }
        });
    }

    /**
     * @return sprite scaling from given sprite
     */
    public static GuiSpriteScaling getSpriteScaling(final TextureAtlasSprite textureAtlasSprite)
    {
        // PORT26: SpriteContents#metadata() is gone; per-sprite metadata sections are
        // exposed via getAdditionalMetadata(MetadataSectionType) for sections registered
        // in the atlas' additionalMetadata set (we register GuiMetadataSection.TYPE).
        return textureAtlasSprite.contents()
            .getAdditionalMetadata(GuiMetadataSection.TYPE)
            .orElse(GuiMetadataSection.DEFAULT)
            .scaling();
    }
}
