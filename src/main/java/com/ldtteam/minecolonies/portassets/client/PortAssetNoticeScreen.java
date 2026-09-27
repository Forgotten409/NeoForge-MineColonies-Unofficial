package com.ldtteam.minecolonies.portassets.client;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ldtteam.minecolonies.MineColonies;
import com.ldtteam.minecolonies.portassets.AssetNamespace;
import com.ldtteam.minecolonies.portassets.AssetProvisioner;
import com.ldtteam.minecolonies.portassets.PortAssetPaths;
import com.ldtteam.minecolonies.portassets.PortAssetSettings;
import com.ldtteam.minecolonies.portassets.PortAssetText;
import com.ldtteam.minecolonies.portassets.PortAssets;
import com.ldtteam.minecolonies.portassets.PortAssetPackSources;
import com.ldtteam.structurize.storage.ClientStructurePackLoader;
import com.ldtteam.structurize.storage.ClientStructurePackLoader.ClientLoadingState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.jetbrains.annotations.Nullable;

/**
 * First-run notice + provisioning screen — PORT26 (publishing support).
 *
 * <p>26.1.2 render pipeline: screens override {@code extractRenderState(GuiGraphicsExtractor,...)}
 * (the old {@code render(GuiGraphics,...)} is gone — see {@code BOScreen}'s port notes);
 * text goes through {@code gui.text(...)} / {@code gui.centeredText(...)}; boxes through
 * {@code gui.fill(...)}.</p>
 *
 * <p>UX contract (hardened over five publish-jar play-tests; v4 = 0.4.6 feedback):</p>
 * <ul>
 *   <li>every label resolves through {@link PortAssetText} — live translations first,
 *       embedded English/Polish fallbacks second, so the screen is readable even when no
 *       lang file loaded at all. Every text color is a full ARGB value with the alpha byte
 *       set ({@code 0xFFFFFFFF}) — {@code GuiGraphicsExtractor#text} silently drops any
 *       color whose alpha is zero (play-test #6's "no text on the screen").</li>
 *   <li><b>v4 layout:</b> the whole content block is vertically CENTERED (the v3
 *       top-anchored block hugged the top edge on tall screens); the button hierarchy is
 *       <b>two full buttons + one demoted text link</b> — [Download now] (primary,
 *       keyboard-focused) and [Manual install] as the full-size secondary, while "play
 *       without the art" became a small underlined link below the stack with an explicit
 *       warning tooltip. Three same-size buttons read as "three equally valid choices",
 *       which is not the message: downloading IS the intended path, manual install is the
 *       offline escape hatch and skipping is the last resort. A density tier
 *       (compact {@code <340px} / ultra {@code <262px}) keeps everything inside the
 *       smallest legal GUI height.</li>
 *   <li><b>v4 hard modal:</b> the screen consumes EVERY input event (mouse click, release,
 *       drag, scroll, key press/release, char input). Whatever the engine (or a layered
 *       screen implementation) would do with an unconsumed click, nothing can be pressed
 *       "through" this overlay — the reported "title screen buttons clickable behind the
 *       notice" class of bug is now structurally impossible. ESC still closes the screen
 *       whenever it is safe to do so (never while a download is running).</li>
 *   <li>the provisioning run reports byte-level progress ("12.4 / 78.2 MB (16%) — 3.2 MB/s"),
 *       extraction file counts and conversion counts — all shown live on a PROGRESS BAR
 *       (the percent is parsed out of the status line, so the worker thread needs no
 *       client-side types); phase changes fall back to the animated indeterminate sweep
 *       (an idle full bar reads as "the game froze" — play-test #3);</li>
 *   <li>network failures are shown as short, actionable, TRANSLATED lines ("no internet",
 *       "download stalled — connection dropped", "server answered HTTP 500…") — see
 *       {@code AssetProvisioner#friendlyError}; the full stack trace stays in the log;</li>
 *   <li>[Download now] turns into [Retry] after a failure; the whole button stack is HIDDEN
 *       while the worker runs (a clean progress view — v3's disabled grey pile only
 *       invited clicking around);</li>
 *   <li><b>v4 explicit exit:</b> every terminal state ends in a focused [Continue] button
 *       (green "assets active" / amber "restart required") — the v3 auto-return raced the
 *       player and reports described "no way out but ESC". ESC also works, but there is
 *       always a visible, focusable way forward;</li>
 *   <li>on success the resource packs are reloaded ({@code Minecraft#reloadResourcePacks()})
 *       and the live repository is VERIFIED afterwards; the client structure-pack discovery
 *       is then RE-RUN (see {@link PortAssetStyleReload}) so the provisioned building
 *       styles are usable immediately — the final status only turns green when the external
 *       style packs are verified REGISTERED; otherwise the screen shows the explicit
 *       "restart the game" line — never a silent no-op;</li>
 *   <li>[Manual install] shows the offline instructions INCLUDING the addon pack options
 *       (TownTalk voices, Byzantine + StyleColonies styles); the small "play without the
 *       art" link starts the game without assets (missing textures — no crash, the notice
 *       reappears on the next launch until the assets are provisioned) and remembers the
 *       choice.</li>
 * </ul>
 */
public class PortAssetNoticeScreen extends Screen
{
    private static final int BUTTON_WIDTH = Button.BIG_WIDTH;
    private static final int BUTTON_HEIGHT = Button.DEFAULT_HEIGHT;

    /** Vertical gap between the stacked buttons. */
    private static final int BUTTON_GAP = 10;

    /** Height of the demoted "play without the art" text link (touch-friendly). */
    private static final int LINK_HEIGHT = 16;

    /** Progress bar geometry (width matches the buttons). */
    private static final int BAR_HEIGHT = 10;
    private static final int BAR_INSET = 1;

    /** Max text column width — keeps wrapped paragraphs readable on wide screens. */
    private static final int MAX_WRAP_WIDTH = 400;

    /** Screen-height density tiers (see class javadoc). */
    private static final int COMPACT_HEIGHT = 340;
    private static final int ULTRA_HEIGHT   = 262;

    /** Extracts the percent out of progress lines like {@code "12.4 / 78.2 MB (16%) — 3.2 MB/s"}. */
    private static final Pattern PERCENT_PATTERN = Pattern.compile("\\((\\d+)%\\)");

    /** Daemon provisioning executor (one sequential worker). */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        final Thread thread = new Thread(r, "minecolonies-port-assets");
        thread.setDaemon(true);
        return thread;
    });

    /** Guards against double-starting the provisioning run. */
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    /**
     * Layout phases — drive both the widget set (init) and the vertical centering.
     * {@code APPLYING} spans the resource reload + style re-discovery window: the assets
     * are on disk but not yet verified live, so there is no actionable button yet.
     */
    private enum Phase
    {
        /** Initial notice (or retry after a failure) — full button stack. */
        NOTICE,
        /** Worker thread provisioning — status + progress bar only. */
        RUNNING,
        /** Provisioned, reload/verification in flight — status (+ bar) only. */
        APPLYING,
        /** Verified terminal state — [Continue]. */
        DONE,
        /** Offline instructions view. */
        MANUAL
    }

    private final List<AssetNamespace> missing;

    /** Volatile status line, written by the worker thread, read by the render thread. */
    private volatile String status = "";

    /** Volatile progress percent 0-100 (parsed from the status line); -1 = indeterminate. */
    private volatile int progressPercent = -1;

    /** Set when the provisioning run finished successfully. */
    private volatile boolean finished = false;

    /** Set when the last provisioning run failed (drives [Retry] and the red status). */
    private volatile boolean failed = false;

    /** Set once a terminal outcome is reached (verified / restart required). */
    private volatile boolean terminal = false;

    /** True between the resource reload and the style re-discovery result. */
    private volatile boolean awaitingStyles = false;

    /** Style re-discovery outcome: -1 pending, 0 verified active, 1 restart required. */
    private volatile int stylesResult = -1;

    /** Manual-instructions view toggle. */
    private boolean showingManual = false;

    /** The primary button (Download / Retry / Continue, per phase). */
    @Nullable
    private Button primaryButton;

    /** The manual-install button (hidden while running / applying / done). */
    @Nullable
    private Button manualButton;

    /** The demoted "play without the art" link. */
    @Nullable
    private Button skipLink;

    /** Computed in {@link #init()}: y of the first text row. */
    private int textY = 8;

    /** Computed in {@link #init()}: y of the status line below the button stack. */
    private int statusY = -1;

    /**
     * One word-wrapped text row of the layout model shared by {@link #init()} (height
     * computation) and {@link #extractRenderState} (drawing).
     *
     * @param text      the rendered text.
     * @param color     the text color.
     * @param centered  true → centered on the screen, false → left edge of the text column.
     * @param padBefore extra vertical padding before this row (section gaps).
     */
    private record Row(FormattedCharSequence text, int color, boolean centered, int padBefore)
    {
    }

    /** White with full alpha (see the class javadoc — text colors must be ARGB). */
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    /** Light grey with full alpha. */
    private static final int COLOR_LIGHT = 0xFFDDDDDD;

    /** Mid grey with full alpha. */
    private static final int COLOR_GREY = 0xFFAAAAAA;

    /** Green with full alpha. */
    private static final int COLOR_GREEN = 0xFF55FF55;

    /** Cyan with full alpha. */
    private static final int COLOR_CYAN = 0xFF55FFFF;

    /** Amber with full alpha. */
    private static final int COLOR_AMBER = 0xFFFFAA00;

    /** Red with full alpha (error lines). */
    private static final int COLOR_RED = 0xFFFF5555;

    /** Yellow with full alpha (progress lines). */
    private static final int COLOR_YELLOW = 0xFFFFFF55;

    /**
     * @param missing the namespaces that are neither bundled nor provisioned.
     */
    public PortAssetNoticeScreen(final List<AssetNamespace> missing)
    {
        super(Component.literal(PortAssetText.format("portassets.screen.title")));
        this.missing = missing;
    }

    // ------------------------------------------------------------------ state

    /**
     * @return the current layout phase (see {@link Phase}).
     */
    private Phase phase()
    {
        if (showingManual)
        {
            return Phase.MANUAL;
        }
        if (RUNNING.get() && !finished)
        {
            return Phase.RUNNING;
        }
        if (finished && !terminal)
        {
            return Phase.APPLYING;
        }
        if (finished)
        {
            return Phase.DONE;
        }
        return Phase.NOTICE;
    }

    // ------------------------------------------------------------------ layout

    /**
     * @return the text column width for the current screen size.
     */
    private int wrapWidth()
    {
        return Math.max(120, Math.min(this.width - 16, MAX_WRAP_WIDTH));
    }

    /**
     * Builds the full text block (title, "what is this", pack list, button explanation —
     * or the manual-install instructions). Word-wrapped to the column width so long
     * translations can never run off the screen.
     *
     * <p>Density tiers (verified by layout simulation down to the 320x240 vanilla
     * minimum): <b>normal</b> (≥340px) shows everything; <b>compact</b> (&lt;340px) drops
     * the second intro line and merges the pack list into one wrapped line;
     * <b>ultra</b> (&lt;262px) additionally swaps to the one-line intro and the short
     * button explanation and tightens the button gap — the essential "what is this"
     * explanation always stays visible.</p>
     *
     * @return the rows, top to bottom.
     */
    private List<Row> buildRows()
    {
        final List<Row> rows = new ArrayList<>();
        final int width = wrapWidth();
        final boolean compact = this.height < COMPACT_HEIGHT;
        final boolean ultra = this.height < ULTRA_HEIGHT;

        rows.add(new Row(literal(PortAssetText.format("portassets.screen.title")), COLOR_WHITE, true, 0));

        if (showingManual)
        {
            rows.add(new Row(literal(PortAssetText.format("portassets.screen.manual.title")), COLOR_AMBER, true, 6));
            for (int i = 1; i <= 5; i++)
            {
                addWrapped(rows, "portassets.screen.manual.line" + i, COLOR_LIGHT, width, 0);
            }
            addWrapped(rows, "portassets.screen.manual.towntalk", COLOR_CYAN, width, 6);
            addWrapped(rows, "portassets.screen.manual.towntalk2", COLOR_CYAN, width, 0);
            return rows;
        }

        // "what is this screen" — directly under the title, always visible;
        // the density tiers drop the second line first, then fall back to the one-liner
        addWrapped(rows, ultra ? "portassets.screen.what.short" : "portassets.screen.what1", COLOR_GREY, width, 6);
        if (!compact)
        {
            addWrapped(rows, "portassets.screen.what2", COLOR_GREY, width, 0);
        }

        // the packs that would be fetched: full list, or one merged line on tight screens
        if (compact)
        {
            final StringBuilder names = new StringBuilder();
            for (final AssetNamespace namespace : missing)
            {
                if (names.length() > 0)
                {
                    names.append(", ");
                }
                names.append(shortName(namespace));
            }
            addWrappedLiteral(rows, PortAssetText.format("portassets.screen.packs") + " " + names, COLOR_LIGHT, width, 6);
        }
        else
        {
            addWrapped(rows, "portassets.screen.packs", COLOR_WHITE, width, 6);
            for (final AssetNamespace namespace : missing)
            {
                final String size = namespace.sizeHint();
                addWrappedLiteral(rows, "• " + namespace.displayName() + (size.isEmpty() ? "" : "  (" + size + ")"),
                    COLOR_LIGHT, width, 0);
            }
        }

        // what the buttons do (the English explanation block, localized) — the short
        // variant keeps the essentials on ultra-tight screens
        if (ultra)
        {
            addWrapped(rows, "portassets.screen.explain.short", COLOR_GREEN, width, 6);
        }
        else
        {
            addWrapped(rows, "portassets.screen.explain1", COLOR_GREEN, width, 6);
            addWrapped(rows, "portassets.screen.explain2", COLOR_GREEN, width, 0);
        }
        return rows;
    }

    /**
     * Appends the word-wrapped rows of one translated key.
     *
     * @param rows      the row sink.
     * @param key       the translation key.
     * @param color     the text color.
     * @param width     the wrap width.
     * @param padBefore padding before the first wrapped line.
     */
    private void addWrapped(final List<Row> rows, final String key, final int color, final int width, final int padBefore)
    {
        addWrappedLiteral(rows, PortAssetText.format(key), color, width, padBefore);
    }

    /**
     * Appends the word-wrapped rows of one literal string.
     *
     * @param rows      the row sink.
     * @param text      the literal text.
     * @param color     the text color.
     * @param width     the wrap width.
     * @param padBefore padding before the first wrapped line.
     */
    private void addWrappedLiteral(final List<Row> rows, final String text, final int color, final int width, final int padBefore)
    {
        final List<FormattedCharSequence> lines = this.font.split(Component.literal(text), width);
        int pad = padBefore;
        for (final FormattedCharSequence line : lines)
        {
            rows.add(new Row(line, color, false, pad));
            pad = 0;
        }
    }

    /**
     * @param text any text.
     * @return its render-ready visual order sequence.
     */
    private static FormattedCharSequence literal(final String text)
    {
        return Component.literal(text).getVisualOrderText();
    }

    /**
     * @param namespace the namespace table entry.
     * @return a short name for the ultra-compact pack list.
     */
    private static String shortName(final AssetNamespace namespace)
    {
        final String display = namespace.displayName();
        final int cut = display.indexOf(" (");
        return cut > 0 ? display.substring(0, cut) : display;
    }

    /**
     * @return the total height of the text block.
     */
    private int textBlockHeight()
    {
        int height = 8; // top margin of the block
        for (final Row row : buildRows())
        {
            height += row.padBefore() + this.font.lineHeight + 2;
        }
        return height;
    }

    /**
     * @param phase the layout phase.
     * @param gap   the effective button gap.
     * @return the vertical space the button stack needs (below the text block).
     */
    private int buttonStackHeight(final Phase phase, final int gap)
    {
        return switch (phase)
        {
            case MANUAL -> 6 + BUTTON_HEIGHT;
            case NOTICE -> 8 + BUTTON_HEIGHT + gap + BUTTON_HEIGHT + gap + LINK_HEIGHT;
            case DONE -> 8 + BUTTON_HEIGHT;
            case RUNNING, APPLYING -> 0;
        };
    }

    /**
     * @param phase the layout phase.
     * @return the vertical space the status + progress area needs (below the buttons).
     */
    private int statusAreaHeight(final Phase phase)
    {
        // status line + bar zone; the bar only shows while work is in flight
        final int line = this.font.lineHeight + 4;
        return (phase == Phase.MANUAL ? 0 : line + 6 + BAR_HEIGHT + 8);
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init()
    {
        final Phase phase = phase();
        final int gap = this.height < ULTRA_HEIGHT ? 6 : BUTTON_GAP;

        // vertically centered content column (v4 — the v3 top anchor hugged the top edge)
        final int contentHeight = textBlockHeight() + buttonStackHeight(phase, gap) + statusAreaHeight(phase);
        int y = Math.max(8, (this.height - contentHeight) / 2);
        textY = y;
        y += textBlockHeight();

        primaryButton = null;
        manualButton = null;
        skipLink = null;
        statusY = -1;

        switch (phase)
        {
            case MANUAL ->
            {
                final int backY = y + 6;
                primaryButton = addRenderableWidget(Button.builder(backLabel(), b -> {
                        showingManual = false;
                        rebuildWidgets();
                    })
                    .bounds(this.width / 2 - BUTTON_WIDTH / 2, backY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
                setInitialFocus(primaryButton);
            }
            case NOTICE ->
            {
                final int buttonY = y + 8;
                primaryButton = addRenderableWidget(Button.builder(downloadLabel(), b -> startDownload())
                    .bounds(this.width / 2 - BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
                primaryButton.setTooltip(Tooltip.create(Component.literal(
                    PortAssetText.format("portassets.screen.download.tooltip"))));

                manualButton = addRenderableWidget(Button.builder(Component.literal(PortAssetText.format("portassets.screen.manual")), b -> {
                        showingManual = true;
                        rebuildWidgets();
                    })
                    .bounds(this.width / 2 - BUTTON_WIDTH / 2, buttonY + BUTTON_HEIGHT + gap, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());

                // the demoted escape hatch: a small underlined text link, not a third
                // same-size button — with an explicit warning tooltip
                final int linkY = buttonY + (BUTTON_HEIGHT + gap) * 2;
                final Component linkLabel = Component.literal(PortAssetText.format("portassets.screen.skip.link"));
                skipLink = addRenderableWidget(new PlainTextButton(
                    this.width / 2 - BUTTON_WIDTH / 2, linkY, BUTTON_WIDTH, LINK_HEIGHT, linkLabel, b -> {
                        final PortAssetSettings settings = PortAssets.getSettings();
                        settings.skipNotice = true;
                        settings.save();
                        onClose();
                    }, this.font));
                skipLink.setTooltip(Tooltip.create(Component.literal(
                    PortAssetText.format("portassets.screen.skip.tooltip"))));

                statusY = linkY + LINK_HEIGHT + 8;
                setInitialFocus(primaryButton);
            }
            case DONE ->
            {
                final int buttonY = y + 8;
                primaryButton = addRenderableWidget(Button.builder(continueLabel(), b -> onClose())
                    .bounds(this.width / 2 - BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
                statusY = buttonY + BUTTON_HEIGHT + 10;
                setInitialFocus(primaryButton);
            }
            case RUNNING, APPLYING ->
            {
                // a clean progress view: no buttons at all while work is in flight
                statusY = y + 6;
            }
        }
    }

    /**
     * @return the primary button label in the NOTICE phase ("Download now" / "Retry").
     */
    private Component downloadLabel()
    {
        return Component.literal(PortAssetText.format(
            failed ? "portassets.screen.status.retry" : "portassets.screen.download"));
    }

    /**
     * @return the focused terminal button label ("Continue").
     */
    private static Component continueLabel()
    {
        return Component.literal(PortAssetText.format("portassets.screen.continue"));
    }

    /**
     * @return the manual view's back button label.
     */
    private static Component backLabel()
    {
        return Component.literal(PortAssetText.format("portassets.screen.back"));
    }

    // ------------------------------------------------------------------ hard modal (v4)

    /**
     * v4 hard modal: every input event is consumed. MouseHandler/KeyboardHandler fall
     * through to game handling when a screen returns {@code false} — whatever any engine
     * layer does with that fall-through, nothing behind this overlay can ever be pressed
     * "through" it. The widget handling still runs first (buttons keep working).
     */
    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick)
    {
        super.mouseClicked(event, doubleClick);
        return true;
    }

    @Override
    public boolean mouseReleased(final MouseButtonEvent event)
    {
        super.mouseReleased(event);
        return true;
    }

    @Override
    public boolean mouseDragged(final MouseButtonEvent event, final double dx, final double dy)
    {
        super.mouseDragged(event, dx, dy);
        return true;
    }

    @Override
    public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY)
    {
        super.mouseScrolled(x, y, scrollX, scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(final KeyEvent event)
    {
        // ESC (when allowed) and focus/tab navigation still run through super
        super.keyPressed(event);
        return true;
    }

    @Override
    public boolean keyReleased(final KeyEvent event)
    {
        super.keyReleased(event);
        return true;
    }

    @Override
    public boolean charTyped(final CharacterEvent event)
    {
        super.charTyped(event);
        return true;
    }

    // ------------------------------------------------------------------ provisioning

    /**
     * Rebuilds the widget layout only while this screen is still the ACTIVE screen —
     * the reload overlay / ESC may have replaced it in the meantime, and touching a
     * dead screen's widgets would re-create them on top of whatever replaced us.
     */
    private void rebuildIfActive()
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen == this)
        {
            rebuildWidgets();
        }
    }

    /**
     * Starts the background provisioning run (guarded — never twice at once).
     */
    private void startDownload()
    {
        if (!RUNNING.compareAndSet(false, true))
        {
            return;
        }
        failed = false;
        finished = false;
        terminal = false;
        awaitingStyles = false;
        stylesResult = -1;
        progressPercent = -1;
        status = PortAssetText.format("portassets.screen.status.starting");
        rebuildWidgets(); // switch to the clean RUNNING view

        WORKER.submit(() -> {
            final String error = AssetProvisioner.provisionMissing(statusLine -> {
                status = statusLine;
                final Matcher matcher = PERCENT_PATTERN.matcher(statusLine);
                if (matcher.find())
                {
                    progressPercent = Integer.parseInt(matcher.group(1));
                }
                else
                {
                    // phase change (extracting / converting / ready): those lines carry no
                    // percent — an idle full bar reads as "the game froze" (play-test #3),
                    // so fall back to the animated indeterminate sweep instead
                    progressPercent = -1;
                }
            });
            final Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> onProvisioningFinished(error));
        });
    }

    /**
     * Completion handler — always invoked on the main thread.
     *
     * @param error null on success, otherwise the failure description.
     */
    private void onProvisioningFinished(@Nullable final String error)
    {
        RUNNING.set(false);
        if (error == null)
        {
            finished = true;
            progressPercent = 100;
            status = PortAssetText.format("portassets.screen.status.done");
            MineColonies.LOGGER.info("port-assets: provisioning finished — reloading resource packs");

            final Minecraft minecraft = Minecraft.getInstance();
            // both title-screen and in-world contexts are safe here — this is exactly the
            // vanilla F3+T path (repository reload + LoadingOverlay)
            minecraft.reloadResourcePacks().thenRun(() -> minecraft.execute(this::afterReload));
        }
        else
        {
            failed = true;
            progressPercent = -1;
            status = error;
            MineColonies.LOGGER.warn("port-assets: provisioning failed: {}", error);
            rebuildIfActive(); // NOTICE view with [Retry]
        }
    }

    /**
     * Post-reload verification (main thread): every provisioned pack that carries
     * resources/data must now be part of the repository's SELECTED set — the source
     * re-registers itself on every {@code PackRepository#reload()} (see
     * {@code PortAssetPackSources}'s lifetime contract), so a missing entry means
     * something unexpected happened and the player genuinely needs a restart. The screen
     * ends in the explicit [Continue] state either way (v4: no silent auto-close, no
     * dead-end — the reported "no way out but ESC").
     */
    private void afterReload()
    {
        final Minecraft minecraft = Minecraft.getInstance();
        boolean allActive = true;
        for (final AssetNamespace namespace : PortAssets.NAMESPACES)
        {
            final String owner = AssetProvisioner.downloadOwner(namespace);
            final var storeDir = PortAssetPaths.namespaceDir(owner);
            if (!Files.isDirectory(storeDir) || !Files.isRegularFile(PortAssetPaths.markerFile(owner)))
            {
                continue; // not provisioned — nothing to verify
            }
            if (!Files.isDirectory(storeDir.resolve("assets")) && !Files.isDirectory(storeDir.resolve("data")))
            {
                continue; // blueprint-only style pack — no resource pack to verify
            }
            final boolean active = minecraft.getResourcePackRepository()
                .getSelectedIds()
                .contains(PortAssetPackSources.PACK_ID_PREFIX + owner);
            if (!active)
            {
                allActive = false;
                MineColonies.LOGGER.warn("port-assets: {} was provisioned but is NOT in the selected pack set "
                    + "after the reload — a game restart is required", owner);
            }
        }

        if (!allActive)
        {
            terminal = true;
            status = PortAssetText.format("portassets.screen.status.restart");
            rebuildIfActive(); // DONE view, amber "restart required" + [Continue]
            return;
        }

        // hot-load the provisioned building styles: the client structure-pack discovery
        // runs once at client construction, i.e. BEFORE this store existed (play-test #5)
        if (PortAssetStyleReload.start())
        {
            awaitingStyles = true;
            stylesResult = -1;
            status = PortAssetText.format("portassets.screen.status.styles");
            MineColonies.LOGGER.info("port-assets: resource packs verified — re-running the client structure "
                + "pack discovery to activate the provisioned styles");
        }
        else
        {
            terminal = true;
            status = PortAssetText.format("portassets.screen.status.active");
            MineColonies.LOGGER.info("port-assets: all provisioned packs verified active in the live repository");
            rebuildIfActive(); // DONE view, green + [Continue]
        }
    }

    /**
     * Screen tick — polls the async style re-discovery started in {@link #afterReload()}
     * and verifies its result as soon as it finished.
     */
    @Override
    public void tick()
    {
        if (awaitingStyles && stylesResult < 0
              && ClientStructurePackLoader.loadingState != ClientLoadingState.LOADING)
        {
            awaitingStyles = false;
            terminal = true;
            if (PortAssetStyleReload.verify())
            {
                stylesResult = 0;
                status = PortAssetText.format("portassets.screen.status.stylesactive");
                MineColonies.LOGGER.info("port-assets: external style packs verified registered — no restart needed");
            }
            else
            {
                stylesResult = 1;
                status = PortAssetText.format("portassets.screen.status.stylesrestart");
                MineColonies.LOGGER.warn("port-assets: external style packs are not registered — a game restart "
                    + "is required to activate the styles");
            }
            rebuildIfActive(); // DONE view (green or amber) + [Continue]
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractRenderState(final GuiGraphicsExtractor gui, final int mouseX, final int mouseY, final float partialTick)
    {
        // widgets (buttons) first — same contract as the vanilla default implementation
        super.extractRenderState(gui, mouseX, mouseY, partialTick);

        final Minecraft minecraft = this.minecraft;
        if (minecraft == null)
        {
            return;
        }
        final var font = minecraft.font;

        // vertically centered, word-wrapped text block (see the class javadoc for v4)
        final List<Row> rows = buildRows();
        final int columnX = this.width / 2 - wrapWidth() / 2;
        int y = textY;
        for (final Row row : rows)
        {
            y += row.padBefore();
            if (row.centered())
            {
                gui.centeredText(font, row.text(), this.width / 2, y, row.color());
            }
            else
            {
                gui.text(font, row.text(), columnX, y, row.color());
            }
            y += font.lineHeight + 2;
        }

        // status line + progress bar below the button stack
        if (status != null && !status.isEmpty() && !showingManual)
        {
            final Phase phase = phase();
            final int statusY = this.statusY >= 0 ? this.statusY
                : this.height - 2 * (font.lineHeight + 6) - BAR_HEIGHT;
            final int color = failed ? COLOR_RED
                : (phase == Phase.DONE ? (stylesResult == 1 ? COLOR_AMBER : COLOR_GREEN)
                : (phase == Phase.NOTICE ? COLOR_LIGHT : COLOR_YELLOW));
            gui.centeredText(font, Component.literal(status), this.width / 2, statusY, color);

            final boolean workInFlight = phase == Phase.RUNNING || phase == Phase.APPLYING;
            if (workInFlight)
            {
                final int barY = statusY + font.lineHeight + 6;
                final int barX0 = this.width / 2 - BUTTON_WIDTH / 2;
                final int barX1 = this.width / 2 + BUTTON_WIDTH / 2;
                gui.fill(barX0, barY, barX1, barY + BAR_HEIGHT, 0xFF000000);
                gui.fill(barX0 + BAR_INSET, barY + BAR_INSET, barX1 - BAR_INSET, barY + BAR_HEIGHT - BAR_INSET, 0xFF333333);
                if (progressPercent >= 0)
                {
                    final int fillWidth = (BUTTON_WIDTH - 2 * BAR_INSET) * Math.min(100, progressPercent) / 100;
                    gui.fill(barX0 + BAR_INSET, barY + BAR_INSET, barX0 + BAR_INSET + fillWidth, barY + BAR_HEIGHT - BAR_INSET,
                        0xFF3FBF3F);
                }
                else
                {
                    // indeterminate: thin animated sweep
                    final long tick = System.currentTimeMillis() / 3;
                    final int sweepWidth = 24;
                    final int sweepX = barX0 + BAR_INSET + (int) ((tick / 4) % (BUTTON_WIDTH - 2 * BAR_INSET - sweepWidth + 1));
                    gui.fill(sweepX, barY + BAR_INSET, sweepX + sweepWidth, barY + BAR_HEIGHT - BAR_INSET, 0xFF3FBF3F);
                }
            }
        }
    }

    @Override
    public void onClose()
    {
        if (this.minecraft != null)
        {
            this.minecraft.setScreen(new TitleScreen());
        }
    }

    @Override
    public boolean shouldCloseOnEsc()
    {
        // never abandon a running download; afterwards ESC is the keyboard twin of [Continue]
        return !RUNNING.get() || finished;
    }
}
