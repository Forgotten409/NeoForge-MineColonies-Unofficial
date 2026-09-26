package com.ldtteam.minecolonies.portassets;

import org.jetbrains.annotations.Nullable;

/**
 * One entry of the external-asset namespace table — PORT26 (publishing support).
 *
 * <p>The published ("legally clean") jar of this port does NOT bundle the upstream
 * MineColonies-family art/data assets (the port author considers them ARR even though the
 * upstream repositories ship GPL-3.0 LICENSE files — see docs/PUBLISHING.md). Instead, the
 * assets of each former sub-mod are provisioned at runtime into
 * {@code <gamedir>/port-assets/<owner>/} from the official distribution channel of the
 * upstream 1.21.1 builds and injected as an always-enabled resource pack + data pack.</p>
 *
 * <p>The bundled-asset probe (dev builds skip provisioning) lives in
 * {@link BundledAssetDetector} — it checks the ART directories of the namespace
 * ({@code textures/ models/ sounds/ blockstates/}), never {@code lang/} (the publish jar
 * legitimately ships the port's novel lang keys).</p>
 *
 * @param modId        the resource namespace this entry provides.
 * @param displayName  human-readable name for logs and the first-run notice screen.
 * @param downloadUrl  primary CurseForge/forgecdn direct URL of the official 1.21.1 jar
 *                     (stable CDN links, no authentication required). Empty for namespaces
 *                     that ship nested inside another jar (jarJar).
 * @param expectedFile file name the primary URL downloads to (also the manual-install file
 *                     name to look for in {@code port-assets/source-jars/}).
 * @param fallbackUrl  secondary source (GitHub source tarball) for manual installs — may be
 *                     empty when the primary jar is the only practical source.
 * @param providedVia  the mod id of the download that CARRIES this namespace's assets
 *                     (jarJar nesting, e.g. {@code blockui} inside the minecolonies jar).
 *                     Empty/null when the namespace downloads its own jar.
 * @param contentRootPrefix directory prefix inside the source jar that carries the pack
 *                     content (e.g. TownTalk keeps everything under {@code "respack/"}).
 *                     Empty/null when the content sits at the jar root (the normal layout).
 * @param notes        short note about where the assets live / special handling.
 */
public record AssetNamespace(
    String modId,
    String displayName,
    String downloadUrl,
    String expectedFile,
    @Nullable String fallbackUrl,
    @Nullable String providedVia,
    @Nullable String contentRootPrefix,
    String notes)
{
    /**
     * Rough download size hints for the first-run notice screen (forgecdn Content-Length
     * of the current namespace table URLs, 2026-09). Rounded — informational only.
     */
    private static final java.util.Map<String, String> SIZE_HINTS = java.util.Map.of(
        "minecolonies", "~78 MB",
        "towntalk", "~52 MB",
        "byzantine", "~16 MB",
        "stylecolonies", "~31 MB");

    /**
     * @return the rough download size of this namespace's source jar for the notice
     *         screen (e.g. "~78 MB"), or an empty string when unknown.
     */
    public String sizeHint()
    {
        return SIZE_HINTS.getOrDefault(modId, "");
    }

    /**
     * @return the content root prefix, or {@code ""} when the content sits at the jar root.
     */
    public String rootPrefix()
    {
        return contentRootPrefix == null ? "" : contentRootPrefix;
    }
}
