package com.ldtteam.blockui.util.texture;

import com.ldtteam.blockui.mod.BlockUI;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

/**
 * Backed by the missing vanilla texture pixels (magenta/black checkerboard).
 *
 * <p>Registered under cursor identifiers whose texture file failed to load, mirroring the
 * 1.21.1 behavior where the texture manager replaced failed textures with the missing
 * texture singleton.
 */
public final class MissingCursorTexture extends CursorTexture
{
    public static final MissingCursorTexture INSTANCE = new MissingCursorTexture();

    private MissingCursorTexture()
    {
        super(Identifier.fromNamespaceAndPath(BlockUI.MOD_ID, "missing_cursor_texture"));
        super.nativeImage = MissingTextureAtlasSprite.generateMissingImage();
    }

    @Override
    public void close()
    {
        // Noop — singleton, the backing image must stay alive
    }

    @Override
    protected void destroyCursorHandle()
    {
        // Noop — singleton, the GLFW cursor handle is kept
    }

    @Override
    public void load(final ResourceManager resourceManager) throws IOException
    {
        // Noop — fallback is always "loaded"
    }
}
