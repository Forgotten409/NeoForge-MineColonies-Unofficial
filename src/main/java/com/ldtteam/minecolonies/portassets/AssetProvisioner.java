package com.ldtteam.minecolonies.portassets;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ldtteam.minecolonies.MineColonies;

import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;

/**
 * Downloads and extracts the official upstream 1.21.1 jars into the external asset store —
 * PORT26 (publishing support).
 *
 * <p>Robustness rules (all verified by design):</p>
 * <ul>
 *   <li>downloads go to {@code *.tmp} files and are atomically renamed;</li>
 *   <li>download progress is streamed to the {@link ProgressListener} (bytes, total,
 *       percentage, speed) — the first play-test showed a ~78 MB download with zero
 *       feedback looks exactly like a frozen game;</li>
 *   <li>extraction happens in a sibling {@code *.tmp-<n>} directory that is atomically moved
 *       over {@code <gamedir>/port-assets/<modId>/} (replacing a broken older store = repair);</li>
 *   <li>after extraction, {@link PortAssetConverter} translates the 1.21.1 formats to the
 *       26.1.2 formats inside the temp store (see its class javadoc for the rule set);</li>
 *   <li>the {@code .provisioned.json} marker is only visible after the move, so a crashed
 *       extraction never leaves a half-provisioned state — and it records the
 *       {@link #MARKER_FORMAT}, so a store provisioned by an OLDER conversion layout is
 *       re-provisioned automatically (repair on format bump);</li>
 *   <li>nested {@code META-INF/jarjar/*.jar} jars are extracted too (BlockUI/MultiPiston-style
 *       jarJar dependencies ship their assets nested);</li>
 *   <li>{@code pack.mcmeta} is REGENERATED with the CURRENT game pack formats (the 1.21.1
 *       jars carry format 34, which 26.1.2 would reject as TOO_OLD);</li>
 *   <li>zip-slip protected entry names;</li>
 *   <li>every failure is caught and reported as a message — the game never crashes.</li>
 * </ul>
 */
public final class AssetProvisioner
{
    /**
     * Progress callback — called on the provisioning worker thread with a short human
     * readable status line (the notice screen displays it verbatim).
     */
    @FunctionalInterface
    public interface ProgressListener
    {
        void accept(String status);
    }

    /** Gson used for the marker and pack.mcmeta files. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * Store layout/conversion marker. Bumped whenever the extracted-store layout or the
     * conversion rule set changes in a way that requires re-provisioning (or re-converting)
     * an existing store — the wholesale-replace semantics make the bump the repair path
     * for every play-tester with an older store:
     * <ul>
     *   <li>{@code port26-2} — runtime conversion layer introduced (asset-side rules);</li>
     *   <li>{@code port26-3} — converter v2 (data-side rules, children-object texture
     *       renames, override target paths inside the pack);</li>
     *   <li>{@code port26-4} — obsolete {@code data/neoforge/loot_modifiers/
     *       global_loot_modifiers.json} deleted (26.1.2's LootModifierManager scans
     *       {@code loot_modifiers/} as a registry folder and chokes on the old
     *       entries/replace registry file — play-test #4's "No key type" error).</li>
     *   <li>{@code port26-5} — upstream sync to {@code minecolonies-1.1.1399-1.21.1-
     *       snapshot} (graveyard GUI layout + tavern-music/manual lang from the ARR pack,
     *       rebalanced mount research effects data).</li>
     * </ul>
     */
    private static final String MARKER_FORMAT = "port26-5";

    /** Progress report interval for streamed downloads (250 ms). */
    private static final long PROGRESS_REPORT_INTERVAL_NANOS = 250_000_000L;

    /**
     * PORT26 (0.4.6, "protection when the player has network problems"): a stalled body
     * read can block indefinitely (Wi-Fi drop without TCP RST) and the 15-minute request
     * timeout alone would leave the player staring at a frozen progress bar. The watchdog
     * aborts the transfer when no bytes arrive for this long.
     */
    private static final long STALL_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30L);

    /** Watchdog poll period (checks the last-byte timestamp above). */
    private static final long STALL_CHECK_SECONDS = 2L;

    /** Daemon watchdog executor (one sequential poller — downloads never overlap). */
    private static final ScheduledExecutorService STALL_WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread thread = new Thread(r, "minecolonies-port-assets-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    /** Shared HTTP client (follows CDN redirects, sane timeouts). */
    private static volatile HttpClient httpClient;

    private AssetProvisioner()
    {
        // static helper only
    }

    // ------------------------------------------------------------------ state

    /**
     * Whether the namespace is provisioned with the CURRENT conversion layout (marker file
     * present AND its format tag matches {@link #MARKER_FORMAT}).
     *
     * @param namespace the namespace table entry.
     * @return true when its store folder carries an up-to-date provisioning marker.
     */
    public static boolean isProvisioned(final AssetNamespace namespace)
    {
        return isStoreCurrent(downloadOwner(namespace));
    }

    /**
     * Marker check shared by {@link #isProvisioned(AssetNamespace)} and the repair paths.
     *
     * @param owner the store owner mod id.
     * @return true when the marker exists and carries the current format tag.
     */
    private static boolean isStoreCurrent(final String owner)
    {
        final Path marker = PortAssetPaths.markerFile(owner);
        if (!Files.isRegularFile(marker))
        {
            return false;
        }
        try
        {
            final JsonObject parsed = JsonParser.parseString(
                Files.readString(marker, StandardCharsets.UTF_8)).getAsJsonObject();
            return MARKER_FORMAT.equals(parsed.has("format") ? parsed.get("format").getAsString() : null);
        }
        catch (final Exception e)
        {
            MineColonies.LOGGER.warn("port-assets: unreadable marker for {} — treating as not provisioned: {}",
                owner, e.toString());
            return false;
        }
    }

    /**
     * The mod id whose jar carries this namespace's assets (jarJar nesting: e.g. blockui is
     * nested inside another upstream jar, so its assets are extracted as part of that
     * download into that owner's store folder).
     *
     * @param namespace the namespace table entry.
     * @return the owning download mod id.
     */
    public static String downloadOwner(final AssetNamespace namespace)
    {
        return namespace.providedVia() == null || namespace.providedVia().isEmpty()
                 ? namespace.modId()
                 : namespace.providedVia();
    }

    /**
     * Whether anything needs provisioning: at least one namespace neither bundled in the
     * running jar nor provisioned externally.
     *
     * @return the list of namespaces that are missing (may be empty).
     */
    public static List<AssetNamespace> missingNamespaces()
    {
        final List<AssetNamespace> missing = new ArrayList<>();
        for (final AssetNamespace namespace : PortAssets.NAMESPACES)
        {
            if (isHandledAtRuntime(namespace) || isProvisioned(namespace))
            {
                continue;
            }
            missing.add(namespace);
        }
        return missing;
    }

    /**
     * Whether a namespace's content is already provided WITHOUT the external store — either
     * bundled in the running jar (dev builds) or, for TownTalk, delivered by its runtime
     * bridge (active mod jar harvested as the provisioning source).
     *
     * @param namespace the namespace table entry.
     * @return true when no store provisioning is needed.
     */
    private static boolean isHandledAtRuntime(final AssetNamespace namespace)
    {
        if (BundledAssetDetector.isNamespaceBundled(namespace.modId()))
        {
            return true;
        }
        return TownTalkCompat.MOD_ID.equals(namespace.modId()) && TownTalkCompat.isModActive() && TownTalkCompat.activeModJar() != null;
    }

    // ------------------------------------------------------------------ provisioning

    /**
     * Provision every missing namespace (sequential, one status line at a time).
     *
     * @param listener progress sink (worker thread).
     * @return null on full success, otherwise a description of the first failure.
     */
    @Nullable
    public static String provisionMissing(final ProgressListener listener)
    {
        for (final AssetNamespace namespace : PortAssets.NAMESPACES)
        {
            if (isHandledAtRuntime(namespace) || isProvisioned(namespace))
            {
                continue;
            }
            final String error = provision(namespace, listener);
            if (error != null)
            {
                return error;
            }
        }
        return null;
    }

    /**
     * Provision a single namespace: locate a source jar (manual folder first, then the
     * official download URL), extract, mark. Idempotent: skips when already provisioned.
     *
     * @param namespace the namespace table entry.
     * @param listener  progress sink (worker thread).
     * @return null on success, otherwise a human readable error.
     */
    @Nullable
    public static String provision(final AssetNamespace namespace, final ProgressListener listener)
    {
        try
        {
            final String owner = downloadOwner(namespace);
            final Path storeDir = PortAssetPaths.namespaceDir(owner);

            // (re-)create the store root and the manual source folder
            Files.createDirectories(PortAssetPaths.root());
            Files.createDirectories(PortAssetPaths.sourceJarsDir());

            // 1) locate the source archive (manual folder, then the mods folder for
            //    bridge namespaces such as TownTalk, then the official download URL)
            Path source = findManualSource(owner);
            String sourceUrl = "manual: " + (source == null ? "-" : source.getFileName().toString());
            if (source == null && TownTalkCompat.MOD_ID.equals(namespace.modId()))
            {
                source = findModsFolderSource();
                if (source == null)
                {
                    source = TownTalkCompat.activeModJar();
                }
                if (source != null)
                {
                    sourceUrl = "mods: " + source.getFileName().toString();
                }
            }
            if (source == null)
            {
                if (namespace.downloadUrl() == null || namespace.downloadUrl().isEmpty())
                {
                    return "No download URL for '" + namespace.displayName()
                             + "' — use a manual install (see docs/PUBLISHING.md).";
                }
                listener.accept(PortAssetText.format("portassets.status.downloading", namespace.displayName()));
                sourceUrl = namespace.downloadUrl();
                source = downloadToTemp(namespace, PortAssetPaths.root(), listener);
            }

            // 2) extract into a temp sibling, then atomically swap into place
            final int[] extractedFiles = {0};
            listener.accept(PortAssetText.format("portassets.status.extracting", namespace.displayName(), 0));
            final Path tempDir = storeDir.resolveSibling(owner + ".tmp-" + Long.toUnsignedString(System.nanoTime()));
            Files.createDirectories(tempDir);
            try
            {
                extractArchive(source, tempDir, 0, namespace.rootPrefix(), namespace.displayName(), extractedFiles, listener);

                // 3) translate the 1.21.1 formats to the 26.1.2 formats inside the store
                listener.accept(PortAssetText.format("portassets.status.converting", 0));
                final int[] convertedFiles = {0};
                PortAssetConverter.convert(tempDir, namespace, convertedFiles, listener);
                listener.accept(PortAssetText.format("portassets.status.converting", convertedFiles[0]));

                writePackMcmeta(tempDir, owner);

                // marker last: only a fully extracted store is ever "provisioned"
                final Marker marker = new Marker();
                marker.sourceUrl = sourceUrl;
                marker.fileName = source.getFileName().toString();
                marker.date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                marker.format = MARKER_FORMAT;
                Files.writeString(tempDir.resolve(PortAssetPaths.MARKER_FILE),
                    GSON.toJson(marker), StandardCharsets.UTF_8);

                // swap (repair semantics: an older/broken store is replaced wholesale)
                if (Files.exists(storeDir))
                {
                    deleteRecursively(storeDir);
                }
                try
                {
                    Files.move(tempDir, storeDir, StandardCopyOption.ATOMIC_MOVE);
                }
                catch (final AtomicMoveNotSupportedException e)
                {
                    Files.move(tempDir, storeDir);
                }
            }
            finally
            {
                if (Files.exists(tempDir))
                {
                    deleteRecursively(tempDir);
                }
            }
            listener.accept(PortAssetText.format("portassets.status.ready", namespace.displayName()));
            return null;
        }
        catch (final Throwable t)
        {
            MineColonies.LOGGER.warn("port-assets: provisioning '{}' failed", namespace.modId(), t);
            return namespace.displayName() + ": " + friendlyError(t);
        }
    }

    /**
     * PORT26 (0.4.6): maps a provisioning failure onto a short, actionable, TRANSLATED
     * message for the notice screen — the full stack stays in the log. Network failures
     * are by far the most common (player offline, captive portal, flaky Wi-Fi, upstream
     * CDN hiccup) and a raw {@code "IOException: unexpected 0x00 while reading"} helps
     * nobody.
     *
     * @param t the failure (message and its whole cause chain are inspected).
     * @return the user-facing error line.
     */
    private static String friendlyError(final Throwable t)
    {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause())
        {
            if (c instanceof StalledDownloadException)
            {
                return PortAssetText.format("portassets.error.stall");
            }
            if (c instanceof InterruptedException)
            {
                return PortAssetText.format("portassets.error.interrupted");
            }
            if (c instanceof java.net.http.HttpTimeoutException
                  || c instanceof java.net.ConnectException
                  || c instanceof java.net.UnknownHostException
                  || c instanceof java.net.NoRouteToHostException
                  || c instanceof java.net.SocketTimeoutException)
            {
                return PortAssetText.format("portassets.error.offline");
            }
            final String message = c.getMessage();
            if (message != null && message.startsWith("HTTP ") && message.contains(" from http"))
            {
                return PortAssetText.format("portassets.error.http", message);
            }
        }
        return t.getClass().getSimpleName() + (t.getMessage() != null ? " — " + t.getMessage() : "");
    }

    /**
     * Marker for the watchdog abort path — recognized by {@link #friendlyError(Throwable)}
     * so the notice screen can show the dedicated "stalled" wording instead of the raw
     * shutdown-io noise the HttpClient produces.
     */
    private static final class StalledDownloadException extends IOException
    {
        private StalledDownloadException()
        {
            super("download stalled — no data for " + TimeUnit.NANOSECONDS.toSeconds(STALL_TIMEOUT_NANOS) + "s");
        }
    }

    /**
     * Looks for a user-provided official archive in {@code port-assets/source-jars/}. Matching
     * is filename-based: the file name must start with the owner mod id (with {@code -} or
     * {@code _} separators), e.g. {@code minecolonies-1.1.1399-1.21.1-snapshot.jar} or
     * {@code blockui-release-main.tar.gz}.
     *
     * @param owner the owning download mod id.
     * @return the jar/zip/tarball path, or null when nothing matches.
     */
    @Nullable
    private static Path findManualSource(final String owner)
    {
        final Path dir = PortAssetPaths.sourceJarsDir();
        if (!Files.isDirectory(dir))
        {
            return null;
        }
        try (final var stream = Files.list(dir))
        {
            final String[] prefixes = {owner + "-", owner + "_", owner + "."};
            for (final Path candidate : stream.filter(Files::isRegularFile).sorted().toList())
            {
                final String name = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".jar") && !name.endsWith(".zip")
                      && !name.endsWith(".tar.gz") && !name.endsWith(".tgz"))
                {
                    continue;
                }
                for (final String prefix : prefixes)
                {
                    if (name.startsWith(prefix))
                    {
                        return candidate;
                    }
                }
            }
        }
        catch (final IOException e)
        {
            MineColonies.LOGGER.warn("port-assets: could not list {}: {}", dir, e.toString());
        }
        return null;
    }

    /**
     * Scans the game's {@code mods/} folder for a TownTalk jar that is NOT an active mod —
     * the documented way to keep the official jar in the mods folder on 26.1.2: rename it to
     * {@code towntalk-1.2.0.jar.disabled} (FML skips disabled files; this port still reads
     * the bytes as an asset source). Any {@code towntalk*} file that is a jar (by magic
     * bytes, regardless of suffix) qualifies.
     *
     * @return the jar path, or null when the folder has no usable TownTalk jar.
     */
    @Nullable
    private static Path findModsFolderSource()
    {
        final Path modsDir = PortAssetPaths.gameDir().resolve("mods");
        if (!Files.isDirectory(modsDir))
        {
            return null;
        }
        try (final var stream = Files.list(modsDir))
        {
            for (final Path candidate : stream.filter(Files::isRegularFile).sorted().toList())
            {
                final String name = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.startsWith(TownTalkCompat.MOD_ID))
                {
                    continue;
                }
                if (hasZipMagicBytes(candidate))
                {
                    MineColonies.LOGGER.info("port-assets: using {} from the mods folder as the TownTalk asset source",
                        candidate.getFileName());
                    return candidate;
                }
            }
        }
        catch (final IOException e)
        {
            MineColonies.LOGGER.warn("port-assets: could not list {}: {}", modsDir, e.toString());
        }
        return null;
    }

    /**
     * @param file any file.
     * @return true when the first four bytes are the zip local-header signature ({@code PK\x03\x04})
     *         — true for every jar/zip regardless of the file suffix.
     */
    private static boolean hasZipMagicBytes(final Path file)
    {
        try (final InputStream in = Files.newInputStream(file))
        {
            final int b0 = in.read();
            final int b1 = in.read();
            final int b2 = in.read();
            final int b3 = in.read();
            return b0 == 'P' && b1 == 'K' && b2 == 3 && b3 == 4;
        }
        catch (final IOException e)
        {
            return false;
        }
    }

    /**
     * Downloads the official jar to a {@code *.tmp} file inside the store root and renames
     * it atomically, STREAMING the body and reporting byte-level progress (total from
     * Content-Length, percentage, MB/s — throttled to ~4 reports/second). Returned file
     * name is stable ({@code source-jars/<expectedFile>}) so a later re-run finds it as a
     * manual source.
     *
     * @param namespace the namespace table entry.
     * @param targetDir directory for the temp file.
     * @param listener  progress sink (worker thread) — may be null for silent calls.
     * @return the downloaded file path.
     * @throws IOException on network/IO failure (caller converts to a message).
     */
    private static Path downloadToTemp(final AssetNamespace namespace, final Path targetDir,
        @Nullable final ProgressListener listener) throws IOException
    {
        Files.createDirectories(targetDir);
        final Path target = PortAssetPaths.sourceJarsDir().resolve(namespace.expectedFile());
        if (Files.isRegularFile(target))
        {
            return target; // cached manual source from an earlier run
        }
        final Path tmp = target.resolveSibling(namespace.expectedFile() + ".tmp");
        Files.deleteIfExists(tmp);
        try
        {
            HttpClient client = httpClient;
            if (client == null)
            {
                synchronized (AssetProvisioner.class)
                {
                    client = httpClient;
                    if (client == null)
                    {
                        client = HttpClient.newBuilder()
                                  .followRedirects(HttpClient.Redirect.NORMAL)
                                  .connectTimeout(Duration.ofSeconds(30))
                                  .build();
                        httpClient = client;
                    }
                }
            }
            final HttpRequest request = HttpRequest.newBuilder(URI.create(namespace.downloadUrl()))
                                             .GET()
                                             .timeout(Duration.ofMinutes(15))
                                             .build();
            final HttpResponse<InputStream> response;
            try
            {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            }
            catch (final InterruptedException e)
            {
                // Cancellation of the provisioning worker thread (notice screen closed /
                // game shutting down): restore the interrupt flag and surface as IOException —
                // provision() converts it into the readable error shown on the notice screen.
                Thread.currentThread().interrupt();
                throw new IOException("Download of " + namespace.displayName() + " was interrupted", e);
            }
            if (response.statusCode() != 200)
            {
                throw new IOException("HTTP " + response.statusCode() + " from " + namespace.downloadUrl());
            }
            final long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            final long started = System.nanoTime();
            long lastReport = 0;

            // PORT26 (0.4.6): stall watchdog — a body read can block forever on a silently
            // dropped connection. The poller watches the last-byte timestamp; on timeout it
            // shuts the client down (the only reliable way to unblock an in-flight body
            // read), and the copy loop below converts that into a StalledDownloadException.
            final AtomicLong lastByteNanos = new AtomicLong(System.nanoTime());
            final AtomicBoolean stalled = new AtomicBoolean(false);
            final ScheduledFuture<?> watchdog = STALL_WATCHDOG.scheduleWithFixedDelay(() -> {
                if (!stalled.get() && System.nanoTime() - lastByteNanos.get() > STALL_TIMEOUT_NANOS)
                {
                    stalled.set(true);
                    MineColonies.LOGGER.warn("port-assets: download of {} stalled — no data for {}s, aborting",
                        namespace.displayName(), TimeUnit.NANOSECONDS.toSeconds(STALL_TIMEOUT_NANOS));
                    final HttpClient dead = httpClient;
                    httpClient = null; // the next attempt builds a fresh client
                    if (dead != null)
                    {
                        try
                        {
                            dead.shutdownNow();
                        }
                        catch (final Throwable ignored)
                        {
                            // already shutting down — the read unblocks either way
                        }
                    }
                }
            }, STALL_CHECK_SECONDS, STALL_CHECK_SECONDS, TimeUnit.SECONDS);

            try (final InputStream body = response.body(); final OutputStream out = Files.newOutputStream(tmp))
            {
                final byte[] buffer = new byte[64 * 1024];
                long downloaded = 0;
                int read;
                try
                {
                    while ((read = body.read(buffer)) >= 0)
                    {
                        out.write(buffer, 0, read);
                        downloaded += read;
                        lastByteNanos.set(System.nanoTime());
                        final long now = lastByteNanos.get();
                        if (listener != null && now - lastReport >= PROGRESS_REPORT_INTERVAL_NANOS)
                        {
                            lastReport = now;
                            listener.accept(progressLine(downloaded, totalBytes, started, now));
                        }
                    }
                }
                catch (final IOException e)
                {
                    if (stalled.get())
                    {
                        throw new StalledDownloadException();
                    }
                    throw e;
                }
                if (listener != null)
                {
                    listener.accept(progressLine(downloaded, totalBytes, started, System.nanoTime()));
                }
            }
            finally
            {
                watchdog.cancel(false);
            }
            try
            {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (final AtomicMoveNotSupportedException e)
            {
                Files.move(tmp, target);
            }
            return target;
        }
        finally
        {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Builds one human-readable progress line, e.g. {@code "12.4 / 78.2 MB (16%) — 3.2 MB/s"}.
     *
     * @param downloaded bytes transferred so far.
     * @param total      total bytes from Content-Length, or -1 when unknown.
     * @param started    download start (nanos).
     * @param now        current time (nanos).
     * @return the progress line.
     */
    private static String progressLine(final long downloaded, final long total, final long started, final long now)
    {
        final double mb = downloaded / (1024.0 * 1024.0);
        final double seconds = Math.max(0.001, (now - started) / 1_000_000_000.0);
        final String speed = String.format(Locale.ROOT, "%.1f", mb / seconds);
        if (total > 0)
        {
            final double totalMb = total / (1024.0 * 1024.0);
            final int percent = (int) Math.min(100, (100 * downloaded) / total);
            return String.format(Locale.ROOT, "%.1f / %.1f MB (%d%%) — %s MB/s", mb, totalMb, percent, speed);
        }
        return String.format(Locale.ROOT, "%.1f MB — %s MB/s", mb, speed);
    }

    // ------------------------------------------------------------------ extraction

    /**
     * Top-level extracted roots (relative to the jar root).
     */
    private static final String[] EXTRACT_ROOTS = {"assets/", "data/", "blueprints/", "structures/"};

    /**
     * Datapack folder renames applied at extraction time: the official 1.21.1 jars predate
     * several <b>vanilla data folder renames</b> that 26.1.2 requires — without this, the
     * injected data pack's tags silently no-op (vanilla only reads the new names) and legacy
     * folders (when present) are ignored:
     * <ul>
     *   <li>plural {@code tags/<type>s} folders → singular ({@code tags/blocks} →
     *       {@code tags/block}, etc.) — renamed by vanilla in the 1.21.2 line;</li>
     *   <li>{@code recipes/ loot_tables/ advancements/ predicates/ item_modifiers/} →
     *       singular forms — renamed by vanilla in 1.21 (defensive: the 1.21.1 jars already
     *       ship singular for most of these).</li>
     * </ul>
     * Renaming only affects the EXTRACTED copy; the source jar is untouched.
     */
    private static final java.util.Map<String, String> DATA_TAG_FOLDER_RENAMES = java.util.Map.of(
        "blocks", "block",
        "items", "item",
        "entity_types", "entity_type",
        "fluids", "fluid",
        "game_events", "game_event");

    private static final java.util.Map<String, String> DATA_FOLDER_RENAMES = java.util.Map.of(
        "recipes", "recipe",
        "loot_tables", "loot_table",
        "advancements", "advancement",
        "predicates", "predicate",
        "item_modifiers", "item_modifier");

    /** Nested jar-in-jar recursion limit (outer jar = depth 0). */
    private static final int MAX_JARJAR_DEPTH = 2;

    /**
     * Applies the {@link #DATA_TAG_FOLDER_RENAMES}/{@link #DATA_FOLDER_RENAMES} to a
     * normalized {@code data/<ns>/...} entry path (see the field javadoc).
     *
     * @param normalized normalized entry path (no leading slash, forward slashes).
     * @return the path to extract to (identical when no rename applies).
     */
    private static String mapDataFolderPath(final String normalized)
    {
        final String[] segments = normalized.split("/");
        if (segments.length >= 4 && "data".equals(segments[0]) && "tags".equals(segments[2]))
        {
            final String renamedTagFolder = DATA_TAG_FOLDER_RENAMES.get(segments[3]);
            if (renamedTagFolder != null)
            {
                segments[3] = renamedTagFolder;
                return String.join("/", segments);
            }
        }
        if (segments.length >= 3 && "data".equals(segments[0]))
        {
            final String renamedFolder = DATA_FOLDER_RENAMES.get(segments[2]);
            if (renamedFolder != null)
            {
                segments[2] = renamedFolder;
                return String.join("/", segments);
            }
        }
        return normalized;
    }

    /**
     * Normalizes an entry name: strips a leading slash and converts backslashes.
     */
    private static String normalizeEntryName(final String entryName)
    {
        return (entryName.startsWith("/") ? entryName.substring(1) : entryName).replace('\\', '/');
    }

    /**
     * Extracts the interesting entries of a zip/jar (or tgz source tarball) into the target
     * directory, recursing into {@code META-INF/jarjar/*.jar}, reporting file-count progress.
     *
     * @param source    the archive file (jar/zip, or GitHub source .tar.gz/.tgz).
     * @param targetDir the store temp directory.
     * @param depth     jarJar nesting depth (outer = 0).
     * @param rootPrefix directory prefix inside the archive that carries the content
     *                  (e.g. TownTalk's {@code "respack/"}); stripped before the root check.
     *                  Only applied at depth 0 — nested jarJar jars have their own layout.
     * @param displayName the namespace display name (progress lines).
     * @param extractedFiles mutable single-int counter shared across the recursion.
     * @param listener  progress sink (worker thread) — may be null.
     * @throws IOException on any IO failure.
     */
    private static void extractArchive(final Path source, final Path targetDir, final int depth, final String rootPrefix,
        final String displayName, final int[] extractedFiles, @Nullable final ProgressListener listener) throws IOException
    {
        final String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz"))
        {
            extractSourceTarball(source, targetDir);
            return;
        }

        try (final ZipFile zip = new ZipFile(source.toFile()))
        {
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements())
            {
                final ZipEntry entry = entries.nextElement();
                final String entryName = entry.getName();
                if (entryName == null || entryName.isEmpty())
                {
                    continue;
                }
                // nested jarJar: extract its assets into the SAME target (namespace merge)
                if (entryName.startsWith("META-INF/jarjar/") && entryName.endsWith(".jar") && !entry.isDirectory())
                {
                    if (depth < MAX_JARJAR_DEPTH)
                    {
                        final Path nestedTmp = targetDir.resolveSibling(
                            targetDir.getFileName() + ".nested-" + Integer.toUnsignedString(entryName.hashCode()));
                        Files.createDirectories(nestedTmp.getParent());
                        try (final InputStream in = zip.getInputStream(entry))
                        {
                            Files.copy(in, nestedTmp, StandardCopyOption.REPLACE_EXISTING);
                        }
                        try
                        {
                            extractArchive(nestedTmp, targetDir, depth + 1, "", displayName, extractedFiles, listener);
                        }
                        finally
                        {
                            Files.deleteIfExists(nestedTmp);
                        }
                    }
                    continue;
                }
                if (!isExtractedRoot(entryName, rootPrefix))
                {
                    continue; // classes, META-INF, pack.mcmeta (regenerated), etc.
                }
                // strip the content root prefix (TownTalk's respack/) BEFORE the data-folder
                // renames, so the store layout is always assets/ data/ blueprints/ structures/
                final String prefixStripped = stripRootPrefix(normalizeEntryName(entryName), rootPrefix);
                // vanilla datapack folder renames (1.21.1 → 26.1.2): tags/blocks → tags/block etc.
                final String mappedName = mapDataFolderPath(prefixStripped);
                final Path destination = safeResolve(targetDir, mappedName);
                if (entry.isDirectory())
                {
                    Files.createDirectories(destination);
                }
                else
                {
                    Files.createDirectories(destination.getParent());
                    try (final InputStream in = zip.getInputStream(entry))
                    {
                        Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                    extractedFiles[0]++;
                    if (listener != null && (extractedFiles[0] % 500) == 0)
                    {
                        listener.accept(PortAssetText.format("portassets.status.extracting", displayName, extractedFiles[0]));
                    }
                }
            }
        }
    }

    /**
     * Extracts a GitHub source tarball ({@code <project>-<branch>/src/main/resources/...})
     * into the target directory — the BlockUI fallback source (not distributed on
     * CurseForge). Only files below {@code src/main/resources/} that live under one of the
     * extracted roots are taken.
     *
     * @param source    the .tar.gz / .tgz file.
     * @param targetDir the store temp directory.
     * @throws IOException on any IO failure.
     */
    private static void extractSourceTarball(final Path source, final Path targetDir) throws IOException
    {
        try (final InputStream fileIn = new BufferedInputStream(Files.newInputStream(source, StandardOpenOption.READ));
               final InputStream gzipIn = new GZIPInputStream(fileIn))
        {
            byte[] header = new byte[512];
            String longName = null;
            for (;;)
            {
                final int read = readFully(gzipIn, header);
                if (read < 512)
                {
                    break; // end of archive
                }
                if (isZeroBlock(header))
                {
                    continue; // padding block
                }
                final long size = parseTarOctal(header, 124, 12);
                final char typeFlag = (char) header[156];
                final String rawName = longName != null ? longName : tarString(header, 0, 100);
                longName = null;

                if (typeFlag == 'L')
                {
                    // GNU long name: data block holds the next entry's real name
                    final byte[] nameBytes = new byte[(int) size];
                    readFully(gzipIn, nameBytes);
                    longName = new String(nameBytes, StandardCharsets.UTF_8).trim();
                    skipTarPadding(gzipIn, size);
                    continue;
                }
                if (typeFlag == 'x' || typeFlag == 'g')
                {
                    // pax headers: skip data
                    skipTarData(gzipIn, size);
                    continue;
                }

                final String mapped = mapSourceTarballPath(rawName);
                if (mapped != null)
                {
                    if (typeFlag == '5')
                    {
                        Files.createDirectories(safeResolve(targetDir, mapped));
                    }
                    else if (typeFlag == '0' || typeFlag == '\0')
                    {
                        final Path destination = safeResolve(targetDir, mapped);
                        Files.createDirectories(destination.getParent());
                        try (final InputStream tarEntryData = boundedStream(gzipIn, size))
                        {
                            Files.copy(tarEntryData, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                        skipTarPadding(gzipIn, size);
                        continue;
                    }
                }
                // entry not needed — skip its data
                skipTarData(gzipIn, size);
            }
        }
    }

    /**
     * Maps {@code <project>-<branch>/src/main/resources/assets/...} to {@code assets/...};
     * returns null for anything that is not a resource under one of the extracted roots.
     * Applies the datapack folder renames to {@code data/...} paths like the jar extractor.
     */
    @Nullable
    private static String mapSourceTarballPath(final String rawName)
    {
        String name = rawName;
        final int marker = name.indexOf("src/main/resources/");
        if (marker >= 0)
        {
            name = name.substring(marker + "src/main/resources/".length());
        }
        else
        {
            return null;
        }
        final String normalized = normalizeEntryName(name);
        return isExtractedRoot(normalized, "") ? mapDataFolderPath(normalized) : null;
    }

    /**
     * @param entryName the jar/tar entry path (forward slashes, no leading slash).
     * @param rootPrefix the content root prefix to strip (may be empty).
     * @return true when the entry lives under an extracted root (after prefix stripping).
     */
    private static boolean isExtractedRoot(final String entryName, final String rootPrefix)
    {
        final String stripped = stripRootPrefix(
            entryName.startsWith("/") ? entryName.substring(1) : entryName, rootPrefix);
        for (final String root : EXTRACT_ROOTS)
        {
            if (stripped.equals(root.substring(0, root.length() - 1)) || stripped.startsWith(root))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Strips the namespace's content root prefix (e.g. TownTalk's {@code "respack/"})
     * from a normalized entry name. Entries NOT under the prefix are returned unchanged
     * (and then fail the {@link #isExtractedRoot} check).
     *
     * @param normalized normalized entry path (no leading slash, forward slashes).
     * @param rootPrefix the prefix to strip (empty = identity).
     * @return the path relative to the content root.
     */
    private static String stripRootPrefix(final String normalized, final String rootPrefix)
    {
        if (rootPrefix.isEmpty() || normalized.equals(rootPrefix) || !normalized.startsWith(rootPrefix))
        {
            return normalized;
        }
        return normalized.substring(rootPrefix.length());
    }

    /**
     * Zip-slip protected resolve (same contract as the port's
     * {@code ClientStructurePackLoader.zipSlipProtect}).
     */
    private static Path safeResolve(final Path targetDir, final String entryName) throws IOException
    {
        final String normalized = (entryName.startsWith("/") ? entryName.substring(1) : entryName)
                                     .replace('\\', '/');
        final Path resolved = targetDir.resolve(normalized).normalize();
        if (!resolved.startsWith(targetDir.normalize()))
        {
            throw new IOException("Bad archive entry: " + entryName);
        }
        return resolved;
    }

    // ------------------------------------------------------------------ tar helpers

    private static boolean isZeroBlock(final byte[] block)
    {
        for (final byte b : block)
        {
            if (b != 0)
            {
                return false;
            }
        }
        return true;
    }

    private static String tarString(final byte[] header, final int offset, final int length)
    {
        int end = offset;
        final int limit = Math.min(offset + length, header.length);
        while (end < limit && header[end] != 0)
        {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long parseTarOctal(final byte[] header, final int offset, final int length)
    {
        long value = 0;
        boolean started = false;
        for (int i = offset; i < offset + length; i++)
        {
            final byte b = header[i];
            if (b == 0 || b == ' ')
            {
                if (started)
                {
                    break;
                }
                continue;
            }
            if (b < '0' || b > '7')
            {
                break;
            }
            started = true;
            value = (value << 3) + (b - '0');
        }
        return value;
    }

    private static int readFully(final InputStream in, final byte[] buffer) throws IOException
    {
        int total = 0;
        while (total < buffer.length)
        {
            final int n = in.read(buffer, total, buffer.length - total);
            if (n < 0)
            {
                return total;
            }
            total += n;
        }
        return total;
    }

    /** Skips the data blocks of one tar entry (and its padding). */
    private static void skipTarData(final InputStream in, final long size) throws IOException
    {
        long remaining = size;
        while (remaining > 0)
        {
            final long skipped = in.skip(remaining);
            if (skipped <= 0)
            {
                if (in.read() < 0)
                {
                    return;
                }
                remaining--;
                continue;
            }
            remaining -= skipped;
        }
        skipTarPadding(in, size);
    }

    private static void skipTarPadding(final InputStream in, final long size) throws IOException
    {
        final int padding = (int) ((512 - (size % 512)) % 512);
        if (padding > 0)
        {
            skipTarData(in, padding);
        }
    }

    /**
     * Reads exactly {@code limit} bytes from the stream (tar entry payload). The stream is
     * NOT closed — the caller owns it.
     */
    private static InputStream boundedStream(final InputStream in, final long limit)
    {
        return new InputStream()
        {
            private long remaining = limit;

            @Override
            public int read() throws IOException
            {
                if (remaining <= 0)
                {
                    return -1;
                }
                final int value = in.read();
                if (value >= 0)
                {
                    remaining--;
                }
                return value;
            }

            @Override
            public int read(final byte[] buffer, final int offset, final int length) throws IOException
            {
                if (remaining <= 0)
                {
                    return -1;
                }
                final int n = in.read(buffer, offset, (int) Math.min(length, remaining));
                if (n > 0)
                {
                    remaining -= n;
                }
                return n;
            }
        };
    }

    // ------------------------------------------------------------------ pack.mcmeta + marker

    /**
     * The pack.mcmeta written into every provisioned store folder. One file serves BOTH the
     * resource pack and the data pack, so the declared format range spans the smaller of
     * the two current majors up to the larger one:
     * <pre>
     * {"pack": {"description": "...", "min_format": 84, "max_format": 101}}
     * </pre>
     * {@code min_format: 84} parses as {@code (84, 0)} and {@code max_format: 101} as
     * {@code (101, Integer.MAX_VALUE)} (26.1.2 {@code PackFormat} codec semantics), which
     * covers both {@code CLIENT_RESOURCES (84.x)} and {@code SERVER_DATA (101.x)} — see
     * {@code PackCompatibility.forVersion}.
     *
     * @param storeDir the store folder.
     * @param owner    the owning mod id (for the description text).
     * @throws IOException on write failure.
     */
    public static void writePackMcmeta(final Path storeDir, final String owner) throws IOException
    {
        final int clientMajor = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major();
        final int dataMajor = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).major();
        final int minFormat = Math.min(clientMajor, dataMajor);
        final int maxFormat = Math.max(clientMajor, dataMajor);

        final StringBuilder json = new StringBuilder(200);
        json.append("{\n  \"pack\": {\n");
        json.append("    \"description\": \"").append(packDescription(owner)).append("\",\n");
        json.append("    \"min_format\": ").append(minFormat).append(",\n");
        json.append("    \"max_format\": ").append(maxFormat).append("\n");
        json.append("  }\n}\n");

        final Path file = storeDir.resolve("pack.mcmeta");
        final String content = json.toString();
        if (!Files.exists(file) || !content.equals(Files.readString(file, StandardCharsets.UTF_8)))
        {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        }
    }

    /**
     * The pack.mcmeta description for one store owner — shown as the pack description in
     * the resource-pack list. Says WHERE the content came from, so the ARR provenance
     * (runtime download from the official channel, never bundled in our jar) stays
     * visible to the player.
     *
     * @param owner the store owner mod id.
     * @return a short human-readable description.
     */
    private static String packDescription(final String owner)
    {
        final AssetNamespace namespace = PortAssets.byOwner(owner);
        if (namespace != null)
        {
            return "Downloaded at runtime from the official CurseForge channel ("
                + namespace.displayName() + ")";
        }
        return "MineColonies 26.1 port external assets (" + owner + ")";
    }

    /**
     * Ensures the pack.mcmeta of a provisioned store matches the CURRENT game formats —
     * called from the pack finder right before the pack is read, so a store provisioned by
     * an older game version transparently upgrades.
     *
     * @param owner the owning mod id.
     */
    public static void ensurePackMcmeta(final String owner)
    {
        final Path storeDir = PortAssetPaths.namespaceDir(owner);
        if (!Files.isDirectory(storeDir))
        {
            return;
        }
        try
        {
            writePackMcmeta(storeDir, owner);
        }
        catch (final IOException e)
        {
            MineColonies.LOGGER.warn("port-assets: could not refresh pack.mcmeta for {}: {}", owner, e.toString());
        }
    }

    /**
     * Deletes a namespace store (repair path — e.g. triggered by
     * {@code settings.json "reprovision": true}).
     *
     * @param modId the namespace mod id.
     * @return true when the folder is gone afterwards.
     */
    public static boolean deleteStore(final String modId)
    {
        final Path dir = PortAssetPaths.namespaceDir(modId);
        if (!Files.exists(dir))
        {
            return true;
        }
        try
        {
            deleteRecursively(dir);
            return true;
        }
        catch (final IOException e)
        {
            MineColonies.LOGGER.warn("port-assets: could not delete {}: {}", dir, e.toString());
            return false;
        }
    }

    private static void deleteRecursively(final Path dir) throws IOException
    {
        try (final var walk = Files.walk(dir))
        {
            final List<Path> paths = walk.sorted(java.util.Comparator.reverseOrder()).toList();
            for (final Path path : paths)
            {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * Content of the {@code .provisioned.json} marker.
     */
    @SuppressWarnings("unused")
    private static final class Marker
    {
        String sourceUrl;
        String fileName;
        String date;
        String format;
    }
}
