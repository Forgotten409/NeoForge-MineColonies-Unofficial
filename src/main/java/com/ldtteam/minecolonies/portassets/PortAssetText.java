package com.ldtteam.minecolonies.portassets;

import java.util.Map;

import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Language lookup with a hard EMBEDDED fallback — PORT26 (publishing support).
 *
 * <p><b>Why:</b> the first play-test of the publish jar showed the notice screen's buttons
 * with raw translation keys. The novel-lang files in the publish jar do contain every
 * {@code portassets.*} key (and 26.1.2 always loads {@code en_us} as the language-stack
 * fallback — verified against {@code LanguageManager#onResourceManagerReload}), so the
 * likeliest cause was a stale build — but critical UI must never depend on that assumption
 * again. This helper resolves each key through, in order:</p>
 * <ol>
 *   <li>the live client translations ({@code I18n}, when the key exists there — honours
 *       any language pack overrides),</li>
 *   <li>the embedded fallback table below — Polish when the selected client language
 *       starts with {@code pl}, English otherwise (the maintainer is Polish; the fallback
 *       also serves the majority-language case),</li>
 *   <li>the raw key (last resort, still greppable in logs).</li>
 * </ol>
 *
 * <p>The client-only lookups are isolated in {@link ClientText} so this class never touches
 * client classes on a dedicated server (same pattern as {@code PortAssetPaths.ClientGameDir}).</p>
 */
public final class PortAssetText
{
    /** English fallbacks — kept in sync with resources-publish novel lang. */
    private static final Map<String, String> FALLBACK_EN = Map.ofEntries(
        Map.entry("portassets.screen.title", "MineColonies port — external assets"),
        Map.entry("portassets.screen.what1", "This port ships without the original MineColonies art and data (ARR license)."),
        Map.entry("portassets.screen.what.short", "Port without the original ARR art — the buttons below fetch it:"),
        Map.entry("portassets.screen.what2", "The buttons below fetch it from the official CurseForge pages."),
        Map.entry("portassets.screen.packs", "Packs to fetch:"),
        Map.entry("portassets.screen.explain1", "[Download now] downloads the packs listed above into <game-dir>/port-assets/ and activates them immediately — no restart needed."),
        Map.entry("portassets.screen.explain2", "No internet, or downloads keep failing? [Manual install] shows the offline way. The small link below the buttons starts the game without the art (missing textures — no crash)."),
        Map.entry("portassets.screen.explain.short", "[Download now] fetches the packs above and activates them immediately — no restart."),
        Map.entry("portassets.screen.download", "Download now"),
        Map.entry("portassets.screen.download.tooltip", "Fetches the packs listed above from the official CurseForge pages into <game-dir>/port-assets/ and activates them immediately — no restart needed."),
        Map.entry("portassets.screen.manual", "Manual install"),
        Map.entry("portassets.screen.skip.link", "Play without the art (not recommended)"),
        Map.entry("portassets.screen.skip.tooltip", "The game runs, but MineColonies blocks and items show missing textures and the building styles are absent.\\nThis notice reappears on every launch until the assets are provided."),
        Map.entry("portassets.screen.continue", "Continue"),
        Map.entry("portassets.screen.back", "Back"),
        Map.entry("portassets.screen.status.starting", "Starting download…"),
        Map.entry("portassets.screen.status.done", "Done — applying resource packs…"),
        Map.entry("portassets.screen.status.styles", "Loading building styles…"),
        Map.entry("portassets.screen.status.active", "Assets active — no restart needed."),
        Map.entry("portassets.screen.status.stylesactive", "Assets + building styles active — no restart needed."),
        Map.entry("portassets.screen.status.stylesrestart", "Packs active — building styles appear after a game restart."),
        Map.entry("portassets.screen.status.restart", "Downloaded — restart the game to activate the packs."),
        Map.entry("portassets.screen.status.retry", "Retry"),
        Map.entry("portassets.screen.status.failed", "Failed — see the log for details."),
        Map.entry("portassets.error.offline", "No internet connection or the network failed — check your connection and press Retry."),
        Map.entry("portassets.error.stall", "The connection dropped mid-download (no data for 30 s) — press Retry."),
        Map.entry("portassets.error.http", "The download server answered: %s — try again later, or use [Manual install]."),
        Map.entry("portassets.error.interrupted", "The download was interrupted."),
        Map.entry("portassets.screen.manual.title", "Manual install (offline)"),
        Map.entry("portassets.screen.manual.line1", "1. Download the official MineColonies 1.21.1 mod jar from CurseForge"),
        Map.entry("portassets.screen.manual.line2", "    (the other mods are already bundled — GPL license)."),
        Map.entry("portassets.screen.manual.line3", "2. Put the jar file into:  <game-dir>/port-assets/source-jars/"),
        Map.entry("portassets.screen.manual.line4", "3. Restart the game and press 'Download now' — the loader extracts"),
        Map.entry("portassets.screen.manual.line5", "    it from the jar without any network access."),
        Map.entry("portassets.screen.manual.towntalk", "Addon packs (TownTalk voices, Byzantine + StyleColonies styles):"),
        Map.entry("portassets.screen.manual.towntalk2", "    put their official jars into the same source-jars folder as well."),
        Map.entry("portassets.status.downloading", "Downloading %s…"),
        Map.entry("portassets.status.extracting", "Extracting %s… %d file(s)"),
        Map.entry("portassets.status.converting", "Converting to the 26.1 formats… %d file(s)"),
        Map.entry("portassets.status.ready", "%s ready."));

    /** Polish fallbacks for the same keys. */
    private static final Map<String, String> FALLBACK_PL = Map.ofEntries(
        Map.entry("portassets.screen.title", "Port MineColonies — zewnętrzne assety"),
        Map.entry("portassets.screen.what1", "Ten port nie zawiera oryginalnych grafik/danych MineColonies (licencja ARR)."),
        Map.entry("portassets.screen.what.short", "Port bez oryginalnej grafiki ARR — przyciski poniżej pobiorą ją:"),
        Map.entry("portassets.screen.what2", "Poniższe przyciski pobiorą je z oficjalnych stron CurseForge."),
        Map.entry("portassets.screen.packs", "Pakiety do pobrania:"),
        Map.entry("portassets.screen.explain1", "[Pobierz teraz] pobiera wymienione wyżej pakiety do <katalog-gry>/port-assets/ i aktywuje je od razu — bez restartu gry."),
        Map.entry("portassets.screen.explain2", "Brak internetu albo pobieranie ciągle pada? [Instalacja ręczna] pokazuje sposób offline. Mały link pod przyciskami uruchamia grę bez grafiki (brak tekstur — bez crasha)."),
        Map.entry("portassets.screen.explain.short", "[Pobierz teraz] pobiera pakiety z listy powyżej i aktywuje je od razu — bez restartu."),
        Map.entry("portassets.screen.download", "Pobierz teraz"),
        Map.entry("portassets.screen.download.tooltip", "Pobiera pakiety z listy powyżej z oficjalnych stron CurseForge do <katalog-gry>/port-assets/ i aktywuje je od razu — bez restartu gry."),
        Map.entry("portassets.screen.manual", "Instalacja ręczna"),
        Map.entry("portassets.screen.skip.link", "Zagraj bez grafiki (niezalecane)"),
        Map.entry("portassets.screen.skip.tooltip", "Gra działa, ale bloki i przedmioty MineColonies mają brakujące tekstury, a style budowania są niedostępne.\\nTen ekran wróci przy każdym uruchomieniu, dopóki assety nie zostaną pobrane."),
        Map.entry("portassets.screen.continue", "Dalej"),
        Map.entry("portassets.screen.back", "Wróć"),
        Map.entry("portassets.screen.status.starting", "Rozpoczynam pobieranie…"),
        Map.entry("portassets.screen.status.done", "Gotowe — przeładowuję pakiety zasobów…"),
        Map.entry("portassets.screen.status.styles", "Ładuję style budowania…"),
        Map.entry("portassets.screen.status.active", "Assety aktywne — restart niepotrzebny."),
        Map.entry("portassets.screen.status.stylesactive", "Assety i style budowania aktywne — restart niepotrzebny."),
        Map.entry("portassets.screen.status.stylesrestart", "Pakiety aktywne — style budowania pojawią się po restarcie gry."),
        Map.entry("portassets.screen.status.restart", "Pobrano — zrestartuj grę, aby aktywować pakiety."),
        Map.entry("portassets.screen.status.retry", "Ponów"),
        Map.entry("portassets.screen.status.failed", "Błąd — szczegóły w logu gry."),
        Map.entry("portassets.error.offline", "Brak internetu lub błąd sieci — sprawdź połączenie i kliknij Ponów."),
        Map.entry("portassets.error.stall", "Połączenie zerwało się w trakcie pobierania (brak danych przez 30 s) — kliknij Ponów."),
        Map.entry("portassets.error.http", "Serwer pobrań odpowiedział: %s — spróbuj później albo użyj [Instalacji ręcznej]."),
        Map.entry("portassets.error.interrupted", "Pobieranie zostało przerwane."),
        Map.entry("portassets.screen.manual.title", "Instalacja ręczna (offline)"),
        Map.entry("portassets.screen.manual.line1", "1. Pobierz oficjalny jar MineColonies 1.21.1 z CurseForge"),
        Map.entry("portassets.screen.manual.line2", "    (pozostałe mody są już wbudowane — licencja GPL)."),
        Map.entry("portassets.screen.manual.line3", "2. Włóż plik jar do:  <katalog-gry>/port-assets/source-jars/"),
        Map.entry("portassets.screen.manual.line4", "3. Uruchom grę ponownie i kliknij 'Pobierz teraz' — loader sam"),
        Map.entry("portassets.screen.manual.line5", "    rozpakuje plik bez użycia sieci."),
        Map.entry("portassets.screen.manual.towntalk", "Pakiety dodatkowe (głosy TownTalk, style Byzantine + StyleColonies):"),
        Map.entry("portassets.screen.manual.towntalk2", "    włóż ich oficjalne jary do tego samego folderu source-jars."),
        Map.entry("portassets.status.downloading", "Pobieranie %s…"),
        Map.entry("portassets.status.extracting", "Rozpakowywanie %s… %d plików"),
        Map.entry("portassets.status.converting", "Konwersja do formatów 26.1… %d plików"),
        Map.entry("portassets.status.ready", "%s gotowe."));

    private PortAssetText()
    {
        // static helper only
    }

    /**
     * Resolves a key and applies {@link String#format(String, Object...)} with the given
     * arguments. Thread-safe: callable from provisioning worker threads.
     *
     * @param key  the translation key.
     * @param args format arguments ({@code %s} / {@code %d} / {@code %1$s} style).
     * @return the formatted text (never the raw pattern, except in the last-resort case).
     */
    public static String format(final String key, final Object... args)
    {
        String pattern = null;
        if (FMLEnvironment.getDist().isClient())
        {
            pattern = ClientText.resolve(key);
        }
        if (pattern == null)
        {
            pattern = fallback(key);
        }
        if (pattern == null)
        {
            return key; // last resort: greppable in logs
        }
        try
        {
            return String.format(pattern, args);
        }
        catch (final java.util.IllegalFormatException e)
        {
            return pattern;
        }
    }

    /**
     * @param key the translation key.
     * @return the embedded fallback for the current client language, or null.
     */
    private static String fallback(final String key)
    {
        final boolean polish = FMLEnvironment.getDist().isClient() && ClientText.isPolish();
        return (polish ? FALLBACK_PL : FALLBACK_EN).get(key);
    }

    /**
     * Client-only lookups (I18n + selected language) — only loaded on the client dist.
     */
    private static final class ClientText
    {
        private ClientText()
        {
            // static helper only
        }

        static String resolve(final String key)
        {
            try
            {
                if (net.minecraft.client.resources.language.I18n.exists(key))
                {
                    return net.minecraft.client.resources.language.I18n.get(key);
                }
            }
            catch (final Throwable ignored)
            {
                // I18n initializes with the client — if anything is off, fall through
            }
            return null;
        }

        static boolean isPolish()
        {
            try
            {
                final var minecraft = net.minecraft.client.Minecraft.getInstance();
                // Minecraft#languageManager is private in 26.1.2 — the selected code is
                // mirrored in the public Options#languageCode (verified: Options.java:1044).
                return minecraft != null && minecraft.options != null
                         && minecraft.options.languageCode != null
                         && minecraft.options.languageCode.startsWith("pl");
            }
            catch (final Throwable t)
            {
                return false;
            }
        }
    }
}
