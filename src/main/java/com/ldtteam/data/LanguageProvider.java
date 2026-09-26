package com.ldtteam.data;

import java.util.List;

import net.minecraft.data.DataGenerator;

/**
 * Vendored minimal replacement for the external {@code com.ldtteam:data} library (LDT Team's
 * datagenerators lib) that domum-ornamentum 1.21.1 depended on via Maven.
 *
 * <p>PORT26: only the language-provider part is required by the merged minecolonies port.
 * The upstream class allowed splitting translations into per-category {@link SubProvider}s
 * that are aggregated into a single {@code assets/<modid>/lang/<locale>.json} file.
 */
public class LanguageProvider extends net.neoforged.neoforge.common.data.LanguageProvider
{
    private final List<SubProvider> subProviders;

    public LanguageProvider(final DataGenerator generator, final String modId, final String locale, final List<SubProvider> subProviders)
    {
        super(generator.getPackOutput(), modId, locale);
        this.subProviders = subProviders;
    }

    @Override
    protected void addTranslations()
    {
        final LanguageAcceptor acceptor = this::add;
        for (final SubProvider subProvider : subProviders)
        {
            subProvider.addTranslations(acceptor);
        }
    }

    public interface SubProvider
    {
        void addTranslations(final LanguageAcceptor acceptor);
    }

    public interface LanguageAcceptor
    {
        void add(final String key, final String value);
    }
}
