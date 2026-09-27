package com.ldtteam.structurize.util;

import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * PORT26-compat (Iris &amp; shader packs): detects whether an Iris shader pack is
 * currently active so the client render paths can degrade to vanilla,
 * shader-pack-supported pipelines.
 *
 * <p>Background: structurize draws its world overlays (placement boxes, colony
 * borders, blueprint previews) through its own {@code RenderPipeline}s
 * ({@code structurize:pipeline/lines_with_width} etc.). Iris only redirects
 * pipelines that exist in the active pack's <em>program override list</em> —
 * for unknown modded pipelines it logs
 * {@code "Missing program ... in override list"} and renders them without the
 * pack's frame integration, which in practice makes the geometry invisible
 * (the pack's composite passes overwrite the main color buffer), and the
 * blueprint preview's manual render pass can outright crash mid-frame.</p>
 *
 * <p>When a pack is active the port falls back to <b>vanilla</b> render types
 * (lines, moving-block), which every shader pack knows how to composite, and
 * defers their flush to {@code RenderLevelStageEvent.AfterLevel} — after the
 * pack's composite — so the overlays survive on screen. Iris stays a soft,
 * run-time-only dependency: everything here is reflection, nothing is
 * compiled against the Iris API and the mod never ships it.</p>
 */
public final class ShaderPackCompat
{
    private static final boolean IRIS_CLASS_PRESENT = resolveInstance() != null;

    @Nullable
    private static final Object IRIS_INSTANCE = resolveInstance();

    @Nullable
    private static volatile Method cachedIsShaderPackInUse;

    @Nullable
    private static volatile Method cachedIsRenderingShadowPass;

    private ShaderPackCompat()
    {
        throw new IllegalStateException("Utility class");
    }

    @Nullable
    private static Object resolveInstance()
    {
        try
        {
            if (!ModList.get().isLoaded("iris"))
            {
                return null;
            }
            final Class<?> impl = Class.forName("net.irisshaders.iris.apiimpl.IrisApiV0Impl");
            final Field instance = impl.getField("INSTANCE");
            return instance.get(null);
        }
        catch (final Throwable t)
        {
            // Iris not installed, older/newer API, or classloading order — treat as "no shaders"
            return null;
        }
    }

    /**
     * Cheap check, safe to call per render call (reflection is resolved once,
     * the target method is a field-read on Iris' side).
     *
     * @return true only when Iris is installed AND a shader pack is currently in use.
     */
    public static boolean isShaderPackActive()
    {
        if (IRIS_INSTANCE == null)
        {
            return false;
        }
        try
        {
            Method method = cachedIsShaderPackInUse;
            if (method == null)
            {
                method = IRIS_INSTANCE.getClass().getMethod("isShaderPackInUse");
                cachedIsShaderPackInUse = method;
            }
            return Boolean.TRUE.equals(method.invoke(IRIS_INSTANCE));
        }
        catch (final Throwable t)
        {
            return false;
        }
    }

    /**
     * Detects whether Iris is currently rendering its shadow map pass.
     *
     * <p>PORT26 (0.4.3): Iris renders every entity again from the sun's point of
     * view to build the shadow map, and flushes the shared
     * {@code MultiBufferSource.BufferSource} at the end of that pass. Any mod
     * geometry that was submitted with a custom pipeline during entity rendering
     * (e.g. minecolonies' citizen status icons) therefore gets drawn inside the
     * shadow pass with the custom pipeline — which Iris does not know
     * ({@code "Missing program minecolonies:pipeline/world_entity_icon in
     * override list"}), with the shadow projection active and into the main
     * color target mid-shadow. World-space UI overlays (name-tag icons etc.)
     * must be suppressed while this returns true.</p>
     *
     * <p>Reflection mirrors {@link #isShaderPackActive()}: soft, run-time-only
     * dependency on {@code IrisApiV0#isRenderingShadowPass()}.</p>
     *
     * @return true only when Iris is installed and currently rendering shadows.
     */
    public static boolean isRenderingShadowPass()
    {
        if (IRIS_INSTANCE == null)
        {
            return false;
        }
        try
        {
            Method method = cachedIsRenderingShadowPass;
            if (method == null)
            {
                method = IRIS_INSTANCE.getClass().getMethod("isRenderingShadowPass");
                cachedIsRenderingShadowPass = method;
            }
            return Boolean.TRUE.equals(method.invoke(IRIS_INSTANCE));
        }
        catch (final Throwable t)
        {
            return false;
        }
    }

    /**
     * Static availability check (no pack state) — used for logging once at startup.
     */
    public static boolean isIrisInstalled()
    {
        return IRIS_CLASS_PRESENT;
    }
}
