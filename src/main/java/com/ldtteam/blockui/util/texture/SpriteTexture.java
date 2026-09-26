package com.ldtteam.blockui.util.texture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ldtteam.blockui.util.resloc.OutOfJarResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TickableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Animated (sprite) texture based on an out-of-jar source, ported to the 26.1.2 GPU
 * texture pipeline.
 *
 * <p>The 1.21.1 version uploaded sprite frames through {@code SpriteContents} +
 * {@code SpriteTicker} into a single GL texture. The 26.1.2 atlas animation system is
 * UBO/RenderPass-bound and only operates inside {@code TextureAtlas}, so this port keeps
 * the sheet on the CPU and re-uploads the current frame region into a frame-sized
 * {@code GpuTexture} whenever {@link #tick()} advances the animation. The visual result
 * (a ticking, frame-advancing texture blitted by identifier) is equivalent for GUI use.
 */
public class SpriteTexture extends ReloadableTexture implements TickableTexture
{
    /** one animation frame: sheet index + duration in ticks */
    private record FrameInfo(int index, int time)
    {
    }

    /** full sprite sheet, owned by this texture (closed on reload/dispose) */
    @Nullable
    private NativeImage sheet = null;
    private final List<FrameInfo> frames = new ArrayList<>();
    private int frameRowSize = 1;
    private int frameWidth = 0;
    private int frameHeight = 0;
    private int currentFrame = 0;
    private int subFrame = 0;

    /**
     * intentionally out-of-jar ctor, for normal locations use vanilla atlases
     */
    public SpriteTexture(final Identifier resourceLocation)
    {
        super(resourceLocation);
    }

    @Override
    public TextureContents loadContents(final ResourceManager resourceManager) throws IOException
    {
        // reset first: when this method throws (missing file) the manager feeds us the
        // missing contents and #apply must take the static path with the previous
        // animation state discarded
        frames.clear();
        currentFrame = 0;
        subFrame = 0;

        final Path nioPath = OutOfJarResourceLocation.getNioPath(resourceId());
        if (nioPath == null || !Files.exists(nioPath))
        {
            throw new FileNotFoundException(resourceId().toString());
        }

        final NativeImage image;
        try (InputStream is = Files.newInputStream(nioPath))
        {
            image = NativeImage.read(is);
        }

        parseAnimation(OutOfJarTexture.readSiblingMcmeta(resourceId()), image);
        return new TextureContents(image, null);
    }

    @Override
    public void apply(final TextureContents contents)
    {
        if (frames.isEmpty())
        {
            // not actually animated (missing/invalid metadata) — plain static upload
            close();
            sheet = null;
            super.apply(contents); // creates a full-size GpuTexture, uploads + closes the image
            return;
        }

        // dispose the previous GPU texture + old sheet, then take ownership of the new
        // image (super#apply would close it after upload, but the sheet must stay alive
        // for ticking)
        super.close();
        if (sheet != null)
        {
            sheet.close();
        }
        sheet = contents.image();

        final GpuDevice device = RenderSystem.getDevice();
        texture = device.createTexture(resourceId()::toString, 5, TextureFormat.RGBA8, frameWidth, frameHeight, 1, 1);
        textureView = device.createTextureView(texture);
        uploadCurrentFrame();
    }

    @Override
    public void tick()
    {
        if (sheet == null || texture == null || frames.isEmpty())
        {
            return;
        }

        subFrame++;
        final FrameInfo frame = frames.get(currentFrame);
        if (subFrame >= frame.time())
        {
            subFrame = 0;
            currentFrame = (currentFrame + 1) % frames.size();
            uploadCurrentFrame();
        }
    }

    @Override
    public void close()
    {
        super.close();
        if (sheet != null)
        {
            sheet.close();
            sheet = null;
        }
        frames.clear();
    }

    private void uploadCurrentFrame()
    {
        if (sheet == null || texture == null || frames.isEmpty())
        {
            return;
        }

        final FrameInfo frame = frames.get(currentFrame);
        final int sourceX = (frame.index() % frameRowSize) * frameWidth;
        final int sourceY = (frame.index() / frameRowSize) * frameHeight;
        RenderSystem.getDevice().createCommandEncoder()
            .writeToTexture(texture, sheet, 0, 0, 0, 0, frameWidth, frameHeight, sourceX, sourceY);
    }

    /**
     * Parses the vanilla {@code animation} mcmeta section into {@link #frames}. Sheet
     * geometry rules follow vanilla: explicit frame width/height when given, otherwise
     * square frames sized by the greatest common divisor of the sheet dimensions.
     */
    private void parseAnimation(@Nullable final JsonObject mcmeta, final NativeImage image)
    {
        if (mcmeta == null || !mcmeta.has("animation"))
        {
            return;
        }

        final JsonObject animation = GsonHelper.getAsJsonObject(mcmeta, "animation", null);
        if (animation == null)
        {
            return;
        }

        final int defaultFrameTime = Math.max(1, GsonHelper.getAsInt(animation, "frametime", 1));

        int width = GsonHelper.getAsInt(animation, "width", 0);
        int height = GsonHelper.getAsInt(animation, "height", 0);
        if (width <= 0 || height <= 0)
        {
            final int gcd = gcd(image.getWidth(), image.getHeight());
            width = gcd;
            height = gcd;
        }

        if (width <= 0 || height <= 0 || image.getWidth() % width != 0 || image.getHeight() % height != 0)
        {
            return; // sheet does not divide into frames — render static
        }

        frameWidth = width;
        frameHeight = height;
        frameRowSize = image.getWidth() / width;
        final int totalFrames = frameRowSize * (image.getHeight() / height);

        final JsonElement framesJson = animation.get("frames");
        if (framesJson != null && framesJson.isJsonArray())
        {
            for (final JsonElement element : framesJson.getAsJsonArray())
            {
                if (element.isJsonObject())
                {
                    final JsonObject frameJson = element.getAsJsonObject();
                    addFrame(GsonHelper.getAsInt(frameJson, "index", 0),
                        GsonHelper.getAsInt(frameJson, "time", defaultFrameTime),
                        totalFrames);
                }
                else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())
                {
                    addFrame(element.getAsInt(), defaultFrameTime, totalFrames);
                }
            }
        }

        if (frames.isEmpty())
        {
            for (int i = 0; i < totalFrames; i++)
            {
                frames.add(new FrameInfo(i, defaultFrameTime));
            }
        }

        if (frames.size() <= 1)
        {
            frames.clear(); // single frame is not an animation
        }
    }

    private void addFrame(final int index, final int time, final int totalFrames)
    {
        if (index >= 0 && index < totalFrames && time > 0)
        {
            frames.add(new FrameInfo(index, time));
        }
    }

    private static int gcd(int a, int b)
    {
        while (b != 0)
        {
            final int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }
}
