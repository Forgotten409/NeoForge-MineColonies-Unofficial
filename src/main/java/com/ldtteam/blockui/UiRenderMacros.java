package com.ldtteam.blockui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.NineSlice;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.Tile;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Our replacement for GuiComponent — PORT 26.1.2.
 *
 * <p>Originally (1.21.1) these helpers did their own tessellation through
 * {@code Tesselator}/{@code BufferBuilder}/{@code BufferUploader.drawWithShader} with the
 * {@code RenderSystem.setShader(...)} immediate-mode pipeline. In 26.1.2 that pipeline is
 * gone — GUI drawing is exclusively queued as render states through
 * {@link GuiGraphicsExtractor}.
 *
 * <p>Port strategy: every helper now takes the {@link BOGuiGraphics} wrapper and delegates
 * to the extract-style primitives ({@code fill}, {@code fillGradient}, {@code blit} with
 * raw UVs). Geometry that cannot be expressed with a single primitive (gradient borders,
 * nine-slice/tiled blits) is decomposed into several primitives.
 *
 * <p>{@code Pane extends UiRenderMacros} (unchanged), so the whole pane tree can call these
 * helpers unprefixed.
 */
public class UiRenderMacros
{
    public static final double HALF_BIAS = 0.5;

    public static void drawLineRectGradient(final BOGuiGraphics target,
        final int x,
        final int y,
        final int w,
        final int h,
        final int argbColorStart,
        final int argbColorEnd)
    {
        drawLineRectGradient(target, x, y, w, h, argbColorStart, argbColorEnd, 1);
    }

    /**
     * Gradient rectangle border, decomposed into four edge gradients.
     * (The 1.21.1 version drew a single TRIANGLE_FAN with per-vertex colors; visually
     * equivalent here with per-edge gradients.)
     */
    public static void drawLineRectGradient(final BOGuiGraphics target,
        final int x,
        final int y,
        final int w,
        final int h,
        final int argbColorStart,
        final int argbColorEnd,
        final int lineWidth)
    {
        drawLineRectGradient(target,
            x,
            y,
            w,
            h,
            (argbColorStart >> 16) & 0xff,
            (argbColorEnd >> 16) & 0xff,
            (argbColorStart >> 8) & 0xff,
            (argbColorEnd >> 8) & 0xff,
            argbColorStart & 0xff,
            argbColorEnd & 0xff,
            (argbColorStart >> 24) & 0xff,
            (argbColorEnd >> 24) & 0xff,
            lineWidth);
    }

    public static void drawLineRectGradient(final BOGuiGraphics target,
        final int x,
        final int y,
        final int w,
        final int h,
        final int redStart,
        final int redEnd,
        final int greenStart,
        final int greenEnd,
        final int blueStart,
        final int blueEnd,
        final int alphaStart,
        final int alphaEnd,
        final int lineWidth)
    {
        if (lineWidth < 1 || (alphaStart == 0 && alphaEnd == 0))
        {
            return;
        }

        final int colorStart = (alphaStart << 24) | (redStart << 16) | (greenStart << 8) | blueStart;
        final int colorEnd = (alphaEnd << 24) | (redEnd << 16) | (greenEnd << 8) | blueEnd;

        // top edge: start -> end
        target.fillGradient(x, y, x + w, y + lineWidth, colorStart, colorEnd);
        // bottom edge: end -> start (mirrored so the gradient stays outside-in)
        target.fillGradient(x, y + h - lineWidth, x + w, y + h, colorEnd, colorStart);
        // left edge: start -> end (vertical)
        target.fillGradient(x, y, x + lineWidth, y + h, colorStart, colorEnd);
        // right edge: end -> start (vertical, mirrored)
        target.fillGradient(x + w - lineWidth, y, x + w, y + h, colorEnd, colorStart);
    }

    public static void drawLineRect(final BOGuiGraphics target, final int x, final int y, final int w, final int h, final int argbColor)
    {
        drawLineRect(target, x, y, w, h, argbColor, 1);
    }

    public static void drawLineRect(final BOGuiGraphics target,
        final int x,
        final int y,
        final int w,
        final int h,
        final int argbColor,
        final int lineWidth)
    {
        drawLineRect(target,
            x,
            y,
            w,
            h,
            (argbColor >> 16) & 0xff,
            (argbColor >> 8) & 0xff,
            argbColor & 0xff,
            (argbColor >> 24) & 0xff,
            lineWidth);
    }

    public static void drawLineRect(final BOGuiGraphics target,
        final int x,
        final int y,
        final int w,
        final int h,
        final int red,
        final int green,
        final int blue,
        final int alpha,
        final int lineWidth)
    {
        if (lineWidth < 1 || alpha == 0)
        {
            return;
        }

        final int color = (alpha << 24) | (red << 16) | (green << 8) | blue;

        target.fill(x, y, x + w, y + lineWidth, color);
        target.fill(x, y + h - lineWidth, x + w, y + h, color);
        target.fill(x, y + lineWidth, x + lineWidth, y + h - lineWidth, color);
        target.fill(x + w - lineWidth, y + lineWidth, x + w, y + h - lineWidth, color);
    }

    public static void fill(final BOGuiGraphics target, final int x, final int y, final int w, final int h, final int argbColor)
    {
        fill(target, x, y, w, h, (argbColor >> 16) & 0xff, (argbColor >> 8) & 0xff, argbColor & 0xff, (argbColor >> 24) & 0xff);
    }

    public static void fill(final BOGuiGraphics target,
        final int x,
        final int y,
        final int w,
        final int h,
        final int red,
        final int green,
        final int blue,
        final int alpha)
    {
        if (alpha == 0)
        {
            return;
        }

        final int color = (alpha << 24) | (red << 16) | (green << 8) | blue;
        target.fill(x, y, x + w, y + h, color);
    }

    public static void fillGradient(final BOGuiGraphics target,
        final int x,
        final int y,
        final int w,
        final int h,
        final int argbColorStart,
        final int argbColorEnd)
    {
        fillGradient(target,
            x,
            y,
            w,
            h,
            (argbColorStart >> 16) & 0xff,
            (argbColorEnd >> 16) & 0xff,
            (argbColorStart >> 8) & 0xff,
            (argbColorEnd >> 8) & 0xff,
            argbColorStart & 0xff,
            argbColorEnd & 0xff,
            (argbColorStart >> 24) & 0xff,
            (argbColorEnd >> 24) & 0xff);
    }

    public static void fillGradient(final BOGuiGraphics target,
        final int x,
        final int y,
        final int w,
        final int h,
        final int redStart,
        final int redEnd,
        final int greenStart,
        final int greenEnd,
        final int blueStart,
        final int blueEnd,
        final int alphaStart,
        final int alphaEnd)
    {
        if (alphaStart == 0 && alphaEnd == 0)
        {
            return;
        }

        final int colorStart = (alphaStart << 24) | (redStart << 16) | (greenStart << 8) | blueStart;
        final int colorEnd = (alphaEnd << 24) | (redEnd << 16) | (greenEnd << 8) | blueEnd;
        target.fillGradient(x, y, x + w, y + h, colorStart, colorEnd);
    }

    public static void hLine(final BOGuiGraphics target, final int x, final int xEnd, final int y, final int argbColor)
    {
        line(target, x, y, xEnd, y, (argbColor >> 16) & 0xff, (argbColor >> 8) & 0xff, argbColor & 0xff, (argbColor >> 24) & 0xff);
    }

    public static void hLine(final BOGuiGraphics target,
        final int x,
        final int xEnd,
        final int y,
        final int red,
        final int green,
        final int blue,
        final int alpha)
    {
        line(target, x, y, xEnd, y, red, green, blue, alpha);
    }

    public static void vLine(final BOGuiGraphics target, final int x, final int y, final int yEnd, final int argbColor)
    {
        line(target, x, y, x, yEnd, (argbColor >> 16) & 0xff, (argbColor >> 8) & 0xff, argbColor & 0xff, (argbColor >> 24) & 0xff);
    }

    public static void vLine(final BOGuiGraphics target,
        final int x,
        final int y,
        final int yEnd,
        final int red,
        final int green,
        final int blue,
        final int alpha)
    {
        line(target, x, y, x, yEnd, red, green, blue, alpha);
    }

    public static void line(final BOGuiGraphics target, final int x, final int y, final int xEnd, final int yEnd, final int argbColor)
    {
        line(target, x, y, xEnd, yEnd, (argbColor >> 16) & 0xff, (argbColor >> 8) & 0xff, argbColor & 0xff, (argbColor >> 24) & 0xff);
    }

    /**
     * Axis-aligned lines are drawn as thin fills. Arbitrary-angle lines (the old
     * DEBUG_LINES path) have no direct equivalent in the GUI pipeline — all current
     * callers use axis-aligned coordinates only.
     */
    public static void line(final BOGuiGraphics target,
        final int x,
        final int y,
        final int xEnd,
        final int yEnd,
        final int red,
        final int green,
        final int blue,
        final int alpha)
    {
        if (alpha == 0)
        {
            return;
        }

        final int color = (alpha << 24) | (red << 16) | (green << 8) | blue;
        target.fill(Math.min(x, xEnd), Math.min(y, yEnd), Math.max(x, xEnd) + 1, Math.max(y, yEnd) + 1, color);
    }

    public static void blit(final BOGuiGraphics target,
        final Identifier rl,
        final int x,
        final int y,
        final int w,
        final int h,
        final int u,
        final int v,
        final int mapW,
        final int mapH)
    {
        blit(target, rl, x, y, w, h, (float) u / mapW, (float) v / mapH, (float) (u + w) / mapW, (float) (v + h) / mapH);
    }

    public static void blit(final BOGuiGraphics target,
        final Identifier rl,
        final int x,
        final int y,
        final int w,
        final int h,
        final int u,
        final int v,
        final int uW,
        final int vH,
        final int mapW,
        final int mapH)
    {
        blit(target, rl, x, y, w, h, (float) u / mapW, (float) v / mapH, (float) (u + uW) / mapW, (float) (v + vH) / mapH);
    }

    public static void blitSprite(final BOGuiGraphics target,
        final TextureAtlasSprite sprite,
        final GuiSpriteScaling guiScaling,
        final int x,
        final int y,
        final int w,
        final int h)
    {
        final Identifier atlasLocation = sprite.atlasLocation();
        final float u0 = sprite.getU0();
        final float v0 = sprite.getV0();
        final float u1 = sprite.getU1();
        final float v1 = sprite.getV1();
        if (guiScaling.type() == Type.STRETCH)
        {
            blit(target, atlasLocation, x, y, w, h, u0, v0, u1, v1);
        }
        else if (guiScaling instanceof final NineSlice nineSlice)
        {
            final int rbW = nineSlice.width();
            final int rbH = nineSlice.height();

            if (rbW == w && rbH == h)
            {
                blit(target, atlasLocation, x, y, w, h, u0, v0, u1, v1);
            }
            else
            {
                final int uR = nineSlice.border().left();
                final int vR = nineSlice.border().top();
                final int rW = rbW - uR - nineSlice.border().right();
                final int rH = rbH - vR - nineSlice.border().bottom();
                blitRepeatable(target, atlasLocation, x, y, w, h, u0, v0, u1, v1, uR, vR, rW, rH, rbW, rbH);
            }
        }
        else if (guiScaling instanceof final Tile tile)
        {
            final int tW = tile.width();
            final int tH = tile.height();

            if (tW == w && tH == h)
            {
                blit(target, atlasLocation, x, y, w, h, u0, v0, u1, v1);
            }
            else
            {
                blitRepeatable(target, atlasLocation, x, y, w, h, u0, v0, u1, v1, 0, 0, tW, tH, tW, tH);
            }
        }
    }

    public static void blitSprite(final BOGuiGraphics target,
        final TextureAtlasSprite sprite,
        final int x,
        final int y,
        final int w,
        final int h)
    {
        blit(target, sprite.atlasLocation(), x, y, w, h, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
    }

    public static void blit(final BOGuiGraphics target, final Identifier rl, final int x, final int y, final int w, final int h)
    {
        blit(target, rl, x, y, w, h, 0.0f, 0.0f, 1.0f, 1.0f);
    }

    public static void blit(final BOGuiGraphics target,
        final Identifier rl,
        final int x,
        final int y,
        final int w,
        final int h,
        final float uMin,
        final float vMin,
        final float uMax,
        final float vMax)
    {
        target.blit(rl, x, y, x + w, y + h, uMin, uMax, vMin, vMax);
    }

    /**
     * Draws texture without scaling so one texel is one pixel, using repeatable texture center.
     * Same tiling algorithm as 1.21.1, but each tile is a separate raw-UV blit.
     */
    protected static void blitRepeatable(final BOGuiGraphics target,
        final Identifier rl,
        final int x,
        final int y,
        final int width,
        final int height,
        final float uMin,
        final float vMin,
        final float uMax,
        final float vMax,
        final int uRepeat,
        final int vRepeat,
        final int repeatWidth,
        final int repeatHeight,
        final int repeatBoxWidth,
        final int repeatBoxHeight)
    {
        if (uRepeat < 0 || vRepeat < 0 ||
            uRepeat >= repeatBoxWidth ||
            vRepeat >= repeatBoxHeight ||
            repeatWidth < 1 ||
            repeatHeight < 1 ||
            repeatWidth > repeatBoxWidth - uRepeat ||
            repeatHeight > repeatBoxHeight - vRepeat)
        {
            throw new IllegalArgumentException("Repeatable box is outside of texture box");
        }

        final int repeatCountX = Math.max(1, Math.max(0, width - (repeatBoxWidth - repeatWidth)) / repeatWidth);
        final int repeatCountY = Math.max(1, Math.max(0, height - (repeatBoxHeight - repeatHeight)) / repeatHeight);
        final float uTexelWidth = (uMax - uMin) / repeatBoxWidth;
        final float vTexelHeight = (vMax - vMin) / repeatBoxHeight;

        // main

        for (int i = 0; i < repeatCountX; i++)
        {
            final int uAdjust = i == 0 ? 0 : uRepeat;
            final int xStart = x + uAdjust + i * repeatWidth;
            final int w = Math.min(repeatWidth + uRepeat - uAdjust, width - (repeatBoxWidth - uRepeat - repeatWidth));
            final float minU = uMin + uTexelWidth * uAdjust;
            final float maxU = minU + uTexelWidth * w;

            for (int j = 0; j < repeatCountY; j++)
            {
                final int vAdjust = j == 0 ? 0 : vRepeat;
                final int yStart = y + vAdjust + j * repeatHeight;
                final int h = Math.min(repeatHeight + vRepeat - vAdjust, height - (repeatBoxHeight - vRepeat - repeatHeight));
                final float minV = vMin + vTexelHeight * vAdjust;
                final float maxV = minV + vTexelHeight * h;

                blit(target, rl, xStart, yStart, w, h, minU, minV, maxU, maxV);
            }
        }

        final int xEnd = x + Math.min(uRepeat + repeatCountX * repeatWidth, width - (repeatBoxWidth - uRepeat - repeatWidth));
        final int yEnd = y + Math.min(vRepeat + repeatCountY * repeatHeight, height - (repeatBoxHeight - vRepeat - repeatHeight));
        final int uLeft = width - (xEnd - x);
        final int vBot = height - (yEnd - y);
        final float restMinU = uMax - uLeft * uTexelWidth;
        final float restMinV = vMax - vBot * vTexelHeight;

        // bot border
        for (int i = 0; i < repeatCountX; i++)
        {
            final int uAdjust = i == 0 ? 0 : uRepeat;
            final int xStart = x + uAdjust + i * repeatWidth;
            final int w = Math.min(repeatWidth + uRepeat - uAdjust, width - uLeft);
            final float minU = uMin + uTexelWidth * uAdjust;
            final float maxU = minU + uTexelWidth * w;

            blit(target, rl, xStart, yEnd, w, vBot, minU, restMinV, maxU, vMax);
        }

        // left border
        for (int j = 0; j < repeatCountY; j++)
        {
            final int vAdjust = j == 0 ? 0 : vRepeat;
            final int yStart = y + vAdjust + j * repeatHeight;
            final int h = Math.min(repeatHeight + vRepeat - vAdjust, height - vBot);
            final float minV = vMin + vTexelHeight * vAdjust;
            final float maxV = minV + vTexelHeight * h;

            blit(target, rl, xEnd, yStart, uLeft, h, restMinU, minV, uMax, maxV);
        }

        // bot left corner
        blit(target, rl, xEnd, yEnd, uLeft, vBot, restMinU, restMinV, uMax, vMax);
    }

    /**
     * Render an entity on a GUI.
     *
     * <p>PORT26 (JEI entity fix — "no entity visible in hut recipe views"): rewritten 1:1
     * after vanilla {@code InventoryScreen#extractEntityInInventoryFollowsMouse}, the
     * known-good 26.1.2 entity-in-GUI path. The previous port attempt rendered nothing
     * visible for three reasons: (a) it passed {@code scale=1.0F} to the picture-in-picture
     * renderer while sizing the display rect from the scale value — the entity was
     * microscopic inside a rect hundreds of pixels wide; (b) the LivingEntityRenderState
     * rotations were set in RADIANS (the fields are DEGREES — see
     * {@code LivingEntityRenderer#setupRotations} using {@code rotationDegrees(180 - bodyRot)}),
     * and (c) the flip used {@code rotateY(π)} instead of vanilla's {@code rotateZ(π)}.</p>
     *
     * <p>Vanilla semantics kept: {@code scale} is pixels-per-block (vanilla inventory uses
     * 30 for a 70px-tall player area; the JEI citizen view uses {@code CITIZEN_H / 2.4f}
     * ≈ 29.6 — same meaning). The display rect is derived from the 1.21.1 call contract:
     * (x, y) anchors the entity's bottom-center (FEET on the anchor — Task 47-c fix), the
     * rect spans ~2.4 blocks tall, ~1.2 blocks wide around it. Rotation fields:
     * {@code bodyRot = 180 + yaw} (degrees, faces the camera), {@code yRot} is the
     * head-minus-body offset in degrees, {@code xRot = -pitch} degrees; bounding box is
     * normalized by the state scale and the state scale reset to 1. The PIP translation is
     * computed from the rect so the feet land exactly on the anchor y (matching the 1.21.1
     * positioning, which did not depend on the entity's bounding-box height).</p>
     *
     * @param target   render target
     * @param x        horizontal center position
     * @param y        vertical bottom position
     * @param scale    pixels per block (vanilla inventory size semantics)
     * @param headYaw  adjusts look rotation (degrees)
     * @param yaw      adjusts body rotation (degrees)
     * @param pitch    adjusts look rotation (degrees)
     * @param entity   the entity to render
     */
    public static void drawEntity(final BOGuiGraphics target,
        final int x,
        final int y,
        final double scale,
        final float headYaw,
        final float yaw,
        final float pitch,
        final Entity entity)
    {
        final LivingEntity livingEntity = (entity instanceof LivingEntity) ? (LivingEntity) entity : null;
        if (entity.level() == null) return;

        // temporarily rotate the entity, extract the render state, restore
        final float oldYaw = entity.getYRot();
        final float oldPitch = entity.getXRot();
        final float oldYawOffset = livingEntity == null ? 0F : livingEntity.yBodyRot;
        final float oldPrevYawHead = livingEntity == null ? 0F : livingEntity.yHeadRotO;
        final float oldYawHead = livingEntity == null ? 0F : livingEntity.yHeadRot;

        entity.setYRot(180.0F + headYaw);
        entity.setXRot(-pitch);
        if (livingEntity != null)
        {
            livingEntity.yBodyRot = 180.0F + yaw;
            livingEntity.yHeadRot = entity.getYRot();
            livingEntity.yHeadRotO = entity.getYRot();
        }

        final EntityRenderState state = extractState(entity);

        entity.setYRot(oldYaw);
        entity.setXRot(oldPitch);
        if (livingEntity != null)
        {
            livingEntity.yBodyRot = oldYawOffset;
            livingEntity.yHeadRotO = oldPrevYawHead;
            livingEntity.yHeadRot = oldYawHead;
        }

        float translationY = 0.0F;
        if (state instanceof final net.minecraft.client.renderer.entity.state.LivingEntityRenderState livingState)
        {
            // rotation fields are DEGREES (LivingEntityRenderer#setupRotations consumes
            // rotationDegrees(180 - bodyRot)); mirror the vanilla inventory values.
            livingState.bodyRot = 180.0F + yaw;
            livingState.yRot = headYaw; // head offset relative to body, degrees
            if (livingState.pose != net.minecraft.world.entity.Pose.FALL_FLYING)
            {
                livingState.xRot = -pitch;
            }

            // vanilla inventory normalization: divide the box by the entity scale and
            // reset it, so the PIP pose (which multiplies by the caller scale) sizes the
            // model correctly.
            livingState.boundingBoxWidth = livingState.boundingBoxWidth / livingState.scale;
            livingState.boundingBoxHeight = livingState.boundingBoxHeight / livingState.scale;
            livingState.scale = 1.0F;
            // NOTE (Task 47-c): the vanilla inventory translation (boundingBoxHeight / 2) is
            // NOT used here -- vanilla centers the entity's bounding box inside its display
            // rect, while our anchor contract is "feet at (x, y)" (see below).
        }

        final float size = (float) scale;
        // display rect from the 1.21.1 anchor contract: bottom-center (x, y),
        // ~1.2 blocks wide, ~2.4 blocks tall (incl. headroom)
        final int halfW = Math.max(1, Math.round(size * 0.6f));
        final int h = Math.max(1, Math.round(size * 2.4f));

        // Task 47-c FIX ("NPC previews in JEI sit slightly too high / stick out of the
        // rectangle"): the picture-in-picture renderer places the entity origin (its FEET)
        // at rect_top + h/2 + translation.y * size (screen px, Y+ down; from
        // PictureInPictureRenderer#prepare translate(W/2, H/2) + GuiEntityRenderer
        // #getTranslateY = height/2, then translate(translation) -- verified against
        // vanilla InventoryScreen: rect 49x70, size 30, translation bbHeight/2 + 0.0625
        // renders the player head ~4px below the rect top and feet ~6px above the rect
        // bottom, the classic look). The 1.21.1 drawEntity put the feet EXACTLY on the
        // (x, y) anchor; our rect bottom is that anchor, so the translation must be
        // h / (2 * size) -- using the vanilla bbHeight/2 translation instead floated every
        // entity (1.2 - bbHeight/2) * size px too high (citizens ~9px, cows ~13px,
        // chickens ~22px at the JEI scales), pushing heads out over the top of the
        // JEI slot/recipe background rectangles.
        translationY = h / (2.0F * size);

        // vanilla: rotateZ(π) flips the model upright for the PIP pose.
        // NOTE: GuiGraphicsExtractor#entity takes SCREEN-space rect coordinates (both
        // vanilla callers — InventoryScreen/SmithingScreen — pass absolute leftPos/topPos
        // offsets and never rely on the pose). BlockUI panes and JEI recipe categories
        // call this with a TRANSLATED pose and LOCAL coordinates, so the rect corners are
        // transformed through the current 2D pose first — without this the entity renders
        // at the top-left of the screen instead of inside the pane/recipe area.
        final org.joml.Matrix3x2f pose = target.pose();
        final org.joml.Vector2f tl = pose.transformPosition(new org.joml.Vector2f(x - halfW, y - h));
        final org.joml.Vector2f br = pose.transformPosition(new org.joml.Vector2f(x + halfW, y));
        final Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        target.gui().entity(state,
            size,
            new Vector3f(0.0F, translationY, 0.0F),
            rotation,
            null,
            Math.round(tl.x),
            Math.round(tl.y),
            Math.round(br.x),
            Math.round(br.y));
    }

    /**
     * PORT26: JEI-style convenience overload — the JEI 26.1 recipe categories receive the
     * vanilla {@link GuiGraphicsExtractor} directly (instead of our BlockUI wrapper), so
     * they can call this without constructing a {@link BOGuiGraphics} themselves.
     */
    public static void drawEntity(final GuiGraphicsExtractor gui,
        final int x,
        final int y,
        final double scale,
        final float headYaw,
        final float yaw,
        final float pitch,
        final Entity entity)
    {
        drawEntity(new BOGuiGraphics(Minecraft.getInstance(), gui), x, y, scale, headYaw, yaw, pitch, entity);
    }

    /**
     * Generic state extraction — {@code getRenderer(T)} returns
     * {@code EntityRenderer<? super T, ?>}, whose state type is a wildcard, so the
     * create/extract pair goes through an unchecked cast (runtime-safe: the state comes
     * from the very same renderer it is passed back to).
     */
    @SuppressWarnings("unchecked")
    private static EntityRenderState extractState(final Entity entity)
    {
        final EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        final EntityRenderer<Entity, EntityRenderState> renderer =
            (EntityRenderer<Entity, EntityRenderState>) (EntityRenderer<?, ?>) dispatcher.getRenderer(entity);
        final EntityRenderState state = renderer.createRenderState();
        renderer.extractRenderState(entity, state, 1.0F);
        return state;
    }

    /**
     * @return rendering lambda detached from sprite and guiScaling instances
     * @implNote same logic as {@link #blitSprite(BOGuiGraphics, TextureAtlasSprite, GuiSpriteScaling, int, int, int, int)}
     */
    public static ResolvedBlit resolveSprite(final TextureAtlasSprite sprite, final GuiSpriteScaling guiScaling)
    {
        final Identifier atlasLocation = sprite.atlasLocation();
        final float u0 = sprite.getU0();
        final float v0 = sprite.getV0();
        final float u1 = sprite.getU1();
        final float v1 = sprite.getV1();
        if (guiScaling.type() == Type.STRETCH)
        {
            return (target, x, y, w, h) -> blit(target, atlasLocation, x, y, w, h, u0, v0, u1, v1);
        }
        else if (guiScaling instanceof final NineSlice nineSlice)
        {
            final int rbW = nineSlice.width();
            final int rbH = nineSlice.height();
            final int uR = nineSlice.border().left();
            final int vR = nineSlice.border().top();
            final int rW = rbW - uR - nineSlice.border().right();
            final int rH = rbH - vR - nineSlice.border().bottom();

            return (target, x, y, w, h) -> {
                if (rbW == w && rbH == h)
                {
                    blit(target, atlasLocation, x, y, w, h, u0, v0, u1, v1);
                }
                else
                {
                    blitRepeatable(target, atlasLocation, x, y, w, h, u0, v0, u1, v1, uR, vR, rW, rH, rbW, rbH);
                }
            };
        }
        else if (guiScaling instanceof final Tile tile)
        {
            final int tW = tile.width();
            final int tH = tile.height();

            return (target, x, y, w, h) -> {
                if (tW == w && tH == h)
                {
                    blit(target, atlasLocation, x, y, w, h, u0, v0, u1, v1);
                }
                else
                {
                    blitRepeatable(target, atlasLocation, x, y, w, h, u0, v0, u1, v1, 0, 0, tW, tH, tW, tH);
                }
            };
        }
        if (!net.neoforged.fml.loading.FMLEnvironment.isProduction())
        {
            throw new UnsupportedOperationException("Missing resolver for gui scaling: " + guiScaling.type());
        }
        return ResolvedBlit.EMPTY;
    }

    /**
     * Used for precompiling math around rendering
     */
    @FunctionalInterface
    public static interface ResolvedBlit
    {
        public static final ResolvedBlit EMPTY = (target, x, y, w, h) -> {};

        void blit(BOGuiGraphics target, int x, int y, int w, int h);
    }
}
