package com.ldtteam.minecolonies.portassets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ldtteam.minecolonies.MineColonies;
import org.jetbrains.annotations.Nullable;

/**
 * Loader settings persisted at {@code <gamedir>/port-assets/settings.json} — PORT26
 * (publishing support).
 *
 * <p>Plain Gson POJO (the port already uses Gson for all JSON IO — e.g.
 * {@code com.ldtteam.common.language.LanguageHandler}). Fields:</p>
 * <ul>
 *   <li>{@code mode} — {@code "auto"} (default; offer to download on first launch),
 *       {@code "manual"} (never download, but still load an existing store and show the
 *       manual-install instructions), {@code "off"} (never show anything, only load an
 *       existing store).</li>
 *   <li>{@code skipNotice} — set when the player clicks "Skip" on the notice screen; the
 *       notice will not be offered again until the file is edited or the store repaired.</li>
 *   <li>{@code serverAutoDownload} — <b>dedicated-server opt-in</b> (default {@code false}):
 *       when {@code true}, a dedicated server provisions missing namespaces itself during
 *       mod construction (before the pack repositories are built), so the data packs and
 *       blueprints are available on the very first boot with no manual folder copying.
 *       The boot blocks for the download with progress lines in the log — that is exactly
 *       what server owners opt into. The client never reads this flag (the notice screen
 *       flow owns the client side).</li>
 * </ul>
 */
public final class PortAssetSettings
{
    /**
     * Provisioning mode: automatic download offered on first launch (default).
     */
    public static final String MODE_AUTO = "auto";

    /**
     * Provisioning mode: no downloads, manual install only.
     */
    public static final String MODE_MANUAL = "manual";

    /**
     * Provisioning mode: fully silent; an existing store is still injected.
     */
    public static final String MODE_OFF = "off";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * The provisioning mode; one of {@link #MODE_AUTO}, {@link #MODE_MANUAL}, {@link #MODE_OFF}.
     */
    public String mode = MODE_AUTO;

    /**
     * Whether the player permanently skipped the first-run notice.
     */
    public boolean skipNotice = false;

    /**
     * One-shot repair switch: set to {@code true} by hand to wipe the external store on the
     * next launch; the loader clears it again immediately and (in auto mode) re-downloads.
     */
    public boolean reprovision = false;

    /**
     * Dedicated-server opt-in: provision missing namespaces during mod construction
     * (blocking, with log progress). Default false — servers stay manual by default.
     */
    public boolean serverAutoDownload = false;

    /**
     * Load the settings file, falling back to defaults (and writing them out) when missing
     * or broken. Never throws — a broken settings file degrades to defaults.
     *
     * @return the loaded settings (never null).
     */
    public static PortAssetSettings load()
    {
        final Path file = PortAssetPaths.root().resolve(PortAssetPaths.SETTINGS_FILE);
        if (Files.isRegularFile(file))
        {
            try
            {
                final PortAssetSettings loaded = GSON.fromJson(
                    Files.readString(file, StandardCharsets.UTF_8), PortAssetSettings.class);
                if (loaded != null)
                {
                    if (loaded.mode == null
                          || (!loaded.mode.equals(MODE_AUTO) && !loaded.mode.equals(MODE_MANUAL) && !loaded.mode.equals(MODE_OFF)))
                    {
                        loaded.mode = MODE_AUTO;
                    }
                    return loaded;
                }
            }
            catch (final IOException | com.google.gson.JsonParseException e)
            {
                MineColonies.LOGGER.warn("port-assets: could not read {} ({}); using defaults",
                    file, e.toString());
            }
        }
        return new PortAssetSettings();
    }

    /**
     * Persist the settings; creates parent folders. Never throws — a failure is logged.
     */
    public void save()
    {
        final Path file = PortAssetPaths.root().resolve(PortAssetPaths.SETTINGS_FILE);
        try
        {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        }
        catch (final IOException e)
        {
            MineColonies.LOGGER.warn("port-assets: could not write {}: {}", file, e.toString());
        }
    }

    /**
     * @return true when the automatic download flow is allowed.
     */
    public boolean isAuto()
    {
        return MODE_AUTO.equals(mode);
    }

    /**
     * @return true when the loader should be fully silent (still injects existing store).
     */
    public boolean isOff()
    {
        return MODE_OFF.equals(mode);
    }

    /**
     * @return true when the first-run notice screen may be shown at all.
     */
    public boolean noticeAllowed()
    {
        return !isOff() && !skipNotice;
    }

    /**
     * Parses a raw mode string, used when editing settings.json by hand.
     *
     * @param raw the raw value, may be null.
     * @return the normalized mode, or {@link #MODE_AUTO} when unknown.
     */
    public static String normalizeMode(@Nullable final String raw)
    {
        return MODE_MANUAL.equals(raw) || MODE_OFF.equals(raw) ? raw : MODE_AUTO;
    }
}
