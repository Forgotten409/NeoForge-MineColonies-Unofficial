package com.ldtteam.common.language;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for localization and sending player messages.
 * Note that MineColonies is still using some of these, so it's not safe to delete yet.
 */
public final class LanguageHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageHandler.class);

    /**
     * Private constructor to hide implicit one.
     */
    private LanguageHandler()
    {
        // Intentionally left empty.
    }

    /**
     * Localize a string and use String.format().
     *
     * @param inputKey translation key.
     * @param args     Objects for String.format().
     * @return Localized string.
     */
    @Deprecated(forRemoval = true, since = "1.21.1")
    public static String format(final String key, final Object... args)
    {
        final String result = (args.length == 0 ? Component.translatable(key) : Component.translatable(key, args)).getString();
        return result.isEmpty() ? key : result;
    }

    /**
     * Translates key to readable string and formats it.
     *
     * @param key    translation key
     * @param format String.format() attributes
     * @return formatted string
     */
    @Deprecated(forRemoval = true, since = "1.21.1")
    public static String translateKeyWithFormat(final String key, final Object... format)
    {
        return String.format(translateKey(key), format);
    }

    /**
     * Translates key to readable string.
     *
     * @param key translation key
     * @return readable string
     */
    public static String translateKey(final String key)
    {
        return LanguageCache.getInstance().translateKey(key);
    }

    /**
     * Sets our cache to use mc default one.
     */
    public static void setMClanguageLoaded()
    {
        LanguageCache.getInstance().isMCloaded = true;
        LanguageCache.getInstance().languageMap = null;
    }

    public static void loadLangPath(final String path)
    {
        LanguageCache.getInstance().load(path);
    }

    private static class LanguageCache
    {
        private static final String defaultLocale = "en_us";
        private static final LanguageCache instance = new LanguageCache();
        private boolean isMCloaded = false;
        private Map<String, String> languageMap = new ConcurrentHashMap<>();

        private LanguageCache()
        {
            load("assets/blockui/lang/%s.json");
        }

        private void load(final String path)
        {
            final String locale = FMLEnvironment.getDist().isClient() ? ClientLocale.getLocale() : ServerLocale.getLocale();

            // PORT26: null-safe fallback chain (locale -> en_us -> default.json -> warn & skip).
            // The upstream 1.21.1 jar ships en_us.json via Crowdin at build time; a locally built port
            // may not have it, and the original code passed a null InputStream straight into
            // new InputStreamReader(...) -> NPE during mod construction (crash: LanguageCache.load:102).
            InputStream is = locale == null ? null : Thread.currentThread().getContextClassLoader().getResourceAsStream(String.format(path, locale));
            if (is == null && !defaultLocale.equals(locale))
            {
                // PORT26 note: also reached when locale is null (datagen / mod construction,
                // where Minecraft.getInstance() does not exist yet) — the original code
                // formatted a literal "null" path, missed, and fell through to en_us here too.
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream(String.format(path, defaultLocale));
            }
            if (is == null && !"default".equals(locale))
            {
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream(String.format(path, "default"));
            }
            if (is == null)
            {
                LOGGER.warn("PORT26: Could not find any language file for pattern '{}' (locale '{}') - skipping", path, locale);
                return;
            }

            try
            {
                final Map<String, String> parsed = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), new TypeToken<Map<String, String>>()
                {}.getType());
                if (parsed != null)
                {
                    languageMap.putAll(parsed);
                }
            }
            catch (final Exception e)
            {
                LOGGER.warn("PORT26: Failed to parse language file for pattern '{}' (locale '{}') - skipping", path, locale, e);
            }
            finally
            {
                IOUtils.closeQuietly(is);
            }
        }

        private static LanguageCache getInstance()
        {
            return instance;
        }

        private String translateKey(final String key)
        {
            if (isMCloaded)
            {
                return Language.getInstance().getOrDefault(key);
            }
            else
            {
                final String res = languageMap.get(key);
                return res == null ? Language.getInstance().getOrDefault(key) : res;
            }
        }
    }
}
